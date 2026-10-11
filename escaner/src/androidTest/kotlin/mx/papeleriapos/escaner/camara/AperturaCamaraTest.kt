package mx.papeleriapos.escaner.camara

import android.Manifest
import android.content.Context
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
import mx.papeleriapos.escaner.MarcasApertura
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.TipoArranque
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * La apertura con la cámara del emulador: abrir con lo mismo no vuelve a enlazar (antes se enlazaba dos veces), el
 * enlace temprano de `onCreate` sirve para la pantalla sin volver a enlazar, el rango de FPS queda guardado, el
 * controlador vuelve a escanear tras cerrar y abrir, y el diagnóstico cuenta cuadros y dice el estado de la cámara.
 */
@RunWith(AndroidJUnit4::class)
class AperturaCamaraTest {
    private class DuenoDePrueba : LifecycleOwner {
        val registro = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registro
    }

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val hiloVista = HandlerThread("vista-apertura").also { it.start() }
    private val lectores = CopyOnWriteArrayList<ImageReader>()
    private val atendidas = HashSet<SurfaceRequest>()
    private var controlador: ControladorCamara? = null

    @Before
    fun preparar() {
        assumeTrue("El equipo no tiene cámara", ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
        inst.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.CAMERA)
        // Sin proporción ni rango guardados: se prueba también el camino de calcularlos.
        ctx.getSharedPreferences(ARCHIVO_APERTURA, Context.MODE_PRIVATE).edit().clear().commit()
        // La validación del rango corre una vez por proceso: cada prueba empieza como un proceso nuevo.
        ValidacionFps.olvidar(true)
        ValidacionFps.olvidar(false)
    }

    @After
    fun limpiar() {
        inst.runOnMainSync { controlador?.cerrar() }
        lectores.forEach { it.close() }
        hiloVista.quitSafely()
    }

    @Test
    fun abrirConLoMismoNoVuelveAEnlazarYOtraProporcionEnlazaUnaSolaVez() {
        val dueno = crearDueno(Lifecycle.State.RESUMED)
        val c = nuevo()
        inst.runOnMainSync { c.abrir(dueno, 1080, 2400, Surface.ROTATION_0) }
        // Con visor, para que la sesión arranque de verdad (sin él, CameraX esperaría la superficie).
        atenderHasta(c) { c.estado.value.diagnostico.cuadrosAnalizados >= 3 }
        // Lo que hacía PantallaEscaner al arrancar: otra vez abrir, el permiso y un píxel de diferencia.
        inst.runOnMainSync {
            c.abrir(dueno, 1080, 2400, Surface.ROTATION_0)
            c.permisoConcedido(true)
            c.abrir(dueno, 1080, 2401, Surface.ROTATION_0)
        }
        atenderDurante(c, 600)
        assertEquals("Abrir con lo mismo volvió a enlazar", 1, c.enlacesHechos)

        // Dos tamaños seguidos (como Compose al medir sin y con las barras): un solo enlace, con el último.
        inst.runOnMainSync {
            c.abrir(dueno, 1080, 2208, Surface.ROTATION_0)
            c.abrir(dueno, 1080, 2100, Surface.ROTATION_0)
        }
        atenderHasta(c) { c.enlacesHechos >= 2 }
        atenderDurante(c, 600)
        assertEquals(2, c.enlacesHechos)
        assertEquals(Proporcion(1080, 2100), MemoriaApertura(ctx).proporcion(ControladorCamara.PANTALLA_PRINCIPAL))
    }

    @Test
    fun elEnlaceTempranoSirveParaLaPantallaSinVolverAEnlazar() {
        val actividad = crearDueno(Lifecycle.State.INITIALIZED)
        val c = nuevo()
        MarcasApertura.nueva(TipoArranque.TIBIO)
        // Como en onCreate: la Activity todavía no empieza; la cámara se enlaza pero no abre.
        inst.runOnMainSync { c.enlazarTemprano(actividad, PANTALLA) { Proporcion(1080, 2400) } }
        atenderHasta(c) { c.enlacesHechos >= 1 }
        assertTrue(MarcasApertura.tiene(PasoApertura.ENLACE_INICIO))
        assertTrue(MarcasApertura.tiene(PasoApertura.ENLACE_FIN))
        // onStart/onResume y luego la pantalla, con su propio dueño y la misma proporción.
        val pantalla = crearDueno(Lifecycle.State.RESUMED)
        inst.runOnMainSync {
            actividad.registro.currentState = Lifecycle.State.RESUMED
            c.abrir(pantalla, 1080, 2400, Surface.ROTATION_0)
        }
        // La pantalla sigue su propio ciclo (como CicloPropio): la Activity ya no manda.
        atenderHasta(c) { MarcasApertura.tiene(PasoApertura.PRIMER_ANALISIS) }
        assertEquals("La pantalla volvió a enlazar lo del arranque", 1, c.enlacesHechos)
        val a = MarcasApertura.apertura
        Log.i(ETIQUETA, "Apertura: ${a.resumen()}")
        for (paso in listOf(PasoApertura.CAMARA_ABIERTA, PasoApertura.PRIMER_CUADRO_SENSOR, PasoApertura.PRIMER_ANALISIS)) {
            assertTrue("Falta la marca $paso", a.ms(paso) >= 0)
        }
        assertTrue(a.ms(PasoApertura.ENLACE_FIN) <= a.ms(PasoApertura.PRIMER_ANALISIS))
        // El rango de FPS quedó guardado para la próxima apertura.
        assertNotEquals(RangoEnCache.SinDato, MemoriaApertura(ctx).rangoFps(ConfigEscaner().fpsFijo30))
        // La proporción la guardó la pantalla, no el enlace temprano.
        assertEquals(Proporcion(1080, 2400), MemoriaApertura(ctx).proporcion(PANTALLA))
    }

    /**
     * Que el segundo controlador de verdad se salte el cálculo del rango: se deja guardado un valor que el cálculo nunca
     * daría (sin rango fijo, «que decida CameraX») cuando la cámara sí acepta uno. Si enlaza con lo guardado, su rango
     * queda vacío; si lo hubiera calculado, saldría el fijo. Y como enlazó con lo guardado, al rato lo valida y guarda
     * el que la cámara da ahora (eso solo pasa en el camino de lo guardado).
     */
    @Test
    fun conElRangoGuardadoEnlazaSinCalcularloYEscanea() {
        // Primero uno que calcula y guarda.
        val dueno = crearDueno(Lifecycle.State.RESUMED)
        val primero = nuevo()
        inst.runOnMainSync { primero.abrir(dueno, 1080, 2400, Surface.ROTATION_0) }
        atenderHasta(primero) { primero.enlacesHechos >= 1 && primero.estado.value.diagnostico.cuadrosAnalizados >= 3 }
        val calculado = primero.estado.value.diagnostico.rangoFps
        inst.runOnMainSync { primero.cerrar() }
        val memoria = MemoriaApertura(ctx)
        val guardado = memoria.rangoFps(true)
        assertNotEquals(RangoEnCache.SinDato, guardado)
        assumeTrue("La cámara no acepta un rango fijo de 30: no se distingue lo guardado de lo calculado", guardado is RangoEnCache.Fijo)
        assertTrue(calculado.isNotEmpty())

        // Lo guardado ahora dice «sin rango fijo»: el cálculo daría el fijo de arriba.
        memoria.guardarRangoFps(true, null)
        assertEquals(RangoEnCache.Auto, memoria.rangoFps(true))

        val c = nuevo()
        val otro = crearDueno(Lifecycle.State.RESUMED)
        inst.runOnMainSync { c.abrir(otro, 1080, 2400, Surface.ROTATION_0) }
        atenderHasta(c) { c.estado.value.diagnostico.cuadrosAnalizados >= 10 }
        val d = c.estado.value.diagnostico
        Log.i(ETIQUETA, "Diagnóstico: producidos ${d.cuadrosProducidos}, analizados ${d.cuadrosAnalizados}, estado ${d.estadoCamara}, FPS «${d.rangoFps}»")
        assertEquals("OPEN", d.estadoCamara)
        // Los dos contadores se toman en momentos distintos y entre la captura y el análisis hay unos cuadros en camino.
        assertTrue("Analizó más cuadros de los que dio la cámara", d.cuadrosProducidos + CUADROS_EN_CAMINO >= d.cuadrosAnalizados)
        assertEquals("Calculó el rango en lugar de usar el guardado", "", d.rangoFps)
        assertEquals(1, c.enlacesHechos)
        // La validación (solo tras enlazar con lo guardado) corrige lo guardado para la próxima, sin volver a enlazar.
        atenderHasta(c, ControladorCamara.VALIDAR_FPS_TRAS_MS + 5_000) { memoria.rangoFps(true) == guardado }
        assertEquals("La validación no restauró el rango fijo", guardado, memoria.rangoFps(true))
        assertEquals(1, c.enlacesHechos)
    }

    @Test
    fun trasCerrarUnAbrirVuelveAEscanear() {
        val dueno = crearDueno(Lifecycle.State.RESUMED)
        val c = nuevo()
        inst.runOnMainSync { c.abrir(dueno, 1080, 2400, Surface.ROTATION_0) }
        atenderHasta(c) { c.estado.value.diagnostico.cuadrosAnalizados >= 5 }
        // Como al cambiar de pestaña: la pantalla cierra el controlador y al volver lo abre con otro dueño.
        inst.runOnMainSync {
            c.cerrar()
            dueno.registro.currentState = Lifecycle.State.DESTROYED
        }
        SystemClock.sleep(500)
        // Una apertura nueva: el primer cuadro analizado después de volver a abrir la marca otra vez.
        MarcasApertura.nueva(TipoArranque.TIBIO)
        val otro = crearDueno(Lifecycle.State.RESUMED)
        inst.runOnMainSync { c.abrir(otro, 1080, 2400, Surface.ROTATION_0) }
        atenderHasta(c) { c.enlacesHechos >= 2 && MarcasApertura.tiene(PasoApertura.PRIMER_ANALISIS) }
        assertEquals(2, c.enlacesHechos)
    }

    private fun nuevo(): ControladorCamara {
        lateinit var c: ControladorCamara
        inst.runOnMainSync { c = ControladorCamara(ctx, ConfigEscaner(segundosParaPausar = 0)) }
        controlador = c
        return c
    }

    /** Un dueño en [estado], creado en el hilo principal (LifecycleRegistry lo exige). */
    private fun crearDueno(estado: Lifecycle.State): DuenoDePrueba {
        lateinit var d: DuenoDePrueba
        inst.runOnMainSync {
            d = DuenoDePrueba()
            if (estado != Lifecycle.State.INITIALIZED) d.registro.currentState = estado
        }
        return d
    }

    private fun atenderDurante(c: ControladorCamara, ms: Long) {
        val hasta = SystemClock.elapsedRealtime() + ms
        atenderHasta(c, ms + 1_000) { SystemClock.elapsedRealtime() >= hasta }
    }

    /** Hace de visor (un ImageReader por cada SurfaceRequest) hasta que se cumpla [condicion]. */
    private fun atenderHasta(c: ControladorCamara, ms: Long = 30_000, condicion: () -> Boolean) {
        val hasta = SystemClock.elapsedRealtime() + ms
        while (!condicion() && SystemClock.elapsedRealtime() < hasta) {
            val s = c.superficie.value
            if (s != null && atendidas.add(s)) {
                val r = s.resolution
                val lector = ImageReader.newInstance(r.width, r.height, ImageFormat.PRIVATE, 4)
                lector.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(hiloVista.looper))
                s.provideSurface(lector.surface, ContextCompat.getMainExecutor(ctx)) {}
                lectores += lector
            }
            SystemClock.sleep(20)
        }
        assertTrue("No se cumplió en $ms ms (estado ${c.estado.value.fase}, ${c.estado.value.diagnostico})", condicion())
    }

    private companion object {

        const val CUADROS_EN_CAMINO = 4
        const val ETIQUETA = "AperturaCamaraTest"
        const val PANTALLA = "prueba"
    }
}
