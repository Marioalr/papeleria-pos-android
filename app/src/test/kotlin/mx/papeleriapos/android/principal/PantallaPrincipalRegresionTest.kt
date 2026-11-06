package mx.papeleriapos.android.principal

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.update
import mx.papeleriapos.android.botonrapido.SIN_CONEXION_IMPRESIONES
import mx.papeleriapos.android.botonrapido.SIN_CONEXION_MANDAR
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.ui.EtiquetasEscaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lo que encontró la revisión de la entrega 1 en la pantalla principal, cada caso como prueba de regresión (falla sin su
 * arreglo), en el chico de verdad ([PruebaDePantalla]).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = CHICO)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PantallaPrincipalRegresionTest : PruebaDePantalla() {

    private fun tocarBoton() {
        compose.onNodeWithTag("boton-rapido").performTouchInput { down(center); up() }
        idle()
    }

    private fun centroBoton() = compose.onNodeWithTag("boton-rapido").fetchSemanticsNode().boundsInRoot.center

    private fun claves() = vm.venta.value.renglones.map { (it.clave ?: it.codigo) to it.cantidad }

    private fun hojaGrande(): String? = (vm.piso.hoja.value as? Hoja.PrecioGrande)?.producto?.clave

    // ---- Ticket: lo último siempre se ve ----

    @Test
    fun `en ticket lo ultimo leido siempre se ve con su menos y su mas`() {
        abrir(ModoApp.TICKET)
        for (c in listOf(CUADERNO, LAPIZ, GOMA, SACAPUNTAS, PEGAMENTO, TIJERAS, FOLDER)) {
            leer(c)
            val panel = limites("panel")!!
            val barra = limites("barra-modos")!!
            val renglon = sinRecorte(compose.onNodeWithTag("renglon-actual", useUnmergedTree = true).fetchSemanticsNode())
            assertTrue("Con ${nombreDe(c)}, el renglón de lo último ($renglon) no cabe en el panel", renglon.top >= panel.top && renglon.bottom <= barra.top)
            val nombre = compose.onAllNodes(hasAnyAncestor(hasTestTag("renglon-actual")) and hasText(nombreDe(c)), useUnmergedTree = true)
            assertEquals("El renglón de lo último es lo que se acaba de leer", 1, nombre.fetchSemanticsNodes().size)
            for (t in listOf("menos-uno", "mas-uno")) {
                val b = limites(t)!!
                assertTrue("$t se ve entero", b.height >= 47f && b.bottom <= barra.top)
            }
        }
        captura("regresion/ticket-siete")
    }

    // ---- «Enseñar al cliente»: nunca un producto viejo ----

    @Test
    fun `tocar arriba con el menu abierto ensena la tarjeta de ese momento`() {
        abrir(ModoApp.PRECIO)
        leer(CUADERNO)
        tocarBoton()
        assertTrue(menuAbierto())
        // Con el menú abierto las lecturas cuentan: la tarjeta cambia.
        leer(LAPIZ)
        compose.onNodeWithTag("opcion-arriba").performClick()
        idle()
        assertEquals(LAPIZ, hojaGrande())
    }

    @Test
    fun `talkback ensena la tarjeta de ahora y no la de un gesto anterior`() {
        abrir(ModoApp.PRECIO)
        leer(CUADERNO)
        compose.onNodeWithTag("boton-rapido").performTouchInput { down(center); moveBy(Offset(0f, -50.dp.toPx())); up() }
        idle()
        assertEquals(CUADERNO, hojaGrande())
        vm.piso.cerrarHoja()
        idle()
        leer(LAPIZ)
        val accion = compose.onNodeWithTag("boton-rapido").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label.startsWith("Enseñar") }
        compose.runOnUiThread { accion.action() }
        idle()
        assertEquals("La acción de TalkBack", LAPIZ, hojaGrande())
        vm.piso.cerrarHoja()
        idle()
        leer(GOMA)
        // El doble toque de TalkBack en el botón y luego en ↑.
        compose.onNodeWithTag("boton-rapido").performSemanticsAction(SemanticsActions.OnClick)
        idle()
        compose.onNodeWithTag("opcion-arriba").performSemanticsAction(SemanticsActions.OnClick)
        idle()
        assertEquals("El doble toque y ↑", GOMA, hojaGrande())
    }

    @Test
    fun `con el gesto ensena lo que habia al bajar el dedo aunque a medio gesto se lea otro`() {
        abrir(ModoApp.PRECIO)
        leer(CUADERNO)
        val c = centroBoton()
        compose.onRoot().performTouchInput { down(0, c); moveTo(0, c + Offset(0f, -50.dp.toPx())) }
        idle()
        leer(LAPIZ)
        compose.onRoot().performTouchInput { up(0) }
        idle()
        assertEquals(CUADERNO, hojaGrande())
    }

    @Test
    fun `arriba no se enciende con un producto leido a medio gesto`() {
        abrir(ModoApp.PRECIO)
        leer(NO_ESTA)
        val c = centroBoton()
        compose.onRoot().performTouchInput { down(0, c); moveTo(0, c + Offset(0f, -60.dp.toPx())) }
        idle()
        leer(CUADERNO)
        val arriba = compose.onNodeWithTag("opcion-arriba").fetchSemanticsNode()
        assertTrue("↑ sigue apagada: al pulsar no había producto", arriba.config[SemanticsProperties.ContentDescription].first().contains("apagada"))
        compose.onRoot().performTouchInput { up(0) }
        idle()
        assertNull(vm.piso.hoja.value)
        assertEquals("Soltar ahí se rechaza con su razón", "Escanea un producto para enseñarlo.", vm.piso.aviso.value?.texto)
    }

    // ---- Con una hoja encima ----

    @Test
    fun `con una hoja encima el boton no esta ni excluye el gesto de atras`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        assertTrue(existe("boton-rapido"))
        assertTrue("Sin hoja, el botón se excluye del gesto de atrás", exclusiones().isNotEmpty())
        vm.piso.abrir(Hoja.Ticket)
        idle()
        assertFalse(existe("boton-rapido"))
        assertTrue("Con el ticket completo, «atrás» desde la orilla es atrás", exclusiones().isEmpty())
        vm.piso.abrir(Hoja.Cantidad(vm.venta.value.actual!!.id))
        idle()
        assertTrue(exclusiones().isEmpty())
        vm.piso.cerrarHoja()
        vm.piso.cerrarHoja()
        idle()
        assertTrue(existe("boton-rapido"))
        assertTrue(exclusiones().isNotEmpty())
    }

    /** El reloj de Compose detenido y el de Android a su hora: el rebote compara los dos. */
    private fun relojesJuntos() {
        compose.mainClock.autoAdvance = false
        val diferencia = compose.mainClock.currentTime - SystemClock.uptimeMillis()
        if (diferencia > 0) ShadowSystemClock.advanceBy(Duration.ofMillis(diferencia))
    }

    private fun cuadros() {
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `el rebote no deja que un toque por reflejo cierre el precio en grande`() {
        abrir(ModoApp.PRECIO)
        leer(CUADERNO)
        val c = centroBoton()
        relojesJuntos()
        // Pulsa 20 dp abajo del centro y sube 40: elige ↑ y suelta dentro de la zona de 64 del botón.
        val pulso = c + Offset(0f, 20 * densidad)
        val suelta = c + Offset(0f, -20 * densidad)
        compose.onRoot().performTouchInput {
            down(pulso)
            moveTo(Offset(pulso.x, c.y))
            moveTo(suelta)
            up()
        }
        cuadros()
        assertEquals(CUADERNO, hojaGrande())
        // El toque por reflejo, 100 ms después y donde soltó: ahí ya no está el botón sino el precio en grande.
        compose.onRoot().performTouchInput {
            advanceEventTime(100)
            down(suelta)
            up()
        }
        cuadros()
        assertEquals("El reflejo no cierra el precio en grande", CUADERNO, hojaGrande())
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `durante el rebote la equis de la hoja si responde y lo demas no`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        val c = centroBoton()
        relojesJuntos()
        // ← con el gesto abre Buscar.
        compose.onRoot().performTouchInput {
            down(c)
            moveTo(c + Offset(-25 * densidad, 0f))
            moveTo(c + Offset(-50 * densidad, 0f))
            up()
        }
        cuadros()
        assertEquals(Hoja.Buscar, vm.piso.hoja.value)
        val resultado = compose.onNodeWithTag("resultado-RAPIDO-COPIA").fetchSemanticsNode().boundsInRoot.center
        val equis = compose.onNodeWithTag("cerrar-hoja").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            advanceEventTime(80)
            down(resultado)
            up()
        }
        cuadros()
        assertEquals("Un resultado no se elige por reflejo", Hoja.Buscar, vm.piso.hoja.value)
        assertTrue(vm.venta.value.renglones.none { it.clave == "RAPIDO-COPIA" })
        compose.onRoot().performTouchInput {
            advanceEventTime(60)
            down(equis)
            up()
        }
        cuadros()
        assertNull("La × sí responde", vm.piso.hoja.value)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `la sesion de prueba no anota lo leido bajo una hoja`() {
        abrir(ModoApp.TICKET)
        vm.iniciarSesion("prueba")
        vm.cambiarEsperado(LAPIZ)
        idle()
        vm.piso.abrir(Hoja.Buscar)
        idle()
        leer(CUADERNO)
        assertTrue("Con la hoja no cuenta", vm.venta.value.renglones.isEmpty())
        vm.piso.cerrarHoja()
        idle()
        leer(LAPIZ)
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            (vm.sesion.value?.intentos?.size ?: 0) > 0
        }
        val intentos = vm.sesion.value!!.intentos
        assertEquals("Solo el intento que sí contó", 1, intentos.size)
        assertTrue("Y es el que se esperaba, no una «lectura incorrecta»", intentos.single().coincide == true)
        vm.terminarSesion()
    }

    // ---- El «Deshacer» de cada aviso ----

    @Test
    fun `el deshacer del aviso regresa lo quitado aunque en medio se lea otro`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        compose.onNodeWithTag("menos-uno").performClick()
        idle()
        assertEquals("Quitado del ticket", vm.piso.aviso.value?.texto)
        leer(GOMA)
        compose.onNodeWithTag("aviso-accion").performClick()
        idle()
        // El lápiz regresa como si no se hubiera quitado; la goma, leída después, queda arriba con su − y su +.
        assertEquals(listOf(GOMA to 1, LAPIZ to 1, CUADERNO to 1), claves())
        assertEquals(1, compose.onAllNodes(hasAnyAncestor(hasTestTag("renglon-actual")) and hasText(nombreDe(GOMA)), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `tras el deshacer del aviso el renglon actual es lo ultimo leido aunque ya estuviera arriba`() {
        abrir(ModoApp.TICKET)
        leer(LAPIZ)
        leer(GOMA) // por error
        // El bote del renglón actual (la goma, en 1) y, con el aviso a la vista, el segundo lápiz.
        compose.onNodeWithTag("menos-uno").performClick()
        idle()
        assertEquals("Quitado del ticket", vm.piso.aviso.value?.texto)
        leer(LAPIZ)
        compose.onNodeWithTag("aviso-accion").performClick()
        idle()
        assertEquals(listOf(LAPIZ to 2, GOMA to 1), claves())
        // El − y el + del panel son del lápiz: el siguiente + es otro lápiz.
        assertEquals(1, compose.onAllNodes(hasAnyAncestor(hasTestTag("renglon-actual")) and hasText(nombreDe(LAPIZ)), useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag("mas-uno").performClick()
        idle()
        assertEquals(listOf(LAPIZ to 3, GOMA to 1), claves())
    }

    @Test
    fun `el deshacer del ticket completo se lleva el aviso de lo que deshizo`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        vm.piso.abrir(Hoja.Ticket)
        idle()
        compose.onAllNodes(hasContentDescription("Quitar") and hasAnyAncestor(hasTestTag("hoja-ticket")), useUnmergedTree = true)[0]
            .performClick()
        idle()
        assertTrue("El aviso se ve encima de la hoja", existe("aviso"))
        // Con el deshacer de la hoja, no con el del aviso: el aviso se va (su «Deshacer» regresaría el lápiz otra vez).
        compose.onNodeWithTag("deshacer-ticket").performClick()
        idle()
        assertFalse(existe("aviso"))
        assertEquals(listOf(LAPIZ to 1, CUADERNO to 1), claves())
    }

    @Test
    fun `el deshacer de ticket vacio regresa lo de antes con lo leido despues arriba`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        vm.piso.abrir(Hoja.Ticket)
        idle()
        compose.onNodeWithTag("empezar-otro").performClick()
        idle()
        assertEquals("Ticket vacío: empieza otro", vm.piso.aviso.value?.texto)
        leer(GOMA)
        compose.onNodeWithTag("aviso-accion").performClick()
        idle()
        assertEquals(listOf(GOMA to 1, LAPIZ to 1, CUADERNO to 1), claves())
    }

    // ---- El menú y Piso siempre dicen lo mismo ----

    @Test
    fun `el menu se cierra al salir de la app y si la pantalla sale con el abierto`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        tocarBoton()
        assertTrue(menuAbierto() && vm.piso.menuAbierto)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        idle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        idle()
        assertFalse("Al volver de segundo plano el menú está cerrado", menuAbierto())
        assertFalse(vm.piso.menuAbierto)
        // La pantalla sale con el menú abierto (a Ajustes, o la Activity se recrea) y vuelve: los avisos no se quedan esperando.
        tocarBoton()
        assertTrue(vm.piso.menuAbierto)
        mostrar = false
        idle()
        mostrar = true
        idle()
        assertFalse(vm.piso.menuAbierto)
        compose.onNodeWithTag("menos-uno").performClick()
        idle()
        assertEquals("Quitado del ticket", vm.piso.aviso.value?.texto)
        assertTrue(existe("aviso"))
    }

    @Test
    fun `cambiar de modo con el menu abierto lo cierra`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        tocarBoton()
        assertTrue(menuAbierto())
        // Como el atajo «Conteo» con la app abierta (onNewIntent): Conteo no tiene botón con qué cerrarlo.
        vm.cambiarModoApp(ModoApp.CONTEO)
        idle()
        assertFalse(menuAbierto())
        assertFalse(existe("pista-deslizar"))
        assertFalse(vm.piso.menuAbierto)
        vm.cambiarModoApp(ModoApp.TICKET)
        idle()
        compose.onNodeWithTag("menos-uno").performClick()
        idle()
        assertTrue("Los avisos no se quedan esperando", existe("aviso"))
    }

    @Test
    fun `un segundo dedo que cierra el menu cancela el gesto del primero`() {
        abrir(ModoApp.PRECIO)
        leer(CUADERNO)
        val c = centroBoton()
        val fuera = Offset(c.x - 150 * densidad, 200 * densidad)
        compose.onRoot().performTouchInput { down(0, c); moveTo(0, c + Offset(0f, -50.dp.toPx())) }
        idle()
        assertTrue(menuAbierto())
        compose.onRoot().performTouchInput { down(1, fuera); up(1) }
        idle()
        assertFalse(menuAbierto())
        compose.onRoot().performTouchInput { up(0) }
        idle()
        assertNull("El gesto del primero ya no elige", vm.piso.hoja.value)
        // Un toque que otro dedo cerró tampoco deja el menú abierto (ni a Piso esperando).
        compose.onRoot().performTouchInput { down(0, c) }
        idle()
        compose.onRoot().performTouchInput { down(1, fuera); up(1) }
        idle()
        compose.onRoot().performTouchInput { up(0) }
        idle()
        assertFalse(menuAbierto())
        assertFalse(vm.piso.menuAbierto)
        compose.onNodeWithTag("al-ticket").performSemanticsAction(SemanticsActions.OnClick)
        idle()
        assertNotNull("El aviso de «Al ticket» sale", vm.piso.aviso.value)
        assertTrue(existe("aviso"))
    }

    // ---- Los avisos ----

    @Test
    fun `un aviso que se veia al abrir el menu se esconde y sale completo al cerrarlo`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        compose.onNodeWithTag("menos-uno").performClick()
        idle()
        val a = vm.piso.aviso.value!!
        tocarBoton()
        assertFalse("Bajo el velo no se ve", existe("aviso"))
        compose.mainClock.advanceTimeBy(6_000)
        idle()
        // Se cierra tocando fuera (el visor).
        compose.onNodeWithTag("menu-rapido").performTouchInput { click(Offset(20f, 200f)) }
        idle()
        assertFalse(menuAbierto())
        assertEquals("El mismo aviso, sin vencerse", a.id, vm.piso.aviso.value?.id)
        assertTrue(existe("aviso"))
        // Y con su tiempo completo: a los 3 s sigue, a los 5 ya no.
        compose.mainClock.advanceTimeBy(3_000)
        idle()
        assertTrue(existe("aviso"))
        compose.mainClock.advanceTimeBy(2_000)
        idle()
        assertFalse(existe("aviso"))
    }

    @Test
    fun `el aviso de quitado sale encima del ticket completo sin tapar su accion principal`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        vm.piso.abrir(Hoja.Ticket)
        idle()
        compose.onAllNodes(hasContentDescription("Quitar") and hasAnyAncestor(hasTestTag("hoja-ticket")), useUnmergedTree = true)[0]
            .performClick()
        idle()
        assertEquals("Quitado del ticket", vm.piso.aviso.value?.texto)
        val aviso = limites("aviso")!!
        assertTrue("Arriba, bajo la barra de la hoja", aviso.top >= limites("deshacer-ticket")!!.bottom)
        assertTrue("No tapa «Mandar» ni «Empezar otro ticket»", aviso.bottom <= limites("mandar-ticket")!!.top)
        captura("regresion/aviso-sobre-ticket-completo")
        // Su «Deshacer» responde: está encima de la hoja.
        compose.onNodeWithTag("aviso-accion").performClick()
        idle()
        assertEquals(listOf(LAPIZ to 1, CUADERNO to 1), claves())
    }

    /** El −, el bote y el + de los renglones del ticket completo que se ven, en dp: los botones enteros, que es lo que se toca. */
    private fun botonesDelTicket(): List<Rect> =
        compose.onAllNodes(
            (hasContentDescription("Uno más") or hasContentDescription("Uno menos") or hasContentDescription("Quitar")) and
                hasAnyAncestor(hasTestTag("hoja-ticket")),
        ).fetchSemanticsNodes().map { enDp(it.boundsInRoot) }.filter { it.height > 0f }

    private fun masDelPrimerRenglon() =
        compose.onAllNodes(hasContentDescription("Uno más") and hasAnyAncestor(hasTestTag("hoja-ticket")))[0]

    private fun assertNoTapaLosRenglones(caso: String) {
        val aviso = limites("aviso")!!
        val tapados = botonesDelTicket().filter { it.overlaps(aviso) }
        assertTrue("$caso: el aviso ($aviso) tapa $tapados", tapados.isEmpty())
    }

    @Test
    fun `el aviso sobre el ticket completo no tapa el menos ni el mas del primer renglon`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        leer(GOMA)
        vm.piso.abrir(Hoja.Ticket)
        idle()
        // El bote de la goma (el primer renglón): el lápiz pasa a ser el primero, y su + queda donde salió el aviso.
        compose.onAllNodes(hasContentDescription("Quitar") and hasAnyAncestor(hasTestTag("hoja-ticket")))[0].performClick()
        idle()
        assertEquals("Quitado del ticket", vm.piso.aviso.value?.texto)
        assertNoTapaLosRenglones("Con el bote")
        captura("regresion/aviso-sobre-ticket-completo-renglones")
        // Su + suma, no deshace: el lápiz lleva otro y la goma no regresa.
        masDelPrimerRenglon().performClick()
        idle()
        assertEquals(listOf(LAPIZ to 2, CUADERNO to 1), claves())
        // Buscar desde el ticket completo y elegir: regresa con «… al ticket» encima y lo elegido como primer renglón.
        compose.onNodeWithTag("buscar-ticket").performClick()
        idle()
        compose.onNodeWithTag("campo-buscar").performTextInput("goma")
        idle()
        compose.onNodeWithTag("resultado-$GOMA").performClick()
        idle()
        assertEquals(Hoja.Ticket, vm.piso.hoja.value)
        assertTrue(vm.piso.aviso.value!!.texto.contains("al ticket"))
        assertNoTapaLosRenglones("Con Buscar")
        masDelPrimerRenglon().performClick()
        idle()
        assertEquals("Dos gomas: el + no la quita", listOf(GOMA to 2, LAPIZ to 2, CUADERNO to 1), claves())
        // Al irse el aviso, la lista vuelve a empezar bajo la barra.
        val conAviso = limites("lista-ticket")!!.top
        compose.mainClock.advanceTimeBy(6_000)
        idle()
        assertFalse(existe("aviso"))
        assertTrue("La lista sube (${limites("lista-ticket")!!.top} contra $conAviso)", limites("lista-ticket")!!.top < conAviso)
    }

    @Test
    fun `con el ticket completo desplazado el aviso tampoco tapa los renglones que se ven`() {
        abrir(ModoApp.TICKET)
        val codigos = listOf(CUADERNO, LAPIZ, GOMA, SACAPUNTAS, PEGAMENTO, TIJERAS, FOLDER) +
            listOf("7509990100088", "7509990100095", "7509990100101", "7509990100118", "7509990100125")
        for (c in codigos) leer(c)
        vm.piso.abrir(Hoja.Ticket)
        idle()
        compose.onNodeWithTag("lista-ticket").performScrollToNode(hasTestTag("buscar-ticket"))
        idle()
        // Desplazada: el primer renglón (lo último leído) ya no se ve entero. Un relleno arriba de su contenido no bastaría.
        val arriba = compose.onAllNodes(hasText(nombreDe(codigos.last())) and hasAnyAncestor(hasTestTag("hoja-ticket")), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("La lista no se desplazó", arriba.isEmpty() || enDp(arriba.single().boundsInRoot).height < sinRecorte(arriba.single()).height - 0.5f)
        // El bote del primero que se ve entero.
        val lista = limites("lista-ticket")!!
        compose.onAllNodes(hasContentDescription("Quitar") and hasAnyAncestor(hasTestTag("hoja-ticket"))).fetchSemanticsNodes()
            .first { enDp(it.boundsInRoot).let { b -> b.top >= lista.top && b.height >= 47f } }
            .let { n -> compose.onRoot().performTouchInput { click(n.boundsInRoot.center) } }
        idle()
        assertEquals("Quitado del ticket", vm.piso.aviso.value?.texto)
        assertNoTapaLosRenglones("Desplazado")
        captura("regresion/aviso-sobre-ticket-completo-desplazado")
    }

    @Test
    fun `el aviso de al ticket no sale encima del precio en grande y espera a que se regrese`() {
        abrir(ModoApp.PRECIO)
        leer(CUADERNO)
        compose.onNodeWithTag("al-ticket").performClick()
        idle()
        val a = vm.piso.aviso.value!!
        // Enseguida, ↑ «Enseñar al cliente»: lo que ve el cliente no lleva el aviso del ticket.
        compose.onNodeWithTag("boton-rapido").performTouchInput { down(center); moveBy(Offset(0f, -50.dp.toPx())); up() }
        idle()
        assertEquals(CUADERNO, hojaGrande())
        assertFalse(existe("aviso"))
        compose.mainClock.advanceTimeBy(6_000)
        idle()
        compose.runOnUiThread { vm.piso.cerrarHoja() }
        idle()
        assertEquals("Al regresar sale el mismo, con su tiempo completo", a.id, vm.piso.aviso.value?.id)
        assertTrue(existe("aviso"))
    }

    @Test
    fun `el aviso dura lo que pide el tiempo para actuar de accesibilidad`() {
        accesibilidad = object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(
                originalTimeoutMillis: Long,
                containsIcons: Boolean,
                containsText: Boolean,
                containsControls: Boolean,
            ): Long = if (containsControls) 20_000L else originalTimeoutMillis
        }
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        compose.onNodeWithTag("menos-uno").performClick()
        idle()
        compose.mainClock.advanceTimeBy(10_000)
        idle()
        assertTrue("Con «Deshacer», a los 10 s sigue", existe("aviso"))
        compose.mainClock.advanceTimeBy(11_000)
        idle()
        assertFalse(existe("aviso"))
    }

    @Test
    fun `el aviso de arriba no tapa la indicacion del escaner`() {
        abrir(ModoApp.PRECIO)
        leer(CUADERNO)
        compose.onNodeWithTag("al-ticket").performClick()
        // Lo leído se confirma medio segundo; luego vuelve la indicación.
        compose.mainClock.advanceTimeBy(700)
        idle()
        val aviso = limites("aviso")!!
        val ajustes = limites("boton-ajustes")!!
        val indicacion = limites(EtiquetasEscaner.INDICACION)!!
        assertEquals("A la barra de estado + 72: 4 dp abajo de Ajustes y la linterna", 24f + 72f, aviso.top, 1f)
        assertTrue("Bajo Ajustes", aviso.top >= ajustes.bottom)
        assertTrue("A lo más el relleno de arriba de la indicación (${aviso.bottom} > ${indicacion.top})", aviso.bottom <= indicacion.top + 8.5f)
    }

    @Test
    fun `con la sesion de prueba el aviso de arriba no tapa no leyo ni su deshacer`() {
        abrir(ModoApp.PRECIO)
        vm.iniciarSesion("prueba")
        idle()
        leer(CUADERNO)
        compose.mainClock.advanceTimeBy(1_500)
        idle()
        compose.onNodeWithTag("al-ticket").performClick()
        idle()
        assertNotNull(vm.piso.aviso.value)
        fun noLosTapa(caso: String) {
            val aviso = limites("aviso")!!
            for (tag in listOf("fallo-TIMEOUT", "deshacer-fallo")) {
                assertFalse("$caso: el aviso ($aviso) tapa $tag (${limites(tag)})", limites(tag)!!.overlaps(aviso))
            }
        }
        noLosTapa("En Precio")
        captura("regresion/aviso-con-sesion")
        // Un toque al centro de «No leyó» registra el intento (antes se lo quedaba el aviso).
        val antes = vm.sesion.value!!.intentos.size
        compose.onNodeWithTag("fallo-TIMEOUT").performClick()
        idle()
        assertEquals(antes + 1, vm.sesion.value!!.intentos.size)
        // Y otro toque al centro de «Al ticket» agrega otro: el aviso de abajo no lo tapa.
        compose.onNodeWithTag("al-ticket").performClick()
        idle()
        assertEquals(listOf(CUADERNO to 2), claves())
        // En Ticket, lo elegido en Buscar desde el panel también avisa arriba.
        compose.mainClock.advanceTimeBy(30_000)
        idle()
        vm.cambiarModoApp(ModoApp.TICKET)
        idle()
        vm.piso.abrir(Hoja.Buscar)
        idle()
        compose.onNodeWithTag("resultado-$LAPIZ").performClick()
        idle()
        assertTrue(vm.piso.aviso.value!!.texto.contains("al ticket"))
        noLosTapa("En Ticket")
        vm.terminarSesion()
    }

    @Test
    fun `con barra de estado el escaner no la vuelve a sumar arriba`() {
        abrir(ModoApp.PRECIO)
        controlador.cambiar { it.copy(hayLinterna = true) }
        vm.piso.abrir(Hoja.Buscar)
        idle()
        val sinBarra = limites("cerrar-hoja")!!.top
        // Robolectric no da barras: se le pasa la de estado del chico (24 dp), como la recibe el celular.
        compose.runOnUiThread {
            val ventana = compose.activity.window
            WindowCompat.setDecorFitsSystemWindows(ventana, false)
            val barra = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (24 * densidad).roundToInt(), 0, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(ventana.decorView, barra)
        }
        idle()
        assertEquals("La barra llegó a Compose: la hoja la deja libre", sinBarra + 24f, limites("cerrar-hoja")!!.top, 0.5f)
        vm.piso.cerrarHoja()
        idle()
        // El relleno del Scaffold ya trae la barra: Ajustes y la linterna van a la barra + 12, no a la barra + 24 + 12.
        val ajustes = limites("boton-ajustes")!!
        val linterna = limites(EtiquetasEscaner.LINTERNA)!!
        assertEquals("Ajustes a la barra + 12", 24f + 12f, ajustes.top, 1f)
        assertEquals("La linterna a la barra + 12, como Ajustes", ajustes.top, linterna.top, 1f)
    }

    // ---- Ajustes siempre se ve ----

    /** Los píxeles casi blancos (el engrane) dentro de [r] (dp). */
    private fun blancos(imagen: Bitmap, r: Rect): Set<Pair<Int, Int>> {
        val d = densidad
        val puntos = HashSet<Pair<Int, Int>>()
        for (x in (r.left * d).toInt() until (r.right * d).toInt()) for (y in (r.top * d).toInt() until (r.bottom * d).toInt()) {
            val p = imagen.getPixel(x, y)
            if ((p shr 16 and 0xFF) > 230 && (p shr 8 and 0xFF) > 230 && (p and 0xFF) > 230) puntos += x to y
        }
        return puntos
    }

    @Test
    fun `ajustes se ve encima del diagnostico y del error del escaner`() {
        abrir(ModoApp.PRECIO)
        val ajustes = limites("boton-ajustes")!!
        val engrane = blancos(captura("regresion/ajustes-solo"), ajustes)
        assertTrue(engrane.size > 200)
        // Con el diagnóstico (que el escáner dibuja en esa esquina) el engrane se sigue viendo entero.
        vm.cambiarConfig(vm.config.value.copy(mostrarDiagnostico = true))
        idle()
        assertTrue(existe(EtiquetasEscaner.DIAGNOSTICO))
        val conDiagnostico = blancos(captura("regresion/ajustes-diagnostico"), ajustes)
        assertTrue("Con el diagnóstico: ${engrane.count { it in conDiagnostico }} de ${engrane.size}", engrane.all { it in conDiagnostico })
        // Con un error de la cámara (su banner va a todo lo ancho, arriba).
        vm.cambiarConfig(vm.config.value.copy(mostrarDiagnostico = false))
        controlador.cambiar { it.copy(fase = Fase.ERROR, mensajeError = "No se pudo abrir la cámara.") }
        idle()
        assertTrue(existe(EtiquetasEscaner.ERROR))
        val conError = blancos(captura("regresion/ajustes-error"), ajustes)
        assertTrue("Con el error: ${engrane.count { it in conError }} de ${engrane.size}", engrane.all { it in conError })
        compose.onNodeWithTag("boton-ajustes").performClick()
        assertEquals(1, ajustesAbiertos)
    }

    @Test
    fun `ajustes se ve encima del aviso de sin volumen del escaner`() {
        // El bip prendido y el volumen de su stream en 0: el escáner dice «Sin sonido: sube el volumen» en esa esquina.
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val audio = app.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audio.setStreamVolume(android.media.AudioManager.STREAM_SYSTEM, 0, 0)
        abrir(ModoApp.PRECIO)
        val ajustes = limites("boton-ajustes")!!
        val engrane = blancos(captura("regresion/ajustes-con-sonido"), ajustes)
        assertTrue(engrane.size > 200)
        vm.cambiarConfig(vm.config.value.copy(sonido = true))
        leer(CUADERNO)
        assertTrue(existe(EtiquetasEscaner.SIN_SONIDO))
        val conAviso = blancos(captura("regresion/ajustes-sin-volumen"), ajustes)
        assertTrue("Con el aviso de sin volumen: ${engrane.count { it in conAviso }} de ${engrane.size}", engrane.all { it in conAviso })
    }

    // ---- Las hojas se apilan ----

    @Test
    fun `la cantidad y buscar abiertas desde el ticket completo regresan a el`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        vm.piso.abrir(Hoja.Ticket)
        idle()
        val cantidad = SemanticsMatcher("teclear") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Teclear la cantidad" }
        compose.onAllNodes(cantidad and hasAnyAncestor(hasTestTag("hoja-ticket")))[0].performClick()
        idle()
        assertTrue(vm.piso.hoja.value is Hoja.Cantidad)
        compose.onNodeWithTag("tecla-3").performClick()
        compose.onNodeWithTag("cantidad-listo").performClick()
        idle()
        assertEquals("«Listo» regresa al ticket completo", Hoja.Ticket, vm.piso.hoja.value)
        assertTrue(existe("hoja-ticket"))
        assertEquals(LAPIZ to 3, claves().first())
        compose.onAllNodes(cantidad and hasAnyAncestor(hasTestTag("hoja-ticket")))[0].performClick()
        idle()
        atras()
        assertEquals("Atrás también", Hoja.Ticket, vm.piso.hoja.value)
        compose.onNodeWithTag("buscar-ticket").performClick()
        idle()
        assertEquals(Hoja.Buscar, vm.piso.hoja.value)
        compose.onNodeWithTag("campo-buscar").performTextInput("goma")
        idle()
        compose.onNodeWithTag("resultado-$GOMA").performClick()
        idle()
        assertEquals("Elegir en Buscar regresa al ticket completo", Hoja.Ticket, vm.piso.hoja.value)
        assertEquals(GOMA to 1, claves().first())
        atras()
        assertNull(vm.piso.hoja.value)
    }

    // ---- Lo que no trae clave ----

    @Test
    fun `lo que sumo el escaneo rapido sale con su nombre y suma al total`() {
        abrir(ModoApp.TICKET)
        // Como lo suma el escaneo rápido: sin la clave del catálogo.
        listas.venta.update { it.sumar(CUADERNO, Simbologia.EAN_13, 1L) }
        idle()
        assertTrue(compose.onAllNodesWithText("Código sin producto", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertEquals(1, compose.onAllNodes(hasText(nombreDe(CUADERNO)), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("\$46.00", textoDe(compose.onNodeWithTag("total-ticket", useUnmergedTree = true).fetchSemanticsNode()))
    }

    @Test
    fun `mientras cargan los productos el renglon no sale en rojo ni el total en cero`() {
        val ticket = ListaLecturas().sumar(CUADERNO, Simbologia.EAN_13, 1L, clave = CUADERNO)
        compose.setContent {
            TemaEscaner(oscuro = false) {
                ContenidoPanel(ModoApp.TICKET, null, ticket, ListaLecturas(), catalogo = null, acciones = AccionesPanel())
            }
        }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Código sin producto", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertEquals("…", textoDe(compose.onNodeWithTag("total-ticket", useUnmergedTree = true).fetchSemanticsNode()))
        assertEquals(1, compose.onAllNodes(hasAnyAncestor(hasTestTag("renglon-actual")) and hasText(CUADERNO), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // ---- Lo que dice el panel ----

    private fun textoDelPanelVacio() = compose.onAllNodes(hasAnyAncestor(hasTestTag("panel-vacio")), useUnmergedTree = true)
        .fetchSemanticsNodes().joinToString(" ") { textoDe(it) }

    @Test
    fun `con el boton a la izquierda el panel vacio dice abajo a la izquierda`() {
        abrir(ModoApp.TICKET) { it.piso.cambiarZurdo(true) }
        assertTrue(textoDelPanelVacio(), textoDelPanelVacio().contains("en el botón de abajo a la izquierda"))
        vm.cambiarModoApp(ModoApp.PRECIO)
        idle()
        assertTrue(textoDelPanelVacio(), textoDelPanelVacio().contains("en el botón de abajo a la izquierda"))
        vm.piso.cambiarZurdo(false)
        idle()
        assertTrue(textoDelPanelVacio(), textoDelPanelVacio().contains("en el botón de abajo a la derecha"))
    }

    @Test
    fun `el conteo vacio no habla de lugares ni de contar a ciegas`() {
        abrir(ModoApp.CONTEO)
        val texto = textoDelPanelVacio()
        assertFalse(texto, texto.contains("lugares") || texto.contains("ciegas"))
        assertTrue(texto, texto.contains("Contar contra el POS llega después del piloto."))
    }

    @Test
    fun `con una sola pieza dice queda 1 en la tarjeta y en el renglon`() {
        abrir(ModoApp.PRECIO)
        leer(SACAPUNTAS)
        assertEquals(1, compose.onAllNodes(hasText("Queda 1"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodes(hasText("Quedan 1"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        vm.cambiarModoApp(ModoApp.TICKET)
        idle()
        leer(SACAPUNTAS)
        val detalle = compose.onAllNodes(hasAnyAncestor(hasTestTag("renglon-actual")) and hasText("Queda 1", substring = true), useUnmergedTree = true)
        assertEquals("Queda 1 · \$9.00 c/u · \$9.00", textoDe(detalle.fetchSemanticsNodes().single()))
    }

    @Test
    fun `de a uno dice pza, va, encontrado e intento en singular`() {
        abrir(ModoApp.CONTEO)
        leer(GOMA)
        assertEquals(1, compose.onAllNodes(hasText("Conteo de prueba · 1 pza"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("Goma blanca chica, va 1", vm.piso.anuncio.value?.texto)
        leer(GOMA)
        assertEquals(1, compose.onAllNodes(hasText("Conteo de prueba · 2 pzs"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("Goma blanca chica, van 2", vm.piso.anuncio.value?.texto)
        // Buscar con un solo resultado.
        vm.piso.abrir(Hoja.Buscar)
        idle()
        assertEquals(1, vm.piso.buscar("calculadora").size)
        compose.onNodeWithTag("campo-buscar").performTextInput("calculadora")
        idle()
        assertEquals("1 encontrado", compose.onNodeWithTag("campo-buscar").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        vm.piso.cerrarHoja()
        idle()
        // La barra de la sesión de prueba con su primer intento.
        vm.iniciarSesion("prueba")
        idle()
        compose.onNodeWithTag("fallo-TIMEOUT").performClick()
        idle()
        assertEquals(1, vm.sesion.value!!.intentos.size)
        assertEquals(1, compose.onAllNodes(hasText("Prueba · 1 intento · ", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        vm.terminarSesion()
        idle()
        // El teclado de la cantidad de un renglón con una pieza.
        vm.cambiarModoApp(ModoApp.TICKET)
        idle()
        leer(LAPIZ)
        vm.piso.abrir(Hoja.Cantidad(vm.venta.value.actual!!.id))
        idle()
        assertEquals(1, compose.onAllNodes(hasText("Va 1: teclea cuántos lleva"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `la burbuja de la primera vez sale solo con el panel vacio`() {
        abrir(ModoApp.TICKET)
        assertTrue(existe("pista-boton"))
        leer(CUADERNO)
        assertFalse("Con renglones no tapa importes ni el − y el +", existe("pista-boton"))
        vm.cambiarModoApp(ModoApp.PRECIO)
        idle()
        assertTrue(existe("pista-boton"))
        leer(GOMA)
        assertFalse("Con la tarjeta no tapa la promoción", existe("pista-boton"))
    }

    @Test
    fun `el ticket completo trae mandar e impresiones apagadas con su razon`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        vm.piso.abrir(Hoja.Ticket)
        idle()
        for ((tag, razon) in listOf("mandar-ticket" to SIN_CONEXION_MANDAR, "impresiones-ticket" to SIN_CONEXION_IMPRESIONES)) {
            val n = compose.onNodeWithTag(tag).fetchSemanticsNode()
            assertTrue("$tag apagado", n.config.contains(SemanticsProperties.Disabled))
            assertEquals("apagada: $razon", n.config[SemanticsProperties.StateDescription])
            assertEquals("Su razón se lee abajo", 1, compose.onAllNodesWithText(razon, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
        // «Mandar» es el principal, abajo; «Empezar otro ticket», con borde, abajo de él.
        assertTrue(limites("mandar-ticket")!!.bottom <= limites("empezar-otro")!!.top)
        assertFalse(compose.onNodeWithTag("empezar-otro").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled))
    }

    // ---- El botón y el panel ----

    @Test
    fun `el boton queda a 16 dp de la orilla y a 4 de la barra de gestos`() {
        abrir(ModoApp.TICKET)
        // Robolectric no da la barra de gestos: la ventana termina donde ella empieza (716).
        val zona = limites("boton-rapido")!!
        assertEquals(360f - 16f - 28f, zona.center.x, 0.5f)
        assertEquals(716f - 4f - 28f, zona.center.y, 0.5f)
        // Y centrado con los modos.
        assertEquals(zona.center.y, limites("modo-PRECIO")!!.center.y, 0.5f)
        vm.piso.cambiarZurdo(true)
        idle()
        assertEquals(16f + 28f, limites("boton-rapido")!!.center.x, 0.5f)
    }

    @Test
    fun `jalar el asidero o el encabezado abre el ticket completo`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        compose.onNodeWithTag("asidero").performTouchInput { swipe(center, center - Offset(0f, 120.dp.toPx()), durationMillis = 300) }
        idle()
        assertEquals(Hoja.Ticket, vm.piso.hoja.value)
        vm.piso.cerrarHoja()
        idle()
        compose.onNodeWithTag("encabezado-ticket").performTouchInput { swipe(center, center - Offset(0f, 120.dp.toPx()), durationMillis = 300) }
        idle()
        assertEquals(Hoja.Ticket, vm.piso.hoja.value)
        vm.piso.cerrarHoja()
        idle()
        // Jalar poco no abre; tocarlo sí.
        compose.onNodeWithTag("asidero").performTouchInput { swipe(center, center - Offset(0f, 16.dp.toPx()), durationMillis = 200) }
        idle()
        assertNull(vm.piso.hoja.value)
        compose.onNodeWithTag("asidero").performTouchInput { click() }
        idle()
        assertEquals(Hoja.Ticket, vm.piso.hoja.value)
    }

    @Test
    fun `en la barra de la sesion caben no leyo y su deshacer`() {
        abrir(ModoApp.TICKET)
        vm.iniciarSesion("prueba")
        idle()
        val barra = limites("barra-sesion")!!
        for (tag in listOf("fallo-TIMEOUT", "deshacer-fallo")) {
            val n = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val visto = enDp(n.boundsInRoot)
            val entero = sinRecorte(n)
            assertTrue("$tag se ve entero: $visto de $entero", abs(visto.width - entero.width) < 0.5f && visto.right <= barra.right)
        }
        vm.terminarSesion()
    }

    // ---- Las opciones salen del botón y la × se marca al ir a cancelar ----

    @Test
    fun `las opciones salen del boton en 150 ms y hacia cancelar se marca la equis`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        val zona = limites("boton-rapido")!!
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("boton-rapido").performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val alSalir = limites("opcion-arriba")!!.center
        assertTrue("Sale del botón (${alSalir.y} contra ${zona.center.y})", zona.center.y - alSalir.y < 60f)
        compose.mainClock.advanceTimeBy(200)
        val enSuLugar = limites("opcion-arriba")!!.center
        assertEquals(zona.center.x, enSuLugar.x, 1f)
        assertEquals(zona.center.y - 88f, enSuLugar.y, 1f)
        assertFalse(existe("boton-rapido-cancela"))
        compose.onNodeWithTag("boton-rapido").performTouchInput { moveBy(Offset(20.dp.toPx(), 20.dp.toPx())); moveBy(Offset(20.dp.toPx(), 20.dp.toPx())) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("Hacia cancelar se marca la ×", existe("boton-rapido-cancela"))
        compose.onNodeWithTag("boton-rapido").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        idle()
        assertFalse(menuAbierto())
    }

    @Test
    fun `sin animaciones en el sistema las opciones salen de golpe`() {
        android.provider.Settings.Global.putFloat(
            compose.activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f,
        )
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        val zona = limites("boton-rapido")!!
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("boton-rapido").performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("Ya en su lugar", zona.center.y - 88f, limites("opcion-arriba")!!.center.y, 1f)
        compose.onNodeWithTag("boton-rapido").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        idle()
    }

    // ---- Lo de cada cuadro del escáner no recompone la pantalla ----

    @OptIn(ExperimentalComposeRuntimeApi::class)
    private fun contarRecomposiciones(accion: () -> Unit): Int {
        var entradas = 0
        val observador = object : CompositionObserver {
            override fun onBeginComposition(composition: ObservableComposition) {}
            override fun onScopeEnter(scope: RecomposeScope) { entradas++ }
            override fun onReadInScope(scope: RecomposeScope, value: Any) {}
            override fun onScopeExit(scope: RecomposeScope) {}
            override fun onEndComposition(composition: ObservableComposition) {}
            override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) {}
            override fun onScopeDisposed(scope: RecomposeScope) {}
        }
        val registro = object : CompositionRegistrationObserver {
            override fun onCompositionRegistered(composition: ObservableComposition) { composition.setObserver(observador) }
            override fun onCompositionUnregistered(composition: ObservableComposition) {}
        }
        val manijas = Recomposer.runningRecomposers.value.mapNotNull { it.observe(registro) }
        idle()
        entradas = 0
        accion()
        manijas.forEach { it.dispose() }
        return entradas
    }

    @Test
    fun `la pantalla no se recompone con cada cuadro del escaner`() {
        abrir(ModoApp.TICKET)
        leer(CUADERNO)
        leer(LAPIZ)
        compose.mainClock.advanceTimeBy(700)
        idle()
        // 30 cuadros con un código a la vista: solo cambia la caja (antes, 1810 scopes contra 120 del escáner solo).
        val n = contarRecomposiciones {
            repeat(30) { i ->
                controlador.cambiar { it.copy(cajaCodigo = Caja(0.20f + i * 0.002f, 0.40f, 0.80f, 0.60f)) }
                idle()
            }
        }
        println("Scopes recompuestos en 30 cuadros con un código a la vista: $n")
        assertTrue("$n scopes recompuestos en 30 cuadros", n < 300)
    }
}
