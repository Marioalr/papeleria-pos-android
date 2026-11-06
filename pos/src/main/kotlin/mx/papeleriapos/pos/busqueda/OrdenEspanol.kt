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
 * misma llave.
 */
internal object OrdenEspanol {
    private val rangos = TablaDeOrden.RANGOS

    /** Los elementos de cada punto de cada rango (primaria y secundaria, alternadas); null si ICU no lo tiene. */
    private val elementos: Array<Array<IntArray?>>

    /** Las contracciones, por los dos caracteres (el primero * 0x10000 + el segundo). */
    private val contracciones = HashMap<Int, IntArray>()

    /** Los caracteres con que empieza alguna contracción: solo con ellos se mira el que sigue. */
    private val empiezanContraccion = HashSet<Int>()

    init {
        val valorDe = IntArray(128) { -1 }
        TablaDeOrden.ALFABETO.forEachIndexed { i, c -> valorDe[c.code] = i }
        val texto = TablaDeOrden.ELEMENTOS
        var k = 0
        elementos = Array(rangos.size / 2) { r ->
            Array(rangos[2 * r + 1] - rangos[2 * r] + 1) {
                val cuantos = valorDe[texto[k++].code]
                if (cuantos == TablaDeOrden.ALFABETO.length - 1) {
                    null
                } else {
                    IntArray(2 * cuantos).also { ces ->
                        for (e in 0 until cuantos) {
                            val valor = (valorDe[texto[k].code] shl 12) or (valorDe[texto[k + 1].code] shl 6) or valorDe[texto[k + 2].code]
                            k += 3
                            ces[2 * e] = valor shr 6
                            ces[2 * e + 1] = valor and 63
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
            if (punto < desde) return null
            if (punto <= rangos[2 * r + 1]) return elementos[r][punto - desde]
        }
        return null
    }

    /** La llave de [texto] para ordenar por nombre (ver la clase). */
    fun llave(texto: String): IntArray {
        val primarias = Enteros(texto.length + 2)
        val secundarias = Enteros(texto.length + 2)
        var i = 0
        while (i < texto.length) {
            val punto = Character.codePointAt(texto, i)
            var largo = Character.charCount(punto)
            var ces = elementosDe(punto)
            // La ñ escrita como n con tilde y la «l·» se leen juntas, como en ICU.
            if (punto in empiezanContraccion && i + largo < texto.length) {
                val junta = contracciones[punto * 0x10000 + texto[i + largo].code]
                if (junta != null) {
                    ces = junta
                    largo++
                }
            }
            if (ces == null) {
                // Lo que la tabla no tiene va después de todo lo que sí, en el orden de su número.
                primarias.agregar(TablaDeOrden.SIN_TABLA + punto)
                secundarias.agregar(TablaDeOrden.COMUN)
            } else {
                for (e in 0 until ces.size / 2) {
                    if (ces[2 * e] != 0) primarias.agregar(ces[2 * e])
                    if (ces[2 * e + 1] != 0) secundarias.agregar(ces[2 * e + 1])
                }
            }
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
