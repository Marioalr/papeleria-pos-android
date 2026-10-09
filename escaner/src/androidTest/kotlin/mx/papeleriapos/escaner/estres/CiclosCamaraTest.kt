package mx.papeleriapos.escaner.estres

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.camara.ControladorCamara
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Estrés de la cámara real (docs/pruebas.md): N ciclos abrir → cámara abierta → primer cuadro analizado → cerrar →
 * cámara cerrada con [ControladorCamara], un ciclo de vida de prueba y un `ImageReader` como visor. Mide abrir_ms,
 * primer_cuadro_ms y cerrar_ms, y cada 10 ciclos la memoria tras GC, los hilos y los descriptores. Los ciclos salen en
 * logcat (etiqueta CiclosCamaraTest) como CSV.
 *
 * Por omisión 30 ciclos (que quepa en una corrida del emulador); con
 * `-Pandroid.testInstrumentationRunnerArguments.ciclos=200` se hacen los 200 del criterio.
 */
@RunWith(AndroidJUnit4::class)
class CiclosCamaraTest {
    private class DuenoDePrueba : LifecycleOwner {
        val registro = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registro
    }

    private data class Ciclo(val n: Int, val abrirMs: Double, val cuadroMs: Double, val cerrarMs: Double, val error: String?)

    private data class Memoria(val n: Int, val heapKb: Long, val nativoKb: Long, val hilos: Int, val fds: Int)

    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val contexto: Context = instrumentacion.targetContext
    private lateinit var hiloVista: HandlerThread
    private lateinit var info: CameraInfo
    private val estadoCamara = AtomicReference<CameraState?>()
    private val observador = Observer<CameraState> { estadoCamara.set(it) }
    private val lectores = CopyOnWriteArrayList<ImageReader>()
    private val ciclosPedidos = InstrumentationRegistry.getArguments().getString("ciclos")?.toIntOrNull() ?: 30

    @Before
    fun preparar() {
        assumeTrue("El equipo no tiene cámara", contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
        instrumentacion.uiAutomation.grantRuntimePermission(contexto.packageName, Manifest.permission.CAMERA)
        hiloVista = HandlerThread("vista-estres").also { it.start() }
        val proveedor = ProcessCameraProvider.getInstance(contexto).get()
        instrumentacion.runOnMainSync {
            info = proveedor.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
            info.cameraState.observeForever(observador)
        }
    }

    @After
    fun limpiar() {
        if (!::hiloVista.isInitialized) return
        instrumentacion.runOnMainSync { info.cameraState.removeObserver(observador) }
        lectores.forEach { runCatching { it.close() } }
        hiloVista.quitSafely()
    }

    @Test
    fun conUnControladorNuevoCadaVezNoFugaNiSeAtrasa() {
        val dueno = enPrincipal { DuenoDePrueba().also { it.registro.currentState = Lifecycle.State.RESUMED } }
        val ciclos = ArrayList<Ciclo>()
        val memoria = ArrayList<Memoria>()
        for (n in 1..ciclosPedidos) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val controlador = enPrincipal {
                ControladorCamara(contexto, ConfigEscaner(segundosParaPausar = 0, segundosParaApagar = 0, sonido = false, vibracion = false))
                    .also { it.abrir(dueno, 1080, 2400, Surface.ROTATION_0) }
            }
            val (tAbierta, tCuadro) = esperarPrimerCuadro(controlador, t0)
            val tCerrar = SystemClock.elapsedRealtimeNanos()
            instrumentacion.runOnMainSync { controlador.cerrar() }
            val cerrada = esperar(5_000) { estadoCamara.get()?.type == CameraState.Type.CLOSED }
            ciclos += ciclo(n, t0, tAbierta, tCuadro, tCerrar, cerrada, controlador)
            if (n == 1 || n % 10 == 0 || n == ciclosPedidos) memoria += memoriaTrasGc(n)
        }
        instrumentacion.runOnMainSync { dueno.registro.currentState = Lifecycle.State.DESTROYED }
        revisar("controlador nuevo", ciclos, memoria)
    }

    @Test
    fun conElMismoControladorYendoYVolviendoDeSegundoPlano() {
        val dueno = enPrincipal { DuenoDePrueba().also { it.registro.currentState = Lifecycle.State.CREATED } }
        val controlador = enPrincipal {
            ControladorCamara(contexto, ConfigEscaner(segundosParaPausar = 0, segundosParaApagar = 0, sonido = false, vibracion = false))
                .also { it.abrir(dueno, 1080, 2400, Surface.ROTATION_0) }
        }
        val ciclos = ArrayList<Ciclo>()
        val memoria = ArrayList<Memoria>()
        for (n in 1..ciclosPedidos) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            instrumentacion.runOnMainSync { dueno.registro.currentState = Lifecycle.State.RESUMED }
            val (tAbierta, tCuadro) = esperarPrimerCuadro(controlador, t0)
            val tCerrar = SystemClock.elapsedRealtimeNanos()
            instrumentacion.runOnMainSync { dueno.registro.currentState = Lifecycle.State.CREATED }
            val cerrada = esperar(5_000) { estadoCamara.get()?.type == CameraState.Type.CLOSED }
            ciclos += ciclo(n, t0, tAbierta, tCuadro, tCerrar, cerrada, controlador)
            if (n == 1 || n % 10 == 0 || n == ciclosPedidos) memoria += memoriaTrasGc(n)
        }
        instrumentacion.runOnMainSync {
            controlador.cerrar()
            dueno.registro.currentState = Lifecycle.State.DESTROYED
        }
        revisar("segundo plano", ciclos, memoria)
    }

    /** Atiende la vista previa y espera el primer cuadro analizado; devuelve cuándo abrió la cámara y cuándo llegó. */
    private fun esperarPrimerCuadro(controlador: ControladorCamara, t0: Long): Pair<Long, Long> {
        val atendidas = HashSet<SurfaceRequest>()
        var tAbierta = 0L
        val hasta = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < hasta) {
            controlador.superficie.value?.let { if (atendidas.add(it)) atender(it) }
            if (tAbierta == 0L && estadoCamara.get()?.type == CameraState.Type.OPEN) tAbierta = SystemClock.elapsedRealtimeNanos()
            val fase = controlador.estado.value.fase
            if (fase == Fase.ERROR) return tAbierta to 0L
            if (fase != Fase.ABRIENDO && fase != Fase.SIN_PERMISO) return tAbierta to SystemClock.elapsedRealtimeNanos()
            SystemClock.sleep(5)
        }
        return tAbierta to 0L
    }

    private fun ciclo(n: Int, t0: Long, tAbierta: Long, tCuadro: Long, tCerrar: Long, cerrada: Boolean, c: ControladorCamara): Ciclo {
        val tCerrada = SystemClock.elapsedRealtimeNanos()
        val error = when {
            tCuadro == 0L -> c.estado.value.mensajeError ?: estadoCamara.get()?.error?.let { "CameraState ${it.code}" } ?: "sin cuadros"
            !cerrada -> "no cerró"
            else -> null
        }
        val ms = { a: Long, b: Long -> if (a == 0L || b == 0L) Double.NaN else (b - a) / 1e6 }
        return Ciclo(n, ms(t0, tAbierta), ms(t0, tCuadro), if (cerrada) ms(tCerrar, tCerrada) else Double.NaN, error).also {
            Log.i(ETIQUETA, "ciclo,${it.n},${it.abrirMs},${it.cuadroMs},${it.cerrarMs},${it.error ?: ""}")
        }
    }

    private fun revisar(modo: String, ciclos: List<Ciclo>, memoria: List<Memoria>) {
        val buenos = ciclos.filter { it.error == null }
        val ultimoBueno = buenos.lastOrNull()?.n ?: 0
        val sinRecuperar = ciclos.count { it.error != null && it.n > ultimoBueno }
        val k = maxOf(3, ciclos.size / 10)
        val p95Inicio = p95(buenos.take(k).map { it.cuadroMs })
        val p95Final = p95(buenos.takeLast(k).map { it.cuadroMs })
        val base = memoria.first()
        val final = memoria.last()
        Log.i(
            ETIQUETA,
            "$modo: ${ciclos.size} ciclos, ${ciclos.count { it.error != null }} errores ($sinRecuperar sin recuperar); " +
                "primer cuadro p50 ${p50(buenos.map { it.cuadroMs })} ms, p95 inicio $p95Inicio → final $p95Final; " +
                "abrir p50 ${p50(buenos.map { it.abrirMs })} ms; cerrar p50 ${p50(buenos.map { it.cerrarMs })} ms; " +
                "memoria $base → $final",
        )
        assertTrue("Errores sin recuperar: ${ciclos.filter { it.error != null }}", sinRecuperar == 0)
        assertTrue("Menos de la mitad de los ciclos leyeron cuadros", buenos.size * 2 >= ciclos.size)
        // Los hilos y descriptores vuelven a la base (± 2); el emulador es ruidoso, así que con un margen de 4.
        assertTrue("Se fugan hilos: ${base.hilos} → ${final.hilos}", final.hilos - base.hilos <= 4)
        assertTrue("Se fugan descriptores: ${base.fds} → ${final.fds}", final.fds - base.fds <= 4)
        // El heap nativo no crece más de 2 MB por cada 100 ciclos.
        val ciclosEntre = (final.n - base.n).coerceAtLeast(1)
        val crecimiento = (final.nativoKb - base.nativoKb) * 100.0 / ciclosEntre
        assertTrue("El heap nativo crece $crecimiento KB por 100 ciclos", crecimiento < 2048 || final.nativoKb - base.nativoKb < 2048)
        if (ciclos.size >= 20) assertTrue("El primer cuadro se atrasó: $p95Inicio → $p95Final", p95Final <= p95Inicio * 1.5)
    }

    /** Le da a la vista previa una superficie que se vacía sola; se cierra cuando CameraX la suelta. */
    private fun atender(solicitud: SurfaceRequest) {
        val r = solicitud.resolution
        val lector = ImageReader.newInstance(r.width, r.height, ImageFormat.PRIVATE, 4)
        lector.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(hiloVista.looper))
        lectores += lector
        solicitud.provideSurface(lector.surface, ContextCompat.getMainExecutor(contexto)) {
            lector.close()
            lectores.remove(lector)
        }
    }

    private fun memoriaTrasGc(n: Int): Memoria {
        val rt = Runtime.getRuntime()
        rt.gc()
        System.runFinalization()
        rt.gc()
        val hilos = try {
            File("/proc/self/status").readLines().firstOrNull { it.startsWith("Threads:") }?.substringAfter(':')?.trim()?.toInt() ?: -1
        } catch (e: Exception) {
            -1
        }
        val fds = File("/proc/self/fd").list()?.size ?: -1
        return Memoria(n, (rt.totalMemory() - rt.freeMemory()) / 1024, Debug.getNativeHeapAllocatedSize() / 1024, hilos, fds).also {
            Log.i(ETIQUETA, "memoria,$it")
        }
    }

    private fun esperar(ms: Long, condicion: () -> Boolean): Boolean {
        val hasta = SystemClock.elapsedRealtime() + ms
        while (SystemClock.elapsedRealtime() < hasta) {
            if (condicion()) return true
            SystemClock.sleep(5)
        }
        return condicion()
    }

    private fun <T> enPrincipal(bloque: () -> T): T {
        val r = AtomicReference<T>()
        instrumentacion.runOnMainSync { r.set(bloque()) }
        return r.get()
    }

    private fun p50(v: List<Double>) = percentil(v, 0.5)
    private fun p95(v: List<Double>) = percentil(v, 0.95)

    private fun percentil(v: List<Double>, p: Double): Double {
        val o = v.filter { !it.isNaN() }.sorted()
        if (o.isEmpty()) return Double.NaN
        val pos = p * (o.size - 1)
        val a = pos.toInt()
        val b = minOf(a + 1, o.size - 1)
        return o[a] + (o[b] - o[a]) * (pos - a)
    }

    private companion object {
        const val ETIQUETA = "CiclosCamaraTest"
    }
}
