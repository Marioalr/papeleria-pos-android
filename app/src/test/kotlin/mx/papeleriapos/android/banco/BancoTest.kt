package mx.papeleriapos.android.banco

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.sesion.DatosEquipo
import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.Luma
import mx.papeleriapos.escaner.nucleo.Rectangulo
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.nucleo.Transformacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BancoTest {
    private val ean = EscenaBanco("ean", Simbologia.EAN_13, "7501234567893", 2f)
    private val upc = EscenaBanco("upc", Simbologia.UPC_A, "036000291452", 3f)
    private val code = EscenaBanco("code", Simbologia.CODE_128, "PAP0042x", 3f)
    private val chico = EscenaBanco("chico", Simbologia.EAN_8, "96385074", 1.5f)
    private val liso = EscenaBanco("liso", null, null, 0f, negativo = Negativo.LISO)
    private val texto = EscenaBanco("texto", null, null, 0f, negativo = Negativo.TEXTO)
    private val escenas = listOf(ean, upc, code, chico, liso, texto)

    private fun lectura(t: String, s: Simbologia = Simbologia.EAN_13) = Lectura(t, s, floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))

    @Test
    fun `el mismo GTIN con distintos ceros es el mismo codigo`() {
        assertTrue(Clasificador.mismoCodigo("036000291452", "0036000291452"))
        assertTrue(Clasificador.mismoCodigo("0036000291452", "036000291452"))
        assertTrue(Clasificador.mismoCodigo("PAP0042x", "PAP0042x"))
        assertFalse(Clasificador.mismoCodigo("PAP0042x", "PAP0042X"))
        assertFalse(Clasificador.mismoCodigo("7501234567893", "7501234567899"))
        assertFalse(Clasificador.mismoCodigo("17501000000019", "1750100000000000000000000000000000000000"))
    }

    @Test
    fun `clasifica aciertos, incorrectas, sin lectura, falsos positivos y rechazos`() {
        assertEquals(Veredicto.ACIERTO, Clasificador.clasificar(upc, listOf(lectura("036000291452", Simbologia.UPC_A))))
        assertEquals(Veredicto.LECTURA_INCORRECTA, Clasificador.clasificar(ean, listOf(lectura("7501234567899"))))
        // Si junto a la buena viene una mala, cuenta como incorrecta: en caja podría ganar la mala.
        assertEquals(Veredicto.LECTURA_INCORRECTA, Clasificador.clasificar(ean, listOf(lectura("7501234567893"), lectura("4006381333931"))))
        assertEquals(Veredicto.SIN_LECTURA, Clasificador.clasificar(ean, emptyList()))
        assertEquals(Veredicto.FALSO_POSITIVO, Clasificador.clasificar(liso, listOf(lectura("12345670", Simbologia.EAN_8))))
        assertEquals(Veredicto.RECHAZO_CORRECTO, Clasificador.clasificar(liso, emptyList()))
    }

    @Test
    fun `girar al buffer es el inverso de la transformacion del nucleo`() {
        // Una imagen derecha de 6×10 con un valor distinto por píxel.
        val derecha = Luma(6, 10, ByteArray(60) { it.toByte() })
        for (rot in listOf(0, 90, 180, 270)) {
            val (w, h) = if (rot % 180 == 0) 6 to 10 else 10 to 6
            val buffer = ByteArray(w * h)
            Banco.aBuffer(derecha, rot, buffer, w, h)
            val t = Transformacion(Rectangulo(0, 0, w, h), rot)
            val p = FloatArray(2)
            for (y in 0 until h) for (x in 0 until w) {
                t.aVisible(x + 0.5f, y + 0.5f, p)
                val ux = (p[0] * t.anchoDerecho).toInt()
                val uy = (p[1] * t.altoDerecho).toInt()
                assertEquals("rotación $rot en ($x, $y)", derecha[ux, uy], buffer[y * w + x].toInt() and 0xFF)
            }
        }
    }

    /** Un motor que sabe qué escena está cargada: lee bien salvo donde se le pide fallar. */
    private class MotorFalso(
        override val motor: Motor,
        private val banco: () -> Banco,
        private val respuestas: Map<String, List<String>> = emptyMap(),
        private val registro: MutableList<String>,
    ) : MotorDeLectura {
        var calentado = false
        val regiones = mutableListOf<Rectangulo>()

        init {
            registro += "crear ${motor.name}"
        }

        override fun leer(cuadro: CuadroY): ResultadoMotor {
            val e = banco().escenaActual!!
            regiones += cuadro.region
            assertTrue("El buffer debe ser directo, como el de la cámara", cuadro.y.isDirect)
            assertEquals(0, cuadro.y.position())
            assertNull(cuadro.proxy)
            val textos = respuestas[e.id] ?: listOfNotNull(e.esperado)
            return ResultadoMotor(textos.map { Lectura(it, e.simbologia ?: Simbologia.EAN_13, null) }, emptyList(), 1000)
        }

        override fun calentar() {
            calentado = true
        }

        override fun close() {
            registro += "cerrar ${motor.name}"
        }
    }

    private fun relojDe2ms(): () -> Long {
        var t = 0L
        return { t += 2_000_000; t }
    }

    @Test
    fun `corre cada escena por cada motor y resume aciertos, errores y tiempos`() {
        val registro = mutableListOf<String>()
        val creados = mutableListOf<MotorFalso>()
        lateinit var banco: Banco
        banco = Banco(
            crearMotor = { m, _ ->
                val respuestas = if (m == Motor.ZXING_CPP) mapOf("code" to listOf("PAP0O42x"), "texto" to listOf("12345670")) else emptyMap()
                MotorFalso(m, { banco }, respuestas, registro).also { creados += it }
            },
            motores = listOf(Motor.ML_KIT, Motor.ZXING_CPP),
            simbologias = setOf(Simbologia.EAN_13),
            recortarFranja = true,
            altoFranja = 0.4f,
            anchoBuffer = 320,
            altoBuffer = 180,
            repeticionesFrio = 2,
            reloj = relojDe2ms(),
        )
        val avances = mutableListOf<Int>()
        val (resultados, resumenes) = banco.correr(escenas, progreso = { h, _ -> avances += h })

        // 2 en frío por motor (cada uno nuevo) + uno caliente por motor.
        assertEquals(6, creados.size)
        assertEquals(registro.count { it.startsWith("crear") }, registro.count { it.startsWith("cerrar") })
        assertTrue(creados.takeLast(2).all { it.calentado })
        assertTrue(creados.take(4).none { it.calentado })
        assertEquals(2 * 2 + escenas.size, banco.pasos(escenas))
        assertEquals(banco.pasos(escenas), avances.last())

        // La franja central del 40 % de la región visible, en el buffer.
        val franja = Transformacion(Rectangulo(0, 0, 320, 180), 90).aBuffer(Caja(0f, 0.3f, 1f, 0.7f))
        assertEquals(franja, banco.region)
        assertTrue(creados.flatMap { it.regiones }.all { it == franja })

        val ml = resumenes.first { it.motor == Motor.ML_KIT }
        assertEquals(4, ml.conCodigo)
        assertEquals(4, ml.aciertos)
        assertEquals(0, ml.incorrectas)
        assertEquals(2, ml.sinCodigo)
        assertEquals(0, ml.falsosPositivos)
        assertEquals(2.0, ml.msCaliente!!.mediana, 1e-9)
        assertEquals(2, ml.msFrio!!.n)
        assertEquals(1 to 1, ml.aciertosPorModulo[1.5f])
        assertEquals(2 to 2, ml.aciertosPorModulo[3f])
        assertNull(ml.error)

        val zx = resumenes.first { it.motor == Motor.ZXING_CPP }
        assertEquals(3, zx.aciertos)
        assertEquals(1, zx.incorrectas)
        assertEquals(1, zx.falsosPositivos)
        assertEquals(2, zx.sinCodigo)
        assertEquals(Veredicto.LECTURA_INCORRECTA, resultados.first { it.motor == Motor.ZXING_CPP && it.escena == code && it.pasada == Pasada.CALIENTE }.veredicto)
        assertEquals(2 * 2 + 2 * escenas.size, resultados.size)
    }

    @Test
    fun `un motor que no se puede crear queda con su error y el otro sigue`() {
        val registro = mutableListOf<String>()
        lateinit var banco: Banco
        banco = Banco(
            crearMotor = { m, _ ->
                if (m == Motor.ZXING_CPP) throw UnsatisfiedLinkError("no se encontró libzxing.so")
                MotorFalso(m, { banco }, registro = registro)
            },
            motores = listOf(Motor.ML_KIT, Motor.ZXING_CPP),
            simbologias = setOf(Simbologia.EAN_13),
            recortarFranja = false,
            anchoBuffer = 320,
            altoBuffer = 180,
            repeticionesFrio = 1,
        )
        assertEquals(Rectangulo(0, 0, 320, 180), banco.region)
        val (resultados, resumenes) = banco.correr(escenas)
        val zx = resumenes.first { it.motor == Motor.ZXING_CPP }
        assertTrue(zx.error!!.contains("libzxing"))
        assertEquals(0, zx.conCodigo)
        assertTrue(resultados.none { it.motor == Motor.ZXING_CPP })
        assertEquals(4, resumenes.first { it.motor == Motor.ML_KIT }.aciertos)
    }

    @Test
    fun `una excepcion al leer deja al motor fuera sin tumbar el banco`() {
        val registro = mutableListOf<String>()
        lateinit var banco: Banco
        banco = Banco(
            crearMotor = { m, _ ->
                object : MotorDeLectura by MotorFalso(m, { banco }, registro = registro) {
                    override fun leer(cuadro: CuadroY): ResultadoMotor {
                        if (banco.escenaActual == code) throw IllegalStateException("se cerró")
                        return ResultadoMotor(listOf(Lectura(banco.escenaActual!!.esperado ?: "", Simbologia.EAN_13, null)).filter { it.texto.isNotEmpty() }, emptyList(), 0)
                    }
                }
            },
            motores = listOf(Motor.ML_KIT),
            simbologias = setOf(Simbologia.EAN_13),
            recortarFranja = true,
            anchoBuffer = 320,
            altoBuffer = 180,
            repeticionesFrio = 1,
        )
        val (_, resumenes) = banco.correr(escenas)
        val ml = resumenes.single()
        assertTrue(ml.error!!.contains("se cerró"))
        // Leyó las dos escenas de antes del error.
        assertEquals(2, ml.aciertos)
        assertEquals(registro.count { it.startsWith("crear") }, registro.count { it.startsWith("cerrar") })
    }

    @Test
    fun `se puede cancelar a la mitad`() {
        val registro = mutableListOf<String>()
        lateinit var banco: Banco
        var leidas = 0
        banco = Banco(
            crearMotor = { m, _ -> MotorFalso(m, { banco }, registro = registro) },
            motores = listOf(Motor.ML_KIT),
            simbologias = setOf(Simbologia.EAN_13),
            recortarFranja = true,
            anchoBuffer = 320,
            altoBuffer = 180,
            repeticionesFrio = 1,
        )
        val (resultados, _) = banco.correr(escenas, progreso = { _, _ -> leidas++ }, cancelado = { leidas > 3 })
        assertTrue(resultados.size < 1 + escenas.size)
        assertEquals(registro.count { it.startsWith("crear") }, registro.count { it.startsWith("cerrar") })
    }

    @Test
    fun `el CSV del banco trae un renglon por resultado`() {
        val r = listOf(
            ResultadoEscena(ean, Motor.ML_KIT, Pasada.CALIENTE, Veredicto.ACIERTO, "7501234567893", 61.234),
            ResultadoEscena(liso, Motor.ZXING_CPP, Pasada.FRIO, Veredicto.FALSO_POSITIVO, "=1+1", 9.5),
            ResultadoEscena(code, Motor.ZXING_CPP, Pasada.CALIENTE, null, "", 1.0, "IllegalStateException: x, y"),
        )
        val texto = CsvBanco.escribir("20261009-101500-Pixel 8", "franja 36%", r, DatosEquipo("Google Pixel 8", 36, "0.1.0", "abc1234"))
        val filas = Csv.leer(texto)
        assertEquals(CsvBanco.COLUMNAS, filas[0])
        assertEquals(4, filas.size)
        val col = { f: Int, n: String -> filas[f][CsvBanco.COLUMNAS.indexOf(n)] }
        assertEquals("ML_KIT", col(1, "motor"))
        assertEquals("CALIENTE", col(1, "pasada"))
        assertEquals("ACIERTO", col(1, "veredicto"))
        assertEquals("61.23", col(1, "ms"))
        assertEquals("2.0", col(1, "modulo_px"))
        assertEquals("franja 36%", col(1, "region"))
        assertEquals("LISO", col(2, "sin_codigo"))
        assertEquals("", col(2, "simbologia"))
        assertEquals("'=1+1", col(2, "leido"))
        assertEquals("ERROR", col(3, "veredicto"))
        assertEquals("IllegalStateException: x, y", col(3, "error"))
    }
}
