package mx.papeleriapos.android.sesion

import mx.papeleriapos.android.csv.Csv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * El CSV de la sesión de prueba, un renglón por intento (docs/pruebas-en-tienda.md, «CSV por intento»). El mismo
 * archivo sirve de respaldo: si Android cierra la app a media prueba, la sesión se recupera leyéndolo.
 */
object CsvSesion {
    val COLUMNAS = listOf(
        "sesion", "n", "hora", "motor", "modo", "rango_fps", "ms_desde_candidato", "indicaciones", "simbologia",
        "valor", "normalizado", "esperado", "coincide", "modulo_px", "zoom", "linterna", "resultado", "bateria_pct",
        "cpu_ms_proceso", "estado_termico", "dispositivo", "sdk", "version_app", "commit",
    )

    /** ISO 8601 con la diferencia horaria, para que no haya duda al juntar archivos de varios días. */
    private const val FORMATO_HORA = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

    fun escribir(sesion: SesionPrueba, equipo: DatosEquipo, zona: TimeZone = TimeZone.getDefault()): String {
        val hora = SimpleDateFormat(FORMATO_HORA, Locale.ROOT).apply { timeZone = zona }
        val clave = Csv.textoSeguro(sesion.clave(zona))
        val salida = StringBuilder(Csv.BOM)
        Csv.renglon(COLUMNAS, salida)
        for (i in sesion.intentos) {
            Csv.renglon(
                listOf(
                    clave,
                    i.n.toString(),
                    hora.format(Date(i.hora)),
                    i.motor,
                    i.modo,
                    i.rangoFps,
                    if (i.msDesdeCandidato >= 0) i.msDesdeCandidato.toString() else "",
                    i.indicaciones.joinToString("|"),
                    i.simbologia,
                    Csv.textoSeguro(i.valor),
                    Csv.textoSeguro(i.normalizado),
                    Csv.textoSeguro(i.esperado),
                    when (i.coincide) { true -> "si"; false -> "no"; null -> "" },
                    if (i.moduloPx > 0f) Csv.decimal(i.moduloPx) else "",
                    if (i.zoom > 0f) Csv.decimal(i.zoom) else "",
                    if (i.linterna) "si" else "no",
                    i.resultado.name,
                    if (i.medicion.bateriaPct >= 0) i.medicion.bateriaPct.toString() else "",
                    if (i.medicion.cpuMsProceso >= 0) i.medicion.cpuMsProceso.toString() else "",
                    if (i.medicion.estadoTermico >= 0) i.medicion.estadoTermico.toString() else "",
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

    /** Los intentos de un CSV escrito por [escribir]; los renglones que no se entienden se saltan. */
    fun leerIntentos(texto: String, zona: TimeZone = TimeZone.getDefault()): List<Intento> {
        val renglones = Csv.leer(texto)
        if (renglones.isEmpty()) return emptyList()
        val indice = renglones[0].withIndex().associate { (i, nombre) -> nombre to i }
        if (!indice.keys.containsAll(COLUMNAS)) return emptyList()
        val hora = SimpleDateFormat(FORMATO_HORA, Locale.ROOT).apply { timeZone = zona }
        return renglones.drop(1).mapNotNull { r ->
            fun col(nombre: String) = r.getOrNull(indice.getValue(nombre)) ?: ""
            runCatching {
                Intento(
                    n = col("n").toInt(),
                    hora = hora.parse(col("hora"))!!.time,
                    motor = col("motor"),
                    modo = col("modo"),
                    rangoFps = col("rango_fps"),
                    msDesdeCandidato = col("ms_desde_candidato").toLongOrNull() ?: -1,
                    indicaciones = col("indicaciones").split('|').filter { it.isNotEmpty() },
                    simbologia = col("simbologia"),
                    valor = Csv.textoOriginal(col("valor")),
                    normalizado = Csv.textoOriginal(col("normalizado")),
                    esperado = Csv.textoOriginal(col("esperado")),
                    coincide = when (col("coincide")) { "si" -> true; "no" -> false; else -> null },
                    moduloPx = col("modulo_px").toFloatOrNull() ?: 0f,
                    zoom = col("zoom").toFloatOrNull() ?: 0f,
                    linterna = col("linterna") == "si",
                    resultado = ResultadoIntento.valueOf(col("resultado")),
                    medicion = Medicion(
                        bateriaPct = col("bateria_pct").toIntOrNull() ?: -1,
                        cpuMsProceso = col("cpu_ms_proceso").toLongOrNull() ?: -1,
                        estadoTermico = col("estado_termico").toIntOrNull() ?: -1,
                    ),
                )
            }.getOrNull()
        }
    }

    /** Un nombre de archivo sin espacios, acentos ni signos que estorben al compartirlo. */
    fun nombreArchivo(prefijo: String, clave: String): String =
        prefijo + "-" + clave.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(80) + ".csv"
}
