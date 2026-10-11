package mx.papeleriapos.android

import mx.papeleriapos.escaner.camara.franjaPara
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantallaPrincipalTest {
    @Test
    fun `enlaza en onCreate al arrancar y al restaurar si la pestana guardada era la del escaner`() {
        assertTrue("Arranque nuevo", enlazarAlCrear(restaurando = false, escanerVisible = null))
        // Android mató el proceso en segundo plano y el empleado toca el ícono: se restaura con estado guardado.
        assertTrue(enlazarAlCrear(restaurando = true, escanerVisible = true))
        assertFalse("Se veía otra pestaña", enlazarAlCrear(restaurando = true, escanerVisible = false))
        assertFalse("Estado de una versión que no lo guardaba", enlazarAlCrear(restaurando = true, escanerVisible = null))
    }

    @Test
    fun `la franja va del 32 al 68 por ciento con el alto por omision y sigue al ajuste`() {
        // Lo de encima del visor (barra de la sesión y panel) se acomoda con la misma franja que dibuja el escáner.
        val f = franjaPara(0.36f)
        assertEquals(0.32f, f.arriba, 1e-6f)
        assertEquals(0.68f, f.abajo, 1e-6f)
        assertEquals(0.2f to 0.8f, franjaPara(0.6f).let { Math.round(it.arriba * 100) / 100f to Math.round(it.abajo * 100) / 100f })
        // Fuera de rango se acota.
        assertEquals(0.45f, franjaPara(0.01f).arriba, 1e-6f)
    }
}
