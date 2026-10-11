package mx.papeleriapos.escaner.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class LocalizadorTest {
    private val loc = Localizador()

    private fun Localizador.procesar(l: Luma): Localizacion {
        val (b, p) = Planos.buffer(l)
        return procesar(b, p, l.ancho, l.alto)
    }

    /** Si el código girado (con sus zonas en blanco y el alto de [Escena]) cabe en el cuadro. */
    private fun cabe(s: Simbologia, t: String, m: Float, grados: Float, ancho: Int = 1280, alto: Int = 720): Boolean {
        val lc = (Barras.para(s, t).size + 2 * Barras.zonaEnBlanco(s)) * m
        val g = Math.toRadians(grados.toDouble())
        val w = lc * abs(cos(g)) + 0.35 * lc * abs(sin(g)); val h = lc * abs(sin(g)) + 0.35 * lc * abs(cos(g))
        return w <= ancho * 0.95 && h <= alto * 0.95
    }

    @Test
    fun `encuentra y verifica EAN-13, Code 128 e ITF a 2, 3 y 5 px por modulo y 0, 20, 45 y 90 grados`() {
        val fallas = ArrayList<String>()
        var casos = 0; var omitidos = 0
        for ((s, t) in CODIGOS) for (m in floatArrayOf(2f, 3f, 5f)) for (g in floatArrayOf(0f, 20f, 45f, 90f)) {
            if (!cabe(s, t, m, g)) {
                omitidos++; continue
            }
            casos++
            val real = Barras.para(s, t).size * m
            val r = loc.procesar(Planos.codigo(s, t, m, base = Escena(giroGrados = g, ruido = 3f)))
            val err = hypot(r.centroX - 640.0, r.centroY - 360.0)
            val caso = "${s.nombre} m=$m g=$g"
            when {
                !r.encontrado || r.tipo != 1 || !r.verificado -> fallas.add("$caso: enc=${r.encontrado} tipo=${r.tipo} ver=${r.verificado}")
                err > 0.05 * real + 12 -> fallas.add("$caso: centro a $err px")
                abs(r.largo / real - 1f) > 0.1f -> fallas.add("$caso: largo ${r.largo} contra $real")
                Planos.difAngulo(r.anguloRad, g) > 15f -> fallas.add("$caso: ángulo ${Planos.difAngulo(r.anguloRad, g)}°")
                abs(r.moduloPx - m) > 1f -> fallas.add("$caso: módulo ${r.moduloPx}")
                r.cortado -> fallas.add("$caso: dice cortado")
            }
        }
        println("matriz: $casos casos, $omitidos no caben en 1280x720, ${fallas.size} fallas")
        assertTrue(fallas.joinToString("\n"), fallas.isEmpty())
        // Solo el ITF-14 a 5 px/módulo girado 45° o 90° (775 px) no cabe en los 720 px de alto.
        assertEquals(2, omitidos)
    }

    @Test
    fun `con desenfoque lo sigue encontrando y la nitidez baja`() {
        for (m in floatArrayOf(3f, 5f)) {
            val nitidez = FloatArray(4)
            for (b in 0..3) {
                val r = loc.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", m, base = Escena(desenfoque = b, ruido = 3f)))
                assertTrue("m=$m desenfoque=$b", r.encontrado && r.tipo == 1)
                if (b <= 1 || m >= 5f) {
                    assertTrue("m=$m desenfoque=$b verificado", r.verificado)
                    assertTrue("m=$m desenfoque=$b centro ${r.centroX}", abs(r.centroX - 640f) < 0.05f * 95 * m + 12)
                }
                nitidez[b] = r.nitidez
            }
            println("m=$m nitidez por desenfoque 0..3: ${nitidez.joinToString { "%.3f".format(it) }}")
            assertTrue("nítido ≥ 0.4: ${nitidez[0]}", nitidez[0] >= 0.4f)
            for (b in 1..3) assertTrue("baja con el desenfoque: ${nitidez.toList()}", nitidez[b] < nitidez[b - 1])
            // Por debajo de borrosoEntra de la guía desde desenfoque 2.
            assertTrue("desenfoque 2 < 0.15: ${nitidez[2]}", nitidez[2] < 0.15f)
        }
    }

    @Test
    fun `el codigo movido se ve borroso`() {
        val quieto = loc.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", 4f, base = Escena(ruido = 3f))).nitidez
        val movido = loc.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", 4f, base = Escena(movido = 4, ruido = 3f)))
        assertTrue(movido.encontrado)
        assertTrue("quieto $quieto, movido ${movido.nitidez}", movido.nitidez < 0.2f && quieto > 0.4f)
    }

    @Test
    fun `desplazado, el centro sigue al codigo`() {
        for ((fx, fy) in listOf(0.2f to 0.5f, 0.8f to 0.5f, 0.5f to 0.22f, 0.5f to 0.8f)) {
            val r = loc.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", 3f, base = Escena(centroX = fx, centroY = fy, ruido = 3f)))
            val err = hypot(r.centroX - 1280.0 * fx, r.centroY - 720.0 * fy)
            assertTrue("($fx, $fy): ver=${r.verificado} error=$err", r.verificado && err < 20)
            assertFalse("($fx, $fy) no está cortado", r.cortado)
        }
    }

    @Test
    fun `cortado por el borde`() {
        // EAN-13 a 4 px/módulo (452 px con zonas): centrado en x = 0.95 queda la mitad fuera.
        for ((fx, giro) in listOf(0.95f to 0f, 0.05f to 0f, 0.95f to 20f)) {
            val r = loc.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", 4f, base = Escena(centroX = fx, giroGrados = giro, ruido = 3f)))
            assertTrue("x=$fx giro=$giro encontrado", r.encontrado && r.tipo == 1)
            assertTrue("x=$fx giro=$giro cortado (centro ${r.centroX}, largo ${r.largo})", r.cortado)
        }
        // Barras horizontales (90°) que se salen por abajo.
        val abajo = loc.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", 4f, base = Escena(centroY = 0.92f, giroGrados = 90f, ruido = 3f)))
        assertTrue("abajo cortado", abajo.encontrado && abajo.cortado)
        // Completo cerca del borde: no.
        val completo = loc.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", 4f, base = Escena(centroX = 0.75f, ruido = 3f)))
        assertTrue(completo.verificado)
        assertFalse("completo no está cortado", completo.cortado)
    }

    @Test
    fun `escenas sin codigo no se verifican`() {
        val escenas = ArrayList<Pair<String, Luma>>()
        escenas.add("fondo" to Planos.fondo())
        escenas.add("plano con ruido" to Lienzo.dibujar(BooleanArray(1), 0, 1280, 720, Escena(anchoRelativo = 1e-4f, ruido = 6f)))
        for (s in 0 until 30) {
            escenas.add("trazos $s" to Planos.trazos(1000 + s))
            escenas.add("texto $s" to Planos.texto(2000 + s))
            escenas.add("rayada $s" to Planos.rayada(3000 + s))
        }
        val verificados = escenas.filter { (_, l) -> loc.procesar(l).let { it.encontrado && it.tipo == 1 && it.verificado } }.map { it.first }
        val encontrados = escenas.count { (_, l) -> loc.procesar(l).let { it.encontrado && it.tipo == 1 } }
        println("sin código: ${escenas.size} escenas, $encontrados con región 1D, ${verificados.size} verificadas")
        assertTrue("verificadas sin código: $verificados", verificados.isEmpty())
        // Para comparar: sin ruido, el plano no tiene ni región.
        assertFalse(loc.procesar(Planos.fondo()).encontrado)
    }

    @Test
    fun `prefiere el codigo mas cercano al punto preferido`() {
        val a = Planos.codigo(Simbologia.EAN_13, "7509993456786", 3f, base = Escena(centroX = 0.27f))
        val b = Planos.codigo(Simbologia.CODE_128, "ZZP42", 3f, base = Escena(centroX = 0.73f))
        // Los dos en la misma escena: donde uno es fondo se toma el otro.
        val fondo = Escena().fondo.toByte()
        for (i in a.pixeles.indices) if (a.pixeles[i] == fondo) a.pixeles[i] = b.pixeles[i]
        val (buf, paso) = Planos.buffer(a)
        val izq = loc.procesar(buf, paso, 1280, 720, preferidoX = 300f, preferidoY = 360f)
        assertTrue("izquierda: ${izq.centroX}", izq.verificado && abs(izq.centroX - 0.27f * 1280) < 20)
        val der = loc.procesar(buf, paso, 1280, 720, preferidoX = 980f, preferidoY = 360f)
        assertTrue("derecha: ${der.centroX}", der.verificado && abs(der.centroX - 0.73f * 1280) < 20)
    }

    @Test
    fun `mide el reflejo y la luz`() {
        val normal = loc.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", 3f, base = Escena(ruido = 3f)))
        assertTrue("sin reflejo: ${normal.saturados}", normal.saturados < 0.03f)
        assertTrue("con luz: ${normal.brilloCentral}", normal.brilloCentral > 100f)
        val reflejo = loc.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", 3f, base = Escena(reflejo = 0.4f, ruido = 3f)))
        assertTrue("reflejo: ${reflejo.saturados}", reflejo.encontrado && reflejo.saturados > 0.06f)
        val oscuro = Lienzo.ganancia(Planos.codigo(Simbologia.EAN_13, "7509993456786", 3f, base = Escena(ruido = 3f)), 0.2f)
        val r = loc.procesar(oscuro)
        assertTrue("oscuro: ${r.brilloCentral}", r.brilloCentral in 0f..45f)
    }

    @Test
    fun `la diferencia entre cuadros mide el movimiento`() {
        val l = Localizador()
        val quieto = Planos.codigo(Simbologia.EAN_13, "7509993456786", 3f, base = Escena(ruido = 3f))
        assertEquals(-1f, l.procesar(quieto).diferenciaCuadro, 0f)
        assertEquals(0f, l.procesar(quieto).diferenciaCuadro, 0f)
        val movido = Planos.codigo(Simbologia.EAN_13, "7509993456786", 3f, base = Escena(centroX = 0.6f, ruido = 3f))
        assertTrue(l.procesar(movido).diferenciaCuadro > 1f)
    }

    @Test
    fun `respeta el paso de fila y deja la posicion del buffer como estaba`() {
        val luma = Planos.codigo(Simbologia.ITF, "17509990000019", 3f, base = Escena(giroGrados = 20f, ruido = 3f))
        val (b0, p0) = Planos.buffer(luma)
        val r0 = loc.procesar(b0, p0, 1280, 720)
        val esperado = floatArrayOf(r0.centroX, r0.centroY, r0.largo, r0.moduloPx, r0.nitidez)
        for ((relleno, directo) in listOf(64 to true, 13 to false)) {
            val (b, p) = Planos.buffer(luma, relleno, directo)
            b.position(7)
            val r = loc.procesar(b, p, 1280, 720)
            assertEquals(7, b.position())
            assertTrue(r.verificado)
            val obtenido = floatArrayOf(r.centroX, r.centroY, r.largo, r.moduloPx, r.nitidez)
            for (i in esperado.indices) assertEquals("relleno $relleno campo $i", esperado[i], obtenido[i], 1e-3f)
        }
    }

    @Test
    fun `un cuadro mas grande que la capacidad inicial amplia los arreglos una vez`() {
        val chico = Localizador(anchoMaximo = 640, altoMaximo = 480)
        val r = chico.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", 5f, base = Escena(giroGrados = 90f, ruido = 3f)))
        assertTrue(r.verificado)
        // Y uno diminuto no truena.
        val diminuto = chico.procesar(Luma(16, 16))
        assertFalse(diminuto.encontrado)
    }

    @Test
    fun `las esquinas encierran el codigo`() {
        val m = 3f
        val r = loc.procesar(Planos.codigo(Simbologia.EAN_13, "7509993456786", m, base = Escena(centroX = 0.4f, giroGrados = 30f, ruido = 3f)))
        assertTrue(r.verificado)
        val caja = Caja.deEsquinas(r.esquinas(FloatArray(8)))
        assertTrue("contiene el centro: $caja", caja.contiene(0.4f * 1280, 360f))
        // El código girado 30° mide 285 px de largo: su caja mide ~247 de ancho sin contar el alto de las barras.
        assertTrue("ancho ${caja.ancho}", caja.ancho > 0.8f * 95 * m * cos(Math.toRadians(30.0)).toFloat())
    }

    @Test
    fun `no asigna memoria por cuadro`() {
        val hilos = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val l = Localizador()
        val (buf, paso) = Planos.buffer(Planos.codigo(Simbologia.EAN_13, "7509993456786", 3f, base = Escena(giroGrados = 10f, ruido = 3f)))
        repeat(2000) { l.procesar(buf, paso, 1280, 720) }
        val antes = hilos.currentThreadAllocatedBytes
        repeat(1000) { l.procesar(buf, paso, 1280, 720) }
        val porCuadro = (hilos.currentThreadAllocatedBytes - antes) / 1000.0
        println("bytes asignados por cuadro (1280x720): $porCuadro")
        assertTrue("asigna $porCuadro bytes por cuadro", porCuadro < 1.0)
    }

    companion object {
        val CODIGOS = listOf(
            Simbologia.EAN_13 to "7509993456786",
            Simbologia.CODE_128 to "ZZP42",
            Simbologia.ITF to "17509990000019",
        )
    }
}
