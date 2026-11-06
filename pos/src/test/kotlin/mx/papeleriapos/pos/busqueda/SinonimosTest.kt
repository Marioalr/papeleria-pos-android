package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los sinónimos de la tienda (SinonimosTests del POS, con los mismos casos). */
class SinonimosTest {
    @Test
    fun `cada lado es una sola palabra sin acentos`() {
        for ((escrito, normal) in listOf(
            " Bolígrafo " to "BOLIGRAFO",
            "FOAMY" to "FOAMY",
            "papel china" to null,
            "x" to null,
        )) {
            assertEquals(escrito, normal, Sinonimo.normalizar(escrito))
        }
    }

    @Test
    fun `vale la palabra y desde cuatro letras su principio`() {
        val sinonimos = SinonimosDeBusqueda(
            listOf(Sinonimo("BOLIGRAFO", "LAPICERO"), Sinonimo("BOLIGRAFO", "PLUMA"), Sinonimo("GOMA", "BORRADOR")),
        )

        assertEquals(listOf(Sinonimo("BOLIGRAFO", "LAPICERO"), Sinonimo("BOLIGRAFO", "PLUMA")), sinonimos.de("BOLIGRAFO"))
        assertEquals(2, sinonimos.de("BOLI").size)
        // Con menos de cuatro letras, solo la palabra entera: «bol» también es bolsa.
        assertTrue(sinonimos.de("BOL").isEmpty())
        assertEquals(listOf(Sinonimo("GOMA", "BORRADOR")), sinonimos.de("GOMA"))
        assertTrue(SinonimosDeBusqueda.NINGUNO.de("GOMA").isEmpty())
    }
}
