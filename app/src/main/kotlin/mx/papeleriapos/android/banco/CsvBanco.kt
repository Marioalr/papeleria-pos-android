package mx.papeleriapos.android.banco

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.sesion.DatosEquipo

/** El CSV del banco de pruebas: un renglón por escena, motor y pasada, con el mismo formato que el de la sesión. */
object CsvBanco {
    val COLUMNAS = listOf(
        "banco", "motor", "pasada", "region", "escena", "simbologia", "esperado", "modulo_px", "giro", "desenfoque",
        "ruido", "luz", "reflejo", "sin_codigo", "veredicto", "leido", "ms", "error", "dispositivo", "sdk",
        "version_app", "commit",
    )

    fun escribir(
        clave: String,
        region: String,
        resultados: List<ResultadoEscena>,
        equipo: DatosEquipo,
    ): String {
        val salida = StringBuilder(Csv.BOM)
        Csv.renglon(COLUMNAS, salida)
        for (r in resultados) {
            val e = r.escena
            Csv.renglon(
                listOf(
                    clave,
                    r.motor.name,
                    r.pasada.name,
                    region,
                    e.id,
                    e.simbologia?.name ?: "",
                    e.esperado ?: "",
                    if (e.conCodigo) Csv.decimal(e.pxPorModulo, 1) else "",
                    if (e.conCodigo) e.giro.toInt().toString() else "",
                    e.desenfoque.toString(),
                    Csv.decimal(e.ruido, 1),
                    Csv.decimal(e.luz, 2),
                    Csv.decimal(e.reflejo, 2),
                    e.negativo?.name ?: "",
                    r.veredicto?.name ?: "ERROR",
                    Csv.textoSeguro(r.leido),
                    Csv.decimal(r.ms, 2),
                    r.error ?: "",
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
}
