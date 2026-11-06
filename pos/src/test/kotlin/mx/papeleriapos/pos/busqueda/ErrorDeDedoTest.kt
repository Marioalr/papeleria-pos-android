package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El error de dedo de la búsqueda (ErrorDeDedoTests del POS, con los mismos casos). */
class ErrorDeDedoTest {
    @Test
    fun `una letra de diferencia es un error de dedo`() {
        for ((escrito, palabra) in listOf(
            "MANIAL" to "MANILA", // dos volteadas
            "PUMTO" to "PUNTO", // una cambiada
            "LAPZ" to "LAPIZ", // una de menos
            "SOBRES" to "SOBRE", // una de más
            "CUADRENO" to "CUADERNO",
        )) {
            assertTrue("$escrito y $palabra", ErrorDeDedo.difierenEnUna(escrito, palabra))
        }
    }

    @Test
    fun `dos o mas letras ya no`() {
        for ((escrito, palabra) in listOf(
            "MANILA" to "MALINA", // dos cambiadas que no están juntas
            "PLUMA" to "PL",
            "ABCD" to "WXYZ",
        )) {
            assertFalse("$escrito y $palabra", ErrorDeDedo.difierenEnUna(escrito, palabra))
        }
    }

    @Test
    fun `se compara con el principio de cada palabra y solo con cuatro letras o mas`() {
        val palabras = listOf("SOBRE", "MANILA", "CARTA", "7501234567890")

        assertTrue(ErrorDeDedo.coincide("MANIAL", palabras))
        assertTrue(ErrorDeDedo.coincide("CRATA", palabras))
        // El principio, como siempre.
        assertTrue(ErrorDeDedo.coincide("MAN", palabras))
        // Con menos de cuatro letras sobran los parecidos, y los números no tienen errores de dedo.
        assertFalse(ErrorDeDedo.coincide("MNA", palabras))
        assertFalse(ErrorDeDedo.coincide("7502", palabras))
    }
}
