package mx.papeleriapos.android.sesion

import mx.papeleriapos.android.csv.Csv
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Cuántos intentos hubo y cuántos leyeron bien en un grupo (una simbología, un lente, con o sin linterna). */
data class Conteo(val intentos: Int = 0, val bien: Int = 0) {
    fun mas(ok: Boolean): Conteo = Conteo(intentos + 1, bien + if (ok) 1 else 0)

    /** «18/20» (bien de intentos). */
    fun texto(): String = "$bien/$intentos"
}

/**
 * El resumen corto de una tanda: un bloque de intentos con la misma condición (docs/plan.md, entrega 8 parte 7). Dice
 * qué leyó bien y a qué distancia, cuántas lecturas por minuto, cuántas falsas (deben ser 0), si la linterna ayudó y
 * con qué lente. Se arma de los intentos de [SesionPrueba] y los bloques de [Anotaciones]; no se guarda aparte.
 */
data class ResumenTanda(
    val condicion: Condicion,
    /** Los números del primer y el último intento de la tanda. */
    val desde: Int,
    val hasta: Int,
    val intentos: Int,
    /** Con resultado OK. */
    val bien: Int,
    /** Lecturas incorrectas y falsos positivos: las que no deben pasar. */
    val falsas: Int,
    val duplicados: Int,
    /** No leyó a tiempo. */
    val noLeyo: Int,
    val cancelados: Int,
    /** Mediana y p90 de los ms desde el candidato de las lecturas bien; −1 = sin dato. */
    val msMediana: Long = -1,
    val msP90: Long = -1,
    /** Mediana de la distancia de enfoque (cm) de las lecturas bien que la traen; −1 = ninguna la trae. */
    val distanciaCm: Float = -1f,
    /** Lecturas bien por minuto, de la primera a la última de la tanda (la ráfaga de conteo); 0 = menos de dos. */
    val lecturasPorMinuto: Float = 0f,
    /** Por simbología, solo los intentos que leyeron algo (un fallo no tiene simbología). */
    val porSimbologia: Map<String, Conteo> = emptyMap(),
    /** Todos los intentos, según la linterna al momento (los fallos también la traen). */
    val conLinterna: Conteo = Conteo(),
    val sinLinterna: Conteo = Conteo(),
    /** Todos los intentos, por el lente de Ajustes; «» si el intento no lo trae (un CSV de antes). */
    val porLente: Map<String, Conteo> = emptyMap(),
) {
    /** De uno a tres renglones para la pantalla de la sesión; lo que no se sabe no sale. */
    fun texto(enCurso: Boolean = false): String {
        val cabeza = StringBuilder()
        cabeza.append(condicion.etiqueta).append(" (#").append(desde)
        if (hasta != desde) cabeza.append('–').append(hasta)
        cabeza.append(')')
        if (enCurso) cabeza.append(", en curso")
        cabeza.append(": ").append(bien).append(" de ").append(intentos).append(" bien")
        if (intentos > 0) cabeza.append(" (").append(bien * 100 / intentos).append(" %)")
        cabeza.append(", ").append(falsas).append(if (falsas == 1) " falsa" else " falsas")
        if (duplicados > 0) cabeza.append(", ").append(duplicados).append(if (duplicados == 1) " duplicado" else " duplicados")
        if (noLeyo > 0) cabeza.append(", ").append(noLeyo).append(" no leyó")
        if (cancelados > 0) cabeza.append(", ").append(cancelados).append(if (cancelados == 1) " cancelado" else " cancelados")
        cabeza.append('.')
        val tiempos = ArrayList<String>(3)
        if (msMediana >= 0) tiempos += "lectura típica $msMediana ms" + if (msP90 >= 0) " (p90 $msP90)" else ""
        if (lecturasPorMinuto > 0f) tiempos += "${lecturasPorMinuto.roundToInt()} lecturas/min"
        if (distanciaCm > 0f) tiempos += "a ${distanciaCm.roundToInt()} cm"
        val grupos = ArrayList<String>(3)
        if (porSimbologia.isNotEmpty()) grupos += porSimbologia.entries.joinToString(" · ") { (s, c) -> "$s ${c.texto()}" }
        if (conLinterna.intentos > 0) grupos += "Linterna: con ${conLinterna.texto()}, sin ${sinLinterna.texto()}"
        if (porLente.isNotEmpty()) {
            grupos += "Lente: " + porLente.entries.joinToString(", ") { (l, c) -> "${l.ifEmpty { "sin dato" }} ${c.texto()}" }
        }
        val renglones = ArrayList<String>(3)
        renglones += cabeza.toString()
        if (tiempos.isNotEmpty()) renglones += tiempos.joinToString(", ").replaceFirstChar { it.uppercaseChar() } + "."
        if (grupos.isNotEmpty()) renglones += grupos.joinToString(". ") + "."
        return renglones.joinToString("\n")
    }
}

object ResumenTandas {
    /** Una tanda por bloque de condición de [anotaciones], en orden; sin intentos no hay tandas. */
    fun de(sesion: SesionPrueba, anotaciones: Anotaciones): List<ResumenTanda> {
        val notas = anotaciones.de(sesion)
        val tandas = ArrayList<ResumenTanda>()
        var grupo = ArrayList<Intento>()
        var bloque = Int.MIN_VALUE
        for (i in sesion.intentos) {
            // El bloque que rige para este intento (−1 = ninguno elegido todavía: NORMAL).
            val b = notas.bloques.indexOfLast { it.desde <= i.n }
            if (b != bloque && grupo.isNotEmpty()) {
                tandas += resumir(grupo, notas.condicionDe(grupo[0].n))
                grupo = ArrayList()
            }
            bloque = b
            grupo += i
        }
        if (grupo.isNotEmpty()) tandas += resumir(grupo, notas.condicionDe(grupo[0].n))
        return tandas
    }

    private fun resumir(intentos: List<Intento>, condicion: Condicion): ResumenTanda {
        val bien = intentos.filter { it.resultado == ResultadoIntento.OK }
        val ms = bien.map { it.msDesdeCandidato }.filter { it >= 0 }.sorted()
        val distancias = bien.map { it.distanciaCm }.filter { it > 0f }.sorted()
        val lapso = if (bien.size >= 2) bien.maxOf { it.hora } - bien.minOf { it.hora } else 0L
        val porSimbologia = LinkedHashMap<String, Conteo>()
        val porLente = LinkedHashMap<String, Conteo>()
        var con = Conteo()
        var sin = Conteo()
        for (i in intentos) {
            val ok = i.resultado == ResultadoIntento.OK
            if (i.simbologia.isNotEmpty()) porSimbologia[i.simbologia] = (porSimbologia[i.simbologia] ?: Conteo()).mas(ok)
            porLente[i.lente] = (porLente[i.lente] ?: Conteo()).mas(ok)
            if (i.linterna) con = con.mas(ok) else sin = sin.mas(ok)
        }
        return ResumenTanda(
            condicion = condicion,
            desde = intentos.first().n,
            hasta = intentos.last().n,
            intentos = intentos.size,
            bien = bien.size,
            falsas = intentos.count { it.resultado == ResultadoIntento.LECTURA_INCORRECTA || it.resultado == ResultadoIntento.FALSO_POSITIVO },
            duplicados = intentos.count { it.resultado == ResultadoIntento.DUPLICADO },
            noLeyo = intentos.count { it.resultado == ResultadoIntento.TIMEOUT },
            cancelados = intentos.count { it.resultado == ResultadoIntento.CANCELADO },
            msMediana = percentil(ms, 0.5) ?: -1,
            msP90 = percentil(ms, 0.9) ?: -1,
            distanciaCm = percentil(distancias, 0.5) ?: -1f,
            // Intervalos entre lecturas: n lecturas en t minutos son n − 1 intervalos.
            lecturasPorMinuto = if (lapso > 0) (bien.size - 1) * 60_000f / lapso else 0f,
            porSimbologia = porSimbologia,
            conLinterna = con,
            sinLinterna = sin,
            porLente = porLente,
        )
    }

    /** Rango más cercano: el elemento en la posición ⌈q·n⌉ (desde 1) de la lista ya ordenada; null si está vacía. */
    internal fun <T> percentil(ordenados: List<T>, q: Double): T? {
        if (ordenados.isEmpty()) return null
        val k = ceil(q * ordenados.size).toInt().coerceIn(1, ordenados.size)
        return ordenados[k - 1]
    }
}

/** El CSV del resumen, una fila por tanda (resumen.csv, junto con el de intentos al exportar). */
object CsvResumen {
    val COLUMNAS = listOf(
        "sesion", "condicion", "desde", "hasta", "intentos", "bien", "falsas", "duplicados", "no_leyo", "cancelados",
        "ms_mediana", "ms_p90", "distancia_cm", "lecturas_por_minuto", "por_simbologia", "con_linterna_intentos",
        "con_linterna_bien", "sin_linterna_intentos", "sin_linterna_bien", "por_lente", "dispositivo", "sdk",
        "version_app", "commit",
    )

    fun escribir(
        sesion: SesionPrueba,
        tandas: List<ResumenTanda>,
        equipo: DatosEquipo,
        zona: TimeZone = TimeZone.getDefault(),
    ): String {
        val clave = Csv.textoSeguro(sesion.clave(zona))
        val salida = StringBuilder(Csv.BOM)
        Csv.renglon(COLUMNAS, salida)
        for (t in tandas) {
            Csv.renglon(
                listOf(
                    clave,
                    t.condicion.name,
                    t.desde.toString(),
                    t.hasta.toString(),
                    t.intentos.toString(),
                    t.bien.toString(),
                    t.falsas.toString(),
                    t.duplicados.toString(),
                    t.noLeyo.toString(),
                    t.cancelados.toString(),
                    if (t.msMediana >= 0) t.msMediana.toString() else "",
                    if (t.msP90 >= 0) t.msP90.toString() else "",
                    if (t.distanciaCm > 0f) Csv.decimal(t.distanciaCm, 1) else "",
                    if (t.lecturasPorMinuto > 0f) Csv.decimal(t.lecturasPorMinuto, 1) else "",
                    conteos(t.porSimbologia),
                    t.conLinterna.intentos.toString(),
                    t.conLinterna.bien.toString(),
                    t.sinLinterna.intentos.toString(),
                    t.sinLinterna.bien.toString(),
                    conteos(t.porLente),
                    Csv.textoSeguro(equipo.dispositivo),
                    equipo.sdk.toString(),
                    equipo.versionApp,
                    equipo.commit,
                ),
                salida,
            )
        }
        return salida.toString()
    }

    /** «EAN_13 18/18|UPC_A 5/7»: empieza con letras, así la hoja de cálculo no lo toma por fecha. */
    private fun conteos(m: Map<String, Conteo>): String =
        m.entries.joinToString("|") { (k, c) -> "${k.ifEmpty { "sin_dato" }} ${c.texto()}" }
}
