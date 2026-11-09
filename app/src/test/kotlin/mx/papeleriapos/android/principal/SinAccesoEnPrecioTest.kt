package mx.papeleriapos.android.principal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.ajustes.textoConexion
import mx.papeleriapos.android.caja.Conexion
import mx.papeleriapos.android.caja.EstadoConexion
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.tema.TemaEscaner
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cuando la caja le quita el acceso al celular y el dueño todavía no lo ve en La conexión, la pantalla principal lo dice
 * discreto al no encontrar un código en Precio, y el renglón de Ajustes también.
 */
@RunWith(AndroidJUnit4::class)
class SinAccesoEnPrecioTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `precio dice por que no encuentra nada solo mientras el aviso esta pendiente`() {
        val sinAcceso = mutableStateOf(true)
        compose.setContent {
            TemaEscaner(oscuro = false) {
                ContenidoPanel(
                    ModoApp.PRECIO, ConsultaPrecio.NoEsta("7509990000013"), ListaLecturas(), ListaLecturas(),
                    catalogo = null, acciones = AccionesPanel(), sinAcceso = sinAcceso.value,
                )
            }
        }
        compose.onNodeWithTag("no-esta").assertIsDisplayed()
        compose.onNodeWithTag("sin-acceso", useUnmergedTree = true).assertIsDisplayed()
        sinAcceso.value = false
        compose.onNodeWithTag("sin-acceso", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `el renglon de ajustes dice que la caja le quito el acceso`() {
        val texto = textoConexion(EstadoConexion(aviso = Conexion.SIN_ACCESO, sinAcceso = true, leida = true), null, dePrueba = false)
        assertTrue(texto, texto.contains("quitó el acceso"))
    }
}
