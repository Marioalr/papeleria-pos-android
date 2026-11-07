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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Escala
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.android.principal.PreferenciasPiso
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.ui.ControladorDeMuestra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * El escaneo rápido (el mosaico «Precio», también sobre el bloqueo) fijo en Precio (docs/propuesta-interfaz.md, «Del
 * bolsillo al precio»: «Sobre el bloqueo solo se ve el precio»): de los productos de prueba, el nombre, el precio, el
 * mayoreo, la promoción y, de una presentación, su precio por pieza; nunca la existencia, el ticket ni el conteo, y lo
 * leído no entra a ninguna lista aunque la app esté en Ticket o en Conteo.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EscaneoRapidoTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>().also {
        shadowOf(it).grantPermissions(Manifest.permission.CAMERA)
    }

    /** Como lo deja la app: el modo de la pantalla y, en el escáner, el del núcleo (Precio y Ticket escanean como venta). */
    private fun guardarModo(modo: ModoApp) {
        val prefs = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
        PreferenciasPiso(prefs).guardarModo(modo)
        val escaner = PreferenciasEscaner(prefs)
        escaner.guardar(escaner.leer().copy(modo = modo.delNucleo))
    }

    private fun vm(productos: (suspend () -> CatalogoDeProductos)? = null): EscaneoRapidoViewModel =
        if (productos == null) EscaneoRapidoViewModel(app, { _, c -> ControladorDeMuestra(config = c) })
        else EscaneoRapidoViewModel(app, { _, c -> ControladorDeMuestra(config = c) }, productos)

    private var mostrada = false

    private fun mostrar(vm: EscaneoRapidoViewModel) {
        compose.setContent { TemaEscaner(oscuro = true) { PantallaRapida(vm, alAbrirApp = {}, alCerrar = {}) } }
        mostrada = true
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        if (mostrada) compose.waitForIdle()
    }

    /** Espera a [condicion], dejando correr el hilo principal (lo de otros hilos vuelve a él). */
    private fun hasta(condicion: () -> Boolean) {
        val limite = System.currentTimeMillis() + 5_000
        while (!condicion()) {
            check(System.currentTimeMillis() < limite) { "No pasó a tiempo" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        idle()
    }

    private fun leer(vm: EscaneoRapidoViewModel, codigo: String, simbologia: Simbologia = Simbologia.EAN_13) {
        (vm.controlador as ControladorDeMuestra).emitirLectura(codigo, simbologia)
        idle()
    }

    /** Los productos se leen con la primera lectura, en su hilo. */
    private fun esperarProductos(vm: EscaneoRapidoViewModel) = hasta { vm.catalogo.value != null }

    private fun texto(t: String) = compose.onAllNodesWithText(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Lo que se ve en el panel, junto. */
    private fun todoElTexto(): String = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        .fetchSemanticsNodes().joinToString(" | ") { n -> n.config[SemanticsProperties.Text].joinToString("") { it.text } }

    @Test
    fun `ensena el nombre, el precio y el mayoreo, y nunca la existencia`() {
        val vm = vm()
        mostrar(vm)
        compose.onNodeWithText("Apunta al código para ver su precio").assertExists()
        leer(vm, CUADERNO)
        esperarProductos(vm)
        compose.onNodeWithText("Cuaderno profesional cuadro chico 100 hojas").assertExists()
        compose.onNodeWithText("\$46.00").assertExists()
        compose.onNodeWithText("Desde 12: \$41.00 c/u").assertExists()
        // El sacapuntas: queda uno. En la app la tarjeta dice «Queda 1»; aquí no.
        leer(vm, SACAPUNTAS)
        compose.onNodeWithText("Sacapuntas metálico").assertExists()
        val todo = todoElTexto()
        for (nunca in listOf("Queda", "Existencia", "Ticket", "Conteo", "art.", "Al ticket")) {
            assertFalse("Sobre el bloqueo no se dice «$nunca»: $todo", todo.contains(nunca))
        }
    }

    @Test
    fun `ensena la promocion y el precio por pieza de una presentacion`() {
        val vm = vm()
        mostrar(vm)
        leer(vm, GOMA)
        esperarProductos(vm)
        compose.onNodeWithText("Regreso a clases: lleva 3 y paga 2").assertExists()
        // La caja de 12 lápices: su precio y, como en la tarjeta de la app, cuánto sale cada uno.
        leer(vm, CAJA_LAPICES)
        compose.onNodeWithText("Lápiz HB No. 2 · caja con 12").assertExists()
        compose.onNodeWithText("\$54.00").assertExists()
        compose.onNodeWithText("\$4.50 c/u (12)").assertExists()
        assertFalse("La promoción era de la goma", texto("Regreso a clases: lleva 3 y paga 2"))
    }

    @Test
    fun `lo que no esta dice no esta en el catalogo con su codigo`() {
        val vm = vm()
        mostrar(vm)
        leer(vm, NO_ESTA)
        esperarProductos(vm)
        compose.onNodeWithText("No está en el catálogo").assertExists()
        // El código, en el panel (la retícula del escáner también lo enseña, un momento).
        compose.onNodeWithTag("rapido-codigo").assertTextEquals(NO_ESTA)
    }

    @Test
    fun `mientras cargan los productos ensena el codigo sin decir que no esta`() {
        val productos = CompletableDeferred<CatalogoDeProductos>()
        val vm = vm { productos.await() }
        mostrar(vm)
        leer(vm, CUADERNO)
        compose.onNodeWithTag("rapido-codigo").assertTextEquals(CUADERNO)
        assertFalse(texto("No está en el catálogo"))
        // Llegan: ya con su nombre y su precio.
        productos.complete(CatalogoDeProductos.deCsv(app.assets.open(CatalogoDeProductos.ARCHIVO_DE_PRUEBA).bufferedReader().use { it.readText() }))
        esperarProductos(vm)
        compose.onNodeWithText("Cuaderno profesional cuadro chico 100 hojas").assertExists()
        assertFalse(texto("No está en el catálogo"))
    }

    @Test
    fun `aunque la app este en ticket o en conteo nada entra a sus listas`() {
        // Las de la app, en disco: las mismas que ve la pantalla principal.
        val listas = ListasCompartidas.delProceso(app)
        hasta { listas.cargadas }
        for (modo in listOf(ModoApp.TICKET, ModoApp.CONTEO)) {
            guardarModo(modo)
            val vm = vm()
            assertEquals("El escáner, en el modo del núcleo de Precio", Modo.VENTA, vm.controlador.config.value.modo)
            val venta = listas.venta.value
            val conteo = listas.conteo.value
            leer(vm, CUADERNO)
            leer(vm, CUADERNO)
            esperarProductos(vm)
            assertSame("$modo: el ticket no cambia", venta, listas.venta.value)
            assertSame("$modo: el conteo no cambia", conteo, listas.conteo.value)
            assertEquals(2, vm.leido.value!!.numero)
        }
    }

    @Test
    fun `al volver lo leido se borra y solo el mosaico reinicia el plazo`() {
        val vm = vm()
        leer(vm, CUADERNO)
        val minuto = 60_000L
        ShadowSystemClock.advanceBy(Duration.ofMinutes(3))
        // Se encendió la pantalla: lo de antes ya no se ve, pero el plazo sigue (5 minutos sin escanear).
        vm.alVolver()
        assertNull(vm.leido.value)
        assertEquals(2 * minuto, vm.msParaCerrar(bloqueado = false))
        // El mosaico la trajo: una apertura nueva, con el plazo completo.
        leer(vm, CUADERNO)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(4))
        vm.aperturaNueva()
        assertNull(vm.leido.value)
        assertEquals(5 * minuto, vm.msParaCerrar(bloqueado = false))
        assertEquals("Con el celular bloqueado, un minuto", minuto, vm.msParaCerrar(bloqueado = true))
    }

    @Test
    fun `abrir la app lleva la clave del producto o, si no esta, el codigo`() {
        // Un UPC-A: lo leído es el GTIN-13 con 0 adelante y en el catálogo está con sus 12 dígitos.
        val upc = Producto("099999000061", "Producto de prueba UPC", "Prueba", listOf(Escala(1, 1000)), existencia = null)
        val productos = CompletableDeferred<CatalogoDeProductos>()
        val vm = vm { productos.await() }
        runBlocking { assertNull("Sin leer nada", vm.productoParaAbrir()) }
        leer(vm, "0099999000061", Simbologia.UPC_A)
        // Los productos todavía no llegan: espera a saber su clave.
        val clave = CoroutineScope(Dispatchers.Unconfined).async { vm.productoParaAbrir() }
        assertFalse(clave.isCompleted)
        productos.complete(CatalogoDeProductos(listOf(upc)))
        assertEquals("099999000061", runBlocking { clave.await() })
        leer(vm, NO_ESTA)
        assertEquals(NO_ESTA, runBlocking { vm.productoParaAbrir() })
    }

    // ---- TalkBack ----

    /** Lo que la región viva le manda a TalkBack: el cambio de su descripción (con lo que dice). */
    private fun anunciosEnviados(): List<String> {
        val am = app.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return shadowOf(am).sentAccessibilityEvents
            .filter {
                it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    it.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION != 0
            }
            .map { it.contentDescription.toString().replace("​", "") }
    }

    /** Los cambios de la semántica salen cada 100 ms (y el primero toma la foto de lo que hay). */
    private fun dejarQueSalganLosEventos() = repeat(3) {
        compose.mainClock.advanceTimeBy(200)
        idle()
    }

    @Test
    fun `cada lectura se anuncia, tambien el mismo producto otra vez`() {
        val am = app.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        shadowOf(am).setEnabled(true)
        shadowOf(am).setTouchExplorationEnabled(true)
        val vm = vm()
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
        leer(vm, NO_ESTA)
        dejarQueSalganLosEventos()
        val cuaderno = "Cuaderno profesional cuadro chico 100 hojas, \$46.00"
        assertEquals(listOf(cuaderno, cuaderno, "No está en el catálogo"), anunciosEnviados().drop(antes))
        val anuncio = compose.onNodeWithTag("rapido-anuncio", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, anuncio.config[SemanticsProperties.LiveRegion])
        // Lo que se ve no va en una región viva: se oiría dos veces.
        val visto = hasTestTag("rapido-leido") or hasAnyAncestor(hasTestTag("rapido-leido"))
        compose.onNode(visto and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true).assertDoesNotExist()
    }

    private companion object {
        const val CUADERNO = "7509990100019"
        const val GOMA = "7509990100033"
        const val SACAPUNTAS = "7509990100040"
        const val CAJA_LAPICES = "7509990100200"
        const val NO_ESTA = "7509990100194"
    }
}
