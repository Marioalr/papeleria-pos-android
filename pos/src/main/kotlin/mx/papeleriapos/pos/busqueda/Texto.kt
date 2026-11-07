package mx.papeleriapos.pos.busqueda

import java.text.Normalizer

/** El texto como lo compara el POS (`Texto` en su núcleo de C#). */
object Texto {
    /**
     * Para comparar sin importar mayúsculas, acentos ni espacios de más: " Pelikán " → "PELIKAN". También sin los acentos
     * sueltos que a veces quedan en un nombre («¨», «´», «`», «^»: 2026-10-24, en tres nombres de la tienda), que si no la
     * búsqueda sin acentos no los encuentra. La ñ se conserva: en español es otra letra, no una n con acento.
     *
     * Carácter por carácter, como en el POS: cada uno se descompone (NFD) y se quedan sus pedazos que no son marcas ni
     * acentos sueltos, en mayúsculas. El POS truena con un emoji (no normaliza medio par); aquí pasa tal cual.
     */
    fun normalizar(texto: String): String {
        val sb = StringBuilder(texto.length)
        var espacioPendiente = false
        for (c in texto) {
            if (ComoNet.esEspacio(c)) {
                espacioPendiente = sb.isNotEmpty()
                continue
            }
            if (c == 'ñ' || c == 'Ñ') {
                espacioPendiente = agregar(sb, 'Ñ', espacioPendiente)
                continue
            }
            if (c.code < 0x80) {
                // El ASCII ya está descompuesto: solo «^» y «`» son acentos sueltos.
                if (!esAcento(c)) espacioPendiente = agregar(sb, ComoNet.mayuscula(c), espacioPendiente)
                continue
            }
            val partes = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
            for (parte in partes) {
                if (!esAcento(parte)) espacioPendiente = agregar(sb, ComoNet.mayuscula(parte), espacioPendiente)
            }
        }
        return sb.toString()
    }

    /** Una marca que va sobre la letra (la tilde de «é») o un acento suelto («´»). */
    private fun esAcento(c: Char): Boolean {
        val tipo = Character.getType(c)
        return tipo == Character.NON_SPACING_MARK.toInt() || tipo == Character.MODIFIER_SYMBOL.toInt()
    }

    /** El espacio que quedó pendiente va antes de la letra que sigue: un acento suelto entre dos espacios no deja dos. */
    private fun agregar(sb: StringBuilder, letra: Char, espacioPendiente: Boolean): Boolean {
        if (espacioPendiente) sb.append(' ')
        sb.append(letra)
        return false
    }
}
