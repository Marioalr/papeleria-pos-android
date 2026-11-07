package mx.papeleriapos.pos.busqueda

/**
 * Lo que .NET hace distinto de Kotlin con el texto, para que la búsqueda dé lo mismo que la del POS: qué es un espacio
 * (`char.IsWhiteSpace`, que no es `Char.isWhitespace`), recortar las orillas (`Trim`), las mayúsculas y minúsculas
 * invariantes y comparar sin importar mayúsculas (`OrdinalIgnoreCase`). Todo es carácter por carácter, como allá.
 */
internal object ComoNet {
    /**
     * `char.IsWhiteSpace`: los de Unicode (White_Space). Kotlin cuenta también los separadores de información
     * (U+001C a U+001F) y no cuenta U+0085; .NET al revés.
     */
    fun esEspacio(c: Char): Boolean {
        val n = c.code
        return when {
            n <= 0x20 -> n == 0x20 || n in 0x09..0x0D
            n < 0x85 -> false
            n == 0x85 || n == 0xA0 || n == 0x1680 -> true
            n < 0x2000 -> false
            n <= 0x200A || n == 0x2028 || n == 0x2029 || n == 0x202F || n == 0x205F || n == 0x3000 -> true
            else -> false
        }
    }

    /** `Trim()`: sin los espacios ([esEspacio]) de las dos orillas. */
    fun recortar(texto: String): String {
        var desde = 0
        var hasta = texto.length
        while (desde < hasta && esEspacio(texto[desde])) desde++
        while (hasta > desde && esEspacio(texto[hasta - 1])) hasta--
        return if (desde == 0 && hasta == texto.length) texto else texto.substring(desde, hasta)
    }

    /** `TrimEnd()`: sin los espacios ([esEspacio]) del final. */
    fun recortarFinal(texto: String): String {
        var hasta = texto.length
        while (hasta > 0 && esEspacio(texto[hasta - 1])) hasta--
        return if (hasta == texto.length) texto else texto.substring(0, hasta)
    }

    /** `char.ToUpperInvariant`: la mayúscula simple de Unicode, salvo la ı sin punto, que se queda (como en Windows). */
    fun mayuscula(c: Char): Char = if (c.code == 0x131) c else Character.toUpperCase(c)

    /** `ToLowerInvariant()`: carácter por carácter, salvo la İ con punto, que se queda (como en Windows). */
    fun minusculas(texto: String): String {
        var cambia = false
        for (c in texto) {
            if (c.code != 0x130 && Character.toLowerCase(c) != c) {
                cambia = true
                break
            }
        }
        if (!cambia) return texto
        val sb = StringBuilder(texto.length)
        for (c in texto) sb.append(if (c.code == 0x130) c else Character.toLowerCase(c))
        return sb.toString()
    }

    /**
     * La mayúscula con que compara `OrdinalIgnoreCase`: la simple de Unicode, salvo la ı sin punto y la ſ larga, que se
     * quedan (así no se confunden con la I y la S).
     */
    private fun mayusculaOrdinal(c: Char): Char = if (c.code == 0x131 || c.code == 0x17F) c else Character.toUpperCase(c)

    /** `string.Equals(a, b, StringComparison.OrdinalIgnoreCase)`. */
    fun igualesSinMayusculas(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x != y && mayusculaOrdinal(x) != mayusculaOrdinal(y)) return false
        }
        return true
    }

    /** Lo mismo para los que son iguales sin importar mayúsculas (`StringComparer.OrdinalIgnoreCase`): para quitar repetidos. */
    fun claveSinMayusculas(texto: String): String {
        val sb = StringBuilder(texto.length)
        for (c in texto) sb.append(mayusculaOrdinal(c))
        return sb.toString()
    }

    /** `Split(' ', RemoveEmptyEntries)`: los pedazos entre espacios (solo el espacio), sin los vacíos. */
    fun pedazos(texto: String): List<String> {
        val salida = ArrayList<String>()
        var desde = 0
        while (desde <= texto.length) {
            var hasta = texto.indexOf(' ', desde)
            if (hasta < 0) hasta = texto.length
            if (hasta > desde) salida.add(texto.substring(desde, hasta))
            desde = hasta + 1
        }
        return salida
    }

    /**
     * La comparación de `double` de .NET (`CompareTo`, la de LINQ): NaN va antes de todo y -0.0 es igual a 0.0. La de
     * Kotlin pone NaN al final y -0.0 antes que 0.0.
     */
    fun comparar(a: Double, b: Double): Int = when {
        a < b -> -1
        a > b -> 1
        a == b -> 0
        a.isNaN() -> if (b.isNaN()) 0 else -1
        else -> 1
    }
}
