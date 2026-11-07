package mx.papeleriapos.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** CantidadTests.cs del POS, con los mismos casos; al final, los desbordes, que en Kotlin no se revisan solos. */
class CantidadTest {
    private fun unidades(texto: String) = Cantidad.unidades(BigDecimal(texto))

    @Test
    fun `las unidades se guardan en milesimas`() {
        assertEquals(1000L, Cantidad.piezas(1).milesimas)
        assertEquals(1500L, unidades("1.5").milesimas)
        assertEquals(250L, unidades("0.25").milesimas)
        assertEquals(0, BigDecimal("1.5").compareTo(unidades("1.5").enUnidades))
    }

    @Test
    fun `mas de tres decimales no se aceptan`() {
        assertThrows(IllegalArgumentException::class.java) { unidades("0.0005") }
    }

    @Test
    fun `paquetes por factor dan unidades base`() {
        val docena = Cantidad.piezas(12)

        assertEquals(Cantidad.piezas(18), unidades("1.5").por(docena))
        assertEquals(Cantidad.piezas(36), Cantidad.piezas(3).por(docena))
    }

    @Test
    fun `el importe se redondea al centavo`() {
        val casos = listOf(
            Triple("1.5", 750L, 1125L), // 1.5 m a $7.50 = $11.25
            Triple("0.333", 1000L, 333L), // 0.333 kg a $10.00 = $3.33
            Triple("0.125", 4L, 1L), // 0.5 centavos se redondea hacia arriba
            Triple("-2", 1550L, -3100L), // devolución
        )
        for ((cantidad, precio, esperado) in casos) {
            assertEquals("$cantidad a $precio", esperado, unidades(cantidad).importe(precio))
        }
    }

    @Test
    fun `solo las piezas completas son enteras`() {
        assertTrue(Cantidad.piezas(3).esEntera)
        assertFalse(unidades("2.5").esEntera)
    }

    @Test
    fun `se muestra sin ceros de mas`() {
        assertEquals("1.5", unidades("1.5").toString())
        assertEquals("12", Cantidad.piezas(12).toString())
    }

    // ── Lo de Kotlin: los negativos al mostrarse y los desbordes ──

    @Test
    fun `los negativos y las fracciones chicas se muestran como en el POS`() {
        assertEquals("-0.5", unidades("-0.5").toString())
        assertEquals("-2", Cantidad.piezas(-2).toString())
        assertEquals("-1.25", unidades("-1.25").toString())
        assertEquals("0.005", Cantidad(5).toString())
        assertEquals("0", Cantidad.CERO.toString())
        assertEquals("1.5", unidades("1.50000").toString())
    }

    /**
     * Lo esperado es lo que da `new Cantidad(m).EnUnidades` en el núcleo del POS, con su escala (`decimal.GetBits`): el
     * cero y 30 sin decimales (no 0.000 ni el 3E+1 de stripTrailingZeros) y los extremos del Long con los tres. Al
     * sumar quedan los decimales del que más tiene, como en el `decimal`: 1.5 + 1.5 = 3.0 en los dos. Y el texto de la
     * cantidad es el mismo (el «0.###» del POS).
     */
    @Test
    fun `en unidades lleva solo los decimales que hacen falta, como el decimal del POS`() {
        val casos = listOf(
            0L to "0", 10L to "0.01", 100L to "0.1", 1010L to "1.01", -1L to "-0.001", 30_000L to "30",
            -30_000L to "-30", 1_000_000_000L to "1000000",
            Long.MAX_VALUE to "9223372036854775.807", Long.MIN_VALUE to "-9223372036854775.808",
            9_223_372_036_854_775_800L to "9223372036854775.8", -9_223_372_036_854_775_000L to "-9223372036854775",
        )
        for ((milesimas, texto) in casos) {
            val enUnidades = Cantidad(milesimas).enUnidades
            assertEquals("$milesimas", BigDecimal(texto), enUnidades)
            assertEquals("$milesimas", texto, enUnidades.toString())
            assertEquals("$milesimas", texto, Cantidad(milesimas).toString())
        }
        assertEquals("3.0", (unidades("1.5").enUnidades + unidades("1.5").enUnidades).toString())
    }

    @Test
    fun `el producto que no cabe en un Long da el mismo importe que el Int128 del POS`() {
        // 9,000,000,000.001 unidades a $30,000.01: el producto (2.7 × 10¹⁹) ya no cabe en un Long, el importe sí.
        val mucho = Cantidad(9_000_000_000_001)
        assertEquals(27_000_009_000_003_000L, mucho.importe(3_000_001))
        assertEquals(-27_000_009_000_003_000L, (-mucho).importe(3_000_001))
        // El medio centavo, también con el producto grande, se aleja de cero.
        val casiLaRaiz = Cantidad(3_037_000_501)
        assertEquals(9_223_372_040_037_251L, casiLaRaiz.importe(3_037_000_500))
        assertEquals(-9_223_372_040_037_251L, casiLaRaiz.importe(-3_037_000_500))
        assertEquals(Cantidad(9_223_372_040_037_251), casiLaRaiz.por(Cantidad(3_037_000_500)))
    }

    @Test
    fun `lo que no cabe en un Long se desborda con error, como el checked del POS`() {
        assertThrows(ArithmeticException::class.java) { Cantidad(Long.MAX_VALUE).importe(1001) }
        assertThrows(ArithmeticException::class.java) { Cantidad(Long.MAX_VALUE).por(Cantidad.piezas(2)) }
        assertThrows(ArithmeticException::class.java) { Cantidad.piezas(Long.MAX_VALUE / 100) }
        assertThrows(ArithmeticException::class.java) { Cantidad(Long.MAX_VALUE) + Cantidad(1) }
        assertThrows(ArithmeticException::class.java) { Cantidad(Long.MIN_VALUE) - Cantidad(1) }
        assertThrows(ArithmeticException::class.java) { -Cantidad(Long.MIN_VALUE) }
        assertThrows(ArithmeticException::class.java) { Cantidad(Long.MIN_VALUE).abs() }
        assertThrows(ArithmeticException::class.java) { unidades("9223372036854775.808") }
        assertEquals(Cantidad(Long.MAX_VALUE), unidades("9223372036854775.807"))
        assertEquals(Cantidad(Long.MAX_VALUE), Cantidad(Long.MAX_VALUE).por(Cantidad.piezas(1)))
    }

    @Test
    fun `se comparan y se suman por sus milesimas`() {
        assertTrue(unidades("0.5") < Cantidad.piezas(1))
        assertTrue(Cantidad.piezas(12) >= Cantidad(12_000))
        assertEquals(unidades("3.5"), Cantidad.piezas(3) + unidades("0.5"))
        assertEquals(unidades("-0.5"), unidades("1.5") - Cantidad.piezas(2))
        assertEquals(Cantidad.piezas(2), Cantidad.piezas(-2).abs())
        assertTrue(Cantidad(1).esPositiva)
        assertFalse(Cantidad.CERO.esPositiva)
        assertTrue(Cantidad.piezas(-3).esEntera)
        assertFalse(unidades("-2.5").esEntera)
    }
}
