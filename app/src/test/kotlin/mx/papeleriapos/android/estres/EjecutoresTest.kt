package mx.papeleriapos.android.estres

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import mx.papeleriapos.escaner.Motor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EjecutoresTest {
    /** Una cámara que tarda siempre lo mismo y falla en los ciclos [fallas]. El reloj avanza con cada paso. */
    private class CamaraFalsa(private val fallas: Set<Int> = emptySet(), override val observaEstado: Boolean = true) : CamaraBajoPrueba {
        var relojNs = 0L
        var n = 0
        var aperturas = 0
        var cierres = 0
        val memorias = ArrayList<Int>()

        private fun avanzar(ms: Long) {
            relojNs += ms * 1_000_000
        }

        override suspend fun abrir() {
            n++
            aperturas++
            avanzar(10)
        }

        override suspend fun esperarAbierta(limiteMs: Long): Boolean {
            if (n in fallas) avanzar(limiteMs).also { return false }
            avanzar(120)
            return true
        }

        override suspend fun esperarPrimerCuadro(limiteMs: Long): Boolean {
            if (n in fallas) avanzar(limiteMs).also { return false }
            avanzar(200)
            return true
        }

        override suspend fun cerrar() {
            cierres++
            avanzar(5)
        }

        override suspend fun esperarCerrada(limiteMs: Long): Boolean {
            avanzar(50)
            return true
        }

        override fun error(): String? = if (n in fallas) "CameraState 2 (RECOVERABLE)" else null

        override suspend fun memoria(en: Int): PuntoMemoria {
            memorias += en
            return PuntoMemoria(en, 20_000, 30_000, 90_000, 180_000, 40, 110)
        }
    }

    @Test
    fun `cada ciclo mide abrir, primer cuadro y cerrar, y la memoria cada 25`() = runTest {
        val camara = CamaraFalsa(fallas = setOf(30))
        val vistos = ArrayList<Ciclo>()
        val (ciclos, memoria) = CiclosCamara(camara, ciclos = 60, cadaMemoria = 25, relojNs = { camara.relojNs })
            .correr(alCiclo = { c, _ -> vistos += c })
        assertEquals(60, ciclos.size)
        assertEquals(ciclos, vistos)
        assertEquals(60, camara.aperturas)
        // Aunque falle, el ciclo cierra la cámara.
        assertEquals(60, camara.cierres)
        val bueno = ciclos.first()
        assertEquals(130.0, bueno.abrirMs, 1e-9)
        assertEquals(330.0, bueno.primerCuadroMs, 1e-9)
        assertEquals(50.0, bueno.cerrarMs, 1e-9)
        assertNull(bueno.error)
        val malo = ciclos[29]
        assertEquals("CameraState 2 (RECOVERABLE)", malo.error)
        assertTrue(malo.abrirMs.isNaN())
        assertTrue(malo.primerCuadroMs.isNaN())
        assertEquals(listOf(1, 25, 50, 60), memoria.map { it.en })
        assertEquals(0, CriteriosCiclos.erroresSinRecuperar(ciclos))
    }

    @Test
    fun `sin estado de la camara solo cuenta el primer cuadro`() = runTest {
        val camara = CamaraFalsa(observaEstado = false)
        val (ciclos, _) = CiclosCamara(camara, ciclos = 3, relojNs = { camara.relojNs }).correr()
        assertTrue(ciclos.all { it.abrirMs.isNaN() && it.cerrarMs.isNaN() && it.error == null })
        assertEquals(330.0, ciclos.first().primerCuadroMs, 1e-9)
    }

    @Test
    fun `los ciclos se pueden cancelar`() = runTest {
        val camara = CamaraFalsa()
        var hechos = 0
        val (ciclos, memoria) = CiclosCamara(camara, ciclos = 200, relojNs = { camara.relojNs })
            .correr(alCiclo = { _, _ -> hechos++ }, cancelado = { hechos >= 7 })
        assertEquals(7, ciclos.size)
        assertEquals(listOf(1), memoria.map { it.en })
        assertEquals(camara.aperturas, camara.cierres)
    }

    private class ControlesFalsos(private val reloj: () -> Long) : ControlesBajoPrueba {
        val zooms = ArrayList<Boolean>()
        var linternas = 0

        override fun zoom(alto: Boolean) {
            zooms += alto
        }

        override fun alternarLinterna() {
            linternas++
        }

        override fun cuadros(): Long = reloj() / 33

        override fun fase(): String = "ESCANEANDO"

        override suspend fun final(): FinalRafaga {
            delay(1_500)
            return FinalRafaga(if (zooms.last()) 1.5f else 1f, if (zooms.last()) 1.5f else 1f, true, linternas % 2 == 1, linternas % 2 == 1, "ESCANEANDO", null)
        }
    }

    @Test
    fun `la rafaga manda zoom cada 30 ms un minuto y la linterna 100 veces`() = runTest {
        val controles = ControlesFalsos { testScheduler.currentTime }
        val (segundos, final) = RafagaControles(controles, { testScheduler.currentTime }, { delay(it) }).correr()
        assertEquals(2000, controles.zooms.size)
        assertEquals(100, controles.linternas)
        // Alterna: alto, 1×, alto…
        assertEquals(listOf(true, false, true, false), controles.zooms.take(4))
        // 59 segundos completos y el registro al terminar.
        assertEquals(60, segundos.size)
        assertEquals(2000, segundos.last().ordenesZoom)
        assertEquals(100, segundos.last().ordenesLinterna)
        assertTrue(CriteriosRafaga.evaluar(segundos, final).pasa)
    }

    @Test
    fun `una rafaga cancelada no espera el final`() = runTest {
        val controles = ControlesFalsos { testScheduler.currentTime }
        val (segundos, final) = RafagaControles(controles, { testScheduler.currentTime }, { delay(it) })
            .correr(cancelado = { testScheduler.currentTime >= 5_000 })
        assertNull(final)
        assertTrue(segundos.size in 5..6)
        assertTrue(controles.zooms.size in 160..170)
    }

    private class EscanerFalso(private val reloj: () -> Long, private val falla: Int = -1) : MotorBajoPrueba {
        val pedidos = ArrayList<Motor>()

        override fun cambiar(motor: Motor) {
            pedidos += motor
        }

        override suspend fun esperarMotor(motor: Motor, limiteMs: Long): Boolean {
            if (pedidos.size == falla) {
                delay(limiteMs)
                return false
            }
            delay(if (motor == Motor.ML_KIT) 400 else 150)
            return true
        }

        override fun error(): String? = null

        override suspend fun memoria(en: Int) = PuntoMemoria(en, 20_000, 30_000, 90_000, 180_000, 40, 110)
    }

    @Test
    fun `cambia de motor en rueda y mide lo que tarda en leer con el nuevo`() = runTest {
        val escaner = EscanerFalso({ testScheduler.currentTime }, falla = 7)
        val (cambios, memoria) = CambiosMotor(escaner, veces = 12, cadaMemoria = 5, relojNs = { testScheduler.currentTime * 1_000_000 }).correr()
        assertEquals(12, cambios.size)
        // Empieza en Automático (ya puesto), así que el primero es ML Kit y nunca repite el anterior.
        assertEquals(Motor.ML_KIT, escaner.pedidos.first())
        assertTrue(escaner.pedidos.zipWithNext().none { (a, b) -> a == b })
        assertEquals(400.0, cambios.first().ms, 1e-9)
        assertEquals(150.0, cambios[1].ms, 1e-9)
        assertEquals("no leyó con ${escaner.pedidos[6].nombre}", cambios[6].error)
        // La base cuando ya se creó cada motor una vez (3), luego cada 5 y el último.
        assertEquals(listOf(3, 5, 10, 12), memoria.map { it.en })
    }
}
