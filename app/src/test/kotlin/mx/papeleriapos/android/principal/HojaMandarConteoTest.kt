package mx.papeleriapos.android.principal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.caja.CajaParaContarDto
import mx.papeleriapos.android.caja.ConteoMandadoDto
import mx.papeleriapos.android.caja.RenglonDeConteoDto
import mx.papeleriapos.android.tema.TemaEscaner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** «¿A qué caja va el conteo?» y «Conteos mandados» dibujadas: la caja que no recibe con su razón, mandar y cómo quedó. */
@RunWith(AndroidJUnit4::class)
class HojaMandarConteoTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `la caja que no recibe sale apagada con la razon del POS y se manda a la otra`() {
        val estado = mutableStateOf(
            EstadoMandarConteo(
                cajas = listOf(
                    CajaParaContarDto("c1", "Caja principal", "A", recibe = false, codigo = "sin-permiso", porQue = "Pedro no puede aceptar conteos."),
                    CajaParaContarDto("c2", "Caja 2", "B", recibe = true, atiende = "Ana", pendientes = 1),
                ),
                elegida = "c2",
            ),
        )
        var mandadas = 0
        var vender = 0
        compose.setContent {
            TemaEscaner {
                HojaMandarConteo(
                    estado = estado.value, contadas = 37, productos = 12,
                    alCargar = {}, alElegir = { estado.value = estado.value.copy(elegida = it) },
                    alMandar = { mandadas++ }, alVolverAVender = { vender++ }, alCerrar = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("37 pzs · 12 productos").assertExists()
        compose.onNodeWithText("Pedro no puede aceptar conteos.").assertExists()
        compose.onNodeWithText("Atiende Ana · 1 conteo por revisar").assertExists()
        compose.onNodeWithTag("conteo-caja-A").assertIsNotEnabled()
        // El rebote de 300 ms: el toque que abrió la hoja no manda.
        compose.mainClock.advanceTimeBy(MS_REBOTE_MANDAR + 50)
        compose.onNodeWithTag("mandar-conteo").assertIsEnabled().performClick()
        assertEquals(1, mandadas)
        compose.onNodeWithText("Mandar a Caja 2").assertExists()
        compose.onNodeWithTag("conteo-sin-mandar").performClick()
        assertEquals(1, vender)
    }

    @Test
    fun `conteos mandados dice como quedo cada lista y, al tocarla, cada producto`() {
        val conteo = ConteoMandadoDto(
            "l1", caja = "Caja 2", estado = "aceptada-con-cambios", contadoUtc = "2026-11-22T17:42:00Z", resolvio = "Ana López", productos = 2,
            renglones = listOf(
                RenglonDeConteoDto("v1", "Lápiz HB", contado = 29.0, aceptado = 27.0, aplicado = true, nota = "En la caja lo cambiaron a 27."),
                RenglonDeConteoDto("v2", "Goma blanca", contado = 3.0, nota = "Lo quitaron en la caja: no cambió."),
            ),
        )
        compose.setContent { TemaEscaner { ContenidoConteos(EstadoConteos(listOf(conteo)), alPreguntar = {}) } }
        compose.onNodeWithText("Aplicado con cambios").assertExists()
        compose.onNodeWithText("En la caja lo cambiaron a 27.").assertDoesNotExist()
        compose.onNodeWithTag("conteo-l1").performClick()
        compose.onNodeWithText("contaste 29 · quedó 27").assertExists()
        compose.onNodeWithText("Lo quitaron en la caja: no cambió.").assertExists()
    }
}
