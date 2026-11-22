package mx.papeleriapos.android.principal

import org.junit.Assert.assertEquals
import org.junit.Test

/** El precio a mano del artículo común: lo que se teclea y los centavos que da. */
class HojaComunTest {

    private fun teclear(vararg teclas: String) = teclas.fold("") { t, k -> teclearPrecio(t, k) }

    @Test
    fun `pesos enteros, con centavos y solo centavos`() {
        assertEquals(1500L, centavosDe(teclear("1", "5")))
        assertEquals(250L, centavosDe(teclear("2", ".", "5")))
        assertEquals(255L, centavosDe(teclear("2", ".", "5", "5")))
        assertEquals(50L, centavosDe(teclear(".", "5")))
        assertEquals("0.5", teclear(".", "5"))
        assertEquals(0L, centavosDe(""))
        assertEquals(0L, centavosDe("0."))
    }

    @Test
    fun `un solo punto, dos decimales y cinco cifras de pesos`() {
        assertEquals("2.5", teclear("2", ".", ".", "5"))
        assertEquals("2.55", teclear("2", ".", "5", "5", "9"))
        assertEquals("12345", teclear("1", "2", "3", "4", "5", "6"))
        assertEquals("12345.6", teclear("1", "2", "3", "4", "5", "6", ".", "6"))
        assertEquals("Un cero a la izquierda no se acumula", "7", teclear("0", "7"))
    }
}
