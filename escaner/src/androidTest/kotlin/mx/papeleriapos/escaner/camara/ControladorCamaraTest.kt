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
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.nucleo.Rotacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
 * En esta rama el Localizador, la Guía y el Confirmador todavía son TODO(): el análisis de cada cuadro falla después
 * del motor y el estado queda en ERROR. Por eso aquí solo se revisan el arranque, los cuadros, lo que lee el motor y
 * el cierre (con [Analizador.observadorMotor]), no las fases de la guía.
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
        // Con el primer cuadro sale de ABRIENDO (a ESCANEANDO; aquí enseguida a ERROR por los TODO del núcleo).
        assertNotEquals("Se quedó en ABRIENDO", Fase.ABRIENDO, estado.fase)

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
