package mx.papeleriapos.escaner.camara

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.util.Range
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.TorchState
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.withStateAtLeast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.Diagnostico
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.EventoLectura
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.MarcasApertura
import mx.papeleriapos.escaner.PasoApertura
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * El escáner con la cámara real (docs/escaner.md, «Cámara»): Preview + ImageAnalysis a 1280x720 en una sola
 * `SessionConfig` con el `ViewPort` del visor (así `ImageProxy.cropRect` es justo lo que se ve), FPS elegidos de los que
 * acepta la sesión, zoom inicial según la distancia mínima de enfoque, enfoque al abrir, al tocar y cuando un código no
 * se deja leer, linterna, y pausa por inactividad.
 *
 * La apertura (docs/escaner.md, «Apertura»): la app puede enlazar desde `onCreate` ([enlazarTemprano]) con la proporción
 * del visor de la vez anterior y el rango de FPS guardado, así la cámara abre en `onStart` mientras Compose dibuja.
 * [abrir] es idempotente: con la misma proporción, rotación y cámara enlazada no vuelve a enlazar (antes se enlazaba dos
 * veces al abrir porque Compose mide el visor primero sin las barras y luego con ellas); si la proporción cambia, espera
 * [ESPERA_REENLACE_MS] y enlaza una sola vez con la última. La cámara se liga a un ciclo de vida propio ([CicloCamara]) que
 * copia el del dueño, así que pasar de la Activity a la pantalla tampoco vuelve a enlazar.
 *
 * Observa el `CameraState` de cada enlace con su propio oyente ([MaquinaEstadoCamara]): otra app con la cámara,
 * desactivada, configuración no aceptada (sin FPS fijo, y queda guardado) y errores críticos (hasta 2 reintentos); y un
 * vigilante vuelve a enlazar una vez si, con la pantalla al frente y la cámara ya abierta, no sale ningún cuadro en 2 s.
 *
 * [cerrar] suelta la cámara y el motor; un [abrir] posterior vuelve a empezar. Lo llama quien creó el controlador (el
 * ViewModel), no la pantalla: al salir de la composición (otra pestaña) basta con que el ciclo de vida de la pantalla
 * ([CicloCamara] lo copia) se detenga, y al volver la cámara reabre sin volver a enlazar.
 *
 * Se usa desde el hilo principal. El análisis corre en un hilo propio ([Analizador]).
 */
class ControladorCamara(
    contexto: Context,
    configInicial: ConfigEscaner = ConfigEscaner(),
) : ControladorEscaner {
    private val app = contexto.applicationContext
    private val memoria = MemoriaApertura(app)

    private val _config = MutableStateFlow(configInicial)
    private val _estado = MutableStateFlow(
        EstadoEscaner(
            franja = franjaPara(configInicial.altoFranja),
            diagnostico = Diagnostico(motor = configInicial.motor),
            apertura = MarcasApertura.apertura,
        ),
    )
    private val _eventos = MutableSharedFlow<EventoLectura>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _superficie = MutableStateFlow<SurfaceRequest?>(null)
    /** Le pasa al visor cada solicitud de superficie de la vista previa. */
    private val proveedorSuperficie = Preview.SurfaceProvider { solicitud -> _superficie.value = solicitud }

    override val estado: StateFlow<EstadoEscaner> = _estado.asStateFlow()
    override val eventos: SharedFlow<EventoLectura> = _eventos.asSharedFlow()
    override val superficie: StateFlow<SurfaceRequest?> = _superficie.asStateFlow()
    override val config: StateFlow<ConfigEscaner> = _config.asStateFlow()

    private val principal = ContextCompat.getMainExecutor(app)
    private var alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Un solo hilo de análisis (los cuadros y las órdenes, en orden). [cerrar] lo apaga en cuanto termina lo que tenga
     * encargado (liberar el motor) y un [abrir] posterior crea otro: abrir y cerrar seguido (el escaneo rápido, las
     * pruebas de estrés) no deja hilos vivos. Sin trabajo también se termina a los 10 s.
     */
    @Volatile private var hiloAnalisis: ExecutorService = nuevoHiloAnalisis()
    private val senales = SenalesCamara()

    /** La cámara enlazada ahora (null si no hay): las pruebas de estrés la usan para el zoom y la linterna directos. */
    @Volatile var camara: Camera? = null
        private set
    private var proveedor: ProcessCameraProvider? = null
    private var sesion: SessionConfig? = null
    private var ciclo: CicloCamara? = null
    private var dueno: LifecycleOwner? = null

    // Lo que pidió la pantalla y con qué está enlazada (o enlazándose) la cámara.
    private var pedida: Proporcion? = null
    private var rotacionPedida = Surface.ROTATION_0
    private var enlazada: Proporcion? = null
    private var rotacionEnlazada = -1
    /** Con qué nombre se guarda la proporción del visor (cada pantalla tiene la suya). */
    private var pantalla = PANTALLA_PRINCIPAL

    /** Ya pidieron abrir (y no se ha cerrado): con el permiso o al reanudar se enlaza. */
    private var pedidoAbrir = false
    /** Se enlazó desde `onCreate` y la pantalla todavía no lo confirma con [abrir]. */
    private var temprano = false
    private var apagadaPorInactividad = false
    private var cerrado = false
    /** Hubo ON_STOP: el siguiente ON_START es una vuelta de segundo plano. */
    private var volvioDeFondo = false

    private var enlace: Job? = null
    private var reenlaceDiferido: Job? = null
    private var reintento: Job? = null
    private var vigilante: Job? = null
    private var confirmacion: Job? = null
    private var validacionFps: Job? = null

    // Estado de la cámara.
    /** Qué hacer con cada `CameraState` (por enlace) y cuántos reintentos van. */
    private val maquina = MaquinaEstadoCamara()
    /** El oyente del `CameraState` del enlace vigente (uno por enlace, con su número) y dónde se registró. */
    private var oyenteEstado: Consumer<CameraState>? = null
    private var infoOyente: CameraInfo? = null
    /** La sesión no aceptó un rango de FPS fijo (STREAM_CONFIG): desde ahí, sin rango. */
    private var sinFpsFijo = false
    /** La fase ERROR la puso el estado de la cámara (y se quita sola al abrir). */
    private var errorDeCamara = false
    /** CameraX está reintentando por su cuenta (otra app tiene la cámara): el vigilante no se mete. */
    private var errorRecuperable = false
    private var vigilanteUsado = false

    /**
     * El número de la apertura que es de este controlador (`MarcasApertura.numero`), para las marcas que se ponen en el
     * hilo principal (enlace, cámara abierta). Se toma al enlazar en `onCreate` y en ON_START y ON_RESUME de su ciclo:
     * la app empieza la apertura antes (en `onCreate` u `onStart`). Las de los cuadros usan [SenalesCamara.aperturaPropia].
     */
    private var aperturaPropia = 0
    /** Cuánto se esperó a `ProcessCameraProvider` en el último enlace (−1 = ya estaba). */
    private var msEsperaProveedor = -1L
    /** Para saber si este es el último controlador cuyo ciclo empezó ([ultimoIniciado]). */
    private val id = SIGUIENTE_ID.incrementAndGet()

    private val acciones = object : AccionesCamara {
        override fun aplicarZoom(zoom: Float) = enPrincipal {
            val cam = camara ?: return@enPrincipal
            val zs = cam.cameraInfo.zoomState.value
            cam.cameraControl.setZoomRatio(if (zs != null) zoom.coerceIn(zs.minZoomRatio, zs.maxZoomRatio) else zoom)
        }

        override fun enfocar(xBuffer: Float, yBuffer: Float) = enPrincipal { enfocarEnBuffer(xBuffer, yBuffer) }

        override fun encenderLinterna(encender: Boolean) = enPrincipal {
            val cam = camara ?: return@enPrincipal
            if (cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(encender)
        }

        override fun apagarPorInactividad() = enPrincipal { apagar() }
    }

    /** Visible para las pruebas (p. ej. [Analizador.observadorMotor]). */
    internal val analizador = Analizador(_estado, _eventos, _config, senales, acciones)

    /** Cuántas veces se ha enlazado la cámara (diagnóstico y pruebas: abrir con lo mismo no vuelve a enlazar). */
    var enlacesHechos = 0
        private set

    private val selectorResolucion = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        // Sin esto ImageAnalysis usa 640x480; 1080p cuesta ~30 % más por cuadro y para lejos ayuda más el zoom.
        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
        .build()

    private val vista: Preview = crearVista()

    private val analisis: ImageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(selectorResolucion)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        // YUV sin rotar: girar la salida cuesta 10–15 ms por cuadro; los motores reciben la rotación.
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build()
        .also { it.setAnalyzer(hiloAnalisis, analizador) }

    private val observadorLinterna = Observer<Int> { estadoLinterna ->
        val encendida = estadoLinterna == TorchState.ON
        analizador.linternaEncendida = encendida
        _estado.update { it.copy(linterna = encendida, sugerirLinterna = it.sugerirLinterna && !encendida) }
    }

    /** Copia el estado del dueño al ciclo de la cámara. */
    private val observadorDueno = LifecycleEventObserver { fuente, _ ->
        if (fuente === dueno) ciclo?.seguir(fuente.lifecycle.currentState)
    }

    private val observadorCiclo = LifecycleEventObserver { _, evento ->
        when (evento) {
            Lifecycle.Event.ON_START -> {
                // CameraX le da la cámara al último ciclo que empezó: desde aquí sus estados son de este controlador.
                ultimoIniciado = id
                analizador.detenido = false
                // La app ya empezó la apertura (MarcasApertura.nueva en onStart): la pantalla la ve desde ya y este
                // controlador marca en ella.
                aperturaPropia = MarcasApertura.numero
                publicarApertura()
                if (volvioDeFondo) {
                    volvioDeFondo = false
                    alVolver()
                }
            }
            Lifecycle.Event.ON_RESUME -> {
                aperturaPropia = MarcasApertura.numero
                // Los cuadros marcan solo con la pantalla al frente.
                senales.aperturaPropia = aperturaPropia
                // Si la cámara ya estaba abierta (p. ej. tras un diálogo encima); si no, empieza al abrir.
                iniciarVigilante()
            }
            Lifecycle.Event.ON_PAUSE -> {
                senales.aperturaPropia = 0
                vigilante?.cancel()
            }
            Lifecycle.Event.ON_STOP -> {
                if (ultimoIniciado == id) ultimoIniciado = 0
                volvioDeFondo = true
                vigilante?.cancel()
                vigilanteUsado = false
                // Cada vuelta al frente es otra apertura: los reintentos empiezan de nuevo y el reenlace que estuviera
                // esperando sobra (al volver, CameraX abre otra vez).
                reintento?.cancel()
                reintento = null
                maquina.alDetenerse()
                // CameraX suelta la cámara (y la linterna); al volver, el primer cuadro marca que ya escanea. Un cuadro
                // que ya estaba en camino no debe publicar después de esto.
                analizador.detenido = true
                enAnalisis { analizador.alDetenerse() }
                _estado.update {
                    it.copy(linterna = false, fase = if (it.fase == Fase.PAUSADO) Fase.PAUSADO else Fase.ABRIENDO, cajaCodigo = null)
                }
            }
            else -> Unit
        }
    }

    init {
        // El motor se crea y se calienta en el hilo de análisis mientras la cámara abre (no espera a la cámara).
        enAnalisis { analizador.configurar(configInicial) }
    }

    @MainThread
    override fun abrir(dueno: LifecycleOwner, anchoVisor: Int, altoVisor: Int, rotacionPantalla: Int) {
        val p = Proporcion(anchoVisor, altoVisor)
        if (!p.valida) return
        revivirSiCerrado()
        seguir(dueno)
        pedidoAbrir = true
        // La pantalla ya mide el visor: lo enlazado temprano queda confirmado y la proporción, guardada para la próxima.
        temprano = false
        confirmacion?.cancel()
        memoria.guardarProporcion(pantalla, p)
        val rotacion = rotacionSurface(rotacionPantalla)
        if (!tienePermiso()) {
            pedida = p
            rotacionPedida = rotacion
            _estado.update { it.copy(fase = Fase.SIN_PERMISO) }
            return
        }
        // «Reintentar» tras un error vuelve a enlazar aunque nada haya cambiado, con los reintentos desde cero.
        val forzar = _estado.value.fase == Fase.ERROR
        if (forzar) maquina.reiniciarReintentos()
        pedir(p, rotacion, forzar)
    }

    /**
     * Enlaza la cámara antes de que haya interfaz: desde `onCreate`, con [dueno] = la Activity y la proporción que el
     * visor de [pantalla] tuvo la vez anterior ([estimar] la primera vez: la ventana menos las barras). La cámara abre en
     * `onStart` y la sesión espera la superficie del visor. Si la pantalla luego mide otra proporción, se vuelve a enlazar
     * solo entonces; si en [PLAZO_CONFIRMAR_MS] con la Activity al frente nadie llamó a [abrir] (no se mostró el visor),
     * se suelta. Sin permiso de cámara no hace nada (la pantalla lo pide).
     */
    @MainThread
    fun enlazarTemprano(
        dueno: LifecycleOwner,
        pantalla: String,
        rotacion: Int = Surface.ROTATION_0,
        estimar: () -> Proporcion?,
    ) {
        revivirSiCerrado()
        this.pantalla = pantalla
        // Se llama en el onCreate de la Activity, que ya empezó su apertura: las marcas del enlace van en ella.
        aperturaPropia = MarcasApertura.numero
        val p = memoria.proporcion(pantalla) ?: estimar() ?: return
        if (!p.valida) return
        seguir(dueno)
        pedidoAbrir = true
        temprano = true
        if (!tienePermiso()) {
            pedida = p
            rotacionPedida = rotacionSurface(rotacion)
            return
        }
        pedir(p, rotacionSurface(rotacion), forzar = false)
    }

    @MainThread
    override fun configurar(config: ConfigEscaner) {
        if (cerrado) return
        val vieja = _config.value
        if (vieja == config) return
        _config.value = config
        if (vieja.altoFranja != config.altoFranja) _estado.update { it.copy(franja = franjaPara(config.altoFranja)) }
        enAnalisis { analizador.configurar(config) }
        val cam = camara
        when {
            // El rango de FPS solo cambia al volver a enlazar.
            vieja.fpsFijo30 != config.fpsFijo30 && sesion != null -> {
                sinFpsFijo = false
                enlazar()
            }
            vieja.zoomMaximo != config.zoomMaximo && cam != null -> {
                val (inicial, tope) = zoomesPara(cam.cameraInfo, config)
                enAnalisis { analizador.ajustarZoomBase(inicial, tope) }
            }
        }
    }

    @MainThread
    override fun alternarLinterna() {
        val cam = camara ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return
        cam.cameraControl.enableTorch(cam.cameraInfo.torchState.value != TorchState.ON)
    }

    @MainThread
    override fun reanudar() {
        if (cerrado) return
        enAnalisis { analizador.reanudar() }
        if (apagadaPorInactividad && pedidoAbrir) enlazar()
    }

    @MainThread
    override fun enfocar(x: Float, y: Float) {
        enAnalisis { analizador.enfocarVisible(x, y) }
    }

    @MainThread
    override fun permisoConcedido(concedido: Boolean) {
        if (cerrado) return
        if (!concedido) {
            _estado.update { it.copy(fase = Fase.SIN_PERMISO) }
            return
        }
        if (_estado.value.fase == Fase.SIN_PERMISO) _estado.update { it.copy(fase = Fase.ABRIENDO) }
        val p = pedida
        if (pedidoAbrir && p != null) pedir(p, rotacionPedida, forzar = false)
    }

    /**
     * El visor salió de la pantalla: la solicitud que tomó quedó sin superficie. Se pide otra en cuanto termina este
     * cambio de pantalla, cuando la cámara ya se detuvo (al cambiar de pestaña el visor sale antes de que el ciclo de la
     * pestaña se destruya): con la cámara detenida, CameraX crea la solicitud nueva sin rearmar la sesión, y al volver
     * la cámara abre una sola vez con la superficie del visor nuevo.
     */
    @MainThread
    override fun visorRetirado() {
        if (cerrado) return
        _superficie.value = null
        principal.execute {
            // Volver a poner el proveedor hace que la vista previa (si está enlazada) mande una solicitud nueva.
            if (!cerrado) vista.setSurfaceProvider(principal, proveedorSuperficie)
        }
    }

    @MainThread
    override fun cerrar() {
        if (cerrado) return
        cerrado = true
        pedidoAbrir = false
        temprano = false
        senales.aperturaPropia = 0
        if (ultimoIniciado == id) ultimoIniciado = 0
        for (j in listOf(enlace, reenlaceDiferido, reintento, vigilante, confirmacion, validacionFps)) j?.cancel()
        alcance.cancel()
        camara?.let { cam -> if (cam.cameraInfo.torchState.value == TorchState.ON) cam.cameraControl.enableTorch(false) }
        soltarSesion()
        apagadaPorInactividad = false
        dueno?.lifecycle?.removeObserver(observadorDueno)
        dueno = null
        ciclo?.let {
            it.lifecycle.removeObserver(observadorCiclo)
            it.destruir()
        }
        ciclo = null
        analisis.clearAnalyzer()
        // El motor se cierra en su hilo, después del cuadro en curso; luego el hilo se termina.
        enAnalisis { analizador.liberar() }
        hiloAnalisis.shutdown()
        _superficie.value = null
    }

    // ---- Enlace ----

    @OptIn(ExperimentalCamera2Interop::class)
    private fun crearVista(): Preview {
        val constructor = Preview.Builder().setResolutionSelector(selectorResolucion)
        Camera2Interop.Extender(constructor).setSessionCaptureCallback(senales)
        return constructor.build().also { it.setSurfaceProvider(principal, proveedorSuperficie) }
    }

    /** Tras [cerrar], un nuevo [abrir] vuelve a empezar: otro alcance, el analizador otra vez y el motor (en su hilo). */
    private fun revivirSiCerrado() {
        if (!cerrado) return
        cerrado = false
        volvioDeFondo = false
        maquina.reiniciarReintentos()
        vigilanteUsado = false
        errorDeCamara = false
        errorRecuperable = false
        alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        // cerrar() apagó el hilo: uno nuevo, que primero espera a que el viejo termine su liberar(); si no, el liberar()
        // atrasado cerraría el motor que revivir() acaba de crear.
        val viejo = hiloAnalisis
        hiloAnalisis = nuevoHiloAnalisis()
        enAnalisis {
            viejo.awaitTermination(ESPERA_HILO_VIEJO_S, TimeUnit.SECONDS)
            analizador.revivir()
            analizador.configurar(_config.value)
        }
        analisis.setAnalyzer(hiloAnalisis, analizador)
        _estado.update { it.copy(fase = Fase.ABRIENDO, mensajeError = null, cajaCodigo = null) }
        Log.i(ETIQUETA, "El escáner se vuelve a abrir")
    }

    private fun seguir(nuevo: LifecycleOwner) {
        if (ciclo == null) ciclo = CicloCamara().also { it.lifecycle.addObserver(observadorCiclo) }
        if (dueno === nuevo) return
        dueno?.lifecycle?.removeObserver(observadorDueno)
        dueno = nuevo
        // Al agregarse recibe el estado actual del dueño: el ciclo lo copia en ese momento.
        nuevo.lifecycle.addObserver(observadorDueno)
    }

    private fun pedir(p: Proporcion, rotacion: Int, forzar: Boolean) {
        pedida = p
        rotacionPedida = rotacion
        // Apagada por inactividad: reanudar() enlaza con lo último que se pidió.
        if (apagadaPorInactividad && !forzar) return
        val hay = sesion != null || enlace?.isActive == true
        when (decidirEnlace(hay, enlazada, rotacionEnlazada, p, rotacion, forzar)) {
            DecisionEnlace.NADA -> {
                reenlaceDiferido?.cancel()
                reenlaceDiferido = null
            }
            DecisionEnlace.AHORA -> enlazar()
            DecisionEnlace.DESPUES -> {
                reenlaceDiferido?.cancel()
                reenlaceDiferido = alcance.launch {
                    delay(ESPERA_REENLACE_MS)
                    reenlaceDiferido = null
                    Log.i(ETIQUETA, "El visor cambió de ${enlazada?.texto()} a ${pedida?.texto()}: se vuelve a enlazar")
                    enlazar()
                }
            }
        }
    }

    private fun enlazar() {
        val p = pedida ?: return
        val dueno = ciclo ?: return
        if (cerrado || !p.valida || dueno.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        enlace?.cancel()
        reenlaceDiferido?.cancel()
        reenlaceDiferido = null
        // Un enlace nuevo deja sin sentido el reenlace por error que estuviera esperando.
        reintento?.cancel()
        reintento = null
        val rotacion = rotacionPedida
        enlazada = p
        rotacionEnlazada = rotacion
        errorDeCamara = false
        errorRecuperable = false
        _estado.update { it.copy(fase = Fase.ABRIENDO, mensajeError = null) }
        enlace = alcance.launch {
            try {
                val prov = proveedor ?: esperarProveedor().also { proveedor = it }
                soltarSesion(conservarPedido = true)
                if (!prov.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    _estado.update { it.copy(fase = Fase.ERROR, mensajeError = "Este celular no tiene cámara trasera.") }
                    return@launch
                }
                vista.targetRotation = rotacion
                analisis.targetRotation = rotacion
                // El mismo recorte para el visor y el análisis: FILL_CENTER como el ContentScale.Crop del visor.
                val puerto = ViewPort.Builder(Rational(p.ancho, p.alto), rotacion).setScaleType(ViewPort.FILL_CENTER).build()
                val fijo30 = _config.value.fpsFijo30
                val e = enlazarCon(prov, dueno, puerto, fijo30)
                MarcasApertura.marcarSi(aperturaPropia, PasoApertura.ENLACE_FIN)
                enlazada = p
                rotacionEnlazada = rotacion
                camara = e.camara
                apagadaPorInactividad = false
                enlacesHechos++
                alEnlazar(e.camara, e.rango, e.guardado, p)
                if (debeValidarRangoFps(e.guardado, sinFpsFijo, yaValidado = ValidacionFps.hecha(fijo30))) {
                    validarRangoFps(p, rotacion, fijo30, e.rango)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(ETIQUETA, "No se pudo abrir la cámara", e)
                enlazada = null
                _estado.update { it.copy(fase = Fase.ERROR, mensajeError = "No se pudo abrir la cámara.") }
            }
        }
    }

    /**
     * Espera a `ProcessCameraProvider` (en frío, la inicialización de CameraX que empezó la Application). Antes no caía
     * en ninguna marca: queda en la sección asíncrona [SECCION_ESPERA_PROVEEDOR] (API 29+) y en el registro del enlace.
     */
    private suspend fun esperarProveedor(): ProcessCameraProvider {
        val desde = SystemClock.elapsedRealtimeNanos()
        val cookie = COOKIES_PROVEEDOR.incrementAndGet()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection(SECCION_ESPERA_PROVEEDOR, cookie)
        try {
            return ProcessCameraProvider.awaitInstance(app)
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.endAsyncSection(SECCION_ESPERA_PROVEEDOR, cookie)
            msEsperaProveedor = (SystemClock.elapsedRealtimeNanos() - desde) / 1_000_000L
        }
    }

    /** [guardado]: lo que había en la memoria y se usó ([RangoEnCache.SinDato] si se calculó en este enlace). */
    private class Enlace(val camara: Camera, val rango: Range<Int>?, val guardado: RangoEnCache)

    /**
     * Enlaza con el rango de FPS guardado para este celular (sin preguntarle a la cámara, que en algunos equipos cuesta
     * en el camino de la apertura). Si no hay guardado o la sesión ya no lo acepta, lo calcula y lo guarda.
     */
    private fun enlazarCon(p: ProcessCameraProvider, dueno: LifecycleOwner, puerto: ViewPort, fijo30: Boolean): Enlace {
        val guardado = if (sinFpsFijo) RangoEnCache.AutoPorError else memoria.rangoFps(fijo30)
        if (guardado != RangoEnCache.SinDato) {
            val rango = (guardado as? RangoEnCache.Fijo)?.let { Range(it.minimo, it.maximo) }
            try {
                return Enlace(enlazarSesion(p, dueno, puerto, rango), rango, guardado)
            } catch (e: IllegalArgumentException) {
                Log.w(ETIQUETA, "La sesión ya no acepta el rango de FPS guardado ($rango); se vuelve a calcular", e)
                memoria.olvidarRangoFps(fijo30)
            }
        }
        val rango = calcularRangoFps(p, puerto, fijo30)
        memoria.guardarRangoFps(fijo30, rango?.let { it.lower to it.upper })
        return Enlace(enlazarSesion(p, dueno, puerto, rango), rango, RangoEnCache.SinDato)
    }

    private fun enlazarSesion(p: ProcessCameraProvider, dueno: LifecycleOwner, puerto: ViewPort, rango: Range<Int>?): Camera {
        val nueva = SessionConfig.Builder(vista, analisis).setViewPort(puerto)
            .apply { if (rango != null) setFrameRateRange(rango) }
            .build()
        // El oyente de este enlace va ANTES del bind: al reenlazar, CameraPipe puede reutilizar el dispositivo abierto y
        // publicar OPEN de inmediato; si el oyente llegara después, se perdería (sin vigilante ni enfoque inicial). El
        // valor actual de cameraState NO se procesa: todavía es el del enlace anterior. getCameraInfo da el mismo
        // adaptador de estado que la cámara enlazada.
        val numero = maquina.nuevoEnlace(fpsFijo = rango != null)
        val oyente = Consumer<CameraState> { e -> alCambiarEstado(numero, e) }
        val info = p.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
        info.addCameraStateListener(principal, oyente)
        oyenteEstado = oyente
        infoOyente = info
        MarcasApertura.marcarSi(aperturaPropia, PasoApertura.ENLACE_INICIO)
        Trace.beginSection("escaner.enlazar")
        try {
            return p.bindToLifecycle(dueno, CameraSelector.DEFAULT_BACK_CAMERA, nueva).also { sesion = nueva }
        } catch (e: Exception) {
            info.removeCameraStateListener(oyente)
            oyenteEstado = null
            infoOyente = null
            throw e
        } finally {
            Trace.endSection()
        }
    }

    /** El rango de FPS de entre los que acepta esta sesión ([elegirRangoFps]); null = que decida CameraX. */
    private fun calcularRangoFps(p: ProcessCameraProvider, puerto: ViewPort, fijo30: Boolean): Range<Int>? {
        Trace.beginSection("escaner.rangosFps")
        try {
            val info = p.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
            val base = SessionConfig.Builder(vista, analisis).setViewPort(puerto).build()
            return elegirRangoFps(info.getSupportedFrameRateRanges(base), fijo30)
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Confirma que el rango guardado sigue siendo el que se elegiría; si no, guarda el nuevo para la próxima apertura (no
     * vuelve a enlazar ahora). Una sola vez por proceso y preferencia ([ValidacionFps]), [VALIDAR_FPS_TRAS_MS] después de
     * enlazar y en `Dispatchers.Default` con casos de uso propios sin enlazar (no los de la cámara, que el hilo principal
     * puede estar cambiando): ni la apertura ni el análisis esperan por él. Sección de trace «escaner.validarFps».
     */
    private fun validarRangoFps(visor: Proporcion, rotacion: Int, fijo30: Boolean, usado: Range<Int>?) {
        if (!ValidacionFps.empezar(fijo30)) return
        validacionFps?.cancel()
        validacionFps = alcance.launch {
            var terminada = false
            try {
                delay(VALIDAR_FPS_TRAS_MS)
                val p = proveedor ?: return@launch
                val calculado = withContext(Dispatchers.Default) { calcularRangoFpsAparte(p, visor, rotacion, fijo30) }
                terminada = true
                // Si mientras tanto la sesión rechazó el FPS fijo, lo pegajoso manda.
                if (sinFpsFijo || memoria.rangoFps(fijo30) == RangoEnCache.AutoPorError) return@launch
                val par = calculado?.let { it.lower to it.upper }
                if (par != usado?.let { it.lower to it.upper }) {
                    Log.i(ETIQUETA, "Rango de FPS guardado $usado; la cámara ahora da $calculado: queda para la próxima apertura")
                    memoria.guardarRangoFps(fijo30, par)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No se insiste en este proceso: lo guardado sigue sirviendo.
                terminada = true
                Log.w(ETIQUETA, "No se pudo validar el rango de FPS guardado", e)
            } finally {
                // Cancelada antes de terminar (se volvió a enlazar o se soltó la cámara): el siguiente enlace la repite.
                if (!terminada) ValidacionFps.olvidar(fijo30)
            }
        }
    }

    /** [calcularRangoFps] con un `Preview` y un `ImageAnalysis` propios, iguales a los del enlace pero sin enlazar. */
    private fun calcularRangoFpsAparte(p: ProcessCameraProvider, visor: Proporcion, rotacion: Int, fijo30: Boolean): Range<Int>? {
        Trace.beginSection("escaner.validarFps")
        try {
            val vistaPrueba = Preview.Builder().setResolutionSelector(selectorResolucion).build()
            val analisisPrueba = ImageAnalysis.Builder()
                .setResolutionSelector(selectorResolucion)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
            val puerto = ViewPort.Builder(Rational(visor.ancho, visor.alto), rotacion).setScaleType(ViewPort.FILL_CENTER).build()
            val info = p.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
            val base = SessionConfig.Builder(vistaPrueba, analisisPrueba).setViewPort(puerto).build()
            return elegirRangoFps(info.getSupportedFrameRateRanges(base), fijo30)
        } finally {
            Trace.endSection()
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun alEnlazar(cam: Camera, rango: Range<Int>?, guardado: RangoEnCache, p: Proporcion) {
        val info = cam.cameraInfo
        val (inicial, tope) = zoomesPara(info, _config.value)
        senales.distanciaMinima = caracteristica(info, CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: -1f
        val hayLinterna = info.hasFlashUnit()
        val c = ciclo
        if (c != null) info.torchState.observe(c, observadorLinterna)
        val textoRango = rango?.toString() ?: ""
        _estado.update {
            it.copy(
                hayLinterna = hayLinterna,
                zoom = inicial,
                diagnostico = it.diagnostico.copy(rangoFps = textoRango),
                apertura = MarcasApertura.apertura,
            )
        }
        val origenFps = when (guardado) {
            RangoEnCache.SinDato -> ""
            RangoEnCache.AutoPorError -> " (sin FPS fijo: la sesión lo rechazó)"
            else -> " (guardado)"
        }
        val espera = msEsperaProveedor
        msEsperaProveedor = -1L
        Log.i(
            ETIQUETA,
            "Cámara enlazada: FPS $textoRango$origenFps, visor ${p.texto()}, zoom inicial $inicial (tope $tope), " +
                "linterna $hayLinterna${if (espera >= 0) ", esperó $espera ms a CameraX" else ""}" +
                if (temprano) ", antes de la interfaz" else "",
        )
        enAnalisis { analizador.alEnlazar(inicial, tope, hayLinterna, textoRango) }
        if (temprano) vigilarConfirmacion()
        // El vigilante empieza cuando esta cámara llegue a OPEN (alCambiarEstado), no aquí.
    }

    // ---- Estado de la cámara ----

    /** Un `CameraState` del oyente del enlace [numero] (en el hilo principal): lo decide [maquina] y aquí se aplica. */
    private fun alCambiarEstado(numero: Int, e: CameraState) {
        if (cerrado) return
        val codigo = e.error?.code ?: 0
        // El oyente es por cámara: con el ciclo detenido, o si después empezó el de otro controlador (el escaneo rápido
        // sobre la app, que todavía no se detiene), lo que llegue es de la cámara de otro.
        val activo = ciclo?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true &&
            (ultimoIniciado == id || ultimoIniciado == 0)
        val r = maquina.alEstado(numero, e.type, codigo, activo)
        if (r.accion == AccionEstado.IGNORAR) return
        val texto = textoEstadoCamara(e.type.name, codigo)
        analizador.textoEstadoCamara = texto
        _estado.update { it.copy(diagnostico = it.diagnostico.copy(estadoCamara = texto)) }
        if (codigo != 0) Log.w(ETIQUETA, "Cámara: $texto") else Log.i(ETIQUETA, "Cámara: $texto")
        // Sin la cámara abierta no hay plazo que vigilar (empieza otra vez al abrir).
        if (!maquina.abierta) vigilante?.cancel()
        when (r.accion) {
            AccionEstado.IGNORAR, AccionEstado.NINGUNA -> Unit
            AccionEstado.ABIERTA -> {
                // Abrió bien: el reenlace por error que estuviera esperando sobra.
                reintento?.cancel()
                reintento = null
                if (MarcasApertura.marcarSi(aperturaPropia, PasoApertura.CAMARA_ABIERTA)) publicarApertura()
                errorRecuperable = false
                if (errorDeCamara) {
                    errorDeCamara = false
                    _estado.update { if (it.fase == Fase.ERROR) it.copy(fase = Fase.ABRIENDO, mensajeError = null) else it }
                    enAnalisis { analizador.alReabrir() }
                }
                // Enfoque y exposición al centro (donde está la franja) en cuanto abre: antes de abrir no se puede.
                enfocarEnBuffer(0.5f, 0.5f)
                iniciarVigilante()
            }
            AccionEstado.AVISO_RECUPERABLE -> {
                errorRecuperable = true
                mostrarError(r.mensaje)
            }
            AccionEstado.AVISO, AccionEstado.ERROR_FINAL -> mostrarError(r.mensaje)
            AccionEstado.REENLAZAR_SIN_FPS -> {
                Log.w(ETIQUETA, "La sesión no aceptó el rango de FPS fijo: se enlaza sin él (y así queda para este sistema)")
                sinFpsFijo = true
                memoria.guardarAutoPorError(_config.value.fpsFijo30)
                enlazar()
            }
            AccionEstado.REENLAZAR -> {
                Log.w(ETIQUETA, "Error crítico de la cámara: se vuelve a enlazar en ${r.esperaMs} ms (intento ${maquina.reintentos})")
                reintento?.cancel()
                reintento = alcance.launch {
                    delay(r.esperaMs)
                    reintento = null
                    maquina.sinReintentoPendiente()
                    enlazar()
                }
            }
        }
    }

    private fun mostrarError(mensaje: String?) {
        errorDeCamara = true
        _estado.update { it.copy(fase = Fase.ERROR, mensajeError = mensaje) }
    }

    /**
     * Con la pantalla al frente y la cámara de este enlace abierta (OPEN), si en [PLAZO_VIGILANTE_MS] desde ahí no sale
     * ningún cuadro del sensor, se vuelve a enlazar una vez. Se llama al abrir la cámara y en ON_RESUME (si ya estaba
     * abierta). El contador de cuadros nunca vuelve a cero: la base se lee aquí sin carrera con el hilo de análisis.
     */
    private fun iniciarVigilante() {
        vigilante?.cancel()
        if (vigilanteUsado || sesion == null || cerrado || !maquina.abierta || ciclo?.resumido != true) return
        val enlaceVigilado = maquina.enlace
        val base = senales.producidos.get()
        val desde = SystemClock.elapsedRealtime()
        vigilante = alcance.launch {
            delay(PLAZO_VIGILANTE_MS)
            val reenlazar = vigilanteDebeReenlazar(
                resumida = ciclo?.resumido == true,
                abierta = maquina.abierta && maquina.enlace == enlaceVigilado && sesion != null,
                cuadrosNuevos = senales.producidos.get() - base,
                msDesdeAbierta = SystemClock.elapsedRealtime() - desde,
                yaReenlazo = vigilanteUsado,
                enPausa = _estado.value.fase == Fase.PAUSADO,
                errorRecuperable = errorRecuperable || errorDeCamara,
            )
            if (reenlazar) {
                vigilanteUsado = true
                Log.w(ETIQUETA, "Cámara abierta y sin cuadros en $PLAZO_VIGILANTE_MS ms con la pantalla al frente: se vuelve a enlazar")
                enlazar()
            }
        }
    }

    /** Lo enlazado desde `onCreate` se suelta si la pantalla del escáner no lo confirma ([abrir]) al quedar al frente. */
    private fun vigilarConfirmacion() {
        confirmacion?.cancel()
        val c = ciclo ?: return
        confirmacion = alcance.launch {
            c.lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {}
            delay(PLAZO_CONFIRMAR_MS)
            if (temprano && !cerrado) {
                Log.i(ETIQUETA, "Nadie mostró el visor: se suelta la cámara enlazada al arrancar")
                temprano = false
                pedidoAbrir = false
                soltarSesion()
                _superficie.value = null
            }
        }
    }

    /** Volvió de segundo plano: a escanear de inmediato aunque se hubiera quedado en pausa o apagada por inactividad. */
    private fun alVolver() {
        if (!pedidoAbrir) return
        if (_estado.value.fase == Fase.PAUSADO || apagadaPorInactividad) {
            Log.i(ETIQUETA, "De vuelta al frente: sale de la pausa")
            reanudar()
        }
    }

    private fun publicarApertura() {
        val a = MarcasApertura.apertura
        _estado.update { if (it.apertura === a) it else it.copy(apertura = a) }
    }

    /** Desenlaza lo de este controlador y quita sus observadores (lo que llegue después de ese enlace ya no cuenta). */
    private fun soltarSesion(conservarPedido: Boolean = false) {
        camara?.let { cam -> cam.cameraInfo.torchState.removeObserver(observadorLinterna) }
        oyenteEstado?.let { o -> infoOyente?.removeCameraStateListener(o) }
        oyenteEstado = null
        infoOyente = null
        maquina.soltar()
        sesion?.let { s -> proveedor?.unbind(s) }
        sesion = null
        camara = null
        if (!conservarPedido) {
            enlazada = null
            rotacionEnlazada = -1
        }
        vigilante?.cancel()
        validacionFps?.cancel()
        // Lo soltado ya no se reintenta (p. ej. un error que llegó justo antes de apagar por inactividad).
        reintento?.cancel()
        reintento = null
    }

    /** El zoom inicial (según la distancia mínima de enfoque) y el tope propio, dentro de lo que la cámara permite. */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun zoomesPara(info: CameraInfo, config: ConfigEscaner): Pair<Float, Float> {
        val zs = info.zoomState.value
        val minimo = zs?.minZoomRatio ?: 1f
        val maximo = zs?.maxZoomRatio ?: caracteristica(info, CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        val tope = min(config.zoomMaximo, maximo).coerceAtLeast(minimo)
        val calibracion = caracteristica(info, CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)
        val inicial = zoomSugerido(
            dioptriasMinimas = caracteristica(info, CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE),
            anchoSensorMm = caracteristica(info, CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width,
            focalMm = caracteristica(info, CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull(),
            calibrado = calibracion != null &&
                calibracion != CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED,
            tope = tope,
        ).coerceIn(minimo, tope)
        return inicial to tope
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun <T> caracteristica(info: CameraInfo, llave: CameraCharacteristics.Key<T>): T? = try {
        Camera2CameraInfo.from(info).getCameraCharacteristic(llave)
    } catch (e: Exception) {
        null
    }

    /** AF + AE en un punto del buffer de análisis (0 a 1, sin rotar); se cancela solo a los 2 s y vuelve el AF continuo. */
    private fun enfocarEnBuffer(x: Float, y: Float) {
        val cam = camara ?: return
        try {
            val punto = SurfaceOrientedMeteringPointFactory(1f, 1f, analisis).createPoint(x, y, TAMANO_PUNTO)
            // FLAG_AF deja el foco fijo hasta que se cancele: por eso el auto-cancel y nunca disableAutoCancel().
            val accion = FocusMeteringAction.Builder(punto, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(2, TimeUnit.SECONDS)
                .build()
            if (cam.cameraInfo.isFocusMeteringSupported(accion)) cam.cameraControl.startFocusAndMetering(accion)
        } catch (e: IllegalStateException) {
            // El caso de uso ya no está enlazado (se cerró mientras llegaba la orden).
        }
    }

    /** Inactividad larga: suelta la cámara; [reanudar] la vuelve a abrir. */
    private fun apagar() {
        if (sesion == null) return
        camara?.let { cam -> if (cam.cameraInfo.torchState.value == TorchState.ON) cam.cameraControl.enableTorch(false) }
        soltarSesion()
        apagadaPorInactividad = true
        _superficie.value = null
        _estado.update { it.copy(linterna = false) }
        Log.i(ETIQUETA, "Cámara apagada por inactividad")
    }

    private fun tienePermiso() =
        ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Corre en el hilo de análisis, entre cuadros; un error ahí no debe tumbar la app. */
    private fun enAnalisis(bloque: () -> Unit) {
        try {
            hiloAnalisis.execute {
                try {
                    bloque()
                } catch (e: Throwable) {
                    if (e is VirtualMachineError) throw e
                    Log.e(ETIQUETA, "Falló una orden en el hilo de análisis", e)
                    _estado.update { it.copy(fase = Fase.ERROR, mensajeError = "No se pudo preparar el lector de códigos.") }
                }
            }
        } catch (_: RejectedExecutionException) {
            // Ya se cerró.
        }
    }

    private fun enPrincipal(bloque: () -> Unit) {
        principal.execute {
            if (!cerrado) {
                try {
                    bloque()
                } catch (e: Exception) {
                    Log.w(ETIQUETA, "Falló una orden a la cámara", e)
                }
            }
        }
    }

    private fun nuevoHiloAnalisis(): ExecutorService =
        ThreadPoolExecutor(0, 1, 10L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>()) { Thread(it, "escaner-analisis") }

    companion object {
        /** Lo más que espera el hilo nuevo a que el viejo suelte el motor al revivir (un cuadro tarda ~100 ms). */
        private const val ESPERA_HILO_VIEJO_S = 2L

        /** La pantalla principal de la app (con pestañas): su visor no ocupa toda la ventana. */
        const val PANTALLA_PRINCIPAL = "principal"
        /** El escaneo rápido (sobre la pantalla de bloqueo): visor a pantalla completa. */
        const val PANTALLA_RAPIDA = "rapida"

        /**
         * Lo que se espera a que el tamaño del visor se asiente antes de volver a enlazar con otra proporción: en gama
         * baja el segundo layout (ya con las barras) puede tardar más de un cuadro. Mientras, la cámara sigue con el
         * recorte anterior.
         */
        const val ESPERA_REENLACE_MS = 300L
        /** Con la Activity al frente, lo que se espera a que la pantalla confirme lo enlazado en `onCreate`. */
        const val PLAZO_CONFIRMAR_MS = 3_000L
        /** Cuándo se valida el rango de FPS guardado (con la cámara ya escaneando, fuera del camino crítico). */
        const val VALIDAR_FPS_TRAS_MS = 3_000L
        /** La sección asíncrona de trace de la espera a `ProcessCameraProvider` al enlazar (API 29+). */
        const val SECCION_ESPERA_PROVEEDOR = "escaner.esperaProveedor"

        private const val ETIQUETA = "Escaner"
        /** Tamaño del punto de enfoque, como fracción del campo: lo que ocupa un código en la franja. */
        private const val TAMANO_PUNTO = 0.2f
        /** Cookies de [SECCION_ESPERA_PROVEEDOR]: dos controladores pueden esperar a la vez. */
        private val COOKIES_PROVEEDOR = AtomicInteger()
        private val SIGUIENTE_ID = AtomicInteger()

        /**
         * El controlador cuyo ciclo empezó al último y sigue sin detenerse (0 = ninguno). Como CameraX (el último ciclo
         * que empieza se queda con la cámara), así el de la app, en pausa debajo del escaneo rápido, no reacciona a los
         * estados de la cámara del otro. Solo en el hilo principal.
         */
        private var ultimoIniciado = 0
    }
}

/**
 * La validación del rango de FPS guardado se hace una sola vez por proceso y por preferencia (30 fijo o 15–30); la
 * huella del sistema no cambia sin reiniciar el proceso. Es del proceso, no del controlador: la app y el escaneo rápido
 * la comparten.
 */
internal object ValidacionFps {
    private val fijo30 = AtomicBoolean()
    private val de15a30 = AtomicBoolean()

    private fun de(fijo: Boolean) = if (fijo) fijo30 else de15a30

    fun hecha(fijo: Boolean): Boolean = de(fijo).get()

    /** Devuelve false si ya se hizo o ya está en curso (la toma quien llega primero). */
    fun empezar(fijo: Boolean): Boolean = de(fijo).compareAndSet(false, true)

    /** No terminó (se canceló): el siguiente enlace la vuelve a intentar. */
    fun olvidar(fijo: Boolean) = de(fijo).set(false)
}
