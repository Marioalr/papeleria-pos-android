package mx.papeleriapos.escaner.nucleo

import org.junit.Assert.assertEquals
import org.junit.Test

class BarrasTest {
    @Test
    fun `un EAN-13 mide 95 modulos con sus guardas`() {
        val m = Barras.ean13("7509993456786")
        assertEquals(95, m.size)
        assertEquals("101", m.take(3).texto())
        assertEquals("01010", m.slice(45 until 50).texto())
        assertEquals("101", m.takeLast(3).texto())
    }

    @Test
    fun `un EAN-8 mide 67 modulos`() {
        assertEquals(67, Barras.ean8("75099916").size)
    }

    @Test
    fun `un UPC-A son las barras del EAN-13 con 0 adelante`() {
        assertEquals(Barras.ean13("0099999291452").texto(), Barras.upcA("099999291452").texto())
    }

    @Test
    fun `Code 128 pone los digitos en pares como el POS`() {
        // Inicio C, 75, 09, 99, 33, verificación, fin.
        val s = Barras.simbolos128("75099933")
        assertEquals(105, s.first())
        assertEquals(listOf(75, 9, 99, 33), s.subList(1, 5))
        assertEquals(106, s.last())
        // Letras: inicio B.
        assertEquals(104, Barras.simbolos128("ZZC-12").first())
    }

    @Test
    fun `un ITF-14 empieza con el patron de inicio`() {
        val m = Barras.itf("17509995000014")
        assertEquals("1010", m.take(4).texto())
    }

    private fun List<Boolean>.texto() = joinToString("") { if (it) "1" else "0" }
    private fun BooleanArray.texto() = toList().texto()
}
