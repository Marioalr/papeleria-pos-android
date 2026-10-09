package mx.papeleriapos.escaner.nucleo

/**
 * Lo que devuelve un motor (ML Kit o zxing-cpp) por cada código que leyó en un cuadro, tal cual, antes de validarlo.
 * [esquinasBuffer] son las cuatro esquinas (x0, y0, …, x3, y3) en píxeles del buffer de la cámara, sin rotar; la caja
 * normalizada se calcula con [Transformacion.cajaVisible].
 */
data class Lectura(
    val texto: String,
    val simbologia: Simbologia,
    val esquinasBuffer: FloatArray?,
) {
    override fun equals(other: Any?) = other is Lectura && texto == other.texto && simbologia == other.simbologia &&
        esquinasBuffer.contentEquals(other.esquinasBuffer)

    override fun hashCode() = 31 * texto.hashCode() + simbologia.hashCode()
}

/** El resultado de un motor en un cuadro: lo leído y los códigos que vio pero no pudo leer (potenciales). */
data class ResultadoMotor(
    val lecturas: List<Lectura>,
    /** Esquinas en el buffer de los códigos vistos sin leer (ML Kit con `enableAllPotentialBarcodes`). */
    val potencialesBuffer: List<FloatArray>,
    /** Lo que tardó el motor en este cuadro, en nanosegundos (sin contar el recorte). */
    val nanos: Long,
    /** Lo leyó el motor de respaldo (ML Kit dentro del automático). */
    val deRespaldo: Boolean = false,
) {
    companion object {
        val VACIO = ResultadoMotor(emptyList(), emptyList(), 0L)
    }
}

/**
 * Un código aceptado: ya validado, normalizado, confirmado y que pasó el filtro de repetidos (docs/escaner.md,
 * «Validación y consenso»).
 */
data class CodigoLeido(
    /** El código como lo leyó el motor (p. ej. un UPC-A de ML Kit llega con 12 dígitos). */
    val original: String,
    /** La forma canónica: GTIN-13 para EAN-13/UPC-A/UPC-E, 8 para EAN-8, 14 para ITF-14; lo demás igual. */
    val normalizado: String,
    val simbologia: Simbologia,
    /** Las formas con que conviene buscarlo en el catálogo del POS (8, 12, 13 o 14 dígitos según el caso). */
    val clavesDeBusqueda: List<String>,
)
