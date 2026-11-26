package mx.papeleriapos.android.principal

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.caja.CajaParaMandarDto
import mx.papeleriapos.android.caja.CobroHechoDto
import mx.papeleriapos.android.caja.CotizacionDto
import mx.papeleriapos.android.caja.VentaCobradaDto
import mx.papeleriapos.android.tema.TemaEscaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La hoja de cobro y «Cobrado», dibujadas: el total, lo que entregó el cliente, el cambio y los toques de rebote. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w448dp-h949dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HojaCobrarTest {
    @get:Rule
    val compose = createComposeRule()

    private val caja1 = CajaParaMandarDto("c1", "Caja 1", "A", recibe = true, turnoAbierto = true)
    private val caja2 = CajaParaMandarDto("c2", "Caja 2", "B", turnoAbierto = true)

    /** $159.50 exactos: $160 en efectivo. */
    private val listo = EstadoCobro(
        totalLocal = 15_950, articulos = 8,
        cotizacion = CotizacionDto(totalCentavos = 15_950, efectivoCentavos = 16_000, redondeoCentavos = 50),
        cajas = listOf(caja1), elegida = "c1",
    )

    private var estado by mutableStateOf(listo)
    private var cobros = 0
    private var mandar = 0

    private fun abrir(inicial: EstadoCobro = listo) {
        estado = inicial
        val acciones = AccionesCobro(
            forma = { estado = estado.copy(forma = it) },
            recibir = { estado = estado.copy(recibidoCentavos = it) },
            referencia = { estado = estado.copy(referencia = it) },
            caja = { estado = estado.copy(elegida = it) },
            imprimir = { estado = estado.copy(imprimir = it) },
            cobrar = { cobros++ },
            mandar = { mandar++ },
        )
        compose.setContent { TemaEscaner(oscuro = false) { HojaCobrar(estado, acciones) {} } }
        compose.waitForIdle()
    }

    private fun pasarElRebote() {
        compose.mainClock.advanceTimeBy(MS_REBOTE_COBRAR + 50)
        compose.waitForIdle()
    }

    @Test
    fun `efectivo y exacto ya vienen elegidos y el total va redondeado al peso`() {
        abrir()
        compose.onNodeWithTag("cobro-total").assertTextEquals("\$160.00")
        compose.onNodeWithTag("cobro-detalle").assertTextEquals("Redondeado al peso, como en la caja: el exacto es \$159.50")
        compose.onNodeWithTag("cobro-cambio", useUnmergedTree = true).assertTextEquals("\$0.00")
        compose.onNodeWithTag("cobro-caja").assertTextEquals("Entra al turno de la Caja 1: el efectivo va a su cajón.")
        pasarElRebote()
        compose.onNodeWithTag("cobrar-ahora").assertIsEnabled().assertTextEquals("Cobrar \$160.00")
        compose.onNodeWithTag("cobrar-ahora").performClick()
        assertEquals(1, cobros)
    }

    @Test
    fun `cobrar no acepta el toque de rebote al abrirse`() {
        compose.mainClock.autoAdvance = false
        abrir()
        compose.onNodeWithTag("cobrar-ahora").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(MS_REBOTE_COBRAR + 50)
        compose.onNodeWithTag("cobrar-ahora").assertIsEnabled()
    }

    @Test
    fun `con el billete que entrego sale el cambio, y con otro monto se teclea`() {
        abrir()
        // Los tres billetes que siguen a $160.
        for (b in listOf("200", "500", "1000")) compose.onNodeWithTag("recibido-$b").assertIsDisplayed()
        compose.onNodeWithTag("recibido-500").performClick()
        compose.onNodeWithTag("cobro-cambio", useUnmergedTree = true).assertTextEquals("\$340.00")
        // Otro monto: el teclado, en la misma hoja.
        compose.onNodeWithTag("recibido-otro").performClick()
        for (t in listOf("tecla-1", "tecla-7", "tecla-0")) compose.onNodeWithTag(t).performClick()
        pasarElRebote()
        compose.onNodeWithTag("cobrar-ahora").assertIsNotEnabled()
        compose.onNodeWithTag("recibido-listo").performClick()
        assertEquals(17_000L, estado.recibidoCentavos)
        compose.onNodeWithTag("cobro-cambio", useUnmergedTree = true).assertTextEquals("\$10.00")
        compose.onNodeWithTag("recibido-otro").assertTextEquals("\$170.00")
        // De menos: dice cuánto falta y no deja cobrar.
        compose.onNodeWithTag("recibido-otro").performClick()
        for (t in listOf("tecla-1", "tecla-0", "tecla-0")) compose.onNodeWithTag(t).performClick()
        compose.onNodeWithTag("recibido-listo").performClick()
        compose.onNodeWithTag("cobro-cambio", useUnmergedTree = true).assertTextEquals("\$60.00")
        compose.onNodeWithTag("cobrar-ahora").assertIsNotEnabled()
        // Y de vuelta a exacto.
        compose.onNodeWithTag("recibido-exacto").performClick()
        assertNull(estado.recibidoCentavos)
        compose.onNodeWithTag("cobrar-ahora").assertIsEnabled()
    }

    @Test
    fun `tarjeta y transferencia cobran el exacto`() {
        abrir()
        compose.onNodeWithTag("forma-TARJETA").performClick()
        compose.onNodeWithTag("cobro-total").assertTextEquals("\$159.50")
        compose.onNodeWithTag("cobro-referencia").assertIsDisplayed()
        compose.onNodeWithTag("cobro-caja").assertTextEquals("Entra al turno de la Caja 1.")
        pasarElRebote()
        compose.onNodeWithTag("cobrar-ahora").assertTextEquals("Cobrar \$159.50")
        compose.onNodeWithTag("forma-TRANSFERENCIA").performClick()
        compose.onNodeWithTag("cobro-total").assertTextEquals("\$159.50")
    }

    @Test
    fun `mientras contesta la caja principal lo dice y no deja cobrar, y los errores se ensenan`() {
        abrir(EstadoCobro(totalLocal = 15_950, articulos = 8))
        compose.onNodeWithTag("cobro-detalle").assertTextEquals("Confirmando el total con la caja principal…")
        pasarElRebote()
        compose.onNodeWithTag("cobrar-ahora").assertIsNotEnabled()
        estado = estado.copy(error = SIN_TURNO_PARA_COBRAR, cotizacion = listo.cotizacion, cajas = emptyList())
        compose.waitForIdle()
        compose.onNodeWithTag("cobro-error").assertTextEquals(SIN_TURNO_PARA_COBRAR)
        compose.onNodeWithTag("cobro-reintentar").assertIsDisplayed()
        compose.onNodeWithTag("cobrar-ahora").assertIsNotEnabled()
        compose.onNodeWithTag("cobro-mandar").performClick()
        assertEquals(1, mandar)
    }

    @Test
    fun `con dos cajas con turno se elige en cual entra`() {
        abrir(listo.copy(cajas = listOf(caja1, caja2), elegida = null))
        pasarElRebote()
        compose.onNodeWithTag("cobrar-ahora").assertIsNotEnabled()
        compose.onNodeWithTag("caja-cobro-B").performScrollTo().performClick()
        assertEquals("c2", estado.elegida)
        compose.onNodeWithTag("cobro-caja").assertTextEquals("Entra al turno de la Caja 2: el efectivo va a su cajón.")
        compose.onNodeWithTag("cobrar-ahora").assertIsEnabled()
    }

    @Test
    fun `cobrado ensena el cambio en grande, el folio y donde dejar el efectivo`() {
        val venta = VentaCobradaDto(
            id = "t1", folio = "A-000124", caja = "Caja 1", articulos = 8.0, totalCentavos = 15_950, redondeoCentavos = 50,
            cobradoCentavos = 16_000, recibidoCentavos = 50_000, cambioCentavos = 34_000,
            cobros = listOf(CobroHechoDto(0, 16_000, 50_000, 34_000)),
        )
        var cerrada = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { TemaEscaner(oscuro = false) { HojaCobrado(venta) { cerrada++ } } }
        compose.onNodeWithTag("cobrado-cambio").assertTextEquals("\$340.00")
        compose.onNodeWithTag("cobrado-detalle").assertTextEquals("\$160.00 · 8 art. · efectivo")
        compose.onNodeWithTag("cobrado-folio").assertTextEquals("Folio A-000124 · Caja 1")
        // «Siguiente cliente» sale donde estaba «Cobrar»: no acepta el toque de rebote.
        compose.onNodeWithTag("siguiente-cliente").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(MS_REBOTE_COBRADO + 50)
        compose.onNodeWithTag("siguiente-cliente").assertIsEnabled().performClick()
        assertEquals(1, cerrada)
    }

    @Test
    fun `imprimir ticket sale marcada y se quita con un toque`() {
        abrir()
        compose.onNodeWithTag("cobro-imprimir").assertIsDisplayed()
        assertEquals(true, estado.imprimir)
        compose.onNodeWithTag("cobro-imprimir").performClick()
        assertEquals(false, estado.imprimir)
        compose.onNodeWithTag("cobro-imprimir").performClick()
        assertEquals(true, estado.imprimir)
    }

    @Test
    fun `cobrado dice si se pidio el ticket`() {
        val venta = VentaCobradaDto(folio = "A-000124", caja = "Caja 1", articulos = 1.0, cobradoCentavos = 2300, cobros = listOf(CobroHechoDto(1, 2300)))
        var pidio by mutableStateOf(true)
        compose.setContent { TemaEscaner(oscuro = false) { HojaCobrado(venta, pidioImprimir = pidio) {} } }
        compose.onNodeWithTag("cobrado-ticket").assertTextEquals("Se pidió imprimir el ticket. Si no sale, imprímelo en la Caja 1, desde sus ventas del día.")
        pidio = false
        compose.onNodeWithTag("cobrado-ticket").assertTextEquals("Sin ticket. Si lo piden, se imprime en la Caja 1, desde sus ventas del día.")
    }
}
