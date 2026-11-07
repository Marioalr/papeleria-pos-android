package mx.papeleriapos.pos.precio

/**
 * Un día del calendario, el `DateOnly` del POS: los días desde el 1 de enero de 1970. Sin `java.time`, que en Android es
 * de la API 26 y la app corre desde la 24: lo poco que hace falta (año, mes y día, el día de la semana, sumar días y
 * comparar) se calcula aquí, con el calendario gregoriano. Va del 1/01/0001 al 31/12/9999, como el `DateOnly`.
 */
@JvmInline
value class Fecha(val diasDesdeLaEpoca: Long) : Comparable<Fecha> {
    init {
        require(diasDesdeLaEpoca in PRIMER_DIA..ULTIMO_DIA) { "La fecha sale del calendario: va del 1/01/0001 al 31/12/9999." }
    }

    val anio: Int get() = aaaammdd() / 10_000

    val mes: Int get() = aaaammdd() / 100 % 100

    val dia: Int get() = aaaammdd() % 100

    /** 1 es lunes y 7 domingo: la semana de México, que empieza en lunes. El 1 de enero de 1970 fue jueves. */
    val diaDeLaSemana: Int get() = (diasDesdeLaEpoca + 3).mod(7) + 1

    /** Fuera del calendario, [IllegalArgumentException] (el `AddDays` del POS también falla). */
    fun masDias(dias: Int): Fecha = Fecha(diasDesdeLaEpoca + dias)

    override fun compareTo(other: Fecha): Int = diasDesdeLaEpoca.compareTo(other.diasDesdeLaEpoca)

    /** «2027-08-04» (ISO 8601). */
    override fun toString(): String =
        anio.toString().padStart(4, '0') + "-" + mes.toString().padStart(2, '0') + "-" + dia.toString().padStart(2, '0')

    /**
     * Año, mes y día en un solo número (20270804), con `civil_from_days` de Howard Hinnant: los años van de marzo a
     * febrero, para que el día que agregan los bisiestos quede al final.
     */
    private fun aaaammdd(): Int {
        val z = diasDesdeLaEpoca + DE_MARZO_DEL_ANIO_0
        val era = z.floorDiv(DIAS_DE_UNA_ERA)
        val diaDeLaEra = z - era * DIAS_DE_UNA_ERA // [0, 146096]
        val anioDeLaEra = (diaDeLaEra - diaDeLaEra / 1460 + diaDeLaEra / 36_524 - diaDeLaEra / 146_096) / 365 // [0, 399]
        val diaDelAnio = diaDeLaEra - (365 * anioDeLaEra + anioDeLaEra / 4 - anioDeLaEra / 100) // [0, 365], desde el 1 de marzo
        val mesDesdeMarzo = (5 * diaDelAnio + 2) / 153 // [0, 11]
        val dia = diaDelAnio - (153 * mesDesdeMarzo + 2) / 5 + 1
        val mes = if (mesDesdeMarzo < 10) mesDesdeMarzo + 3 else mesDesdeMarzo - 9
        val anio = anioDeLaEra + era * 400 + if (mes <= 2) 1 else 0
        return (anio * 10_000 + mes * 100 + dia).toInt()
    }

    companion object {
        /** Con un día que no existe (31 de abril, 29 de febrero de un año que no es bisiesto), [IllegalArgumentException]. */
        fun de(anio: Int, mes: Int, dia: Int): Fecha {
            require(anio in 1..9999) { "El año va del 1 al 9999." }
            require(mes in 1..12) { "El mes va del 1 al 12." }
            require(dia in 1..diasDelMes(anio, mes)) { "El mes $mes de $anio no tiene día $dia." }
            // days_from_civil de Howard Hinnant, con el año de marzo a febrero.
            val a = (if (mes <= 2) anio - 1 else anio).toLong()
            val era = a.floorDiv(400L)
            val anioDeLaEra = a - era * 400 // [0, 399]
            val diaDelAnio = (153 * (if (mes > 2) mes - 3 else mes + 9) + 2) / 5 + dia - 1 // [0, 365]
            val diaDeLaEra = anioDeLaEra * 365 + anioDeLaEra / 4 - anioDeLaEra / 100 + diaDelAnio // [0, 146096]
            return Fecha(era * DIAS_DE_UNA_ERA + diaDeLaEra - DE_MARZO_DEL_ANIO_0)
        }

        fun esBisiesto(anio: Int): Boolean = anio % 4 == 0 && (anio % 100 != 0 || anio % 400 == 0)

        private fun diasDelMes(anio: Int, mes: Int): Int = when (mes) {
            2 -> if (esBisiesto(anio)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        /** 400 años del calendario gregoriano: después se repite igual, también los días de la semana. */
        private const val DIAS_DE_UNA_ERA = 146_097L

        /** Del 1 de marzo del año 0 al 1 de enero de 1970. */
        private const val DE_MARZO_DEL_ANIO_0 = 719_468L

        /** El 1/01/0001 y el 31/12/9999: lo que cabe en un `DateOnly`. */
        private const val PRIMER_DIA = -719_162L
        private const val ULTIMO_DIA = 2_932_896L
    }
}
