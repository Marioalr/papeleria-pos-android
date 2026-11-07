package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lo que dice la clase [IndiceDeBusqueda], regla por regla, con un catálogo chico hecho a mano. No viene de una prueba del POS:
 * lo que esperan salió de correr la búsqueda del POS (pos/herramientas/oraculo) con este mismo catálogo. «Sin» no tiene marcas
 * ni sinónimos; «con» tiene la marca Trazo y los sinónimos «boligrafo» = «pluma», también al revés.
 */
class IndiceDeBusquedaTest {
    /** Un producto con su código, todos sus códigos y cuántas veces se vendió; su Id va por su lugar (por fecha). */
    private class Producto(val nombre: String, val codigo: String?, val codigos: List<String>, val vendidos: Int)

    private val catalogo = listOf(
        Producto("Papel lustre azul", "750100", listOf("750100"), 0),
        Producto("Papel lustre rojo", "750101", listOf("750101", "750101"), 0),
        Producto("Papel lustre plateado", "750102", listOf("750102"), 4),
        Producto("Papelería surtida", null, emptyList(), 0),
        Producto("Folder (azul) carta", "FOL-AZ", listOf("FOL-AZ"), 2),
        Producto("Broca 3/4 acero", "BRO-3/4", listOf("BRO-3/4"), 0),
        Producto("Libro de cuentos", "LIB-01", listOf("LIB-01", "7501234567890"), 0),
        Producto("Lapicero 1.0 Mm Negro Trazo", "P000001", listOf("P000001"), 7),
        Producto("Lapicero 0.7mm Negra Trazo", "P000002", listOf("P000002"), 3),
        Producto("Pluma V/Colores Trazo", "P000003", listOf("P000003"), 0),
        Producto("Pluma Colores Surt. Trazo", null, emptyList(), 1),
        Producto("Cuaderno Prof 100h Raya Escriba", "7501234567890", listOf("7501234567890"), 0),
        Producto("Sobre manila carta", null, emptyList(), 5),
        Producto("Sobre manila oficio", null, emptyList(), 5),
        Producto("Plumón permanente negro", null, emptyList(), 0),
        Producto("Pluma punto fino negro", null, emptyList(), 0),
        Producto("Ñandú de peluche", null, emptyList(), 0),
        Producto("Nanda gris", null, emptyList(), 0),
        Producto("Lápiz adhesivo", null, emptyList(), 0),
        Producto("Lapiz Mirado 2", null, emptyList(), 0),
        Producto("LAPIZ MIRADO 2", null, emptyList(), 0),
        Producto("Bolígrafo multicolor", null, emptyList(), 0),
    )

    private val productos = catalogo.mapIndexed { i, p -> ProductoParaBuscar(BaseDePrueba.id(i), p.nombre, p.codigo, p.codigos) }
    private val vendidos = catalogo.mapIndexed { i, p -> BaseDePrueba.id(i) to p.vendidos }.toMap()
    private val sin = IndiceDeBusqueda.armar(productos, emptyList(), null)
    private val con = IndiceDeBusqueda.armar(productos, listOf("Trazo"), listOf(Sinonimo("BOLIGRAFO", "PLUMA"), Sinonimo("PLUMA", "BOLIGRAFO")))

    /** Lo que encontró: el nombre de cada uno (y el sinónimo por el que salió), y si fue por un error de dedo. */
    private fun encontrados(indice: IndiceDeBusqueda, texto: String, maximo: Int, elegido: Int, conVendidos: Boolean): Pair<List<String>, Boolean> {
        val porVendidos: ((String) -> Int)? = if (conVendidos) { id -> vendidos[id] ?: 0 } else null
        val encontrados = indice.buscar(texto, maximo, if (elegido >= 0) BaseDePrueba.id(elegido) else null, porVendidos)
        return encontrados.map { e -> e.articulo.descripcion + (e.porSinonimo?.let { " (por «$it»)" } ?: "") } to encontrados.any { it.parecido }
    }

    private fun buscaSin(texto: String, esperado: List<String>, maximo: Int = 30, elegido: Int = -1, conVendidos: Boolean = true, parecido: Boolean) =
        assertEquals("sin «$texto»", esperado to parecido, encontrados(sin, texto, maximo, elegido, conVendidos))

    private fun buscaCon(texto: String, esperado: List<String>, maximo: Int = 30, elegido: Int = -1, conVendidos: Boolean = true, parecido: Boolean) =
        assertEquals("con «$texto»", esperado to parecido, encontrados(con, texto, maximo, elegido, conVendidos))

    /** Lo mismo con la marca y los sinónimos que sin ellos. */
    private fun busca(texto: String, esperado: List<String>, parecido: Boolean) {
        buscaSin(texto, esperado, parecido = parecido)
        buscaCon(texto, esperado, parecido = parecido)
    }

    /** «lus az» es el papel lustre azul, y «lu» no son las plumas, que lo traen en medio. Sin acentos ni mayúsculas; la ñ es otra letra. */
    @Test
    fun `cada pedazo es el principio de una palabra en cualquier orden y sin acentos`() {
        busca("lus az", listOf("Papel lustre azul"), parecido = false)
        busca("az lus", listOf("Papel lustre azul"), parecido = false)
        busca(
            "papel",
            listOf("Papel lustre plateado", "Papel lustre azul", "Papel lustre rojo", "Papelería surtida"),
            parecido = false,
        )
        busca(
            "PAPEL",
            listOf("Papel lustre plateado", "Papel lustre azul", "Papel lustre rojo", "Papelería surtida"),
            parecido = false,
        )
        busca("papel lus", listOf("Papel lustre plateado", "Papel lustre azul", "Papel lustre rojo"), parecido = false)
        busca("lu", listOf("Papel lustre plateado", "Papel lustre azul", "Papel lustre rojo"), parecido = false)
        busca("lapiz", listOf("Lápiz adhesivo", "Lapiz Mirado 2", "LAPIZ MIRADO 2"), parecido = false)
        busca("lápiz", listOf("Lápiz adhesivo", "Lapiz Mirado 2", "LAPIZ MIRADO 2"), parecido = false)
        busca("LAPIZ MIRADO", listOf("Lapiz Mirado 2", "LAPIZ MIRADO 2"), parecido = false)
        busca("ñandu", listOf("Ñandú de peluche"), parecido = false)
        busca("x", emptyList(), parecido = false)
        busca("zzz", emptyList(), parecido = false)
        busca("", emptyList(), parecido = false)
        busca("   ", emptyList(), parecido = false)
    }

    /** «(azul)» y «azul»; «3/4» y «BRO-3/4»; un código se encuentra por su principio. */
    @Test
    fun `las palabras de los codigos van tal cual y partidas en lo que no es letra ni numero`() {
        busca("azul", listOf("Folder (azul) carta", "Papel lustre azul"), parecido = false)
        busca("(azul)", listOf("Folder (azul) carta"), parecido = false)
        busca("3/4", listOf("Broca 3/4 acero"), parecido = false)
        busca("bro-3", listOf("Broca 3/4 acero"), parecido = false)
        busca("lib-01", listOf("Libro de cuentos"), parecido = false)
        busca("LIB", listOf("Libro de cuentos"), parecido = false)
        busca("lib", listOf("Libro de cuentos"), parecido = false)
        busca("fol-az", listOf("Folder (azul) carta"), parecido = false)
        busca(
            "7501",
            listOf("Papel lustre plateado", "Cuaderno Prof 100h Raya Escriba", "Libro de cuentos", "Papel lustre azul", "Papel lustre rojo"),
            parecido = false,
        )
    }

    /** «negro» encuentra «Negra», «surtido» los «V/Colores» y «Colores Surt.», «1mm» el «1.0 Mm» y «plata» el «plateado»; el rayado, en cualquier orden. */
    @Test
    fun `tambien se encuentra lo que lee la caja del nombre`() {
        busca(
            "negro",
            listOf("Lapicero 1.0 Mm Negro Trazo", "Lapicero 0.7mm Negra Trazo", "Pluma punto fino negro", "Plumón permanente negro"),
            parecido = false,
        )
        busca(
            "surtido",
            listOf("Pluma Colores Surt. Trazo", "Bolígrafo multicolor", "Papelería surtida", "Pluma V/Colores Trazo"),
            parecido = false,
        )
        busca("1mm", listOf("Lapicero 1.0 Mm Negro Trazo"), parecido = false)
        busca("0.7mm", listOf("Lapicero 0.7mm Negra Trazo"), parecido = false)
        busca("lustre plata", listOf("Papel lustre plateado"), parecido = false)
        busca("lapicero negro", listOf("Lapicero 1.0 Mm Negro Trazo", "Lapicero 0.7mm Negra Trazo"), parecido = false)
        busca("cuaderno raya", listOf("Cuaderno Prof 100h Raya Escriba"), parecido = false)
        busca("raya cuaderno", listOf("Cuaderno Prof 100h Raya Escriba"), parecido = false)
    }

    /** Una letra de diferencia con cuatro letras o más; «nandu» no es «ñandú» (la ñ es otra letra), pero se le parece. */
    @Test
    fun `si nada coincide exacto sale lo parecido por un error de dedo`() {
        busca("sobre manial", listOf("Sobre manila carta", "Sobre manila oficio"), parecido = true)
        busca("manial", listOf("Sobre manila carta", "Sobre manila oficio"), parecido = true)
        busca("cuadreno", listOf("Cuaderno Prof 100h Raya Escriba"), parecido = true)
        busca("pluma pumto", listOf("Pluma punto fino negro"), parecido = true)
        busca("nandu", listOf("Nanda gris", "Ñandú de peluche"), parecido = true)
    }

    /** Y el otro producto que también tiene ese código, después. */
    @Test
    fun `el codigo exacto va primero sin importar mayusculas ni espacios`() {
        busca("750100", listOf("Papel lustre azul"), parecido = false)
        busca(" 750100 ", listOf("Papel lustre azul"), parecido = false)
        busca("7501234567890", listOf("Cuaderno Prof 100h Raya Escriba", "Libro de cuentos"), parecido = false)
    }

    /** Lo que salió por un sinónimo va después y dice por cuál; «bol» es muy corto. Sin sinónimos, solo lo que se llama así. */
    @Test
    fun `los sinonimos de la tienda valen por la palabra y desde cuatro letras por su principio`() {
        buscaSin(
            "pluma",
            listOf("Pluma Colores Surt. Trazo", "Pluma punto fino negro", "Pluma V/Colores Trazo"),
            parecido = false,
        )
        buscaCon(
            "pluma",
            listOf("Pluma Colores Surt. Trazo", "Pluma punto fino negro", "Pluma V/Colores Trazo", "Bolígrafo multicolor (por «pluma»)"),
            parecido = false,
        )
        busca(
            "plu",
            listOf("Pluma Colores Surt. Trazo", "Pluma punto fino negro", "Pluma V/Colores Trazo", "Plumón permanente negro"),
            parecido = false,
        )
        buscaSin("bolig", listOf("Bolígrafo multicolor"), parecido = false)
        buscaCon(
            "bolig",
            listOf("Bolígrafo multicolor", "Pluma Colores Surt. Trazo (por «boligrafo»)", "Pluma punto fino negro (por «boligrafo»)", "Pluma V/Colores Trazo (por «boligrafo»)"),
            parecido = false,
        )
        buscaSin("boli", listOf("Bolígrafo multicolor"), parecido = false)
        buscaCon(
            "boli",
            listOf("Bolígrafo multicolor", "Pluma Colores Surt. Trazo (por «boligrafo»)", "Pluma punto fino negro (por «boligrafo»)", "Pluma V/Colores Trazo (por «boligrafo»)"),
            parecido = false,
        )
        busca("bol", listOf("Bolígrafo multicolor"), parecido = false)
        buscaSin("boligrafo", listOf("Bolígrafo multicolor"), parecido = false)
        buscaCon(
            "boligrafo",
            listOf("Bolígrafo multicolor", "Pluma Colores Surt. Trazo (por «boligrafo»)", "Pluma punto fino negro (por «boligrafo»)", "Pluma V/Colores Trazo (por «boligrafo»)"),
            parecido = false,
        )
        busca("pluma trazo", listOf("Pluma Colores Surt. Trazo", "Pluma V/Colores Trazo"), parecido = false)
        busca(
            "trazo",
            listOf("Lapicero 1.0 Mm Negro Trazo", "Lapicero 0.7mm Negra Trazo", "Pluma Colores Surt. Trazo", "Pluma V/Colores Trazo"),
            parecido = false,
        )
    }

    /** En español de México: «Papel lustre» antes que «Papelería», y entre los que se llaman igual sin importar mayúsculas, el más antiguo. */
    @Test
    fun `sin otra cosa ordena por lo que empieza con lo escrito por lo mas vendido y por el nombre`() {
        buscaSin(
            "papel",
            listOf("Papel lustre azul", "Papel lustre plateado", "Papel lustre rojo", "Papelería surtida"),
            conVendidos = false,
            parecido = false,
        )
        buscaSin("lapiz", listOf("Lápiz adhesivo", "Lapiz Mirado 2", "LAPIZ MIRADO 2"), conVendidos = false, parecido = false)
        busca("sobre", listOf("Sobre manila carta", "Sobre manila oficio"), parecido = false)
    }

    /** Aunque no empiece con lo escrito ni se venda; si no salió con ese texto, no aparece. También antes de lo que se llama así. */
    @Test
    fun `lo elegido antes sale primero`() {
        buscaSin(
            "papel",
            listOf("Papelería surtida", "Papel lustre plateado", "Papel lustre azul", "Papel lustre rojo"),
            elegido = 3,
            parecido = false,
        )
        buscaSin("sobre", listOf("Sobre manila oficio", "Sobre manila carta"), elegido = 13, parecido = false)
        buscaSin("750100", listOf("Papel lustre azul"), elegido = 2, parecido = false)
        buscaCon(
            "pluma",
            listOf("Bolígrafo multicolor (por «pluma»)", "Pluma Colores Surt. Trazo", "Pluma punto fino negro", "Pluma V/Colores Trazo"),
            elegido = 21,
            parecido = false,
        )
    }

    /** Los primeros del mismo orden; con cero o menos, nada. */
    @Test
    fun `hasta el maximo`() {
        buscaSin("papel", listOf("Papel lustre plateado", "Papel lustre azul"), maximo = 2, parecido = false)
        buscaSin("papel", listOf("Papel lustre plateado"), maximo = 1, parecido = false)
        buscaSin("papel", emptyList(), maximo = 0, parecido = false)
        buscaSin("papel", emptyList(), maximo = -1, parecido = false)
    }

    @Test
    fun `la clave de lo escrito y la que se guarda`() {
        assertEquals("LAPIZ AZUL", IndiceDeBusqueda.clave("  Lápiz   Azul \t"))
        // Hasta 100 letras, sin el espacio en que quede el corte.
        assertEquals("A".repeat(99), IndiceDeBusqueda.claveGuardada("a".repeat(99) + " bcd"))
        assertEquals("A".repeat(100), IndiceDeBusqueda.claveGuardada("a".repeat(100) + "bcd"))
        assertEquals("PLUMA PUNTO FINO", IndiceDeBusqueda.claveGuardada(" Pluma  PUNTO fino"))
    }

    /** Lo normalizado de cada producto (el nombre y todos sus códigos, sin repetir y en orden) y su nombre en pedazos. */
    @Test
    fun `cada producto queda normalizado y en pedazos como en el POS`() {
        val esperados = listOf(
            "PAPEL LUSTRE AZUL 750100" to "Papel//PAPEL lustre//LUSTRE azul/Color/AZUL",
            "PAPEL LUSTRE ROJO 750101" to "Papel//PAPEL lustre//LUSTRE rojo/Color/ROJO",
            "PAPEL LUSTRE PLATEADO 750102" to "Papel//PAPEL lustre//LUSTRE plateado/Color/PLATA",
            "PAPELERIA SURTIDA" to "Papelería//PAPELERIA surtida/Color/SURTIDO",
            "FOLDER (AZUL) CARTA FOL-AZ" to "Folder//FOLDER (azul)//(AZUL) carta/Medida/CARTA",
            "BROCA 3/4 ACERO BRO-3/4" to "Broca//BROCA 3/4/Medida/3/4 acero//ACERO",
            "LIBRO DE CUENTOS 7501234567890 LIB-01" to "Libro//LIBRO de// cuentos//CUENTOS",
            "LAPICERO 1.0 MM NEGRO TRAZO P000001" to "Lapicero//LAPICERO 1.0/Medida/1MM Mm/Medida/ Negro/Color/NEGRO Trazo/Marca/TRAZO",
            "LAPICERO 0.7MM NEGRA TRAZO P000002" to "Lapicero//LAPICERO 0.7mm/Medida/0.7MM Negra/Color/NEGRO Trazo/Marca/TRAZO",
            "PLUMA V/COLORES TRAZO P000003" to "Pluma//PLUMA V/Colores/Color/SURTIDO Trazo/Marca/TRAZO",
            "PLUMA COLORES SURT. TRAZO" to "Pluma//PLUMA Colores/Color/SURTIDO Surt./Color/ Trazo/Marca/TRAZO",
            "CUADERNO PROF 100H RAYA ESCRIBA 7501234567890" to "Cuaderno//CUADERNO Prof//PROF 100h/Medida/100H Raya/Rayado/RAYA Escriba//ESCRIBA",
            "SOBRE MANILA CARTA" to "Sobre//SOBRE manila//MANILA carta/Medida/CARTA",
            "SOBRE MANILA OFICIO" to "Sobre//SOBRE manila//MANILA oficio/Medida/OFICIO",
            "PLUMON PERMANENTE NEGRO" to "Plumón//PLUMON permanente//PERMANENTE negro/Color/NEGRO",
            "PLUMA PUNTO FINO NEGRO" to "Pluma//PLUMA punto//PUNTO fino//FINO negro/Color/NEGRO",
            "ÑANDU DE PELUCHE" to "Ñandú//ÑANDU de// peluche//PELUCHE",
            "NANDA GRIS" to "Nanda//NANDA gris/Color/GRIS",
            "LAPIZ ADHESIVO" to "Lápiz//LAPIZ adhesivo//ADHESIVO",
            "LAPIZ MIRADO 2" to "Lapiz//LAPIZ Mirado//MIRADO 2//2",
            "LAPIZ MIRADO 2" to "LAPIZ//LAPIZ MIRADO//MIRADO 2//2",
            "BOLIGRAFO MULTICOLOR" to "Bolígrafo//BOLIGRAFO multicolor/Color/SURTIDO",
        )
        assertEquals(esperados, con.articulos.map { it.normalizado to BaseDePrueba.partes(it.partes) })
        assertEquals(productos.size, con.productos)
        assertEquals("Papelería surtida", con.porVariante(BaseDePrueba.id(3))?.descripcion)
        assertNull(con.porVariante("no está"))
    }

    @Test
    fun `sin productos no encuentra nada`() {
        assertEquals(0, IndiceDeBusqueda.VACIO.productos)
        assertEquals(emptyList<Encontrado>(), IndiceDeBusqueda.VACIO.buscar("papel"))
    }
}
