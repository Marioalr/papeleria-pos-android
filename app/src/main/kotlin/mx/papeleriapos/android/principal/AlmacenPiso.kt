package mx.papeleriapos.android.principal

import mx.papeleriapos.android.botonrapido.Lugar
import mx.papeleriapos.android.csv.Csv

/**
 * Lo que la pantalla principal recuerda entre aperturas. Va por celular mientras la app no sepa quién la usa (en la
 * propuesta va por persona: el celular de la tienda lo usan varios por turnos; llega con la entrega 2).
 */
interface AlmacenPiso {
    /** El último modo usado (la app abre en él), o null si nunca se eligió uno (una app que viene de la 0.1). */
    fun leerModo(): ModoApp?
    fun guardarModo(modo: ModoApp)

    /** Ajustes › «Botón rápido a la izquierda». */
    fun leerZurdo(): Boolean
    fun guardarZurdo(zurdo: Boolean)

    /** Los modos en que ya se abrió el menú: ahí ya no sale la burbuja de la primera vez. */
    fun leerPistasVistas(): Set<ModoApp>
    fun guardarPistasVistas(modos: Set<ModoApp>)

    /** Las veces que se eligió deslizando: después de tres ya no sale «Pulsa y desliza sin soltar». */
    fun leerElegidasDeslizando(): Int
    fun guardarElegidasDeslizando(veces: Int)

    fun leerContadores(): ContadoresRapido
    fun guardarContadores(contadores: ContadoresRapido)
}

/**
 * Cómo se usa el botón rápido, para medir en la tienda (docs/propuesta-interfaz.md, «Cómo se aprende»): cuántas veces se
 * abre con un toque y cuántas se elige con el gesto, y cuántas veces lo que abrió una opción se cerró en menos de
 * [MS_CERRADA_PRONTO] sin hacer nada (se eligió la que no era). Sin el nombre de nadie; salen solo con la sesión de
 * prueba, como las mediciones del escáner: la app no manda estadísticas.
 */
data class ContadoresRapido(
    val abiertoConToque: Int = 0,
    val conGesto: Map<Lugar, Int> = emptyMap(),
    val conToque: Map<Lugar, Int> = emptyMap(),
    val cerradasPronto: Map<Lugar, Int> = emptyMap(),
) {
    fun alAbrirConToque() = copy(abiertoConToque = abiertoConToque + 1)

    fun alElegir(lugar: Lugar, conElGesto: Boolean) =
        if (conElGesto) copy(conGesto = conGesto.mas(lugar)) else copy(conToque = conToque.mas(lugar))

    fun alCerrarPronto(lugar: Lugar) = copy(cerradasPronto = cerradasPronto.mas(lugar))

    /** Para Ajustes y el CSV: una línea por dato, en orden fijo. */
    fun renglones(): List<Pair<String, Int>> = listOf("Abierto con un toque" to abiertoConToque) +
        Lugar.entries.flatMap { l ->
            listOf(
                "${nombre(l)}: con el gesto" to (conGesto[l] ?: 0),
                "${nombre(l)}: con un toque" to (conToque[l] ?: 0),
                "${nombre(l)}: cerrada en menos de 2 s" to (cerradasPronto[l] ?: 0),
            )
        }

    /**
     * contadores-rapido.csv, el que sale con la medición de la sesión de prueba: un renglón por dato, en el orden de
     * [renglones], con la marca de UTF-8 para que Excel lea las flechas.
     */
    fun csv(): String {
        val sb = StringBuilder(Csv.BOM)
        Csv.renglon(listOf("dato", "veces"), sb)
        for ((que, cuantas) in renglones()) Csv.renglon(listOf(que, cuantas.toString()), sb)
        return sb.toString()
    }

    private fun Map<Lugar, Int>.mas(l: Lugar) = this + (l to ((this[l] ?: 0) + 1))

    private fun nombre(l: Lugar) = when (l) {
        Lugar.ARRIBA -> "↑"
        Lugar.DIAGONAL -> "↖"
        Lugar.LADO -> "←"
    }

    companion object {
        /** Lo que abrió una opción y se cerró antes de esto sin hacer nada cuenta como «se eligió la que no era». */
        const val MS_CERRADA_PRONTO = 2_000L
    }
}
