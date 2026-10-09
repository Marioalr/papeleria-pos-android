package mx.papeleriapos.android.rapido

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.ListasCompartidas
import mx.papeleriapos.android.MainActivity
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.escaner.MarcasApertura
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.camara.ControladorCamara
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La apertura de la app completa en el emulador (con la cámara apuntando a un cartel, docs/pruebas.md): la segunda vez
 * que se abre la cámara se enlaza una sola vez (con la proporción guardada, desde onCreate), los atajos cambian el modo
 * sin perder lo leído, y el escaneo rápido llega a escanear.
 */
@RunWith(AndroidJUnit4::class)
class AperturaAppTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext

    @Before
    fun preparar() {
        inst.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.CAMERA)
    }

    @Test
    fun laSegundaAperturaEnlazaUnaSolaVez() {
        assumeTrue("El equipo no tiene cámara", ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
        // La primera guarda la proporción que mide Compose (si no estaba).
        ActivityScenario.launch(MainActivity::class.java).use { esperarPrimerAnalisis() }
        ActivityScenario.launch(MainActivity::class.java).use { escenario ->
            esperarPrimerAnalisis()
            // Por si llegara un segundo enlace tarde (el reenlace por otra proporción espera ESPERA_REENLACE_MS).
            SystemClock.sleep(1_000)
            var enlaces = -1
            escenario.onActivity { a ->
                val c = ViewModelProvider(a)[EscanerViewModel::class.java].controlador as ControladorCamara
                enlaces = c.enlacesHechos
            }
            val apertura = MarcasApertura.apertura
            Log.i(ETIQUETA, "Apertura: ${apertura.resumen()}")
            assertEquals("Se enlazó más de una vez al abrir", 1, enlaces)
            for (paso in listOf(
                PasoApertura.ACTIVIDAD, PasoApertura.PRIMER_CUADRO_UI, PasoApertura.ENLACE_INICIO, PasoApertura.ENLACE_FIN,
                PasoApertura.CAMARA_ABIERTA, PasoApertura.PRIMER_CUADRO_SENSOR, PasoApertura.PRIMER_ANALISIS,
            )) {
                assertTrue("Falta la marca $paso", apertura.ms(paso) >= 0)
            }
            // Se enlaza en onCreate, antes de que la cámara abra y del primer cuadro analizado.
            assertTrue(apertura.ms(PasoApertura.ENLACE_FIN) <= apertura.ms(PasoApertura.CAMARA_ABIERTA))
            assertTrue(apertura.ms(PasoApertura.ENLACE_INICIO) <= apertura.ms(PasoApertura.PRIMER_CUADRO_UI))
        }
    }

    @Test
    fun elAtajoDeConteoCambiaElModoSinPerderLoLeido() {
        val prefs = PreferenciasEscaner(ctx.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE))
        val antes = prefs.leer()
        val listas = ListasCompartidas.delProceso
        listas.venta.value = ListaLecturas().sumar("7501234567893", Simbologia.EAN_13, 0L)
        try {
            prefs.guardar(antes.copy(modo = Modo.VENTA))
            // Lo que trae el atajo (el sistema además recrea la tarea: Activity y ViewModel nuevos, como aquí).
            val atajo = Intent(ctx, MainActivity::class.java)
                .setAction("mx.papeleriapos.android.action.ESCANEAR")
                .putExtra(MainActivity.EXTRA_MODO, MainActivity.MODO_CONTEO)
            ActivityScenario.launch<MainActivity>(atajo).use { escenario ->
                var modo: Modo? = null
                var enVenta = -1
                escenario.onActivity { a ->
                    val vm = ViewModelProvider(a)[EscanerViewModel::class.java]
                    modo = vm.config.value.modo
                    enVenta = vm.venta.value.total
                }
                assertEquals(Modo.CONTEO, modo)
                assertEquals(Modo.CONTEO, prefs.leer().modo)
                assertEquals("Se perdió lo leído en venta", 1, enVenta)
            }
        } finally {
            prefs.guardar(antes)
            listas.venta.value = ListaLecturas()
        }
    }

    @Test
    fun elEscaneoRapidoLlegaAEscanear() {
        assumeTrue("El equipo no tiene cámara", ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
        ActivityScenario.launch(EscaneoRapidoActivity::class.java).use {
            esperarPrimerAnalisis()
            Log.i(ETIQUETA, "Escaneo rápido: ${MarcasApertura.apertura.resumen()}")
        }
    }

    private fun esperarPrimerAnalisis(ms: Long = 20_000) {
        val hasta = SystemClock.elapsedRealtime() + ms
        while (!MarcasApertura.tiene(PasoApertura.PRIMER_ANALISIS) && SystemClock.elapsedRealtime() < hasta) SystemClock.sleep(20)
        assertTrue("No llegó el primer cuadro analizado en $ms ms", MarcasApertura.tiene(PasoApertura.PRIMER_ANALISIS))
    }

    private companion object {
        const val ETIQUETA = "AperturaAppTest"
    }
}
