package mx.papeleriapos.escaner.camara

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.Motor
import org.junit.Test
import org.junit.runner.RunWith
import zxingcpp.BarcodeReader
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Diagnóstico con la cámara del emulador: guarda un cuadro real (plano Y de la región visible, en PGM) y prueba
 * zxing-cpp con varias opciones sobre el mismo cuadro. No aserta: deja en el registro qué lee cada opción y el archivo
 * en files/diagnostico para revisarlo (adb pull).
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticoCamaraTest {
    private class Dueno : LifecycleOwner {
        val registro = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registro
    }

    @Test
    fun guardarCuadroYProbarZxing() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        inst.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.CAMERA)
        val hilo = HandlerThread("visor").also { it.start() }
        val listo = CountDownLatch(1)
        var cuenta = 0
        val carpeta = File(ctx.getExternalFilesDir(null), "diagnostico").also { it.mkdirs() }
        lateinit var dueno: Dueno
        lateinit var controlador: ControladorCamara
        inst.runOnMainSync {
            dueno = Dueno().also { it.registro.currentState = Lifecycle.State.RESUMED }
            controlador = ControladorCamara(ctx, ConfigEscaner(motor = Motor.ZXING_CPP, segundosParaPausar = 0))
            controlador.analizador.observadorCuadro = { imagen, t, franja ->
                if (++cuenta == 40) {
                    val plano = imagen.planes[0]
                    val v = t.visible
                    // El plano Y de lo visible, tal cual (sin rotar), en PGM.
                    val bytes = ByteArray(v.ancho * v.alto)
                    val buf = plano.buffer.duplicate()
                    for (fila in 0 until v.alto) {
                        buf.position((v.arriba + fila) * plano.rowStride + v.izquierda)
                        buf.get(bytes, fila * v.ancho, v.ancho)
                    }
                    File(carpeta, "visible.pgm").outputStream().use {
                        it.write("P5\n${v.ancho} ${v.alto}\n255\n".toByteArray()); it.write(bytes)
                    }
                    Log.i(ETIQUETA, "Cuadro ${imagen.width}x${imagen.height} formato ${imagen.format} rowStride ${plano.rowStride} " +
                        "pixelStride ${plano.pixelStride} crop ${imagen.cropRect} rot ${imagen.imageInfo.rotationDegrees} franja $franja")
                    for ((nombre, opciones) in variantes()) {
                        val lector = BarcodeReader(opciones)
                        val original = imagen.cropRect
                        val conProxy = try {
                            imagen.setCropRect(Rect(franja.izquierda, franja.arriba, franja.derecha, franja.abajo))
                            lector.read(imagen).map { "${it.format}:${it.text}" }
                        } finally {
                            imagen.setCropRect(original)
                        }
                        val completo = lector.read(imagen).map { "${it.format}:${it.text}" }
                        // El mismo plano por la ruta del mapa de bits (como el banco de pruebas).
                        val mapa = Bitmap.createBitmap(v.ancho, v.alto, Bitmap.Config.ALPHA_8)
                        val filas = ByteArray(mapa.rowBytes * v.alto)
                        for (fila in 0 until v.alto) System.arraycopy(bytes, fila * v.ancho, filas, fila * mapa.rowBytes, v.ancho)
                        mapa.copyPixelsFromBuffer(ByteBuffer.wrap(filas))
                        val porMapa = lector.read(mapa, Rect(0, 0, v.ancho, v.alto), imagen.imageInfo.rotationDegrees).map { "${it.format}:${it.text}" }
                        Log.i(ETIQUETA, "zxing $nombre: franja=$conProxy cropRect=$completo mapa=$porMapa")
                    }
                    listo.countDown()
                }
            }
            controlador.abrir(dueno, 1080, 2400, Surface.ROTATION_0)
        }
        val atendidas = HashSet<SurfaceRequest>()
        val lectores = ArrayList<ImageReader>()
        val hasta = SystemClock.elapsedRealtime() + 30_000
        while (listo.count > 0 && SystemClock.elapsedRealtime() < hasta) {
            controlador.superficie.value?.let { s ->
                if (atendidas.add(s)) {
                    val lector = ImageReader.newInstance(s.resolution.width, s.resolution.height, ImageFormat.PRIVATE, 4)
                    lector.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(hilo.looper))
                    s.provideSurface(lector.surface, ContextCompat.getMainExecutor(ctx)) {}
                    lectores += lector
                }
            }
            SystemClock.sleep(20)
        }
        listo.await(1, TimeUnit.SECONDS)
        inst.runOnMainSync { controlador.cerrar(); dueno.registro.currentState = Lifecycle.State.DESTROYED }
        lectores.forEach { it.close() }
        hilo.quitSafely()
    }

    private fun variantes(): List<Pair<String, BarcodeReader.Options>> {
        val formatos = setOf(BarcodeReader.Format.EAN_13, BarcodeReader.Format.ITF_14, BarcodeReader.Format.CODE_128)
        return listOf(
            "rápido" to BarcodeReader.Options(formats = formatos, minLineCount = 2),
            "tryRotate" to BarcodeReader.Options(formats = formatos, minLineCount = 2, tryRotate = true),
            "tryHarder" to BarcodeReader.Options(formats = formatos, minLineCount = 2, tryHarder = true),
            "tryHarder+rotate" to BarcodeReader.Options(formats = formatos, tryHarder = true, tryRotate = true),
            "histograma" to BarcodeReader.Options(formats = formatos, binarizer = BarcodeReader.Binarizer.GLOBAL_HISTOGRAM),
            "todo" to BarcodeReader.Options(formats = emptySet(), tryHarder = true, tryRotate = true, tryDownscale = true),
        )
    }

    private companion object {
        const val ETIQUETA = "Diagnostico"
    }
}
