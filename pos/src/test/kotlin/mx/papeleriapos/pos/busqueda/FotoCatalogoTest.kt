package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La búsqueda por nombre de la copia de la caja (FotoCatalogoTests del POS, «La_copia_busca_por_nombre_si_trae_lo_mas_vendido»),
 * con los mismos dos productos y sus códigos: con lo más vendido, como el servicio; un código repetido se encuentra en los dos
 * productos. Si la copia trae lo más vendido (y entonces se busca en ella) lo decide la copia del celular, no la búsqueda.
 */
class FotoCatalogoTest {
    // Sus Id van por fecha: el lapicero se dio de alta antes que el cuaderno.
    private val lapicero = ProductoParaBuscar(BaseDePrueba.id(1), "Lapicero azul", "750100", listOf("750100", "7501000000012", "LAP-AZ", "750200"))
    private val cuaderno = ProductoParaBuscar(BaseDePrueba.id(2), "Cuaderno raya", "750200", listOf("750200"))

    @Test
    fun `la copia busca por nombre si trae lo mas vendido`() {
        val vendidos = mapOf(cuaderno.varianteId to 3)
        val copia = IndiceDeBusqueda.armar(listOf(lapicero, cuaderno), emptyList(), emptyList())
        fun buscar(texto: String) = copia.buscar(texto, 30, null) { vendidos[it] ?: 0 }

        assertEquals(listOf("Cuaderno raya"), buscar("raya").map { it.articulo.descripcion })
        // El código exacto primero, y el otro producto que también lo tiene.
        assertEquals(listOf("Cuaderno raya", "Lapicero azul"), buscar("750200").map { it.articulo.descripcion })
        assertEquals(3, vendidos[buscar("cuaderno")[0].articulo.varianteId])
    }
}
