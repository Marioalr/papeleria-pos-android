package mx.papeleriapos.android.camara

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.sesion.DatosEquipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FichaCamaraTest {
    /** La principal del Pixel 8 Pro (medida el 2026-10-10): 6.9 mm, sensor de 9.8 × 7.4 mm y enfoca desde 10.5 cm. */
    private val principal = FichaLente(
        id = "2", logica = "0", nivel = "FULL", focalesMm = listOf(6.9f), aperturas = listOf(1.68f),
        enfoqueMinimoDioptrias = 100f / 10.5f, hiperfocalDioptrias = 0.2f, calibracion = FichaLente.CALIBRADA,
        modosAf = listOf("continuo_foto", "continuo_video", "auto", "macro"), zoomMinimo = 1f, zoomMaximo = 8f,
        sensorMm = 9.8f to 7.4f, pixeles = 8160 to 6144,
        resolucionesAnalisis = listOf(4080 to 3072, 1920 to 1080, 1280 to 720), resolucionesVisor = listOf(1920 to 1080),
        rangosFps = listOf(15 to 30, 30 to 30, 60 to 60), linterna = true, capacidades = listOf("basica", "sensor_manual"),
    )

    /** La gran angular: 2.0 mm, sensor chico y enfoca desde 2 cm. */
    private val granAngular = principal.copy(
        id = "3", focalesMm = listOf(2.0f), enfoqueMinimoDioptrias = 50f, sensorMm = 5.6f to 4.2f, linterna = false,
    )

    private val logica = principal.copy(id = "0", logica = null, zoomMinimo = 0.49f, zoomMaximo = 30f, capacidades = listOf("logica_multicamara"))

    private val fija = principal.copy(id = "9", enfoqueMinimoDioptrias = 0f, calibracion = FichaLente.SIN_CALIBRAR)

    private val equipo = DatosEquipo("Google Pixel 8 Pro", 37, "0.1.0", "abc1234")

    @Test
    fun `de la distancia minima de enfoque salen el campo, los px por modulo y el zoom con que abre`() {
        assertEquals(10.5f, principal.enfoqueMinimoCm!!, 1e-3f)
        // 9.8 mm × 9/16 = 5.51 mm de sensor a lo ancho; a 105 mm con 6.9 mm de focal abarca 83.9 mm.
        assertEquals(8.39f, principal.campoCm(10.5f)!!, 0.01f)
        // 720 px en 83.9 mm: un módulo de 0.33 mm son 2.8 px.
        assertEquals(2.83f, principal.pxPorModulo(10.5f)!!, 0.01f)
        // Un código de 30 mm llena el 60 % del ancho a 6.3 cm: más cerca de lo que enfoca, así que abre con zoom 1.7×.
        assertEquals(6.26f, principal.distanciaParaLlenar()!!, 0.01f)
        assertEquals(1.68f, principal.zoomSugerido, 0.01f)
        assertFalse(principal.focoFijo)
    }

    @Test
    fun `la gran angular ve el modulo mas grande de cerca y no necesita zoom`() {
        assertEquals(2f, granAngular.enfoqueMinimoCm!!, 1e-3f)
        assertEquals(3.15f, granAngular.campoCm(2f)!!, 0.01f)
        assertEquals(7.5f, granAngular.pxPorModulo(2f)!!, 0.05f)
        assertEquals(1f, granAngular.zoomSugerido, 1e-6f)
    }

    @Test
    fun `con foco fijo o sin datos no hay distancia y el zoom es el de sin datos`() {
        assertTrue(fija.focoFijo)
        assertNull(fija.enfoqueMinimoCm)
        assertNull(fija.pxPorModulo(10f)?.takeIf { fija.enfoqueMinimoCm != null })
        assertEquals(1.5f, fija.zoomSugerido, 1e-6f)
        val sinSensor = principal.copy(sensorMm = null)
        assertNull(sinSensor.campoCm(10f))
        assertNull(sinSensor.distanciaParaLlenar())
    }

    @Test
    fun `el csv trae una fila por lente con todas las columnas y punto decimal`() {
        val texto = CsvFicha.escribir(listOf(logica, principal, granAngular), "google/husky/husky:17", equipo)
        val filas = Csv.leer(texto)
        assertEquals(CsvFicha.COLUMNAS, filas[0])
        assertEquals(4, filas.size)
        for (f in filas) assertEquals(CsvFicha.COLUMNAS.size, f.size)
        val col = { nombre: String, fila: Int -> filas[fila][CsvFicha.COLUMNAS.indexOf(nombre)] }
        assertEquals("google/husky/husky:17", col("huella", 1))
        assertEquals("0", col("lente", 1))
        assertEquals("", col("logica", 1))
        assertEquals("2", col("lente", 2))
        assertEquals("0", col("logica", 2))
        assertEquals("6.90", col("focal_mm", 2))
        assertEquals("10.5", col("enfoque_minimo_cm", 2))
        assertEquals("8.4", col("campo_cm_al_minimo", 2))
        assertEquals("2.8", col("px_por_modulo_033_al_minimo", 2))
        assertEquals("6.3", col("cm_para_30mm_al_60", 2))
        assertEquals("1.68", col("zoom_sugerido", 2))
        assertEquals("4080x3072|1920x1080|1280x720", col("resoluciones_analisis", 2))
        assertEquals("15-30|30-30|60-60", col("rangos_fps", 2))
        assertEquals("continuo_foto|continuo_video|auto|macro", col("modos_af", 2))
        assertEquals("si", col("linterna", 2))
        assertEquals("no", col("linterna", 3))
        assertEquals("Google Pixel 8 Pro", col("dispositivo", 3))
        assertEquals("abc1234", col("commit", 3))
    }

    @Test
    fun `el resumen dice que lente es, desde donde enfoca y con que zoom abre`() {
        val r = CsvFicha.resumen(listOf(logica, principal, fija))
        val lineas = r.lines()
        assertEquals("Lógica 0 · FULL · 6.9 mm · sensor 9.8×7.4 mm · enfoca desde 10.5 cm (calibrada) · zoom 0.5–30.0× · AF continuo foto, continuo video, auto, macro · linterna · análisis hasta 4080×3072", lineas[0])
        assertEquals("  a 10.5 cm abarca 8.4 cm: 0.33 mm = 2.8 px; 30 mm al 60 % desde 6.3 cm → zoom 1.7×", lineas[1])
        assertTrue(lineas[2].startsWith("Física 2 (de 0) · FULL · 6.9 mm"))
        assertTrue("foco fijo sin renglón de distancias", lineas[4].startsWith("Física 9 (de 0)") && lineas[4].contains("foco fijo"))
        assertEquals(5, lineas.size)
    }
}
