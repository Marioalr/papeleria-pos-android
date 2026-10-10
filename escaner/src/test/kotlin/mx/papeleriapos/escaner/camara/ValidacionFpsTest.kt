package mx.papeleriapos.escaner.camara

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionFpsTest {
    @Test
    fun seValidaUnaVezPorProcesoYPreferenciaYSiSeCancelaSeRepite() {
        val fijo60 = PreferenciaFps(fijo = true, tope = 60)
        val variable60 = PreferenciaFps(fijo = false, tope = 60)
        val fijo30 = PreferenciaFps(fijo = true, tope = 30)
        ValidacionFps.olvidarTodas()
        // El primer enlace la toma; el segundo (otro controlador, o un reenlace) ya no.
        assertTrue(ValidacionFps.empezar(fijo60))
        assertTrue(ValidacionFps.hecha(fijo60))
        assertFalse(ValidacionFps.empezar(fijo60))
        // Otra preferencia es otra validación, también si solo cambia el tope.
        assertFalse(ValidacionFps.hecha(variable60))
        assertTrue(ValidacionFps.empezar(variable60))
        assertFalse(ValidacionFps.hecha(fijo30))
        // Cancelada antes de terminar: el siguiente enlace la vuelve a intentar.
        ValidacionFps.olvidar(fijo60)
        assertTrue(ValidacionFps.empezar(fijo60))
        ValidacionFps.olvidarTodas()
    }
}
