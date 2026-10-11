package mx.papeleriapos.escaner.camara

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionFpsTest {
    @Test
    fun seValidaUnaVezPorProcesoYPreferenciaYSiSeCancelaSeRepite() {
        ValidacionFps.olvidar(true)
        ValidacionFps.olvidar(false)
        // El primer enlace la toma; el segundo (otro controlador, o un reenlace) ya no.
        assertTrue(ValidacionFps.empezar(true))
        assertTrue(ValidacionFps.hecha(true))
        assertFalse(ValidacionFps.empezar(true))
        // La otra preferencia es otra validación.
        assertFalse(ValidacionFps.hecha(false))
        assertTrue(ValidacionFps.empezar(false))
        // Cancelada antes de terminar: el siguiente enlace la vuelve a intentar.
        ValidacionFps.olvidar(true)
        assertTrue(ValidacionFps.empezar(true))
        ValidacionFps.olvidar(true)
        ValidacionFps.olvidar(false)
    }
}
