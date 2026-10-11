package mx.papeleriapos.escaner.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GtinTest {
    @Test
    fun `el verificador de un EAN-13 mexicano`() {
        assertEquals(6, Gtin.digitoVerificador("750999345678"))
        assertEquals("7509993456786", Gtin.completar("750999345678"))
        assertTrue(Gtin.esValido("7509993456786"))
        assertFalse(Gtin.esValido("7509993456787"))
    }

    @Test
    fun `EAN-8, UPC-A e ITF-14 usan la misma regla`() {
        assertTrue(Gtin.esValido("75099916")) // EAN-8
        assertTrue(Gtin.esValido("099999291452")) // UPC-A
        assertTrue(Gtin.esValido("17509995000014")) // ITF-14 / GTIN-14
        assertFalse(Gtin.esValido("099999291453"))
    }

    @Test
    fun `solo largos de GTIN y solo digitos`() {
        assertFalse(Gtin.esValido("12345"))
        assertFalse(Gtin.esValido("75099934567A6"))
        assertFalse(Gtin.esValido(""))
    }

    @Test
    fun `un UPC-E se expande a su UPC-A`() {
        assertEquals("099100005268", Gtin.upcEaUpcA("09952618"))
        // Último dígito 3: los tres primeros, cinco ceros y los dos siguientes.
        val upcA = Gtin.completar("09990000041")
        assertEquals(upcA, Gtin.upcEaUpcA("0999413" + upcA.last()))
        assertNull(Gtin.upcEaUpcA("29952618")) // sistema 2 no existe en UPC-E
        assertNull(Gtin.upcEaUpcA("09952619")) // verificador malo
    }

    @Test
    fun `los internos empiezan con 2`() {
        val interno = Gtin.completar("299999000001")
        assertTrue(Gtin.esInterno(interno))
        assertFalse(Gtin.esInterno("7509993456786"))
    }
}
