package mx.papeleriapos.escaner.motores

import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.nucleo.Barras
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Escena
import mx.papeleriapos.escaner.nucleo.Gtin
import mx.papeleriapos.escaner.nucleo.Lienzo
import mx.papeleriapos.escaner.nucleo.Luma
import mx.papeleriapos.escaner.nucleo.Rectangulo
import mx.papeleriapos.escaner.nucleo.Rotacion
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.nucleo.Transformacion
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** La franja por defecto del escáner (EstadoEscaner.franja). */
val FRANJA = Caja(0.04f, 0.32f, 0.96f, 0.68f)

const val ANCHO_BUFFER = 1280
const val ALTO_BUFFER = 720

/**
 * Lo visible en un celular 20:9 en vertical (rotación 90) con FILL_CENTER: del derecho de 720x1280 solo se ven ~576 px
 * de ancho, que en el buffer son las filas 72 a 648. En horizontal (rotación 0) se ve todo.
 */
fun visiblePara(rotacion: Int): Rectangulo =
    if (rotacion % 180 != 0) Rectangulo(0, 72, ANCHO_BUFFER, 648) else Rectangulo(0, 0, ANCHO_BUFFER, ALTO_BUFFER)

/**
 * Un cuadro de prueba como lo daría la cámara: el plano Y del buffer sin rotar (con relleno al final de cada renglón,
 * como en muchos celulares) y dónde quedó el código, en el espacio derecho del buffer completo.
 */
class EscenaBuffer(
    val pixeles: ByteArray,
    val pasoFila: Int,
    val rotacion: Int,
    val visible: Rectangulo,
    /** La zona de barras (sin zonas en blanco) en el derecho completo; null si la escena no tiene código. */
    val barrasDerecho: Caja?,
    val moduloPx: Float,
) {
    val transformacion = Transformacion(visible, rotacion)
    val regionFranja: Rectangulo = transformacion.aBuffer(FRANJA)

    /** Directo, como el de CameraX (zxing-cpp lee su dirección nativa). */
    val buffer: ByteBuffer = ByteBuffer.allocateDirect(pixeles.size).also { it.put(pixeles); it.rewind() }

    fun cuadro(region: Rectangulo = regionFranja, conProxy: Boolean = false) = CuadroY(
        buffer, pasoFila, ANCHO_BUFFER, ALTO_BUFFER, rotacion, region,
        if (conProxy) ProxyDePrueba(buffer, pasoFila, ANCHO_BUFFER, ALTO_BUFFER, rotacion, visible) else null,
    )

    /** Las esquinas de un motor (buffer) pasadas al derecho completo, para compararlas con [barrasDerecho]. */
    fun aDerecho(esquinasBuffer: FloatArray): FloatArray {
        val p = FloatArray(2)
        val salida = FloatArray(esquinasBuffer.size)
        for (i in esquinasBuffer.indices step 2) {
            Rotacion.aDerecho(esquinasBuffer[i], esquinasBuffer[i + 1], ANCHO_BUFFER, ALTO_BUFFER, rotacion, p)
            salida[i] = p[0]
            salida[i + 1] = p[1]
        }
        return salida
    }

    /**
     * Las esquinas caen sobre las barras (con [tolerancia] px) y cubren al menos [cobertura] de su largo: así se sabe
     * que la conversión recorte → buffer es correcta, no solo que el texto salió bien.
     */
    fun esquinasSobreElCodigo(esquinasBuffer: FloatArray, tolerancia: Float, cobertura: Float = 0.5f): String? {
        val b = barrasDerecho ?: return "la escena no tiene código"
        val d = aDerecho(esquinasBuffer)
        for (i in d.indices step 2) {
            val x = d[i]
            val y = d[i + 1]
            if (x < b.izquierda - tolerancia || x > b.derecha + tolerancia || y < b.arriba - tolerancia || y > b.abajo + tolerancia) {
                return "esquina ($x, $y) fuera de las barras $b ± $tolerancia"
            }
        }
        val caja = Caja.deEsquinas(d)
        if (caja.ancho < b.ancho * cobertura) return "la caja $caja cubre menos de ${cobertura * 100}% del largo de $b"
        return null
    }

    companion object {
        /**
         * Dibuja el código derecho (como lo ve el empleado) centrado en ([centroX], [centroY]) de la región visible y lo
         * gira al buffer de la cámara.
         */
        fun conCodigo(
            simbologia: Simbologia,
            texto: String,
            rotacion: Int = 90,
            moduloPx: Float = 3f,
            centroX: Float = 0.5f,
            centroY: Float = 0.5f,
            desenfoque: Int = 0,
            ruido: Float = 0f,
            pasoFila: Int = if (rotacion % 180 != 0) ANCHO_BUFFER + 64 else ANCHO_BUFFER,
        ): EscenaBuffer {
            val visible = visiblePara(rotacion)
            val (anchoDerecho, altoDerecho) = medidasDerecho(rotacion)
            val visibleDerecho = visibleEnDerecho(visible, rotacion)
            val cx = visibleDerecho.izquierda + centroX * visibleDerecho.ancho
            val cy = visibleDerecho.arriba + centroY * visibleDerecho.alto
            val modulos = Barras.para(simbologia, texto)
            val zona = Barras.zonaEnBlanco(simbologia)
            val anchoPx = (modulos.size + 2 * zona) * moduloPx
            val escena = Escena(
                centroX = cx / anchoDerecho, centroY = cy / altoDerecho, anchoRelativo = anchoPx / anchoDerecho,
                desenfoque = desenfoque, ruido = ruido,
            )
            val luma = Lienzo.dibujar(modulos, zona, anchoDerecho, altoDerecho, escena)
            val altoPx = anchoPx * escena.proporcionAlto
            val barras = Caja(cx - anchoPx / 2 + zona * moduloPx, cy - altoPx / 2, cx + anchoPx / 2 - zona * moduloPx, cy + altoPx / 2)
            return EscenaBuffer(alBuffer(luma, rotacion, pasoFila), pasoFila, rotacion, visible, barras, moduloPx)
        }

        /** Escenas sin código para buscar lecturas falsas: cosas que un motor podría confundir con barras. */
        fun sinCodigo(tipo: String, rotacion: Int = 90, semilla: Int = 7): EscenaBuffer {
            val (anchoDerecho, altoDerecho) = medidasDerecho(rotacion)
            val r = Random(semilla)
            val px = ByteArray(anchoDerecho * altoDerecho) { 180.toByte() }
            fun rect(x0: Int, y0: Int, x1: Int, y1: Int, v: Int) {
                for (y in max(0, y0) until min(altoDerecho, y1)) for (x in max(0, x0) until min(anchoDerecho, x1)) {
                    px[y * anchoDerecho + x] = v.toByte()
                }
            }
            when (tipo) {
                "gris" -> Unit
                "ruido" -> for (i in px.indices) px[i] = (120 + r.nextInt(-60, 61)).toByte()
                // Hoja rayada: renglones horizontales cada 24 px.
                "rayada" -> {
                    rect(0, 0, anchoDerecho, altoDerecho, 235)
                    var y = 12
                    while (y < altoDerecho) { rect(0, y, anchoDerecho, y + 2, 90); y += 24 }
                }
                // Franjas verticales de anchos al azar: lo más parecido a un código sin serlo.
                "franjas" -> {
                    rect(0, 0, anchoDerecho, altoDerecho, 230)
                    var x = anchoDerecho / 8
                    var oscura = true
                    val y0 = altoDerecho * 2 / 5
                    val y1 = altoDerecho * 3 / 5
                    while (x < anchoDerecho * 7 / 8) {
                        val w = 2 + r.nextInt(0, 10)
                        if (oscura) rect(x, y0, x + w, y1, 30)
                        oscura = !oscura
                        x += w
                    }
                }
                // Texto: renglones de «letras» (bloques de 6 a 14 px con trazos).
                "texto" -> {
                    rect(0, 0, anchoDerecho, altoDerecho, 235)
                    var y = 40
                    while (y < altoDerecho - 40) {
                        var x = 30
                        while (x < anchoDerecho - 40) {
                            val w = 6 + r.nextInt(0, 9)
                            if (r.nextInt(0, 6) != 0) {
                                rect(x, y, x + 2, y + 16, 40)
                                rect(x, y + 7, x + w, y + 9, 40)
                                if (r.nextBoolean()) rect(x + w - 2, y, x + w, y + 16, 40)
                            }
                            x += w + 4
                        }
                        y += 30
                    }
                }
                else -> error("Escena desconocida: $tipo")
            }
            val luma = Luma(anchoDerecho, altoDerecho, px)
            val pasoFila = if (rotacion % 180 != 0) ANCHO_BUFFER + 64 else ANCHO_BUFFER
            return EscenaBuffer(alBuffer(luma, rotacion, pasoFila), pasoFila, rotacion, visiblePara(rotacion), null, 0f)
        }

        private fun medidasDerecho(rotacion: Int) =
            if (rotacion % 180 != 0) ALTO_BUFFER to ANCHO_BUFFER else ANCHO_BUFFER to ALTO_BUFFER

        /** La región visible del buffer, en el derecho completo. */
        private fun visibleEnDerecho(visible: Rectangulo, rotacion: Int): Caja {
            val p = FloatArray(2)
            val xy = FloatArray(4)
            Rotacion.aDerecho(visible.izquierda.toFloat(), visible.arriba.toFloat(), ANCHO_BUFFER, ALTO_BUFFER, rotacion, p)
            xy[0] = p[0]; xy[1] = p[1]
            Rotacion.aDerecho(visible.derecha.toFloat(), visible.abajo.toFloat(), ANCHO_BUFFER, ALTO_BUFFER, rotacion, p)
            xy[2] = p[0]; xy[3] = p[1]
            return Caja.deEsquinas(xy)
        }

        /** Gira el derecho al buffer de la cámara: cada píxel del buffer toma el del derecho que le corresponde. */
        private fun alBuffer(derecho: Luma, rotacion: Int, pasoFila: Int): ByteArray {
            val salida = ByteArray(pasoFila * ALTO_BUFFER)
            val p = FloatArray(2)
            for (y in 0 until ALTO_BUFFER) for (x in 0 until ANCHO_BUFFER) {
                Rotacion.aDerecho(x + 0.5f, y + 0.5f, ANCHO_BUFFER, ALTO_BUFFER, rotacion, p)
                val ux = p[0].toInt().coerceIn(0, derecho.ancho - 1)
                val uy = p[1].toInt().coerceIn(0, derecho.alto - 1)
                salida[y * pasoFila + x] = derecho.pixeles[uy * derecho.ancho + ux]
            }
            return salida
        }
    }
}

/** El código en GTIN-13 si es EAN/UPC (ML Kit da UPC-A en 12 dígitos); lo demás tal cual. */
fun comparable(texto: String, simbologia: Simbologia): String = when {
    simbologia == Simbologia.UPC_A && texto.length == 12 -> "0$texto"
    simbologia == Simbologia.EAN_13 && texto.length == 12 && Gtin.esValido(texto) -> "0$texto"
    else -> texto
}

/** Un `ImageProxy` sobre un plano Y en memoria, para probar el camino en vivo de zxing-cpp (cropRect + rotación). */
class ProxyDePrueba(
    private val y: ByteBuffer,
    private val pasoFila: Int,
    private val ancho: Int,
    private val alto: Int,
    private val rotacion: Int,
    recorte: Rectangulo,
) : ImageProxy {
    private var recorteActual = Rect(recorte.izquierda, recorte.arriba, recorte.derecha, recorte.abajo)
    private val plano = object : ImageProxy.PlaneProxy {
        override fun getRowStride() = pasoFila
        override fun getPixelStride() = 1
        override fun getBuffer(): ByteBuffer = y
    }
    private val info = object : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
        override fun getTimestamp() = 0L
        override fun getRotationDegrees() = rotacion
        override fun populateExifData(exifBuilder: ExifData.Builder) = Unit
    }

    override fun close() = Unit
    override fun getCropRect() = Rect(recorteActual)
    override fun setCropRect(rect: Rect?) {
        recorteActual = if (rect == null) Rect(0, 0, ancho, alto) else Rect(rect)
    }
    override fun getFormat() = ImageFormat.YUV_420_888
    override fun getHeight() = alto
    override fun getWidth() = ancho
    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(plano)
    override fun getImageInfo(): ImageInfo = info

    @ExperimentalGetImage
    override fun getImage(): Image? = null
}
