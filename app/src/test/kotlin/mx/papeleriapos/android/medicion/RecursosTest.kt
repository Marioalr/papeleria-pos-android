package mx.papeleriapos.android.medicion

import mx.papeleriapos.android.csv.Csv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class RecursosTest {
    private val status = """
        Name:	papeleriapos.android
        Umask:	0077
        State:	S (sleeping)
        VmPeak:	15623456 kB
        VmHWM:	  301234 kB
        VmRSS:	  287654 kB
        RssAnon:	  120000 kB
        RssFile:	  160000 kB
        Threads:	57
    """.trimIndent()

    @Test
    fun `lee VmRSS, RssAnon e hilos de proc self status`() {
        assertEquals(EstadoProceso(287654, 120000, 57), EstadoProceso.leer(status))
    }

    @Test
    fun `un kernel viejo sin RssAnon deja ese campo en -1`() {
        val viejo = status.lines().filterNot { it.startsWith("RssAnon") }.joinToString("\n")
        assertEquals(EstadoProceso(287654, -1, 57), EstadoProceso.leer(viejo))
        assertEquals(EstadoProceso(), EstadoProceso.leer(""))
        assertEquals(EstadoProceso(), EstadoProceso.leer("VmRSS: mucho kB\nThreads: x"))
    }

    @Test
    fun `de los bytes de proc self status sale lo mismo que del texto`() {
        val buffer = ByteArray(4096)
        for (texto in listOf(status, status.lines().filterNot { it.startsWith("RssAnon") }.joinToString("\n"), "", "VmRSS: mucho kB\nThreads: x")) {
            val b = texto.toByteArray(Charsets.US_ASCII)
            b.copyInto(buffer)
            assertEquals(texto, EstadoProceso.leer(texto), EstadoProceso.leer(buffer, b.size))
        }
        // Lo que sobra del buffer de una lectura anterior no se lee.
        val corto = "Threads:\t9\n".toByteArray(Charsets.US_ASCII)
        corto.copyInto(buffer)
        assertEquals(EstadoProceso(-1, -1, 9), EstadoProceso.leer(buffer, corto.size))
    }

    private fun calibrado(crudo: Int, n: Int = 15, enchufado: Boolean = false) =
        NormalizadorCorriente().also { c -> repeat(n) { c.observar(crudo, enchufado) } }

    @Test
    fun `con la convencion de Android (uA, negativo al descargar) no cambia nada`() {
        val c = calibrado(-350_000)
        assertTrue(c.calibrado)
        assertEquals(1, c.factor)
        assertEquals(1, c.signo)
        assertEquals(-420_000L, c.microamperes(-420_000))
    }

    @Test
    fun `un equipo que reporta mA y positivo al descargar se corrige`() {
        val c = calibrado(350)
        assertEquals(1000, c.factor)
        assertEquals(-1, c.signo)
        assertEquals(-400_000L, c.microamperes(400))
    }

    @Test
    fun `conectado no calibra (la corriente es la de la carga)`() {
        val c = calibrado(-350_000, n = 30, enchufado = true)
        assertFalse(c.calibrado)
        assertNull(c.microamperes(-350_000))
    }

    @Test
    fun `MIN_VALUE o cero constante es sin medidor`() {
        val min = NormalizadorCorriente().also { it.observar(Int.MIN_VALUE, false) }
        assertTrue(min.sinMedidor)
        val ceros = NormalizadorCorriente().also { c -> repeat(NormalizadorCorriente.CEROS_SIN_MEDIDOR) { c.observar(0, false) } }
        assertTrue(ceros.sinMedidor)
        // Un cero suelto después de lecturas buenas no lo vuelve «sin medidor».
        val bueno = calibrado(-300_000).also { c -> repeat(20) { c.observar(0, false) } }
        assertFalse(bueno.sinMedidor)
        assertEquals(0L, bueno.microamperes(0))
    }

    @Test
    fun `la comprobacion con el contador de carga corrige la unidad si da 1000 veces`() {
        // Un equipo con 9 000 mA de mediana se tomó como uA (raro, pero posible con el umbral): el contador lo desmiente.
        val c = calibrado(-12_000)
        assertEquals(1, c.factor)
        val razon = c.comprobar(descargaPorCargaUa = 12_000_000.0, descargaMediaUa = 12_000.0)
        assertEquals(1000.0, razon, 1e-9)
        assertEquals(1000, c.factor)
        // Con la unidad bien, la razón cerca de 1 no cambia nada.
        val bien = calibrado(-300_000)
        bien.comprobar(310_000.0, 300_000.0)
        assertEquals(1, bien.factor)
        assertEquals(310.0 / 300.0, bien.razonCarga, 1e-9)
    }

    @Test
    fun `la descarga por carga sale en uA positivos`() {
        // 25 000 uAh en 5 min = 300 mA.
        assertEquals(300_000.0, NormalizadorCorriente.descargaPorCarga(4_000_000, 3_975_000, 0, 300_000), 1e-6)
        assertTrue(NormalizadorCorriente.descargaPorCarga(1, 0, 10, 10).isNaN())
    }

    private fun lectura(
        t: Long,
        cpu: Long,
        prod: Long = -1,
        ana: Long = -1,
        corriente: Int = -300_000,
        enchufado: Boolean = false,
        carga: Int = 3_000_000,
    ) = LecturaCruda(
        paredMs = t, cpuMs = cpu, heapJavaBytes = 10 * 1024 * 1024, heapNativoBytes = 20 * 1024 * 1024,
        proceso = EstadoProceso(200_000, 90_000, 40), fds = 120, corrienteCruda = corriente, cargaUah = carga,
        voltajeMv = 4000, bateriaPct = 80, tempBatDc = 312, enchufado = enchufado, termico = 1, headroom = Float.NaN,
        cuadrosProducidos = prod, cuadrosAnalizados = ana, estadoCamara = "OPEN",
    )

    @Test
    fun `calcula deltas de CPU y cuadros por segundo, no acumulados`() {
        val calc = CalculadoraRecursos()
        val m0 = calc.calcular(lectura(10_000, 5_000, prod = 100, ana = 90), "escaneando")
        assertEquals(0L, m0.tMs)
        assertEquals(-1L, m0.cpuMs)
        assertTrue(m0.cpuPctNucleo.isNaN())
        assertTrue(m0.fpsAnalisis.isNaN())
        val m1 = calc.calcular(lectura(11_000, 5_250, prod = 130, ana = 115), "escaneando")
        assertEquals(1_000L, m1.tMs)
        assertEquals(250L, m1.cpuMs)
        assertEquals(25.0, m1.cpuPctNucleo, 1e-9)
        assertEquals(30.0, m1.fpsCamara, 1e-9)
        assertEquals(25.0, m1.fpsAnalisis, 1e-9)
        assertEquals(10.0, m1.cpuMsPorCuadro, 1e-9)
        assertEquals(10_240L, m1.heapJavaKb)
        assertEquals(20_480L, m1.heapNativoKb)
        assertEquals(40, m1.hilos)
    }

    @Test
    fun `un contador que vuelve a cero (se volvio a enlazar) cuenta lo de despues, no se pierde el segundo`() {
        val calc = CalculadoraRecursos()
        calc.calcular(lectura(0, 0, prod = 500, ana = 480), "")
        val m = calc.calcular(lectura(1_000, 100, prod = 20, ana = 18), "")
        assertEquals(20.0, m.fpsCamara, 1e-9)
        assertEquals(18.0, m.fpsAnalisis, 1e-9)
        assertEquals(18L, m.analizadosDelta)
        assertEquals(100.0 / 18, m.cpuMsPorCuadro, 1e-9)
        // Sin escáner (−1) no hay dato; un 0 con escáner sí es dato (todavía no llega nada).
        val sin = calc.calcular(lectura(2_000, 200), "")
        assertTrue(sin.fpsAnalisis.isNaN())
        val cero = calc.calcular(lectura(3_000, 300, prod = 0, ana = 0), "")
        assertTrue("El primero tras no haber escáner no tiene con qué comparar", cero.fpsAnalisis.isNaN())
        val uno = calc.calcular(lectura(4_000, 400, prod = 30, ana = 25), "")
        assertEquals(25.0, uno.fpsAnalisis, 1e-9)
    }

    @Test
    fun `con el diagnostico cada 500 ms los cuadros por segundo no saltan 50 por ciento y los promedios salen exactos`() {
        // El escáner analiza 30 cuadros/s pero publica sus contadores cada 500 ms; el monitor los ve cada segundo con un
        // desfase que cambia (el hilo no es exacto): lo que ve es el último publicado.
        val tasa = TasaCuadros()
        val vistos = ArrayList<Double>()
        var t = 0L
        var anterior = -1L
        for (s in 0 until 120) {
            t = s * 1_000L + (s * 137L) % 600
            val publicado = (t / 500) * 500
            val contador = publicado * 30 / 1_000
            val fps = tasa.observar(contador, t)
            if (s >= 4) vistos += fps
            if (anterior >= 0) assertTrue(tasa.delta >= 0)
            anterior = contador
        }
        // Sin ventana, un segundo daría 15 o 45 (±50 %); con ella, cerca de 30.
        assertTrue(vistos.toString(), vistos.all { abs(it - 30.0) <= 30.0 * 0.2 })
        assertEquals(30.0, vistos.average(), 1.5)
    }

    @Test
    fun `al volver a enlazar a media ventana la tasa sigue con sentido`() {
        val tasa = TasaCuadros()
        for (s in 0..5) tasa.observar(s * 30L, s * 1_000L)
        // Se volvió a enlazar: el contador empezó de cero y lleva 12 desde entonces.
        val fps = tasa.observar(12, 6_000)
        assertEquals(12L, tasa.delta)
        assertFalse(fps.isNaN())
        assertTrue("fps $fps", fps in 15.0..30.0)
        assertEquals(30L, tasa.delta.let { tasa.observar(42, 7_000); tasa.delta })
        // Sin escáner, sin dato, y se empieza de nuevo.
        assertTrue(tasa.observar(-1, 8_000).isNaN())
        assertTrue(tasa.observar(5, 9_000).isNaN())
        assertEquals(30.0, tasa.observar(35, 10_000), 1e-9)
    }

    @Test
    fun `el headroom se pregunta una vez cada 10 s y los dos muestreadores reciben el dato`() {
        val cache = CacheHeadroom(intervaloMs = 10_000)
        var preguntas = 0
        val fuente = FuenteHeadroom { preguntas++; 0.42f }
        // La sesión y la prueba de estrés, cada una a 1 Hz y desfasadas 300 ms.
        for (s in 0 until 30) {
            assertEquals(0.42f, cache.leer(s * 1_000L, fuente), 0f)
            assertEquals(0.42f, cache.leer(s * 1_000L + 300, fuente), 0f)
        }
        assertEquals(3, preguntas)
        // Un error del sistema es NaN (sin dato), no 0.
        val falla = CacheHeadroom()
        assertTrue(falla.leer(0, FuenteHeadroom { throw IllegalStateException() }).isNaN())
    }

    @Test
    fun `la hora se escribe igual que con SimpleDateFormat`() {
        for (zona in listOf("UTC", "America/Mexico_City", "Asia/Kolkata", "America/St_Johns")) {
            val z = TimeZone.getTimeZone(zona)
            val formato = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).apply { timeZone = z }
            val hora = HoraIso(z)
            for (ms in listOf(0L, 1_760_000_000_123L, 1_711_900_800_007L, 1_730_617_199_999L)) {
                val sb = StringBuilder()
                hora.agregar(sb, ms)
                assertEquals("$zona $ms", formato.format(Date(ms)), sb.toString())
            }
        }
    }

    @Test
    fun `los decimales salen con punto y redondeados como el CSV`() {
        fun d(v: Double, n: Int) = StringBuilder().also { CsvRecursos.agregarDecimal(it, v, n) }.toString()
        assertEquals("12.0", d(12.0, 1))
        assertEquals("0.420", d(0.42, 3))
        assertEquals("30", d(29.6, 0))
        assertEquals("1.06", d(1.0555, 2))
        assertEquals("-3.5", d(-3.5, 1))
        assertEquals("0.0", d(-0.01, 1))
        for (v in listOf(0.0, 3.14159, 25.25, 99.99, 1234.5678)) assertEquals(Csv.decimal(v, 2), d(v, 2))
    }

    @Test
    fun `la potencia solo se calcula desconectado y ya calibrado`() {
        val calc = CalculadoraRecursos()
        var m = calc.calcular(lectura(0, 0), "")
        assertTrue("Sin calibrar todavía", m.potenciaMw.isNaN())
        for (i in 1..20) m = calc.calcular(lectura(i * 1000L, i * 100L), "")
        assertEquals(-300_000L, m.corrienteUa)
        // 300 mA × 4 V = 1200 mW.
        assertEquals(1200.0, m.potenciaMw, 1e-9)
        val conectado = calc.calcular(lectura(30_000, 3_000, corriente = 500_000, enchufado = true), "")
        assertTrue(conectado.potenciaMw.isNaN())
        assertEquals(500_000L, conectado.corrienteUa)
    }

    @Test
    fun `cada 5 minutos desconectado compara la corriente con el contador de carga`() {
        val calc = CalculadoraRecursos(ventanaCargaMs = 300_000)
        // 300 mA durante 5 min = 25 000 uAh; el contador baja 25 000 uAh.
        for (s in 0..330) {
            calc.calcular(lectura(s * 1000L, s * 100L, carga = (4_000_000 - s * 25_000 / 300)), "")
        }
        val razon = calc.corriente.razonCarga
        assertTrue("razón $razon", razon in 0.9..1.1)
    }

    @Test
    fun `recursos csv trae las columnas pedidas y deja vacio lo que no se sabe`() {
        assertEquals(
            listOf(
                "t_ms", "fase", "cpu_ms", "cpu_pct_nucleo", "heap_java_kb", "heap_nativo_kb", "rss_kb", "rss_anon_kb", "hilos",
                "fds", "corriente_cruda", "corriente_ua", "voltaje_mv", "potencia_mw", "carga_uah", "bateria_pct", "temp_bat_dc",
                "enchufado", "termico", "headroom", "fps_camara", "fps_analisis", "estado_camara", "costo_muestra_us",
            ),
            CsvRecursos.COLUMNAS.take(24),
        )
        val calc = CalculadoraRecursos()
        val m0 = calc.calcular(lectura(0, 0, prod = 0, ana = 0, corriente = Int.MIN_VALUE, carga = Int.MIN_VALUE), "=escaneando")
        val m1 = calc.calcular(lectura(1_000, 120, prod = 30, ana = 24, corriente = Int.MIN_VALUE, carga = Int.MIN_VALUE), "escaneando")
            .copy(costoMuestraUs = 850, horaMs = 1_760_000_000_000)
        val texto = CsvRecursos.escribir(listOf(m0, m1), hora = { "H$it" })
        assertTrue(texto.startsWith(Csv.BOM))
        val filas = Csv.leer(texto)
        assertEquals(3, filas.size)
        assertTrue(filas.all { it.size == CsvRecursos.COLUMNAS.size })
        val col = { f: Int, n: String -> filas[f][CsvRecursos.COLUMNAS.indexOf(n)] }
        assertEquals("'=escaneando", col(1, "fase"))
        assertEquals("", col(1, "cpu_ms"))
        assertEquals("", col(1, "cpu_pct_nucleo"))
        assertEquals("", col(1, "corriente_cruda"))
        assertEquals("", col(1, "corriente_ua"))
        assertEquals("", col(1, "carga_uah"))
        assertEquals("", col(1, "headroom"))
        assertEquals("1000", col(2, "t_ms"))
        assertEquals("120", col(2, "cpu_ms"))
        assertEquals("12.0", col(2, "cpu_pct_nucleo"))
        assertEquals("30.0", col(2, "fps_camara"))
        assertEquals("24.0", col(2, "fps_analisis"))
        assertEquals("312", col(2, "temp_bat_dc"))
        assertEquals("no", col(2, "enchufado"))
        assertEquals("OPEN", col(2, "estado_camara"))
        assertEquals("850", col(2, "costo_muestra_us"))
        assertEquals("H1760000000000", col(2, "hora"))
    }
}
