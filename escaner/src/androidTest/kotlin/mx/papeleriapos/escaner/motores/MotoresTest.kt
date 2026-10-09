package mx.papeleriapos.escaner.motores

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Gtin
import mx.papeleriapos.escaner.nucleo.Rectangulo
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los dos motores sobre cuadros dibujados (docs/pruebas.md, «N1»): códigos del POS en planos Y de 1280x720 como los da
 * la cámara (rotación 90 en vertical y 0 en horizontal, con relleno al final del renglón), leídos en la franja.
 * Los tiempos salen en el registro con la etiqueta MotoresTest.
 */
@RunWith(AndroidJUnit4::class)
class MotoresTest {

    /** Cómo se le pasa el cuadro a cada motor: zxing-cpp tiene el camino en vivo (proxy) y el del banco (mapa de bits). */
    private enum class Variante(val motor: Motor, val conProxy: Boolean, val nombre: String) {
        ML_KIT(Motor.ML_KIT, false, "ML Kit"),
        ZXING_PROXY(Motor.ZXING_CPP, true, "zxing-cpp (proxy)"),
        ZXING_MAPA(Motor.ZXING_CPP, false, "zxing-cpp (mapa)"),
    }

    private data class Caso(val simbologia: Simbologia, val texto: String, val rotacion: Int, val moduloPx: Float)

    private val casos = listOf(
        Caso(Simbologia.EAN_13, "7501234567893", 90, 3f),
        Caso(Simbologia.EAN_13, Gtin.completar("200000000001"), 90, 3f),
        Caso(Simbologia.UPC_A, "036000291452", 90, 3f),
        Caso(Simbologia.EAN_8, "96385074", 90, 3f),
        Caso(Simbologia.ITF, Gtin.completar("1750100000001"), 90, 2.5f),
        Caso(Simbologia.CODE_128, "PAP0042x", 90, 2.5f),
        Caso(Simbologia.EAN_13, "7501234567893", 0, 3f),
        Caso(Simbologia.EAN_13, Gtin.completar("200000000001"), 0, 3f),
        Caso(Simbologia.UPC_A, "036000291452", 0, 3f),
        Caso(Simbologia.EAN_8, "96385074", 0, 3f),
        Caso(Simbologia.ITF, Gtin.completar("1750100000001"), 0, 3f),
        Caso(Simbologia.CODE_128, "ELEV-00123a", 0, 3f),
    )

    @Test
    fun ambosMotoresLeenLosCodigosDelPosYSusEsquinasCaenSobreElCodigo() {
        val fallas = ArrayList<String>()
        for (variante in Variante.entries) {
            crearMotor(variante.motor, POS).use { motor ->
                motor.calentar()
                for (c in casos) {
                    val escena = EscenaBuffer.conCodigo(c.simbologia, c.texto, c.rotacion, c.moduloPx)
                    val r = motor.leer(escena.cuadro(conProxy = variante.conProxy))
                    val esperado = comparable(c.texto, c.simbologia)
                    val leidas = r.lecturas.map { comparable(it.texto, it.simbologia) }
                    val linea = "${variante.nombre} ${c.simbologia.nombre} ${c.texto} rot=${c.rotacion}: " +
                        "${r.lecturas.map { "${it.simbologia.nombre}:${it.texto}" }} pot=${r.potencialesBuffer.size} " +
                        "%.1f ms".format(r.nanos / 1e6)
                    Log.i(ETIQUETA, linea)
                    if (leidas != listOf(esperado)) {
                        fallas += "$linea (se esperaba $esperado)"
                        continue
                    }
                    val esquinas = r.lecturas[0].esquinasBuffer
                    if (esquinas == null) {
                        fallas += "$linea: sin esquinas"
                        continue
                    }
                    val problema = escena.esquinasSobreElCodigo(esquinas, tolerancia = 4 * c.moduloPx + 4)
                    Log.i(ETIQUETA, "  esquinas en el derecho: ${escena.aDerecho(esquinas).joinToString { "%.0f".format(it) }} " +
                        "barras=${escena.barrasDerecho}")
                    if (problema != null) fallas += "$linea: $problema"
                }
            }
        }
        if (fallas.isNotEmpty()) fail(fallas.joinToString("\n"))
    }

    @Test
    fun mlKitDaPotencialesConCodigosChicosOBorrosos() {
        // ML Kit no es monotónico: no da potenciales a 0.45 px/módulo ni siempre a 1.5–2. Se pide en la mayoría.
        val escenas = listOf(
            "1.2 px/módulo" to EscenaBuffer.conCodigo(Simbologia.EAN_13, "7501234567893", moduloPx = 1.2f),
            "1.0 px/módulo" to EscenaBuffer.conCodigo(Simbologia.EAN_13, "7501234567893", moduloPx = 1.0f),
            "0.8 px/módulo" to EscenaBuffer.conCodigo(Simbologia.EAN_13, "7501234567893", moduloPx = 0.8f),
            "borroso r=4" to EscenaBuffer.conCodigo(Simbologia.EAN_13, "7501234567893", moduloPx = 2f, desenfoque = 4),
            "borroso r=6" to EscenaBuffer.conCodigo(Simbologia.EAN_13, "7501234567893", moduloPx = 2f, desenfoque = 6),
            "1.0 px/módulo rot 0" to EscenaBuffer.conCodigo(Simbologia.EAN_13, "7501234567893", rotacion = 0, moduloPx = 1.0f),
        )
        var conPotencial = 0
        val fallas = ArrayList<String>()
        crearMotor(Motor.ML_KIT, POS).use { motor ->
            motor.calentar()
            for ((nombre, escena) in escenas) {
                val r = motor.leer(escena.cuadro())
                Log.i(ETIQUETA, "Potenciales $nombre: lecturas=${r.lecturas.map { it.texto }} potenciales=${r.potencialesBuffer.size}")
                if (r.lecturas.any { comparable(it.texto, it.simbologia) != "7501234567893" }) fallas += "$nombre: lectura falsa ${r.lecturas}"
                if (r.potencialesBuffer.isNotEmpty() && r.lecturas.isEmpty()) conPotencial++
                for (p in r.potencialesBuffer) {
                    Log.i(ETIQUETA, "  potencial en el derecho: ${escena.aDerecho(p).joinToString { "%.0f".format(it) }} barras=${escena.barrasDerecho}")
                    // La caja de un potencial es aproximada: basta con que caiga sobre el código.
                    escena.esquinasSobreElCodigo(p, tolerancia = 24f, cobertura = 0.3f)?.let { fallas += "$nombre: $it" }
                }
            }
        }
        Log.i(ETIQUETA, "Potenciales sin lectura en $conPotencial de ${escenas.size} escenas")
        if (fallas.isNotEmpty()) fail(fallas.joinToString("\n"))
        assertTrue("ML Kit dio potenciales solo en $conPotencial de ${escenas.size} escenas", conPotencial >= escenas.size / 2)
    }

    @Test
    fun zxingCppNoDaLecturasFalsasEnEscenasSinCodigo() {
        val tipos = listOf("gris", "ruido", "rayada", "franjas", "texto")
        val fallas = ArrayList<String>()
        crearMotor(Motor.ZXING_CPP, POS).use { zxing ->
            crearMotor(Motor.ML_KIT, POS).use { mlKit ->
                mlKit.calentar()
                for (rotacion in listOf(90, 0)) for (tipo in tipos) for (semilla in 1..3) {
                    val escena = EscenaBuffer.sinCodigo(tipo, rotacion, semilla)
                    for (region in listOf(escena.regionFranja, escena.visible)) {
                        val z = zxing.leer(escena.cuadro(region, conProxy = true))
                        val m = mlKit.leer(escena.cuadro(region))
                        Log.i(
                            ETIQUETA,
                            "Sin código $tipo/$semilla rot=$rotacion región=${region.ancho}x${region.alto}: " +
                                "zxing=${z.lecturas.map { it.texto }} mlkit=${m.lecturas.map { it.texto }} potenciales mlkit=${m.potencialesBuffer.size}",
                        )
                        if (z.lecturas.isNotEmpty()) fallas += "zxing-cpp leyó ${z.lecturas} en $tipo/$semilla rot=$rotacion"
                    }
                }
            }
        }
        if (fallas.isNotEmpty()) fail(fallas.joinToString("\n"))
    }

    @Test
    fun latenciasEnFrioYEnCalienteConFranjaYCuadroCompleto() {
        val conCodigo = EscenaBuffer.conCodigo(Simbologia.EAN_13, "7501234567893")
        val vacia = EscenaBuffer.sinCodigo("texto")
        val resumen = StringBuilder()
        for (variante in Variante.entries) {
            for (probarGiros in if (variante.motor == Motor.ZXING_CPP) listOf(false, true) else listOf(false)) {
                val nombre = variante.nombre + if (probarGiros) " tryRotate" else ""
                // Frío: la primera lectura de un motor recién creado, sin calentar.
                val inicio = SystemClock.elapsedRealtimeNanos()
                val motor = nuevo(variante.motor, probarGiros)
                val msCrear = (SystemClock.elapsedRealtimeNanos() - inicio) / 1e6
                val frio = motor.leer(conCodigo.cuadro(conProxy = variante.conProxy))
                assertEquals("$nombre en frío", listOf("7501234567893"), frio.lecturas.map { it.texto })
                resumen.append("%-28s crear %6.1f ms, primera lectura %6.1f ms\n".format(nombre, msCrear, frio.nanos / 1e6))
                motor.use {
                    for ((escenaNombre, escena) in listOf("con código" to conCodigo, "sin código" to vacia)) {
                        for ((regionNombre, region) in listOf("franja" to escena.regionFranja, "visible" to escena.visible)) {
                            val tiempos = medir(it, escena, region, variante.conProxy, escenaNombre == "con código")
                            resumen.append(
                                "%-28s %-10s %-8s %4dx%-4d mediana %6.2f ms  p95 %6.2f ms\n".format(
                                    nombre, escenaNombre, regionNombre, region.ancho, region.alto,
                                    percentil(tiempos, 0.5), percentil(tiempos, 0.95),
                                ),
                            )
                        }
                    }
                }
            }
        }
        // Una línea por renglón: logcat corta los mensajes largos.
        resumen.lines().filter { it.isNotBlank() }.forEach { Log.i(ETIQUETA, "Latencia $it") }
    }

    @Test
    fun simbologiasIdaYVuelta() {
        for (s in Simbologia.entries) {
            val ml = aFormatoMlKit(s)
            val zx = aFormatoZxing(s)
            if (s == Simbologia.OTRA) {
                assertNull(ml)
                assertNull(zx)
                continue
            }
            assertEquals("ML Kit $s", s, deFormatoMlKit(ml!!))
            assertEquals("zxing-cpp $s", s, deFormatoZxing(zx!!))
        }
        assertEquals(Simbologia.ITF, deFormatoZxing(zxingcpp.BarcodeReader.Format.ITF))
        assertEquals(Simbologia.QR, deFormatoZxing(zxingcpp.BarcodeReader.Format.QR_CODE_MODEL_2))
        assertEquals(Simbologia.OTRA, deFormatoMlKit(-1))
        for (motor in Motor.entries) {
            try {
                crearMotor(motor, setOf(Simbologia.OTRA)).close()
                fail("$motor aceptó un juego de simbologías que no lee")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun cerrarDosVecesNoFalla() {
        for (motor in Motor.entries) {
            val m = crearMotor(motor, POS)
            m.calentar()
            m.close()
            m.close()
        }
    }

    private fun nuevo(motor: Motor, probarGiros: Boolean): MotorDeLectura =
        if (motor == Motor.ZXING_CPP) MotorZxingCpp(POS, probarGiros) else crearMotor(motor, POS)

    private fun medir(motor: MotorDeLectura, escena: EscenaBuffer, region: Rectangulo, conProxy: Boolean, debeLeer: Boolean): DoubleArray {
        repeat(5) { motor.leer(escena.cuadro(region, conProxy)) }
        return DoubleArray(REPETICIONES) {
            val r: ResultadoMotor = motor.leer(escena.cuadro(region, conProxy))
            if (debeLeer) assertEquals(listOf("7501234567893"), r.lecturas.map { l -> l.texto })
            else assertTrue("lectura falsa ${r.lecturas}", r.lecturas.isEmpty())
            r.nanos / 1e6
        }
    }

    private fun percentil(xs: DoubleArray, q: Double): Double {
        val s = xs.sorted()
        return s[((s.size - 1) * q).toInt()]
    }

    companion object {
        const val ETIQUETA = "MotoresTest"
        const val REPETICIONES = 40
        val POS = setOf(
            Simbologia.EAN_13, Simbologia.EAN_8, Simbologia.UPC_A, Simbologia.UPC_E, Simbologia.ITF,
            Simbologia.CODE_128, Simbologia.QR,
        )
    }
}
