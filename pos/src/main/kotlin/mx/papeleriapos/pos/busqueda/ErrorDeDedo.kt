package mx.papeleriapos.pos.busqueda

import kotlin.math.abs

/**
 * Un error de dedo en la búsqueda. Si nada coincide exacto, cada pedazo de 4 letras o más puede diferir en una letra
 * (cambiada, de más, de menos o dos volteadas) del principio de una palabra o de la palabra entera: «sobre manial» da
 * los sobres manila, en lugar de «no existe» (docs/propuesta-interfaz.md del POS).
 */
object ErrorDeDedo {
    /** El pedazo es el principio de una palabra o, con 4 letras o más, se le parece con una letra de diferencia. */
    fun coincide(pedazo: String, palabras: Iterable<String>): Boolean {
        val conErrores = admiteErrores(pedazo)
        return palabras.any { coincide(pedazo, it, conErrores) }
    }

    /**
     * Lo mismo con una sola palabra: la búsqueda la revisa una vez por cada palabra del catálogo, no por cada producto
     * ([IndiceDeBusqueda]).
     */
    fun coincide(pedazo: String, palabra: String): Boolean = coincide(pedazo, palabra, admiteErrores(pedazo))

    /** Con menos de cuatro letras sobran los parecidos, y los números no tienen errores de dedo. */
    private fun admiteErrores(pedazo: String): Boolean = pedazo.length >= 4 && pedazo.any { it.isLetter() }

    private fun coincide(pedazo: String, palabra: String, conErrores: Boolean): Boolean =
        palabra.startsWith(pedazo) ||
            (
                conErrores &&
                    (
                        difierenEnUna(pedazo, pedazo.length, palabra, minOf(palabra.length, pedazo.length)) ||
                            difierenEnUna(pedazo, pedazo.length, palabra, palabra.length)
                        )
                )

    /** Si a y b difieren en a lo más una letra: cambiada, de más, de menos o dos volteadas («manial» y «manila»). */
    fun difierenEnUna(a: CharSequence, b: CharSequence): Boolean = difierenEnUna(a, a.length, b, b.length)

    /** Con solo el principio de cada uno: los primeros [largoA] de a y los primeros [largoB] de b. */
    private fun difierenEnUna(a: CharSequence, largoA: Int, b: CharSequence, largoB: Int): Boolean {
        if (abs(largoA - largoB) > 1) return false
        var i = 0
        var j = 0
        var diferencias = 0
        while (i < largoA && j < largoB) {
            if (a[i] == b[j]) {
                i++
                j++
                continue
            }
            if (++diferencias > 1) return false
            if (largoA == largoB && i + 1 < largoA && a[i] == b[j + 1] && a[i + 1] == b[j]) {
                i += 2
                j += 2
            } else if (largoA > largoB) {
                i++
            } else if (largoB > largoA) {
                j++
            } else {
                i++
                j++
            }
        }
        return diferencias + (largoA - i) + (largoB - j) <= 1
    }
}
