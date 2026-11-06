package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La búsqueda de la pantalla de venta (BusquedaApiTests del POS, que va contra el servicio con su base de prueba): por
 * principios de palabra, lo elegido antes en esa caja y lo más vendido. Aquí, con la misma base de prueba ([BaseDePrueba])
 * y hasta 30, como contesta la API. De lo que guarda la base (lo elegido, lo que no se encontró) queda la clave con que se
 * guarda; quién puede dar de alta productos y cobrar lo decide el POS.
 */
class BusquedaApiTest {
    private val busqueda = BaseDePrueba.juego("base")

    private fun buscar(texto: String, elegido: String? = null, maximo: Int = 30): List<Encontrado> =
        busqueda.buscar(texto, maximo, elegido, BaseDePrueba::vendidos)

    @Test
    fun `cada pedazo es el principio de una palabra en cualquier orden`() {
        val resultados = buscar("gel plu")

        assertTrue(resultados.isNotEmpty())
        assertTrue(resultados.all { it.articulo.descripcion.startsWith("Pluma de gel") })
        // «unto» está dentro de «punto», pero no es el principio de ninguna palabra: no coincide exacto, solo como
        // parecido (le falta una letra).
        assertTrue(buscar("unto fino").all { it.parecido })
    }

    @Test
    fun `lo que eligio la caja con un texto sale primero ahi y solo ahi`() {
        // Lo que eligió cada caja con cada texto, con la clave con que lo guarda el servicio.
        val elegidos = HashMap<Pair<String, String>, String>()
        fun elegidoEn(caja: String?, texto: String): String? = caja?.let { elegidos[it to IndiceDeBusqueda.clave(texto)] }
        val antes = buscar("pluma punto fino", elegidoEn("Caja 1", "pluma punto fino"))
        val elegido = antes[antes.size - 1].articulo.varianteId

        elegidos["Caja 1" to IndiceDeBusqueda.claveGuardada(" Pluma  PUNTO fino")] = elegido

        assertEquals(elegido, buscar("pluma punto fino", elegidoEn("Caja 1", "pluma punto fino"))[0].articulo.varianteId)
        // Otra caja (o sin caja) sigue con el orden de siempre.
        assertEquals(antes[0].articulo.varianteId, buscar("pluma punto fino", elegidoEn("Caja 2", "pluma punto fino"))[0].articulo.varianteId)
        assertEquals(antes[0].articulo.varianteId, buscar("pluma punto fino", elegidoEn(null, "pluma punto fino"))[0].articulo.varianteId)
        // Se guarda sin acentos, mayúsculas ni espacios de más.
        assertEquals("PLUMA PUNTO FINO", IndiceDeBusqueda.claveGuardada(" Pluma  PUNTO fino"))
    }

    @Test
    fun `lo que no encontro nada se anota sin ensenar nada`() {
        assertTrue(buscar("plumero de avestruz").isEmpty())
        // Se anota con la misma clave que lo elegido, y sin nada elegido.
        assertEquals("PLUMERO DE AVESTRUZ", IndiceDeBusqueda.claveGuardada("plumero de avestruz"))
    }

    @Test
    fun `si el texto no distingue sale primero lo mas vendido`() {
        // Dos servicios dados de alta después de todo lo demás, sin código ni ventas.
        val conEngargolados = BaseDePrueba.juego("engargolado")
        val beta = BaseDePrueba.engargolados[1].varianteId
        // Sin ventas, por nombre: alfa antes que beta.
        assertEquals(
            listOf("Engargolado orden alfa", "Engargolado orden beta"),
            conEngargolados.buscar("engar orden", 30, null, BaseDePrueba::vendidos).map { it.articulo.descripcion },
        )

        // Se vende el beta una vez (lo vendido se cuenta una vez al día; aquí, como si fuera otro día).
        val vendidos: (String) -> Int = { if (it == beta) 1 else BaseDePrueba.vendidos(it) }
        val despues = conEngargolados.buscar("engar orden", 30, null, vendidos)

        assertEquals(listOf("Engargolado orden beta", "Engargolado orden alfa"), despues.map { it.articulo.descripcion })
        // Cuántas veces se vendió viaja con el resultado: en una familia, Enter agrega el más vendido.
        assertEquals(1 to 0, vendidos(despues[0].articulo.varianteId) to vendidos(despues[1].articulo.varianteId))
    }

    @Test
    fun `si nada coincide exacto salen los parecidos por un error de dedo`() {
        val parecidos = buscar("pluma pumto fino")
        val exactos = buscar("pluma punto fino")

        assertTrue(parecidos.isNotEmpty())
        assertTrue(parecidos.all { it.parecido })
        assertTrue(parecidos.all { "punto fino" in it.articulo.descripcion })
        // Primero los que coinciden exacto en más palabras: las plumas antes que los plumones.
        assertTrue(parecidos[0].articulo.descripcion.startsWith("Pluma punto fino"))
        assertTrue(exactos.none { it.parecido })
    }

    @Test
    fun `tambien se busca por lo que lee la caja del nombre`() {
        // «Plata» es como la caja lee «Plateado»: sale exacto, no como parecido.
        val plata = buscar("lustre plata")

        assertTrue(plata.isNotEmpty())
        assertTrue(plata.all { "Plateado" in it.articulo.descripcion })
        assertTrue(plata.none { it.parecido })
    }

    @Test
    fun `cada resultado trae su nombre en pedazos para juntar las familias`() {
        val todos = buscar("pluma punto fino", maximo = 200)
        val pocos = buscar("pluma punto fino", maximo = 3)

        assertEquals(3, pocos.size)
        assertTrue(todos.all { it.articulo.partes.isNotEmpty() })
        // Las plumas de la base de prueba cambian en el color y en la marca: se juntan por color.
        val familias = Familias.agrupar(todos, { it.articulo.partes }, { BaseDePrueba.vendidos(it.articulo.varianteId) })
        assertEquals(EjeFamilia.COLOR, familias.eje)
        assertTrue(EjeFamilia.MARCA in familias.ejes)
        assertTrue(familias.renglones.size < todos.size)
    }

    /** De ApiCajaTests del POS («Busca_sin_importar_acentos_ni_el_orden_de_las_palabras»), con la misma base de prueba. */
    @Test
    fun `busca sin importar acentos ni el orden de las palabras`() {
        val resultados = buscar("liston ROJO")

        assertTrue(resultados.isNotEmpty())
        assertTrue(resultados.all { "Listón" in it.articulo.descripcion })
    }
}
