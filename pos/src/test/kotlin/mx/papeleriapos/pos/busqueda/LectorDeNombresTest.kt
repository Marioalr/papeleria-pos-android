package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lo que lee el POS de nombres raros (medidas con decimales y fracciones, números con «No.», colores con tono, rayados,
 * contenidos, acentos sueltos, marcas de dos palabras…), con las marcas de la base de prueba, tal como lo dio el oráculo
 * (pos/herramientas/oraculo). No viene de una prueba del POS: completa las de FamiliasTest con casos de las reglas.
 */
class LectorDeNombresTest {
    private val lector = LectorDeNombres(BaseDePrueba.productos.map { it.descripcion }, BaseDePrueba.marcas)

    @Test
    fun `lee los nombres raros como el POS`() {
        val renglones = BaseDePrueba.renglones(BaseDePruebaDatos.LECTOR)
        assertEquals(105, renglones.size)
        for (renglon in renglones) {
            val (nombre, partes, medidas) = renglon.split('|').map { BaseDePrueba.desescapar(it) }
            val leidas = lector.leer(nombre)
            assertEquals("«$nombre»", partes, BaseDePrueba.partes(leidas))
            val enOrden = leidas.filter { it.eje == EjeFamilia.MEDIDA && it.clave.isNotEmpty() }.map { LectorDeNombres.ordenDeMedida(it.clave) }
            val esperadas = if (medidas.isEmpty()) emptyList() else medidas.split(',').map { it.toDouble() }
            assertEquals("«$nombre»: el orden de sus medidas", esperadas, enOrden)
        }
    }

    /** Cada producto de la base de prueba, en la búsqueda, con su nombre en pedazos como en el POS (por su huella). */
    @Test
    fun `cada producto de la base de prueba se lee como en el POS`() {
        val busqueda = BaseDePrueba.juego("base")
        for (p in BaseDePrueba.productos) {
            val partes = BaseDePrueba.partes(busqueda.porVariante(p.varianteId)!!.partes)
            assertEquals("«${p.descripcion}»: $partes", p.huellaDePartes, BaseDePrueba.huella(partes))
        }
    }

    /**
     * Donde el POS truena (un emoji, medio emoji o dígitos de otras escrituras en el nombre: su `Normalize` y su `decimal.Parse`
     * no los aceptan), aquí se lee y se busca: los dígitos valen por su número y lo demás pasa tal cual. Y el cero sin
     * decimales («0.000» con `stripTrailingZeros` en Android) es «0».
     */
    @Test
    fun `con lo que el POS no sabe leer no truena`() {
        val nombres = listOf(
            "Taza \ud83d\ude00 Roja", "Regla \u0663\u0660cm Azul", "Regla \ud835\udfcf\ud835\udfcemm", "Hoja \ud83d suelta", "Lapicero 0 mm",
            "Lapicero 0.0004mm",
        )
        val lector = LectorDeNombres(nombres)

        assertEquals("30CM", Familias.clave(lector.leer(nombres[1]), EjeFamilia.MEDIDA))
        assertEquals("10MM", Familias.clave(lector.leer(nombres[2]), EjeFamilia.MEDIDA))
        assertEquals("0MM", Familias.clave(lector.leer(nombres[4]), EjeFamilia.MEDIDA))
        assertEquals("0MM", Familias.clave(lector.leer(nombres[5]), EjeFamilia.MEDIDA))
        val busqueda = IndiceDeBusqueda.armar(nombres.mapIndexed { i, n -> ProductoParaBuscar(BaseDePrueba.id(i), n, null, emptyList()) }, null, null)
        assertEquals(listOf(nombres[0]), busqueda.buscar("taza \ud83d\ude00").map { it.articulo.descripcion })
        assertEquals(listOf(nombres[1]), busqueda.buscar("regla 30cm").map { it.articulo.descripcion })
        assertEquals(listOf(nombres[3]), busqueda.buscar("hoja").map { it.articulo.descripcion })
    }

    @Test
    fun `las medidas se ordenan por su numero o por su tamano`() {
        assertEquals(0.125, LectorDeNombres.ordenDeMedida("1/8"), 0.0)
        assertEquals(45.0, LectorDeNombres.ordenDeMedida("45X60"), 0.0)
        assertEquals(11.0, LectorDeNombres.ordenDeMedida("CARTA"), 0.0)
        assertEquals(13.0, LectorDeNombres.ordenDeMedida("DOBLE CARTA"), 0.0)
        assertEquals(Double.MAX_VALUE, LectorDeNombres.ordenDeMedida("XL"), 0.0)
    }
}
