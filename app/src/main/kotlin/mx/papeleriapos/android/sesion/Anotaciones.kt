package mx.papeleriapos.android.sesion

import mx.papeleriapos.android.csv.Csv

/** Una condición que rige desde el intento [desde] hasta que se elija otra. */
data class Bloque(val desde: Int, val condicion: Condicion)

/**
 * Lo que el operador anota sobre la sesión además de los intentos: la condición de cada bloque (se elige antes de
 * escanearlo y rige para los intentos que siguen) y una nota libre por intento. Es inmutable y va aparte de
 * [SesionPrueba] para poder anotar sin tocar el registro de lecturas; [CsvSesion] las junta al escribir.
 */
data class Anotaciones(
    /** El inicio de la sesión a la que pertenecen; otra sesión empieza sin anotaciones. */
    val inicioSesion: Long = 0L,
    /** En orden de [Bloque.desde]. */
    val bloques: List<Bloque> = emptyList(),
    val notas: Map<Int, String> = emptyMap(),
) {
    /** La que se eligió al último: la de los intentos que vienen. */
    val condicionActual: Condicion get() = bloques.lastOrNull()?.condicion ?: Condicion.NORMAL

    fun condicionDe(n: Int): Condicion = bloques.lastOrNull { it.desde <= n }?.condicion ?: Condicion.NORMAL

    fun notaDe(n: Int): String = notas[n] ?: ""

    /** Las de [sesion]; si eran de otra sesión (o no hay), empieza de cero. */
    fun de(sesion: SesionPrueba?): Anotaciones = when {
        sesion == null -> Anotaciones()
        sesion.inicio == inicioSesion -> this
        else -> Anotaciones(inicioSesion = sesion.inicio)
    }

    /** Elige la condición de los intentos que siguen a partir de [siguiente] (el número del próximo intento). */
    fun conCondicion(condicion: Condicion, siguiente: Int): Anotaciones {
        val ultimo = bloques.lastOrNull()
        return when {
            ultimo != null && ultimo.desde >= siguiente ->
                // Todavía no hay intentos en el bloque: se cambia en lugar de abrir otro (y si queda igual al anterior, se une).
                copy(bloques = (bloques.dropLast(1) + Bloque(siguiente, condicion)).unidos())
            condicionActual == condicion -> this
            else -> copy(bloques = bloques + Bloque(siguiente, condicion))
        }
    }

    /** Pone (o borra, si queda vacía) la nota del intento [n]. */
    fun conNota(n: Int, nota: String): Anotaciones {
        val limpia = nota.trim()
        return if (limpia.isEmpty()) copy(notas = notas - n) else copy(notas = notas + (n to limpia))
    }

    /**
     * Se quitó el intento [n] (el último: «Deshacer» de un fallo). Su número lo toma el siguiente, así que la condición
     * elegida después de él (un bloque que empezaba en n + 1) rige desde [n]; su nota se borra.
     */
    fun alQuitarIntento(n: Int): Anotaciones {
        val movidos = bloques.map { if (it.desde > n) it.copy(desde = n) else it }
        // Si quedan varios en el mismo número, rige el último que se eligió.
        val unicos = movidos.groupBy { it.desde }.map { (_, mismos) -> mismos.last() }.sortedBy { it.desde }
        return copy(bloques = unicos.unidos(), notas = notas - n)
    }

    private fun List<Bloque>.unidos(): List<Bloque> {
        val salida = ArrayList<Bloque>(size)
        for (b in this) {
            val previa = salida.lastOrNull()?.condicion ?: Condicion.NORMAL
            if (b.condicion != previa) salida += b
        }
        return salida
    }

    /** Para guardarlas: un CSV de tres columnas (tipo, n, valor). */
    fun aTexto(): String {
        val sb = StringBuilder()
        Csv.renglon(listOf("tipo", "n", "valor"), sb)
        Csv.renglon(listOf("sesion", "0", inicioSesion.toString()), sb)
        for (b in bloques) Csv.renglon(listOf("bloque", b.desde.toString(), b.condicion.name), sb)
        for ((n, nota) in notas.toSortedMap()) Csv.renglon(listOf("nota", n.toString(), nota), sb)
        return sb.toString()
    }

    companion object {
        /** Lo contrario de [aTexto]; lo que no se entiende se salta. */
        fun deTexto(texto: String): Anotaciones {
            var inicio = 0L
            val bloques = ArrayList<Bloque>()
            val notas = LinkedHashMap<Int, String>()
            for (r in Csv.leer(texto).drop(1)) {
                if (r.size < 3) continue
                val n = r[1].toIntOrNull() ?: continue
                when (r[0]) {
                    "sesion" -> inicio = r[2].toLongOrNull() ?: 0L
                    "bloque" -> Condicion.entries.firstOrNull { it.name == r[2] }?.let { bloques += Bloque(n, it) }
                    "nota" -> if (r[2].isNotEmpty()) notas[n] = r[2]
                }
            }
            return Anotaciones(inicio, bloques.sortedBy { it.desde }, notas)
        }
    }
}
