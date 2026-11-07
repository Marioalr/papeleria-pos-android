package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El orden por nombre de la búsqueda, el de .NET con es-MX sin importar mayúsculas (`CompareInfo.GetSortKey`), con lo que dio
 * el oráculo (pos/herramientas/oraculo): nombres raros y los de la base de prueba. No viene de una prueba del POS: es lo que
 * decide el empate en la lista, y el `Collator` de Java y el de Android ordenan distinto (y el de Android, según su versión).
 */
class OrdenEspanolTest {
    private fun comparar(a: String, b: String): Int = OrdenEspanol.comparar(OrdenEspanol.llave(a), OrdenEspanol.llave(b))

    @Test
    fun `los nombres raros van en el orden del POS`() {
        val casos = BaseDePrueba.renglones(BaseDePruebaDatos.CASOS_DE_ORDEN).map { it[0] to BaseDePrueba.desescapar(it.substring(1)) }
        assertTrue(casos.size > 200)
        for (k in 1 until casos.size) {
            val (antes, a) = casos[k - 1]
            val (como, b) = casos[k]
            val c = comparar(a, b)
            if (como == '=') assertEquals("«$a» y «$b» empatan", 0, c) else assertTrue("«$a» va antes que «$b»", c < 0)
            assertTrue(antes == '<' || antes == '=')
        }
    }

    @Test
    fun `los productos de la base de prueba van en el orden del POS`() {
        val productos = BaseDePrueba.productos
        val orden = BaseDePruebaDatos.ORDEN_DEL_CATALOGO.flatMap { it.split(' ', '\n') }.filter { it.isNotEmpty() }
        assertEquals(productos.size, orden.size)
        for (k in 1 until orden.size) {
            val a = productos[orden[k - 1].substring(1).toInt()].descripcion
            val b = productos[orden[k].substring(1).toInt()].descripcion
            val c = comparar(a, b)
            if (orden[k][0] == '=') assertEquals("«$a» y «$b» empatan", 0, c) else assertTrue("«$a» va antes que «$b»", c < 0)
        }
    }

    /** Lo que se ve en la lista: los espacios y los signos van antes que las letras, la ñ después de la n, y los acentos al final. */
    @Test
    fun `papel lustre va antes que papeleria y la n antes que la ene`() {
        assertTrue(comparar("Papel lustre", "Papelería") < 0)
        assertTrue(comparar("Nanda", "Ñandú") < 0)
        assertTrue(comparar("Nz", "Ña") < 0)
        assertTrue(comparar("Lapiz", "Lápiz") < 0)
        assertTrue(comparar("Lápiz", "Lapiz azul") < 0)
        assertEquals(0, comparar("LAPIZ MIRADO 2", "Lapiz Mirado 2"))
    }
}
