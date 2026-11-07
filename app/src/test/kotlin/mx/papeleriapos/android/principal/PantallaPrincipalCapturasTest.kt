package mx.papeleriapos.android.principal

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La pantalla principal sin celular: Robolectric la dibuja de verdad (gráficos nativos) con la geometría del chico de la
 * propuesta (360 × 740 dp con barras de 24 + 24; ver [PruebaDePantalla]), con el controlador de muestra en lugar de la
 * cámara. Revisa lo principal de cada modo y deja una captura de cada estado en build/capturas, para verla sin emulador;
 * las de la letra al 200 % llevan «-200». La Application de la app no corre (calienta CameraX).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = CHICO)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PantallaPrincipalCapturasTest : PruebaDePantalla() {

    @Test
    fun `precio ensena la tarjeta y al ticket agrega sin salir`() {
        abrir(ModoApp.PRECIO)
        compose.onNodeWithTag("panel-vacio").assertIsDisplayed()
        captura("01-precio-vacio")
        leer(CUADERNO)
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
        leer(NO_ESTA)
        compose.onNodeWithTag("no-esta").assertIsDisplayed()
        captura("05-precio-no-esta")
        leer(SACAPUNTAS)
        captura("06-precio-queda-1")
    }

    @Test
    fun `ticket suma cada lectura y el renglon de arriba lleva menos y mas`() {
        abrir(ModoApp.TICKET)
        captura("10-ticket-vacio")
        leer(CUADERNO)
        leer(LAPIZ)
        leer(LAPIZ)
        leer(NO_ESTA)
        assertEquals(4, vm.venta.value.total)
        compose.onNodeWithTag("encabezado-ticket").assertIsDisplayed()
        compose.onNodeWithTag("renglon-actual").assertIsDisplayed()
        captura("11-ticket")
        compose.onNodeWithTag("menos-uno").performClick()
        compose.waitForIdle()
        assertEquals(3, vm.venta.value.total)
        captura("12-ticket-quitado")
        // Seis productos distintos: lo último sigue a la vista, con su − y su +.
        for (c in listOf(GOMA, SACAPUNTAS, PEGAMENTO, TIJERAS, FOLDER)) leer(c)
        compose.onNodeWithTag("renglon-actual").assertIsDisplayed()
        captura("13-ticket-seis")
    }

    @Test
    fun `el boton rapido se abre con un toque y busca`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
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
        leer(CUADERNO)
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
    fun `zurdo el boton y el menu van en espejo`() {
        abrir(ModoApp.PRECIO) { it.piso.cambiarZurdo(true) }
        captura("23-zurdo-precio-vacio")
        leer(CUADERNO)
        compose.onNodeWithTag("boton-rapido").performTouchInput { down(center); up() }
        idle()
        captura("24-zurdo-menu")
    }

    @Test
    fun `las hojas del ticket`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        vm.piso.abrir(Hoja.Ticket)
        compose.waitForIdle()
        compose.onNodeWithTag("hoja-ticket").assertIsDisplayed()
        captura("30-ticket-completo")
        vm.piso.abrir(Hoja.Cantidad(vm.venta.value.actual!!.id))
        compose.waitForIdle()
        compose.onNodeWithTag("hoja-cantidad").assertIsDisplayed()
        captura("31-cantidad")
        compose.onNodeWithTag("tecla-0").performClick()
        compose.onNodeWithTag("cantidad-listo").performClick()
        idle()
        // «Quitar» regresa al ticket completo con «Quitado del ticket · Deshacer» encima.
        assertEquals(Hoja.Ticket, vm.piso.hoja.value)
        captura("32-ticket-completo-quitado")
    }

    @Test
    fun `conteo de prueba`() {
        abrir(ModoApp.CONTEO)
        captura("40-conteo-vacio")
        leer(CUADERNO)
        leer(CUADERNO)
        leer(LAPIZ)
        compose.onNodeWithTag("contador").assertIsDisplayed()
        captura("41-conteo")
    }

    // ---- Con la letra al 200 % ----

    @Test
    @Config(fontScale = 2.0f)
    fun `con la letra al 200 por ciento`() {
        abrir(ModoApp.PRECIO)
        captura("50-precio-vacio-200")
        leer(CUADERNO)
        captura("51-precio-cuaderno-200")
        leer(GOMA)
        captura("52-precio-goma-200")
        compose.onNodeWithTag("boton-rapido").performTouchInput { down(center); up() }
        idle()
        captura("53-menu-precio-200")
        atras()
        vm.cambiarModoApp(ModoApp.TICKET)
        idle()
        captura("54-ticket-vacio-200")
        for (c in listOf(CUADERNO, LAPIZ, LAPIZ, SACAPUNTAS)) leer(c)
        captura("55-ticket-200")
        vm.piso.abrir(Hoja.Ticket)
        idle()
        captura("56-ticket-completo-200")
        vm.piso.cerrarHoja()
        vm.cambiarModoApp(ModoApp.CONTEO)
        idle()
        leer(CUADERNO)
        captura("57-conteo-200")
    }
}
