package mx.papeleriapos.android.rapido

import android.Manifest
import android.app.Application
import android.content.Context
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.android.principal.PreferenciasPiso
import mx.papeleriapos.android.tema.TemaEscaner
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
 * El panel del escaneo rápido con la letra grande (docs/propuesta-interfaz.md, «Una mano, letra grande y TalkBack»), en el
 * chico a pantalla completa (360 × 740, de orilla a orilla) con la barra de navegación de gestos (24 dp) o de tres botones
 * (48 dp), que Robolectric no da y se le pasa a la ventana. En Precio, con el nombre y el precio, ya no cabía en su 31 %
 * del alto y cedían los botones: «Abrir la app» se cortaba y al 200 % se quedaban sin texto. Lo que cede es lo leído.
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
     * [alto]: el de la ventana (el de @Config). [cabeTodo]: en el chico cabe todo; en uno más bajo no, y lo que cede es lo
     * leído, no los botones.
     */
    private fun medir(modo: ModoApp, barraDp: Int, alto: Int = 740, cabeTodo: Boolean = true) {
        val prefs = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
        PreferenciasPiso(prefs).guardarModo(modo)
        val escaner = PreferenciasEscaner(prefs)
        escaner.guardar(escaner.leer().copy(modo = modo.delNucleo))
        val vm = EscaneoRapidoViewModel(app, { _, c -> ControladorDeMuestra(config = c) }, { ListasCompartidas() })
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
        (vm.controlador as ControladorDeMuestra).emitirLectura(CUADERNO)
        if (modo == ModoApp.PRECIO) {
            compose.waitUntil(5_000) {
                shadowOf(Looper.getMainLooper()).idle()
                vm.catalogo.value != null
            }
        }
        idle()
        val caso = "$modo, letra ${compose.density.fontScale}, barra $barraDp, alto $alto"
        val abajo = alto - barraDp.toFloat()
        // No pasa del 31 % del alto (no tapa la franja): arriba de lo primero que dice van los 10 dp de su relleno.
        val panel = visto(nodo("rapido-total")).top - 10f
        assertTrue("$caso: el panel empieza en $panel, arriba del 31 %", panel >= alto * (1 - 0.31f) - 0.5f)
        for (tag in listOf("rapido-cerrar", "rapido-abrir-app")) {
            val b = visto(nodo(tag))
            assertTrue("$caso: $tag mide ${b.height} dp de alto", b.height >= 56f - 0.5f)
            assertTrue("$caso: $tag queda bajo la barra de navegación ($b)", b.bottom <= abajo + 0.5f)
        }
        assertEntero("Abrir la app", caso)
        // Con la letra grande «Cerrar» se queda con su ícono, y TalkBack lo dice igual.
        val cerrar = compose.onNodeWithTag("rapido-cerrar").fetchSemanticsNode()
        assertEquals(listOf("Cerrar"), cerrar.config.getOrNull(SemanticsProperties.ContentDescription))
        // Lo leído se ve entero: en Precio el nombre (en un renglón) y el precio; en Ticket, el código. Si no cabe, se queda
        // arriba de los botones, sin encimarse.
        val leidos = if (modo == ModoApp.PRECIO) listOf("rapido-producto", "rapido-precio") else listOf("rapido-ultimo")
        for (tag in leidos) {
            val n = nodo(tag)
            if (cabeTodo) {
                assertTrue("$caso: $tag recortado: ${visto(n)} de ${entero(n)}", entero(n).height > 0f && abs(visto(n).height - entero(n).height) < 0.5f)
            } else {
                assertTrue("$caso: $tag (${visto(n)}) se encima con los botones", visto(n).bottom <= visto(nodo("rapido-cerrar")).top + 0.5f)
            }
        }
        val carpeta = File("build/capturas/rapido").apply { mkdirs() }
        val ventana = compose.activity.window.decorView
        val imagen = android.graphics.Bitmap.createBitmap(ventana.width, ventana.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { ventana.draw(android.graphics.Canvas(imagen)) }
        File(carpeta, "letra-${modo.name.lowercase()}-${(compose.density.fontScale * 100).roundToInt()}-barra$barraDp${if (alto == 740) "" else "-alto$alto"}.png").outputStream()
            .use { imagen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(fontScale = 1.5f) fun `en precio al 150 con la barra de gestos`() = medir(ModoApp.PRECIO, 24)
    @Test @Config(fontScale = 1.5f) fun `en precio al 150 con tres botones`() = medir(ModoApp.PRECIO, 48)
    @Test @Config(fontScale = 2.0f) fun `en precio al 200 con la barra de gestos`() = medir(ModoApp.PRECIO, 24)
    @Test @Config(fontScale = 2.0f) fun `en precio al 200 con tres botones`() = medir(ModoApp.PRECIO, 48)
    @Test @Config(fontScale = 1.5f) fun `en ticket al 150 con la barra de gestos`() = medir(ModoApp.TICKET, 24)
    @Test @Config(fontScale = 1.5f) fun `en ticket al 150 con tres botones`() = medir(ModoApp.TICKET, 48)
    @Test @Config(fontScale = 2.0f) fun `en ticket al 200 con la barra de gestos`() = medir(ModoApp.TICKET, 24)
    @Test @Config(fontScale = 2.0f) fun `en ticket al 200 con tres botones`() = medir(ModoApp.TICKET, 48)

    /**
     * Más bajo que el chico (640 dp), al 200 % y con tres botones no cabe todo en el 31 %: el nombre y el precio se
     * recortan y los botones siguen de 56 dp, con «Abrir la app» entero.
     */
    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi", fontScale = 2.0f)
    fun `en un celular mas bajo cede lo leido y no los botones`() = medir(ModoApp.PRECIO, 48, alto = 640, cabeTodo = false)

    private companion object {
        const val CUADERNO = "7509990100019"
    }
}
