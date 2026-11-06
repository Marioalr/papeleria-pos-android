package mx.papeleriapos.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

/**
 * De RedondeoTests.cs del POS, la prueba de `Redondeo.AlPeso` (las demás son del cobro en efectivo de la venta). Luego lo
 * de [Redondeo.dividir] que allá da el `Int128`: los medios, los signos y los números que no caben en un `Long`.
 */
class RedondeoTest {
    @Test
    fun `al peso mas cercano los 50 centavos suben`() {
        val casos = listOf(3749L to 3700L, 3750L to 3800L, 3700L to 3700L, 49L to 0L, 50L to 100L)
        for ((centavos, redondeado) in casos) {
            assertEquals("$centavos", redondeado, Redondeo.alPeso(centavos))
        }
    }

    @Test
    fun `los medios se alejan de cero con cualquier signo`() {
        assertEquals(2L, Redondeo.dividir(3, 2))
        assertEquals(-2L, Redondeo.dividir(-3, 2))
        assertEquals(-2L, Redondeo.dividir(3, -2))
        assertEquals(2L, Redondeo.dividir(-3, -2))
        assertEquals(0L, Redondeo.dividir(1, 3))
        assertEquals(1L, Redondeo.dividir(2, 3))
        assertEquals(-1L, Redondeo.dividir(-2, 3))
        assertEquals(1L, Redondeo.dividir(1499, 1000))
        assertEquals(2L, Redondeo.dividir(1500, 1000))
        assertEquals(-37L, Redondeo.alPeso(-3749) / 100)
        assertEquals(-3800L, Redondeo.alPeso(-3750))
    }

    @Test
    fun `entre cero no se divide`() {
        assertThrows(ArithmeticException::class.java) { Redondeo.dividir(1, 0) }
        assertThrows(ArithmeticException::class.java) { Redondeo.dividir(BigInteger.ONE, BigInteger.ZERO) }
    }

    @Test
    fun `en las orillas de un Long da lo mismo que el Int128 del POS`() {
        assertEquals(4_611_686_018_427_387_904L, Redondeo.dividir(Long.MAX_VALUE, 2)) // …903.5
        assertEquals(-4_611_686_018_427_387_904L, Redondeo.dividir(Long.MIN_VALUE, 2))
        assertEquals(Long.MIN_VALUE, Redondeo.dividir(Long.MIN_VALUE, 1))
        assertEquals(-Long.MAX_VALUE, Redondeo.dividir(Long.MAX_VALUE, -1))
        assertEquals(-1L, Redondeo.dividir(Long.MAX_VALUE, Long.MIN_VALUE))
        assertEquals(1L, Redondeo.dividir(Long.MIN_VALUE, Long.MIN_VALUE))
        assertEquals(1L, Redondeo.dividir(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(1L, Redondeo.dividir(Long.MAX_VALUE, Long.MAX_VALUE - 1)) // n + d / 2 ya no cabría en un Long
        assertEquals(Long.MAX_VALUE / 1000 + 1, Redondeo.dividir(Long.MAX_VALUE, 1000)) // …775.807
        assertEquals(1L, Redondeo.dividir(Long.MAX_VALUE / 2, Long.MAX_VALUE - 1)) // el medio exacto, hacia arriba
        assertEquals(0L, Redondeo.dividir(Long.MAX_VALUE / 2 - 1, Long.MAX_VALUE - 1))
        // 2⁶³ ya no cabe: el checked del POS lanza.
        assertThrows(ArithmeticException::class.java) { Redondeo.dividir(Long.MIN_VALUE, -1) }
    }

    @Test
    fun `el producto que no cabe en un Long se divide entero`() {
        val maximo = BigInteger.valueOf(Long.MAX_VALUE)
        // (2⁶³ − 1)² ÷ (2⁶³ − 1) = 2⁶³ − 1: el producto tiene 126 bits.
        assertEquals(Long.MAX_VALUE, Redondeo.dividirProducto(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(Long.MIN_VALUE, Redondeo.dividirProducto(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE))
        assertEquals(-Long.MAX_VALUE, Redondeo.dividirProducto(Long.MAX_VALUE, -Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, Redondeo.dividir(maximo.multiply(maximo), maximo))
        // 3,037,000,500 × 3,037,000,501 = 9,223,372,040,037,250,500 (más que un Long): entre mil, el medio sube.
        assertEquals(9_223_372_040_037_251L, Redondeo.dividirProducto(3_037_000_500, 3_037_000_501, 1000))
        assertEquals(-9_223_372_040_037_251L, Redondeo.dividirProducto(-3_037_000_500, 3_037_000_501, 1000))
        assertEquals(-9_223_372_040_037_251L, Redondeo.dividirProducto(3_037_000_500, 3_037_000_501, -1000))
        // Lo de siempre va por Long y da lo mismo.
        assertEquals(1125L, Redondeo.dividirProducto(750, 1500, 1000))
        val raiz = BigInteger.valueOf(3_037_000_499)
        assertEquals(Redondeo.dividir(raiz.multiply(raiz), BigInteger.valueOf(7)), Redondeo.dividirProducto(3_037_000_499, 3_037_000_499, 7))
        assertEquals(Redondeo.dividir(raiz.multiply(raiz).negate(), BigInteger.valueOf(7)), Redondeo.dividirProducto(-3_037_000_499, 3_037_000_499, 7))
        // Si el resultado no cabe, lanza.
        assertThrows(ArithmeticException::class.java) { Redondeo.dividirProducto(Long.MAX_VALUE, 3, 2) }
        assertThrows(ArithmeticException::class.java) { Redondeo.dividir(maximo.add(BigInteger.ONE), BigInteger.ONE) }
    }
}
