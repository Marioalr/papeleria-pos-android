package mx.papeleriapos.escaner.motores

import mx.papeleriapos.escaner.ComparacionMotores
import mx.papeleriapos.escaner.LadoMotor
import org.junit.Assert.assertEquals
import org.junit.Test

class BitacoraLadoALadoTest {
    private val ms = 1_000_000L

    private fun bitacora(): BitacoraLadoALado {
        val b = BitacoraLadoALado(capacidad = 4)
        b.anotar(100 * ms, null, 400_000, null, 30 * ms) // Antes del candidato; el anillo lo desecha al quinto cuadro.
        b.anotar(1_000 * ms, null, 500_000, null, 40 * ms)
        b.anotar(1_100 * ms, "7509993456786", 450_000, "7509993456786", 35 * ms)
        b.anotar(1_200 * ms, "7509993456786", 420_000, "7509993456782", 36 * ms) // ML Kit leyó otra cosa.
        b.anotar(1_300 * ms, null, 430_000, "7509993456786", 38 * ms)
        return b
    }

    @Test
    fun comparaLosCuadrosDelCandidatoFrenteAlCodigoAceptado() {
        val b = bitacora()
        assertEquals(4, b.cuadros)
        val c = b.comparar(desde = 1_000 * ms, hasta = 1_300 * ms, normalizado = "7509993456786")
        assertEquals(4, c.cuadros)
        // Mediana (rango más cercano) de 0.50, 0.45, 0.42 y 0.43 = 0.43; la primera lectura, 100 ms después del candidato.
        assertEquals(LadoMotor(leidos = 2, otras = 0, msPrimera = 100, msCuadro = 0.43f), c.zxing)
        assertEquals(LadoMotor(leidos = 2, otras = 1, msPrimera = 100, msCuadro = 36f), c.mlKit)
        // Solo los cuadros de la ventana.
        val tarde = b.comparar(desde = 1_250 * ms, hasta = 1_300 * ms, normalizado = "7509993456786")
        assertEquals(ComparacionMotores(1, LadoMotor(0, 0, -1, 0.43f), LadoMotor(1, 0, 50, 38f)), tarde)
        assertEquals(ComparacionMotores(0, LadoMotor(0, 0, -1, 0f), LadoMotor(0, 0, -1, 0f)), b.comparar(5_000 * ms, 6_000 * ms, "x"))
    }

    @Test
    fun elResumenRecienteDiceCuantoTardaYCuantoLeeCadaUno() {
        assertEquals("", BitacoraLadoALado(4).resumenReciente())
        // Los últimos 4 (el anillo ya soltó el primero): zxing (0.50 + 0.45 + 0.42 + 0.43) / 4 = 0.45; ML Kit 37.25.
        assertEquals("zxing 0.45 ms, lee 2/4 · ML Kit 37 ms, lee 3/4", bitacora().resumenReciente())
        assertEquals("zxing 0.43 ms, lee 1/2 · ML Kit 37 ms, lee 2/2", bitacora().resumenReciente(2))
    }
}
