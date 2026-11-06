package mx.papeleriapos.android.rapido

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.android.principal.PreferenciasPiso
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.escaner.ui.ControladorDeMuestra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * El escaneo rápido (el mosaico, también sobre el bloqueo) en el modo de la app: en Precio solo enseña, con su nombre y
 * su precio, y nada entra al ticket (docs/propuesta-interfaz.md, «Precio»: «Escanear solo enseña. Nada entra al ticket»).
 * En el núcleo, Precio se guarda como venta.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EscaneoRapidoTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>().also {
        shadowOf(it).grantPermissions(Manifest.permission.CAMERA)
    }
    private val listas = ListasCompartidas()

    private fun vm(modo: ModoApp): EscaneoRapidoViewModel {
        val prefs = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
        // Como lo deja la app: el modo de la pantalla y, en el escáner, el del núcleo (Precio y Ticket escanean como venta).
        PreferenciasPiso(prefs).guardarModo(modo)
        val escaner = PreferenciasEscaner(prefs)
        escaner.guardar(escaner.leer().copy(modo = modo.delNucleo))
        return EscaneoRapidoViewModel(app, { _, c -> ControladorDeMuestra(config = c) }, { listas })
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    @Test
    fun `en precio el escaneo rapido ensena el precio y no suma al ticket`() {
        val vm = vm(ModoApp.PRECIO)
        assertEquals(ModoApp.PRECIO, vm.modo)
        compose.setContent { TemaEscaner(oscuro = false) { PantallaRapida(vm, alAbrirApp = {}, alCerrar = {}) } }
        idle()
        (vm.controlador as ControladorDeMuestra).emitirLectura("7509990100019")
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.catalogo.value != null
        }
        idle()
        assertTrue("Nada entra al ticket", listas.venta.value.renglones.isEmpty())
        assertEquals(1, vm.aqui.value.total)
        compose.onNodeWithTag("rapido-total").assertExists()
        compose.onNodeWithText("Precio").assertExists()
        compose.onNodeWithText("Cuaderno profesional cuadro chico 100 hojas").assertExists()
        compose.onNodeWithText("\$46.00").assertExists()
    }

    @Test
    fun `en ticket y en conteo suma a su lista como antes`() {
        val vm = vm(ModoApp.TICKET)
        compose.setContent { TemaEscaner(oscuro = false) { PantallaRapida(vm, alAbrirApp = {}, alCerrar = {}) } }
        idle()
        (vm.controlador as ControladorDeMuestra).emitirLectura("7509990100019")
        idle()
        assertEquals(1, listas.venta.value.total)
        compose.onNodeWithText("Ticket · 1 pzs leídas ahora").assertExists()
        val conteo = vm(ModoApp.CONTEO)
        (conteo.controlador as ControladorDeMuestra).emitirLectura("7509990100026")
        idle()
        assertEquals(1, listas.conteo.value.total)
        assertEquals(1, listas.venta.value.total)
    }
}
