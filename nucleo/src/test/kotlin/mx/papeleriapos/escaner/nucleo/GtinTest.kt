package mx.papeleriapos.escaner.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GtinTest {
    @Test
    fun `el verificador de un EAN-13 mexicano`() {
        assertEquals(3, Gtin.digitoVerificador("750123456789"))
        assertEquals("7501234567893", Gtin.completar("750123456789"))
        assertTrue(Gtin.esValido("7501234567893"))
        assertFalse(Gtin.esValido("7501234567894"))
    }

    @Test
    fun `EAN-8, UPC-A e ITF-14 usan la misma regla`() {
        assertTrue(Gtin.esValido("96385074")) // EAN-8 de ejemplo de GS1
        assertTrue(Gtin.esValido("036000291452")) // UPC-A
        assertTrue(Gtin.esValido("10012345000017")) // ITF-14 / GTIN-14
        assertFalse(Gtin.esValido("036000291453"))
    }

    @Test
    fun `solo largos de GTIN y solo digitos`() {
        assertFalse(Gtin.esValido("12345"))
        assertFalse(Gtin.esValido("75012345678A3"))
        assertFalse(Gtin.esValido(""))
    }

    @Test
    fun `un UPC-E se expande a su UPC-A`() {
        assertEquals("042100005264", Gtin.upcEaUpcA("04252614"))
        // Último dígito 3: los tres primeros, cinco ceros y los dos siguientes.
        val upcA = Gtin.completar("01230000041")
        assertEquals(upcA, Gtin.upcEaUpcA("0123413" + upcA.last()))
        assertNull(Gtin.upcEaUpcA("24252614")) // sistema 2 no existe en UPC-E
        assertNull(Gtin.upcEaUpcA("04252615")) // verificador malo
    }

    @Test
    fun `los internos empiezan con 2`() {
        val interno = Gtin.completar("200000000001")
        assertTrue(Gtin.esInterno(interno))
        assertFalse(Gtin.esInterno("7501234567893"))
    }
}
