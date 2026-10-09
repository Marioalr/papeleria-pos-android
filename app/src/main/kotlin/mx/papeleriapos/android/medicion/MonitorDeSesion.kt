package mx.papeleriapos.android.medicion

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.papeleriapos.escaner.Diagnostico
import java.io.File

/**
 * El monitor de recursos de la sesión de prueba: corre mientras hay sesión y escribe recursos.csv junto a sus
 * intentos, para exportarlo aparte. Es uno por proceso (la sesión también lo es) y sobrevive a que se recree la
 * pantalla; si Android cierra la app, al volver sigue agregando al mismo archivo.
 */
object MonitorDeSesion {
    private var monitor: MonitorRecursos? = null
    private var sesionActual = 0L
    private val _ultima = MutableStateFlow<MuestraRecursos?>(null)

    /** La muestra más reciente de la sesión (null si no está corriendo). */
    val ultima: StateFlow<MuestraRecursos?> = _ultima.asStateFlow()

    fun archivo(contexto: Context): File = File(File(contexto.filesDir, "sesion"), "recursos.csv")

    private fun marca(contexto: Context): File = File(File(contexto.filesDir, "sesion"), "recursos.inicio")

    /**
     * Empieza a muestrear para la sesión que empezó en [inicioSesion]. Si el archivo es de otra sesión, empieza uno
     * nuevo. Se llama en el hilo principal.
     */
    @Synchronized
    fun iniciar(contexto: Context, inicioSesion: Long, fuente: () -> Diagnostico?, fase: () -> String) {
        if (monitor != null && sesionActual == inicioSesion) return
        detener()
        val app = contexto.applicationContext
        val a = archivo(app)
        val m = marca(app)
        val deEstaSesion = try { m.exists() && m.readText().trim() == inicioSesion.toString() } catch (e: Exception) { false }
        if (!deEstaSesion) {
            a.parentFile?.mkdirs()
            a.delete()
            try { m.writeText(inicioSesion.toString()) } catch (_: Exception) { }
        }
        sesionActual = inicioSesion
        _ultima.value = null
        monitor = MonitorRecursos(app, fuente, fase, a, alMuestrear = { _ultima.value = it }, origenHoraMs = inicioSesion)
            .also { it.iniciar() }
    }

    @Synchronized
    fun detener() {
        monitor?.detener()
        monitor = null
        _ultima.value = null
    }

    /** Borra el archivo (al terminar la sesión). */
    @Synchronized
    fun borrar(contexto: Context) {
        detener()
        archivo(contexto).delete()
        marca(contexto).delete()
        sesionActual = 0L
    }

    /** El contenido de recursos.csv completo, sin una fila a medias; null si no hay. Fuera del hilo principal. */
    fun leer(contexto: Context): String? {
        val a = archivo(contexto)
        if (!a.exists()) return null
        return synchronized(MonitorRecursos.BLOQUEO_ARCHIVO) { a.readText() }
    }
}
