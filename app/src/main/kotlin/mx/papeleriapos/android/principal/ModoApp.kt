package mx.papeleriapos.android.principal

import mx.papeleriapos.escaner.nucleo.Modo

/**
 * Los tres modos de la pantalla principal (docs/propuesta-interfaz.md, «La pantalla principal: el escáner con tres
 * modos»): Precio enseña, Ticket arma la preventa y Conteo cuenta. El escáner solo conoce venta y conteo ([Modo], del
 * núcleo, que no se toca): Precio y Ticket escanean como venta, y [delNucleo] es lo que va en `ConfigEscaner.modo`, en la
 * lista que guarda cada lectura y en la columna modo de la sesión de prueba.
 */
enum class ModoApp(val nombre: String, val delNucleo: Modo) {
    PRECIO("Precio", Modo.VENTA),
    TICKET("Ticket", Modo.VENTA),
    CONTEO("Conteo", Modo.CONTEO),
    ;

    companion object {
        /** El de una app que todavía no guardaba el suyo (la 0.1): venta era el ticket. */
        fun deNucleo(modo: Modo): ModoApp = if (modo == Modo.CONTEO) CONTEO else TICKET

        /** El guardado con [name], o null si no hay o no se entiende. */
        fun deNombre(nombre: String?): ModoApp? = entries.firstOrNull { it.name == nombre }

        /**
         * El de los accesos directos y del intent: «precio», «ticket» y «conteo»; «venta» (los accesos directos que ya
         * estaban anclados con la 0.1) abre el ticket. null si no trae modo.
         */
        fun deExtra(extra: String?): ModoApp? = when (extra) {
            "precio" -> PRECIO
            "ticket", "venta" -> TICKET
            "conteo" -> CONTEO
            else -> null
        }
    }
}
