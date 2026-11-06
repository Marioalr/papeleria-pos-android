package mx.papeleriapos.android.rapido

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.android.principal.PreferenciasPiso
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.escaner.nucleo.Modo
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
 * En el núcleo, Precio se guarda como venta. Es singleTask: cuando vuelve, sigue el modo que la app tenga entonces.
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

    /** Como lo deja la app: el modo de la pantalla y, en el escáner, el del núcleo (Precio y Ticket escanean como venta). */
    private fun guardarModo(modo: ModoApp) {
        val prefs = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
        PreferenciasPiso(prefs).guardarModo(modo)
        val escaner = PreferenciasEscaner(prefs)
        escaner.guardar(escaner.leer().copy(modo = modo.delNucleo))
    }

    private fun vm(modo: ModoApp): EscaneoRapidoViewModel {
        guardarModo(modo)
        return EscaneoRapidoViewModel(app, { _, c -> ControladorDeMuestra(config = c) }, { listas })
    }

    private fun mostrar(vm: EscaneoRapidoViewModel) {
        compose.setContent { TemaEscaner(oscuro = false) { PantallaRapida(vm, alAbrirApp = {}, alCerrar = {}) } }
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun leer(vm: EscaneoRapidoViewModel, codigo: String) {
        (vm.controlador as ControladorDeMuestra).emitirLectura(codigo)
        idle()
    }

    /** En Precio los productos se leen con la primera lectura, en su hilo. */
    private fun esperarProductos(vm: EscaneoRapidoViewModel) {
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.catalogo.value != null
        }
        idle()
    }

    @Test
    fun `en precio el escaneo rapido ensena el precio y no suma al ticket`() {
        val vm = vm(ModoApp.PRECIO)
        assertEquals(ModoApp.PRECIO, vm.modo.value)
        mostrar(vm)
        leer(vm, CUADERNO)
        esperarProductos(vm)
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
        mostrar(vm)
        leer(vm, CUADERNO)
        assertEquals(1, listas.venta.value.total)
        // Una sola pieza, en singular.
        compose.onNodeWithText("Ticket · 1 pza leída ahora").assertExists()
        leer(vm, LAPIZ)
        compose.onNodeWithText("Ticket · 2 pzs leídas ahora").assertExists()
        val conteo = vm(ModoApp.CONTEO)
        leer(conteo, LAPIZ)
        assertEquals(1, listas.conteo.value.total)
        assertEquals(2, listas.venta.value.total)
    }

    // ---- Vuelve (el mosaico otra vez, o la pantalla que se enciende) con el modo de la app de ahora ----

    @Test
    fun `abierto en ticket y de vuelta con la app en precio ya no suma al ticket`() {
        val vm = vm(ModoApp.TICKET)
        mostrar(vm)
        leer(vm, CUADERNO)
        assertEquals(1, listas.venta.value.total)
        // Inicio, la app pasa a Precio (lo que guarda la pantalla principal) y el mosaico trae la misma pantalla.
        guardarModo(ModoApp.PRECIO)
        vm.alVolver()
        idle()
        assertEquals(ModoApp.PRECIO, vm.modo.value)
        assertTrue("Es otra apertura: lo de la anterior ya no se ve", vm.aqui.value.renglones.isEmpty())
        leer(vm, LAPIZ)
        // Antes de esperar los productos (en Precio se leen con la primera lectura): esto ya no depende de ellos.
        assertEquals("En Precio nada entra al ticket", listOf(CUADERNO to 1), listas.venta.value.renglones.map { it.codigo to it.cantidad })
        assertEquals("Solo lo de esta apertura", listOf(LAPIZ), vm.aqui.value.renglones.map { it.codigo })
        esperarProductos(vm)
        compose.onNodeWithText("Precio").assertExists()
        compose.onNodeWithText("Lápiz HB No. 2").assertExists()
        compose.onNodeWithText("\$5.50").assertExists()
        // Y de vuelta en Ticket, lo leído entra otra vez al ticket.
        guardarModo(ModoApp.TICKET)
        vm.alVolver()
        leer(vm, GOMA)
        assertEquals(listOf(GOMA to 1, CUADERNO to 1), listas.venta.value.renglones.map { it.codigo to it.cantidad })
        compose.onNodeWithText("Ticket · 1 pza leída ahora").assertExists()
    }

    @Test
    fun `de vuelta con la app en conteo lo contado va al conteo y no al ticket`() {
        val vm = vm(ModoApp.TICKET)
        mostrar(vm)
        leer(vm, CUADERNO)
        val muestra = vm.controlador as ControladorDeMuestra
        assertEquals(Modo.VENTA, muestra.config.value.modo)
        guardarModo(ModoApp.CONTEO)
        vm.alVolver()
        idle()
        assertEquals(ModoApp.CONTEO, vm.modo.value)
        assertEquals("El escáner toma el modo del núcleo que guardó la app", Modo.CONTEO, muestra.config.value.modo)
        leer(vm, LAPIZ)
        leer(vm, LAPIZ)
        assertEquals(listOf(LAPIZ to 2), listas.conteo.value.renglones.map { it.codigo to it.cantidad })
        assertEquals("El ticket queda como estaba", 1, listas.venta.value.total)
        compose.onNodeWithText("Conteo · 2 pzs leídas ahora").assertExists()
        // Volver sin que la app cambie de modo también es otra apertura: lo de aquí empieza de cero.
        vm.alVolver()
        idle()
        assertEquals(ModoApp.CONTEO, vm.modo.value)
        compose.onNodeWithText("Conteo · 0 pzs leídas ahora").assertExists()
        assertEquals(2, listas.conteo.value.total)
    }

    // ---- TalkBack en Precio ----

    /** Lo que la región viva le manda a TalkBack: el cambio de su descripción (con lo que dice). */
    private fun anunciosEnviados(): List<String> {
        val am = app.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return shadowOf(am).sentAccessibilityEvents
            .filter {
                it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    it.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION != 0
            }
            .map { it.contentDescription.toString().replace("\u200B", "") }
    }

    /** Los cambios de la semántica salen cada 100 ms (y el primero toma la foto de lo que hay). */
    private fun dejarQueSalganLosEventos() = repeat(3) {
        compose.mainClock.advanceTimeBy(200)
        idle()
    }

    @Test
    fun `en precio el mismo producto otra vez se vuelve a anunciar`() {
        val am = app.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        shadowOf(am).setEnabled(true)
        shadowOf(am).setTouchExplorationEnabled(true)
        val vm = vm(ModoApp.PRECIO)
        mostrar(vm)
        // Lo que ve TalkBack: el proveedor de accesibilidad de Compose, forzado como si el lector estuviera prendido.
        val v = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view
        val delegado = v.javaClass.getDeclaredField("composeAccessibilityDelegate").apply { isAccessible = true }.get(v)
        delegado.javaClass.getMethod("setAccessibilityForceEnabledForTesting\$ui", Boolean::class.javaPrimitiveType).invoke(delegado, true)
        v.accessibilityNodeProvider
        dejarQueSalganLosEventos()
        val antes = anunciosEnviados().size
        leer(vm, CUADERNO)
        esperarProductos(vm)
        dejarQueSalganLosEventos()
        // El cliente vuelve a preguntar: el mismo producto, otra vez.
        leer(vm, CUADERNO)
        dejarQueSalganLosEventos()
        val cuaderno = "Cuaderno profesional cuadro chico 100 hojas, \$46.00"
        assertEquals(listOf(cuaderno, cuaderno), anunciosEnviados().drop(antes).filter { it.contains("Cuaderno") })
        val anuncio = compose.onNodeWithTag("rapido-anuncio", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, anuncio.config[SemanticsProperties.LiveRegion])
        // El nombre y el precio a la vista ya no van en una región viva: se oirían dos veces.
        for (tag in listOf("rapido-producto", "rapido-precio")) {
            compose.onNode(hasTestTag(tag) and hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)), useUnmergedTree = true)
                .assertDoesNotExist()
        }
    }

    private companion object {
        const val CUADERNO = "7509990100019"
        const val LAPIZ = "7509990100026"
        const val GOMA = "7509990100033"
    }
}
