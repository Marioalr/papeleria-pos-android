package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los sinónimos de la tienda (SinonimosApiTests del POS): los da de alta quien edita el catálogo, y la búsqueda los usa en
 * línea y sin conexión. Aquí, lo de la búsqueda con la base de prueba ([BaseDePrueba]): cómo queda cada palabra y lo que se
 * encuentra con ellos y sin ellos. Quién puede darlos de alta lo decide el POS.
 */
class SinonimosApiTest {
    private fun buscar(indice: IndiceDeBusqueda, texto: String): List<Encontrado> = indice.buscar(texto, 30, null, BaseDePrueba::vendidos)

    @Test
    fun `los da de alta quien edita el catalogo y la busqueda los usa`() {
        // La base de prueba tiene un «Bolígrafo multicolor», pero ninguno de punto fino.
        assertTrue(buscar(BaseDePrueba.armar(), "boligrafo punto fino").isEmpty())

        // Cada lado es una sola palabra: «papel china» no se puede.
        assertNull(Sinonimo.normalizar("papel china"))
        // Sin acentos, en mayúsculas y también al revés.
        val palabra = Sinonimo.normalizar(" Bolígrafo ")!!
        val equivale = Sinonimo.normalizar("pluma")!!
        val lista = listOf(Sinonimo(palabra, equivale), Sinonimo(equivale, palabra))
        assertEquals(listOf(Sinonimo("BOLIGRAFO", "PLUMA"), Sinonimo("PLUMA", "BOLIGRAFO")), lista)
        val conSinonimos = BaseDePrueba.armar(sinonimos = lista)

        // «boligrafo» encuentra las plumas, y dice por qué; desde 4 letras también vale el principio.
        val plumas = buscar(conSinonimos, "boligrafo punto fino")
        assertTrue(plumas.isNotEmpty())
        assertTrue(plumas.all { it.articulo.descripcion.startsWith("Pluma punto fino") })
        assertTrue(plumas.all { it.porSinonimo == "boligrafo" })
        assertTrue(buscar(conSinonimos, "bolig gel").isNotEmpty())
        assertTrue(buscar(conSinonimos, "bol gel").isEmpty())
        // Lo que ya se llama así no lleva sinónimo.
        assertTrue(buscar(conSinonimos, "pluma punto fino").all { it.porSinonimo == null })

        // Quitarlos: ya no encuentra nada.
        assertTrue(buscar(BaseDePrueba.armar(sinonimos = emptyList()), "boligrafo punto fino").isEmpty())
    }

    @Test
    fun `sin conexion la caja busca con los sinonimos de su copia`() {
        // «rotulador» = «plumon», sin el al revés.
        val copia = BaseDePrueba.armar(sinonimos = listOf(Sinonimo(Sinonimo.normalizar("rotulador")!!, Sinonimo.normalizar("plumon")!!)))

        val plumones = buscar(copia, "rotulador")

        assertTrue(plumones.isNotEmpty())
        assertTrue(plumones.all { it.articulo.descripcion.startsWith("Plum", ignoreCase = true) })
        assertTrue(plumones.all { it.porSinonimo == "rotulador" })
    }
}
