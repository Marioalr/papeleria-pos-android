package mx.papeleriapos.android.rapido

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.flow.update
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.MainActivity
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.lista.ListasCompartidas
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
import java.io.File

/**
 * La apertura de la app completa en el emulador (con la cámara apuntando a un cartel, docs/pruebas.md): la segunda vez
 * que se abre la cámara se enlaza una sola vez (con la proporción guardada, desde onCreate), el atajo recrea la tarea y
 * cambia el modo sin perder lo leído (que además queda en disco), y el escaneo rápido llega a escanear.
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

    /**
     * Como el atajo del ícono: el sistema lanza su intent con NEW_TASK | CLEAR_TASK | TASK_ON_HOME, que destruye la tarea
     * abierta (Activity y ViewModel) y crea otra. Lo leído en venta sigue ahí (vive en el proceso y en disco).
     */
    @Test
    fun elAtajoDeConteoRecreaLaTareaSinPerderLoLeido() {
        val prefs = PreferenciasEscaner(ctx.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE))
        val antes = prefs.leer()
        val listas = ListasCompartidas.delProceso(ctx)
        esperar("No se cargaron las listas guardadas") { listas.cargadas }
        val previa = listas.venta.value
        var nueva: Activity? = null
        try {
            prefs.guardar(antes.copy(modo = Modo.VENTA))
            // La app abierta como desde el lanzador, en su propia tarea.
            val lanzador = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ctx.startActivity(lanzador)
            val vieja = esperarMainActivity(distintaDe = null)
            // Una lectura en la venta en curso.
            listas.venta.update { it.sumar("7501234567893", Simbologia.EAN_13, System.currentTimeMillis()) }
            val total = listas.venta.value.total

            val atajo = Intent(ctx, MainActivity::class.java)
                .setAction("mx.papeleriapos.android.action.ESCANEAR")
                .putExtra(MainActivity.EXTRA_MODO, MainActivity.MODO_CONTEO)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            ctx.startActivity(atajo)
            val a = esperarMainActivity(distintaDe = vieja)
            nueva = a
            esperar("La Activity anterior no se destruyó al recrear la tarea") { destruida(vieja) }
            var modo: Modo? = null
            var enVenta = -1
            inst.runOnMainSync {
                val vm = ViewModelProvider(a as MainActivity)[EscanerViewModel::class.java]
                modo = vm.config.value.modo
                enVenta = vm.venta.value.total
            }
            assertEquals(Modo.CONTEO, modo)
            assertEquals(Modo.CONTEO, prefs.leer().modo)
            // Al menos lo que había: la cámara puede leer el cartel del emulador mientras tanto y sumar a la venta.
            assertTrue("Se perdió lo leído en venta ($enVenta de $total)", enVenta >= total)
            // Y quedó en disco (sobrevive a que Android mate el proceso).
            esperar("La venta no se guardó en disco") {
                val f = File(File(ctx.filesDir, "listas"), "venta.csv")
                f.exists() && ListaLecturas.deTexto(f.readText()).total >= total
            }
        } finally {
            nueva?.let { a -> inst.runOnMainSync { a.finish() } }
            prefs.guardar(antes)
            listas.venta.value = previa
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

    /** La MainActivity al frente (RESUMED) que no sea [distintaDe]. */
    private fun esperarMainActivity(distintaDe: Activity?, ms: Long = 15_000): Activity {
        var encontrada: Activity? = null
        esperar("No quedó al frente una MainActivity nueva", ms) {
            inst.runOnMainSync {
                encontrada = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .firstOrNull { it is MainActivity && it !== distintaDe }
            }
            encontrada != null
        }
        return encontrada!!
    }

    private fun destruida(a: Activity): Boolean {
        var d = false
        inst.runOnMainSync { d = a.isDestroyed }
        return d
    }

    private fun esperar(mensaje: String, ms: Long = 10_000, condicion: () -> Boolean) {
        val hasta = SystemClock.elapsedRealtime() + ms
        while (!condicion() && SystemClock.elapsedRealtime() < hasta) SystemClock.sleep(50)
        assertTrue(mensaje, condicion())
    }

    private companion object {
        const val ETIQUETA = "AperturaAppTest"
    }
}
