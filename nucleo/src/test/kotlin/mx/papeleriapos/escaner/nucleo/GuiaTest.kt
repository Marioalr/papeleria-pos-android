package mx.papeleriapos.escaner.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.random.Random

class GuiaTest {
    private val o = Observacion()
    private var t = 0L

    /** Lo que se mostró en un cuadro, con su tiempo en ms. */
    private data class Cuadro(val ms: Long, val i: Indicacion)

    /** Un cuadro «bueno»: candidato centrado, de buen tamaño, nítido y con luz. */
    private fun bueno() {
        o.reiniciar()
        o.candidato = true; o.largoRelativo = 0.5f; o.nitidez = 0.5f; o.moduloPx = 3f; o.brillo = 140f
        o.proporcionEnfoque = 0.3f
    }

    /** Corre [n] cuadros a [fps] con [ajuste] sobre un cuadro bueno. */
    private fun correr(g: Guia, n: Int, fps: Double = 30.0, ajuste: Observacion.(Int) -> Unit = {}): List<Cuadro> {
        val paso = (1e9 / fps).toLong()
        val salida = ArrayList<Cuadro>(n)
        repeat(n) { k ->
            bueno(); o.ajuste(k); o.nanos = t
            salida.add(Cuadro(t / 1_000_000, g.actualizar(o)))
            t += paso
        }
        return salida
    }

    /** Ms (desde el primer cuadro de [c]) en que aparece [i] por primera vez; −1 si nunca. */
    private fun primera(c: List<Cuadro>, i: Indicacion) = c.firstOrNull { it.i == i }?.let { it.ms - c.first().ms } ?: -1L

    private fun cambios(c: List<Cuadro>) = c.zipWithNext().count { (a, b) -> a.i != b.i }

    /** Los tramos de [c] con el ms en que empieza cada uno, para los mensajes. */
    private fun tramos(c: List<Cuadro>): String {
        val s = StringBuilder()
        for ((k, x) in c.withIndex()) if (k == 0 || c[k - 1].i != x.i) s.append(" ${x.i}@${x.ms - c.first().ms}")
        return s.toString()
    }

    /**
     * Parpadeo: una indicación que se quita antes de los 700 ms en pantalla por otra que no es de mayor prioridad (sin
     * contar el primer tramo, que es lo que había antes, ni el último, que corta la secuencia). Que una de mayor
     * prioridad reemplace antes de tiempo es lo que pide el diseño (p. ej. «Leyendo…» y luego «Mueve…»).
     */
    private fun parpadeos(c: List<Cuadro>): Int {
        val inicios = c.indices.filter { it == 0 || c[it - 1].i != c[it].i }
        return (1 until inicios.size - 1).count {
            c[inicios[it + 1]].ms - c[inicios[it]].ms < 700 && prioridadDe(c[inicios[it + 1]].i) <= prioridadDe(c[inicios[it]].i)
        }
    }

    @Test
    fun `codigo a la izquierda pide IZQUIERDA al terminar la gracia`() {
        val izquierda: Observacion.(Int) -> Unit = { dx = -0.3f }
        val derecha: Observacion.(Int) -> Unit = { dx = 0.3f }
        val arriba: Observacion.(Int) -> Unit = { dy = -0.4f }
        val abajo: Observacion.(Int) -> Unit = { dy = 0.4f }
        for ((ajuste, esperada) in listOf(
            izquierda to Indicacion.IZQUIERDA,
            derecha to Indicacion.DERECHA,
            arriba to Indicacion.ARRIBA,
            abajo to Indicacion.ABAJO,
        )) {
            val g = Guia()
            val c = correr(g, 40, ajuste = ajuste)
            val ms = primera(c, esperada)
            assertTrue("$esperada a los $ms ms", ms in 500..540)
            // Durante la gracia no hay pistas de dirección ni un «leyendo» engañoso.
            assertTrue(c.filter { it.ms - c.first().ms < 500 }.all { it.i == Indicacion.APUNTA })
            assertEquals(1, cambios(c))
        }
    }

    @Test
    fun `tocar un solo borde da la direccion y no ALEJATE`() {
        val g = Guia()
        val c = correr(g, 40) { dx = -0.05f; cortado = true; bordeX = -1 }
        assertEquals(Indicacion.IZQUIERDA, c.last().i)
        assertFalse(c.any { it.i == Indicacion.ALEJATE })
        val g2 = Guia()
        assertEquals(Indicacion.ABAJO, correr(g2, 40) { cortado = true; bordeY = 1 }.last().i)
    }

    @Test
    fun `grande o cortado por ambos lados pide ALEJATE`() {
        val grande = correr(Guia(), 30) { largoRelativo = 0.95f }
        assertTrue("grande a los ${primera(grande, Indicacion.ALEJATE)} ms", primera(grande, Indicacion.ALEJATE) in 300..340)
        val cortado = correr(Guia(), 30) { largoRelativo = 0.7f; cortado = true }
        assertEquals(Indicacion.ALEJATE, cortado.last().i)
    }

    @Test
    fun `chico con zoom al tope pide ACERCATE y sin tope no`() {
        // Por el largo (módulo desconocido).
        val porLargo = correr(Guia(), 30) { largoRelativo = 0.2f; moduloPx = 0f }
        assertEquals(Indicacion.ACERCATE, porLargo.last().i)
        // Por el módulo, que manda cuando la imagen es nítida.
        val porModulo = correr(Guia(), 30) { moduloPx = 1.5f }
        assertEquals(Indicacion.ACERCATE, porModulo.last().i)
        // Sin el zoom al tope, primero se sube el zoom: no se pide acercarse.
        val sinTope = correr(Guia(), 60) { largoRelativo = 0.2f; moduloPx = 0f; zoomAlTope = false }
        assertFalse(sinTope.any { it.i == Indicacion.ACERCATE })
        assertEquals(Indicacion.LEYENDO, sinTope.last().i)
    }

    @Test
    fun `borroso pide QUIETO, ENFOCANDO si el enfoque trabaja y ALEJATE si ya no alcanza a enfocar`() {
        assertEquals(Indicacion.QUIETO, correr(Guia(), 30) { nitidez = 0.08f }.last().i)
        assertEquals(Indicacion.ENFOCANDO, correr(Guia(), 30) { nitidez = 0.08f; enfocando = true }.last().i)
        assertEquals(Indicacion.ALEJATE, correr(Guia(), 30) { nitidez = 0.08f; proporcionEnfoque = 1f }.last().i)
        assertEquals(Indicacion.QUIETO, correr(Guia(), 30) { movimiento = 0.08f }.last().i)
        assertEquals(Indicacion.LEYENDO, correr(Guia(), 30).last().i)
    }

    @Test
    fun `poca luz pide luz solo tras 500 ms de oscuridad, haya o no codigo`() {
        for (hayCodigo in listOf(true, false)) {
            val c = correr(Guia(), 40) { brillo = 30f; candidato = hayCodigo }
            val ms = primera(c, Indicacion.MAS_LUZ)
            assertTrue("con código=$hayCodigo: MAS_LUZ a los $ms ms", ms in 500..540)
        }
        // Un apagón breve (300 ms) no la pide.
        val g = Guia()
        val breve = correr(g, 9) { brillo = 30f } + correr(g, 30)
        assertFalse(breve.any { it.i == Indicacion.MAS_LUZ })
        // Al volver la luz se quita, después de su tiempo en pantalla.
        val g2 = Guia()
        val oscuro = correr(g2, 20) { brillo = 30f }
        val desde = oscuro.first { it.i == Indicacion.MAS_LUZ }.ms
        val luz = correr(g2, 40) { brillo = 100f }
        val sale = luz.first { it.i != Indicacion.MAS_LUZ }
        assertEquals(Indicacion.LEYENDO, sale.i)
        assertTrue("sale a los ${sale.ms - desde} ms de mostrarse", sale.ms - desde >= 700)
    }

    @Test
    fun `el reflejo gana a la direccion`() {
        val c = correr(Guia(), 40) { saturados = 0.1f; dx = -0.3f }
        assertEquals(Indicacion.REFLEJO, c.last().i)
        assertFalse(c.any { it.i == Indicacion.IZQUIERDA })
    }

    @Test
    fun `valores oscilando alrededor de un umbral no hacen parpadear`() {
        val r = Random(5)
        // Largo alrededor de chicoEntra (0.30).
        val largo = correr(Guia(), 200) { largoRelativo = 0.27f + r.nextFloat() * 0.08f; moduloPx = 0f }
        assertEquals("largo: ${tramos(largo)}", 0, parpadeos(largo))
        assertTrue("largo: ${tramos(largo)}", cambios(largo) <= 3)
        // Descentrado alrededor de descentradoEntra (0.20).
        val descentrado = correr(Guia(), 200) { dx = 0.15f + r.nextFloat() * 0.10f }
        assertEquals("dx: ${tramos(descentrado)}", 0, parpadeos(descentrado))
        assertTrue("dx: ${tramos(descentrado)}", cambios(descentrado) <= 3)
        assertFalse(descentrado.any { it.i == Indicacion.IZQUIERDA })
        // Nitidez entre borrosoEntra (0.15) y borrosoSale (0.22).
        val nit = correr(Guia(), 200) { nitidez = 0.12f + r.nextFloat() * 0.13f }
        assertEquals("nitidez: ${tramos(nit)}", 0, parpadeos(nit))
        assertTrue("nitidez: ${tramos(nit)}", cambios(nit) <= 3)
        // Reflejo que va y viene en cada cuadro: nunca dura lo suficiente para mostrarse.
        val reflejo = correr(Guia(), 200) { k -> saturados = if (k % 2 == 0) 0.1f else 0f }
        assertFalse(reflejo.any { it.i == Indicacion.REFLEJO })
        assertEquals(1, cambios(reflejo))
        // Dos condiciones que se alternan cada dos cuadros.
        val alterna = correr(Guia(), 200) { k -> if (k / 2 % 2 == 0) largoRelativo = 0.95f }
        assertEquals("alterna: ${tramos(alterna)}", 0, parpadeos(alterna))
        assertTrue("alterna: ${tramos(alterna)}", cambios(alterna) <= 3)
        for ((n, c) in listOf("largo" to largo, "dx" to descentrado, "nitidez" to nit, "reflejo" to reflejo, "alterna" to alterna)) {
            println("oscilando $n (200 cuadros, 6.6 s):${tramos(c)}")
        }
        // Simulación como la de la investigación: escenarios seguidos, cada uno debe asentarse sin ir y venir.
        val g = Guia()
        val escenarios = listOf<Observacion.(Int) -> Unit>(
            { candidato = false },
            { largoRelativo = 0.2f; moduloPx = 0f },
            { largoRelativo = 0.28f + r.nextFloat() * 0.06f; moduloPx = 0f },
            { dx = 0.25f },
            { dx = 0.15f },
            { nitidez = 0.10f; movimiento = 0.08f },
            { largoRelativo = 0.85f; nitidez = 0.08f; proporcionEnfoque = 1f },
            { saturados = 0.10f },
            {},
        )
        for ((k, e) in escenarios.withIndex()) {
            val c = correr(g, 30, ajuste = e)
            assertEquals("escenario $k: ${c.map { it.i }}", 0, parpadeos(c))
            assertTrue("escenario $k: ${c.map { it.i }}", cambios(c) <= 1)
        }
    }

    @Test
    fun `LEIDO es inmediato y luego respeta su tiempo en pantalla`() {
        val g = Guia()
        val antes = correr(g, 40) { dx = -0.3f }
        assertEquals(Indicacion.IZQUIERDA, antes.last().i)
        val lectura = correr(g, 1) { leido = true }
        assertEquals(Indicacion.LEIDO, lectura.single().i)
        val despues = correr(g, 40)
        val sale = despues.first { it.i != Indicacion.LEIDO }
        assertEquals(Indicacion.LEYENDO, sale.i)
        assertTrue("LEIDO duró ${sale.ms - lectura.single().ms} ms", sale.ms - lectura.single().ms >= 700)
        // Un LEIDO de inmediato aunque haya otro LEIDO en pantalla (lecturas seguidas).
        correr(g, 3) { leido = true }.forEach { assertEquals(Indicacion.LEIDO, it.i) }
    }

    @Test
    fun `tolera perdidas breves y vuelve a APUNTA si el codigo se va`() {
        val g = Guia()
        assertEquals(Indicacion.ACERCATE, correr(g, 30) { largoRelativo = 0.2f; moduloPx = 0f }.last().i)
        // 5 cuadros sin candidato (menos de cuadrosPerdidosParaReiniciar): sigue igual.
        assertTrue(correr(g, 5) { candidato = false }.all { it.i == Indicacion.ACERCATE })
        assertTrue(correr(g, 10) { largoRelativo = 0.2f; moduloPx = 0f }.all { it.i == Indicacion.ACERCATE })
        // Se fue: APUNTA, pero no antes de 6 cuadros + 300 ms de confirmación.
        val fuera = correr(g, 40) { candidato = false }
        val ms = primera(fuera, Indicacion.APUNTA)
        assertTrue("APUNTA a los $ms ms", ms >= 5 * 33 + 300)
        assertEquals(Indicacion.APUNTA, fuera.last().i)
    }

    @Test
    fun `tiempo minimo en pantalla salvo que entre una de mayor prioridad`() {
        val g = Guia()
        val chico = correr(g, 30) { largoRelativo = 0.2f; moduloPx = 0f }
        val mostrado = chico.first { it.i == Indicacion.ACERCATE }.ms
        // Ya bien colocado justo después: LEYENDO (menor prioridad) espera los 700 ms.
        val bien = correr(g, 40)
        val leyendo = bien.first { it.i == Indicacion.LEYENDO }.ms
        assertTrue("LEYENDO a los ${leyendo - mostrado} ms de ACERCATE", leyendo - mostrado >= 700)

        // Un reflejo (mayor prioridad) entra en cuanto se confirma, aunque no hayan pasado los 700 ms.
        val g2 = Guia()
        val c2 = correr(g2, 12) { largoRelativo = 0.2f; moduloPx = 0f }
        val acercate = c2.first { it.i == Indicacion.ACERCATE }.ms
        val reflejo = correr(g2, 20) { largoRelativo = 0.2f; moduloPx = 0f; saturados = 0.1f }
        val ms = reflejo.first { it.i == Indicacion.REFLEJO }.ms
        assertTrue("REFLEJO a los ${ms - acercate} ms de ACERCATE", ms - acercate < 700)
        // El reflejo es de un solo cuadro: se sostiene 3 cuadros (~67 ms) y luego se confirma (300 ms).
        assertTrue("REFLEJO a los ${ms - reflejo.first().ms} ms de empezar", ms - reflejo.first().ms in 300..420)
    }

    @Test
    fun `confirmacion por cuadros y por tiempo`() {
        // A 120 cuadros por segundo, 3 cuadros son 25 ms: manda el tiempo (300 ms).
        val rapido = correr(Guia(), 60, fps = 120.0) { largoRelativo = 0.95f }
        assertTrue("a 120 fps: ${primera(rapido, Indicacion.ALEJATE)} ms", primera(rapido, Indicacion.ALEJATE) in 300..310)
        // A 2 cuadros por segundo, a los 500 ms ya pasó el tiempo pero son 2 cuadros: manda la cuenta (3 cuadros).
        val lento = correr(Guia(), 4, fps = 2.0) { largoRelativo = 0.95f }
        assertEquals(listOf(Indicacion.APUNTA, Indicacion.APUNTA, Indicacion.ALEJATE, Indicacion.ALEJATE), lento.map { it.i })
    }

    @Test
    fun `reiniciar vuelve a APUNTA`() {
        val g = Guia()
        correr(g, 40) { dx = -0.3f }
        assertEquals(Indicacion.IZQUIERDA, g.actual)
        g.reiniciar()
        assertEquals(Indicacion.APUNTA, g.actual)
        // Y la gracia vuelve a empezar.
        val c = correr(g, 10) { dx = -0.3f }
        assertTrue(c.all { it.i == Indicacion.APUNTA })
    }

    @Test
    fun `no asigna memoria por cuadro`() {
        val hilos = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val g = Guia()
        val obs = Observacion()
        var tt = 0L
        fun cuadro(k: Int) {
            obs.reiniciar()
            obs.candidato = k % 50 < 40; obs.largoRelativo = 0.2f + (k % 7) * 0.12f; obs.dx = (k % 11 - 5) * 0.06f
            obs.nitidez = (k % 13) * 0.05f; obs.brillo = if (k % 97 < 30) 30f else 140f; obs.saturados = (k % 17) * 0.01f
            obs.leido = k % 101 == 0; obs.nanos = tt; tt += 33_333_333L
            g.actualizar(obs)
        }
        for (k in 0 until 20000) cuadro(k)
        val antes = hilos.currentThreadAllocatedBytes
        for (k in 0 until 20000) cuadro(k)
        val porCuadro = (hilos.currentThreadAllocatedBytes - antes) / 20000.0
        println("guía: bytes asignados por cuadro: $porCuadro")
        assertTrue("asigna $porCuadro bytes por cuadro", porCuadro < 1.0)
    }
}
