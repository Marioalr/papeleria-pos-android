package mx.papeleriapos.escaner.motores

import android.graphics.Point
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Rotacion
import mx.papeleriapos.escaner.nucleo.Simbologia
import java.util.concurrent.ExecutionException

/**
 * ML Kit bundled (docs/escaner.md, «Motores»). ML Kit ignora el `cropRect` (issue #491), así que la región se copia a
 * un NV21 propio: el plano Y tal cual y el color en 128 (gris), que el lector de barras no usa. Las esquinas llegan en
 * el recorte ya derecho y se regresan al buffer.
 */
class MotorMlKit(simbologias: Set<Simbologia>) : MotorDeLectura {
    override val motor = Motor.ML_KIT

    private val cliente: BarcodeScanner

    init {
        val formatos = simbologias.mapNotNull(::aFormatoMlKit).distinct()
        require(formatos.isNotEmpty()) { "ML Kit no lee ninguna de: ${simbologias.joinToString { it.nombre }}." }
        // Nunca FORMAT_ALL_FORMATS: Code 39/93 y Codabar tienen verificador débil y dan lecturas falsas.
        cliente = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(formatos[0], *formatos.drop(1).toIntArray())
                // Las cajas de lo que ve sin poder leer: la base de «ya lo estoy viendo» y de la guía.
                .enableAllPotentialBarcodes()
                .build(),
        )
    }

    /** El NV21 de la región: se reusa mientras no cambie su tamaño (casi nunca: solo si cambia la franja). */
    private var nv21 = ByteArray(0)
    private var anchoNv = 0
    private var altoNv = 0
    private val punto = FloatArray(2)

    override fun leer(cuadro: CuadroY): ResultadoMotor {
        // NV21 submuestrea el color 2×2: la región va en pares.
        val r = cuadro.region
        val izquierda = (r.izquierda + 1) and 1.inv()
        val arriba = (r.arriba + 1) and 1.inv()
        val ancho = (r.derecha - izquierda) and 1.inv()
        val alto = (r.abajo - arriba) and 1.inv()
        // ML Kit no acepta imágenes de menos de 32 px por lado.
        if (ancho < MINIMO || alto < MINIMO) return ResultadoMotor.VACIO
        preparar(ancho, alto)
        copiarPlanoY(cuadro.y, cuadro.pasoFila, izquierda, arriba, ancho, alto, nv21)
        val imagen = InputImage.fromByteArray(nv21, ancho, alto, cuadro.rotacion, InputImage.IMAGE_FORMAT_NV21)
        val inicio = SystemClock.elapsedRealtimeNanos()
        val codigos = try {
            // Ya estamos en el hilo de análisis: esperar aquí evita otro salto de hilo por cuadro.
            Tasks.await(cliente.process(imagen))
        } catch (e: ExecutionException) {
            Log.w(ETIQUETA, "ML Kit falló en un cuadro", e.cause ?: e)
            return resultadoVacio(SystemClock.elapsedRealtimeNanos() - inicio)
        }
        val nanos = SystemClock.elapsedRealtimeNanos() - inicio
        if (codigos.isEmpty()) return resultadoVacio(nanos)

        var lecturas: ArrayList<Lectura>? = null
        var potenciales: ArrayList<FloatArray>? = null
        for (codigo in codigos) {
            val esquinas = esquinasEnBuffer(codigo, ancho, alto, cuadro.rotacion, izquierda, arriba) ?: continue
            val texto = codigo.rawValue
            // Los potenciales llegan con rawValue "" (el javadoc dice null): cuentan los dos.
            if (texto.isNullOrEmpty()) {
                (potenciales ?: ArrayList<FloatArray>(2).also { potenciales = it }).add(esquinas)
            } else {
                (lecturas ?: ArrayList<Lectura>(2).also { lecturas = it })
                    .add(Lectura(texto, deFormatoMlKit(codigo.format), esquinas))
            }
        }
        return resultado(lecturas, potenciales, nanos)
    }

    /** La primera lectura carga el modelo (~100 ms en el emulador): mejor con un cuadro gris al abrir. */
    override fun calentar() {
        val ancho = 640
        val alto = 360
        preparar(ancho, alto)
        java.util.Arrays.fill(nv21, 0, ancho * alto, 128.toByte())
        try {
            Tasks.await(cliente.process(InputImage.fromByteArray(nv21, ancho, alto, 0, InputImage.IMAGE_FORMAT_NV21)))
        } catch (e: ExecutionException) {
            Log.w(ETIQUETA, "No se pudo calentar ML Kit", e.cause ?: e)
        }
    }

    override fun close() {
        try {
            cliente.close()
        } catch (e: Exception) {
            // Cerrar dos veces o durante una lectura lanza «This detector is already closed!»: no importa.
            Log.d(ETIQUETA, "ML Kit ya estaba cerrado: ${e.message}")
        }
    }

    private fun preparar(ancho: Int, alto: Int) {
        if (ancho == anchoNv && alto == altoNv) return
        val tamano = ancho * alto * 3 / 2
        // Del tamaño exacto: no está claro que ML Kit acepte un arreglo más grande que la imagen.
        nv21 = ByteArray(tamano)
        java.util.Arrays.fill(nv21, ancho * alto, tamano, 128.toByte())
        anchoNv = ancho
        altoNv = alto
    }

    /** Las esquinas de ML Kit (en el recorte derecho) a píxeles del buffer sin rotar. */
    private fun esquinasEnBuffer(codigo: Barcode, ancho: Int, alto: Int, rotacion: Int, dx: Int, dy: Int): FloatArray? {
        val puntos: Array<Point>? = codigo.cornerPoints
        val esquinas = FloatArray(8)
        if (puntos != null && puntos.size == 4) {
            for (i in 0 until 4) {
                Rotacion.aBuffer(puntos[i].x.toFloat(), puntos[i].y.toFloat(), ancho, alto, rotacion, punto)
                esquinas[2 * i] = punto[0] + dx
                esquinas[2 * i + 1] = punto[1] + dy
            }
            return esquinas
        }
        val caja: Rect = codigo.boundingBox ?: return null
        val xs = intArrayOf(caja.left, caja.right, caja.right, caja.left)
        val ys = intArrayOf(caja.top, caja.top, caja.bottom, caja.bottom)
        for (i in 0 until 4) {
            Rotacion.aBuffer(xs[i].toFloat(), ys[i].toFloat(), ancho, alto, rotacion, punto)
            esquinas[2 * i] = punto[0] + dx
            esquinas[2 * i + 1] = punto[1] + dy
        }
        return esquinas
    }

    private companion object {
        const val ETIQUETA = "MotorMlKit"
        const val MINIMO = 32
    }
}

/** La simbología en las constantes de ML Kit; null si ML Kit no la lee. */
fun aFormatoMlKit(simbologia: Simbologia): Int? = when (simbologia) {
    Simbologia.EAN_13 -> Barcode.FORMAT_EAN_13
    Simbologia.EAN_8 -> Barcode.FORMAT_EAN_8
    Simbologia.UPC_A -> Barcode.FORMAT_UPC_A
    Simbologia.UPC_E -> Barcode.FORMAT_UPC_E
    Simbologia.ITF -> Barcode.FORMAT_ITF
    Simbologia.CODE_128 -> Barcode.FORMAT_CODE_128
    Simbologia.CODE_39 -> Barcode.FORMAT_CODE_39
    Simbologia.CODE_93 -> Barcode.FORMAT_CODE_93
    Simbologia.CODABAR -> Barcode.FORMAT_CODABAR
    Simbologia.QR -> Barcode.FORMAT_QR_CODE
    Simbologia.DATA_MATRIX -> Barcode.FORMAT_DATA_MATRIX
    Simbologia.PDF_417 -> Barcode.FORMAT_PDF417
    Simbologia.AZTEC -> Barcode.FORMAT_AZTEC
    Simbologia.OTRA -> null
}

/** Lo que ML Kit dice que leyó, como simbología del escáner. */
fun deFormatoMlKit(formato: Int): Simbologia = when (formato) {
    Barcode.FORMAT_EAN_13 -> Simbologia.EAN_13
    Barcode.FORMAT_EAN_8 -> Simbologia.EAN_8
    Barcode.FORMAT_UPC_A -> Simbologia.UPC_A
    Barcode.FORMAT_UPC_E -> Simbologia.UPC_E
    Barcode.FORMAT_ITF -> Simbologia.ITF
    Barcode.FORMAT_CODE_128 -> Simbologia.CODE_128
    Barcode.FORMAT_CODE_39 -> Simbologia.CODE_39
    Barcode.FORMAT_CODE_93 -> Simbologia.CODE_93
    Barcode.FORMAT_CODABAR -> Simbologia.CODABAR
    Barcode.FORMAT_QR_CODE -> Simbologia.QR
    Barcode.FORMAT_DATA_MATRIX -> Simbologia.DATA_MATRIX
    Barcode.FORMAT_PDF417 -> Simbologia.PDF_417
    Barcode.FORMAT_AZTEC -> Simbologia.AZTEC
    else -> Simbologia.OTRA
}
