package mx.papeleriapos.pos.busqueda

/** Un valor de una familia: el producto, cómo se lee («Negro», «0.7mm P/Fino») y cómo se compara. */
data class ValorDeFamilia<T>(val elemento: T, val texto: String, val clave: String)

/**
 * Un renglón de la lista: una familia (lo que solo cambia en [eje], con un valor por producto) o un producto suelto (sin
 * eje y con un solo valor).
 *
 * @param titulo El nombre del más vendido sin lo que cambia.
 * @param masVendido El valor que agrega Enter.
 */
data class RenglonDeFamilia<T>(
    val eje: EjeFamilia?,
    val titulo: String?,
    val valores: List<ValorDeFamilia<T>>,
    val masVendido: Int,
) {
    val esFamilia: Boolean get() = eje != null

    val elegido: T get() = valores[masVendido].elemento
}

/**
 * @param eje Lo que se despliega; null si nada se junta.
 * @param ejes Lo que cambia entre los resultados, en orden: lo que se puede desplegar.
 */
data class AgrupacionDeFamilias<T>(val eje: EjeFamilia?, val ejes: List<EjeFamilia>, val renglones: List<RenglonDeFamilia<T>>)

/**
 * Junta los resultados de una búsqueda en familias: lo que solo cambia en un eje va en un renglón. El eje es el primero
 * que cambia entre los resultados (color, medida, marca, contenido; en cuadernos, el rayado primero), o el que se eligió.
 * Con «lapicero negro» el color ya no cambia: se despliega la medida.
 */
object Familias {
    private val ORDEN = listOf(EjeFamilia.COLOR, EjeFamilia.MEDIDA, EjeFamilia.MARCA, EjeFamilia.CONTENIDO, EjeFamilia.RAYADO)

    /** Un resultado con su nombre en pedazos. */
    private class Leido<T>(val elemento: T, val partes: List<ParteDeNombre>)

    /**
     * @param elementos En el orden de la búsqueda: los renglones salen en el orden de su primer producto.
     * @param vendidos Cuántas veces se vendió: en cada familia, Enter agrega el más vendido.
     * @param preferido El eje que se eligió; si no cambia entre estos resultados, el primero que sí.
     */
    fun <T> agrupar(
        elementos: List<T>,
        partes: (T) -> List<ParteDeNombre>,
        vendidos: (T) -> Int,
        preferido: EjeFamilia? = null,
    ): AgrupacionDeFamilias<T> {
        val leidos = elementos.map { Leido(it, partes(it)) }
        val producto = leidos.firstOrNull()?.partes?.firstOrNull()?.clave
        // En cuadernos, el rayado primero (y lo demás en su orden).
        val orden = if (producto != null && esCuaderno(producto)) ORDEN.sortedBy { if (it == EjeFamilia.RAYADO) 0 else 1 } else ORDEN
        val ejes = orden.filter { eje ->
            grupos(leidos, eje).any { g -> g.size >= 2 && g.map { clave(it.partes, eje) }.distinct().size >= 2 }
        }
        val elegido = if (preferido != null && preferido in ejes) preferido else ejes.firstOrNull()

        val renglones = ArrayList<Pair<Int, RenglonDeFamilia<T>>>()
        val enFamilia = HashSet<Int>()
        if (elegido != null) {
            for (grupo in grupos(leidos, elegido).filter { it.size >= 2 }) {
                val indices = grupo.map { lugar(leidos, it) }
                enFamilia.addAll(indices)
                // El más vendido; si empatan, el que salió antes en la búsqueda.
                val masVendido = grupo.sortedWith(compareByDescending<Leido<T>> { vendidos(it.elemento) }.thenBy { lugar(leidos, it) })[0]
                val valores = ordenarValores(grupo, elegido, vendidos)
                    .map { ValorDeFamilia(it.elemento, valor(it.partes, elegido), clave(it.partes, elegido)) }
                val indice = valores.indexOfFirst { it.elemento == masVendido.elemento }
                renglones.add(indices.min() to RenglonDeFamilia(elegido, titulo(masVendido.partes, elegido), valores, indice))
            }
        }
        for (k in leidos.indices) {
            if (k !in enFamilia) renglones.add(k to RenglonDeFamilia(null, null, listOf(ValorDeFamilia(leidos[k].elemento, "", "")), 0))
        }
        return AgrupacionDeFamilias(elegido, ejes, renglones.sortedBy { it.first }.map { it.second })
    }

    /** El nombre sin lo que cambia: «Lapicero 0.7mm P/Fino Azor» de «Lapicero 0.7mm Negro P/Fino Azor». */
    fun titulo(partes: List<ParteDeNombre>, eje: EjeFamilia): String = partes.filter { it.eje != eje }.joinToString(" ") { it.texto }

    /** Lo que cambia, como se escribió: «Negro», «Colores Surt.», «0.7mm P/Fino». */
    fun valor(partes: List<ParteDeNombre>, eje: EjeFamilia): String = partes.filter { it.eje == eje }.joinToString(" ") { it.texto }

    fun clave(partes: List<ParteDeNombre>, eje: EjeFamilia): String =
        partes.filter { it.eje == eje && it.clave.isNotEmpty() }.joinToString(" ") { it.clave }

    /**
     * El lugar de un resultado como lo da `IndexOf` en el POS: el primero igual a él (el mismo elemento, con la misma lista
     * de pedazos). Un producto que viene dos veces tal cual cuenta como el primero, igual que allá.
     */
    private fun <T> lugar(leidos: List<Leido<T>>, leido: Leido<T>): Int =
        leidos.indexOfFirst { it === leido || (it.elemento == leido.elemento && it.partes === leido.partes) }

    private fun esCuaderno(producto: String): Boolean =
        producto == "CUADERNO" || producto == "CUADERNOS" || producto == "LIBRETA" || producto == "LIBRETAS" ||
            producto == "BLOCK" || producto == "BLOC"

    /** Los que son iguales en todo menos en el eje (sin importar el orden de las palabras). El que no tiene ese eje va solo. */
    private fun <T> grupos(leidos: List<Leido<T>>, eje: EjeFamilia): List<List<Leido<T>>> =
        leidos.filter { clave(it.partes, eje).isNotEmpty() }
            .groupBy { x ->
                x.partes.filter { it.eje != eje && it.clave.isNotEmpty() }
                    .map { "${nombreEnElPos(it.eje)}:${it.clave}" }
                    .sorted()
                    .joinToString("|")
            }
            .values.toList()

    /** El nombre del eje como lo escribe el POS al juntar («Color»; vacío si no tiene): así la llave de cada grupo es la misma. */
    private fun nombreEnElPos(eje: EjeFamilia?): String = when (eje) {
        null -> ""
        EjeFamilia.COLOR -> "Color"
        EjeFamilia.MEDIDA -> "Medida"
        EjeFamilia.MARCA -> "Marca"
        EjeFamilia.CONTENIDO -> "Contenido"
        EjeFamilia.RAYADO -> "Rayado"
    }

    /** Color y marca, del más vendido al menos (el surtido al final); medida y contenido, de menor a mayor. */
    private fun <T> ordenarValores(grupo: List<Leido<T>>, eje: EjeFamilia, vendidos: (T) -> Int): List<Leido<T>> {
        val porMedida = Comparator<Leido<T>> { x, y ->
            ComoNet.comparar(LectorDeNombres.ordenDeMedida(clave(x.partes, eje)), LectorDeNombres.ordenDeMedida(clave(y.partes, eje)))
        }
        return when (eje) {
            EjeFamilia.MEDIDA -> grupo.sortedWith(porMedida.thenBy { clave(it.partes, eje) })
            EjeFamilia.CONTENIDO -> grupo.sortedWith(porMedida)
            else -> grupo.sortedWith(compareBy<Leido<T>> { clave(it.partes, eje) == "SURTIDO" }.thenByDescending { vendidos(it.elemento) })
        }
    }
}
