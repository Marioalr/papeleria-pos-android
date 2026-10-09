package mx.papeleriapos.android.banco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstadisticaTest {
    private val delta = 1e-9

    @Test
    fun `mediana y p95 interpolan como numpy`() {
        // numpy.percentile(range(1, 11), [50, 95]) = [5.5, 9.55]
        val e = Estadistica.de((1..10).map { it.toDouble() })!!
        assertEquals(10, e.n)
        assertEquals(5.5, e.mediana, delta)
        assertEquals(9.55, e.p95, delta)
    }

    @Test
    fun `no importa el orden de entrada`() {
        val e = Estadistica.de(listOf(61.0, 95.8, 64.0, 62.5, 69.0))!!
        assertEquals(64.0, e.mediana, delta)
        // Ordenados: 61, 62.5, 64, 69, 95.8; p95 está en la posición 3.8 → 69 + 0.8 · 26.8.
        assertEquals(90.44, e.p95, delta)
    }

    @Test
    fun `un solo dato es su propia mediana y p95`() {
        val e = Estadistica.de(listOf(42.0))!!
        assertEquals(42.0, e.mediana, delta)
        assertEquals(42.0, e.p95, delta)
    }

    @Test
    fun `sin datos no hay estadistica`() {
        assertNull(Estadistica.de(emptyList()))
    }

    @Test
    fun `los extremos son el minimo y el maximo`() {
        val v = doubleArrayOf(3.0, 7.0, 11.0)
        assertEquals(3.0, Estadistica.percentil(v, 0.0), delta)
        assertEquals(11.0, Estadistica.percentil(v, 100.0), delta)
        assertEquals(9.0, Estadistica.percentil(v, 75.0), delta)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un percentil fuera de rango falla`() {
        Estadistica.percentil(doubleArrayOf(1.0), 101.0)
    }
}
