package mx.papeleriapos.android.banco

/** Mediana y p95 de una serie de tiempos, en ms. */
data class Estadistica(val n: Int, val mediana: Double, val p95: Double) {
    companion object {
        /** null si no hay datos. */
        fun de(valores: Collection<Double>): Estadistica? {
            if (valores.isEmpty()) return null
            val ordenados = valores.toDoubleArray().also { it.sort() }
            return Estadistica(ordenados.size, percentil(ordenados, 50.0), percentil(ordenados, 95.0))
        }

        /**
         * El percentil [p] (0 a 100) de valores ya ordenados, interpolando entre los dos vecinos (el método por omisión
         * de numpy y de Excel PERCENTIL.INC), para que los números coincidan al revisarlos en la computadora.
         */
        fun percentil(ordenados: DoubleArray, p: Double): Double {
            require(ordenados.isNotEmpty()) { "Sin datos." }
            require(p in 0.0..100.0) { "Percentil fuera de rango: $p" }
            val posicion = p / 100.0 * (ordenados.size - 1)
            val abajo = posicion.toInt()
            val arriba = minOf(abajo + 1, ordenados.size - 1)
            val fraccion = posicion - abajo
            return ordenados[abajo] + (ordenados[arriba] - ordenados[abajo]) * fraccion
        }
    }
}
