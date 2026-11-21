package mx.papeleriapos.android.principal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.caja.CajaParaMandarDto
import mx.papeleriapos.android.caja.JsonDelPos
import mx.papeleriapos.android.caja.PaqueteDto
import mx.papeleriapos.android.copia.Copia
import mx.papeleriapos.android.copia.CopiaTest
import mx.papeleriapos.android.copia.ProductosDeLaCopia
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.pos.precio.Fecha
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** «¿A qué caja?» dibujada: la caja sin la venta abierta apagada, «Mandar» con el rebote de 300 ms y el error en español. */
@RunWith(AndroidJUnit4::class)
class HojaMandarTest {
    @get:Rule
    val compose = createComposeRule()

    private val catalogo = ProductosDeLaCopia.armar(
        Copia.dePaquete(JsonDelPos.decodeFromString(PaqueteDto.serializer(), CopiaTest.textoDelPaquete()), 0L), Fecha.de(2026, 11, 8),
    )

    @Test
    fun `elige y manda, con la caja apagada en gris y el error a la vista`() {
        val lapiz = "0190c5a2-0000-7000-8000-000000000002"
        val ticket = ListaLecturas().agregarDelCatalogo(lapiz, 1L, cantidad = 3)
        val estado = mutableStateOf(
            EstadoMandar(
                cajas = listOf(
                    CajaParaMandarDto("c1", "Caja 1", "A", recibe = false),
                    CajaParaMandarDto("c2", "Caja 2", "B", recibe = true, atiende = "Ana", esperan = 1),
                ),
            ),
        )
        var mandadas = 0
        var cargas = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TemaEscaner {
                HojaMandar(
                    estado = estado.value,
                    ticket = ticket,
                    catalogo = catalogo,
                    alCargar = { cargas++ },
                    alElegir = { estado.value = estado.value.copy(elegida = it) },
                    alMandar = { mandadas++ },
                    alCerrar = {},
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("¿A qué caja?").assertExists()
        compose.onNodeWithText("Ticket · 3 art. · \$16.50").assertExists()
        compose.onNodeWithText("Sin la venta abierta: que entren a vender ahí.").assertExists()
        compose.onNodeWithText("Atiende Ana · 1 ticket en espera").assertExists()
        // El rebote: recién abierta, aunque haya caja elegida, el toque que suelta el gesto no manda.
        estado.value = estado.value.copy(elegida = "c2")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("mandar-a-la-caja").assertIsNotEnabled()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        // La que no tiene la venta abierta no se elige; la otra sí.
        estado.value = estado.value.copy(elegida = null)
        compose.waitForIdle()
        compose.onNodeWithTag("mandar-a-la-caja").assertIsNotEnabled()
        compose.onNodeWithTag("caja-A").performClick()
        assertEquals(null, estado.value.elegida)
        compose.onNodeWithTag("caja-B").performClick()
        assertEquals("c2", estado.value.elegida)
        compose.onNodeWithText("Mandar a Caja 2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("mandar-a-la-caja").assertIsEnabled().performClick()
        assertEquals(1, mandadas)
        assertEquals(1, cargas)

        estado.value = estado.value.copy(error = "La Caja 2 no lo recogió: revisa que tenga la venta abierta y vuelve a mandarlo.")
        compose.waitForIdle()
        compose.onNodeWithTag("mandar-error").assertExists()
        compose.onNodeWithText("La Caja 2 no lo recogió: revisa que tenga la venta abierta y vuelve a mandarlo.").assertExists()
    }
}
