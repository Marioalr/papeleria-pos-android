package mx.papeleriapos.escaner.nucleo

/** Cómo se usa el escáner: en venta (cada código distinto entra de inmediato) o en conteo (cada lectura suma uno). */
enum class Modo { VENTA, CONTEO }

/** Una lectura que pasó la validación y el consenso; [repetida] = el mismo código que la anterior aceptada. */
data class Aceptada(val codigo: CodigoLeido, val caja: Caja?, val nanos: Long, val repetida: Boolean)

/** Reglas de validación, consenso y repetidos (docs/escaner.md, «Validación y consenso»). */
data class ReglasConfirmacion(
    /** Lecturas iguales que hacen falta en la ventana para aceptar, por simbología. */
    val consenso: Map<Simbologia, Int> = mapOf(
        Simbologia.EAN_13 to 2, Simbologia.UPC_A to 2, Simbologia.UPC_E to 2, Simbologia.EAN_8 to 2,
        Simbologia.ITF to 2, Simbologia.CODE_128 to 2, Simbologia.QR to 1,
    ),
    /** Cuadros que se miran hacia atrás para el consenso. */
    val ventanaCuadros: Int = 3,
    /** El mismo código vuelve a contar solo si estuvo ausente al menos esto. */
    val msAusenciaParaRepetir: Long = 500,
)

/**
 * Valida (dígito verificador, largo del ITF), normaliza, exige consenso entre cuadros, elige el código más cercano al
 * centro de la franja y filtra repetidos.
 *
 * CONTRATO (implementación pendiente).
 */
class Confirmador(val reglas: ReglasConfirmacion = ReglasConfirmacion()) {
    /**
     * Procesa lo leído en un cuadro. [lecturas] ya trae cada caja en la región visible normalizada; [potenciales] son las
     * cajas de códigos vistos sin leer (cuentan como «sigue presente» para el filtro de repetidos). [franja] es la
     * región de lectura (el centro preferido). Devuelve la lectura aceptada en este cuadro, o null.
     */
    fun procesar(lecturas: List<Pair<Lectura, Caja?>>, potenciales: List<Caja>, franja: Caja, nanos: Long): Aceptada? =
        TODO("Pendiente")

    fun reiniciar(): Unit = TODO("Pendiente")

    companion object {
        /** Valida y normaliza una lectura; null si no es válida (p. ej. ITF que no es de 14 dígitos con su verificador). */
        fun normalizar(lectura: Lectura): CodigoLeido? = TODO("Pendiente")
    }
}
