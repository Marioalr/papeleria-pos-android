package mx.papeleriapos.pos.busqueda

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Lo que puede cambiar entre productos que por lo demás se llaman igual, en el orden del POS (la API lo manda como número). */
enum class EjeFamilia {
    COLOR,
    MEDIDA,
    MARCA,
    CONTENIDO,
    RAYADO,
}

/**
 * Un pedazo del nombre tal como se escribió («0.7mm»), qué es (null: lo demás del nombre) y cómo se compara («0.7MM»).
 * La clave vacía no cuenta al comparar: las palabras de relleno («de», «con») y lo que acompaña a una medida («P/Fino»
 * junto a «0.7mm»).
 */
data class ParteDeNombre(val texto: String, val eje: EjeFamilia?, val clave: String)

/**
 * Lee el nombre de un producto en pedazos: el color, la medida, la marca, el contenido y, en cuadernos, el rayado. Así
 * la búsqueda junta en un renglón lo que solo cambia en una cosa («Los colores en un renglón», en la propuesta del POS), y
 * encuentra «Negra» con «negro». Las reglas salen de cómo están escritos los nombres que vienen de eleventa (análisis del
 * 2026-10-11). Las marcas no se escriben aquí: se aprenden de lo que se repite al final de los nombres del catálogo, y
 * también valen las dadas de alta.
 *
 * @param nombres Todo el catálogo: una marca se repite al final de por lo menos 3 nombres de 2 productos distintos.
 * @param marcas Las marcas dadas de alta.
 */
class LectorDeNombres(nombres: Iterable<String>, marcas: Iterable<String>? = null) {
    /** Las marcas de una palabra y las de dos («PAPER MATE»), normalizadas. */
    private val marcasDeUna = HashSet<String>()
    private val marcasDeDos = HashSet<String>()

    init {
        val veces = HashMap<String, Veces>()
        fun contar(marca: String, producto: String) {
            val v = veces.getOrPut(marca) { Veces() }
            v.productos.add(producto)
            v.nombres++
        }

        // Cada nombre una vez, sin importar mayúsculas.
        val vistos = HashSet<String>()
        for (nombre in nombres) {
            if (!vistos.add(ComoNet.claveSinMayusculas(nombre))) continue
            val partes = leer(nombre, conMarcas = false)
            if (partes.size < 2) continue
            val producto = normal(partes[0].texto)
            // La última palabra de lo demás del nombre (sin el color ni la medida que vengan después), y la de antes.
            val resto = partes.subList(1, partes.size).filter { it.eje == null && it.clave.isNotEmpty() }
            if (resto.isEmpty()) continue
            val ultima = resto[resto.size - 1].clave
            if (puedeSerMarca(ultima)) contar(ultima, producto)
            val antes = if (resto.size >= 2) resto[resto.size - 2] else null
            // Las dos juntas, si van seguidas en el nombre (`IndexOf` como en el POS: el primer pedazo igual).
            if (antes != null && puedeSerMarca(antes.clave) && partes.indexOf(resto[resto.size - 1]) - partes.indexOf(antes) == 1 &&
                SOLO_LETRAS.containsMatchIn(ultima)
            ) {
                contar("${antes.clave} $ultima", producto)
            }
        }
        for ((marca, v) in veces) {
            if (v.nombres >= 3 && v.productos.size >= 2) (if (' ' in marca) marcasDeDos else marcasDeUna).add(marca)
        }
        for (marca in marcas ?: emptyList()) {
            val normal = normal(marca)
            if (normal.isNotEmpty()) (if (' ' in normal) marcasDeDos else marcasDeUna).add(normal)
        }
    }

    private class Veces {
        var nombres = 0
        val productos = HashSet<String>()
    }

    /** Los pedazos del nombre, en orden; cada palabra va en uno. */
    fun leer(nombre: String): List<ParteDeNombre> = leer(nombre, conMarcas = true)

    private fun leer(nombre: String, conMarcas: Boolean): List<ParteDeNombre> {
        val palabras = nombre.split(' ').map { ComoNet.recortar(it) }.filter { it.isNotEmpty() }
        val normales = palabras.map { normal(it) }
        val partes = ArrayList<ParteDeNombre>(palabras.size)
        if (palabras.isEmpty()) return partes
        // La primera palabra es el producto: nunca un color ni una medida («Rosa C/Oso», «Colores C/12»).
        partes.add(ParteDeNombre(palabras[0], null, normales[0]))
        val deCuaderno = normales[0] in CUADERNOS

        var i = 1
        while (i < palabras.size) {
            val w = normales[i]
            val siguiente = if (i + 1 < palabras.size) normales[i + 1] else null
            val tercera = if (i + 2 < palabras.size) normales[i + 2] else null

            // En cuadernos, el rayado: raya, cuadro (grande, chico, alemán), C7, blanco (sin rayas).
            val rayado = if (deCuaderno) rayado(w, siguiente) else null
            if (rayado != null) {
                i = agregar(partes, palabras, i, EjeFamilia.RAYADO, rayado)
                continue
            }

            // Los colores surtidos: el mismo producto en un color que no se vende suelto («V/Colores», «Colores Surt.»).
            if (esSurtido(w) || ((w == "V" || w == "C/V") && siguiente == "COLORES")) {
                partes.add(ParteDeNombre(palabras[i], EjeFamilia.COLOR, "SURTIDO"))
                while (i + 1 < palabras.size && esSurtido(normales[i + 1])) {
                    i++
                    partes.add(ParteDeNombre(palabras[i], EjeFamilia.COLOR, ""))
                }
                i++
                continue
            }

            if (w == "PALO" && siguiente == "DE" && tercera == "ROSA") {
                partes.add(ParteDeNombre(palabras[i], EjeFamilia.COLOR, "PALO DE ROSA"))
                partes.add(ParteDeNombre(palabras[i + 1], EjeFamilia.COLOR, ""))
                partes.add(ParteDeNombre(palabras[i + 2], EjeFamilia.COLOR, ""))
                i += 3
                continue
            }
            val color = COLORES[w]
            if (color != null) {
                // El tono compuesto le gana al color solo: «Azul Rey», «Rosa Mexicano».
                val tono = siguiente?.let { TONOS[it] }
                val leida = if (tono != null) Leida("$color $tono", 2) else Leida(color, 1)
                i = agregar(partes, palabras, i, EjeFamilia.COLOR, leida)
                continue
            }

            val medida = medida(w, siguiente)
            if (medida != null) {
                i = agregar(partes, palabras, i, EjeFamilia.MEDIDA, medida)
                continue
            }

            val contenido = contenido(w, siguiente)
            if (contenido != null) {
                i = agregar(partes, palabras, i, EjeFamilia.CONTENIDO, contenido)
                continue
            }

            partes.add(ParteDeNombre(palabras[i], null, if (w in RELLENO) "" else w))
            i++
        }

        // Lo que acompaña a los milímetros es parte de la medida: «0.7mm … P/Fino» y «1.0mm … P/Mediano».
        if (partes.any { it.eje == EjeFamilia.MEDIDA && it.clave.endsWith("MM") }) {
            for (k in 1 until partes.size) {
                if (partes[k].eje == null && partes[k].clave.startsWith("P/")) {
                    partes[k] = partes[k].copy(eje = EjeFamilia.MEDIDA, clave = "")
                }
            }
        }

        // La marca: lo último de lo demás del nombre, aunque después venga el color o la medida («… Politec Rojo»). La de
        // dos palabras se busca primero («Paper Mate»), y pueden ir dos seguidas («Kilométrico Papermate»).
        if (conMarcas) {
            var k = partes.indexOfLast { esDelNucleo(it) }
            while (k >= 1 && esDelNucleo(partes[k])) {
                val dos = if (k >= 2 && esDelNucleo(partes[k - 1])) "${partes[k - 1].clave} ${partes[k].clave}" else null
                if (dos != null && dos in marcasDeDos) {
                    partes[k - 1] = partes[k - 1].copy(eje = EjeFamilia.MARCA, clave = dos)
                    partes[k] = partes[k].copy(eje = EjeFamilia.MARCA, clave = "")
                    k -= 2
                } else if (partes[k].clave in marcasDeUna) {
                    partes[k] = partes[k].copy(eje = EjeFamilia.MARCA)
                    k--
                } else {
                    break
                }
            }
        }
        return partes
    }

    /** Un color, una medida, un contenido o un rayado leído, con cuántas palabras toma (1 o 2). */
    private class Leida(val clave: String, val palabras: Int)

    companion object {
        /**
         * Lo leído en la palabra [i]: ella con su clave y, si toma dos, la siguiente con la clave vacía. Da el lugar de la
         * palabra que sigue.
         */
        private fun agregar(
            partes: MutableList<ParteDeNombre>,
            palabras: List<String>,
            i: Int,
            eje: EjeFamilia,
            leida: Leida,
        ): Int {
            partes.add(ParteDeNombre(palabras[i], eje, leida.clave))
            if (leida.palabras == 1) return i + 1
            partes.add(ParteDeNombre(palabras[i + 1], eje, ""))
            return i + 2
        }

        /** Para ordenar las medidas de menor a mayor: el primer número, o el lugar de la palabra. */
        fun ordenDeMedida(clave: String): Double {
            if (clave in TAMANOS.values || clave == "DOBLE CARTA") {
                return when (clave) {
                    "MINI" -> 1.0
                    "CHICO" -> 2.0
                    "MEDIANO" -> 3.0
                    "GRANDE" -> 4.0
                    "JUMBO" -> 5.0
                    "GIGANTE" -> 6.0
                    "ESQUELA" -> 10.0
                    "CARTA" -> 11.0
                    "A4" -> 11.5
                    "OFICIO" -> 12.0
                    else -> 13.0
                }
            }
            val numero = PRIMER_NUMERO.find(clave) ?: return Double.MAX_VALUE
            val valor = numeroDecimal(numero.value)
            val fraccion = FRACCION.find(clave)
            return if (fraccion != null && fraccion.value == clave) {
                valor / numeroDecimal(clave.substring(clave.indexOf('/') + 1))
            } else {
                valor
            }
        }

        private fun esDelNucleo(parte: ParteDeNombre): Boolean = parte.eje == null && parte.clave.isNotEmpty()

        /** Para comparar: sin acentos, en mayúsculas y sin el punto final de las abreviaturas («Surt.»). */
        private fun normal(palabra: String): String = Texto.normalizar(palabra).trimEnd('.', ',', ';', ':')

        private fun puedeSerMarca(clave: String): Boolean =
            clave.length >= 3 && SOLO_LETRAS.containsMatchIn(clave) && clave !in NO_SON_MARCA && clave !in RELLENO &&
                clave !in COLORES && clave !in TONOS && !esSurtido(clave)

        private fun esSurtido(w: String): Boolean = w in SURTIDOS

        private fun medida(w: String, siguiente: String?): Leida? {
            val nu = NUMERO_CON_UNIDAD.find(w)
            if (nu != null) {
                val unidad = unidad(nu.groupValues[2])
                if (unidad != null) return Leida("${numero(nu.groupValues[1])}$unidad", 1)
            }
            val d = DIMENSIONES.find(w)
            if (d != null) {
                return Leida("${numero(d.groupValues[1])}X${numero(d.groupValues[2])}${unidad(d.groupValues[3]) ?: ""}", 1)
            }
            if (SOLO_NUMERO.containsMatchIn(w)) {
                val suUnidad = siguiente?.let { unidad(it) }
                if (suUnidad != null) return Leida("${numero(w)}$suUnidad", 2)
                // Un decimal sin unidad son milímetros: «Lapicero 1.0» es el de 1.0 mm (por confirmar en la depuración).
                if ('.' in w || ',' in w) return Leida("${numero(w)}MM", 1)
                return null
            }
            val g = NUMERO_CON_GATO.find(w)
            if (g != null) return Leida("#${g.groupValues[1]}", 1)
            if ((w == "NO" || w == "NUM") && siguiente != null && ENTERO.containsMatchIn(siguiente)) {
                return Leida("#$siguiente", 2)
            }
            if (FRACCION.containsMatchIn(w)) return Leida(w, 1)
            if (w == "DOBLE" && siguiente == "CARTA") return Leida("DOBLE CARTA", 2)
            return TAMANOS[w]?.let { Leida(it, 1) }
        }

        private fun contenido(w: String, siguiente: String?): Leida? {
            CON_CONTENIDO.find(w)?.let { return Leida("C/${it.groupValues[1]}", 1) }
            PIEZAS.find(w)?.let { return Leida("C/${it.groupValues[1]}", 1) }
            if (ENTERO.containsMatchIn(w) && siguiente in PIEZAS_SUELTAS) return Leida("C/$w", 2)
            return null
        }

        private fun rayado(w: String, siguiente: String?): Leida? {
            if (w == "RAYA" || w == "RAYAS" || w == "RAYADO" || w == "RAYADA") return Leida("RAYA", 1)
            if (w == "DOBLE" && (siguiente == "RAYA" || siguiente == "RAYAS")) return Leida("DOBLE RAYA", 2)
            if (w == "CUADRO" || w == "CUADROS" || w == "CUADRICULA") {
                val conTamano = siguiente == "GRANDE" || siguiente == "CHICO" || siguiente == "ALEMAN"
                return if (conTamano) Leida("CUADRO $siguiente", 2) else Leida("CUADRO", 1)
            }
            if (w == "BLANCO" || w == "BLANCA" || w == "BLANCAS" || w == "BLANCOS") return Leida("BLANCO", 1)
            return if (CUADRO_DE_CUADERNO.containsMatchIn(w)) Leida(w, 1) else null
        }

        /**
         * «1.0» es «1», «0.70» es «0.7» y «.5» es «0.5»: hasta tres decimales, redondeando la mitad hacia arriba (`decimal` con
         * "0.###" en el POS), del número como lo guarda el `decimal` ([comoDecimal]): con 29 cifras o más ya viene
         * redondeado, y «1.00049999999999999999999999999» es «1.001». Los ceros del final se quitan del texto y no con
         * `stripTrailingZeros`, que en Android deja el cero como «0.000». Con dígitos que no son ASCII o un número que no cabe
         * en un `decimal` el POS truena; aquí valen por su número.
         */
        private fun numero(texto: String): String {
            val t = digitosAscii(texto).replace(',', '.')
            val escrito = if (t.startsWith('.')) "0$t" else t
            val valor = comoDecimal(escrito) ?: BigDecimal(escrito)
            return valor.setScale(3, RoundingMode.HALF_UP).toPlainString().trimEnd('0').trimEnd('.')
        }

        /** Lo más que guarda un `decimal`: 96 bits. */
        private val MAXIMO_DECIMAL = BigInteger.ONE.shiftLeft(96).subtract(BigInteger.ONE)

        /** Lo que deja .NET cuando redondear [MAXIMO_DECIMAL] hacia arriba se pasa: 2^96 entre 10 (hacia arriba), un decimal menos. */
        private val DECIMO_DEL_MAXIMO = BigInteger("19999999999999999999999A", 16)

        /**
         * [escrito] (cifras ASCII y a lo más un punto) como lo guarda `decimal.Parse` (`Number.TryNumberToDecimal` de .NET): desde
         * la primera cifra que no es cero, todas las que quepan en 96 bits y hasta 28 decimales. La siguiente redondea: hacia
         * arriba desde el 5, salvo un 5 seguido solo de ceros con lo guardado par, que se queda (al par). .NET guarda 30 cifras
         * y de las demás solo si alguna no es cero, que es lo que pesa al redondear. Null si no cabe (el POS truena).
         */
        private fun comoDecimal(escrito: String): BigDecimal? {
            val cifras = StringBuilder()
            var escala = 0 // cuántas cifras van antes del punto (negativo: ceros después del punto)
            var colaConCifras = false
            var enDecimales = false
            for (c in escrito) {
                if (c == '.') {
                    enDecimales = true
                } else if (c != '0' || cifras.isNotEmpty()) {
                    if (cifras.length < 30) cifras.append(c) else if (c != '0') colaConCifras = true
                    if (!enDecimales) escala++
                } else if (enDecimales) {
                    escala--
                }
            }
            if (cifras.isEmpty()) return BigDecimal.ZERO
            if (escala > 29) return null
            var guardado = BigInteger.ZERO
            var e = escala
            var p = 0
            // Cifra por cifra (y ceros, si se acaban antes del punto) mientras quepa.
            while (e > 0 || (p < cifras.length && e > -28)) {
                val cifra = if (p < cifras.length) (cifras[p] - '0').toLong() else 0L
                val siguiente = guardado.multiply(BigInteger.TEN).add(BigInteger.valueOf(cifra))
                if (siguiente > MAXIMO_DECIMAL) break
                guardado = siguiente
                if (p < cifras.length) p++
                e--
            }
            val redondea = p < cifras.length && cifras[p] >= '5' &&
                (cifras[p] > '5' || guardado.testBit(0) || colaConCifras || (p + 1 until cifras.length).any { cifras[it] != '0' })
            if (redondea) {
                guardado = guardado.add(BigInteger.ONE)
                if (guardado > MAXIMO_DECIMAL) {
                    guardado = DECIMO_DEL_MAXIMO
                    e++
                }
            }
            if (e > 0) return null
            return if (e <= -29) BigDecimal.ZERO else BigDecimal(guardado, -e)
        }

        /** Un número como lo lee `double.Parse`, para ordenar las medidas. */
        private fun numeroDecimal(texto: String): Double = digitosAscii(texto).toDouble()

        /** Los dígitos de otras escrituras (los que acepta \d en .NET, como «٣» o «３») como los del 0 al 9, para leerlos. */
        private fun digitosAscii(texto: String): String {
            if (texto.all { it < '\u0080' }) return texto
            val sb = StringBuilder(texto.length)
            var i = 0
            while (i < texto.length) {
                val punto = Character.codePointAt(texto, i)
                if (punto >= 0x80 && Character.isDigit(punto)) {
                    sb.append('0' + Character.digit(punto, 10))
                } else {
                    sb.appendCodePoint(punto)
                }
                i += Character.charCount(punto)
            }
            return sb.toString()
        }

        private fun unidad(u: String): String? = when (u) {
            "MM" -> "MM"
            "CM" -> "CM"
            "M", "MT", "MTS" -> "M"
            "ML" -> "ML"
            "L", "LT", "LTS" -> "L"
            "G", "GR", "GRS", "GMS" -> "G"
            "KG", "K" -> "KG"
            "OZ" -> "OZ"
            "H", "HJ", "HJS", "HOJAS" -> "H"
            "PULG" -> "PULG"
            else -> null
        }

        private val CUADERNOS = hashSetOf("CUADERNO", "CUADERNOS", "LIBRETA", "LIBRETAS", "BLOCK", "BLOC", "CUADERNILLO")

        private val RELLENO = hashSetOf("DE", "DEL", "Y", "O", "X", "CON", "PARA", "EL", "LA", "LOS", "LAS", "EN", "-", "/")

        private val SURTIDOS = hashSetOf(
            "V/COLORES", "VCOLORES", "V/COLOR", "MULTICOLOR", "MULTICOLORES", "ARCOIRIS", "COLORES", "SURTIDO", "SURTIDOS", "SURTIDA",
            "SURTIDAS", "SURT",
        )

        private val PIEZAS_SUELTAS = hashSetOf("PZ", "PZS", "PZA", "PZAS", "PIEZAS")

        /** Los colores y cómo se comparan: sin género ni plural («Negra» = «Negro»). */
        private val COLORES = lista(
            "NEGRO" to listOf("NEGRO", "NEGRA", "NEGROS", "NEGRAS"),
            "BLANCO" to listOf("BLANCO", "BLANCA", "BLANCOS", "BLANCAS"),
            "AZUL" to listOf("AZUL", "AZULES"),
            "ROJO" to listOf("ROJO", "ROJA", "ROJOS", "ROJAS"),
            "VERDE" to listOf("VERDE", "VERDES"),
            "AMARILLO" to listOf("AMARILLO", "AMARILLA", "AMARILLOS", "AMARILLAS"),
            "ROSA" to listOf("ROSA", "ROSAS", "ROSADO", "ROSADA"),
            "MORADO" to listOf("MORADO", "MORADA", "MORADOS", "MORADAS"),
            "NARANJA" to listOf("NARANJA", "NARANJAS"),
            "CAFE" to listOf("CAFE", "CAFES"),
            "GRIS" to listOf("GRIS", "GRISES"),
            "LILA" to listOf("LILA", "LILAS"),
            "VIOLETA" to listOf("VIOLETA", "VIOLETAS"),
            "TURQUESA" to listOf("TURQUESA", "TURQUESAS"),
            "FUCSIA" to listOf("FUCSIA", "FIUSHA"),
            "MAGENTA" to listOf("MAGENTA"),
            "DORADO" to listOf("DORADO", "DORADA", "DORADOS", "DORADAS", "ORO"),
            "PLATA" to listOf("PLATA", "PLATEADO", "PLATEADA", "PLATEADOS", "PLATEADAS"),
            "COBRE" to listOf("COBRE"),
            "TRANSPARENTE" to listOf("TRANSPARENTE", "TRANSPARENTES"),
            "CREMA" to listOf("CREMA"),
            "HUESO" to listOf("HUESO"),
            "MARFIL" to listOf("MARFIL"),
            "BEIGE" to listOf("BEIGE"),
            "OCRE" to listOf("OCRE"),
            "VINO" to listOf("VINO"),
            "GUINDA" to listOf("GUINDA"),
            "CORAL" to listOf("CORAL"),
            "LAVANDA" to listOf("LAVANDA"),
            "MALVA" to listOf("MALVA"),
            "CELESTE" to listOf("CELESTE"),
            "MARINO" to listOf("MARINO"),
            "MELON" to listOf("MELON"),
            "TERRACOTA" to listOf("TERRACOTA"),
            "CARMIN" to listOf("CARMIN"),
            "CIAN" to listOf("CIAN"),
            "SEPIA" to listOf("SEPIA"),
        )

        /** Lo que hace compuesto a un color («Azul Rey», «Verde Limón»). Neón, pastel solo o metálico son el acabado. */
        private val TONOS = lista(
            "REY" to listOf("REY"), "MARINO" to listOf("MARINO"), "CIELO" to listOf("CIELO"), "CLARO" to listOf("CLARO", "CLARA"),
            "OBSCURO" to listOf("OBSCURO", "OBSCURA", "OSCURO", "OSCURA"), "ELECTRICO" to listOf("ELECTRICO"), "PASTEL" to listOf("PASTEL"),
            "CELESTE" to listOf("CELESTE"), "COBALTO" to listOf("COBALTO"), "ULTRAMAR" to listOf("ULTRAMAR"), "TURQUESA" to listOf("TURQUESA"),
            "ROYAL" to listOf("ROYAL"), "LIMON" to listOf("LIMON"), "BANDERA" to listOf("BANDERA"), "MANZANA" to listOf("MANZANA"),
            "ESMERALDA" to listOf("ESMERALDA"), "HOJA" to listOf("HOJA"), "OLIVO" to listOf("OLIVO"), "MILITAR" to listOf("MILITAR"),
            "JADE" to listOf("JADE"), "MEXICANO" to listOf("MEXICANO"), "FUERTE" to listOf("FUERTE"), "BEBE" to listOf("BEBE"),
            "CEREZA" to listOf("CEREZA"), "CANARIO" to listOf("CANARIO"), "HUEVO" to listOf("HUEVO"), "MEDIO" to listOf("MEDIO"),
            "INTENSO" to listOf("INTENSO"), "NIEVE" to listOf("NIEVE"), "TITANIO" to listOf("TITANIO"),
        )

        private val TAMANOS = lista(
            "CARTA" to listOf("CARTA"), "OFICIO" to listOf("OFICIO"), "ESQUELA" to listOf("ESQUELA"), "A4" to listOf("A4"),
            "MINI" to listOf("MINI"), "CHICO" to listOf("CHICO", "CHICA", "CHICOS", "CHICAS"),
            "MEDIANO" to listOf("MEDIANO", "MEDIANA", "MEDIANOS", "MEDIANAS"), "GRANDE" to listOf("GRANDE", "GRANDES"),
            "JUMBO" to listOf("JUMBO"), "GIGANTE" to listOf("GIGANTE", "GIGANTES"),
        )

        /** Palabras que se repiten al final de muchos nombres sin ser marca: acabados, empaques, para quién y sabores. */
        private val NO_SON_MARCA = hashSetOf(
            "ESCOLAR", "ESCOLARES", "INFANTIL", "PROFESIONAL", "PROF", "PLASTICO", "PLASTICA", "METALICO", "METALICA", "ADHESIVO",
            "ADHESIVA", "ESPIRAL", "COSIDO", "DOBLE", "SENCILLO", "GRUESO", "GRUESA", "DELGADO", "DELGADA", "FINO", "FINA",
            "LARGO", "LARGOS", "CORTO", "CORTOS", "REDONDO", "REDONDA", "TRIANGULAR", "TRIANGULARES", "HEXAGONAL", "BORRABLE",
            "PERMANENTE", "LAVABLE", "RETRACTIL", "NEON", "PASTEL", "METALIZADO", "GLITTER", "DIAMANTADO", "HOLOGRAFICO", "MATE",
            "BRILLANTE", "LISO", "LISA", "ESTAMPADO", "NIÑO", "NIÑA", "NIÑOS", "NIÑAS", "DAMA", "CABALLERO", "HOMBRE", "MUJER",
            "BEBE", "PIEZA", "PIEZAS", "PZ", "PZA", "PZS", "PAQ", "PAQUETE", "BOLSA", "CAJA", "BLISTER", "ROLLO", "METRO",
            "SET", "JUEGO", "JGO", "SUELTO", "SUELTA", "MAYOREO", "MAY", "MENUDEO", "UNIDAD", "GRANEL", "KILO", "LITRO",
            "FRESA", "MANGO", "UVA", "LIMON", "PIÑA", "MANZANA", "CHOCOLATE", "VAINILLA", "COCO", "TAMARINDO", "CHAMOY",
            "CEREZA", "SANDIA", "DURAZNO", "GUAYABA", "NUEZ", "CAJETA", "MORA", "FRAMBUESA", "MENTA", "CANELA", "NATURAL",
            "ORIGINAL", "CLASICO", "CLASICA", "LIGHT", "NUEVO", "NUEVA", "DECORADO", "DECORADA", "DECORATIVO", "DECORATIVA",
        )

        /** Cada escritura con su clave. */
        private fun lista(vararg valores: Pair<String, List<String>>): Map<String, String> {
            val mapa = HashMap<String, String>()
            for ((clave, escrituras) in valores) {
                for (e in escrituras) require(mapa.put(e, clave) == null) { "«$e» está dos veces" }
            }
            return mapa
        }

        // Como en el POS: \d de .NET es cualquier dígito decimal de Unicode (\p{Nd}), no solo del 0 al 9.
        private const val D = "\\p{Nd}"
        private val NUMERO_CON_UNIDAD =
            Regex("^($D+(?:[.,]$D+)?|[.,]$D+)(MM|CM|MTS|MT|M|ML|LTS|LT|L|GRS|GR|GMS|G|KG|K|OZ|HJS|HJ|H|HOJAS|PULG)$")
        private val DIMENSIONES = Regex("^($D+(?:[.,]$D+)?|[.,]$D+)X($D+(?:[.,]$D+)?)(CM|MM|MTS|MT|M)?$")
        private val SOLO_NUMERO = Regex("^($D+(?:[.,]$D+)?|[.,]$D+)$")
        private val ENTERO = Regex("^$D+$")
        private val NUMERO_CON_GATO = Regex("^(?:#|NO\\.?|N°)($D+)$")
        private val FRACCION = Regex("^$D+/$D+$")
        private val CON_CONTENIDO = Regex("^C/($D+)$")
        private val PIEZAS = Regex("^($D+)(PZ|PZS|PZA|PZAS|PIEZAS)$")
        private val CUADRO_DE_CUADERNO = Regex("^C$D{1,2}$")
        private val SOLO_LETRAS = Regex("^[A-ZÑ]+$")
        private val PRIMER_NUMERO = Regex("$D+(?:\\.$D+)?")
    }
}
