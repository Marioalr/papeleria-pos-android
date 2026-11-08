package mx.papeleriapos.android.entrada

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Cuándo se pide la huella: al abrir y al volver después de un rato en el fondo. */
@RunWith(AndroidJUnit4::class)
class EntradaTest {
    @Before
    fun limpiar() = Entrada.reiniciar()

    @Test
    fun `se pide al abrir y despues de diez minutos en el fondo`() {
        assertTrue("Nunca se ha entrado en este proceso", Entrada.hayQuePedir(ahora = 5_000))
        Entrada.desbloquear()
        assertFalse(Entrada.hayQuePedir(ahora = 6_000))
        Entrada.alFondo(ahora = 10_000)
        assertFalse("Un rato corto en el fondo no pide", Entrada.hayQuePedir(ahora = 10_000 + Entrada.MS_SIN_PEDIR - 1))
        assertTrue(Entrada.hayQuePedir(ahora = 10_000 + Entrada.MS_SIN_PEDIR))
        assertFalse(Entrada.bloqueada.value)
    }
}
