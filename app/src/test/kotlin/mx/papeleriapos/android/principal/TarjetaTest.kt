package mx.papeleriapos.android.principal

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.botonrapido.SIN_CONEXION_MANDAR
import mx.papeleriapos.android.catalogo.pesos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La pantalla de la 0.5 (docs/propuesta-interfaz/prototipo-0.5.html): la venta en una sola pantalla, la tarjeta de lo
 * último con sus gestos (tocar suma, deslizar a la izquierda quita en dos grados, dejar el dedo abre el teclado), lo que no
 * está fuera del ticket con sus dos salidas, «Cobrar» abajo y Conteo en el menú. Con los dedos de verdad sobre la tarjeta,
 * no con sus acciones de accesibilidad.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = CHICO)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TarjetaTest : PruebaDePantalla() {

    private fun actual() = vm.venta.value.actual!!
    private fun textoEn(tag: String) = textoDe(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())

    @Test
    fun `un toque en la tarjeta suma uno y el precio cambia al llegar al mayoreo`() {
        abrir(ModoApp.VENTA)
        leer(CUADERNO)
        assertEquals("×1", textoEn("cantidad"))
        assertEquals("\$46.00", textoEn("precio"))
        repeat(2) { tocarTarjeta() }
        assertEquals(3, actual().cantidad)
        assertEquals("×3", textoEn("cantidad"))
        assertEquals("\$138.00", textoEn("importe-actual"))
        assertEquals("Cobrar · \$138.00", textoEn("total-ticket"))
        // Con 12 entra el mayoreo: la tarjeta enseña el precio de cada una ya con él.
        repeat(9) { tocarTarjeta() }
        assertEquals("\$41.00", textoEn("precio"))
        assertEquals(pesos(4100L * 12), textoEn("importe-actual"))
    }

    @Test
    fun `deslizar un tramo corto quita uno y hasta el fondo quita el renglon entero`() {
        abrir(ModoApp.VENTA)
        leer(LAPIZ)
        leer(CUADERNO)
        repeat(3) { tocarTarjeta() }
        assertEquals(4, actual().cantidad)
        // Menos de 48 dp: no pasa nada.
        deslizarTarjeta(30f)
        assertEquals(4, actual().cantidad)
        // Un tramo corto: uno menos.
        deslizarTarjeta(80f)
        assertEquals(3, actual().cantidad)
        assertNull("Quitar una de varias no avisa", vm.piso.aviso.value)
        // Hasta el fondo (más de la mitad de la tarjeta): sale el renglón, con sus 3, y se puede deshacer.
        deslizarTarjeta(260f)
        assertEquals(listOf(LAPIZ), vm.venta.value.renglones.map { it.clave })
        assertEquals("Quitados los 3 del ticket", vm.piso.aviso.value?.texto)
        compose.onNodeWithTag("aviso-accion").performClick()
        idle()
        assertEquals(CUADERNO to 3, actual().let { it.clave to it.cantidad })
    }

    @Test
    fun `a medio deslizar la tarjeta dice que va a pasar y regresar el dedo lo cancela`() {
        abrir(ModoApp.VENTA)
        leer(CUADERNO)
        tocarTarjeta()
        val reposo = textoEn("pista-tarjeta")
        assertTrue(reposo, reposo.startsWith("Toca: uno más"))
        compose.onNodeWithTag("renglon-actual").performTouchInput {
            down(center)
            repeat(4) { moveBy(Offset(-20.dp.toPx(), 0f)) }
        }
        idle()
        assertEquals("Suelta: quita uno · sigue hasta el fondo para quitar todo", textoEn("pista-tarjeta"))
        compose.onNodeWithTag("renglon-actual").performTouchInput { repeat(5) { moveBy(Offset(-40.dp.toPx(), 0f)) } }
        idle()
        assertEquals("Suelta: sale del ticket con sus 2", textoEn("pista-tarjeta"))
        // De regreso al principio y soltar: no se quita nada.
        compose.onNodeWithTag("renglon-actual").performTouchInput {
            repeat(7) { moveBy(Offset(40.dp.toPx(), 0f)) }
            up()
        }
        idle()
        assertEquals(2, actual().cantidad)
        assertEquals(reposo, textoEn("pista-tarjeta"))
    }

    @Test
    fun `dejar el dedo en la tarjeta abre el teclado de la cantidad`() {
        abrir(ModoApp.VENTA)
        leer(LAPIZ)
        compose.onNodeWithTag("renglon-actual").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        idle()
        assertEquals(Hoja.Cantidad(actual().id), vm.piso.hoja.value)
        compose.onNodeWithTag("renglon-actual", useUnmergedTree = true).performTouchInput { up() }
        idle()
        assertEquals("Dejar el dedo no suma", 1, actual().cantidad)
        for (t in listOf("2", "0")) compose.onNodeWithTag("tecla-$t").performTouchInput { down(center); up() }
        compose.onNodeWithTag("cantidad-listo").performTouchInput { down(center); up() }
        idle()
        assertEquals(20, actual().cantidad)
    }

    @Test
    fun `tocar un renglon de la lista lo sube a la tarjeta sin sumarle`() {
        abrir(ModoApp.VENTA)
        leer(CUADERNO)
        leer(LAPIZ)
        val deAbajo = vm.venta.value.renglones.last()
        compose.onNodeWithTag("renglon-${deAbajo.id}").performClick()
        idle()
        assertEquals(CUADERNO, actual().clave)
        assertEquals("Sin sumarle", 2, vm.venta.value.total)
    }

    @Test
    fun `lo que no esta se queda fuera y el articulo comun entra con su precio a mano`() {
        abrir(ModoApp.VENTA)
        leer(LAPIZ)
        leer(NO_ESTA)
        assertEquals("No entró al ticket", 1, vm.venta.value.total)
        compose.onNodeWithTag("no-esta").assertIsDisplayed()
        compose.onNodeWithTag("buscar-por-nombre").assertIsDisplayed()
        compose.onNodeWithTag("articulo-comun").performClick()
        idle()
        assertEquals(Hoja.Comun, vm.piso.hoja.value)
        for (t in listOf("tecla-1", "tecla-5", "tecla-punto", "tecla-5")) compose.onNodeWithTag(t).performTouchInput { down(center); up() }
        idle()
        assertEquals("\$15.5", textoEn("precio-comun"))
        compose.onNodeWithTag("comun-listo").performTouchInput { down(center); up() }
        idle()
        assertNull(vm.piso.hoja.value)
        assertFalse(existe("no-esta"))
        assertEquals(1550L, actual().comunCentavos)
        assertEquals("Artículo común", textoEn("nombre-actual"))
        assertEquals("Cobrar · \$21.00", textoEn("total-ticket"))
        captura("0.5/articulo-comun")
        // Al contar no hay artículo común: solo buscarlo por nombre.
        vm.cambiarModoApp(ModoApp.CONTEO)
        idle()
        leer(NO_ESTA)
        assertTrue(existe("buscar-por-nombre"))
        assertFalse(existe("articulo-comun"))
        assertTrue("No entró al conteo", vm.conteo.value.renglones.isEmpty())
    }

    @Test
    fun `cobrar apagado dice por que al tocarlo`() {
        abrir(ModoApp.VENTA)
        compose.onNodeWithTag("cobrar").performClick()
        idle()
        assertEquals(TICKET_VACIO, vm.piso.aviso.value?.texto)
        leer(CUADERNO)
        compose.mainClock.advanceTimeBy(6_000)
        idle()
        compose.onNodeWithTag("cobrar").performClick()
        idle()
        assertEquals("Sin alta no hay a qué caja mandarlo", SIN_CONEXION_MANDAR, vm.piso.aviso.value?.texto)
        assertNull(vm.piso.hoja.value)
    }

    @Test
    fun `conteo se abre desde el menu, la pantalla lo dice y lo contado se queda guardado`() {
        abrir(ModoApp.VENTA)
        leer(LAPIZ)
        compose.onNodeWithTag("boton-menu").performClick()
        idle()
        assertEquals(Hoja.Menu, vm.piso.hoja.value)
        captura("0.5/menu")
        assertFalse("Sin nada contado, no hay otro conteo que empezar", existe("menu-otro-conteo"))
        compose.onNodeWithTag("menu-contar").performClick()
        idle()
        assertNull(vm.piso.hoja.value)
        assertEquals(ModoApp.CONTEO, vm.piso.modo.value)
        compose.onNodeWithTag("contando").assertIsDisplayed()
        leer(CUADERNO)
        repeat(2) { tocarTarjeta() }
        assertEquals(3, vm.conteo.value.total)
        assertEquals("El ticket no se toca", 1, vm.venta.value.total)
        assertEquals("Terminar conteo · 3 pzs", textoEn("total-conteo"))
        captura("0.5/conteo")
        // Con algo contado, «Terminar conteo» pregunta a qué caja va (0.5.6); sin mandarlo, de vuelta a vender.
        compose.onNodeWithTag("terminar-conteo").performClick()
        idle()
        assertEquals(Hoja.MandarConteo, vm.piso.hoja.value)
        compose.onNodeWithTag("conteo-sin-mandar").performClick()
        idle()
        assertNull(vm.piso.hoja.value)
        assertEquals(ModoApp.VENTA, vm.piso.modo.value)
        assertFalse(existe("contando"))
        assertEquals(3, vm.conteo.value.total)
        // De vuelta en el menú: seguir contando o empezar otro.
        compose.onNodeWithTag("boton-menu").performClick()
        idle()
        compose.onNodeWithTag("menu-otro-conteo").performClick()
        idle()
        assertEquals(ModoApp.CONTEO, vm.piso.modo.value)
        assertTrue(vm.conteo.value.renglones.isEmpty())
        assertEquals("Conteo vacío: empieza otro", vm.piso.aviso.value?.texto)
    }

    @Test
    fun `el mismo codigo no vuelve a contar mientras siga a la vista`() {
        abrir(ModoApp.VENTA)
        assertEquals("Solo al retirarlo de la mira; las demás piezas, con el toque", 0L, controlador.config.value.msParaRepetirAVista)
        // Cambiar un ajuste no lo regresa.
        vm.cambiarConfig(vm.config.value.copy(vibracion = !vm.config.value.vibracion))
        assertEquals(0L, controlador.config.value.msParaRepetirAVista)
    }
}
