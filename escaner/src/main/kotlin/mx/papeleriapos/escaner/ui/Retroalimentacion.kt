package mx.papeleriapos.escaner.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import mx.papeleriapos.escaner.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * El bip, el tono de error y la vibración del escáner (docs/escaner.md, «Experiencia»).
 *
 * Los sonidos se cargan al crearla para que el primer bip salga a tiempo (≤50 ms desde la lectura): SoundPool tarda
 * en decodificar y un `play` antes de eso no suena. Hay que llamar [close] al salir de la pantalla.
 */
class Retroalimentacion(contexto: Context) : AutoCloseable {
    private val audio = contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val atributos = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** El stream cuyo volumen manda sobre el bip (el de sistema en la mayoría de los celulares). */
    val stream: Int =
        if (Build.VERSION.SDK_INT >= 26) atributos.volumeControlStream else AudioManager.STREAM_SYSTEM

    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(atributos).build()
    private val bipListo = AtomicBoolean(false)
    private val errorListo = AtomicBoolean(false)
    private val idBip: Int
    private val idError: Int

    init {
        // El listener va antes de load para no perder el aviso si la carga termina muy rápido.
        pool.setOnLoadCompleteListener { _, id, estado ->
            if (estado == 0) {
                if (id == idBip) bipListo.set(true)
                if (id == idError) errorListo.set(true)
            }
        }
        idBip = pool.load(contexto, R.raw.bip, 1)
        idError = pool.load(contexto, R.raw.error, 1)
    }

    /** Los dos sonidos ya están decodificados y listos para sonar. */
    val listos: Boolean get() = bipListo.get() && errorListo.get()

    /** Suena el bip de lectura; false si todavía no está cargado. */
    fun bip(): Boolean = bipListo.get() && pool.play(idBip, 1f, 1f, 1, 0, 1f) != 0

    /** Suena el doble tono grave de error; false si todavía no está cargado. */
    fun error(): Boolean = errorListo.get() && pool.play(idError, 1f, 1f, 1, 0, 1f) != 0

    /** El bip no se va a oír: el volumen de su stream está en cero o lo calla el modo de timbre. */
    fun sinVolumen(): Boolean =
        runCatching { audio.getStreamVolume(stream) == 0 || audio.isStreamMute(stream) }.getOrDefault(false)

    /** Respeta el ajuste «Respuesta táctil» del usuario (desde API 33 no hay forma de saltarlo). */
    fun vibrarConfirmacion(vista: View) {
        vista.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    fun vibrarRechazo(vista: View) {
        vista.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
        )
    }

    override fun close() {
        pool.release()
    }
}
