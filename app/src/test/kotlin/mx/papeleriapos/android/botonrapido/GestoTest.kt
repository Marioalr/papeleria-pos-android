package mx.papeleriapos.android.botonrapido

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los casos del gesto que se probaron en el prototipo (probar2.js) y la tabla «Cómo se usa» de la propuesta. */
class GestoTest {

    private val todas = setOf(Lugar.ARRIBA, Lugar.DIAGONAL, Lugar.LADO)

    /** Precio: ↖ queda vacío. */
    private val sinDiagonal = setOf(Lugar.ARRIBA, Lugar.LADO)

    private fun gesto(
        pulso: Punto = Punto(0f, 0f),
        hay: Set<Lugar> = todas,
        yaAbierto: Boolean = false,
        zurdo: Boolean = false,
    ) = GestoRapido(pulso, tPulsoMs = 1_000L, hay = hay, yaAbierto = yaAbierto, zurdo = zurdo)

    /** Desliza desde el pulso hasta [hasta] en [pasos] movimientos y suelta [ms] después de pulsar. */
    private fun GestoRapido.deslizar(desde: Punto, hasta: Punto, ms: Long = 250L, pasos: Int = 6): Final {
        for (i in 1..pasos) {
            val f = i.toFloat() / pasos
            mover(Punto(desde.x + (hasta.x - desde.x) * f, desde.y + (hasta.y - desde.y) * f))
        }
        return soltar(hasta, 1_000L + ms)
    }

    @Test
    fun `un toque deja abiertas las tres`() {
        assertEquals(Final.DejarAbierto, gesto().soltar(Punto(0f, 0f), 1_120L))
    }

    @Test
    fun `un toque con temblor de 5 dp sigue siendo toque`() {
        val g = gesto()
        g.mover(Punto(3f, 4f))
        assertEquals(Final.DejarAbierto, g.soltar(Punto(3f, 4f), 1_150L))
    }

    @Test
    fun `moverse 13 dp y soltar sin elegir cierra`() {
        val g = gesto()
        assertEquals(Final.Cerrar, g.deslizar(Punto(0f, 0f), Punto(-13f, 0f)))
    }

    @Test
    fun `sostener y soltar sin moverse cierra`() {
        assertEquals(Final.Cerrar, gesto().soltar(Punto(0f, 0f), 1_000L + 600L))
    }

    @Test
    fun `deslizar arriba elige arriba`() {
        assertEquals(Final.Elegir(Lugar.ARRIBA), gesto().deslizar(Punto(0f, 0f), Punto(0f, -40f)))
    }

    @Test
    fun `deslizar a la izquierda busca`() {
        assertEquals(Final.Elegir(Lugar.LADO), gesto().deslizar(Punto(0f, 0f), Punto(-40f, 0f)))
    }

    @Test
    fun `la diagonal elige lo del modo`() {
        assertEquals(Final.Elegir(Lugar.DIAGONAL), gesto().deslizar(Punto(0f, 0f), Punto(-30f, -30f)))
    }

    @Test
    fun `abajo a la derecha cancela y marca la equis`() {
        val g = gesto()
        g.mover(Punto(30f, 30f))
        assertEquals(Resaltado.Cancelar, g.resaltado)
        assertEquals(Final.Cerrar, g.soltar(Punto(30f, 30f), 1_300L))
    }

    @Test
    fun `con el dedo sobre una opcion gana esa aunque el angulo diga otra`() {
        // Pulsa en la esquina de abajo a la izquierda de la zona de toque y va al centro de ↖: desde el pulso el ángulo
        // es de 111°, que es ↑; el dedo está encima de ↖.
        val pulso = Punto(-28f, 28f)
        val diagonal = CENTROS.getValue(Lugar.DIAGONAL)
        assertEquals(Lugar.ARRIBA, sectorDe(angulo(diagonal - pulso), null))
        val g = gesto(pulso = pulso)
        assertEquals(Final.Elegir(Lugar.DIAGONAL), g.deslizar(pulso, diagonal))
    }

    @Test
    fun `llegar encima de una opcion la elige aunque no se haya movido 32 dp`() {
        // Desde la esquina de arriba a la izquierda de la zona de toque (64 dp), 15 dp bastan para quedar a 28 dp del
        // centro de ↖, aunque no se haya armado por distancia.
        val pulso = Punto(-32f, -32f)
        val hasta = Punto(-42.7f, -42.7f)
        assertTrue((hasta - pulso).distancia() < 16f)
        val g = gesto(pulso = pulso)
        assertEquals(Final.Elegir(Lugar.DIAGONAL), g.deslizar(pulso, hasta))
    }

    @Test
    fun `la de antes se queda en la frontera con 4 grados de mas`() {
        assertEquals(Lugar.ARRIBA, sectorDe(114.0, Lugar.ARRIBA))
        assertEquals(Lugar.DIAGONAL, sectorDe(114.0, null))
        assertEquals(Lugar.DIAGONAL, sectorDe(110.0, Lugar.DIAGONAL))
        assertEquals(Lugar.ARRIBA, sectorDe(110.0, null))
        assertEquals(Lugar.LADO, sectorDe(228.0, Lugar.LADO))
        assertNull(sectorDe(228.0, null))
        assertNull(sectorDe(0.0, null))
        assertNull(sectorDe(300.0, Lugar.ARRIBA))
    }

    @Test
    fun `la histeresis vale en el gesto`() {
        val g = gesto()
        g.mover(Punto(0f, -40f))
        assertEquals(Resaltado.Opcion(Lugar.ARRIBA), g.resaltado)
        // 114°: ya pasó la frontera de 112.5, pero ↑ estaba resaltada.
        val r = 40.0
        val a = Math.toRadians(114.0)
        g.mover(Punto((r * Math.cos(a)).toFloat(), (-r * Math.sin(a)).toFloat()))
        assertEquals(Resaltado.Opcion(Lugar.ARRIBA), g.resaltado)
        val b = Math.toRadians(118.0)
        g.mover(Punto((r * Math.cos(b)).toFloat(), (-r * Math.sin(b)).toFloat()))
        assertEquals(Resaltado.Opcion(Lugar.DIAGONAL), g.resaltado)
    }

    @Test
    fun `el hueco de un modo se porta como cancelar`() {
        val g = gesto(hay = sinDiagonal)
        g.mover(Punto(-30f, -30f))
        assertEquals(Resaltado.Cancelar, g.resaltado)
        assertEquals(Final.Cerrar, g.soltar(Punto(-30f, -30f), 1_300L))
    }

    @Test
    fun `encima del hueco no se elige nada`() {
        val g = gesto(hay = sinDiagonal)
        val r = g.deslizar(Punto(0f, 0f), CENTROS.getValue(Lugar.DIAGONAL))
        assertEquals(Final.Cerrar, r)
    }

    @Test
    fun `con el menu abierto un toque al boton lo cierra`() {
        assertEquals(Final.Cerrar, gesto(yaAbierto = true).soltar(Punto(0f, 0f), 1_100L))
    }

    @Test
    fun `con el menu abierto pulsar la equis y deslizar elige`() {
        assertEquals(Final.Elegir(Lugar.ARRIBA), gesto(yaAbierto = true).deslizar(Punto(0f, 0f), Punto(0f, -45f)))
    }

    @Test
    fun `volver al centro despues de armarse cierra`() {
        val g = gesto()
        g.mover(Punto(0f, -40f))
        assertTrue(g.armado)
        g.mover(Punto(0f, -10f))
        assertEquals(Resaltado.Nada, g.resaltado)
        assertFalse("Bajo 24 dp se desarma", g.armado)
        // Se alejó más que un toque: cierra, no deja el menú abierto.
        assertEquals(Final.Cerrar, g.soltar(Punto(0f, -10f), 1_400L))
    }

    @Test
    fun `armado entre 24 y 32 dp vuelve a mirar el angulo con la histeresis`() {
        // Como el lugarDe de «Cómo se hace»: ya armado, de 24 dp para arriba el ángulo sigue mandando.
        val g = gesto()
        g.mover(Punto(0f, -40f))
        g.mover(Punto(0f, -28f))
        assertEquals(Resaltado.Opcion(Lugar.ARRIBA), g.resaltado)
        assertTrue(g.armado)
        // Hacia la orilla sin bajar de 24 (26.9 dp, a 21.8°): cancela.
        val orilla = gesto()
        orilla.mover(Punto(0f, -33f))
        orilla.mover(Punto(25f, -10f))
        assertEquals(Resaltado.Cancelar, orilla.resaltado)
        assertEquals(Final.Cerrar, orilla.soltar(Punto(25f, -10f), 1_300L))
        // Hacia ↖ sin bajar de 24 (29.7 dp, a 135°): elige ↖.
        val diagonal = gesto()
        diagonal.mover(Punto(0f, -33f))
        assertEquals(Final.Elegir(Lugar.DIAGONAL), diagonal.deslizar(Punto(0f, -33f), Punto(-21f, -21f)))
        // A 116° (pasó la frontera de 112.5 por menos de 4°) sigue en ↑, como a 40 dp.
        val r = 28.0
        val a = Math.toRadians(116.0)
        val frontera = gesto()
        frontera.mover(Punto(0f, -40f))
        frontera.mover(Punto((r * Math.cos(a)).toFloat(), (-r * Math.sin(a)).toFloat()))
        assertEquals(Resaltado.Opcion(Lugar.ARRIBA), frontera.resaltado)
        // Sin armarse antes, a 28 dp todavía no hay nada.
        val h = gesto()
        h.mover(Punto(0f, -28f))
        assertEquals(Resaltado.Nada, h.resaltado)
        assertFalse(h.armado)
    }

    @Test
    fun `en zurdo todo va en espejo`() {
        assertEquals(Final.Elegir(Lugar.ARRIBA), gesto(zurdo = true).deslizar(Punto(0f, 0f), Punto(0f, -40f)))
        assertEquals(Final.Elegir(Lugar.DIAGONAL), gesto(zurdo = true).deslizar(Punto(0f, 0f), Punto(30f, -30f)))
        assertEquals(Final.Elegir(Lugar.LADO), gesto(zurdo = true).deslizar(Punto(0f, 0f), Punto(40f, 0f)))
        assertEquals(Final.Cerrar, gesto(zurdo = true).deslizar(Punto(0f, 0f), Punto(-40f, 0f)))
        val sobre = CENTROS.getValue(Lugar.DIAGONAL).let { Punto(-it.x, it.y) }
        assertEquals(Final.Elegir(Lugar.DIAGONAL), gesto(zurdo = true).deslizar(Punto(0f, 0f), sobre))
    }

    @Test
    fun `cada cambio vibra distinto`() {
        val g = gesto()
        assertNull(g.mover(Punto(0f, -10f)))
        assertEquals(Vibracion.ARMARSE, g.mover(Punto(0f, -40f)))
        assertNull(g.mover(Punto(0f, -50f)))
        assertEquals(Vibracion.CAMBIAR, g.mover(Punto(-40f, -40f)))
        assertEquals(Vibracion.DESARMARSE, g.mover(Punto(40f, 40f)))
        assertNull(g.mover(Punto(0f, 5f)))
        assertEquals(Vibracion.ARMARSE, g.mover(Punto(-45f, 0f)))
    }

    @Test
    fun `el rebote dura 300 ms desde que el boton eligio o se cerro`() {
        val r = Rebote()
        assertFalse(r.esRebote(5_000L))
        r.marcar(5_000L)
        assertFalse(r.esRebote(4_999L))
        assertTrue(r.esRebote(5_000L))
        assertTrue(r.esRebote(5_299L))
        assertFalse(r.esRebote(5_300L))
        r.olvidar()
        assertFalse(r.esRebote(5_100L))
    }
}
