package mx.papeleriapos.android.principal

import android.Manifest
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.escaner.ui.ControladorDeMuestra
import org.junit.Rule
import org.robolectric.Shadows.shadowOf
import java.io.File

/** El chico: 360 × 740 dp menos los 24 de la barra de gestos, que Robolectric no da. */
internal const val CHICO = "w360dp-h716dp-xxhdpi"

internal const val CUADERNO = "7509990100019"
internal const val LAPIZ = "7509990100026"
internal const val GOMA = "7509990100033"
internal const val SACAPUNTAS = "7509990100040"
internal const val PEGAMENTO = "7509990100057"
internal const val TIJERAS = "7509990100064"
internal const val FOLDER = "7509990100071"
internal const val NO_ESTA = "7509990100194"

/**
 * Lo que comparten las pruebas de la pantalla principal con Robolectric (gráficos nativos): el chico de verdad, el
 * controlador de muestra en lugar de la cámara y las capturas. El chico mide 360 × 740 dp con barras de 24 + 24; Robolectric
 * no da la barra de gestos, así que la ventana mide 716 y abajo va solo el hueco de los modos (80): el panel queda del alto
 * que tiene en el celular (166 dp útiles), no 24 dp más alto (docs/pruebas.md). Las subclases ponen
 * `@Config(application = Application::class, qualifiers = CHICO)`.
 */
abstract class PruebaDePantalla {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected lateinit var vm: EscanerViewModel
    protected lateinit var listas: ListasCompartidas
    protected val controlador get() = vm.controlador as ControladorDeMuestra

    /** Como el Scaffold en el chico: la barra de estado arriba y el hueco de los modos (80 dp) abajo. */
    protected val relleno = PaddingValues(top = 24.dp, bottom = 80.dp)

    /** false: la pantalla principal sale de la composición (a Ajustes, o la Activity se recrea), y true la regresa. */
    protected var mostrar by mutableStateOf(true)
    protected var ajustesAbiertos = 0
    protected var modoEntrada: InputModeManager? = null

    /** Para fijar el «Tiempo para actuar» de Accesibilidad; null: el del sistema. */
    protected var accesibilidad: AccessibilityManager? = null

    protected fun abrir(
        modo: ModoApp,
        oscuro: Boolean = false,
        cargarProductos: Boolean = true,
        /** Con el lector de pantalla prendido (para mirar lo que expone el proveedor de accesibilidad). */
        talkBack: Boolean = false,
        antes: (EscanerViewModel) -> Unit = {},
    ) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        if (talkBack) {
            val am = app.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            shadowOf(am).setEnabled(true)
            shadowOf(am).setTouchExplorationEnabled(true)
        }
        listas = ListasCompartidas()
        vm = EscanerViewModel(app, { _, c -> ControladorDeMuestra(config = c) }, listas)
        vm.cambiarModoApp(modo)
        antes(vm)
        compose.setContent {
            modoEntrada = LocalInputModeManager.current
            val a = accesibilidad ?: LocalAccessibilityManager.current
            CompositionLocalProvider(LocalAccessibilityManager provides a) {
                TemaEscaner(oscuro = oscuro) {
                    Box(Modifier.fillMaxSize()) { if (mostrar) PantallaPrincipal(vm, relleno) { ajustesAbiertos++ } }
                }
            }
        }
        compose.waitForIdle()
        if (!cargarProductos) return
        vm.prepararProductos()
        // Los productos se leen en su hilo y vuelven al principal, que en Robolectric solo avanza cuando se le pide.
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.piso.catalogo.value != null
        }
    }

    protected fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    protected fun leer(codigo: String) {
        controlador.emitirLectura(codigo)
        idle()
    }

    protected fun atras() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        idle()
    }

    protected fun existe(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected val densidad get() = compose.density.density

    /** Los límites en dp, recortados por lo que los contiene (lo que se ve). */
    protected fun enDp(r: Rect) = Rect(r.left / densidad, r.top / densidad, r.right / densidad, r.bottom / densidad)

    protected fun limites(tag: String): Rect? =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { enDp(it.boundsInRoot) }

    /** Los límites en dp sin recortar: dónde quedó aunque no se vea. */
    protected fun sinRecorte(n: SemanticsNode): Rect {
        val p = n.positionInRoot
        return Rect(p.x / densidad, p.y / densidad, (p.x + n.size.width) / densidad, (p.y + n.size.height) / densidad)
    }

    protected fun textoDe(n: SemanticsNode): String =
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: ""

    protected fun disposicion(n: SemanticsNode): TextLayoutResult? {
        val r = mutableListOf<TextLayoutResult>()
        n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(r)
        return r.firstOrNull()
    }

    protected fun menuAbierto() = existe("menu-rapido")

    protected fun nombreDe(codigo: String) = vm.piso.catalogo.value!!.porClave(codigo)!!.nombre

    /** Dibuja la ventana en un Bitmap (captureToImage espera un cuadro que Robolectric no entrega). */
    protected fun captura(nombre: String, carpeta: String = "build/capturas"): Bitmap {
        idle()
        val vista = compose.activity.window.decorView
        val imagen = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { vista.draw(android.graphics.Canvas(imagen)) }
        val archivo = File(carpeta, "$nombre.png").apply { parentFile?.mkdirs() }
        archivo.outputStream().use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return imagen
    }

    // ---- TalkBack: lo que expone el proveedor de accesibilidad de Compose ----

    protected data class NodoAccesible(val id: Int, val info: AccessibilityNodeInfo)

    /**
     * Lo que ve TalkBack: el proveedor de accesibilidad de Compose, forzado como si el lector estuviera prendido (la
     * prueba abre con `talkBack = true`). Un nodo tapado o sin área no sale.
     */
    protected fun nodosAccesibles(): List<NodoAccesible> {
        idle()
        val v = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view
        val campo = v.javaClass.getDeclaredField("composeAccessibilityDelegate").apply { isAccessible = true }
        val delegado = campo.get(v)
        delegado.javaClass.getMethod("setAccessibilityForceEnabledForTesting\$ui", Boolean::class.javaPrimitiveType).invoke(delegado, true)
        val proveedor: AccessibilityNodeProvider = v.accessibilityNodeProvider!!
        val raiz = compose.onRoot().fetchSemanticsNode().id
        return compose.onAllNodes(SemanticsMatcher("todo") { true }, useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { n ->
            val id = if (n.id == raiz) AccessibilityNodeProvider.HOST_VIEW_ID else n.id
            proveedor.createAccessibilityNodeInfo(id)?.let { NodoAccesible(n.id, it) }
        }
    }

    /**
     * El orden en que TalkBack recorre la pantalla, como lo calcula Compose (la cadena de «antes de»): de cada parada, su
     * testTag o, si no tiene, lo que dice. Un nodo que junta a sus hijos (el botón rápido) lleva lo que dice en un hijo.
     */
    protected fun ordenDeTalkBack(nodos: List<NodoAccesible>): List<String> {
        val etiquetas = compose.onAllNodes(SemanticsMatcher("todo") { true }, useUnmergedTree = true).fetchSemanticsNodes()
            .associate { it.id to it.config.getOrNull(SemanticsProperties.TestTag) }
        val antes = mutableMapOf<Int, Int>()
        for (n in nodos) {
            val b = n.info.extras.getInt("android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSALBEFORE_VAL", Int.MIN_VALUE)
            if (b != Int.MIN_VALUE) antes[n.id] = b
        }
        val porId = nodos.associateBy { it.id }
        val apuntados = antes.values.toSet()
        var actual = antes.keys.firstOrNull { it !in apuntados }
        val orden = mutableListOf<String>()
        val vistos = mutableSetOf<Int>()
        while (actual != null && actual !in vistos) {
            vistos += actual
            val info = porId[actual]?.info
            orden += (etiquetas[actual] ?: info?.contentDescription ?: info?.text ?: "id$actual").toString()
            actual = antes[actual]
        }
        return orden
    }

    /** Las vistas con exclusión del gesto de atrás (Android 10+), en px. */
    protected fun exclusiones(v: View = compose.activity.window.decorView): List<android.graphics.Rect> {
        val propias = v.systemGestureExclusionRects
        val hijas = if (v is ViewGroup) (0 until v.childCount).flatMap { exclusiones(v.getChildAt(it)) } else emptyList()
        return propias + hijas
    }
}
