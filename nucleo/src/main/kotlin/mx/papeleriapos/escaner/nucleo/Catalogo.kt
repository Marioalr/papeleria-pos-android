package mx.papeleriapos.escaner.nucleo

import java.util.Locale

/**
 * Cómo coincidió una lectura con una clave del catálogo (docs/escaner.md, «Búsqueda en el catálogo»), en el orden en que
 * se prueba.
 */
enum class Variante {
    /** El código tal cual. */
    TAL_CUAL,
    /** Un UPC-A de 12 que empieza con 0, sin ese 0 (11 dígitos). */
    UPC_A_11,
    /** Un EAN-13 que empieza con 0, como UPC-A de 12 (sin ese 0). */
    EAN13_A_UPC_A,
    /** Un EAN-13 que empieza con 00, como UPC-A de 11 (sin los dos ceros). */
    EAN13_A_UPC_A_11,
    /** Un UPC-E expandido a su UPC-A de 12. */
    UPC_E_A_UPC_A,
}

/** La clave del catálogo que coincidió y por qué variante. */
data class Coincidencia(val clave: String, val variante: Variante)

/**
 * Los códigos de un catálogo (el de ejemplo o el que exporta el dueño), en un HashSet: buscar cuesta microsegundos. La
 * búsqueda es la misma regla que usará el servicio del POS (docs/escaner.md, «Búsqueda en el catálogo»): el código tal
 * cual y, según su simbología, sus variantes de UPC, en ese orden y nada más. Un código del catálogo con el dígito
 * verificador mal nunca coincide con una lectura válida: es un hallazgo del catálogo y no se arregla.
 */
class Catalogo private constructor(private val claves: HashSet<String>) {
    /** Cuántos códigos distintos trae. */
    val tamano: Int get() = claves.size

    operator fun contains(clave: String): Boolean = clave in claves

    /**
     * La regla sobre un texto con su simbología, como lo mandaría un lector: el EAN-13 con 13 dígitos, el UPC-A con 12 y
     * el UPC-E con 8. Prueba, en este orden: tal cual; el UPC-A de 12 que empieza con 0 sin ese 0; el EAN-13 que empieza
     * con 0 como UPC-A de 12 y luego de 11; el UPC-E expandido a UPC-A de 12. null si nada coincide o si [texto] no es
     * una lectura válida de [simbologia] (largo y dígito verificador): una lectura sin verificar no se busca.
     */
    fun buscar(texto: String, simbologia: Simbologia): Coincidencia? {
        if (!esLecturaValida(texto, simbologia)) return null
        if (texto in claves) return Coincidencia(texto, Variante.TAL_CUAL)
        when (simbologia) {
            Simbologia.UPC_A -> if (texto[0] == '0') {
                probar(texto.substring(1), Variante.UPC_A_11)?.let { return it }
            }
            Simbologia.EAN_13 -> if (texto[0] == '0') {
                val upcA = texto.substring(1)
                probar(upcA, Variante.EAN13_A_UPC_A)?.let { return it }
                if (upcA[0] == '0') probar(upcA.substring(1), Variante.EAN13_A_UPC_A_11)?.let { return it }
            }
            Simbologia.UPC_E -> {
                val upcA = Gtin.upcEaUpcA(texto) ?: return null
                probar(upcA, Variante.UPC_E_A_UPC_A)?.let { return it }
            }
            else -> Unit
        }
        return null
    }

    /** Una lectura aceptada: la regla sobre su [formaDeBusqueda], igual venga de ML Kit o de zxing-cpp. */
    fun buscar(codigo: CodigoLeido): Coincidencia? = buscarNormalizado(codigo.normalizado, codigo.simbologia)

    /** Como [buscar] con el código normalizado y su simbología (lo que guardan las listas de venta y conteo). */
    fun buscarNormalizado(normalizado: String, simbologia: Simbologia): Coincidencia? {
        val (texto, s) = formaDeBusqueda(normalizado, simbologia) ?: return null
        return buscar(texto, s)
    }

    private fun probar(clave: String, variante: Variante): Coincidencia? =
        if (clave in claves) Coincidencia(clave, variante) else null

    companion object {
        val VACIO = Catalogo(HashSet())

        /**
         * Un catálogo desde líneas de texto: la primera columna es el código (hasta la primera coma, punto y coma o
         * tabulador), sin comillas ni espacios alrededor; el primer renglón es encabezado si su primera columna empieza
         * con «codigo», «código», «clave», «code» o «barcode»; las líneas vacías se saltan y un código repetido cuenta
         * una vez. El código se guarda como viene: no se valida ni se corrige.
         */
        fun deLineas(lineas: Sequence<String>): Catalogo {
            val claves = HashSet<String>()
            var primera = true
            for (linea in lineas) {
                val codigo = primeraColumna(if (primera) linea.removePrefix(BOM) else linea)
                if (codigo.isEmpty()) continue
                val encabezado = primera && esEncabezado(codigo)
                primera = false
                if (!encabezado) claves.add(codigo)
            }
            return Catalogo(claves)
        }

        fun deTexto(texto: String): Catalogo = deLineas(texto.lineSequence())

        /**
         * El texto y la simbología con que se busca una lectura aceptada (docs/escaner.md, «Búsqueda en el catálogo»): la
         * forma que dan las barras y que da ML Kit, para que el resultado no dependa del motor. Se arma desde el
         * normalizado, que es igual con los dos:
         *
         * - UPC-A: 12 dígitos. zxing-cpp lo da con 13 («0…»), ML Kit con 12. Un EAN-13 que empieza con 0 tiene las mismas
         *   barras que un UPC-A (ML Kit lo entrega siempre como UPC-A): también se busca como UPC-A de 12.
         * - UPC-E: sus 8 dígitos. zxing-cpp lo da expandido a 13, ML Kit con 8.
         * - Lo demás, el normalizado: EAN-13 (sin 0 adelante), EAN-8, ITF-14, Code 128 sin prefijo ni caracteres de
         *   control y QR.
         *
         * null si no es una forma que la normalización entregue.
         */
        internal fun formaDeBusqueda(normalizado: String, simbologia: Simbologia): Pair<String, Simbologia>? {
            if (simbologia != Simbologia.EAN_13 && simbologia != Simbologia.UPC_A && simbologia != Simbologia.UPC_E) {
                return normalizado to simbologia
            }
            if (normalizado.length != 13) return null
            if (normalizado[0] != '0') return if (simbologia == Simbologia.UPC_E) null else normalizado to Simbologia.EAN_13
            val upcA = normalizado.substring(1)
            if (simbologia != Simbologia.UPC_E) return upcA to Simbologia.UPC_A
            // Un UPC-E siempre tiene su forma corta; si no la tuviera, se busca como el UPC-A que es.
            return Normalizacion.upcAaUpcE(upcA)?.let { it to Simbologia.UPC_E } ?: (upcA to Simbologia.UPC_A)
        }

        /** Si [texto] es lo que entrega un lector de [simbologia] ya validado: largo, solo dígitos y verificador. */
        private fun esLecturaValida(texto: String, simbologia: Simbologia): Boolean = when (simbologia) {
            Simbologia.EAN_13 -> texto.length == 13 && Gtin.esValido(texto)
            Simbologia.UPC_A -> texto.length == 12 && Gtin.esValido(texto)
            Simbologia.UPC_E -> Gtin.upcEaUpcA(texto) != null
            Simbologia.EAN_8 -> texto.length == 8 && Gtin.esValido(texto)
            Simbologia.ITF -> texto.length == 14 && Gtin.esValido(texto)
            Simbologia.CODE_128, Simbologia.QR -> texto.isNotEmpty()
            // Las que la app no acepta (verificación débil o no se usan en la tienda).
            else -> false
        }

        private fun primeraColumna(linea: String): String {
            var fin = linea.length
            for (i in linea.indices) {
                val c = linea[i]
                if (c == ',' || c == ';' || c == '\t') {
                    fin = i
                    break
                }
            }
            return linea.substring(0, fin).trim().trim('"').trim()
        }

        private fun esEncabezado(columna: String): Boolean {
            val s = columna.lowercase(Locale.ROOT).replace('ó', 'o')
            return ENCABEZADOS.any { s.startsWith(it) }
        }

        private const val BOM = "﻿"
        private val ENCABEZADOS = listOf("codigo", "clave", "code", "barcode")
    }
}
