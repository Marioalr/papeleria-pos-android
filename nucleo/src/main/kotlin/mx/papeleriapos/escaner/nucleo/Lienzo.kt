package mx.papeleriapos.escaner.nucleo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Una imagen en escala de grises (el plano Y de la cámara): un byte por píxel, renglón por renglón. */
class Luma(val ancho: Int, val alto: Int, val pixeles: ByteArray = ByteArray(ancho * alto)) {
    init {
        require(pixeles.size >= ancho * alto) { "Faltan píxeles." }
    }

    operator fun get(x: Int, y: Int): Int = pixeles[y * ancho + x].toInt() and 0xFF
}

/**
 * Cómo se ve un código en la escena de prueba. Las posiciones van de 0 a 1 respecto a la imagen; [anchoRelativo] es el
 * ancho del código (con sus zonas en blanco) respecto al ancho de la imagen.
 */
data class Escena(
    val centroX: Float = 0.5f,
    val centroY: Float = 0.5f,
    val anchoRelativo: Float = 0.5f,
    /** Alto de las barras respecto a su ancho total. */
    val proporcionAlto: Float = 0.35f,
    val giroGrados: Float = 0f,
    /** Inclinación hacia atrás (perspectiva): 0 es de frente; 0.3 encoge 30 % el lado de arriba. */
    val perspectiva: Float = 0f,
    val fondo: Int = 170,
    val espacio: Int = 235,
    val barra: Int = 30,
    /** Radio del desenfoque (cajas sucesivas, parecido a un desenfoque gaussiano), en píxeles. */
    val desenfoque: Int = 0,
    /** Barrido horizontal (celular movido), en píxeles. */
    val movido: Int = 0,
    val ruido: Float = 0f,
    /** Reflejo: un círculo que satura, centrado en el código, de este radio relativo al ancho del código. */
    val reflejo: Float = 0f,
    val semilla: Int = 1,
)

/** Dibuja códigos de prueba en un plano de luminancia, sin dependencias de Android. */
object Lienzo {
    fun dibujar(modulos: BooleanArray, zonaEnBlanco: Int, ancho: Int, alto: Int, escena: Escena): Luma {
        val total = modulos.size + 2 * zonaEnBlanco
        val anchoPx = escena.anchoRelativo * ancho
        val altoPx = anchoPx * escena.proporcionAlto
        val cx = escena.centroX * ancho
        val cy = escena.centroY * alto
        val giro = escena.giroGrados * PI.toFloat() / 180f
        val c = cos(giro)
        val s = sin(giro)
        val px = ByteArray(ancho * alto)
        // Supermuestreo 3x3 para que los bordes de las barras queden suaves, como en una foto.
        val sub = 3
        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                var suma = 0
                for (sy in 0 until sub) for (sx in 0 until sub) {
                    val fx = x + (sx + 0.5f) / sub - cx
                    val fy = y + (sy + 0.5f) / sub - cy
                    // Deshacer el giro.
                    val rx = c * fx + s * fy
                    val ry = -s * fx + c * fy
                    // Perspectiva: arriba (ry negativo) se ve más angosto.
                    val v = ry / altoPx + 0.5f
                    val escala = 1f - escena.perspectiva * (1f - v.coerceIn(0f, 1f))
                    val u = rx / (anchoPx * escala) + 0.5f
                    suma += if (u in 0f..1f && v in 0f..1f) {
                        val m = (u * total).toInt() - zonaEnBlanco
                        if (m in modulos.indices && modulos[m]) escena.barra else escena.espacio
                    } else {
                        escena.fondo
                    }
                }
                px[y * ancho + x] = (suma / (sub * sub)).toByte()
            }
        }
        val luma = Luma(ancho, alto, px)
        if (escena.reflejo > 0f) reflejo(luma, cx, cy, escena.reflejo * anchoPx)
        if (escena.desenfoque > 0) repeat(3) { caja(luma, escena.desenfoque, escena.desenfoque) }
        if (escena.movido > 0) caja(luma, escena.movido, 0)
        if (escena.ruido > 0f) ruido(luma, escena.ruido, Random(escena.semilla))
        return luma
    }

    /**
     * Ruido del sensor: gaussiano aproximado (suma de tres uniformes, sin el costo de Box-Muller) de desviación [sigma],
     * con [azar]; sin cambios si [sigma] ≤ 0. La misma receta para las escenas, el banco de pruebas y el modo «Límites».
     */
    fun ruido(luma: Luma, sigma: Float, azar: Random): Luma {
        if (sigma <= 0f) return luma
        val p = luma.pixeles
        for (i in 0 until luma.ancho * luma.alto) {
            val n = (azar.nextFloat() + azar.nextFloat() + azar.nextFloat() - 1.5f) * 2f * sigma
            p[i] = ((p[i].toInt() and 0xFF) + n).roundToInt().coerceIn(0, 255).toByte()
        }
        return luma
    }

    /** Escala la luminancia (poca luz < 1). */
    fun ganancia(luma: Luma, factor: Float): Luma {
        val p = luma.pixeles
        for (i in 0 until luma.ancho * luma.alto) p[i] = ((p[i].toInt() and 0xFF) * factor).roundToInt().coerceIn(0, 255).toByte()
        return luma
    }

    private fun reflejo(luma: Luma, cx: Float, cy: Float, radio: Float) {
        val p = luma.pixeles
        for (y in max(0, (cy - radio).toInt()) until min(luma.alto, (cy + radio).toInt() + 1)) {
            for (x in max(0, (cx - radio).toInt()) until min(luma.ancho, (cx + radio).toInt() + 1)) {
                val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / radio
                if (d < 1f) {
                    val i = y * luma.ancho + x
                    val fuerza = exp(-3f * d * d)
                    val nuevo = (p[i].toInt() and 0xFF) + (255 * 1.6f * fuerza).toInt()
                    p[i] = nuevo.coerceAtMost(255).toByte()
                }
            }
        }
    }

    /** Desenfoque de caja separable con radios [rx] y [ry]. */
    private fun caja(luma: Luma, rx: Int, ry: Int) {
        val w = luma.ancho
        val h = luma.alto
        val p = luma.pixeles
        val tmp = IntArray(max(w, h))
        if (rx > 0) for (y in 0 until h) {
            for (x in 0 until w) tmp[x] = p[y * w + x].toInt() and 0xFF
            var suma = 0
            for (k in -rx..rx) suma += tmp[k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                p[y * w + x] = (suma / (2 * rx + 1)).toByte()
                suma += tmp[(x + rx + 1).coerceAtMost(w - 1)] - tmp[(x - rx).coerceAtLeast(0)]
            }
        }
        if (ry > 0) for (x in 0 until w) {
            for (y in 0 until h) tmp[y] = p[y * w + x].toInt() and 0xFF
            var suma = 0
            for (k in -ry..ry) suma += tmp[k.coerceIn(0, h - 1)]
            for (y in 0 until h) {
                p[y * w + x] = (suma / (2 * ry + 1)).toByte()
                suma += tmp[(y + ry + 1).coerceAtMost(h - 1)] - tmp[(y - ry).coerceAtLeast(0)]
            }
        }
    }
}
