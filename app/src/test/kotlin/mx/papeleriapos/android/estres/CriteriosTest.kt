package mx.papeleriapos.android.estres

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.medicion.MuestraRecursos
import mx.papeleriapos.android.sesion.DatosEquipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CriteriosTest {
    private fun ciclo(n: Int, primer: Double = 400.0, error: String? = null) = Ciclo(n, 150.0, primer, 60.0, error)

    private fun memoria(en: Int, heap: Long = 20_000, nativo: Long = 30_000, rss: Long = 180_000, hilos: Int = 40, fds: Int = 110) =
        PuntoMemoria(en, heap, nativo, 90_000, rss, hilos, fds)

    private val memoriaEstable = listOf(1, 25, 50, 75, 100, 125, 150, 175, 200).map { memoria(it, heap = 20_000L + (it % 2) * 300) }

    @Test
    fun `la pendiente es la de minimos cuadrados`() {
        assertEquals(2.0, Cuentas.pendiente(listOf(0.0, 1.0, 2.0, 3.0), listOf(1.0, 3.0, 5.0, 7.0)), 1e-12)
        assertEquals(0.0, Cuentas.pendiente(listOf(1.0, 2.0, 3.0), listOf(5.0, 5.0, 5.0)), 1e-12)
        assertTrue(Cuentas.pendiente(listOf(1.0), listOf(1.0)).isNaN())
        assertTrue(Cuentas.pendiente(listOf(2.0, 2.0), listOf(1.0, 3.0)).isNaN())
    }

    @Test
    fun `un error es recuperado si despues hubo un ciclo bueno`() {
        val ciclos = listOf(ciclo(1), ciclo(2, error = "no abrió"), ciclo(3), ciclo(4, error = "sin cuadros"), ciclo(5, error = "sin cuadros"))
        assertEquals(2, CriteriosCiclos.erroresSinRecuperar(ciclos))
        assertEquals(0, CriteriosCiclos.erroresSinRecuperar(ciclos.take(3)))
        assertEquals(1, CriteriosCiclos.erroresSinRecuperar(listOf(ciclo(1, error = "x"))))
    }

    @Test
    fun `200 ciclos estables pasan`() {
        val ciclos = (1..200).map { ciclo(it, primer = 380.0 + (it % 7) * 5) } + emptyList()
        val d = CriteriosCiclos.evaluar(ciclos, memoriaEstable)
        assertTrue(d.fallidos.toString(), d.pasa)
        val (inicio, fin) = CriteriosCiclos.extremos(ciclos)
        assertEquals((1..20).toList(), inicio.map { it.n })
        assertEquals((181..200).toList(), fin.map { it.n })
    }

    @Test
    fun `si el primer cuadro se vuelve 30 por ciento mas lento al final no pasa`() {
        val ciclos = (1..200).map { ciclo(it, primer = if (it > 180) 520.0 else 400.0) }
        val d = CriteriosCiclos.evaluar(ciclos, memoriaEstable)
        assertFalse(d.pasa)
        assertEquals(listOf("p95 al primer cuadro (inicio → final, ms)"), d.fallidos.map { it.nombre })
    }

    @Test
    fun `una fuga de memoria, de hilos o de descriptores no pasa`() {
        val ciclos = (1..200).map { ciclo(it) }
        // 3 MB por cada 100 ciclos en el heap nativo.
        val fuga = memoriaEstable.map { it.copy(heapNativoKb = 30_000 + it.en * 30L) }
        assertEquals(listOf("Heap nativo (KB por 100 ciclos)"), CriteriosCiclos.evaluar(ciclos, fuga).fallidos.map { it.nombre })
        val hilos = memoriaEstable.mapIndexed { i, p -> p.copy(hilos = 40 + i) }
        assertEquals(listOf("Hilos (base → final)"), CriteriosCiclos.evaluar(ciclos, hilos).fallidos.map { it.nombre })
        val fds = memoriaEstable.map { it.copy(fds = 110 + it.en / 20) }
        assertEquals(listOf("Descriptores (base → final)"), CriteriosCiclos.evaluar(ciclos, fds).fallidos.map { it.nombre })
        // Bajar no es fuga.
        val baja = memoriaEstable.mapIndexed { i, p -> p.copy(hilos = 40 - i, fds = 110 - i) }
        assertTrue(CriteriosCiclos.evaluar(ciclos, baja).pasa)
        // Un kernel sin RSS no hace fallar.
        assertTrue(CriteriosCiclos.evaluar(ciclos, memoriaEstable.map { it.copy(rssKb = -1) }).pasa)
    }

    @Test
    fun `un error al final sin recuperar no pasa`() {
        val ciclos = (1..199).map { ciclo(it) } + ciclo(200, error = "no cerró")
        val d = CriteriosCiclos.evaluar(ciclos, memoriaEstable)
        assertEquals(listOf("Errores sin recuperar"), d.fallidos.map { it.nombre })
    }

    @Test
    fun `la rafaga cuenta los huecos sin cuadros y compara la interfaz con la camara`() {
        val segundos = listOf(10L, 20, 30, 30, 30, 31, 40).mapIndexed { i, c -> SegundoRafaga(i + 1, 33 * (i + 1), 5 * (i + 1), c, "ESCANEANDO") }
        assertEquals(2, CriteriosRafaga.huecoMaximo(segundos))
        val bien = FinalRafaga(1.5f, 1.5f, true, false, false, "ESCANEANDO", null)
        assertTrue(CriteriosRafaga.evaluar(segundos, bien).pasa)
        val linterna = bien.copy(linternaUi = true)
        assertEquals(listOf("Linterna de la interfaz = la de la cámara"), CriteriosRafaga.evaluar(segundos, linterna).fallidos.map { it.nombre })
        val zoom = bien.copy(zoomReal = 1f)
        assertEquals(listOf("Zoom de la interfaz = el de la cámara"), CriteriosRafaga.evaluar(segundos, zoom).fallidos.map { it.nombre })
        // Sin linterna en el celular, no se evalúa.
        assertTrue(CriteriosRafaga.evaluar(segundos, linterna.copy(hayLinterna = false)).pasa)
        val error = bien.copy(fase = "ERROR", error = "No se pudo abrir la cámara.")
        assertFalse(CriteriosRafaga.evaluar(segundos, error).pasa)
        val atorado = segundos + listOf(40L, 40, 40).mapIndexed { i, c -> SegundoRafaga(8 + i, 300, 50, c, "ESCANEANDO") }
        assertEquals(listOf("Segundos seguidos sin cuadros nuevos"), CriteriosRafaga.evaluar(atorado, bien).fallidos.map { it.nombre })
        assertFalse("Cancelada: sin final", CriteriosRafaga.evaluar(segundos, null).pasa)
    }

    @Test
    fun `cambiar de motor pasa sin errores, rapido y sin fugas`() {
        val cambios = (1..50).map { CambioMotor(it, "ML_KIT", 300.0 + it) }
        val mem = listOf(3, 10, 20, 30, 40, 50).map { memoria(it) }
        assertTrue(CriteriosMotor.evaluar(cambios, mem).pasa)
        val lento = cambios.map { if (it.n > 40) it.copy(ms = 4_000.0) else it }
        assertEquals(listOf("p95 hasta leer con el motor nuevo (ms)"), CriteriosMotor.evaluar(lento, mem).fallidos.map { it.nombre })
        val conError = cambios.take(49) + CambioMotor(50, "ZXING_CPP", Double.NaN, "no leyó con zxing-cpp")
        assertEquals(listOf("Cambios con error"), CriteriosMotor.evaluar(conError, mem).fallidos.map { it.nombre })
    }

    /** Un segundo de escaneo continuo con lo que importa para el detector. */
    private fun muestra(s: Int, fps: Double, cpuMs: Long, termico: Int = 0, headroom: Float = Float.NaN, temp: Int = 300) = MuestraRecursos(
        tMs = s * 1000L, fase = "continuo", cpuMs = cpuMs, cpuPctNucleo = cpuMs / 10.0, heapJavaKb = 1, heapNativoKb = 1,
        rssKb = 1, rssAnonKb = 1, hilos = 1, fds = 1, corrienteCruda = 0, corrienteUa = null, voltajeMv = 0,
        potenciaMw = Double.NaN, cargaUah = 0, bateriaPct = 80, tempBatDc = temp, enchufado = false, termico = termico,
        headroom = headroom, fpsCamara = 30.0, fpsAnalisis = fps, estadoCamara = "", paredDeltaMs = 1000,
        analizadosDelta = fps.toLong(),
    )

    @Test
    fun `sin calor ni caida de cuadros no hay estrangulamiento`() {
        val m = (0 until 600).map { muestra(it, 25.0, 250) }
        val r = DetectorEstrangulamiento().evaluar(m)
        assertFalse(r.detectado)
        assertEquals(25.0, r.fpsBase, 1e-9)
        assertEquals(10.0, r.cpuPorCuadroBase, 1e-9)
        assertEquals(10, r.minutos.size)
        assertEquals(30.0, r.minutos.first().tempBatC, 1e-9)
    }

    @Test
    fun `con dos condiciones sostenidas 60 s lo detecta, con una sola no`() {
        // Del minuto 5 en adelante: estado térmico MODERATE y menos cuadros por segundo (20 < 85 % de 25).
        val dos = (0 until 600).map { s -> if (s >= 300) muestra(s, 20.0, 200, termico = 2) else muestra(s, 25.0, 250) }
        val r = DetectorEstrangulamiento().evaluar(dos)
        assertTrue(r.detectado)
        assertEquals(359, r.desdeSegundo)
        assertEquals(2, r.condiciones.size)
        // Solo calor: no basta.
        val uno = (0 until 600).map { s -> if (s >= 300) muestra(s, 25.0, 250, termico = 3) else muestra(s, 25.0, 250) }
        assertFalse(DetectorEstrangulamiento().evaluar(uno).detectado)
        // Calor que va y viene (no toda la ventana) tampoco.
        val intermitente = (0 until 600).map { s -> if (s >= 300) muestra(s, 20.0, 200, termico = if (s % 30 == 0) 0 else 2) else muestra(s, 25.0, 250) }
        assertFalse(DetectorEstrangulamiento().evaluar(intermitente).detectado)
    }

    @Test
    fun `headroom alto y mas CPU por cuadro tambien cuentan`() {
        val m = (0 until 400).map { s -> if (s >= 200) muestra(s, 25.0, 330, headroom = 0.9f) else muestra(s, 25.0, 250, headroom = 0.4f) }
        val r = DetectorEstrangulamiento().evaluar(m)
        assertTrue(r.detectado)
        assertTrue(r.condiciones.toString(), r.condiciones.any { it.startsWith("headroom") } && r.condiciones.any { it.startsWith("CPU") })
    }

    @Test
    fun `los CSV de estres traen sus columnas y el resumen sus criterios`() {
        val ciclos = listOf(ciclo(1), ciclo(2, error = "no abrió").copy(abrirMs = Double.NaN))
        val texto = CsvEstres.ciclos(ciclos, listOf(memoria(1)))
        val f = Csv.leer(texto)
        assertEquals(CsvEstres.COLUMNAS_CICLOS, f[0])
        assertEquals("150.0", f[1][1])
        assertEquals("20000", f[1][5])
        assertEquals("", f[2][1])
        assertEquals("no abrió", f[2][4])
        assertEquals("", f[2][5])
        val d = Dictamen(listOf(Criterio("Errores sin recuperar", "0", "0", true), Criterio("x", "=1", "y", false)))
        val r = Csv.leer(CsvEstres.resumen("CICLOS", d, DatosEquipo("Pixel", 36, "0.1.0", "abc")))
        assertEquals(CsvEstres.COLUMNAS_RESUMEN, r[0])
        assertEquals(listOf("CICLOS", "x", "'=1", "y", "no", "Pixel", "36", "0.1.0", "abc"), r[2])
        assertFalse(d.pasa)
        assertFalse(Dictamen(emptyList()).pasa)
    }
}
