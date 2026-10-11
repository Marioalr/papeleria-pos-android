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
        "sesion", "n", "hora", "motor", "via", "modo", "rango_fps", "condicion", "ms_desde_candidato", "indicaciones",
        "simbologia", "valor", "normalizado", "esperado", "coincide", "en_catalogo", "variante", "clave_catalogo",
        "modulo_px", "zoom", "linterna", "lente", "camara_fisica", "resolucion", "ms_cuadro", "distancia_cm", "resultado",
        "nota", "bateria_pct", "cpu_ms", "pared_ms", "cpu_pct_nucleo", "estado_termico", "tipo_arranque", "ms_actividad",
        "ms_enlace", "ms_camara", "ms_primer_cuadro", "ms_primer_analisis", "ms_primera_lectura", "dispositivo", "sdk",
        "version_app", "commit",
    )

    /** Las que tiene que traer un CSV para recuperar sus intentos (los de versiones anteriores no traen las nuevas). */
    private val ESENCIALES = listOf("n", "hora", "motor", "modo", "rango_fps", "resultado")

    /** ISO 8601 con la diferencia horaria, para que no haya duda al juntar archivos de varios días (también aperturas.csv). */
    const val FORMATO_HORA = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

    fun escribir(
        sesion: SesionPrueba,
        equipo: DatosEquipo,
        zona: TimeZone = TimeZone.getDefault(),
        anotaciones: Anotaciones = Anotaciones(),
    ): String {
        val hora = SimpleDateFormat(FORMATO_HORA, Locale.ROOT).apply { timeZone = zona }
        val clave = Csv.textoSeguro(sesion.clave(zona))
        val notas = anotaciones.de(sesion)
        val salida = StringBuilder(Csv.BOM)
        Csv.renglon(COLUMNAS, salida)
        for (i in sesion.intentos) {
            val m = i.medicion
            val a = m.apertura
            Csv.renglon(
                listOf(
                    clave,
                    i.n.toString(),
                    hora.format(Date(i.hora)),
                    i.motor,
                    i.via,
                    i.modo,
                    i.rangoFps,
                    notas.condicionDe(i.n).name,
                    if (i.msDesdeCandidato >= 0) i.msDesdeCandidato.toString() else "",
                    i.indicaciones.joinToString("|"),
                    i.simbologia,
                    Csv.textoSeguro(i.valor),
                    Csv.textoSeguro(i.normalizado),
                    Csv.textoSeguro(i.esperado),
                    siNo(i.coincide),
                    siNo(i.enCatalogo),
                    i.variante,
                    Csv.textoSeguro(i.claveCatalogo),
                    if (i.moduloPx > 0f) Csv.decimal(i.moduloPx) else "",
                    if (i.zoom > 0f) Csv.decimal(i.zoom) else "",
                    if (i.linterna) "si" else "no",
                    i.lente,
                    i.camaraFisica,
                    i.resolucion,
                    if (i.msCuadro > 0f) Csv.decimal(i.msCuadro, 1) else "",
                    if (i.distanciaCm > 0f) Csv.decimal(i.distanciaCm, 1) else "",
                    i.resultado.name,
                    Csv.textoSeguro(notas.notaDe(i.n)),
                    if (m.bateriaPct >= 0) m.bateriaPct.toString() else "",
                    ms(m.cpuMs),
                    ms(m.paredMs),
                    m.cpuPctNucleo.let { if (it.isNaN()) "" else Csv.decimal(it, 1) },
                    if (m.estadoTermico >= 0) m.estadoTermico.toString() else "",
                    a.tipo,
                    ms(a.msActividad),
                    ms(a.msEnlace),
                    ms(a.msCamara),
                    ms(a.msPrimerCuadro),
                    ms(a.msPrimerAnalisis),
                    ms(a.msPrimeraLectura),
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

    /**
     * Los intentos de un CSV escrito por [escribir] (o por una versión anterior: lo que no traiga queda sin dato); los
     * renglones que no se entienden se saltan.
     */
    fun leerIntentos(texto: String, zona: TimeZone = TimeZone.getDefault()): List<Intento> {
        val renglones = Csv.leer(texto)
        if (renglones.isEmpty()) return emptyList()
        val indice = renglones[0].withIndex().associate { (i, nombre) -> nombre to i }
        if (!indice.keys.containsAll(ESENCIALES)) return emptyList()
        val hora = SimpleDateFormat(FORMATO_HORA, Locale.ROOT).apply { timeZone = zona }
        return renglones.drop(1).mapNotNull { r ->
            fun col(nombre: String) = indice[nombre]?.let { r.getOrNull(it) } ?: ""
            fun largo(nombre: String) = col(nombre).toLongOrNull() ?: -1L
            runCatching {
                Intento(
                    n = col("n").toInt(),
                    hora = hora.parse(col("hora"))!!.time,
                    motor = col("motor"),
                    via = col("via"),
                    modo = col("modo"),
                    rangoFps = col("rango_fps"),
                    msDesdeCandidato = largo("ms_desde_candidato"),
                    indicaciones = col("indicaciones").split('|').filter { it.isNotEmpty() },
                    simbologia = col("simbologia"),
                    valor = Csv.textoOriginal(col("valor")),
                    normalizado = Csv.textoOriginal(col("normalizado")),
                    esperado = Csv.textoOriginal(col("esperado")),
                    coincide = deSiNo(col("coincide")),
                    enCatalogo = deSiNo(col("en_catalogo")),
                    variante = col("variante"),
                    claveCatalogo = Csv.textoOriginal(col("clave_catalogo")),
                    moduloPx = col("modulo_px").toFloatOrNull() ?: 0f,
                    zoom = col("zoom").toFloatOrNull() ?: 0f,
                    linterna = col("linterna") == "si",
                    lente = col("lente"),
                    camaraFisica = col("camara_fisica"),
                    resolucion = col("resolucion"),
                    msCuadro = col("ms_cuadro").toFloatOrNull() ?: 0f,
                    distanciaCm = col("distancia_cm").toFloatOrNull() ?: -1f,
                    resultado = ResultadoIntento.valueOf(col("resultado")),
                    medicion = Medicion(
                        bateriaPct = col("bateria_pct").toIntOrNull() ?: -1,
                        // El cpu_ms_proceso de las versiones anteriores era acumulado: no se convierte.
                        cpuMs = largo("cpu_ms"),
                        estadoTermico = col("estado_termico").toIntOrNull() ?: -1,
                        paredMs = largo("pared_ms"),
                        apertura = MedidasApertura(
                            tipo = col("tipo_arranque"),
                            msActividad = largo("ms_actividad"),
                            msEnlace = largo("ms_enlace"),
                            msCamara = largo("ms_camara"),
                            msPrimerCuadro = largo("ms_primer_cuadro"),
                            msPrimerAnalisis = largo("ms_primer_analisis"),
                            msPrimeraLectura = largo("ms_primera_lectura"),
                        ),
                    ),
                )
            }.getOrNull()
        }
    }

    /**
     * Las anotaciones (condición de cada bloque y notas) que quedaron en las columnas condicion y nota de un CSV escrito
     * por [escribir]: para reconstruirlas si se perdió anotaciones.csv. Un bloque empieza donde cambia la condición.
     */
    fun leerAnotaciones(texto: String, inicioSesion: Long): Anotaciones {
        val renglones = Csv.leer(texto)
        if (renglones.isEmpty()) return Anotaciones(inicioSesion)
        val indice = renglones[0].withIndex().associate { (i, nombre) -> nombre to i }
        val iN = indice["n"] ?: return Anotaciones(inicioSesion)
        val iCondicion = indice["condicion"]
        val iNota = indice["nota"]
        var a = Anotaciones(inicioSesion)
        var previa = Condicion.NORMAL
        for (r in renglones.drop(1).sortedBy { it.getOrNull(iN)?.toIntOrNull() ?: Int.MAX_VALUE }) {
            val n = r.getOrNull(iN)?.toIntOrNull() ?: continue
            val condicion = iCondicion?.let { r.getOrNull(it) }?.let { c -> Condicion.entries.firstOrNull { it.name == c } }
            if (condicion != null && condicion != previa) {
                a = a.copy(bloques = a.bloques + Bloque(n, condicion))
                previa = condicion
            }
            val nota = iNota?.let { r.getOrNull(it) }.orEmpty()
            if (nota.isNotEmpty()) a = a.conNota(n, Csv.textoOriginal(nota))
        }
        return a
    }

    /** Un nombre de archivo sin espacios, acentos ni signos que estorben al compartirlo. */
    fun nombreArchivo(prefijo: String, clave: String): String =
        prefijo + "-" + clave.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(80) + ".csv"

    private fun ms(v: Long) = if (v >= 0) v.toString() else ""

    private fun siNo(v: Boolean?) = when (v) { true -> "si"; false -> "no"; null -> "" }

    private fun deSiNo(v: String) = when (v) { "si" -> true; "no" -> false; else -> null }
}
