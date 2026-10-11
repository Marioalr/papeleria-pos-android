package mx.papeleriapos.escaner.motores

import mx.papeleriapos.escaner.nucleo.Aceptada
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Confirmador
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnoRespaldoTest {

    /** [cuadros] en los que hace falta el respaldo; [lee] dice si lee en cada uno. Devuelve los cuadros en que corrió. */
    private fun correr(cuadros: Int, cadaCuantos: Int = 2, lee: (Int) -> Boolean): List<Int> {
        val turno = TurnoRespaldo(cadaCuantos)
        val corrio = ArrayList<Int>()
        for (k in 0 until cuadros) {
            turno.cuadro()
            if (!turno.toca()) continue
            corrio += k
            turno.corrio(lee(k))
        }
        return corrio
    }

    @Test
    fun sinLeerCorreUnoDeCadaDos() {
        assertEquals(listOf(1, 3, 5, 7, 9), correr(10) { false })
        assertEquals((0 until 10).toList(), correr(10, cadaCuantos = 1) { false })
    }

    @Test
    fun siLeeEnSuTurnoCorreTambienEnElSiguiente() {
        // La segunda corrida sale en el cuadro seguido y no encadena una tercera; toma el turno que seguía.
        assertEquals(listOf(1, 2, 5, 6, 9, 10), correr(12) { true })
        // Lee solo en el primer turno: la segunda corre y luego vuelve a uno de cada dos.
        assertEquals(listOf(1, 2, 5, 7, 9), correr(10) { it == 1 })
    }

    @Test
    fun enPromedioSigueSiendoUnoDeCadaDos() {
        for (patron in listOf<(Int) -> Boolean>({ true }, { false }, { it % 3 == 0 }, { it % 5 < 2 })) {
            val corridas = correr(300, lee = patron).size
            assertTrue("corrió $corridas de 300", corridas <= 151)
        }
    }

    @Test
    fun unCodigoQueSoloLeeElRespaldoEntraConDosLecturasSeguidas() {
        // zxing-cpp no lo lee; ML Kit sí, cada vez que corre. Sin la segunda corrida, las lecturas quedarían un cuadro
        // sí y otro no, y la racha del confirmador nunca llegaría a dos.
        val franja = Caja(0.04f, 0.32f, 0.96f, 0.68f)
        val leida = listOf(Lectura("7509993456786", Simbologia.EAN_13, null) to Caja(0.3f, 0.45f, 0.7f, 0.55f))
        val nada = emptyList<Pair<Lectura, Caja?>>()
        val turno = TurnoRespaldo(2)
        val confirmador = Confirmador()
        var aceptada: Aceptada? = null
        var k = 0
        while (aceptada == null && k < 30) {
            turno.cuadro()
            val corre = turno.toca()
            if (corre) turno.corrio(true)
            aceptada = confirmador.procesar(if (corre) leida else nada, emptyList(), franja, k * 33_333_333L)
            k++
        }
        assertNotNull("nunca entró", aceptada)
        // Primer turno en el cuadro 1, la segunda en el 2: entra ahí.
        assertEquals(3, k)
    }
}
