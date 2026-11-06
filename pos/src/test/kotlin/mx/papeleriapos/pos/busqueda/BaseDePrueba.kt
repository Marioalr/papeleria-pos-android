package mx.papeleriapos.pos.busqueda

/**
 * La base de prueba del POS (la de ServicioDePrueba: GeneradorFicticios con 30 días) y lo que su búsqueda encontró en ella
 * ([BaseDePruebaDatos], de pos/herramientas/oraculo): con ella las pruebas que en el POS van contra el servicio comparan la
 * búsqueda de Kotlin con la del POS, con los mismos productos, marcas y sinónimos.
 */
internal object BaseDePrueba {
    /** Un producto de la copia: su lugar (el orden de su Id), lo que la búsqueda usa y la huella de sus partes en el POS. */
    class Producto(
        val lugar: Int,
        val descripcion: String,
        val codigo: String?,
        val codigos: List<String>,
        val vendidos: Int,
        val huellaDePartes: String,
    ) {
        val varianteId: String = id(lugar)

        fun conNombre(descripcion: String) = Producto(lugar, descripcion, codigo, codigos, vendidos, huellaDePartes)
    }

    /** Lo que encontró el POS con un texto, en su orden: el lugar de cada producto y, si salió por un sinónimo, la palabra. */
    class Esperado(val juego: String, val texto: String, val elegido: Int, val parecido: Boolean, val encontrados: List<Pair<Int, String?>>)

    /** El Id de un lugar, como los de [BaseDePruebaDatos]: en el mismo orden que los de la base de prueba (van por fecha). */
    fun id(lugar: Int): String = "00000000-0000-7000-8000-" + lugar.toString(16).padStart(12, '0')

    fun lugar(varianteId: String): Int = varianteId.substring(24).toInt(16)

    val productos: List<Producto> by lazy {
        renglones(BaseDePruebaDatos.PRODUCTOS).mapIndexed { i, r ->
            val c = r.split('|').map { desescapar(it) }
            Producto(i, c[0], c[1].ifEmpty { null }, if (c[2].isEmpty()) emptyList() else c[2].split(' '), c[3].toInt(), c[4])
        }
    }

    val marcas: List<String> by lazy { BaseDePruebaDatos.MARCAS.split('|').map { desescapar(it) } }

    /** Cuántas veces se vendió (en la base de prueba, todo cae en los 90 días). */
    fun vendidos(varianteId: String): Int = productos.getOrNull(lugar(varianteId))?.vendidos ?: 0

    val esperados: List<Esperado> by lazy {
        renglones(BaseDePruebaDatos.ESPERADO).map { r ->
            val c = r.split('|').map { desescapar(it) }
            val encontrados = if (c[4].isEmpty()) {
                emptyList()
            } else {
                c[4].split(',').map {
                    val k = it.indexOf(':')
                    if (k < 0) it.toInt() to null else it.substring(0, k).toInt() to it.substring(k + 1)
                }
            }
            Esperado(c[0], c[1], c[2].toInt(), c[3] == "1", encontrados)
        }
    }

    /** Lo que encontró el POS con ese juego, texto y elegido; si el oráculo no lo anotó, la prueba está mal hecha. */
    fun esperado(juego: String, texto: String, elegido: Int = -1): Esperado =
        esperados.firstOrNull { it.juego == juego && it.texto == texto && it.elegido == elegido }
            ?: error("El oráculo no anotó «$texto» en el juego $juego (con elegido $elegido): pos/herramientas/oraculo")

    /** La búsqueda de Kotlin con los productos de la copia, sus marcas y los sinónimos de la tienda. */
    fun armar(conProductos: List<Producto> = productos, sinonimos: List<Sinonimo> = emptyList()): IndiceDeBusqueda =
        IndiceDeBusqueda.armar(conProductos.map { ProductoParaBuscar(it.varianteId, it.descripcion, it.codigo, it.codigos) }, marcas, sinonimos)

    /** Los dos servicios que da de alta BusquedaApiTests, sin código ni ventas: después de todos (sus Id son más nuevos). */
    val engargolados: List<Producto> by lazy {
        listOf("Engargolado orden alfa", "Engargolado orden beta").mapIndexed { k, nombre ->
            Producto(productos.size + k, nombre, null, emptyList(), 0, "")
        }
    }

    /**
     * Cada juego del oráculo armado en Kotlin: «base», sin sinónimos; «rotulador», «rotulador-solo» y «boligrafo», con los
     * sinónimos que dan de alta las pruebas del POS; «revueltos», los productos al revés con cada séptimo repetido y los
     * sinónimos en otro orden y repetidos; «zanfona», el producto que renombra CopiaAlDiaTests con su sinónimo; y
     * «engargolado», con los dos servicios.
     */
    @Synchronized
    fun juego(nombre: String): IndiceDeBusqueda = juegos.getOrPut(nombre) {
        when (nombre) {
            "base" -> armar()
            "rotulador" -> armar(sinonimos = listOf(Sinonimo("ROTULADOR", "PLUMON"), Sinonimo("PLUMON", "ROTULADOR")))
            "rotulador-solo" -> armar(sinonimos = listOf(Sinonimo("ROTULADOR", "PLUMON")))
            "boligrafo" -> armar(sinonimos = listOf(Sinonimo("BOLIGRAFO", "PLUMA"), Sinonimo("PLUMA", "BOLIGRAFO")))
            "revueltos" -> armar(
                productos.asReversed() + productos.filterIndexed { i, _ -> i % 7 == 0 },
                listOf(Sinonimo("PLUMA", "BOLIGRAFO"), Sinonimo("BOLIGRAFO", "PLUMA"), Sinonimo("BOLIGRAFO", "PLUMA")),
            )
            "zanfona" -> armar(
                productos.map { if (it.lugar == BaseDePruebaDatos.RENOMBRADO) it.conNombre(BaseDePruebaDatos.NOMBRE_RENOMBRADO) else it },
                listOf(Sinonimo("OCARINA", "ZANFONA")),
            )
            "engargolado", "engargolado-vendido" -> armar(productos + engargolados)
            else -> error("No hay juego $nombre")
        }
    }

    private val juegos = HashMap<String, IndiceDeBusqueda>()

    /** Lo vendido de cada juego: en «engargolado-vendido», el beta se vendió una vez. */
    fun vendidosDe(juego: String): (String) -> Int =
        if (juego == "engargolado-vendido") ({ if (it == engargolados[1].varianteId) 1 else vendidos(it) }) else ::vendidos

    /** Lo que encontró, como lo anota el oráculo: el lugar y la palabra del sinónimo. */
    fun lugares(encontrados: List<Encontrado>): List<Pair<Int, String?>> = encontrados.map { lugar(it.articulo.varianteId) to it.porSinonimo }

    /** El nombre en pedazos como lo escribe el POS: «Negro/Color/NEGRO», con el eje como su enum de C# (vacío si no tiene). */
    fun partes(partes: List<ParteDeNombre>): String = partes.joinToString(" ") { "${it.texto}/${ejeDelPos(it.eje)}/${it.clave}" }

    fun ejeDelPos(eje: EjeFamilia?): String = when (eje) {
        null -> ""
        EjeFamilia.COLOR -> "Color"
        EjeFamilia.MEDIDA -> "Medida"
        EjeFamilia.MARCA -> "Marca"
        EjeFamilia.CONTENIDO -> "Contenido"
        EjeFamilia.RAYADO -> "Rayado"
    }

    /** La huella de un texto (String.hashCode), con la que van algunos datos del oráculo. */
    fun huella(texto: String): String = "%08x".format(texto.hashCode())

    /** Los renglones de unas constantes de [BaseDePruebaDatos] (ninguno va vacío). */
    fun renglones(pedazos: List<String>): List<String> = pedazos.flatMap { it.split('\n') }.filter { it.isNotEmpty() }

    /** Lo que [BaseDePruebaDatos] trae como \uXXXX (lo que no se ve o no cabe en un texto crudo). */
    fun desescapar(s: String): String {
        if ("\\u" !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 5 < s.length && s[i + 1] == 'u') {
                sb.append(s.substring(i + 2, i + 6).toInt(16).toChar())
                i += 6
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }
}
