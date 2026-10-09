package mx.papeleriapos.android.sesion

/**
 * Ignora lo que llegue a menos de [msMinimo] de lo anterior que sí pasó: un doble toque con el pulgar en «No leyó»
 * registraría dos intentos fallidos y alteraría la tasa de lectura. Se usa en un solo hilo (el principal).
 */
class Antirrebote(private val msMinimo: Long = 1_000) {
    private var ultimo = Long.MIN_VALUE

    /** Si lo que llega en [ahoraMs] (reloj monotónico) cuenta. */
    fun permite(ahoraMs: Long): Boolean {
        if (ultimo != Long.MIN_VALUE && ahoraMs - ultimo in 0 until msMinimo) return false
        ultimo = ahoraMs
        return true
    }

    /** Después de deshacer, el siguiente toque cuenta de inmediato. */
    fun olvidar() {
        ultimo = Long.MIN_VALUE
    }
}
