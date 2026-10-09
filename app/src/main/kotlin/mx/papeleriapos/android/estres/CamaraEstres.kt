package mx.papeleriapos.android.estres

import android.content.Context
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.annotation.MainThread
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mx.papeleriapos.android.fabrica.crearControlador
import mx.papeleriapos.android.medicion.MonitorRecursos
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.Diagnostico
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.Motor

/** Un ciclo de vida propio para la prueba: la cámara se abre y se cierra moviéndolo, sin depender de la pantalla. */
class DuenoEstres : LifecycleOwner {
    private val registro = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registro

    @MainThread
    fun a(estado: Lifecycle.State) {
        if (registro.currentState == Lifecycle.State.DESTROYED) return
        if (registro.currentState == Lifecycle.State.INITIALIZED && estado == Lifecycle.State.DESTROYED) {
            registro.currentState = Lifecycle.State.CREATED
        }
        registro.currentState = estado
    }
}

/**
 * Lo delgado de las pruebas de estrés: un controlador propio ([crearControlador]) con su propio [DuenoEstres] y un
 * `ImageReader` como visor (sin pantalla: CameraX no arranca la sesión si nadie consume la vista previa). El estado de
 * la cámara (OPEN, CLOSED, errores), el zoom y la linterna reales se leen del `CameraInfo` de CameraX.
 *
 * Se usa desde el hilo principal.
 *
 * @param nuevoPorCiclo true = cada ciclo crea y cierra un controlador (prueba fugas de hilos, motores y descriptores);
 *   false = un solo controlador y el ciclo de vida va de CREATED a RESUMED (como ir y volver de segundo plano).
 */
class CamaraEstres(
    contexto: Context,
    private val alcance: CoroutineScope,
    private val nuevoPorCiclo: Boolean = true,
    motorInicial: Motor = Motor.AUTOMATICO,
) : CamaraBajoPrueba, ControlesBajoPrueba, MotorBajoPrueba {
    private val app = contexto.applicationContext
    private val dueno = DuenoEstres()
    private var controlador: ControladorEscaner? = null
    private var config = ConfigEscaner(
        motor = motorInicial,
        // Que no se pause ni se apague sola a media prueba, y sin bip ni vibración.
        segundosParaPausar = 0,
        segundosParaApagar = 0,
        sonido = false,
        vibracion = false,
    )
    private val hiloVisor = HandlerThread("estres-visor").also { it.start() }
    private var trabajos = ArrayList<Job>()
    private var info: CameraInfo? = null
    private val estadoCamara = MutableStateFlow<CameraState?>(null)
    private val observador = Observer<CameraState> { estadoCamara.value = it }
    @Volatile private var actualizaciones = 0L
    private val zoomBase = ConfigEscaner().zoomMaximo
    private val ancho = app.resources.displayMetrics.widthPixels.coerceAtLeast(720)
    private val alto = app.resources.displayMetrics.heightPixels.coerceAtLeast(1280)

    override var observaEstado: Boolean = true
        private set

    val diagnostico: Diagnostico? get() = controlador?.estado?.value?.diagnostico

    /** Prepara el proveedor de CameraX y deja el controlador listo (abierto en el modo de segundo plano). */
    suspend fun preparar() {
        val p = ProcessCameraProvider.awaitInstance(app)
        val i = p.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
        info = i
        i.cameraState.observeForever(observador)
        // Si CameraX no da un estado inicial, no se puede saber cuándo abre y cierra: solo cuenta el primer cuadro.
        observaEstado = estadoCamara.value != null
        if (nuevoPorCiclo) {
            dueno.a(Lifecycle.State.RESUMED)
        } else {
            dueno.a(Lifecycle.State.CREATED)
            crear()
        }
    }

    /** Abre con el controlador ya creado y lo deja escaneando (para la ráfaga y el cambio de motor). */
    suspend fun abrirYEsperar(limiteMs: Long = 10_000): Boolean {
        abrir()
        return esperarPrimerCuadro(limiteMs)
    }

    override suspend fun abrir() {
        if (nuevoPorCiclo) crear() else dueno.a(Lifecycle.State.RESUMED)
    }

    override suspend fun esperarAbierta(limiteMs: Long): Boolean {
        if (!observaEstado) return false
        return withTimeoutOrNull(limiteMs) {
            estadoCamara.first { it?.type == CameraState.Type.OPEN || controlador?.estado?.value?.fase == Fase.ERROR }
        }?.type == CameraState.Type.OPEN
    }

    override suspend fun esperarPrimerCuadro(limiteMs: Long): Boolean {
        val c = controlador ?: return false
        val e = withTimeoutOrNull(limiteMs) { c.estado.first { it.fase != Fase.ABRIENDO && it.fase != Fase.SIN_PERMISO } }
        return e != null && e.fase != Fase.ERROR
    }

    override suspend fun cerrar() {
        if (nuevoPorCiclo) {
            controlador?.cerrar()
            controlador = null
            for (t in trabajos) t.cancel()
            trabajos.clear()
        } else {
            dueno.a(Lifecycle.State.CREATED)
        }
    }

    override suspend fun esperarCerrada(limiteMs: Long): Boolean {
        if (!observaEstado) {
            // Sin estado de la cámara, se le da un respiro para que suelte antes del siguiente ciclo.
            delay(300)
            return false
        }
        return withTimeoutOrNull(limiteMs) { estadoCamara.first { it?.type == CameraState.Type.CLOSED } } != null
    }

    override fun error(): String? {
        val c = controlador?.estado?.value
        if (c?.fase == Fase.ERROR) return c.mensajeError ?: "error del escáner"
        val e = estadoCamara.value?.error ?: return null
        return "CameraState ${e.code} (${e.type})"
    }

    override suspend fun memoria(en: Int): PuntoMemoria = withContext(Dispatchers.Default) { memoriaTrasGc(en) }

    // ---- Ráfaga de zoom y linterna ----

    override fun zoom(alto: Boolean) {
        // El controlador no tiene una orden de zoom: cambiar el tope pide el zoom inicial (con tope 1 queda en 1×) por el
        // mismo camino que usa la guía (hilo de análisis → hilo principal → CameraControl).
        val c = controlador ?: return
        config = config.copy(zoomMaximo = if (alto) zoomBase else 1f)
        c.configurar(config)
    }

    override fun alternarLinterna() {
        controlador?.alternarLinterna()
    }

    override fun cuadros(): Long {
        val d = diagnostico
        // Si el escáner ya cuenta los cuadros analizados, ese número; si no, cuántas veces publicó su diagnóstico
        // (cada 500 ms mientras analiza).
        return if (d != null && d.cuadrosAnalizados > 0) d.cuadrosAnalizados else actualizaciones
    }

    override fun fase(): String = controlador?.estado?.value?.fase?.name ?: ""

    override suspend fun final(): FinalRafaga {
        delay(1_500)
        val e = controlador?.estado?.value
        val i = info
        return FinalRafaga(
            zoomUi = e?.zoom ?: Float.NaN,
            zoomReal = i?.zoomState?.value?.zoomRatio ?: Float.NaN,
            hayLinterna = e?.hayLinterna ?: false,
            linternaUi = e?.linterna ?: false,
            linternaReal = i?.torchState?.value?.let { it == TorchState.ON },
            fase = e?.fase?.name ?: "",
            error = error(),
        )
    }

    // ---- Cambio de motor ----

    override fun cambiar(motor: Motor) {
        config = config.copy(motor = motor)
        controlador?.configurar(config)
    }

    override suspend fun esperarMotor(motor: Motor, limiteMs: Long): Boolean {
        val c = controlador ?: return false
        val e = withTimeoutOrNull(limiteMs) {
            // El diagnóstico se publica con el primer cuadro analizado después del cambio.
            c.estado.first { it.diagnostico.motor == motor || it.fase == Fase.ERROR }
        }
        return e != null && e.fase != Fase.ERROR
    }

    /** Suelta todo: el controlador, el ciclo de vida, el visor y el observador. */
    fun liberar() {
        for (t in trabajos) t.cancel()
        trabajos.clear()
        controlador?.cerrar()
        controlador = null
        info?.cameraState?.removeObserver(observador)
        dueno.a(Lifecycle.State.DESTROYED)
        hiloVisor.quitSafely()
    }

    private fun crear() {
        val c = crearControlador(app, config)
        controlador = c
        trabajos += alcance.launch { c.superficie.collect { it?.let(::atender) } }
        trabajos += alcance.launch {
            var previo: Diagnostico? = null
            c.estado.collect {
                if (it.diagnostico !== previo) {
                    previo = it.diagnostico
                    actualizaciones++
                }
            }
        }
        c.permisoConcedido(true)
        c.abrir(dueno, ancho, alto, Surface.ROTATION_0)
    }

    /** Le da a la vista previa una superficie que se vacía sola; se cierra cuando CameraX la suelta (sin fugas de fd). */
    private fun atender(solicitud: SurfaceRequest) {
        val r = solicitud.resolution
        val lector = try {
            ImageReader.newInstance(r.width, r.height, ImageFormat.PRIVATE, 4)
        } catch (e: RuntimeException) {
            Log.w(ETIQUETA, "Sin ImageReader PRIVATE; se usa YUV", e)
            ImageReader.newInstance(r.width, r.height, ImageFormat.YUV_420_888, 4)
        }
        lector.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(hiloVisor.looper))
        solicitud.provideSurface(lector.surface, ContextCompat.getMainExecutor(app)) { lector.close() }
    }

    companion object {
        private const val ETIQUETA = "CamaraEstres"

        /** Solo en pruebas de estrés: el GC explícito y `getPss` (smaps_rollup) alteran los tiempos. */
        fun memoriaTrasGc(en: Int): PuntoMemoria {
            val rt = Runtime.getRuntime()
            rt.gc()
            System.runFinalization()
            rt.gc()
            val proceso = MonitorRecursos.estadoProceso()
            return PuntoMemoria(
                en = en,
                heapJavaKb = (rt.totalMemory() - rt.freeMemory()) / 1024,
                heapNativoKb = Debug.getNativeHeapAllocatedSize() / 1024,
                pssKb = Debug.getPss(),
                rssKb = proceso.rssKb,
                hilos = proceso.hilos,
                fds = MonitorRecursos.contarFds(),
            )
        }
    }
}
