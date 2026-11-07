package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La copia puesta al día con lo que cambió, del lado de la búsqueda (CopiaAlDiaTests, CopiaPuestaAlDiaTests y
 * LoElegidoEnLaCopiaTests del POS): lo renombrado y un sinónimo nuevo se encuentran en cuanto llegan, la copia puesta al día
 * busca igual que la completa y lo elegido viaja con su clave. Cómo llegan los cambios (el aviso, la red, la base de la
 * caja) es del cliente de la API; aquí, lo que la búsqueda tiene que dar con ellos.
 */
class CopiaAlDiaTest {
    private fun buscar(indice: IndiceDeBusqueda, texto: String, maximo: Int = 30): List<Encontrado> =
        indice.buscar(texto, maximo, null, BaseDePrueba::vendidos)

    @Test
    fun `lo renombrado y un sinonimo nuevo se encuentran al llegar el aviso`() {
        val antes = BaseDePrueba.armar()
        assertTrue(buscar(antes, "zanfona").isEmpty())
        assertTrue(buscar(antes, "ocarina").isEmpty())

        // En la caja principal: el producto se renombra y la tienda da de alta un sinónimo para el nombre nuevo.
        val producto = BaseDePrueba.productos[BaseDePruebaDatos.RENOMBRADO]
        val renombrados = BaseDePrueba.productos.map { if (it.lugar == producto.lugar) it.conNombre(BaseDePruebaDatos.NOMBRE_RENOMBRADO) else it }
        val alDia = BaseDePrueba.armar(renombrados, listOf(Sinonimo(Sinonimo.normalizar("ocarina")!!, Sinonimo.normalizar("zanfona")!!)))

        val porNombre = buscar(alDia, "zanfona")
        val porSinonimo = buscar(alDia, "ocarina")
        assertEquals(producto.varianteId, porNombre[0].articulo.varianteId)
        assertTrue("Zanfona" in porNombre[0].articulo.descripcion)
        assertEquals("ocarina", porSinonimo.single { it.articulo.varianteId == producto.varianteId }.porSinonimo)
        // Como lo busca la caja principal.
        for (texto in listOf("zanfona", "ocarina")) {
            assertEquals(texto, BaseDePrueba.esperado("zanfona", texto).encontrados.take(30), BaseDePrueba.lugares(buscar(alDia, texto)))
        }
    }

    /**
     * La puesta al día deja al final lo que cambió (con todos sus códigos otra vez), y las marcas y los sinónimos llegan en
     * otro orden y repetidos: busca igual que la completa y que la caja principal, con los textos de la prueba del POS que
     * no dependen de lo que ella cambia en la base.
     */
    @Test
    fun `la copia puesta al dia busca igual que la completa`() {
        val productos = BaseDePrueba.productos
        val puestos = productos.filterIndexed { i, _ -> i % 11 != 0 } + productos.filterIndexed { i, _ -> i % 11 == 0 }
        val sinonimos = listOf(Sinonimo("BOLIGRAFO", "PLUMA"), Sinonimo("PLUMA", "BOLIGRAFO"))
        fun puestaAlDia(conSinonimos: List<Sinonimo>) = IndiceDeBusqueda.armar(
            puestos.map { ProductoParaBuscar(it.varianteId, it.descripcion, it.codigo, alReves(it.codigos) + it.codigos) },
            alReves(BaseDePrueba.marcas) + BaseDePrueba.marcas,
            alReves(conSinonimos) + conSinonimos,
        )
        val (completa, alDia) = BaseDePrueba.juego("base") to puestaAlDia(emptyList())
        val (completaConSinonimos, alDiaConSinonimos) = BaseDePrueba.juego("boligrafo") to puestaAlDia(sinonimos)
        val textos = listOf(
            "zanfona", "ocarina", "lapicero", "pluma", "pluma gel", "zz-caso", "ZZ-CASO-01", "cua pro", "folder", "lap az", "sobre manial",
        ) + productos.filterIndexed { i, _ -> i % 25 == 0 }.map { it.descripcion.split(' ').take(2).joinToString(" ") }

        for (texto in textos.distinct()) {
            val deLaPuestaAlDia = buscar(alDia, texto, 200)
            assertEquals("«$texto»: la completa y la puesta al día buscan distinto.", vistos(buscar(completa, texto, 200)), vistos(deLaPuestaAlDia))
            val enLaPrincipal = BaseDePrueba.esperado("base", texto)
            assertEquals(
                "«$texto»: la caja principal y la puesta al día buscan distinto.",
                enLaPrincipal.encontrados to enLaPrincipal.encontrados.map { enLaPrincipal.parecido },
                vistos(deLaPuestaAlDia),
            )
            assertEquals("«$texto», con sinónimos", vistos(buscar(completaConSinonimos, texto, 200)), vistos(buscar(alDiaConSinonimos, texto, 200)))
        }
    }

    /** Lo elegido viaja con la copia (LoElegidoEnLaCopiaTests): con la clave con que lo guarda el servicio, y lo nuevo pisa. */
    @Test
    fun `lo elegido viaja con la clave de lo escrito`() {
        assertEquals("TEXTO NUEVO", IndiceDeBusqueda.claveGuardada("Texto Nuevo"))
        val elegidos = HashMap<String, String>()
        elegidos[IndiceDeBusqueda.claveGuardada("Texto Que Cambia")] = BaseDePrueba.productos[7].varianteId
        elegidos[IndiceDeBusqueda.claveGuardada("texto que cambia")] = BaseDePrueba.productos[8].varianteId
        assertEquals(mapOf("TEXTO QUE CAMBIA" to BaseDePrueba.productos[8].varianteId), elegidos)
    }

    /** Lo que encontró, con el sinónimo de cada uno y si es parecido. */
    private fun vistos(encontrados: List<Encontrado>) = BaseDePrueba.lugares(encontrados) to encontrados.map { it.parecido }

    private fun <T> alReves(lista: List<T>): List<T> = List(lista.size) { lista[lista.size - 1 - it] }
}
