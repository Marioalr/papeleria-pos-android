package mx.papeleriapos.android.rapido

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.android.catalogo.FuenteDeProductos
import mx.papeleriapos.escaner.ui.ControladorDeMuestra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * El panel del escaneo rápido con la letra normal y con la grande (docs/propuesta-interfaz.md, «Una mano, letra grande y
 * TalkBack»), en el chico a pantalla completa (360 × 740, de orilla a orilla) con la barra de navegación de gestos (24 dp)
 * o de tres botones (48 dp), que Robolectric no da y se le pasa a la ventana. No pasa del 31 % del alto (no tapa la
 * franja): con la letra normal cabe todo, también el mayoreo; con la grande, lo que cede es lo leído (se desplaza), nunca
 * los botones ni el nombre y el precio.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PantallaRapidaLetraGrandeTest {
    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>().also {
        shadowOf(it).grantPermissions(Manifest.permission.CAMERA)
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private val d get() = compose.density.density

    /** Lo que se ve (recortado por lo que lo contiene), en dp. */
    private fun visto(n: SemanticsNode) = n.boundsInRoot.let { Rect(it.left / d, it.top / d, it.right / d, it.bottom / d) }

    /** Dónde quedó aunque no se vea, en dp. */
    private fun entero(n: SemanticsNode) = n.positionInRoot.let { p -> Rect(p.x / d, p.y / d, (p.x + n.size.width) / d, (p.y + n.size.height) / d) }

    private fun disposicion(n: SemanticsNode): TextLayoutResult? {
        val r = mutableListOf<TextLayoutResult>()
        n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(r)
        return r.firstOrNull()
    }

    private fun nodo(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun completo(n: SemanticsNode) = entero(n).height > 0f && abs(visto(n).height - entero(n).height) < 0.5f

    /** El texto [t] entero y en un renglón, sin «…» y sin recortar. */
    private fun assertEntero(t: String, caso: String) {
        val n = compose.onNode(hasText(t) and hasAnyAncestor(hasTestTag("rapido-abrir-app") or hasTestTag("rapido-cerrar")), useUnmergedTree = true)
            .fetchSemanticsNode()
        val l = disposicion(n)!!
        assertEquals("$caso: «$t» en un renglón", 1, l.lineCount)
        assertFalse("$caso: «$t» con «…»", l.isLineEllipsized(0))
        assertTrue("$caso: «$t» recortado: ${visto(n)} de ${entero(n)}", abs(visto(n).height - entero(n).height) < 0.5f)
    }

    /**
     * [codigo]: lo leído (el cuaderno trae mayoreo; la goma, promoción; la caja, su precio por pieza). [extras]: lo que se
     * enseña abajo del nombre. [alto]: el de la ventana (el de @Config). [cabeTodo]: lo leído se ve entero; si no, cede
     * (se desplaza, sin encimarse con los botones), pero el nombre y el precio se ven enteros salvo en uno más bajo
     * ([nombreYPrecio] = false).
     */
    private fun medir(
        codigo: String,
        extras: List<String>,
        barraDp: Int,
        alto: Int = 740,
        cabeTodo: Boolean = false,
        nombreYPrecio: Boolean = true,
    ) {
        val vm = EscaneoRapidoViewModel(app, { _, c -> ControladorDeMuestra(config = c) }) { FuenteDeProductos.dePrueba(app) }
        compose.setContent { TemaEscaner(oscuro = true) { PantallaRapida(vm, alAbrirApp = {}, alCerrar = {}) } }
        idle()
        // Las barras del chico llegan a Compose como en el celular de orilla a orilla.
        val vista = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view
        val barras = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (24 * d).roundToInt(), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (barraDp * d).roundToInt()))
            .build()
        compose.runOnIdle { ViewCompat.dispatchApplyWindowInsets(vista, barras) }
        idle()
        (vm.controlador as ControladorDeMuestra).emitirLectura(codigo)
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.catalogo.value != null
        }
        idle()
        val caso = "$codigo, letra ${compose.density.fontScale}, barra $barraDp, alto $alto"
        val abajo = alto - barraDp.toFloat()
        // No pasa del 31 % del alto: no tapa la franja.
        val panel = visto(nodo("rapido-panel"))
        assertTrue("$caso: el panel empieza en ${panel.top}, arriba del 31 %", panel.top >= alto * (1 - 0.31f) - 0.5f)
        for (tag in listOf("rapido-cerrar", "rapido-abrir-app")) {
            val b = visto(nodo(tag))
            assertTrue("$caso: $tag mide ${b.height} dp de alto", b.height >= 56f - 0.5f)
            assertTrue("$caso: $tag queda bajo la barra de navegación ($b)", b.bottom <= abajo + 0.5f)
        }
        assertEntero("Abrir la app", caso)
        if (compose.density.fontScale >= 1.3f) {
            // Con la letra grande «Cerrar» se queda con su ícono, y TalkBack lo dice igual.
            val cerrar = compose.onNodeWithTag("rapido-cerrar").fetchSemanticsNode()
            assertEquals(listOf("Cerrar"), cerrar.config.getOrNull(SemanticsProperties.ContentDescription))
        }
        val botones = visto(nodo("rapido-cerrar")).top
        val leido = nodo("rapido-leido")
        assertTrue("$caso: lo leído (${visto(leido)}) se encima con los botones", visto(leido).bottom <= botones + 0.5f)
        if (nombreYPrecio) {
            for (tag in listOf("rapido-producto", "rapido-precio")) assertTrue("$caso: $tag recortado", completo(nodo(tag)))
        }
        for (e in extras) {
            val n = compose.onAllNodesWithTag(e, useUnmergedTree = true).fetchSemanticsNodes().single()
            if (cabeTodo) {
                assertTrue("$caso: $e recortado: ${visto(n)} de ${entero(n)}", completo(n))
            } else if (!completo(n)) {
                // No cabe: se llega desplazando lo leído (también con TalkBack), no se pierde.
                val rango = leido.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                assertTrue("$caso: $e recortado y lo leído no se desplaza", rango != null && rango.maxValue() > 0f)
            }
        }
        val carpeta = File("build/capturas/rapido").apply { mkdirs() }
        val ventana = compose.activity.window.decorView
        val imagen = android.graphics.Bitmap.createBitmap(ventana.width, ventana.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { ventana.draw(android.graphics.Canvas(imagen)) }
        val nombre = "letra-${extras.first().removePrefix("rapido-")}-${(compose.density.fontScale * 100).roundToInt()}-barra$barraDp" +
            (if (alto == 740) "" else "-alto$alto")
        File(carpeta, "$nombre.png").outputStream().use { imagen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val mayoreo = listOf("rapido-mayoreo")

    @Test fun `con la letra normal cabe todo con la barra de gestos`() = medir(CUADERNO, mayoreo, 24, cabeTodo = true)
    @Test fun `con la letra normal cabe todo con tres botones`() = medir(CUADERNO, mayoreo, 48, cabeTodo = true)
    @Test fun `con la letra normal cabe la promocion`() = medir(GOMA, listOf("rapido-promocion"), 48, cabeTodo = true)
    @Test fun `con la letra normal cabe el precio por pieza`() = medir(CAJA_LAPICES, listOf("rapido-por-pieza"), 48, cabeTodo = true)
    @Test @Config(fontScale = 1.5f) fun `al 150 con la barra de gestos`() = medir(CUADERNO, mayoreo, 24)
    @Test @Config(fontScale = 1.5f) fun `al 150 con tres botones`() = medir(CUADERNO, mayoreo, 48)
    @Test @Config(fontScale = 2.0f) fun `al 200 con la barra de gestos`() = medir(CUADERNO, mayoreo, 24)
    @Test @Config(fontScale = 2.0f) fun `al 200 con tres botones`() = medir(CUADERNO, mayoreo, 48)
    @Test @Config(fontScale = 2.0f) fun `al 200 la promocion con tres botones`() = medir(GOMA, listOf("rapido-promocion"), 48)

    /**
     * Más bajo que el chico (640 dp), al 200 % y con tres botones no cabe todo en el 31 %: lo leído se desplaza y los
     * botones siguen de 56 dp, con «Abrir la app» entero.
     */
    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi", fontScale = 2.0f)
    fun `en un celular mas bajo cede lo leido y no los botones`() = medir(CUADERNO, mayoreo, 48, alto = 640, nombreYPrecio = false)

    private companion object {
        const val CUADERNO = "7509990100019"
        const val GOMA = "7509990100033"
        const val CAJA_LAPICES = "7509990100200"
    }
}
