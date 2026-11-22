package mx.papeleriapos.android.principal

import mx.papeleriapos.escaner.nucleo.Modo

/**
 * Lo que hace la pantalla principal con cada lectura (0.5): en [VENTA] enseña el precio y entra al ticket (Precio y
 * Ticket de antes, en una sola pantalla) y en [CONTEO] cuenta. Conteo es una tarea aparte: se entra desde el menú «Este
 * celular» y la pantalla lo dice mientras dura. El escáner conoce los mismos dos ([Modo], del núcleo, que no se toca):
 * [delNucleo] es lo que va en `ConfigEscaner.modo`, en la lista que guarda cada lectura y en la columna modo de la sesión
 * de prueba.
 */
enum class ModoApp(val nombre: String, val delNucleo: Modo) {
    VENTA("Venta", Modo.VENTA),
    CONTEO("Conteo", Modo.CONTEO),
    ;

    companion object {
        /** El de una app que todavía no guardaba el suyo (la 0.1). */
        fun deNucleo(modo: Modo): ModoApp = if (modo == Modo.CONTEO) CONTEO else VENTA

        /**
         * El guardado con [name], o null si no hay o no se entiende. «PRECIO» y «TICKET» son los de antes de la 0.5: los
         * dos son la venta.
         */
        fun deNombre(nombre: String?): ModoApp? = when (nombre) {
            "VENTA", "PRECIO", "TICKET" -> VENTA
            "CONTEO" -> CONTEO
            else -> null
        }

        /**
         * El de los accesos directos y del intent: «precio», «ticket» y «venta» (los accesos que ya estaban anclados) abren
         * la venta; «conteo», el conteo. null si no trae modo.
         */
        fun deExtra(extra: String?): ModoApp? = when (extra) {
            "precio", "ticket", "venta" -> VENTA
            "conteo" -> CONTEO
            else -> null
        }
    }
}
