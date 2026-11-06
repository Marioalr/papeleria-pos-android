package mx.papeleriapos.android.principal

import android.app.Application
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * La letra al 200 % en el chico (docs/propuesta-interfaz.md, «Letra al 200 %»), con la escala no lineal de Android 14
 * (Robolectric con la API 35): lo que encontró la revisión, cada caso como prueba de regresión.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = CHICO, fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PantallaPrincipalLetraGrandeTest : PruebaDePantalla() {

    private fun tocarBoton() {
        compose.onNodeWithTag("boton-rapido").performTouchInput { down(center); up() }
        idle()
    }

    /** El nodo con [texto] dentro de la tarjeta de Precio. */
    private fun enLaTarjeta(texto: String) =
        compose.onNode(hasAnyAncestor(hasTestTag("tarjeta-precio")) and hasText(texto), useUnmergedTree = true)

    @Test
    fun `la tarjeta de precio no pierde nada con la letra al 200`() {
        abrir(ModoApp.PRECIO)
        assertEquals(2f, compose.density.fontScale, 0.01f)
        leer(CUADERNO)
        captura("regresion/precio-200-cuaderno")
        val panel = limites("panel")!!
        val barra = limites("barra-modos")!!
        // El precio y «Al ticket» se ven enteros, juntos, dentro del panel: sin desplazar.
        for (tag in listOf("precio", "al-ticket")) {
            val n = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val visto = enDp(n.boundsInRoot)
            val entero = sinRecorte(n)
            assertTrue("$tag cortado: $visto de $entero", abs(visto.height - entero.height) < 0.5f && entero.height > 0f)
            assertTrue("$tag fuera del panel: $entero", entero.top >= panel.top && entero.bottom <= barra.top)
        }
        // Lo demás no desaparece: si no cabe, la tarjeta se desplaza y ahí está.
        for (texto in listOf("Desde 12: \$41.00 c/u", "Existencia: 8", "Escolar · $CUADERNO")) {
            val nodo = enLaTarjeta(texto)
            assertTrue("«$texto» sin alto", sinRecorte(nodo.fetchSemanticsNode()).height > 0f)
            nodo.performScrollTo()
            idle()
            val visto = enDp(nodo.fetchSemanticsNode().boundsInRoot)
            assertTrue("«$texto» no se alcanza: $visto", visto.height > 0f && visto.bottom <= barra.top + 0.5f)
        }
        leer(SACAPUNTAS)
        assertTrue(sinRecorte(enLaTarjeta("Queda 1").fetchSemanticsNode()).height > 0f)
        leer(GOMA)
        val promocion = enLaTarjeta("Regreso a clases: lleva 3 y paga 2")
        promocion.performScrollTo()
        idle()
        assertTrue(enDp(promocion.fetchSemanticsNode().boundsInRoot).height > 0f)
        captura("regresion/precio-200-goma")
        // El siguiente producto empieza arriba: su precio se ve aunque el de antes se haya desplazado.
        leer(CUADERNO)
        val precio = compose.onNodeWithTag("precio", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("El precio del nuevo quedó desplazado", abs(enDp(precio.boundsInRoot).height - sinRecorte(precio).height) < 0.5f)
    }

    @Test
    fun `la pista queda arriba de la etiqueta de arriba sin encimarse`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        for (zurdo in listOf(false, true)) {
            vm.piso.cambiarZurdo(zurdo)
            idle()
            tocarBoton()
            val pista = limites("pista-deslizar")!!
            val etiqueta = limites("etiqueta-arriba")!!
            assertTrue("Zurdo=$zurdo: la pista ($pista) pisa la etiqueta de ↑ ($etiqueta)", pista.bottom <= etiqueta.top)
            assertTrue("Dentro del margen de 16 dp", pista.left >= 16f - 0.5f && pista.right <= 360f - 16f + 0.5f)
            captura("regresion/menu-200-${if (zurdo) "zurdo" else "diestro"}")
            atras()
        }
    }

    @Test
    fun `la burbuja y el aviso de abajo no se cortan`() {
        abrir(ModoApp.TICKET)
        val burbuja = disposicion(compose.onNodeWithTag("pista-boton", useUnmergedTree = true).fetchSemanticsNode())!!
        assertFalse("La burbuja se corta: ${burbuja.lineCount} renglones", burbuja.hasVisualOverflow)
        captura("regresion/burbuja-200")
        leer(CUADERNO)
        tocarBoton()
        // ↑ «Mandar», apagada: el aviso trae su razón completa.
        compose.onNodeWithTag("opcion-arriba").performClick()
        idle()
        val texto = compose.onNode(hasAnyAncestor(hasTestTag("aviso")) and hasText("Mandarlo a una caja", substring = true), useUnmergedTree = true)
            .fetchSemanticsNode()
        val aviso = disposicion(texto)!!
        assertFalse("El aviso se corta en «…»", (0 until aviso.lineCount).any { aviso.isLineEllipsized(it) })
        captura("regresion/aviso-200")
    }

    @Test
    fun `en el ticket completo nada se parte y el numero de la pestana no se recorta`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        leer(LAPIZ)
        leer(GOMA)
        // Elegido, Ticket no lleva el número (lo dice el encabezado) y los otros modos se quedan con su ícono.
        assertFalse(existe("numero-ticket"))
        assertEquals("Ticket · 4 art.", textoDe(compose.onNode(hasAnyAncestor(hasTestTag("encabezado-ticket")) and hasText("Ticket", substring = true), useUnmergedTree = true).fetchSemanticsNode()))
        val precio = compose.onNodeWithTag("modo-PRECIO").fetchSemanticsNode()
        assertEquals("El nombre, para TalkBack", "Precio", precio.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single())
        // Desde otro modo, el número se ve entero y dentro de la barra (no recortado ni encima de lo de arriba).
        vm.cambiarModoApp(ModoApp.PRECIO)
        idle()
        val numero = compose.onNodeWithTag("numero-ticket", useUnmergedTree = true).fetchSemanticsNode()
        val visto = enDp(numero.boundsInRoot)
        val entero = sinRecorte(numero)
        assertTrue("El número del ticket se recorta: $visto de $entero", abs(visto.top - entero.top) < 0.5f && abs(visto.height - entero.height) < 0.5f)
        assertTrue("El número sale de la barra: $entero", entero.top >= limites("barra-modos")!!.top)
        captura("regresion/barra-200")
        vm.cambiarModoApp(ModoApp.TICKET)
        idle()
        vm.piso.abrir(Hoja.Ticket)
        idle()
        compose.onNodeWithTag("lista-ticket").performScrollToNode(hasTestTag("buscar-ticket"))
        idle()
        for (t in listOf("Buscar", "Impresiones", "Mandar a una caja", "Empezar otro ticket")) {
            val n = compose.onNode(hasAnyAncestor(hasTestTag("hoja-ticket")) and hasText(t), useUnmergedTree = true).fetchSemanticsNode()
            assertEquals("«$t» en un renglón", 1, disposicion(n)!!.lineCount)
        }
        captura("regresion/ticket-completo-200")
    }
}
