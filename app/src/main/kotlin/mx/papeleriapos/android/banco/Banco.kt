package mx.papeleriapos.android.banco

import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.Luma
import mx.papeleriapos.escaner.nucleo.Rectangulo
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.nucleo.Transformacion
import java.nio.ByteBuffer

/** Cómo le fue a un motor con una escena. */
enum class Veredicto(val etiqueta: String) {
    ACIERTO("Acierto"),
    /** Leyó otra cosa: lo peor que puede pasar en caja (se cobra otro producto). */
    LECTURA_INCORRECTA("Lectura incorrecta"),
    SIN_LECTURA("Sin lectura"),
    /** Leyó un código donde no hay ninguno. */
    FALSO_POSITIVO("Falso positivo"),
    /** No leyó nada donde no hay código: lo correcto. */
    RECHAZO_CORRECTO("Rechazo correcto"),
}

/** La primera lectura de un motor recién creado (frío) o las de uno ya calentado (caliente). */
enum class Pasada { FRIO, CALIENTE }

data class ResultadoEscena(
    val escena: EscenaBanco,
    val motor: Motor,
    val pasada: Pasada,
    /** null si el motor falló con una excepción. */
    val veredicto: Veredicto?,
    /** Lo leído, separado por | si hubo varios. */
    val leido: String,
    val ms: Double,
    val error: String? = null,
)

/** El resumen de un motor para la tabla del banco. */
data class ResumenMotor(
    val motor: Motor,
    val conCodigo: Int,
    val aciertos: Int,
    val incorrectas: Int,
    val sinLectura: Int,
    val sinCodigo: Int,
    val falsosPositivos: Int,
    val msFrio: Estadistica?,
    val msCaliente: Estadistica?,
    /** Por px/módulo: aciertos y escenas con código. */
    val aciertosPorModulo: Map<Float, Pair<Int, Int>>,
    val error: String?,
)

/** Decide si una lectura es la esperada. */
object Clasificador {
    fun clasificar(escena: EscenaBanco, lecturas: List<Lectura>): Veredicto {
        if (!escena.conCodigo) return if (lecturas.isEmpty()) Veredicto.RECHAZO_CORRECTO else Veredicto.FALSO_POSITIVO
        if (lecturas.isEmpty()) return Veredicto.SIN_LECTURA
        // Basta una lectura equivocada para que cuente como incorrecta: en caja podría ganar esa.
        return if (lecturas.all { mismoCodigo(escena.esperado!!, it.texto) }) Veredicto.ACIERTO else Veredicto.LECTURA_INCORRECTA
    }

    /** Igual, o el mismo GTIN con distinto número de ceros a la izquierda (ML Kit da el UPC-A en 12 y zxing-cpp en 13). */
    fun mismoCodigo(esperado: String, leido: String): Boolean {
        if (esperado == leido) return true
        val gtin = { t: String -> t.length in 8..14 && t.all { it in '0'..'9' } }
        return gtin(esperado) && gtin(leido) && esperado.padStart(14, '0') == leido.padStart(14, '0')
    }

    fun resumir(motor: Motor, resultados: List<ResultadoEscena>, error: String? = null): ResumenMotor {
        val propios = resultados.filter { it.motor == motor }
        val calientes = propios.filter { it.pasada == Pasada.CALIENTE && it.veredicto != null }
        val conCodigo = calientes.filter { it.escena.conCodigo }
        val porModulo = conCodigo.groupBy { it.escena.pxPorModulo }.toSortedMap().mapValues { (_, r) ->
            r.count { it.veredicto == Veredicto.ACIERTO } to r.size
        }
        return ResumenMotor(
            motor = motor,
            conCodigo = conCodigo.size,
            aciertos = conCodigo.count { it.veredicto == Veredicto.ACIERTO },
            incorrectas = conCodigo.count { it.veredicto == Veredicto.LECTURA_INCORRECTA },
            sinLectura = conCodigo.count { it.veredicto == Veredicto.SIN_LECTURA },
            sinCodigo = calientes.count { !it.escena.conCodigo },
            falsosPositivos = calientes.count { it.veredicto == Veredicto.FALSO_POSITIVO },
            msFrio = Estadistica.de(propios.filter { it.pasada == Pasada.FRIO && it.error == null }.map { it.ms }),
            msCaliente = Estadistica.de(calientes.map { it.ms }),
            aciertosPorModulo = porModulo,
            error = error ?: propios.firstOrNull { it.error != null }?.error,
        )
    }
}

/**
 * Corre las escenas por cada motor como lo haría la cámara: el plano Y de [anchoBuffer]×[altoBuffer] sin rotar, con
 * [rotacion] grados para dejarlo derecho, en un `ByteBuffer` directo, y la región de lectura en píxeles del buffer.
 * Se usa desde un solo hilo (los motores no son para varios hilos).
 */
class Banco(
    private val crearMotor: (Motor, Set<Simbologia>) -> MotorDeLectura,
    private val motores: List<Motor>,
    private val simbologias: Set<Simbologia>,
    /** Leer solo la franja central (como en vivo) o el cuadro completo. */
    private val recortarFranja: Boolean,
    private val altoFranja: Float = 0.36f,
    private val anchoBuffer: Int = 1280,
    private val altoBuffer: Int = 720,
    private val rotacion: Int = 90,
    /** Cuántas veces se crea cada motor para medir su primera lectura. */
    private val repeticionesFrio: Int = 3,
    private val reloj: () -> Long = System::nanoTime,
) {
    private val transformacion = Transformacion(Rectangulo(0, 0, anchoBuffer, altoBuffer), rotacion)
    val anchoDerecho: Int = transformacion.anchoDerecho
    val altoDerecho: Int = transformacion.altoDerecho

    /** La región que se le pasa al motor, en píxeles del buffer. */
    val region: Rectangulo =
        if (recortarFranja) transformacion.aBuffer(Caja(0f, 0.5f - altoFranja / 2f, 1f, 0.5f + altoFranja / 2f))
        else Rectangulo(0, 0, anchoBuffer, altoBuffer)

    // Se reutilizan entre escenas: ~0.9 MB cada uno a 1280x720.
    private val plano = ByteArray(anchoBuffer * altoBuffer)
    private val buffer: ByteBuffer = ByteBuffer.allocateDirect(anchoBuffer * altoBuffer)
    private val cuadro = CuadroY(buffer, anchoBuffer, anchoBuffer, altoBuffer, rotacion, region)

    /** La escena que está cargada en el buffer (para depurar y para los motores falsos de las pruebas). */
    @Volatile
    var escenaActual: EscenaBanco? = null
        private set

    /** Las pasadas que va a dar, para la barra de progreso. */
    fun pasos(escenas: List<EscenaBanco>) = motores.size * repeticionesFrio + escenas.size

    /**
     * Corre todo. [progreso] recibe los pasos hechos y lo que se está probando; si [cancelado] da true se detiene y
     * devuelve lo que lleve. Un motor que no se puede crear o que lanza una excepción queda con su error y los demás
     * siguen.
     */
    fun correr(
        escenas: List<EscenaBanco>,
        progreso: (hechos: Int, etiqueta: String) -> Unit = { _, _ -> },
        cancelado: () -> Boolean = { false },
    ): Pair<List<ResultadoEscena>, List<ResumenMotor>> {
        val resultados = ArrayList<ResultadoEscena>()
        val errores = HashMap<Motor, String>()
        var hechos = 0
        val conCodigo = escenas.filter { it.conCodigo }.ifEmpty { escenas }

        // Frío: un motor nuevo por repetición y solo su primera lectura.
        for (motor in motores) {
            for (k in 0 until repeticionesFrio) {
                if (cancelado() || motor in errores || conCodigo.isEmpty()) break
                val escena = conCodigo[k % conCodigo.size]
                progreso(hechos, "${motor.nombre} en frío ${k + 1} de $repeticionesFrio")
                cargar(escena)
                var m: MotorDeLectura? = null
                try {
                    m = crearMotor(motor, simbologias)
                    resultados += medir(m, motor, escena, Pasada.FRIO)
                } catch (t: Throwable) {
                    errores[motor] = descripcion(t)
                } finally {
                    runCatching { m?.close() }
                }
                hechos++
            }
        }
        hechos = motores.size * repeticionesFrio

        // Caliente: un motor por tipo, calentado, y todas las escenas alternando el orden (ABBA) para no favorecer a uno.
        val activos = LinkedHashMap<Motor, MotorDeLectura>()
        try {
            for (motor in motores) {
                if (motor in errores) continue
                try {
                    activos[motor] = crearMotor(motor, simbologias).also { it.calentar() }
                } catch (t: Throwable) {
                    errores[motor] = descripcion(t)
                }
            }
            for ((i, escena) in escenas.withIndex()) {
                if (cancelado()) break
                progreso(hechos, escena.id)
                cargar(escena)
                val orden = if (i % 2 == 0) activos.keys.toList() else activos.keys.reversed()
                for (motor in orden) {
                    val m = activos[motor] ?: continue
                    val r = medir(m, motor, escena, Pasada.CALIENTE)
                    resultados += r
                    if (r.error != null) {
                        errores[motor] = r.error
                        runCatching { m.close() }
                        activos.remove(motor)
                    }
                }
                hechos++
            }
        } finally {
            for (m in activos.values) runCatching { m.close() }
        }
        progreso(hechos, "")
        return resultados to motores.map { Clasificador.resumir(it, resultados, errores[it]) }
    }

    private fun medir(m: MotorDeLectura, motor: Motor, escena: EscenaBanco, pasada: Pasada): ResultadoEscena {
        buffer.rewind()
        val t0 = reloj()
        return try {
            val r = m.leer(cuadro)
            val ms = (reloj() - t0) / 1e6
            ResultadoEscena(escena, motor, pasada, Clasificador.clasificar(escena, r.lecturas), r.lecturas.joinToString("|") { it.texto }, ms)
        } catch (t: Throwable) {
            ResultadoEscena(escena, motor, pasada, null, "", (reloj() - t0) / 1e6, descripcion(t))
        }
    }

    /** Dibuja la escena derecha y la pone en el buffer sin rotar. */
    private fun cargar(escena: EscenaBanco) {
        aBuffer(CatalogoEscenas.dibujar(escena, anchoDerecho, altoDerecho), rotacion, plano, anchoBuffer, altoBuffer)
        escenaActual = escena
        buffer.clear()
        buffer.put(plano)
        buffer.rewind()
    }

    private fun descripcion(t: Throwable) = t::class.java.simpleName + (t.message?.let { ": $it" } ?: "")

    companion object {
        /**
         * Copia una imagen derecha a un buffer de [ancho]×[alto] sin rotar, tal que girándolo [rotacion] grados en sentido
         * horario (`rotationDegrees`) se vuelve a ver derecha. Es el inverso de [Transformacion.aVisible], píxel a píxel.
         */
        fun aBuffer(derecha: Luma, rotacion: Int, destino: ByteArray, ancho: Int, alto: Int) {
            val wd = derecha.ancho
            val p = derecha.pixeles
            when (rotacion) {
                0 -> System.arraycopy(p, 0, destino, 0, ancho * alto)
                // Derecha (ux, uy) = (alto − 1 − y, x).
                90 -> for (y in 0 until alto) {
                    val ux = alto - 1 - y
                    val fila = y * ancho
                    for (x in 0 until ancho) destino[fila + x] = p[x * wd + ux]
                }
                // Derecha (ux, uy) = (ancho − 1 − x, alto − 1 − y).
                180 -> for (y in 0 until alto) {
                    val fila = y * ancho
                    val uy = (alto - 1 - y) * wd
                    for (x in 0 until ancho) destino[fila + x] = p[uy + ancho - 1 - x]
                }
                // Derecha (ux, uy) = (y, ancho − 1 − x).
                270 -> for (y in 0 until alto) {
                    val fila = y * ancho
                    for (x in 0 until ancho) destino[fila + x] = p[(ancho - 1 - x) * wd + y]
                }
                else -> throw IllegalArgumentException("Rotación inválida: $rotacion")
            }
        }
    }
}
