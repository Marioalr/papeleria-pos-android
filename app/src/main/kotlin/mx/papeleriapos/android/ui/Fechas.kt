package mx.papeleriapos.android.ui

import java.util.Calendar
import java.util.TimeZone

/**
 * Las fechas que se leen en pantalla, siempre en español de México, como el resto de la app: no siguen el idioma del
 * celular (con el celular en inglés salían «Nov 9, 2026 8:47 AM»). Se arman a mano, sin `DateFormat`: los meses abreviados
 * y el «a. m.» cambian entre versiones de Android y de la JVM, y así salen igual en el celular y en las pruebas.
 */
object Fechas {
    private val MESES = arrayOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /** «9 nov 2026, 8:47» (reloj de 24 horas). */
    fun fechaYHora(ms: Long, zona: TimeZone = TimeZone.getDefault()): String {
        val c = calendario(ms, zona)
        return "${c.get(Calendar.DAY_OF_MONTH)} ${MESES[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}, ${hora(c)}"
    }

    /** «8:47» o «20:47». */
    fun hora(ms: Long, zona: TimeZone = TimeZone.getDefault()): String = hora(calendario(ms, zona))

    private fun hora(c: Calendar): String = "${c.get(Calendar.HOUR_OF_DAY)}:${c.get(Calendar.MINUTE).toString().padStart(2, '0')}"

    private fun calendario(ms: Long, zona: TimeZone): Calendar = Calendar.getInstance(zona).apply { timeInMillis = ms }
}
