package mx.papeleriapos.escaner.ui

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mx.papeleriapos.escaner.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Los sonidos del escáner: que los WAV sean lo que dice la especificación y que SoundPool los cargue y los toque. */
@RunWith(AndroidJUnit4::class)
class RetroalimentacionTest {
    private val contexto: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private class Wav(val muestreo: Int, val canales: Int, val bits: Int, val muestras: ShortArray) {
        val ms get() = muestras.size * 1000.0 / muestreo
    }

    private fun leerWav(id: Int): Wav {
        val bytes = contexto.resources.openRawResource(id).use { it.readBytes() }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        var i = 12
        var muestreo = 0; var canales = 0; var bits = 0
        while (i + 8 <= bytes.size) {
            val id4 = String(bytes, i, 4)
            val largo = b.getInt(i + 4)
            if (id4 == "fmt ") {
                canales = b.getShort(i + 10).toInt()
                muestreo = b.getInt(i + 12)
                bits = b.getShort(i + 22).toInt()
            } else if (id4 == "data") {
                val muestras = ShortArray(largo / 2) { b.getShort(i + 8 + it * 2) }
                return Wav(muestreo, canales, bits, muestras)
            }
            i += 8 + largo
        }
        error("WAV sin datos")
    }

    /** Frecuencia aproximada por cruces por cero (sirve para un tono puro). */
    private fun frecuencia(w: Wav, desde: Int, hasta: Int): Double {
        var cruces = 0
        for (k in desde + 1 until hasta) if ((w.muestras[k - 1] < 0) != (w.muestras[k] < 0)) cruces++
        return cruces / 2.0 / ((hasta - desde).toDouble() / w.muestreo)
    }

    @Test
    fun elBipDuraUnos100msA2_7kHzConRampas() {
        val w = leerWav(R.raw.bip)
        assertEquals(1, w.canales)
        assertEquals(16, w.bits)
        assertEquals(44_100, w.muestreo)
        assertTrue("duración ${w.ms} ms", w.ms in 80.0..120.0)
        val f = frecuencia(w, 0, w.muestras.size)
        assertTrue("frecuencia $f Hz", f in 2_600.0..2_800.0)
        // Las rampas: empieza y termina en silencio (sin chasquido) y llega cerca del máximo en medio.
        assertTrue(kotlin.math.abs(w.muestras.first().toInt()) < 200)
        assertTrue(kotlin.math.abs(w.muestras.last().toInt()) < 200)
        assertTrue(w.muestras.maxOf { it.toInt() } > 20_000)
    }

    @Test
    fun elErrorSonDosTonosGravesDeUnos120ms() {
        val w = leerWav(R.raw.error)
        assertEquals(44_100, w.muestreo)
        // Busca los tramos con sonido (silencio de ≥10 ms entre ellos).
        val tramos = mutableListOf<IntRange>()
        var inicio = -1; var ultimoSonido = -1
        val hueco = w.muestreo / 100
        for (k in w.muestras.indices) {
            if (kotlin.math.abs(w.muestras[k].toInt()) > 300) {
                if (inicio < 0) inicio = k
                else if (k - ultimoSonido > hueco) { tramos += inicio..ultimoSonido; inicio = k }
                ultimoSonido = k
            }
        }
        if (inicio >= 0) tramos += inicio..ultimoSonido
        assertEquals("tramos $tramos", 2, tramos.size)
        for (t in tramos) {
            val ms = (t.last - t.first) * 1000.0 / w.muestreo
            assertTrue("tono de $ms ms", ms in 100.0..130.0)
        }
        // Más grave que el bip, y el segundo más grave que el primero.
        val f1 = tramos[0].let { frecuenciaFundamental(w, it) }
        val f2 = tramos[1].let { frecuenciaFundamental(w, it) }
        assertTrue("f1 $f1", f1 in 300.0..800.0)
        assertTrue("f2 $f2 < f1 $f1", f2 < f1)
    }

    /** Con tercer armónico los cruces por cero no bastan: se cuenta solo el cruce hacia arriba de un nivel alto. */
    private fun frecuenciaFundamental(w: Wav, tramo: IntRange): Double {
        val nivel = w.muestras.slice(tramo).maxOf { it.toInt() } / 2
        var subidas = 0
        for (k in tramo.first + 1..tramo.last) if (w.muestras[k - 1] < nivel && w.muestras[k] >= nivel) subidas++
        return subidas / ((tramo.last - tramo.first).toDouble() / w.muestreo)
    }

    @Test
    fun soundPoolCargaLosDosSonidosYLosToca() {
        val instrumento = InstrumentationRegistry.getInstrumentation()
        var retro: Retroalimentacion? = null
        // En el hilo principal, como en la pantalla: ahí llega el aviso de carga.
        instrumento.runOnMainSync { retro = Retroalimentacion(contexto).apply { prepararSonidos() } }
        val r = retro!!
        try {
            val limite = SystemClock.uptimeMillis() + 5_000
            while (!r.listos && SystemClock.uptimeMillis() < limite) SystemClock.sleep(20)
            assertTrue("los sonidos no cargaron en 5 s", r.listos)
            assertTrue("no sonó el bip", r.bip())
            assertTrue("no sonó el error", r.error())
        } finally {
            instrumento.runOnMainSync { r.close() }
        }
    }
}
