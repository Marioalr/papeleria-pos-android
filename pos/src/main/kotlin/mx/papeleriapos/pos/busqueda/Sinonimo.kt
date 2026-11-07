package mx.papeleriapos.pos.busqueda

/**
 * Un sinónimo de la tienda para la búsqueda: al escribir [palabra] también sale lo que se llama [equivale]. Los decide la
 * tienda, uno por uno, y ninguno viene puesto: un sinónimo mal puesto estorba más de lo que ayuda (en esa tienda «pluma» no
 * es el lapicero). Viajan del POS con el catálogo, ya normalizados (sin acentos y en mayúsculas).
 */
data class Sinonimo(val palabra: String, val equivale: String) {
    companion object {
        /** Una palabra, sin acentos y en mayúsculas; null si no es una sola palabra de 2 letras o más. */
        fun normalizar(texto: String): String? {
            val normal = Texto.normalizar(texto)
            return if (normal.length >= 2 && ' ' !in normal) normal else null
        }
    }
}

/** Los sinónimos de la tienda, listos para buscar: igual en línea y sin conexión. */
class SinonimosDeBusqueda(sinonimos: Iterable<Sinonimo>) {
    /** Lo que vale por cada palabra, en el orden en que llegaron (como el diccionario del POS). */
    private val porPalabra: Map<String, List<String>> =
        sinonimos.groupBy({ it.palabra }, { it.equivale }).mapValues { (_, equivalen) -> equivalen.distinct() }

    /**
     * Lo que también vale por un pedazo escrito: los sinónimos de esa palabra y, con 4 letras o más, de las palabras que
     * empiezan así («bolig» toma los de «boligrafo»). Cada uno con la palabra de la tienda, para decir por qué salió.
     */
    fun de(pedazo: String): List<Sinonimo> {
        val salida = ArrayList<Sinonimo>()
        for ((palabra, equivalen) in porPalabra) {
            if (palabra == pedazo || (pedazo.length >= 4 && palabra.startsWith(pedazo))) {
                for (equivale in equivalen) salida.add(Sinonimo(palabra, equivale))
            }
        }
        return salida
    }

    companion object {
        val NINGUNO = SinonimosDeBusqueda(emptyList())
    }
}
