package mx.papeleriapos.pos.precio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La fecha sin `java.time` (el `DateOnly` del POS) y los días de la semana de las promociones. En el POS esto lo da .NET;
 * aquí se calcula a mano, así que se prueba entero: todos los días del 1/01/0001 al 31/12/9999.
 */
class FechaTest {
    @Test
    fun `los dias desde 1970 y el dia de la semana de fechas conocidas`() {
        val casos = listOf(
            Triple(Fecha.de(1970, 1, 1), 0L, 4), // jueves
            Triple(Fecha.de(1969, 12, 31), -1L, 3),
            Triple(Fecha.de(2000, 1, 1), 10_957L, 6),
            Triple(Fecha.de(2000, 3, 1), 11_017L, 3),
            Triple(Fecha.de(2027, 8, 4), 21_034L, 3), // el «hoy» de las pruebas del POS: un miércoles
            Triple(Fecha.de(2027, 8, 14), 21_044L, 6),
            Triple(Fecha.de(2028, 2, 29), 21_243L, 2),
            Triple(Fecha.de(1900, 3, 1), -25_508L, 4),
            Triple(Fecha.de(1600, 2, 29), -135_081L, 2),
            Triple(Fecha.de(1, 1, 1), -719_162L, 1), // el primero del DateOnly: lunes
            Triple(Fecha.de(9999, 12, 31), 2_932_896L, 5), // el último: viernes
        )
        for ((fecha, dias, diaDeLaSemana) in casos) {
            assertEquals("$fecha", dias, fecha.diasDesdeLaEpoca)
            assertEquals("$fecha", diaDeLaSemana, fecha.diaDeLaSemana)
        }
        assertEquals("2027-08-04", Fecha.de(2027, 8, 4).toString())
        assertEquals("0001-01-01", Fecha.de(1, 1, 1).toString())
    }

    @Test
    fun `cada dia del calendario va y vuelve y los dias de la semana se siguen`() {
        var (anioAntes, mesAntes, diaAntes) = Triple(1, 1, 1)
        var diaDeLaSemanaAntes = 1
        for (dias in -719_161L..2_932_896L) {
            val fecha = Fecha(dias)
            val (anio, mes, dia) = Triple(fecha.anio, fecha.mes, fecha.dia)
            if (Fecha.de(anio, mes, dia) != fecha) throw AssertionError("$fecha no vuelve")
            if (fecha.diaDeLaSemana != diaDeLaSemanaAntes % 7 + 1) throw AssertionError("$fecha: ${fecha.diaDeLaSemana}")
            // El siguiente del anterior: un día más, o el 1 del mes siguiente, o el 1 de enero.
            val siguienteDia = dia == diaAntes + 1 && mes == mesAntes && anio == anioAntes
            val siguienteMes = dia == 1 && mes == mesAntes + 1 && anio == anioAntes
            val siguienteAnio = dia == 1 && mes == 1 && mesAntes == 12 && diaAntes == 31 && anio == anioAntes + 1
            if (!(siguienteDia || siguienteMes || siguienteAnio)) throw AssertionError("$anioAntes-$mesAntes-$diaAntes → $fecha")
            anioAntes = anio
            mesAntes = mes
            diaAntes = dia
            diaDeLaSemanaAntes = fecha.diaDeLaSemana
        }
        assertEquals(Triple(9999, 12, 31), Triple(anioAntes, mesAntes, diaAntes))
    }

    @Test
    fun `los bisiestos son los del calendario gregoriano`() {
        for (anio in 1..9998) {
            val diasDelAnio = Fecha.de(anio + 1, 1, 1).diasDesdeLaEpoca - Fecha.de(anio, 1, 1).diasDesdeLaEpoca
            assertEquals("$anio", if (Fecha.esBisiesto(anio)) 366L else 365L, diasDelAnio)
        }
        assertTrue(Fecha.esBisiesto(2000))
        assertTrue(Fecha.esBisiesto(2028))
        assertFalse(Fecha.esBisiesto(1900))
        assertFalse(Fecha.esBisiesto(2100))
        assertFalse(Fecha.esBisiesto(2027))
    }

    @Test
    fun `sumar dias cruza meses y anios`() {
        val hoy = Fecha.de(2027, 8, 4)

        assertEquals(Fecha.de(2027, 8, 14), hoy.masDias(10))
        assertEquals(Fecha.de(2027, 7, 25), hoy.masDias(-10))
        assertEquals(Fecha.de(2028, 1, 1), Fecha.de(2027, 12, 31).masDias(1))
        assertEquals(Fecha.de(2028, 2, 29), Fecha.de(2028, 2, 28).masDias(1))
        assertEquals(Fecha.de(2027, 3, 1), Fecha.de(2027, 2, 28).masDias(1))
        assertEquals(Fecha.de(2028, 8, 4), hoy.masDias(366))
        assertEquals(hoy, hoy.masDias(0))
    }

    @Test
    fun `se comparan por el dia`() {
        val hoy = Fecha.de(2027, 8, 4)

        assertTrue(hoy < hoy.masDias(1))
        assertTrue(hoy > Fecha.de(2027, 7, 31))
        assertTrue(hoy >= Fecha.de(2027, 8, 4))
        assertEquals(0, hoy.compareTo(Fecha(21_034)))
        assertEquals(listOf(Fecha.de(2026, 1, 1), hoy, Fecha.de(2027, 12, 31)), listOf(Fecha.de(2027, 12, 31), hoy, Fecha.de(2026, 1, 1)).sorted())
    }

    @Test
    fun `un dia que no existe o que sale del calendario no se acepta`() {
        assertThrows(IllegalArgumentException::class.java) { Fecha.de(2027, 4, 31) }
        assertThrows(IllegalArgumentException::class.java) { Fecha.de(2027, 2, 29) }
        assertThrows(IllegalArgumentException::class.java) { Fecha.de(1900, 2, 29) }
        assertThrows(IllegalArgumentException::class.java) { Fecha.de(2027, 13, 1) }
        assertThrows(IllegalArgumentException::class.java) { Fecha.de(2027, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { Fecha.de(2027, 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { Fecha.de(0, 12, 31) }
        assertThrows(IllegalArgumentException::class.java) { Fecha.de(10_000, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { Fecha.de(9999, 12, 31).masDias(1) }
        assertThrows(IllegalArgumentException::class.java) { Fecha.de(1, 1, 1).masDias(-1) }
        assertThrows(IllegalArgumentException::class.java) { Fecha(Long.MAX_VALUE) }
    }

    @Test
    fun `el dia de la semana de una fecha es su bandera`() {
        // Del lunes 2 al domingo 8 de agosto de 2027.
        val semana = listOf(
            DiasDeLaSemana.LUNES, DiasDeLaSemana.MARTES, DiasDeLaSemana.MIERCOLES, DiasDeLaSemana.JUEVES, DiasDeLaSemana.VIERNES,
            DiasDeLaSemana.SABADO, DiasDeLaSemana.DOMINGO,
        )
        for ((i, bandera) in semana.withIndex()) {
            val dia = Fecha.de(2027, 8, 2 + i)
            assertEquals("$dia", bandera, DiasDeLaSemana.de(dia))
            assertTrue("$dia", bandera.incluye(dia))
            assertTrue("$dia", DiasDeLaSemana.TODOS.incluye(dia))
            assertFalse("$dia", DiasDeLaSemana.NINGUNO.incluye(dia))
            assertFalse("$dia", DiasDeLaSemana(DiasDeLaSemana.TODOS.valor xor bandera.valor).incluye(dia))
        }
        // Los mismos valores que el POS: el número de la copia del catálogo se usa tal cual.
        assertEquals(listOf(1, 2, 4, 8, 16, 32, 64), semana.map { it.valor })
        assertEquals(127, semana.reduce { todos, dia -> todos or dia }.valor)
        assertEquals(DiasDeLaSemana(96), DiasDeLaSemana.SABADO or DiasDeLaSemana.DOMINGO)
    }
}
