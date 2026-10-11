package mx.papeleriapos.android.medicion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorDeSesionTest {
    @Test
    fun `el onDispose tardio de la pantalla vieja no apaga el monitor que tomo la nueva`() {
        val r = ReglasMonitor()
        val vieja = r.iniciar(1_000)
        // Atrás y reabrir rápido: la composición nueva lo toma (misma sesión) antes de que se vaya la vieja.
        val nueva = r.iniciar(1_000)
        assertFalse(r.detener(vieja))
        assertTrue(r.debeCorrer)
        assertTrue(r.detener(nueva))
        assertFalse(r.debeCorrer)
        // Detener dos veces o con un token que no es no hace nada.
        assertFalse(r.detener(nueva))
        assertFalse(r.detener(0))
    }

    @Test
    fun `mientras corre una prueba de estres se pausa y al terminar sigue`() {
        val r = ReglasMonitor()
        val t = r.iniciar(1_000)
        r.pausar()
        assertFalse("Un solo muestreador a la vez", r.debeCorrer)
        // Si la pantalla se recrea a media prueba, sigue en pausa.
        val otra = r.iniciar(1_000)
        assertFalse(r.debeCorrer)
        r.reanudar()
        assertTrue(r.debeCorrer)
        assertFalse(r.detener(t))
        assertTrue(r.detener(otra))
        // Un reanudar de más no deja la cuenta negativa.
        r.reanudar()
        r.iniciar(1_000)
        assertTrue(r.debeCorrer)
    }

    @Test
    fun `al terminar la sesion deja de correr aunque su duena siga y una sesion nueva vuelve a correr`() {
        val r = ReglasMonitor()
        r.iniciar(1_000)
        r.terminar()
        assertFalse(r.debeCorrer)
        r.iniciar(2_000)
        assertTrue(r.debeCorrer)
        assertEquals(2_000L, r.sesion)
    }
}
