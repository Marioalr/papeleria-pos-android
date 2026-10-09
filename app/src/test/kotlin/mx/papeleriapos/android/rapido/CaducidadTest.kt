package mx.papeleriapos.android.rapido

import android.app.StatusBarManager
import mx.papeleriapos.android.Arranque
import mx.papeleriapos.escaner.Fase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun conElCelularBloqueadoElPlazoEsDeUnMinuto() {
        val inicio = 1_000_000L
        assertEquals(MS_PARA_CERRAR_CON_BLOQUEO, msParaCerrar(inicio, inicio, 5, bloqueado = true))
        assertTrue(msParaCerrar(inicio, inicio + 61_000, 5, bloqueado = true) < 0L)
        // Aunque se haya pedido «nunca» o un plazo más largo.
        assertTrue(msParaCerrar(inicio, inicio + 61_000, 0, bloqueado = true) < 0L)
        // Con un plazo propio de un minuto, el mismo minuto.
        assertEquals(0L, msParaCerrar(inicio, inicio + 60_000, 1, bloqueado = true))
        // Desbloqueado, el de siempre.
        assertEquals(4 * minuto, msParaCerrar(inicio, inicio + minuto, 5, bloqueado = false))
    }

    @Test
    fun seCierraCuandoLaCamaraSeApagaPorInactividad() {
        // En pausa y sin superficie, después de haber tenido una: se apagó por inactividad.
        assertTrue(camaraApagadaPorInactividad(Fase.PAUSADO, haySuperficie = false, huboSuperficie = true))
        // En pausa con la cámara todavía enlazada: no.
        assertFalse(camaraApagadaPorInactividad(Fase.PAUSADO, haySuperficie = true, huboSuperficie = true))
        // Abriendo (todavía no hay superficie) o tras soltar lo enlazado al arrancar: no.
        assertFalse(camaraApagadaPorInactividad(Fase.ABRIENDO, haySuperficie = false, huboSuperficie = false))
        assertFalse(camaraApagadaPorInactividad(Fase.ABRIENDO, haySuperficie = false, huboSuperficie = true))
        assertFalse(camaraApagadaPorInactividad(Fase.PAUSADO, haySuperficie = false, huboSuperficie = false))
    }

    @Test
    fun elMosaicoDiceQuePasoYComoAgregarloAMano() {
        assertTrue(mensajeMosaico(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED).startsWith("Listo"))
        assertTrue(mensajeMosaico(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED).contains("ya estaba"))
        assertTrue(mensajeMosaico(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED).contains(COMO_AGREGAR_MOSAICO_A_MANO))
        val error = mensajeMosaico(StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND)
        assertTrue(error, error.contains(COMO_AGREGAR_MOSAICO_A_MANO))
    }
}
