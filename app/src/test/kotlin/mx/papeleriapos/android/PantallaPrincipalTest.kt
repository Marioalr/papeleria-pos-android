package mx.papeleriapos.android

import mx.papeleriapos.android.escanear.bordesFranja
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
        val (arriba, abajo) = bordesFranja(0.36f)
        assertEquals(0.32f, arriba, 1e-6f)
        assertEquals(0.68f, abajo, 1e-6f)
        assertEquals(0.2f to 0.8f, bordesFranja(0.6f).let { (a, b) -> Math.round(a * 100) / 100f to Math.round(b * 100) / 100f })
        // Fuera de rango se acota como en el escáner.
        assertEquals(0.45f, bordesFranja(0.01f).first, 1e-6f)
    }
}
