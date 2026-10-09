package mx.papeleriapos.escaner.camara

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Rational
import android.util.Size
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * El escáner con la cámara real (docs/escaner.md, «Cámara»): Preview + ImageAnalysis a 1280x720 en una sola
 * `SessionConfig` con el `ViewPort` del visor (así `ImageProxy.cropRect` es justo lo que se ve), FPS elegidos de los que
 * acepta la sesión, zoom inicial según la distancia mínima de enfoque, enfoque al abrir, al tocar y cuando un código no
 * se deja leer, linterna, y pausa por inactividad.
 *
 * Se usa desde el hilo principal. El análisis corre en un hilo propio ([Analizador]).
 */
class ControladorCamara(
    contexto: Context,
    configInicial: ConfigEscaner = ConfigEscaner(),
) : ControladorEscaner {
    private val app = contexto.applicationContext

    private val _config = MutableStateFlow(configInicial)
    private val _estado = MutableStateFlow(
        EstadoEscaner(franja = franjaPara(configInicial.altoFranja), diagnostico = Diagnostico(motor = configInicial.motor)),
    )
    private val _eventos = MutableSharedFlow<EventoLectura>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _superficie = MutableStateFlow<SurfaceRequest?>(null)

    override val estado: StateFlow<EstadoEscaner> = _estado.asStateFlow()
    override val eventos: SharedFlow<EventoLectura> = _eventos.asSharedFlow()
    override val superficie: StateFlow<SurfaceRequest?> = _superficie.asStateFlow()
    override val config: StateFlow<ConfigEscaner> = _config.asStateFlow()

    private val principal = ContextCompat.getMainExecutor(app)
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hiloAnalisis: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "escaner-analisis") }
    private val senales = SenalesCamara()

    @Volatile private var camara: Camera? = null
    private var proveedor: ProcessCameraProvider? = null
    private var sesion: SessionConfig? = null
    private var dueno: LifecycleOwner? = null
    private var anchoVisor = 0
    private var altoVisor = 0
    private var rotacionPantalla = 0
    /** Ya pidieron abrir (y no se ha cerrado): con el permiso o al reanudar se enlaza. */
    private var pedidoAbrir = false
    private var apagadaPorInactividad = false
    private var cerrado = false
    private var enlace: Job? = null

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

    private val observadorCiclo = LifecycleEventObserver { _, evento ->
        when (evento) {
            Lifecycle.Event.ON_STOP -> {
                // CameraX suelta la cámara (y la linterna); al volver, el primer cuadro marca que ya escanea.
                enAnalisis { analizador.alDetenerse() }
                _estado.update {
                    it.copy(linterna = false, fase = if (it.fase == Fase.PAUSADO) Fase.PAUSADO else Fase.ABRIENDO, cajaCodigo = null)
                }
            }
            Lifecycle.Event.ON_DESTROY -> {
                // CameraX ya desenlazó lo de este dueño.
                camara = null
                sesion = null
                dueno = null
                _superficie.value = null
            }
            else -> Unit
        }
    }

    init {
        // El motor se crea y se calienta en el hilo de análisis mientras la cámara abre.
        enAnalisis { analizador.configurar(configInicial) }
    }

    @MainThread
    override fun abrir(dueno: LifecycleOwner, anchoVisor: Int, altoVisor: Int, rotacionPantalla: Int) {
        if (cerrado) return
        this.anchoVisor = anchoVisor
        this.altoVisor = altoVisor
        this.rotacionPantalla = rotacionPantalla
        pedidoAbrir = true
        if (this.dueno !== dueno) {
            this.dueno?.lifecycle?.removeObserver(observadorCiclo)
            this.dueno = dueno
            dueno.lifecycle.addObserver(observadorCiclo)
        }
        if (!tienePermiso()) {
            _estado.update { it.copy(fase = Fase.SIN_PERMISO) }
            return
        }
        enlazar()
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
            vieja.fpsFijo30 != config.fpsFijo30 && cam != null -> enlazar()
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
        if (pedidoAbrir && camara == null) enlazar()
    }

    @MainThread
    override fun cerrar() {
        if (cerrado) return
        cerrado = true
        pedidoAbrir = false
        enlace?.cancel()
        alcance.cancel()
        camara?.let { cam ->
            if (cam.cameraInfo.torchState.value == TorchState.ON) cam.cameraControl.enableTorch(false)
            cam.cameraInfo.torchState.removeObserver(observadorLinterna)
        }
        sesion?.let { s -> proveedor?.unbind(s) }
        camara = null
        sesion = null
        dueno?.lifecycle?.removeObserver(observadorCiclo)
        dueno = null
        analisis.clearAnalyzer()
        // El motor se cierra en su hilo, después del cuadro en curso.
        hiloAnalisis.execute { analizador.liberar() }
        hiloAnalisis.shutdown()
        _superficie.value = null
    }

    // ---- Enlace ----

    @OptIn(ExperimentalCamera2Interop::class)
    private fun crearVista(): Preview {
        val constructor = Preview.Builder().setResolutionSelector(selectorResolucion)
        Camera2Interop.Extender(constructor).setSessionCaptureCallback(senales)
        return constructor.build().also { it.setSurfaceProvider(principal) { solicitud -> _superficie.value = solicitud } }
    }

    private fun enlazar() {
        val dueno = dueno ?: return
        if (cerrado || anchoVisor <= 0 || altoVisor <= 0) return
        if (dueno.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        enlace?.cancel()
        _estado.update { it.copy(fase = Fase.ABRIENDO, mensajeError = null) }
        enlace = alcance.launch {
            try {
                val p = proveedor ?: ProcessCameraProvider.awaitInstance(app).also { proveedor = it }
                sesion?.let { p.unbind(it) }
                sesion = null
                camara?.cameraInfo?.torchState?.removeObserver(observadorLinterna)
                camara = null
                if (!p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    _estado.update { it.copy(fase = Fase.ERROR, mensajeError = "Este celular no tiene cámara trasera.") }
                    return@launch
                }
                val rotacion = rotacionSurface(rotacionPantalla)
                vista.targetRotation = rotacion
                analisis.targetRotation = rotacion
                // El mismo recorte para el visor y el análisis: FILL_CENTER como el ContentScale.Crop del visor.
                val puerto = ViewPort.Builder(Rational(anchoVisor, altoVisor), rotacion).setScaleType(ViewPort.FILL_CENTER).build()
                val info = p.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
                val base = SessionConfig.Builder(vista, analisis).setViewPort(puerto).build()
                val rango = elegirRangoFps(info.getSupportedFrameRateRanges(base), _config.value.fpsFijo30)
                val nueva = SessionConfig.Builder(vista, analisis).setViewPort(puerto)
                    .apply { if (rango != null) setFrameRateRange(rango) }
                    .build()
                val cam = p.bindToLifecycle(dueno, CameraSelector.DEFAULT_BACK_CAMERA, nueva)
                sesion = nueva
                camara = cam
                apagadaPorInactividad = false
                alEnlazar(dueno, cam, rango?.toString() ?: "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(ETIQUETA, "No se pudo abrir la cámara", e)
                _estado.update { it.copy(fase = Fase.ERROR, mensajeError = "No se pudo abrir la cámara.") }
            }
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun alEnlazar(dueno: LifecycleOwner, cam: Camera, rangoFps: String) {
        val info = cam.cameraInfo
        val (inicial, tope) = zoomesPara(info, _config.value)
        senales.distanciaMinima = caracteristica(info, CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: -1f
        val hayLinterna = info.hasFlashUnit()
        info.torchState.observe(dueno, observadorLinterna)
        _estado.update {
            it.copy(
                hayLinterna = hayLinterna,
                zoom = inicial,
                diagnostico = it.diagnostico.copy(rangoFps = rangoFps),
            )
        }
        Log.i(ETIQUETA, "Cámara enlazada: FPS $rangoFps, zoom inicial $inicial (tope $tope), linterna $hayLinterna")
        enAnalisis { analizador.alEnlazar(inicial, tope, hayLinterna, rangoFps) }
        // Enfoque y exposición al centro (donde está la franja) en cuanto abre.
        enfocarEnBuffer(0.5f, 0.5f)
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
        val s = sesion ?: return
        camara?.let { cam ->
            if (cam.cameraInfo.torchState.value == TorchState.ON) cam.cameraControl.enableTorch(false)
            cam.cameraInfo.torchState.removeObserver(observadorLinterna)
        }
        proveedor?.unbind(s)
        sesion = null
        camara = null
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

    private companion object {
        const val ETIQUETA = "Escaner"
        /** Tamaño del punto de enfoque, como fracción del campo: lo que ocupa un código en la franja. */
        const val TAMANO_PUNTO = 0.2f
    }
}
