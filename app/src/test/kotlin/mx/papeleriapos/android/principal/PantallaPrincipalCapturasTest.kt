package mx.papeleriapos.android.principal

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.escaner.ui.ControladorDeMuestra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * La pantalla principal sin celular: Robolectric la dibuja de verdad (gráficos nativos) a 360 × 740 dp, el chico de la
 * propuesta, con el controlador de muestra en lugar de la cámara. Revisa lo principal de cada modo y deja una captura de
 * cada estado en build/capturas, para verla sin emulador. La Application de la app no corre (calienta CameraX).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PantallaPrincipalCapturasTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var vm: EscanerViewModel
    private val controlador get() = vm.controlador as ControladorDeMuestra

    /** Como el Scaffold de la app: la barra de estado arriba y el hueco de los modos (80 dp) con la barra de gestos abajo. */
    private val relleno = PaddingValues(top = 24.dp, bottom = 80.dp + 24.dp)

    private fun abrir(modo: ModoApp) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        vm = EscanerViewModel(app, { _, c -> ControladorDeMuestra(config = c) }, ListasCompartidas())
        vm.cambiarModoApp(modo)
        compose.setContent {
            TemaEscaner(oscuro = false) {
                Box(Modifier.fillMaxSize()) { PantallaPrincipal(vm, relleno) {} }
            }
        }
        compose.waitForIdle()
        vm.prepararProductos()
        // Los productos se leen en su hilo y vuelven al principal, que en Robolectric solo avanza cuando se le pide.
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.piso.catalogo.value != null
        }
    }

    private fun leer(codigo: String) {
        controlador.emitirLectura(codigo)
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    /** Dibuja la ventana en un Bitmap (captureToImage espera un cuadro que Robolectric no entrega). */
    private fun captura(nombre: String) {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        val vista = compose.activity.window.decorView
        val imagen = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { vista.draw(android.graphics.Canvas(imagen)) }
        val carpeta = File("build/capturas").apply { mkdirs() }
        File(carpeta, "$nombre.png").outputStream().use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `precio ensena la tarjeta y al ticket agrega sin salir`() {
        abrir(ModoApp.PRECIO)
        compose.onNodeWithTag("panel-vacio").assertIsDisplayed()
        captura("01-precio-vacio")
        leer("7509990100019")
        compose.onNodeWithTag("tarjeta-precio").assertIsDisplayed()
        compose.onNodeWithText("\$46.00").assertIsDisplayed()
        captura("02-precio-cuaderno")
        compose.onNodeWithTag("al-ticket").performClick()
        compose.waitForIdle()
        assertEquals(1, vm.venta.value.total)
        compose.onNodeWithTag("aviso").assertIsDisplayed()
        captura("03-precio-al-ticket")
        leer("7509990100200")
        captura("04-precio-caja")
        leer("7509990100194")
        compose.onNodeWithTag("no-esta").assertIsDisplayed()
        captura("05-precio-no-esta")
    }

    @Test
    fun `ticket suma cada lectura y el renglon de arriba lleva menos y mas`() {
        abrir(ModoApp.TICKET)
        captura("10-ticket-vacio")
        leer("7509990100019")
        leer("7509990100026")
        leer("7509990100026")
        leer("7509990100194")
        assertEquals(4, vm.venta.value.total)
        compose.onNodeWithTag("encabezado-ticket").assertIsDisplayed()
        compose.onNodeWithTag("renglon-actual").assertIsDisplayed()
        captura("11-ticket")
        compose.onNodeWithTag("menos-uno").performClick()
        compose.waitForIdle()
        assertEquals(3, vm.venta.value.total)
        captura("12-ticket-quitado")
    }

    @Test
    fun `el boton rapido se abre con un toque y busca`() {
        abrir(ModoApp.TICKET)
        leer("7509990100019")
        compose.onNodeWithTag("boton-rapido").performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("menu-rapido").assertIsDisplayed()
        compose.onNodeWithTag("opcion-lado").assertIsDisplayed()
        captura("20-menu-ticket")
        compose.onNodeWithTag("opcion-lado").performClick()
        compose.waitForIdle()
        assertEquals(Hoja.Buscar, vm.piso.hoja.value)
        captura("21-buscar")
    }

    @Test
    fun `deslizar hacia arriba desde el boton elige arriba`() {
        abrir(ModoApp.PRECIO)
        leer("7509990100019")
        compose.onNodeWithTag("boton-rapido").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -50.dp.toPx()))
            up()
        }
        compose.waitForIdle()
        assertTrue(vm.piso.hoja.value is Hoja.PrecioGrande)
        captura("22-precio-grande")
    }

    @Test
    fun `las hojas del ticket`() {
        abrir(ModoApp.TICKET)
        leer("7509990100019")
        leer("7509990100026")
        vm.piso.abrir(Hoja.Ticket)
        compose.waitForIdle()
        compose.onNodeWithTag("hoja-ticket").assertIsDisplayed()
        captura("30-ticket-completo")
        vm.piso.abrir(Hoja.Cantidad(vm.venta.value.actual!!.id))
        compose.waitForIdle()
        compose.onNodeWithTag("hoja-cantidad").assertIsDisplayed()
        captura("31-cantidad")
    }

    @Test
    fun `conteo de prueba`() {
        abrir(ModoApp.CONTEO)
        captura("40-conteo-vacio")
        leer("7509990100019")
        leer("7509990100019")
        leer("7509990100026")
        compose.onNodeWithTag("contador").assertIsDisplayed()
        captura("41-conteo")
    }
}
