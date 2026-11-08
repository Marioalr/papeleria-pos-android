package mx.papeleriapos.android.rapido

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.MainActivity
import mx.papeleriapos.android.catalogo.FuenteDeProductos
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.ui.ControladorDeMuestra
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/**
 * El escaneo rápido de verdad (la Activity con su ViewModel, con el controlador de muestra en lugar de la cámara): es
 * singleTask y en segundo plano sigue viva, así que el mosaico la trae con el mismo ViewModel (`onNewIntent`, y
 * `onRestart` si estaba detenida: en Android 10 o más, el intent llega después de `onRestart`; en 7 a 9, antes), y al
 * encender la pantalla vuelve a verse (`onRestart` sin intent). «Abrir la app» pide desbloquear con
 * ShadowKeyguardManager. Robolectric no tiene tareas ni bloqueo de verdad: eso se revisa en el celular (docs/pruebas.md).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EscaneoRapidoActividadTest {
    private val app = ApplicationProvider.getApplicationContext<Application>().also {
        shadowOf(it).grantPermissions(Manifest.permission.CAMERA)
    }
    private val bloqueo = shadowOf(app.getSystemService(KeyguardManager::class.java))
    private lateinit var vm: EscaneoRapidoViewModel
    private lateinit var controlador: ControladorQueAnota
    private var actividad: ActivityController<EscaneoRapidoActivity>? = null

    /** El de muestra, anotando con qué ciclo de vida lo abre la pantalla (el de la cámara de verdad lo sigue). */
    private class ControladorQueAnota(val muestra: ControladorDeMuestra) : ControladorEscaner by muestra {
        var dueno: LifecycleOwner? = null

        override fun abrir(dueno: LifecycleOwner, anchoVisor: Int, altoVisor: Int, rotacionPantalla: Int) {
            this.dueno = dueno
            muestra.abrir(dueno, anchoVisor, altoVisor, rotacionPantalla)
        }
    }

    /**
     * Abierta con el mosaico, con su ViewModel (puesto antes de `onCreate`, donde lo pide `by viewModels()`). La muestra,
     * en pausa: escaneando, la retícula pulsa sin fin y, al adelantar el reloj minutos con la pantalla a la vista,
     * Robolectric dibujaría cada cuadro de esos minutos.
     */
    private fun abrir(): ActivityController<EscaneoRapidoActivity> {
        val c = Robolectric.buildActivity(EscaneoRapidoActivity::class.java, intentEscaneoRapido(app))
        val fabrica = viewModelFactory {
            initializer {
                EscaneoRapidoViewModel(app, { _, config ->
                    ControladorQueAnota(ControladorDeMuestra(EstadoEscaner(fase = Fase.PAUSADO), config)).also { controlador = it }
                }) { FuenteDeProductos.dePrueba(app) }
            }
        }
        vm = ViewModelProvider(c.get().viewModelStore, fabrica)[EscaneoRapidoViewModel::class.java]
        actividad = c
        c.setup()
        idle()
        return c
    }

    @After
    fun cerrar() {
        actividad?.let { c ->
            if (!c.get().isDestroyed) {
                val estado = c.get().lifecycle.currentState
                c.get().finish()
                if (estado.isAtLeast(Lifecycle.State.RESUMED)) c.pause()
                if (estado.isAtLeast(Lifecycle.State.STARTED)) c.stop()
                c.destroy()
            }
        }
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun leer(codigo: String) {
        controlador.muestra.emitirLectura(codigo)
        idle()
    }

    private fun esperarProductos() {
        val limite = System.currentTimeMillis() + 5_000
        while (vm.catalogo.value == null) {
            check(System.currentTimeMillis() < limite) { "Los productos no llegaron" }
            idle()
            Thread.sleep(5)
        }
        idle()
    }

    /** Los estados por los que pasa el ciclo de vida que sigue la cámara, desde ahora. */
    private fun estadosDeLaCamara(c: ActivityController<EscaneoRapidoActivity>): List<Lifecycle.State> {
        val estados = mutableListOf<Lifecycle.State>()
        c.get().compuerta.lifecycle.addObserver(LifecycleEventObserver { fuente, _ -> estados += fuente.lifecycle.currentState })
        estados.clear()
        return estados
    }

    private val minuto = 60_000L

    /** El plazo que le queda, en ms, con lo que corrió el reloj mientras se dibujaba (Robolectric avanza 15 ms por cuadro). */
    private fun assertQueda(mensaje: String, ms: Long, bloqueado: Boolean = false) {
        val queda = vm.msParaCerrar(bloqueado)
        assertTrue("$mensaje: le quedan $queda ms, no $ms", queda in (ms - 1_000)..ms)
    }

    // ---- La cámara sigue a la compuerta ----

    @Test
    fun `la camara se abre con el ciclo de la compuerta`() {
        val c = abrir()
        assertSame(c.get().compuerta, controlador.dueno)
        assertEquals(Lifecycle.State.RESUMED, c.get().compuerta.lifecycle.currentState)
        c.pause().stop()
        assertEquals(Lifecycle.State.CREATED, c.get().compuerta.lifecycle.currentState)
    }

    // ---- El mosaico siempre abre (y es una apertura nueva) ----

    @Test
    fun `caducada, el mosaico la abre igual en android 9 o mas`() {
        val c = abrir()
        c.pause().stop()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(6))
        // Android 10 o más: onRestart (ya caducada) antes que el intent del mosaico. La cámara espera.
        c.restart()
        assertEquals(Lifecycle.State.CREATED, c.get().compuerta.lifecycle.currentState)
        assertFalse(c.get().isFinishing)
        c.newIntent(intentEscaneoRapido(app))
        assertTrue(c.get().compuerta.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        c.resume()
        idle()
        assertFalse("El toque al mosaico no se pierde", c.get().isFinishing)
        assertQueda("Una apertura nueva: el plazo completo", 5 * minuto)
    }

    @Test
    fun `caducada, el mosaico la abre igual en android 7 a 9`() {
        val c = abrir()
        c.pause().stop()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(6))
        // Android 7 a 9: el intent antes que onRestart.
        c.newIntent(intentEscaneoRapido(app)).restart().resume()
        idle()
        assertFalse(c.get().isFinishing)
        assertEquals(Lifecycle.State.RESUMED, c.get().compuerta.lifecycle.currentState)
        assertQueda("Una apertura nueva", 5 * minuto)
    }

    @Test
    fun `caducada, si solo se enciende la pantalla se cierra sin abrir la camara`() {
        val c = abrir()
        c.pause().stop()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(6))
        val estados = estadosDeLaCamara(c)
        c.restart().resume()
        idle()
        assertTrue(c.get().isFinishing)
        assertTrue("La cámara no abrió: $estados", estados.none { it.isAtLeast(Lifecycle.State.STARTED) })
    }

    @Test
    fun `el mosaico la trae como una apertura nueva y no se cierra a los pocos segundos`() {
        for (androidDiezOMas in listOf(true, false)) {
            val c = abrir()
            leer(CUADERNO)
            c.pause().stop()
            // Cuatro de sus cinco minutos ya pasaron.
            ShadowSystemClock.advanceBy(Duration.ofMinutes(4))
            if (androidDiezOMas) c.restart().newIntent(intentEscaneoRapido(app)).resume() else c.newIntent(intentEscaneoRapido(app)).restart().resume()
            idle()
            assertNull("$androidDiezOMas: lo leído antes ya no se ve", vm.leido.value)
            assertQueda("$androidDiezOMas: el plazo empieza otra vez", 5 * minuto)
            // Un minuto y medio después sigue ahí (con el plazo de antes ya se habría cerrado).
            ShadowSystemClock.advanceBy(Duration.ofSeconds(90))
            idle()
            assertFalse(c.get().isFinishing)
            cerrar()
        }
    }

    /**
     * Como se revisa en el celular (docs/pruebas.md, «En el Pixel 8 Pro o el emulador», punto 8): abierta sin bloqueo,
     * Inicio (detenida y viva: detrás nada la cierra; encender la pantalla no la trae, arriba está Inicio), la pantalla
     * apagada con el celular bloqueado y luego el mosaico. Bloqueado, el plazo es de un minuto: ya vencido, el toque abre
     * igual (con la 0.2 se perdía); a medias, empieza otra vez (con la 0.2 se cerraba al minuto de haberla abierto).
     */
    @Test
    fun `dejada en inicio y traida ya con el bloqueo, dura un minuto desde el toque`() {
        bloqueo.setIsDeviceSecure(true)
        for (androidDiezOMas in listOf(true, false)) {
            for (apagada in listOf(Duration.ofMinutes(2), Duration.ofSeconds(30))) {
                val caso = "${if (androidDiezOMas) "10+" else "7-9"}, ${apagada.seconds} s apagada"
                bloqueo.setKeyguardLocked(false)
                val c = abrir()
                c.pause().stop()
                bloqueo.setKeyguardLocked(true)
                ShadowSystemClock.advanceBy(apagada)
                idle()
                assertFalse("$caso: detrás nada la cierra", c.get().isFinishing)
                val mosaico = intentEscaneoRapido(app)
                if (androidDiezOMas) c.restart().newIntent(mosaico).resume() else c.newIntent(mosaico).restart().resume()
                idle()
                assertFalse("$caso: el toque no se pierde", c.get().isFinishing)
                assertQueda("$caso: un minuto desde el toque", minuto, bloqueado = true)
                ShadowSystemClock.advanceBy(Duration.ofSeconds(55))
                idle()
                assertFalse("$caso: no se cierra antes del minuto", c.get().isFinishing)
                ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
                idle()
                assertTrue("$caso: cumplido el minuto, se cierra sola", c.get().isFinishing)
                cerrar()
            }
        }
    }

    @Test
    fun `al encender la pantalla lo leido se borra pero el plazo sigue`() {
        val c = abrir()
        leer(CUADERNO)
        c.pause().stop()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(4))
        c.restart().resume()
        idle()
        assertFalse(c.get().isFinishing)
        assertNull(vm.leido.value)
        assertQueda("El plazo sigue", minuto)
        // Se cumple: se cierra sola.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
        idle()
        assertTrue(c.get().isFinishing)
    }

    // ---- «Abrir la app» ----

    private fun abiertaDesdeAqui(c: ActivityController<EscaneoRapidoActivity>): Intent? = shadowOf(c.get()).nextStartedActivity

    @Test
    fun `bloqueado, abre la app en precio con el producto al poner el pin`() {
        bloqueo.setIsDeviceSecure(true)
        bloqueo.setKeyguardLocked(true)
        val c = abrir()
        leer(CUADERNO)
        esperarProductos()
        c.get().abrirApp()
        // Mientras no conteste el bloqueo, nada se abre.
        assertNull(abiertaDesdeAqui(c))
        assertFalse(c.get().isFinishing)
        bloqueo.setKeyguardLocked(false)
        idle()
        val abierta = abiertaDesdeAqui(c)!!
        assertEquals(ComponentName(app, MainActivity::class.java), abierta.component)
        assertTrue("El mismo filtro que el ícono", abierta.filterEquals(Intent.makeMainActivity(ComponentName(app, MainActivity::class.java))))
        assertEquals(MainActivity.MODO_PRECIO, abierta.getStringExtra(MainActivity.EXTRA_MODO))
        assertEquals(CUADERNO, abierta.getStringExtra(MainActivity.EXTRA_PRODUCTO))
        val banderas = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals("0x${Integer.toHexString(abierta.flags)}", banderas, abierta.flags and banderas)
        assertEquals("Con ella el producto no llegaba a la app abierta", 0, abierta.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        assertTrue(c.get().isFinishing)
        assertNull("Una sola vez", abiertaDesdeAqui(c))
    }

    @Test
    fun `lo que no esta en el catalogo va con su codigo`() {
        val c = abrir()
        leer(NO_ESTA)
        esperarProductos()
        c.get().abrirApp()
        assertEquals(NO_ESTA, abiertaDesdeAqui(c)!!.getStringExtra(MainActivity.EXTRA_PRODUCTO))
    }

    @Test
    fun `sin bloqueo abre directo y sin leer nada no lleva producto`() {
        val c = abrir()
        c.get().abrirApp()
        val abierta = abiertaDesdeAqui(c)!!
        assertEquals(MainActivity.MODO_PRECIO, abierta.getStringExtra(MainActivity.EXTRA_MODO))
        assertFalse(abierta.hasExtra(MainActivity.EXTRA_PRODUCTO))
        assertTrue(c.get().isFinishing)
    }

    @Test
    fun `mientras espera el pin no se cierra sola y dos toques piden una sola vez`() {
        bloqueo.setIsDeviceSecure(true)
        bloqueo.setKeyguardLocked(true)
        val c = abrir()
        leer(CUADERNO)
        esperarProductos()
        c.get().abrirApp()
        // Un segundo toque: otro pedido registraría otro aviso (y el shadow contestaría con error al instante, soltándola).
        c.get().abrirApp()
        // Bloqueado, el plazo es de un minuto; tecleando el PIN no cuenta.
        ShadowSystemClock.advanceBy(Duration.ofMinutes(3))
        idle()
        assertFalse("Se cerró con el PIN a medias: el aviso se perdería", c.get().isFinishing)
        bloqueo.setKeyguardLocked(false)
        idle()
        assertEquals(CUADERNO, abiertaDesdeAqui(c)!!.getStringExtra(MainActivity.EXTRA_PRODUCTO))
        assertNull("Se abre una sola vez", abiertaDesdeAqui(c))
    }

    @Test
    fun `cancelado el pin sigue sobre el bloqueo y se puede volver a pedir`() {
        bloqueo.setIsDeviceSecure(true)
        bloqueo.setKeyguardLocked(true)
        val c = abrir()
        c.get().abrirApp()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(2))
        // Atrás en el PIN: el shadow contesta onDismissCancelled.
        bloqueo.setKeyguardLocked(true)
        idle()
        assertFalse(c.get().isFinishing)
        assertNull(abiertaDesdeAqui(c))
        assertTrue("Con su plazo desde cero", vm.msParaCerrar(bloqueado = true) in (minuto - 1_000)..minuto)
        // Otra vez «Abrir la app»: un pedido nuevo, que esta vez sí desbloquea.
        c.get().abrirApp()
        bloqueo.setKeyguardLocked(false)
        idle()
        assertEquals(ComponentName(app, MainActivity::class.java), abiertaDesdeAqui(c)!!.component)
        // Y sin abrir la app, cancelado, vuelve a cerrarse sola al minuto.
        val otra = abrir()
        bloqueo.setKeyguardLocked(true)
        otra.get().abrirApp()
        bloqueo.setKeyguardLocked(true)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
        idle()
        assertTrue(otra.get().isFinishing)
    }

    @Test
    fun `en api 25 no puede pedir el pin y abre directo con el producto`() {
        bloqueo.setIsDeviceSecure(true)
        bloqueo.setKeyguardLocked(true)
        val c = abrir()
        leer(CUADERNO)
        esperarProductos()
        val sdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.N_MR1)
        try {
            c.get().abrirApp()
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        }
        // La app aparece al desbloquear (MainActivity no se muestra sobre el bloqueo): basta con los extras.
        assertEquals(CUADERNO, abiertaDesdeAqui(c)!!.getStringExtra(MainActivity.EXTRA_PRODUCTO))
        assertTrue(c.get().isFinishing)
    }

    @Test
    @Config(shadows = [ShadowCapturaDeRecientes::class])
    fun `recientes no guarda su imagen y la ventana no lleva flag secure`() {
        ShadowCapturaDeRecientes.habilitada = null
        val c = abrir()
        // API 33+: en lugar de la imagen de lo que se veía sobre el bloqueo, el fondo del tema.
        assertEquals(false, ShadowCapturaDeRecientes.habilitada)
        // Lo que enseña es lo que la propuesta decidió enseñarle a cualquiera: se puede capturar y compartir.
        assertEquals(0, c.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }

    private companion object {
        const val CUADERNO = "7509990100019"
        const val NO_ESTA = "7509990100194"
    }
}

/** Anota lo que la Activity le pide al sistema de su imagen en Recientes (el ShadowActivity de serie no lo tiene). */
@Implements(Activity::class)
class ShadowCapturaDeRecientes : ShadowActivity() {
    @Implementation(minSdk = Build.VERSION_CODES.TIRAMISU)
    protected fun setRecentsScreenshotEnabled(enabled: Boolean) {
        habilitada = enabled
    }

    companion object {
        var habilitada: Boolean? = null
    }
}
