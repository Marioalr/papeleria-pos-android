package mx.papeleriapos.escaner.nucleo

/**
 * Los códigos de fábrica (GTIN): EAN-8, UPC-A (12), EAN-13 e ITF-14, todos con un dígito verificador al final con pesos
 * 3 y 1 desde la derecha. Es la misma regla que `Ean13` del POS, generalizada a cualquier largo.
 */
object Gtin {
    /** El dígito verificador de [digitos] (sin el verificador). */
    fun digitoVerificador(digitos: CharSequence): Int {
        require(digitos.isNotEmpty() && digitos.all { it in '0'..'9' }) { "Solo se admiten dígitos." }
        var suma = 0
        // Desde la derecha: el último dígito antes del verificador pesa 3, el anterior 1, y así.
        for (i in digitos.indices) {
            val desdeLaDerecha = digitos.length - 1 - i
            suma += (digitos[i] - '0') * if (desdeLaDerecha % 2 == 0) 3 else 1
        }
        return (10 - suma % 10) % 10
    }

    fun completar(digitos: String): String = digitos + digitoVerificador(digitos)

    /** Si [codigo] es un GTIN de 8, 12, 13 o 14 dígitos con su verificador correcto. */
    fun esValido(codigo: String): Boolean =
        codigo.length in LARGOS && codigo.all { it in '0'..'9' } &&
            digitoVerificador(codigo.substring(0, codigo.length - 1)) == codigo.last() - '0'

    /**
     * Un UPC-E (8 dígitos: sistema 0 o 1, seis dígitos y verificador) a su UPC-A de 12. Devuelve null si no es un UPC-E
     * válido.
     */
    fun upcEaUpcA(upcE: String): String? {
        if (upcE.length != 8 || !upcE.all { it in '0'..'9' }) return null
        val sistema = upcE[0]
        if (sistema != '0' && sistema != '1') return null
        val d = upcE.substring(1, 7)
        val cuerpo = when (d[5]) {
            '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            '3' -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        val upcA = "$sistema$cuerpo${upcE[7]}"
        return if (esValido(upcA)) upcA else null
    }

    /** Los EAN-13 que empiezan con 2 son de uso interno de cada tienda (el POS genera los suyos así). */
    fun esInterno(codigo: String): Boolean = codigo.length == 13 && codigo[0] == '2' && esValido(codigo)

    private val LARGOS = setOf(8, 12, 13, 14)
}
