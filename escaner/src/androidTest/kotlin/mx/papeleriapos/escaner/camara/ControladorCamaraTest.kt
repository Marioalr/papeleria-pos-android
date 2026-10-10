package mx.papeleriapos.escaner.camara

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraState
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.nucleo.Rotacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * La cámara de verdad en el emulador (`-camera-back imagefile:<cartel>`, docs/pruebas.md): el controlador abre,
 * recibe cuadros con el recorte del ViewPort, el motor lee la franja, aguanta ir a segundo plano y volver, y cierra
 * sin caerse.
 *
 * Aquí se revisan el arranque, los cuadros, lo que lee el motor (con [Analizador.observadorMotor]) y el cierre; las
 * indicaciones de la guía las cubren las pruebas del núcleo y la de punta a punta (`herramientas/e2e.sh`).
 */
@RunWith(AndroidJUnit4::class)
class ControladorCamaraTest {

    private class DuenoDePrueba : LifecycleOwner {
        val registro = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registro
    }

    @Test
    fun abreRecibeCuadrosVaYVuelveDeSegundoPlanoYCierra() {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        val contexto = instrumentacion.targetContext
        assumeTrue(
            "El equipo no tiene cámara",
            contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
        )
        instrumentacion.uiAutomation.grantRuntimePermission(contexto.packageName, Manifest.permission.CAMERA)

        val hiloVista = HandlerThread("vista-de-prueba").also { it.start() }
        val cuadros = AtomicInteger()
        val geometria = AtomicReference<String>()
        val leidos = CopyOnWriteArrayList<String>()
        val fases = CopyOnWriteArrayList<Fase>()
        lateinit var dueno: DuenoDePrueba
        lateinit var controlador: ControladorCamara

        instrumentacion.runOnMainSync {
            dueno = DuenoDePrueba()
            dueno.registro.currentState = Lifecycle.State.RESUMED
            controlador = ControladorCamara(contexto, ConfigEscaner(segundosParaPausar = 0))
            controlador.analizador.observadorMotor = { r, t ->
                if (cuadros.incrementAndGet() == 1) {
                    geometria.set("visible=${t.visible} rotación=${t.rotacion} derecho=${t.anchoDerecho}x${t.altoDerecho}")
                }
                for (l in r.lecturas) {
                    // Dónde quedó lo leído en lo visible, para comprobar el recorte del ViewPort.
                    val caja = l.esquinasBuffer?.let { e -> cajaVisible(t, e, FloatArray(2)) }
                    leidos += "${l.simbologia.nombre}:${l.texto} en $caja"
                }
            }
            controlador.abrir(dueno, 1080, 2400, Surface.ROTATION_0)
        }
        // Sin quien consuma la vista previa CameraX no arranca la sesión: un ImageReader hace de visor.
        val lectores = CopyOnWriteArrayList<ImageReader>()
        val atendidas = HashSet<SurfaceRequest>()
        val hastaVer = SystemClock.elapsedRealtime() + 30_000
        while (cuadros.get() < 30 && SystemClock.elapsedRealtime() < hastaVer) {
            fases.agregarSiCambia(controlador.estado.value.fase)
            val solicitud = controlador.superficie.value
            if (solicitud != null && atendidas.add(solicitud)) lectores += atender(solicitud, hiloVista, contexto)
            SystemClock.sleep(20)
        }
        val estado = controlador.estado.value
        Log.i(ETIQUETA, "Cuadros: ${cuadros.get()}, ${geometria.get()}, fases $fases, estado ${estado.fase} «${estado.mensajeError}»")
        Log.i(ETIQUETA, "Diagnóstico: ${estado.diagnostico}, zoom ${estado.zoom}, linterna ${estado.hayLinterna}")
        Log.i(ETIQUETA, "Lecturas del motor: ${leidos.distinct()}")
        assertTrue("No llegaron cuadros al analizador (${cuadros.get()})", cuadros.get() >= 30)
        // Con el primer cuadro sale de ABRIENDO y escanea (con un código a la vista, VIENDO o LEIDO).
        assertTrue(
            "No quedó escaneando: ${estado.fase} «${estado.mensajeError}»",
            estado.fase == Fase.ESCANEANDO || estado.fase == Fase.VIENDO || estado.fase == Fase.LEIDO,
        )

        // Segundo plano y de vuelta: CameraX suelta la cámara y la vuelve a abrir.
        instrumentacion.runOnMainSync { dueno.registro.currentState = Lifecycle.State.CREATED }
        SystemClock.sleep(1_000)
        assertEquals(Fase.ABRIENDO, controlador.estado.value.fase)
        val antes = cuadros.get()
        SystemClock.sleep(500)
        val enPausa = cuadros.get() - antes
        instrumentacion.runOnMainSync { dueno.registro.currentState = Lifecycle.State.RESUMED }
        val hastaVolver = SystemClock.elapsedRealtime() + 20_000
        while (cuadros.get() < antes + 20 && SystemClock.elapsedRealtime() < hastaVolver) {
            val solicitud = controlador.superficie.value
            if (solicitud != null && atendidas.add(solicitud)) lectores += atender(solicitud, hiloVista, contexto)
            SystemClock.sleep(20)
        }
        Log.i(ETIQUETA, "En segundo plano llegaron $enPausa cuadros; al volver, ${cuadros.get() - antes}")
        assertTrue("En segundo plano siguieron llegando cuadros ($enPausa)", enPausa <= 2)
        assertTrue("No volvieron los cuadros", cuadros.get() >= antes + 20)

        // Cerrar: deja de analizar y suelta la superficie.
        instrumentacion.runOnMainSync { controlador.cerrar() }
        SystemClock.sleep(500)
        val alCerrar = cuadros.get()
        SystemClock.sleep(1_000)
        Log.i(ETIQUETA, "Tras cerrar llegaron ${cuadros.get() - alCerrar} cuadros")
        assertEquals("Siguió analizando después de cerrar", alCerrar, cuadros.get())
        assertNull(controlador.superficie.value)
        instrumentacion.runOnMainSync {
            // Llamadas después de cerrar: no deben hacer nada ni tronar.
            controlador.reanudar()
            controlador.enfocar(0.5f, 0.5f)
            controlador.alternarLinterna()
            controlador.cerrar()
            dueno.registro.currentState = Lifecycle.State.DESTROYED
        }
        lectores.forEach { it.close() }
        hiloVista.quitSafely()
    }

    /**
     * Cambiar de pestaña y volver: el visor sale (su superficie muere con él) y el ciclo de la pestaña se destruye; al
     * volver, otro dueño y otro visor. La cámara debe abrir una sola vez: si abre con la solicitud vieja, CameraX rearma
     * la sesión al llegar la nueva (abre, cierra y vuelve a abrir) y lo primero que se apunta se pierde.
     */
    @Test
    fun alVolverALaPestanaLaCamaraAbreUnaSolaVez() {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        val contexto = instrumentacion.targetContext
        assumeTrue(
            "El equipo no tiene cámara",
            contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
        )
        instrumentacion.uiAutomation.grantRuntimePermission(contexto.packageName, Manifest.permission.CAMERA)

        val hiloVista = HandlerThread("vista-de-prueba").also { it.start() }
        val cuadros = AtomicInteger()
        val estados = CopyOnWriteArrayList<CameraState.Type>()
        val observador = Observer<CameraState> { if (estados.lastOrNull() != it.type) estados += it.type }
        lateinit var pestana: DuenoDePrueba
        lateinit var controlador: ControladorCamara
        instrumentacion.runOnMainSync {
            pestana = DuenoDePrueba()
            pestana.registro.currentState = Lifecycle.State.RESUMED
            controlador = ControladorCamara(contexto, ConfigEscaner(segundosParaPausar = 0))
            controlador.analizador.observadorMotor = { _, _ -> cuadros.incrementAndGet() }
            controlador.abrir(pestana, 1080, 2400, Surface.ROTATION_0)
        }
        val atendidas = HashSet<SurfaceRequest>()
        var visor: ImageReader? = null
        fun esperarCuadros(cuantos: Int, ms: Long) {
            val hasta = SystemClock.elapsedRealtime() + ms
            while (cuadros.get() < cuantos && SystemClock.elapsedRealtime() < hasta) {
                val solicitud = controlador.superficie.value
                if (solicitud != null && atendidas.add(solicitud)) visor = atender(solicitud, hiloVista, contexto)
                SystemClock.sleep(10)
            }
        }
        esperarCuadros(20, 30_000)
        assertTrue("No llegaron cuadros al abrir (${cuadros.get()})", cuadros.get() >= 20)

        // A otra pestaña: primero sale el visor (Compose desecha a los hijos antes) y luego se destruye el ciclo.
        instrumentacion.runOnMainSync {
            controlador.visorRetirado()
            pestana.registro.currentState = Lifecycle.State.DESTROYED
            visor?.close()
        }
        SystemClock.sleep(1_000)
        val vieja = atendidas.toSet()
        instrumentacion.runOnMainSync { controlador.camara?.cameraInfo?.cameraState?.observeForever(observador) }
        SystemClock.sleep(200)
        estados.clear()

        // De vuelta: otro dueño, otro visor.
        val antes = cuadros.get()
        instrumentacion.runOnMainSync {
            pestana = DuenoDePrueba()
            pestana.registro.currentState = Lifecycle.State.RESUMED
            controlador.abrir(pestana, 1080, 2400, Surface.ROTATION_0)
        }
        esperarCuadros(antes + 20, 20_000)
        SystemClock.sleep(500)
        Log.i(ETIQUETA, "Al volver: estados de la cámara $estados, ${cuadros.get() - antes} cuadros")
        assertTrue("No volvieron los cuadros", cuadros.get() >= antes + 20)
        assertTrue("Se reusó la solicitud del visor que salió", (atendidas - vieja).isNotEmpty())
        assertEquals("La cámara abrió más de una vez: $estados", 1, estados.count { it == CameraState.Type.OPENING })
        assertEquals("La cámara se cerró al volver: $estados", 0, estados.count { it == CameraState.Type.CLOSING })

        instrumentacion.runOnMainSync {
            controlador.camara?.cameraInfo?.cameraState?.removeObserver(observador)
            controlador.cerrar()
            pestana.registro.currentState = Lifecycle.State.DESTROYED
        }
        visor?.close()
        hiloVista.quitSafely()
    }

    @Test
    fun sinPermisoSeQuedaEnSinPermiso() {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        lateinit var controlador: ControladorCamara
        instrumentacion.runOnMainSync {
            controlador = ControladorCamara(instrumentacion.targetContext)
            controlador.permisoConcedido(false)
        }
        assertEquals(Fase.SIN_PERMISO, controlador.estado.value.fase)
        instrumentacion.runOnMainSync { controlador.cerrar() }
    }

    /** Le da a la vista previa una superficie que se vacía sola (nadie la dibuja). */
    private fun atender(solicitud: SurfaceRequest, hilo: HandlerThread, contexto: android.content.Context): ImageReader {
        val r = solicitud.resolution
        val lector = ImageReader.newInstance(r.width, r.height, ImageFormat.PRIVATE, 4)
        lector.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(hilo.looper))
        solicitud.provideSurface(lector.surface, ContextCompat.getMainExecutor(contexto)) {}
        Log.i(ETIQUETA, "Vista previa de ${r.width}x${r.height}")
        return lector
    }

    private fun MutableList<Fase>.agregarSiCambia(fase: Fase) {
        if (lastOrNull() != fase) add(fase)
    }

    @Test
    fun cuentasDeGeometriaSinAsignarCoincidenConTransformacion() {
        // aVisible/aBuffer del analizador repiten las de Transformacion sin asignar: deben dar lo mismo.
        val p = FloatArray(2)
        val q = FloatArray(2)
        for (rotacion in listOf(0, 90, 180, 270)) {
            val t = mx.papeleriapos.escaner.nucleo.Transformacion(mx.papeleriapos.escaner.nucleo.Rectangulo(10, 72, 1270, 648), rotacion)
            for ((x, y) in listOf(10f to 72f, 640f to 360f, 1270f to 648f, 100f to 500f)) {
                aVisible(t, x, y, p)
                t.aVisible(x, y, q)
                assertEquals(q[0], p[0], 1e-5f)
                assertEquals(q[1], p[1], 1e-5f)
                aBuffer(t, p[0], p[1], q)
                assertEquals(x, q[0], 1e-3f)
                assertEquals(y, q[1], 1e-3f)
            }
        }
        // Y la rotación de un punto ida y vuelta.
        Rotacion.aDerecho(5f, 7f, 1280, 720, 90, p)
        Rotacion.aBuffer(p[0], p[1], 1280, 720, 90, q)
        assertEquals(5f, q[0], 1e-5f)
        assertEquals(7f, q[1], 1e-5f)
    }

    private companion object {
        const val ETIQUETA = "ControladorCamaraTest"
    }
}
