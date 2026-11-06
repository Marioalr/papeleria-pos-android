package mx.papeleriapos.android.rapido

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.android.principal.PreferenciasPiso
import mx.papeleriapos.escaner.nucleo.Modo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * El escaneo rápido de verdad (la Activity con su ViewModel y el controlador de la cámara, que en Robolectric no lee
 * nada): es singleTask y en segundo plano sigue viva, así que el mosaico la trae con el mismo ViewModel (`onNewIntent` y
 * `onRestart`), y al encender la pantalla vuelve a verse (`onRestart`). Las dos veces toma el modo que la app tenga
 * entonces (docs/propuesta-interfaz.md, «Precio»: en Precio el escaneo rápido no suma al ticket).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EscaneoRapidoActividadTest {
    private val app = ApplicationProvider.getApplicationContext<Application>().also {
        shadowOf(it).grantPermissions(Manifest.permission.CAMERA)
    }

    /** Como lo deja la app: el modo de la pantalla y, en el escáner, el del núcleo. */
    private fun guardarModo(modo: ModoApp) {
        val prefs = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
        PreferenciasPiso(prefs).guardarModo(modo)
        val escaner = PreferenciasEscaner(prefs)
        escaner.guardar(escaner.leer().copy(modo = modo.delNucleo))
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `al volver la misma pantalla toma el modo que la app tenga entonces`() {
        guardarModo(ModoApp.TICKET)
        val c = Robolectric.buildActivity(EscaneoRapidoActivity::class.java, intentEscaneoRapido(app)).setup()
        try {
            val vm = ViewModelProvider(c.get())[EscaneoRapidoViewModel::class.java]
            assertEquals(ModoApp.TICKET, vm.modo.value)
            // Inicio: se queda detenida en su tarea. Mientras, la app pasa a Conteo.
            c.pause().stop()
            guardarModo(ModoApp.CONTEO)
            // El mosaico otra vez: la misma vuelve a verse y le llega el intent (en Android 9 o más, en ese orden).
            c.restart().newIntent(intentEscaneoRapido(app)).resume()
            idle()
            assertFalse(c.get().isFinishing)
            assertSame("La misma pantalla, con su ViewModel", vm, ViewModelProvider(c.get())[EscaneoRapidoViewModel::class.java])
            assertEquals(ModoApp.CONTEO, vm.modo.value)
            assertEquals("El escáner, con el modo del núcleo que guardó la app", Modo.CONTEO, vm.controlador.config.value.modo)
            // Igual con el intent antes (Android 7 y 8), ahora con la app en Precio.
            c.pause().stop()
            guardarModo(ModoApp.PRECIO)
            c.newIntent(intentEscaneoRapido(app)).restart().resume()
            idle()
            assertEquals(ModoApp.PRECIO, vm.modo.value)
            assertEquals(Modo.VENTA, vm.controlador.config.value.modo)
            // Se apaga la pantalla, la app pasa a Ticket y al encenderla vuelve a verse, sin intent nuevo.
            c.pause().stop()
            guardarModo(ModoApp.TICKET)
            c.restart().resume()
            idle()
            assertEquals(ModoApp.TICKET, vm.modo.value)
            // Sin detenerse (con el panel de notificaciones encima) el mosaico solo le da onNewIntent: también relee.
            c.pause()
            guardarModo(ModoApp.CONTEO)
            c.newIntent(intentEscaneoRapido(app)).resume()
            idle()
            assertEquals(ModoApp.CONTEO, vm.modo.value)
        } finally {
            c.get().finish()
            c.pause().stop().destroy()
            idle()
        }
    }
}
