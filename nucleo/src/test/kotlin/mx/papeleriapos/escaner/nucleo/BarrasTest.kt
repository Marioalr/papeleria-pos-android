package mx.papeleriapos.escaner.nucleo

import org.junit.Assert.assertEquals
import org.junit.Test

class BarrasTest {
    @Test
    fun `un EAN-13 mide 95 modulos con sus guardas`() {
        val m = Barras.ean13("7501234567893")
        assertEquals(95, m.size)
        assertEquals("101", m.take(3).texto())
        assertEquals("01010", m.slice(45 until 50).texto())
        assertEquals("101", m.takeLast(3).texto())
    }

    @Test
    fun `un EAN-8 mide 67 modulos`() {
        assertEquals(67, Barras.ean8("96385074").size)
    }

    @Test
    fun `un UPC-A son las barras del EAN-13 con 0 adelante`() {
        assertEquals(Barras.ean13("0036000291452").texto(), Barras.upcA("036000291452").texto())
    }

    @Test
    fun `Code 128 pone los digitos en pares como el POS`() {
        // Inicio C, 75, 01, 23, verificación, fin.
        val s = Barras.simbolos128("75012345")
        assertEquals(105, s.first())
        assertEquals(listOf(75, 1, 23, 45), s.subList(1, 5))
        assertEquals(106, s.last())
        // Letras: inicio B.
        assertEquals(104, Barras.simbolos128("ABC-12").first())
    }

    @Test
    fun `un ITF-14 empieza con el patron de inicio`() {
        val m = Barras.itf("10012345000017")
        assertEquals("1010", m.take(4).texto())
    }

    private fun List<Boolean>.texto() = joinToString("") { if (it) "1" else "0" }
    private fun BooleanArray.texto() = toList().texto()
}
