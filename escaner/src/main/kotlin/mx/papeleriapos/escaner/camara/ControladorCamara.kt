package mx.papeleriapos.escaner.camara

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
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
 * Observa el `CameraState` tras cada enlace ([reaccionEstado]): otra app con la cámara, desactivada, configuración no
 * aceptada (sin FPS fijo) y errores críticos (hasta 2 reintentos); y un vigilante vuelve a enlazar una vez si con la
 * pantalla al frente no sale ningún cuadro en 2 s.
 *
 * [cerrar] suelta la cámara y el motor; un [abrir] posterior vuelve a empezar (la pantalla llama a cerrar al salir de la
 * composición, p. ej. al cambiar de pestaña o al recrearse la Activity).
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

    override val estado: StateFlow<EstadoEscaner> = _estado.asStateFlow()
    override val eventos: SharedFlow<EventoLectura> = _eventos.asSharedFlow()
    override val superficie: StateFlow<SurfaceRequest?> = _superficie.asStateFlow()
    override val config: StateFlow<ConfigEscaner> = _config.asStateFlow()

    private val principal = ContextCompat.getMainExecutor(app)
    private var alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Un solo hilo de análisis (los cuadros y las órdenes, en orden). Sin trabajo se termina a los 10 s y el siguiente
     * encargo crea otro: así [cerrar] no tiene que apagarlo para siempre y un [abrir] posterior lo vuelve a usar.
     */
    private val hiloAnalisis: ExecutorService = ThreadPoolExecutor(
        0, 1, 10L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(),
    ) { Thread(it, "escaner-analisis") }
    private val senales = SenalesCamara()

    @Volatile private var camara: Camera? = null
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
    private var reintentosCriticos = 0
    /** La sesión no aceptó un rango de FPS fijo (STREAM_CONFIG): desde ahí, sin rango. */
    private var sinFpsFijo = false
    private var fpsFijoEnlazado = false
    /** La fase ERROR la puso el estado de la cámara (y se quita sola al abrir). */
    private var errorDeCamara = false
    /** CameraX está reintentando por su cuenta (otra app tiene la cámara): el vigilante no se mete. */
    private var errorRecuperable = false
    private var vigilanteUsado = false
    private var ultimoEstado = -1

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

    private val observadorEstado = Consumer<CameraState> { alCambiarEstado(it) }

    /** Copia el estado del dueño al ciclo de la cámara. */
    private val observadorDueno = LifecycleEventObserver { fuente, _ ->
        if (fuente === dueno) ciclo?.seguir(fuente.lifecycle.currentState)
    }

    private val observadorCiclo = LifecycleEventObserver { _, evento ->
        when (evento) {
            Lifecycle.Event.ON_START -> {
                analizador.detenido = false
                // La app ya empezó la apertura (MarcasApertura.nueva en onStart): la pantalla la ve desde ya.
                publicarApertura()
                if (volvioDeFondo) {
                    volvioDeFondo = false
                    alVolver()
                }
            }
            Lifecycle.Event.ON_RESUME -> iniciarVigilante()
            Lifecycle.Event.ON_STOP -> {
                volvioDeFondo = true
                vigilante?.cancel()
                vigilanteUsado = false
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
        // «Reintentar» tras un error vuelve a enlazar aunque nada haya cambiado.
        pedir(p, rotacion, forzar = _estado.value.fase == Fase.ERROR)
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

    @MainThread
    override fun cerrar() {
        if (cerrado) return
        cerrado = true
        pedidoAbrir = false
        temprano = false
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
        // El motor se cierra en su hilo, después del cuadro en curso.
        enAnalisis { analizador.liberar() }
        _superficie.value = null
    }

    // ---- Enlace ----

    @OptIn(ExperimentalCamera2Interop::class)
    private fun crearVista(): Preview {
        val constructor = Preview.Builder().setResolutionSelector(selectorResolucion)
        Camera2Interop.Extender(constructor).setSessionCaptureCallback(senales)
        return constructor.build().also { it.setSurfaceProvider(principal) { solicitud -> _superficie.value = solicitud } }
    }

    /** Tras [cerrar], un nuevo [abrir] vuelve a empezar: otro alcance, el analizador otra vez y el motor (en su hilo). */
    private fun revivirSiCerrado() {
        if (!cerrado) return
        cerrado = false
        volvioDeFondo = false
        reintentosCriticos = 0
        vigilanteUsado = false
        errorDeCamara = false
        errorRecuperable = false
        alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        // Por el mismo ejecutor: va después del liberar() que dejó cerrar().
        enAnalisis {
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
        val rotacion = rotacionPedida
        enlazada = p
        rotacionEnlazada = rotacion
        errorDeCamara = false
        errorRecuperable = false
        _estado.update { it.copy(fase = Fase.ABRIENDO, mensajeError = null) }
        enlace = alcance.launch {
            try {
                val prov = proveedor ?: ProcessCameraProvider.awaitInstance(app).also { proveedor = it }
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
                MarcasApertura.marcar(PasoApertura.ENLACE_FIN)
                enlazada = p
                rotacionEnlazada = rotacion
                camara = e.camara
                apagadaPorInactividad = false
                enlacesHechos++
                alEnlazar(e.camara, e.rango, e.desdeMemoria, p)
                if (e.desdeMemoria && !sinFpsFijo) validarRangoFps(puerto, fijo30, e.rango)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(ETIQUETA, "No se pudo abrir la cámara", e)
                enlazada = null
                _estado.update { it.copy(fase = Fase.ERROR, mensajeError = "No se pudo abrir la cámara.") }
            }
        }
    }

    private class Enlace(val camara: Camera, val rango: Range<Int>?, val desdeMemoria: Boolean)

    /**
     * Enlaza con el rango de FPS guardado para este celular (sin preguntarle a la cámara, que en algunos equipos cuesta
     * en el camino de la apertura). Si no hay guardado o la sesión ya no lo acepta, lo calcula y lo guarda.
     */
    private fun enlazarCon(p: ProcessCameraProvider, dueno: LifecycleOwner, puerto: ViewPort, fijo30: Boolean): Enlace {
        val guardado = if (sinFpsFijo) RangoEnCache.Auto else memoria.rangoFps(fijo30)
        if (guardado != RangoEnCache.SinDato) {
            val rango = (guardado as? RangoEnCache.Fijo)?.let { Range(it.minimo, it.maximo) }
            try {
                return Enlace(enlazarSesion(p, dueno, puerto, rango), rango, desdeMemoria = true)
            } catch (e: IllegalArgumentException) {
                Log.w(ETIQUETA, "La sesión ya no acepta el rango de FPS guardado ($rango); se vuelve a calcular", e)
                memoria.olvidarRangoFps(fijo30)
            }
        }
        val rango = calcularRangoFps(p, puerto, fijo30)
        memoria.guardarRangoFps(fijo30, rango?.let { it.lower to it.upper })
        return Enlace(enlazarSesion(p, dueno, puerto, rango), rango, desdeMemoria = false)
    }

    private fun enlazarSesion(p: ProcessCameraProvider, dueno: LifecycleOwner, puerto: ViewPort, rango: Range<Int>?): Camera {
        val nueva = SessionConfig.Builder(vista, analisis).setViewPort(puerto)
            .apply { if (rango != null) setFrameRateRange(rango) }
            .build()
        MarcasApertura.marcar(PasoApertura.ENLACE_INICIO)
        Trace.beginSection("escaner.enlazar")
        try {
            return p.bindToLifecycle(dueno, CameraSelector.DEFAULT_BACK_CAMERA, nueva).also { sesion = nueva }
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
     * Con la cámara ya escaneando, confirma que el rango guardado sigue siendo el que se elegiría; si no, guarda el nuevo
     * para la próxima apertura (no vuelve a enlazar ahora).
     */
    private fun validarRangoFps(puerto: ViewPort, fijo30: Boolean, usado: Range<Int>?) {
        validacionFps?.cancel()
        validacionFps = alcance.launch {
            delay(VALIDAR_FPS_TRAS_MS)
            val p = proveedor ?: return@launch
            val calculado = try {
                calcularRangoFps(p, puerto, fijo30)
            } catch (e: Exception) {
                Log.w(ETIQUETA, "No se pudo validar el rango de FPS guardado", e)
                return@launch
            }
            val par = calculado?.let { it.lower to it.upper }
            if (par != usado?.let { it.lower to it.upper }) {
                Log.i(ETIQUETA, "Rango de FPS guardado $usado; la cámara ahora da $calculado: queda para la próxima apertura")
                memoria.guardarRangoFps(fijo30, par)
            }
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun alEnlazar(cam: Camera, rango: Range<Int>?, desdeMemoria: Boolean, p: Proporcion) {
        val info = cam.cameraInfo
        val (inicial, tope) = zoomesPara(info, _config.value)
        senales.distanciaMinima = caracteristica(info, CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: -1f
        val hayLinterna = info.hasFlashUnit()
        val c = ciclo
        if (c != null) info.torchState.observe(c, observadorLinterna)
        fpsFijoEnlazado = rango != null
        val textoRango = rango?.toString() ?: ""
        _estado.update {
            it.copy(
                hayLinterna = hayLinterna,
                zoom = inicial,
                diagnostico = it.diagnostico.copy(rangoFps = textoRango),
                apertura = MarcasApertura.apertura,
            )
        }
        Log.i(
            ETIQUETA,
            "Cámara enlazada: FPS $textoRango${if (desdeMemoria) " (guardado)" else ""}, visor ${p.texto()}, " +
                "zoom inicial $inicial (tope $tope), linterna $hayLinterna${if (temprano) ", antes de la interfaz" else ""}",
        )
        enAnalisis { analizador.alEnlazar(inicial, tope, hayLinterna, textoRango) }
        // El estado de la cámara de este enlace (el primero que llegue se procesa aunque repita el anterior).
        ultimoEstado = -1
        info.addCameraStateListener(principal, observadorEstado)
        info.cameraState.value?.let { alCambiarEstado(it) }
        if (temprano) vigilarConfirmacion()
        if (c?.resumido == true) iniciarVigilante()
    }

    // ---- Estado de la cámara ----

    private fun alCambiarEstado(e: CameraState) {
        if (cerrado) return
        val codigo = e.error?.code ?: 0
        val clave = e.type.ordinal * 100 + codigo
        if (clave == ultimoEstado) return
        ultimoEstado = clave
        val texto = textoEstadoCamara(e.type.name, codigo)
        analizador.textoEstadoCamara = texto
        _estado.update { it.copy(diagnostico = it.diagnostico.copy(estadoCamara = texto)) }
        if (codigo != 0) Log.w(ETIQUETA, "Cámara: $texto") else Log.i(ETIQUETA, "Cámara: $texto")
        val abierta = e.type == CameraState.Type.OPEN
        if (abierta && MarcasApertura.marcar(PasoApertura.CAMARA_ABIERTA)) publicarApertura()
        val r = reaccionEstado(abierta, codigo, reintentosCriticos, fpsFijoEnlazado)
        when (r.accion) {
            AccionEstado.NINGUNA -> Unit
            AccionEstado.ABIERTA -> {
                reintentosCriticos = 0
                errorRecuperable = false
                if (errorDeCamara) {
                    errorDeCamara = false
                    _estado.update { if (it.fase == Fase.ERROR) it.copy(fase = Fase.ABRIENDO, mensajeError = null) else it }
                    enAnalisis { analizador.alReabrir() }
                }
                // Enfoque y exposición al centro (donde está la franja) en cuanto abre: antes de abrir no se puede.
                enfocarEnBuffer(0.5f, 0.5f)
            }
            AccionEstado.AVISO_RECUPERABLE -> {
                errorRecuperable = true
                mostrarError(r.mensaje)
            }
            AccionEstado.AVISO, AccionEstado.ERROR_FINAL -> mostrarError(r.mensaje)
            AccionEstado.REENLAZAR_SIN_FPS -> {
                Log.w(ETIQUETA, "La sesión no aceptó el rango de FPS fijo: se enlaza sin él")
                sinFpsFijo = true
                memoria.guardarRangoFps(_config.value.fpsFijo30, null)
                enlazar()
            }
            AccionEstado.REENLAZAR -> if (reintento?.isActive != true) {
                reintentosCriticos++
                Log.w(ETIQUETA, "Error crítico de la cámara: se vuelve a enlazar en ${r.esperaMs} ms (intento $reintentosCriticos)")
                reintento = alcance.launch {
                    delay(r.esperaMs)
                    reintento = null
                    enlazar()
                }
            }
        }
    }

    private fun mostrarError(mensaje: String?) {
        errorDeCamara = true
        _estado.update { it.copy(fase = Fase.ERROR, mensajeError = mensaje) }
    }

    /** Con la pantalla al frente y sin cuadros del sensor en [PLAZO_VIGILANTE_MS], se vuelve a enlazar una vez. */
    private fun iniciarVigilante() {
        vigilante?.cancel()
        if (vigilanteUsado || sesion == null || cerrado) return
        val base = senales.producidos.get()
        val desde = SystemClock.elapsedRealtime()
        vigilante = alcance.launch {
            delay(PLAZO_VIGILANTE_MS)
            val actual = senales.producidos.get()
            // Si en medio se volvió a enlazar, el contador empezó de cero.
            val nuevos = if (actual >= base) actual - base else actual
            val reenlazar = vigilanteDebeReenlazar(
                resumida = ciclo?.resumido == true,
                enlazada = sesion != null,
                cuadrosNuevos = nuevos,
                msSinCuadros = SystemClock.elapsedRealtime() - desde,
                yaReenlazo = vigilanteUsado,
                enPausa = _estado.value.fase == Fase.PAUSADO,
                errorRecuperable = errorRecuperable || errorDeCamara,
            )
            if (reenlazar) {
                vigilanteUsado = true
                Log.w(ETIQUETA, "Sin cuadros de la cámara en $PLAZO_VIGILANTE_MS ms con la pantalla al frente: se vuelve a enlazar")
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

    /** Desenlaza lo de este controlador y quita sus observadores. */
    private fun soltarSesion(conservarPedido: Boolean = false) {
        camara?.let { cam ->
            cam.cameraInfo.torchState.removeObserver(observadorLinterna)
            cam.cameraInfo.removeCameraStateListener(observadorEstado)
        }
        sesion?.let { s -> proveedor?.unbind(s) }
        sesion = null
        camara = null
        if (!conservarPedido) {
            enlazada = null
            rotacionEnlazada = -1
        }
        vigilante?.cancel()
        validacionFps?.cancel()
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

    companion object {
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

        private const val ETIQUETA = "Escaner"
        /** Tamaño del punto de enfoque, como fracción del campo: lo que ocupa un código en la franja. */
        private const val TAMANO_PUNTO = 0.2f
    }
}
