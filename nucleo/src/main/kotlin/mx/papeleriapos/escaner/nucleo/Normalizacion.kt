package mx.papeleriapos.escaner.nucleo

/**
 * Validación y forma canónica de lo que entrega un motor (docs/escaner.md, «Validación y consenso»). Los motores no
 * coinciden: ML Kit da el UPC-A con 12 dígitos y el UPC-E con 8 sin expandir; zxing-cpp da ambos con 13. Todo lo de
 * fábrica se lleva a GTIN-13 (o 8/14), igual con los dos. La búsqueda en el catálogo parte de esa forma ([Catalogo]).
 */
internal object Normalizacion {
    /** Valida y normaliza; null si no es válida o si la simbología no se espera en la papelería. */
    fun normalizar(lectura: Lectura): CodigoLeido? {
        val v = lectura.texto
        val s = lectura.simbologia
        return when (s) {
            Simbologia.EAN_13 -> if (v.length == 13 && Gtin.esValido(v)) gtin13(v, s, v) else null
            Simbologia.UPC_A -> when {
                v.length == 12 && Gtin.esValido(v) -> gtin13("0$v", s, v)
                v.length == 13 && v[0] == '0' && Gtin.esValido(v) -> gtin13(v, s, v)
                else -> null
            }
            Simbologia.UPC_E -> when (v.length) {
                8 -> Gtin.upcEaUpcA(v)?.let { upcA -> CodigoLeido(v, "0$upcA", s, listOf(v, upcA, "0$upcA")) }
                // zxing-cpp ya lo entrega expandido a 13.
                13 -> if (v[0] == '0' && Gtin.esValido(v)) gtin13(v, s, v) else null
                12 -> if (Gtin.esValido(v)) gtin13("0$v", s, v) else null
                else -> null
            }
            Simbologia.EAN_8 -> if (v.length == 8 && Gtin.esValido(v)) CodigoLeido(v, v, s, listOf(v)) else null
            // ML Kit no valida ITF: llegó a leer 40 dígitos de algo que no era código. Solo ITF-14 con verificador.
            Simbologia.ITF -> if (v.length == 14 && Gtin.esValido(v)) {
                // Un GTIN-14 que empieza con 0 es el mismo artículo que su GTIN-13.
                CodigoLeido(v, v, s, if (v[0] == '0') listOf(v, v.substring(1)) else listOf(v))
            } else {
                null
            }
            Simbologia.CODE_128 -> limpiar128(v)?.let { CodigoLeido(v, it, s, listOf(it)) }
            Simbologia.QR -> if (v.isNotEmpty()) CodigoLeido(v, v, s, listOf(v)) else null
            // Code 39, Codabar y compañía tienen verificación débil o no se usan en la tienda.
            else -> null
        }
    }

    /**
     * Un GTIN-13 (que pasó la validación) con sus claves: primero como se leyó; si empieza con 0, también el UPC-A de
     * 12 y, si se puede, el UPC-E de 8.
     */
    private fun gtin13(g: String, s: Simbologia, original: String): CodigoLeido {
        val claves = ArrayList<String>(3)
        claves.add(original)
        if (g != original) claves.add(g)
        if (g[0] == '0') {
            val upcA = g.substring(1)
            if (upcA != original) claves.add(upcA)
            if (s == Simbologia.UPC_E) upcAaUpcE(upcA)?.let { if (it != original) claves.add(it) }
        }
        return CodigoLeido(original, g, s, claves)
    }

    /** El prefijo de símbolo `]C1` (GS1-128) y los caracteres de control (FNC1 como GS) no son parte de la clave. */
    private fun limpiar128(v: String): String? {
        val sinPrefijo = if (v.startsWith("]C1")) v.substring(3) else v
        val limpio = buildString(sinPrefijo.length) {
            for (c in sinPrefijo) if (c >= ' ' && c != '\u007F') append(c)
        }
        return limpio.ifEmpty { null }
    }

    /**
     * El UPC-E de 8 dígitos que se expande al [upcA] de 12, o null si no tiene forma corta. Se prueba cada patrón de
     * compresión y se confirma expandiendo de vuelta.
     */
    fun upcAaUpcE(upcA: String): String? {
        if (upcA.length != 12 || (upcA[0] != '0' && upcA[0] != '1') || !Gtin.esValido(upcA)) return null
        val b = upcA.substring(1, 11)
        val candidatos = listOf(
            // Fabricante X X {0,1,2} 0 0, producto 0 0 P P P.
            "${b[0]}${b[1]}${b[7]}${b[8]}${b[9]}${b[2]}",
            // Fabricante X X X 0 0, producto 0 0 0 P P.
            "${b[0]}${b[1]}${b[2]}${b[8]}${b[9]}3",
            // Fabricante X X X X 0, producto 0 0 0 0 P.
            "${b[0]}${b[1]}${b[2]}${b[3]}${b[9]}4",
            // Fabricante X X X X X, producto 0 0 0 0 {5..9}.
            "${b[0]}${b[1]}${b[2]}${b[3]}${b[4]}${b[9]}",
        )
        for (medio in candidatos) {
            val upcE = "${upcA[0]}$medio${upcA[11]}"
            if (Gtin.upcEaUpcA(upcE) == upcA) return upcE
        }
        return null
    }
}
