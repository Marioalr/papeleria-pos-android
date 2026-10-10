package mx.papeleriapos.android.csv

import java.security.MessageDigest
import java.util.Locale

/**
 * CSV según RFC 4180 (comas, comillas dobles y fin de línea CRLF), para la sesión de prueba y el banco de pruebas. Lo
 * abren Excel, Google Sheets y pandas.
 */
object Csv {
    const val FIN_DE_LINEA = "\r\n"

    /** Excel en Windows solo lee bien los acentos de un CSV en UTF-8 si empieza con esta marca. */
    const val BOM = "\uFEFF"

    /** Un campo listo para escribir: entre comillas (y con las comillas dobladas) solo si hace falta. */
    fun campo(valor: String): String {
        val necesitaComillas = valor.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            valor.startsWith(' ') || valor.endsWith(' ')
        return if (necesitaComillas) "\"" + valor.replace("\"", "\"\"") + "\"" else valor
    }

    fun renglon(campos: List<String>, salida: StringBuilder) {
        campos.forEachIndexed { i, c ->
            if (i > 0) salida.append(',')
            salida.append(campo(c))
        }
        salida.append(FIN_DE_LINEA)
    }

    /**
     * Un texto que viene de fuera (lo leído o lo tecleado): si empieza con =, +, − o @ una hoja de cálculo lo tomaría
     * como fórmula, así que se le antepone un apóstrofo.
     */
    fun textoSeguro(valor: String): String = if (valor.isNotEmpty() && valor[0] in INICIOS_DE_FORMULA) "'$valor" else valor

    /** Lo contrario de [textoSeguro], al leer un CSV propio. */
    fun textoOriginal(valor: String): String =
        if (valor.length >= 2 && valor[0] == '\'' && valor[1] in INICIOS_DE_FORMULA) valor.substring(1) else valor

    /** Lee un CSV completo (con o sin BOM, con saltos de línea dentro de comillas). */
    fun leer(texto: String): List<List<String>> {
        val renglones = ArrayList<List<String>>()
        var campos = ArrayList<String>()
        val actual = StringBuilder()
        var i = if (texto.startsWith(BOM)) 1 else 0
        var entreComillas = false
        var hayAlgo = false
        while (i < texto.length) {
            val c = texto[i]
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < texto.length && texto[i + 1] == '"') {
                        actual.append('"'); i++
                    } else {
                        entreComillas = false
                    }
                } else {
                    actual.append(c)
                }
            } else {
                when (c) {
                    '"' -> { entreComillas = true; hayAlgo = true }
                    ',' -> { campos.add(actual.toString()); actual.setLength(0); hayAlgo = true }
                    '\r' -> {}
                    '\n' -> {
                        campos.add(actual.toString()); actual.setLength(0)
                        renglones.add(campos); campos = ArrayList(); hayAlgo = false
                    }
                    else -> { actual.append(c); hayAlgo = true }
                }
            }
            i++
        }
        if (hayAlgo || actual.isNotEmpty() || campos.isNotEmpty()) {
            campos.add(actual.toString())
            renglones.add(campos)
        }
        return renglones
    }

    /** Un número con punto decimal sin importar el idioma del celular (en es-ES sería coma y rompería el CSV). */
    fun decimal(valor: Float, decimales: Int = 2): String = String.format(Locale.ROOT, "%.${decimales}f", valor)

    fun decimal(valor: Double, decimales: Int = 2): String = String.format(Locale.ROOT, "%.${decimales}f", valor)

    /**
     * Lo que se guarda de un código 2D (el QR del ticket): un hash SHA-256 corto y los últimos 4 caracteres, para poder
     * comparar intentos sin dejar el contenido en el archivo.
     */
    fun ocultar(valor: String): String {
        if (valor.isEmpty()) return ""
        val hash = MessageDigest.getInstance("SHA-256").digest(valor.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(12)
        for (k in 0 until 6) hex.append(String.format(Locale.ROOT, "%02x", hash[k].toInt() and 0xFF))
        return "sha256:$hex...${valor.takeLast(4)}"
    }

    private const val INICIOS_DE_FORMULA = "=+-@\t\r"
}
