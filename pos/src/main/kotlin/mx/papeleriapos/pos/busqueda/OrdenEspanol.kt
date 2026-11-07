package mx.papeleriapos.pos.busqueda

/**
 * El orden por nombre de la búsqueda del POS: el de .NET con la cultura es-MX sin importar mayúsculas
 * (`CompareInfo.GetSortKey(nombre, CompareOptions.IgnoreCase)`), que en Windows y en Linux sale de ICU: la intercalación de
 * Unicode (CLDR) con la ñ entre la n y la o, hasta los acentos. Los espacios y los signos cuentan y van antes que los números
 * y las letras («Papel lustre» antes que «Papelería»); `java.text.Collator` los ignora y ordenaría distinto, y el de Android
 * cambia con la versión de ICU de cada celular. Por eso la tabla va aquí ([TablaDeOrden], sacada de ICU).
 *
 * La llave de un nombre son sus primarias (la letra), un 1 y sus secundarias (los acentos): se compara número por número,
 * como la llave de ICU, y una que es el principio de otra va antes. Dos nombres que solo cambian en mayúsculas tienen la
 * misma llave, y lo que ICU no cuenta (el BOM que deja un CSV, el U+FE0F que el teclado pone tras ❤) no la cambia.
 */
internal object OrdenEspanol {
    private val rangos = TablaDeOrden.RANGOS

    /** Los elementos de cada punto de cada rango (primaria y secundaria, alternadas); null si ICU no lo tiene. */
    private val elementos: Array<Array<IntArray?>>

    /** Las contracciones, por los dos caracteres (el primero * 0x10000 + el segundo). */
    private val contracciones = HashMap<Int, IntArray>()

    /** Los caracteres con que empieza alguna contracción: solo con ellos se mira el que sigue. */
    private val empiezanContraccion = HashSet<Int>()

    /** Los elementos de lo que no cuenta. */
    private val ninguno = IntArray(0)

    init {
        val valorDe = IntArray(128) { -1 }
        TablaDeOrden.ALFABETO.forEachIndexed { i, c -> valorDe[c.code] = i }
        val texto = TablaDeOrden.ELEMENTOS
        val secundaria = (1 shl TablaDeOrden.BITS_SECUNDARIA) - 1
        var k = 0
        elementos = Array(rangos.size / 2) { r ->
            Array(rangos[2 * r + 1] - rangos[2 * r] + 1) {
                val cuantos = valorDe[texto[k++].code]
                if (cuantos == TablaDeOrden.ALFABETO.length - 1) {
                    null
                } else {
                    IntArray(2 * cuantos).also { ces ->
                        for (e in 0 until cuantos) {
                            var valor = 0
                            repeat(4) { valor = (valor shl 6) or valorDe[texto[k++].code] }
                            ces[2 * e] = valor shr TablaDeOrden.BITS_SECUNDARIA
                            ces[2 * e + 1] = valor and secundaria
                        }
                    }
                }
            }
        }
        check(k == texto.length) { "La tabla de orden no cuadra con sus rangos." }
        for (c in TablaDeOrden.CONTRACCIONES) {
            contracciones[c[0] * 0x10000 + c[1]] = c.copyOfRange(2, c.size)
            empiezanContraccion.add(c[0])
        }
    }

    private fun elementosDe(punto: Int): IntArray? {
        for (r in 0 until rangos.size / 2) {
            val desde = rangos[2 * r]
            if (punto < desde) break
            if (punto <= rangos[2 * r + 1]) return elementos[r][punto - desde]
        }
        return if (enRangos(TablaDeOrden.IGNORABLES, 2, punto) >= 0) ninguno else null
    }

    /** El lugar en [lista] (de [paso] en [paso]: desde, hasta y lo demás) del rango que tiene a [punto]; -1 si ninguno. */
    private fun enRangos(lista: IntArray, paso: Int, punto: Int): Int {
        var desde = 0
        var hasta = lista.size / paso
        while (desde < hasta) {
            val medio = (desde + hasta) ushr 1
            when {
                punto < lista[paso * medio] -> hasta = medio
                punto > lista[paso * medio + 1] -> desde = medio + 1
                else -> return paso * medio
            }
        }
        return -1
    }

    /**
     * La clase de combinación de [punto]: la del principio por 256 más la del final ([TablaDeOrden.COMBINANTES]). Menos de 256
     * es una letra (o algo que la tabla no tiene): ahí se acaban las marcas que siguen a una letra.
     */
    private fun clase(punto: Int): Int {
        val lugar = enRangos(TablaDeOrden.COMBINANTES, 3, punto)
        return if (lugar < 0) 0 else TablaDeOrden.COMBINANTES[lugar + 2]
    }

    private fun contraccion(primero: Int, segundo: Int): IntArray? =
        if (segundo > 0xFFFF) null else contracciones[primero * 0x10000 + segundo]

    /** La llave de [texto] para ordenar por nombre (ver la clase). */
    fun llave(texto: String): IntArray {
        val primarias = Enteros(texto.length + 2)
        val secundarias = Enteros(texto.length + 2)
        fun agregar(punto: Int, ces: IntArray?) {
            if (ces == null) {
                // Lo que la tabla no tiene va después de todo lo que sí, en el orden de su número.
                primarias.agregar(TablaDeOrden.SIN_TABLA + punto)
                secundarias.agregar(TablaDeOrden.COMUN)
                return
            }
            for (e in 0 until ces.size / 2) {
                if (ces[2 * e] != 0) primarias.agregar(ces[2 * e])
                if (ces[2 * e + 1] != 0) secundarias.agregar(ces[2 * e + 1])
            }
        }

        var i = 0
        siguiente@ while (i < texto.length) {
            val punto = Character.codePointAt(texto, i)
            var largo = Character.charCount(punto)
            var ces = elementosDe(punto)
            // La ñ escrita como n con tilde y la «l·» se leen juntas, como en ICU.
            if (punto in empiezanContraccion && i + largo < texto.length) {
                val sigue = Character.codePointAt(texto, i + largo)
                val junta = contraccion(punto, sigue)
                if (junta != null) {
                    ces = junta
                    largo += Character.charCount(sigue)
                } else if (clase(sigue) >= 0x100) {
                    // La tilde también, si entre ella y la n solo van marcas que no la tapan, de clase menor que la suya
                    // («n» + punto abajo + tilde): la contracción separada de ICU. Después van esas marcas.
                    var anterior = clase(sigue) and 0xFF
                    var j = i + largo + Character.charCount(sigue)
                    while (j < texto.length) {
                        val marca = Character.codePointAt(texto, j)
                        val suClase = clase(marca)
                        if (suClase < 0x100) break
                        val separada = if (anterior < (suClase shr 8)) contraccion(punto, marca) else null
                        if (separada != null) {
                            agregar(punto, separada)
                            var m = i + largo
                            while (m < j) {
                                val saltada = Character.codePointAt(texto, m)
                                agregar(saltada, elementosDe(saltada))
                                m += Character.charCount(saltada)
                            }
                            i = j + Character.charCount(marca)
                            continue@siguiente
                        }
                        anterior = suClase and 0xFF
                        j += Character.charCount(marca)
                    }
                }
            }
            agregar(punto, ces)
            i += largo
        }
        val llave = IntArray(primarias.cuantos + 1 + secundarias.cuantos)
        System.arraycopy(primarias.valores, 0, llave, 0, primarias.cuantos)
        llave[primarias.cuantos] = 1
        System.arraycopy(secundarias.valores, 0, llave, primarias.cuantos + 1, secundarias.cuantos)
        return llave
    }

    /** Compara dos llaves de [llave]: número por número, y la que es el principio de la otra va antes. */
    fun comparar(a: IntArray, b: IntArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            if (a[i] != b[i]) return if (a[i] < b[i]) -1 else 1
        }
        return a.size.compareTo(b.size)
    }

    /** Una lista de enteros que crece. */
    private class Enteros(capacidad: Int) {
        var valores = IntArray(capacidad)
        var cuantos = 0

        fun agregar(valor: Int) {
            if (cuantos == valores.size) valores = valores.copyOf(valores.size * 2)
            valores[cuantos++] = valor
        }
    }
}
