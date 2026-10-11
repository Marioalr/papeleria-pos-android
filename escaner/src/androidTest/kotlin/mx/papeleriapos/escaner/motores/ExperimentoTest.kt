package mx.papeleriapos.escaner.motores

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import mx.papeleriapos.escaner.nucleo.Barras
import mx.papeleriapos.escaner.nucleo.Escena
import mx.papeleriapos.escaner.nucleo.Gtin
import mx.papeleriapos.escaner.nucleo.Lienzo
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Test
import org.junit.runner.RunWith

/** Temporal: de dónde sale la latencia de ML Kit. */
@RunWith(AndroidJUnit4::class)
class ExperimentoTest {
    @Test
    fun latenciaSegunTamanoYRotacion() {
        val cliente = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_ITF, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE,
            ).enableAllPotentialBarcodes().build(),
        )
        val modulos = Barras.ean13("7509993456786")
        fun nv21(w: Int, h: Int, rot: Int): ByteArray {
            // Código horizontal en el derecho; si rot=90 el buffer es h×w girado.
            val dw = if (rot % 180 == 0) w else h
            val dh = if (rot % 180 == 0) h else w
            val l = Lienzo.dibujar(modulos, 9, dw, dh, Escena(anchoRelativo = 339f / dw))
            val out = ByteArray(w * h * 3 / 2) { 128.toByte() }
            for (y in 0 until h) for (x in 0 until w) {
                val (ux, uy) = if (rot == 90) (h - 1 - y) to x else x to y
                out[y * w + x] = l.pixeles[uy * dw + ux]
            }
            return out
        }
        repeat(10) { Tasks.await(cliente.process(InputImage.fromByteArray(nv21(640, 360, 0), 640, 360, 0, InputImage.IMAGE_FORMAT_NV21))) }
        for ((w, h, rot) in listOf(
            Triple(462, 530, 90), Triple(464, 528, 90), Triple(448, 512, 90), Triple(480, 544, 90), Triple(512, 512, 90),
            Triple(530, 462, 0), Triple(528, 464, 0), Triple(1280, 576, 90), Triple(1280, 576, 0), Triple(1178, 260, 0),
            Triple(1168, 256, 0), Triple(1280, 720, 0), Triple(640, 360, 0),
        )) {
            val datos = nv21(w, h, rot)
            val img = { InputImage.fromByteArray(datos, w, h, rot, InputImage.IMAGE_FORMAT_NV21) }
            repeat(3) { Tasks.await(cliente.process(img())) }
            val ts = DoubleArray(15) {
                val t0 = SystemClock.elapsedRealtimeNanos()
                val r = Tasks.await(cliente.process(img()))
                check(r.any { it.rawValue == "7509993456786" }) { "no leyó ${w}x$h rot $rot: ${r.map { it.rawValue }}" }
                (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
            }.sorted()
            Log.i("Experimento", "ML Kit ${w}x$h rot=$rot mediana %.1f ms min %.1f".format(ts[7], ts[0]))
        }
        cliente.close()
    }

    @Test
    fun itfConBarrasAnchasDe2y3() {
        val cliente = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ITF).build())
        val texto = Gtin.completar("1750999000001")
        for (ancho in listOf(2, 3)) for (modulo in listOf(2f, 3f, 4f)) {
            val m = Barras.itf(texto, ancho)
            val w = 1280; val h = 720
            val l = Lienzo.dibujar(m, 10, w, h, Escena(anchoRelativo = (m.size + 20) * modulo / w))
            val out = ByteArray(w * h * 3 / 2) { 128.toByte() }
            System.arraycopy(l.pixeles, 0, out, 0, w * h)
            val r = Tasks.await(cliente.process(InputImage.fromByteArray(out, w, h, 0, InputImage.IMAGE_FORMAT_NV21)))
            Log.i("Experimento", "ITF ancho=$ancho módulo=$modulo: ${r.map { it.rawValue }}")
        }
        cliente.close()
    }

    @Suppress("unused")
    private val s = Simbologia.ITF
}
