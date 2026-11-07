package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El nombre en pedazos y las familias de la lista (FamiliasTests del POS, con el mismo catálogo y los mismos casos). */
class FamiliasTest {
    /** Un catálogo inventado: las marcas (Pluvia, Trazo) se repiten al final de nombres de varios productos. */
    private val catalogo = listOf(
        "Lapicero 0.7mm Negro P/Fino Pluvia", "Lapicero 0.7mm Azul P/Fino Pluvia", "Lapicero 0.7mm Rojo P/Fino Pluvia",
        "Lapicero 0.7mm Colores Surt. Pluvia", "Lapicero 1.0mm Negro P/Mediano Pluvia", "Lapicero 1.0mm Rojo P/Medio Pluvia",
        "Lapicero 0.5mm Negro P/Fino Pluvia", "Lapicero 0.7mm Negro P/Fino Trazo", "Lapicero 1.0 Mm Negro Trazo",
        "Plumon Permanente Negro Pluvia", "Plumon Permanente Rojo Trazo", "Jgo Lapiceros C/10 Pluvia", "Jgo Lapiceros C/4 Pluvia",
        "Foami Carta Azul Rey", "Foami Carta Azul", "Foami Oficio Azul",
        "Cuaderno Prof 100h Raya Escriba", "Cuaderno Prof 100h Cuadro Grande Escriba", "Cuaderno Prof 100h Blanco Escriba",
        "Libreta Forma Italiana Raya Escriba",
        "Bolsa Regalo Grande Ocasion", "Bolsa Regalo Chica Ocasion", "Bolsa Regalo Mediana Ocasion",
    )

    private val lector = LectorDeNombres(catalogo)

    private data class Producto(val nombre: String, val vendidos: Int)

    private fun agrupar(preferido: EjeFamilia?, vararg productos: Producto): AgrupacionDeFamilias<Producto> =
        Familias.agrupar(productos.toList(), { lector.leer(it.nombre) }, { it.vendidos }, preferido)

    @Test
    fun `el nombre se lee en pedazos y lo que acompana a los milimetros es de la medida`() {
        val partes = lector.leer("Lapicero 0.7mm Negro P/Fino Pluvia")

        assertEquals(
            listOf(
                ParteDeNombre("Lapicero", null, "LAPICERO"),
                ParteDeNombre("0.7mm", EjeFamilia.MEDIDA, "0.7MM"),
                ParteDeNombre("Negro", EjeFamilia.COLOR, "NEGRO"),
                ParteDeNombre("P/Fino", EjeFamilia.MEDIDA, ""),
                ParteDeNombre("Pluvia", EjeFamilia.MARCA, "PLUVIA"),
            ),
            partes,
        )
    }

    @Test
    fun `la medida se compara igual aunque se escriba distinto`() {
        for ((nombre, clave) in listOf(
            "Lapicero 1.0 Mm Negro Trazo" to "1MM",
            "Lapicero 1mm Negro Trazo" to "1MM",
            "Lapicero 1.0 Negro Trazo" to "1MM",
            "Pintura 250 Ml Negra Trazo" to "250ML",
            "Bola Unicel No. 9" to "#9",
            "Bola Unicel #9" to "#9",
            "Papel Cascaron 1/8" to "1/8",
            "Foami 45x60 Azul" to "45X60",
            "Foami Carta Azul" to "CARTA",
        )) {
            assertEquals(nombre, clave, Familias.clave(lector.leer(nombre), EjeFamilia.MEDIDA))
        }
    }

    @Test
    fun `el color se lee con su tono y el surtido es un color mas`() {
        for ((nombre, clave, texto) in listOf(
            Triple("Lapicero 0.7mm Colores Surt. Pluvia", "SURTIDO", "Colores Surt."),
            Triple("Lapicero 1.0mm V/Colores Pluvia", "SURTIDO", "V/Colores"),
            Triple("Foami Carta Azul Rey", "AZUL REY", "Azul Rey"),
            Triple("Pintura 250 Ml Negra Trazo", "NEGRO", "Negra"),
            Triple("Liston Palo De Rosa", "PALO DE ROSA", "Palo De Rosa"),
        )) {
            val partes = lector.leer(nombre)

            assertEquals(nombre, clave to texto, Familias.clave(partes, EjeFamilia.COLOR) to Familias.valor(partes, EjeFamilia.COLOR))
        }
    }

    @Test
    fun `la marca se aprende de lo que se repite al final de varios productos`() {
        assertEquals("PLUVIA", Familias.clave(lector.leer("Goma Blanca Pluvia"), EjeFamilia.MARCA))
        // «Ocasion» se repite al final, pero solo en bolsas: no es marca.
        assertEquals("", Familias.clave(lector.leer("Bolsa Regalo Grande Ocasion"), EjeFamilia.MARCA))
        // Y la dada de alta vale aunque no se repita.
        assertEquals("ESCRIBA", Familias.clave(LectorDeNombres(emptyList(), listOf("Escriba")).leer("Cuaderno Raya Escriba"), EjeFamilia.MARCA))
    }

    @Test
    fun `la marca es lo ultimo del nombre aunque despues venga el color`() {
        val conTintaViva = LectorDeNombres(catalogo, listOf("Tinta Viva"))

        for ((nombre, marca) in listOf(
            "Pluma Punto Fino Tinta Viva Negro" to "TINTA VIVA",
            "Plumon Permanente Trazo Rojo" to "TRAZO",
            "Lapicero 1.0mm V/Colores Trazo Tinta Viva" to "TRAZO TINTA VIVA",
        )) {
            assertEquals(nombre, marca, Familias.clave(conTintaViva.leer(nombre), EjeFamilia.MARCA))
        }
    }

    @Test
    fun `lo que solo cambia en el color va en un renglon y Enter agrega el mas vendido`() {
        val agrupacion = agrupar(
            null,
            Producto("Lapicero 0.7mm Azul P/Fino Pluvia", 700),
            Producto("Lapicero 0.7mm Colores Surt. Pluvia", 900),
            Producto("Lapicero 0.7mm Negro P/Fino Pluvia", 1600),
            Producto("Lapicero 0.7mm Rojo P/Fino Pluvia", 500),
            Producto("Lapicero 4 Puntas Trazo", 10),
        )

        assertEquals(EjeFamilia.COLOR, agrupacion.eje)
        assertEquals(2, agrupacion.renglones.size)
        val familia = agrupacion.renglones[0]
        assertEquals("Lapicero 0.7mm P/Fino Pluvia", familia.titulo)
        // Del más vendido al menos, con el surtido al final aunque se venda más.
        assertEquals(listOf("Negro", "Azul", "Rojo", "Colores Surt."), familia.valores.map { it.texto })
        assertEquals("Lapicero 0.7mm Negro P/Fino Pluvia", familia.elegido.nombre)
        assertFalse(agrupacion.renglones[1].esFamilia)
    }

    @Test
    fun `si el color ya no cambia se despliega la medida y se puede elegir la marca`() {
        val negros = arrayOf(
            Producto("Lapicero 0.7mm Negro P/Fino Pluvia", 1600), Producto("Lapicero 1.0mm Negro P/Mediano Pluvia", 1000),
            Producto("Lapicero 0.5mm Negro P/Fino Pluvia", 500), Producto("Lapicero 0.7mm Negro P/Fino Trazo", 40),
            Producto("Lapicero 1.0 Mm Negro Trazo", 30),
        )

        val porMedida = agrupar(null, *negros)
        val porMarca = agrupar(EjeFamilia.MARCA, *negros)

        assertEquals(listOf(EjeFamilia.MEDIDA, EjeFamilia.MARCA), porMedida.ejes)
        assertEquals(EjeFamilia.MEDIDA, porMedida.eje)
        assertEquals("Lapicero Negro Pluvia", porMedida.renglones[0].titulo)
        // Las medidas, de menor a mayor; Enter, la más vendida.
        assertEquals(listOf("0.5mm P/Fino", "0.7mm P/Fino", "1.0mm P/Mediano"), porMedida.renglones[0].valores.map { it.texto })
        assertEquals("0.7mm P/Fino", porMedida.renglones[0].valores[porMedida.renglones[0].masVendido].texto)
        assertEquals(EjeFamilia.MARCA, porMarca.eje)
        assertTrue(porMarca.renglones.any { r -> r.esFamilia && r.valores.map { it.texto } == listOf("Pluvia", "Trazo") })
    }

    @Test
    fun `lo que se escribe distinto junto a los milimetros no separa la familia`() {
        val agrupacion = agrupar(null, Producto("Lapicero 1.0mm Negro P/Mediano Pluvia", 10), Producto("Lapicero 1.0mm Rojo P/Medio Pluvia", 5))

        assertEquals(1, agrupacion.renglones.size)
        assertEquals(listOf("Negro", "Rojo"), agrupacion.renglones[0].valores.map { it.texto })
    }

    @Test
    fun `los juegos no se juntan con la pieza y entre juegos cambia el contenido`() {
        val agrupacion = agrupar(
            null,
            Producto("Jgo Lapiceros C/10 Pluvia", 20), Producto("Jgo Lapiceros C/4 Pluvia", 30), Producto("Lapicero 0.7mm Negro P/Fino Pluvia", 1600),
        )

        assertEquals(EjeFamilia.CONTENIDO, agrupacion.eje)
        assertEquals(listOf("C/4", "C/10"), agrupacion.renglones.single { it.esFamilia }.valores.map { it.texto })
        assertEquals(2, agrupacion.renglones.size)
    }

    @Test
    fun `en cuadernos el rayado va primero y blanco es sin rayas`() {
        val agrupacion = agrupar(
            null,
            Producto("Cuaderno Prof 100h Raya Escriba", 50), Producto("Cuaderno Prof 100h Cuadro Grande Escriba", 40),
            Producto("Cuaderno Prof 100h Blanco Escriba", 10),
        )

        assertEquals(EjeFamilia.RAYADO, agrupacion.eje)
        assertEquals("", Familias.clave(lector.leer("Cuaderno Prof 100h Blanco Escriba"), EjeFamilia.COLOR))
        assertEquals(3, agrupacion.renglones.single().valores.size)
    }

    @Test
    fun `sin nada que juntar cada producto va solo en el orden de la busqueda`() {
        val agrupacion = agrupar(null, Producto("Lapicero 4 Puntas Trazo", 5), Producto("Goma Blanca Pluvia", 80))

        assertNull(agrupacion.eje)
        assertTrue(agrupacion.ejes.isEmpty())
        assertEquals(listOf("Lapicero 4 Puntas Trazo", "Goma Blanca Pluvia"), agrupacion.renglones.map { it.elegido.nombre })
    }
}
