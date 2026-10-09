package mx.papeleriapos.android.banco

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.sesion.DatosEquipo
import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Barras
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Escena
import mx.papeleriapos.escaner.nucleo.Lienzo
import mx.papeleriapos.escaner.nucleo.Luma
import mx.papeleriapos.escaner.nucleo.Rectangulo
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.nucleo.Transformacion
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Lo que se va haciendo más difícil en el modo «Límites» del banco, del grado más fácil al más difícil. Los grados son
 * el valor de la condición en su unidad (px de desenfoque, luz relativa, px por módulo…).
 */
enum class CondicionLimite(val etiqueta: String, val unidad: String, val grados: List<Float>) {
    DESENFOQUE("Desenfoque", "px de radio", listOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 8f)),
    MOVIDO("Movido", "px de barrido", listOf(0f, 2f, 4f, 6f, 8f, 10f, 13f, 16f, 20f)),
    REFLEJO("Reflejo", "radio ÷ ancho del código", listOf(0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.65f, 0.8f, 1f)),
    /** La imagen se oscurece a la luz del grado y el ruido sube como la ganancia del sensor (1.5/√luz). */
    POCA_LUZ("Poca luz + ruido", "luz (1 = normal)", listOf(1f, 0.6f, 0.4f, 0.3f, 0.2f, 0.15f, 0.1f, 0.07f, 0.05f)),
    TAMANO("Tamaño", "px por módulo", listOf(3f, 2.5f, 2f, 1.7f, 1.5f, 1.3f, 1.15f, 1f, 0.9f, 0.8f)),
    GIRO("Giro", "grados", listOf(0f, 10f, 20f, 30f, 40f, 50f, 60f, 75f, 90f)),
    PERSPECTIVA("Perspectiva", "fracción", listOf(0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f)),
}

/**
 * Una escena del modo «Límites»: el código de prueba con la condición en un grado y una variante (semilla) que mueve
 * un poco la posición y el giro y cambia el ruido, para que las variantes no sean la misma imagen.
 */
data class EscenaLimite(val condicion: CondicionLimite, val indiceGrado: Int, val variante: Int) {
    val grado: Float get() = condicion.grados[indiceGrado]

    /** El ruido del sensor que llevan todas (salvo la de poca luz, que lleva el suyo). */
    val ruido: Float
        get() = if (condicion == CondicionLimite.POCA_LUZ) (1.5f / sqrt(grado.coerceAtLeast(0.01f))) else RUIDO_BASE

    val luz: Float get() = if (condicion == CondicionLimite.POCA_LUZ) grado else 1f

    /** Cómo se dibuja en una imagen derecha de [anchoDerecho] de ancho. */
    fun escena(anchoDerecho: Int): Escena {
        val azar = Random(SEMILLA_BASE + condicion.ordinal * 1000 + indiceGrado * 37 + variante)
        val modulo = if (condicion == CondicionLimite.TAMANO) grado else PX_POR_MODULO_BASE
        val anchoPx = (Barras.para(SIMBOLOGIA, CODIGO).size + 2 * Barras.zonaEnBlanco(SIMBOLOGIA)) * modulo
        val giroBase = if (condicion == CondicionLimite.GIRO) grado else 0f
        return Escena(
            centroX = 0.5f + (azar.nextFloat() - 0.5f) * 0.08f,
            centroY = 0.5f + (azar.nextFloat() - 0.5f) * 0.04f,
            anchoRelativo = anchoPx / anchoDerecho,
            giroGrados = giroBase + (azar.nextFloat() - 0.5f) * 6f,
            perspectiva = if (condicion == CondicionLimite.PERSPECTIVA) grado else 0f,
            desenfoque = if (condicion == CondicionLimite.DESENFOQUE) grado.roundToInt() else 0,
            movido = if (condicion == CondicionLimite.MOVIDO) grado.roundToInt() else 0,
            reflejo = if (condicion == CondicionLimite.REFLEJO) grado else 0f,
            // El ruido se pone después de la luz (la ganancia lo sube); aquí va sin ruido.
            ruido = 0f,
            semilla = 1 + variante,
        )
    }

    /** Dibuja la escena derecha de [ancho]×[alto]. */
    fun dibujar(ancho: Int, alto: Int): Luma {
        val luma = Lienzo.dibujar(Barras.para(SIMBOLOGIA, CODIGO), Barras.zonaEnBlanco(SIMBOLOGIA), ancho, alto, escena(ancho))
        if (luz != 1f) Lienzo.ganancia(luma, luz)
        ruido(luma, ruido, Random(SEMILLA_BASE * 3 + condicion.ordinal * 7919 + indiceGrado * 101 + variante))
        return luma
    }

    companion object {
        /** El código de prueba: el EAN-13 750… de casi todo lo que se vende en la tienda. */
        val SIMBOLOGIA = Simbologia.EAN_13
        const val CODIGO = "7501234567893"
        const val PX_POR_MODULO_BASE = 3f
        const val RUIDO_BASE = 2f
        private const val SEMILLA_BASE = 20261009

        private fun ruido(luma: Luma, sigma: Float, azar: Random) {
            if (sigma <= 0f) return
            val p = luma.pixeles
            for (i in 0 until luma.ancho * luma.alto) {
                // Suma de tres uniformes: casi normal y sin Box-Muller.
                val n = (azar.nextFloat() + azar.nextFloat() + azar.nextFloat() - 1.5f) * 2f * sigma
                p[i] = ((p[i].toInt() and 0xFF) + n).roundToInt().coerceIn(0, 255).toByte()
            }
        }
    }
}

/** Cómo le fue a un motor en un grado: cuántas variantes leyó bien. */
data class TasaGrado(
    val grado: Float,
    val variantes: Int,
    val leidas: Int,
    val incorrectas: Int,
    val msMediana: Double,
) {
    val tasa: Double get() = if (variantes == 0) 0.0 else leidas.toDouble() / variantes
}

/**
 * El límite de un motor en una condición: el último grado que leyó en ≥ 80 % de las variantes ANTES del primer grado que
 * falló. Un grado que pasa por suerte después de uno que falló queda en [grados] (y en el CSV), pero no sube el límite.
 */
data class LimiteMotor(
    val motor: Motor,
    val condicion: CondicionLimite,
    /** null = ni el grado más fácil. */
    val ultimoGrado: Float?,
    /** Leyó hasta el grado más difícil que hay: el límite está más allá. */
    val alcanzoElMaximo: Boolean,
    val grados: List<TasaGrado>,
    val error: String? = null,
)

/**
 * El modo «Límites» del banco de pruebas: para cada motor y cada condición sube la dificultad en pasos y encuentra el
 * último grado que se lee en al menos [umbral] de las [variantes] antes del primero que no ([limiteAntesDelPrimerFallo]).
 * Un motor sigue probando hasta [fallosParaParar] grados seguidos sin pasar, para que el CSV muestre si fue mala suerte,
 * pero eso ya no mueve el límite: con pocas variantes un grado puede pasar por suerte. Cada escena se dibuja
 * una vez y la leen todos los motores que siguen subiendo, como en el banco normal: el plano Y de 1280×720 girado 90°
 * en un buffer directo.
 */
class BancoLimites(
    private val crearMotor: (Motor, Set<Simbologia>) -> MotorDeLectura,
    private val motores: List<Motor>,
    private val simbologias: Set<Simbologia>,
    recortarFranja: Boolean,
    altoFranja: Float = 0.36f,
    private val anchoBuffer: Int = 1280,
    private val altoBuffer: Int = 720,
    private val rotacion: Int = 90,
    private val condiciones: List<CondicionLimite> = CondicionLimite.entries,
    private val variantes: Int = 5,
    private val umbral: Double = 0.8,
    private val fallosParaParar: Int = 2,
    private val reloj: () -> Long = System::nanoTime,
) {
    private val transformacion = Transformacion(Rectangulo(0, 0, anchoBuffer, altoBuffer), rotacion)
    private val anchoDerecho = transformacion.anchoDerecho
    private val altoDerecho = transformacion.altoDerecho
    val region: Rectangulo =
        if (recortarFranja) transformacion.aBuffer(Caja(0f, 0.5f - altoFranja / 2f, 1f, 0.5f + altoFranja / 2f))
        else Rectangulo(0, 0, anchoBuffer, altoBuffer)

    private val plano = ByteArray(anchoBuffer * altoBuffer)
    private val buffer: ByteBuffer = ByteBuffer.allocateDirect(anchoBuffer * altoBuffer)
    private val cuadro = CuadroY(buffer, anchoBuffer, anchoBuffer, altoBuffer, rotacion, region)

    /** La escena cargada en el buffer (para los motores falsos de las pruebas). */
    @Volatile
    var escenaActual: EscenaLimite? = null
        private set

    /** Lo más que puede tardar (si ningún motor se detiene antes), para la barra de progreso. */
    fun pasos(): Int = condiciones.sumOf { it.grados.size * variantes }

    fun correr(
        progreso: (hechos: Int, etiqueta: String) -> Unit = { _, _ -> },
        cancelado: () -> Boolean = { false },
    ): List<LimiteMotor> {
        val errores = HashMap<Motor, String>()
        val activos = LinkedHashMap<Motor, MotorDeLectura>()
        val salida = ArrayList<LimiteMotor>()
        var hechos = 0
        try {
            for (m in motores) {
                try {
                    activos[m] = crearMotor(m, simbologias).also { it.calentar() }
                } catch (t: Throwable) {
                    errores[m] = descripcion(t)
                }
            }
            for (condicion in condiciones) {
                if (cancelado()) break
                val subiendo = activos.keys.filterTo(LinkedHashSet()) { it !in errores }
                val fallos = HashMap<Motor, Int>()
                val tasas = HashMap<Motor, MutableList<TasaGrado>>()
                for (m in subiendo) tasas[m] = ArrayList()
                for (g in condicion.grados.indices) {
                    if (subiendo.isEmpty() || cancelado()) break
                    val leidas = HashMap<Motor, Int>()
                    val incorrectas = HashMap<Motor, Int>()
                    val tiempos = HashMap<Motor, MutableList<Double>>()
                    for (v in 0 until variantes) {
                        if (cancelado()) break
                        val escena = EscenaLimite(condicion, g, v)
                        progreso(hechos, "${condicion.etiqueta} ${Csv.decimal(escena.grado, 2)} · variante ${v + 1}")
                        cargar(escena)
                        val orden = if (v % 2 == 0) subiendo.toList() else subiendo.reversed()
                        for (m in orden) {
                            val motor = activos[m] ?: continue
                            buffer.rewind()
                            val t0 = reloj()
                            val veredicto = try {
                                val r = motor.leer(cuadro)
                                when {
                                    r.lecturas.isEmpty() -> Veredicto.SIN_LECTURA
                                    r.lecturas.all { Clasificador.mismoCodigo(EscenaLimite.CODIGO, it.texto) } -> Veredicto.ACIERTO
                                    else -> Veredicto.LECTURA_INCORRECTA
                                }
                            } catch (t: Throwable) {
                                errores[m] = descripcion(t)
                                null
                            }
                            tiempos.getOrPut(m) { ArrayList() } += (reloj() - t0) / 1e6
                            if (veredicto == Veredicto.ACIERTO) leidas[m] = (leidas[m] ?: 0) + 1
                            if (veredicto == Veredicto.LECTURA_INCORRECTA) incorrectas[m] = (incorrectas[m] ?: 0) + 1
                        }
                        // Un motor que tronó sale de todo (como en el banco normal).
                        for (m in errores.keys) if (subiendo.remove(m)) activos.remove(m)?.let { runCatching { it.close() } }
                        hechos++
                    }
                    for (m in subiendo.toList()) {
                        val ms = tiempos[m]?.let { Estadistica.de(it)?.mediana } ?: Double.NaN
                        val tasa = TasaGrado(condicion.grados[g], variantes, leidas[m] ?: 0, incorrectas[m] ?: 0, ms)
                        tasas.getValue(m) += tasa
                        if (tasa.tasa >= umbral) {
                            fallos[m] = 0
                        } else {
                            val f = (fallos[m] ?: 0) + 1
                            fallos[m] = f
                            if (f >= fallosParaParar) subiendo.remove(m)
                        }
                    }
                }
                for (m in motores) {
                    val propias = tasas[m] ?: emptyList()
                    val ultimoGrado = limiteAntesDelPrimerFallo(propias, umbral)
                    salida += LimiteMotor(
                        motor = m,
                        condicion = condicion,
                        ultimoGrado = ultimoGrado,
                        alcanzoElMaximo = ultimoGrado != null && ultimoGrado == condicion.grados.last(),
                        grados = propias,
                        error = errores[m],
                    )
                }
            }
        } finally {
            for (m in activos.values) runCatching { m.close() }
        }
        progreso(pasos(), "")
        return salida
    }

    private fun cargar(escena: EscenaLimite) {
        Banco.aBuffer(escena.dibujar(anchoDerecho, altoDerecho), rotacion, plano, anchoBuffer, altoBuffer)
        escenaActual = escena
        buffer.clear()
        buffer.put(plano)
        buffer.rewind()
    }

    private fun descripcion(t: Throwable) = t::class.java.simpleName + (t.message?.let { ": $it" } ?: "")
}

/**
 * El límite de una escalera: el último grado que pasó ([umbral]) antes del primero que falló; null si falló el primero
 * (o no hay). Los grados van del más fácil al más difícil.
 */
fun limiteAntesDelPrimerFallo(grados: List<TasaGrado>, umbral: Double = 0.8): Float? {
    var limite: Float? = null
    for (g in grados) {
        if (g.tasa < umbral) break
        limite = g.grado
    }
    return limite
}

/** El CSV del modo «Límites»: un renglón por motor, condición y grado probado. */
object CsvLimites {
    val COLUMNAS = listOf(
        "limites", "motor", "condicion", "unidad", "grado", "variantes", "leidas", "incorrectas", "tasa", "pasa",
        "es_limite", "limite", "ms_mediana", "error", "dispositivo", "sdk", "version_app", "commit",
    )

    fun escribir(clave: String, limites: List<LimiteMotor>, equipo: DatosEquipo, umbral: Double = 0.8): String {
        val salida = StringBuilder(Csv.BOM)
        Csv.renglon(COLUMNAS, salida)
        for (l in limites) {
            val filas = l.grados.ifEmpty { listOf(null) }
            for (t in filas) {
                Csv.renglon(
                    listOf(
                        clave,
                        l.motor.name,
                        l.condicion.name,
                        l.condicion.unidad,
                        t?.let { Csv.decimal(it.grado, 2) } ?: "",
                        t?.variantes?.toString() ?: "",
                        t?.leidas?.toString() ?: "",
                        t?.incorrectas?.toString() ?: "",
                        t?.let { Csv.decimal(it.tasa, 2) } ?: "",
                        t?.let { if (it.tasa >= umbral) "si" else "no" } ?: "",
                        if (t != null && l.ultimoGrado == t.grado) "si" else "no",
                        textoLimite(l),
                        t?.msMediana?.takeIf { !it.isNaN() }?.let { Csv.decimal(it, 2) } ?: "",
                        l.error ?: "",
                        Csv.textoSeguro(equipo.dispositivo),
                        equipo.sdk.toString(),
                        equipo.versionApp,
                        equipo.commit,
                    ),
                    salida,
                )
            }
        }
        return salida.toString()
    }

    /** «≥ 0.80» si leyó hasta el grado más difícil, «—» si ni el más fácil. */
    fun textoLimite(l: LimiteMotor): String {
        val g = l.ultimoGrado ?: return "—"
        val texto = if (g % 1f == 0f) g.toInt().toString() else Csv.decimal(g, 2)
        return if (l.alcanzoElMaximo) "≥ $texto" else texto
    }
}
