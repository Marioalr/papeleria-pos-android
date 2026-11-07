package mx.papeleriapos.android.principal

import android.app.Application
import android.view.View
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.tema.LocalColoresEstado
import mx.papeleriapos.android.tema.TemaEscaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Lo que encontró la revisión de la entrega 1 para TalkBack y el teclado (docs/propuesta-interfaz.md, «Accesibilidad y
 * los otros gestos»), cada caso como prueba de regresión. TalkBack se mira en el proveedor de accesibilidad de Compose,
 * forzado como si estuviera prendido; el teclado, en el modo de entrada Keyboard.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = CHICO)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PantallaPrincipalAccesibleTest : PruebaDePantalla() {

    private fun tocarBoton() {
        compose.onNodeWithTag("boton-rapido").performTouchInput { down(center); up() }
        idle()
    }

    private fun anuncio(): String {
        val n = compose.onNodeWithTag("anuncio", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, n.config[SemanticsProperties.LiveRegion])
        return n.config[SemanticsProperties.ContentDescription].joinToString("").replace("\u200B", "")
    }

    /** El anuncio está en lo que ve TalkBack, como región viva (un nodo tapado o sin área no saldría). */
    private fun anuncioAccesible(): Boolean {
        val id = compose.onNodeWithTag("anuncio", useUnmergedTree = true).fetchSemanticsNode().id
        return nodosAccesibles().firstOrNull { it.id == id }?.info?.liveRegion == View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    /** Lo que la región viva le manda a TalkBack: el cambio de su descripción (con lo que dice). */
    private fun anunciosEnviados(): List<String> {
        val am = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
            .getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return org.robolectric.Shadows.shadowOf(am).sentAccessibilityEvents
            .filter {
                it.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    it.contentChangeTypes and android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION != 0
            }
            .map { it.contentDescription.toString().replace("​", "") }
    }

    /** Los cambios de la semántica salen cada 100 ms (y el primero toma la foto de lo que hay). */
    private fun dejarQueSalganLosEventos() = repeat(3) {
        compose.mainClock.advanceTimeBy(200)
        idle()
    }

    @Test
    fun `cada lectura le llega a talkback y antes de leer no hay una parada vacia`() {
        abrir(ModoApp.PRECIO, talkBack = true)
        assertFalse("Sin lecturas, el anuncio no es una parada (no diría nada)", "anuncio" in ordenDeTalkBack(nodosAccesibles()))
        dejarQueSalganLosEventos()
        vm.cambiarModoApp(ModoApp.TICKET)
        dejarQueSalganLosEventos()
        val antes = anunciosEnviados().size
        // La primera lectura, el mismo producto otra vez y uno en Ticket: cada una manda su anuncio.
        leer(LAPIZ)
        dejarQueSalganLosEventos()
        leer(LAPIZ)
        dejarQueSalganLosEventos()
        vm.cambiarModoApp(ModoApp.PRECIO)
        dejarQueSalganLosEventos()
        leer(CUADERNO)
        dejarQueSalganLosEventos()
        leer(CUADERNO)
        dejarQueSalganLosEventos()
        assertEquals(
            listOf(
                "Lápiz HB No. 2 al ticket. Total \$5.50",
                "Lápiz HB No. 2 al ticket, van 2. Total \$11.00",
                "Cuaderno profesional cuadro chico 100 hojas, \$46.00",
                "Cuaderno profesional cuadro chico 100 hojas, \$46.00",
            ),
            anunciosEnviados().drop(antes).filter { it.contains("Lápiz") || it.contains("Cuaderno") },
        )
    }

    @Test
    fun `lo que llega del escaneo rapido se anuncia como una lectura, tambien al volver de ajustes`() {
        abrir(ModoApp.PRECIO, talkBack = true)
        nodosAccesibles()
        dejarQueSalganLosEventos()
        vm.cambiarModoApp(ModoApp.TICKET)
        dejarQueSalganLosEventos()
        val antes = anunciosEnviados().size
        // «Abrir la app» con la principal a la vista (y en Ticket: pasa a Precio).
        vm.ensenarDeFuera(CUADERNO)
        dejarQueSalganLosEventos()
        // Con Ajustes a la vista: la principal no está y regresa con el producto ya en la tarjeta.
        mostrar = false
        idle()
        vm.ensenarDeFuera(LAPIZ)
        idle()
        mostrar = true
        idle()
        dejarQueSalganLosEventos()
        assertEquals(
            listOf("Cuaderno profesional cuadro chico 100 hojas, \$46.00", "Lápiz HB No. 2, \$5.50"),
            anunciosEnviados().drop(antes).filter { it.contains("Lápiz") || it.contains("Cuaderno") },
        )
        assertTrue(anuncioAccesible())
    }

    @Test
    fun `cada lectura anuncia lo que hizo por una region viva oculta`() {
        abrir(ModoApp.PRECIO, talkBack = true)
        leer(CUADERNO)
        assertEquals("Cuaderno profesional cuadro chico 100 hojas, \$46.00", anuncio())
        assertTrue(anuncioAccesible())
        vm.cambiarModoApp(ModoApp.TICKET)
        idle()
        leer(LAPIZ)
        leer(LAPIZ)
        assertEquals("Lápiz HB No. 2 al ticket, van 2. Total \$11.00", anuncio())
        // Con el menú abierto el panel sale del árbol, pero las lecturas siguen contando y se anuncian.
        tocarBoton()
        assertTrue(menuAbierto())
        leer(CUADERNO)
        assertEquals("Cuaderno profesional cuadro chico 100 hojas al ticket. Total \$57.00", anuncio())
        assertTrue(anuncioAccesible())
        atras()
        vm.cambiarModoApp(ModoApp.CONTEO)
        idle()
        leer(GOMA)
        leer(GOMA)
        assertEquals("Goma blanca chica, van 2", anuncio())
        assertTrue(anuncioAccesible())
    }

    // ---- El teclado ----

    private fun teclado() {
        compose.runOnUiThread { modoEntrada!!.requestInputMode(InputMode.Keyboard) }
        idle()
    }

    private fun tecla(k: Key) {
        compose.onRoot().performKeyInput { pressKey(k) }
        idle()
    }

    private fun foco(): String? = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrNull(SemanticsProperties.TestTag)

    private val delMenu = setOf("opcion-arriba", "opcion-diagonal", "opcion-lado", "boton-rapido")

    @Test
    fun `con el menu abierto el foco del teclado no sale del menu`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        teclado()
        compose.onNodeWithTag("boton-rapido").requestFocus()
        idle()
        tecla(Key.Enter)
        assertTrue(menuAbierto())
        assertEquals("Enter abre y lleva el foco a la primera", "opcion-arriba", foco())
        val antes = vm.venta.value
        repeat(14) {
            tecla(Key.Tab)
            assertTrue("El foco salió del menú: ${foco()}", foco() in delMenu)
        }
        repeat(6) {
            tecla(Key.DirectionDown)
            assertTrue("El foco salió del menú: ${foco()}", foco() in delMenu)
        }
        // Enter bajo el velo no hace nada: ni cambia de modo, ni abre el ticket, ni quita renglones.
        assertEquals(ModoApp.TICKET, vm.piso.modo.value)
        assertNull(vm.piso.hoja.value)
        assertTrue(vm.venta.value === antes)
    }

    @Test
    fun `esc y atras cierran el menu y el foco regresa al boton`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        teclado()
        compose.onNodeWithTag("boton-rapido").requestFocus()
        idle()
        tecla(Key.Enter)
        assertEquals("opcion-arriba", foco())
        tecla(Key.Escape)
        assertFalse("Esc cierra", menuAbierto())
        assertEquals("y regresa al botón", "boton-rapido", foco())
        tecla(Key.Enter)
        assertTrue(menuAbierto())
        atras()
        assertFalse(menuAbierto())
        assertEquals("Atrás también", "boton-rapido", foco())
        // Elegir con Enter la apagada (↑ «Mandar»): el aviso dice por qué y el foco regresa al botón.
        tecla(Key.Enter)
        tecla(Key.Enter)
        assertFalse(menuAbierto())
        assertTrue(vm.piso.aviso.value!!.texto.startsWith("Mandarlo a una caja"))
        assertEquals("boton-rapido", foco())
    }

    @Test
    fun `el boton enfocado se ve distinto`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        teclado()
        val sinFoco = captura("regresion/boton-sin-foco")
        compose.onNodeWithTag("boton-rapido").requestFocus()
        idle()
        assertEquals("boton-rapido", foco())
        val conFoco = captura("regresion/boton-con-foco")
        val b = compose.onNodeWithTag("boton-rapido").fetchSemanticsNode().boundsInRoot
        var distintos = 0
        for (x in b.left.toInt() until b.right.toInt()) for (y in b.top.toInt() until b.bottom.toInt()) {
            if (sinFoco.getPixel(x, y) != conFoco.getPixel(x, y)) distintos++
        }
        assertTrue("Píxeles distintos en el botón: $distintos", distintos > 500)
    }

    // ---- TalkBack ----

    @Test
    fun `talkback recorre el menu en su orden con el boton al final`() {
        abrir(ModoApp.TICKET, talkBack = true)
        leer(CUADERNO)
        val cerrado = ordenDeTalkBack(nodosAccesibles())
        assertEquals("Cerrado, el botón es una parada y va al final: $cerrado", "boton-rapido", cerrado.last())
        tocarBoton()
        val abierto = ordenDeTalkBack(nodosAccesibles())
        assertEquals(
            "Abierto, primero el menú en su orden: $abierto",
            listOf("opcion-arriba", "opcion-diagonal", "opcion-lado", "boton-rapido"),
            abierto.take(4),
        )
        assertTrue("La pista del gesto no es una parada: $abierto", "pista-deslizar" !in abierto)
        // Lo que dice el botón: su nombre y cuáles hay.
        val boton = compose.onNodeWithTag("boton-rapido").fetchSemanticsNode()
        assertEquals("Opciones del ticket", boton.config[SemanticsProperties.ContentDescription].single())
        assertEquals("abiertas: Mandar a una caja, Impresiones, Buscar", boton.config[SemanticsProperties.StateDescription])
    }

    @Test
    fun `el asidero, el velo y el numero de la pestana no confunden a talkback`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        val asidero = compose.onNodeWithTag("asidero", useUnmergedTree = true).fetchSemanticsNode()
        assertFalse("El asidero no es un botón sin nombre (el encabezado ya abre el ticket)", asidero.config.contains(SemanticsActions.OnClick))
        val pestana = compose.onNodeWithTag("modo-TICKET").fetchSemanticsNode()
        assertEquals(listOf("Ticket"), pestana.config[SemanticsProperties.Text].map { it.text })
        assertEquals("2 artículos", pestana.config[SemanticsProperties.StateDescription])
        vm.piso.abrir(Hoja.Cantidad(vm.venta.value.actual!!.id))
        idle()
        val velo = compose.onNodeWithTag("velo-cantidad", useUnmergedTree = true).fetchSemanticsNode()
        assertFalse("El velo no es otro botón: la × cierra", velo.config.contains(SemanticsActions.OnClick))
        compose.onNodeWithTag("tecla-7").performTouchInput { down(center); up() }
        idle()
        val numero = compose.onNode(hasContentDescription("Cantidad: 7"), useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("Lo tecleado se anuncia", LiveRegionMode.Polite, numero.config[SemanticsProperties.LiveRegion])
    }

    @Test
    @Config(qualifiers = HOJA_DEL_CHICO)
    fun `buscar dice cuantos encontro`() {
        abrir(ModoApp.TICKET, talkBack = true)
        vm.piso.abrir(Hoja.Buscar)
        idle()
        // Como en el celular, el resultado de más arriba queda entero bajo el subtítulo: sin la barra encima, TalkBack no lo
        // vería. Con 24 dp de más de hoja no quedaba debajo y la prueba pasaba sin eso.
        val bajoLaBarra = sinRecorte(compose.onNode(hasText("Los rápidos y lo más vendido"), useUnmergedTree = true).fetchSemanticsNode())
        val deMasArriba = compose.onAllNodes(SemanticsMatcher("un resultado") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("resultado-") == true })
            .fetchSemanticsNodes().map { sinRecorte(it) }.minBy { it.top }
        assertTrue("El de más arriba ($deMasArriba) no queda bajo el subtítulo ($bajoLaBarra)", deMasArriba.top <= bajoLaBarra.top && deMasArriba.bottom >= bajoLaBarra.bottom)
        val visibles = nodosAccesibles().map { (it.info.text ?: it.info.contentDescription)?.toString() }
        assertTrue("El subtítulo llega a TalkBack: $visibles", "Los rápidos y lo más vendido" in visibles)
        compose.onNodeWithTag("campo-buscar").performTextInput("lapiz")
        idle()
        val campo = compose.onNodeWithTag("campo-buscar").fetchSemanticsNode()
        val n = vm.piso.buscar("lapiz").size
        assertEquals("$n encontrados", campo.config[SemanticsProperties.StateDescription])
        val subtitulo = compose.onNode(androidx.compose.ui.test.hasText("$n encontrados"), useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, subtitulo.config[SemanticsProperties.LiveRegion])
    }

    @Test
    fun `en buscar talkback lee el campo y luego del mejor resultado al peor`() {
        // La lista va de abajo hacia arriba: el mejor junto al campo. Leída de corrido, no debe empezar por el peor.
        abrir(ModoApp.TICKET, talkBack = true)
        vm.piso.abrir(Hoja.Buscar)
        idle()
        compose.onNodeWithTag("campo-buscar").performTextInput("lapiz")
        idle()
        val orden = ordenDeTalkBack(nodosAccesibles())
        val mejores = vm.piso.buscar("lapiz").map { "resultado-${it.clave}" }.filter { it in orden }
        assertTrue("Hay varios resultados a la vista: $orden", mejores.size >= 2)
        val enLaHoja = orden.filter { it == "campo-buscar" || it.startsWith("resultado-") }
        assertEquals("Orden de TalkBack: $orden", listOf("campo-buscar") + mejores, enLaHoja)
        assertTrue("La barra de la hoja va antes que el campo: $orden", orden.indexOf("Agregar al ticket") in 0 until orden.indexOf("campo-buscar"))
    }

    // ---- Los colores ----

    private fun contraste(a: Color, b: Color): Float {
        val (alto, bajo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (alto + 0.05f) / (bajo + 0.05f)
    }

    @Test
    fun `los avisos y la promocion usan colores del tema que se leen`() {
        lateinit var claro: ColorScheme
        lateinit var oscuro: ColorScheme
        var exito = Color.Unspecified
        compose.setContent {
            TemaEscaner(oscuro = false) {
                claro = MaterialTheme.colorScheme
                exito = LocalColoresEstado.current.exito
            }
            TemaEscaner(oscuro = true) { oscuro = MaterialTheme.colorScheme }
        }
        compose.waitForIdle()
        // El «Deshacer» de los avisos ya no es el lila de fábrica de Material.
        assertNotEquals(lightColorScheme().inversePrimary, claro.inversePrimary)
        for (t in listOf(claro, oscuro)) {
            assertTrue(contraste(t.inversePrimary, t.inverseSurface) >= 4.5f)
            assertTrue(contraste(t.inverseOnSurface, t.inverseSurface) >= 4.5f)
        }
        assertTrue("La promoción (14 sp) sobre el panel", contraste(exito, claro.surfaceContainer) >= 4.5f)
    }

    @Test
    fun `en oscuro las opciones del menu se separan del velo`() {
        abrir(ModoApp.TICKET, oscuro = true)
        leer(CUADERNO)
        compose.onNodeWithTag("boton-rapido").performTouchInput { down(center); up() }
        idle()
        assertTrue(menuAbierto())
        val imagen = captura("regresion/oscuro-menu")
        for (tag in listOf("opcion-arriba", "opcion-diagonal", "opcion-lado")) {
            // La orilla de arriba del círculo, contra lo que queda justo encima (el velo sobre el panel): 3:1 o más.
            val r = limites(tag)!!
            val x = ((r.left + r.right) / 2 * densidad).toInt()
            val arriba = (r.top * densidad).toInt()
            val orilla = Color(imagen.getPixel(x, arriba + 1))
            val afuera = Color(imagen.getPixel(x, arriba - 3))
            assertTrue("$tag: ${contraste(orilla, afuera)}:1", contraste(orilla, afuera) >= 3f)
        }
    }
}
