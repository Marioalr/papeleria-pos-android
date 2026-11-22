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
            listOf(Lugar.ARRIBA to Accion.ENSENAR, Lugar.DIAGONAL to Accion.IMPRESIONES, Lugar.LADO to Accion.BUSCAR),
            lugares(ModoApp.VENTA),
        )
        assertEquals(listOf(Lugar.LADO to Accion.BUSCAR), lugares(ModoApp.CONTEO))
        // ← siempre busca.
        for (m in ModoApp.entries) {
            assertEquals(Accion.BUSCAR, opcionesDe(m, EstadoOpciones()).single { it.lugar == Lugar.LADO }.accion)
        }
    }

    @Test
    fun `sin producto en la tarjeta no se ensena nada`() {
        val sin = opcionesDe(ModoApp.VENTA, EstadoOpciones(hayProducto = false))
        assertFalse(sin.single { it.accion == Accion.ENSENAR }.encendida)
        val con = opcionesDe(ModoApp.VENTA, EstadoOpciones(hayProducto = true))
        assertTrue(con.single { it.accion == Accion.ENSENAR }.encendida)
    }

    @Test
    fun `impresiones sale apagada con su razon y cobrar ya no va en el boton`() {
        val venta = opcionesDe(ModoApp.VENTA, EstadoOpciones(hayProducto = true))
        assertEquals(SIN_CONEXION_IMPRESIONES, venta.single { it.accion == Accion.IMPRESIONES }.razonApagada)
        assertTrue(venta.single { it.accion == Accion.BUSCAR }.encendida)
        assertTrue("Mandar a una caja es el botón «Cobrar», no una opción del gesto", Accion.entries.none { it.name == "MANDAR" })
    }

    @Test
    fun `en conteo el boton solo busca`() {
        assertEquals(listOf(Accion.BUSCAR), opcionesDe(ModoApp.CONTEO, EstadoOpciones()).map { it.accion })
        assertEquals("Pulsa y desliza: ← buscar", pistaDe(ModoApp.CONTEO, zurdo = false))
    }

    @Test
    fun `las etiquetas llevan su flecha y en zurdo va en espejo`() {
        val buscar = opcionesDe(ModoApp.VENTA, EstadoOpciones()).single { it.accion == Accion.BUSCAR }
        assertEquals("← Buscar", etiqueta(buscar, zurdo = false))
        assertEquals("→ Buscar", etiqueta(buscar, zurdo = true))
        assertEquals("↗", flecha(Lugar.DIAGONAL, zurdo = true))
        assertEquals("↑", flecha(Lugar.ARRIBA, zurdo = true))
    }

    @Test
    fun `la pista de la primera vez dice las direcciones del modo`() {
        assertEquals("Pulsa y desliza: ↑ enseñar · ↖ impresiones · ← buscar", pistaDe(ModoApp.VENTA, zurdo = false))
        assertEquals("Pulsa y desliza: ↑ enseñar · ↗ impresiones · → buscar", pistaDe(ModoApp.VENTA, zurdo = true))
    }

    @Test
    fun `el nombre del boton para talkback dice de que modo es`() {
        assertEquals("Opciones del ticket", nombreDelBoton(ModoApp.VENTA))
        assertEquals("Opciones del conteo", nombreDelBoton(ModoApp.CONTEO))
    }

    // ---- Los modos de la app y los del núcleo ----

    @Test
    fun `la venta escanea como venta y conteo como conteo`() {
        assertEquals(Modo.VENTA, ModoApp.VENTA.delNucleo)
        assertEquals(Modo.CONTEO, ModoApp.CONTEO.delNucleo)
        assertEquals(ModoApp.VENTA, ModoApp.deNucleo(Modo.VENTA))
        assertEquals(ModoApp.CONTEO, ModoApp.deNucleo(Modo.CONTEO))
    }

    @Test
    fun `los modos y los accesos de antes de la 0_5 abren la venta`() {
        assertEquals(ModoApp.VENTA, ModoApp.deExtra("venta"))
        assertEquals(ModoApp.VENTA, ModoApp.deExtra("ticket"))
        assertEquals(ModoApp.VENTA, ModoApp.deExtra("precio"))
        assertEquals(ModoApp.CONTEO, ModoApp.deExtra("conteo"))
        assertNull(ModoApp.deExtra(null))
        assertNull(ModoApp.deExtra("otro"))
        // Lo que guardó una versión anterior como último modo.
        assertEquals(ModoApp.VENTA, ModoApp.deNombre("PRECIO"))
        assertEquals(ModoApp.VENTA, ModoApp.deNombre("TICKET"))
        assertEquals(ModoApp.VENTA, ModoApp.deNombre("VENTA"))
        assertEquals(ModoApp.CONTEO, ModoApp.deNombre("CONTEO"))
        assertNull(ModoApp.deNombre("precio"))
    }
}
