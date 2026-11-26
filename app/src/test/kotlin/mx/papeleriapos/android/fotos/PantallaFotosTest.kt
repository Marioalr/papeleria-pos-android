package mx.papeleriapos.android.fotos

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.caja.CanalDeFotos
import mx.papeleriapos.android.caja.FotoDto
import mx.papeleriapos.android.caja.FotoGuardadaDto
import mx.papeleriapos.android.caja.JsonDelPos
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.caja.PaqueteDto
import mx.papeleriapos.android.copia.Copia
import mx.papeleriapos.android.tema.TemaEscaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** «Fotos de productos», dibujada: la lista, el filtro, la búsqueda y la ficha de un producto. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w448dp-h949dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PantallaFotosTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val carpeta = TemporaryFolder()

    private val cuaderno = "0190c5a2-0000-7000-8000-000000000001"
    private val lapiz = "0190c5a2-0000-7000-8000-000000000002"

    private val canal = object : CanalDeFotos {
        val quitadas = ArrayList<String>()
        var falla: NoSeMando? = null
        override suspend fun ponerFoto(varianteId: String, jpeg: ByteArray) = FotoGuardadaDto(varianteId, "nueva")
        override suspend fun quitarFoto(varianteId: String) {
            falla?.let { throw it }
            quitadas.add(varianteId)
            copia = copia!!.sinFoto(varianteId)
        }
        override suspend fun bajarFoto(varianteId: String): ByteArray? = null
    }

    private var copia by mutableStateOf<Copia?>(null)

    private fun abrir(ultimo: String? = null) {
        val paquete = JsonDelPos.decodeFromString(
            PaqueteDto.serializer(),
            listOf("src/test/resources/paquete-prueba.json", "app/src/test/resources/paquete-prueba.json").map(::File).first { it.isFile }.readText(),
        )
        copia = Copia.dePaquete(paquete.copy(fotos = listOf(FotoDto(cuaderno, "aaaa"))), 1L)
        val fotos = Fotos(canal, AlmacenFotos(carpeta.newFolder()))
        compose.setContent { TemaEscaner(oscuro = false) { PantallaFotos(copia, ultimo, fotos) } }
        compose.waitForIdle()
    }

    private fun existe(tag: String) = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `de fabrica ensena los que no tienen foto y dice cuantos la tienen`() {
        abrir()
        compose.onNodeWithTag("fotos-cuantas").assertTextEquals("1 de 6 productos con foto")
        assertFalse("El que ya tiene foto no sale", existe("foto-de-0190C5A2-0000-7000-8000-000000000001"))
        assertTrue(existe("foto-de-$lapiz"))
        // Sin el filtro, salen todos.
        compose.onNodeWithTag("fotos-solo-sin").performClick()
        assertTrue(existe("foto-de-0190C5A2-0000-7000-8000-000000000001"))
    }

    @Test
    fun `se busca por nombre o por codigo`() {
        abrir()
        compose.onNodeWithTag("fotos-buscar").performTextInput("lapi")
        compose.waitForIdle()
        assertTrue(existe("foto-de-$lapiz"))
        assertEquals(1, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("fotos-lista")).fetchSemanticsNodes().single().children.size)
    }

    @Test
    fun `lo ultimo escaneado sale arriba y su ficha deja tomar, elegir y quitar`() {
        abrir(ultimo = cuaderno)
        compose.onNodeWithTag("foto-de-0190C5A2-0000-7000-8000-000000000001").assertIsDisplayed().performClick()
        compose.onNodeWithTag("ficha-foto").assertIsDisplayed()
        compose.onNodeWithTag("ficha-estado").assertTextEquals("La foto que ven las cajas")
        compose.onNodeWithTag("foto-tomar").assertTextEquals("Tomar otra foto")
        compose.onNodeWithTag("foto-elegir").assertIsDisplayed()
        // Quitarla falla sin red: lo dice y no cambia nada.
        canal.falla = NoSeMando("No contestó la caja principal.", sinRed = true)
        compose.onNodeWithTag("foto-quitar").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("ficha-error").assertTextEquals("No contestó la caja principal.")
        assertTrue(canal.quitadas.isEmpty())
        // Con red, se quita y la ficha queda sin foto.
        canal.falla = null
        compose.onNodeWithTag("foto-quitar").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, canal.quitadas.size)
        compose.onNodeWithTag("ficha-estado").assertTextEquals("Sin foto")
        compose.onNodeWithTag("foto-tomar").assertTextEquals("Tomar foto")
        assertFalse(existe("foto-quitar"))
        compose.onNodeWithTag("ficha-regresar").performScrollTo().performClick()
        compose.onNodeWithTag("fotos-cuantas").assertTextEquals("0 de 6 productos con foto")
    }

    @Test
    fun `sin la copia dice que hay que dar de alta el celular`() {
        copia = null
        val fotos = Fotos(canal, AlmacenFotos(carpeta.newFolder()))
        compose.setContent { TemaEscaner(oscuro = false) { PantallaFotos(null, null, fotos) } }
        compose.onNodeWithTag("pantalla-fotos").assertIsDisplayed()
        assertFalse(existe("fotos-lista"))
    }
}
