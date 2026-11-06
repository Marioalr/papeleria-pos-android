package mx.papeleriapos.android.botonrapido

import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.escaner.nucleo.Modo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué lleva el botón rápido en cada modo (docs/propuesta-interfaz.md, «Qué lleva cada modo»). */
class OpcionesTest {

    private fun lugares(modo: ModoApp) = opcionesDe(modo, EstadoOpciones()).map { it.lugar to it.accion }

    @Test
    fun `cada direccion quiere decir siempre lo mismo`() {
        assertEquals(
            listOf(Lugar.ARRIBA to Accion.MANDAR, Lugar.DIAGONAL to Accion.IMPRESIONES, Lugar.LADO to Accion.BUSCAR),
            lugares(ModoApp.TICKET),
        )
        assertEquals(listOf(Lugar.ARRIBA to Accion.ENSENAR, Lugar.LADO to Accion.BUSCAR), lugares(ModoApp.PRECIO))
        // ← siempre busca.
        for (m in listOf(ModoApp.TICKET, ModoApp.PRECIO)) {
            assertEquals(Accion.BUSCAR, opcionesDe(m, EstadoOpciones()).single { it.lugar == Lugar.LADO }.accion)
        }
    }

    @Test
    fun `en precio el hueco de la diagonal y sin producto no se ensena nada`() {
        val sin = opcionesDe(ModoApp.PRECIO, EstadoOpciones(hayProducto = false))
        assertTrue(sin.none { it.lugar == Lugar.DIAGONAL })
        assertFalse(sin.single { it.accion == Accion.ENSENAR }.encendida)
        val con = opcionesDe(ModoApp.PRECIO, EstadoOpciones(hayProducto = true))
        assertTrue(con.single { it.accion == Accion.ENSENAR }.encendida)
    }

    @Test
    fun `sin la conexion al pos mandar e impresiones salen apagadas con su razon`() {
        val vacio = opcionesDe(ModoApp.TICKET, EstadoOpciones(ticketVacio = true))
        assertTrue(vacio.single { it.accion == Accion.MANDAR }.razonApagada!!.startsWith("El ticket está vacío"))
        val lleno = opcionesDe(ModoApp.TICKET, EstadoOpciones(ticketVacio = false))
        assertEquals(SIN_CONEXION_MANDAR, lleno.single { it.accion == Accion.MANDAR }.razonApagada)
        assertEquals(SIN_CONEXION_IMPRESIONES, lleno.single { it.accion == Accion.IMPRESIONES }.razonApagada)
        assertTrue(lleno.single { it.accion == Accion.BUSCAR }.encendida)
    }

    @Test
    fun `conteo no tiene boton`() {
        assertTrue(opcionesDe(ModoApp.CONTEO, EstadoOpciones()).isEmpty())
        assertNull(pistaDe(ModoApp.CONTEO, zurdo = false))
    }

    @Test
    fun `las etiquetas llevan su flecha y en zurdo va en espejo`() {
        val buscar = opcionesDe(ModoApp.TICKET, EstadoOpciones()).single { it.accion == Accion.BUSCAR }
        assertEquals("← Buscar", etiqueta(buscar, zurdo = false))
        assertEquals("→ Buscar", etiqueta(buscar, zurdo = true))
        assertEquals("↗", flecha(Lugar.DIAGONAL, zurdo = true))
        assertEquals("↑", flecha(Lugar.ARRIBA, zurdo = true))
    }

    @Test
    fun `la pista de la primera vez dice las direcciones del modo`() {
        assertEquals("Pulsa y desliza: ↑ mandar · ↖ impresiones · ← buscar", pistaDe(ModoApp.TICKET, zurdo = false))
        assertEquals("Pulsa y desliza: ↑ enseñar · ← buscar", pistaDe(ModoApp.PRECIO, zurdo = false))
        assertEquals("Pulsa y desliza: ↑ enseñar · → buscar", pistaDe(ModoApp.PRECIO, zurdo = true))
    }

    @Test
    fun `el nombre del boton para talkback dice de que modo es`() {
        assertEquals("Opciones del ticket", nombreDelBoton(ModoApp.TICKET))
        assertEquals("Opciones del precio", nombreDelBoton(ModoApp.PRECIO))
    }

    // ---- Los modos de la app y los del núcleo ----

    @Test
    fun `precio y ticket escanean como venta y conteo como conteo`() {
        assertEquals(Modo.VENTA, ModoApp.PRECIO.delNucleo)
        assertEquals(Modo.VENTA, ModoApp.TICKET.delNucleo)
        assertEquals(Modo.CONTEO, ModoApp.CONTEO.delNucleo)
        assertEquals(ModoApp.TICKET, ModoApp.deNucleo(Modo.VENTA))
        assertEquals(ModoApp.CONTEO, ModoApp.deNucleo(Modo.CONTEO))
    }

    @Test
    fun `los accesos directos viejos de venta abren el ticket`() {
        assertEquals(ModoApp.TICKET, ModoApp.deExtra("venta"))
        assertEquals(ModoApp.TICKET, ModoApp.deExtra("ticket"))
        assertEquals(ModoApp.PRECIO, ModoApp.deExtra("precio"))
        assertEquals(ModoApp.CONTEO, ModoApp.deExtra("conteo"))
        assertNull(ModoApp.deExtra(null))
        assertNull(ModoApp.deExtra("otro"))
        assertEquals(ModoApp.PRECIO, ModoApp.deNombre("PRECIO"))
        assertNull(ModoApp.deNombre("precio"))
    }
}
