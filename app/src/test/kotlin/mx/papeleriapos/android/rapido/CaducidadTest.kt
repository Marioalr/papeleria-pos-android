package mx.papeleriapos.android.rapido

import mx.papeleriapos.android.Arranque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaducidadTest {
    private val minuto = 60_000L

    @Test
    fun seCierraTrasLosMinutosSinEscanear() {
        val inicio = 1_000_000L
        assertEquals(5 * minuto, msParaCerrar(inicio, inicio, 5))
        assertEquals(2 * minuto, msParaCerrar(inicio, inicio + 3 * minuto, 5))
        assertEquals(0L, msParaCerrar(inicio, inicio + 5 * minuto, 5))
        assertTrue(msParaCerrar(inicio, inicio + 6 * minuto, 5) < 0L)
    }

    @Test
    fun conCeroMinutosNuncaSeCierra() {
        assertEquals(Long.MAX_VALUE, msParaCerrar(0L, 10 * 60 * minuto, 0))
        assertEquals(Long.MAX_VALUE, msParaCerrar(0L, 10 * 60 * minuto, -3))
    }

    @Test
    fun unRelojQueRetrocedeNoAlargaElPlazo() {
        // elapsedRealtime no retrocede, pero si la última actividad viniera «del futuro» no se suma tiempo.
        assertEquals(5 * minuto, msParaCerrar(2 * minuto, minuto, 5))
    }

    @Test
    fun lasMarcasAlRegistroDeArranquesUsanClavesPropiasDistintas() {
        // ApplicationStartInfo solo acepta claves de 21 a 30 para las marcas de la app.
        val claves = Arranque.CLAVES_INICIO.map { it.second }
        assertTrue(claves.all { it in 21..30 })
        assertEquals(claves.size, claves.toSet().size)
        assertEquals(Arranque.CLAVES_INICIO.size, Arranque.CLAVES_INICIO.map { it.first }.toSet().size)
    }
}
