package mx.papeleriapos.android.medicion

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.papeleriapos.escaner.Diagnostico
import java.io.File

/**
 * Las reglas de [MonitorDeSesion], sin Android para probarlas: quién es el dueño, de qué sesión y cuántas pausas hay.
 * - El dueño es el último que lo inició; [detener] con otro token no hace nada (el `onDispose` tardío de una pantalla
 *   que ya se fue no apaga el monitor que tomó la nueva).
 * - Cada [pausar] (una prueba de estrés: un solo muestreador a la vez) necesita su [reanudar].
 */
class ReglasMonitor {
    private var siguienteToken = 1L
    private var dueno = 0L
    private var pausas = 0

    /** La sesión que se mide (su inicio); 0 = ninguna. */
    var sesion = 0L
        private set

    /** Si el monitor tiene que estar muestreando ahora. */
    val debeCorrer: Boolean get() = dueno != 0L && pausas == 0 && sesion != 0L

    /** Quien llama se vuelve el dueño; devuelve su token. */
    fun iniciar(inicioSesion: Long): Long {
        sesion = inicioSesion
        dueno = siguienteToken++
        return dueno
    }

    /** Suelta si [token] es el del dueño actual; devuelve si lo era. */
    fun detener(token: Long): Boolean {
        if (token == 0L || token != dueno) return false
        dueno = 0L
        return true
    }

    fun pausar() {
        pausas++
    }

    fun reanudar() {
        if (pausas > 0) pausas--
    }

    /** La sesión terminó. */
    fun terminar() {
        sesion = 0L
    }
}

/**
 * El monitor de recursos de la sesión de prueba: corre mientras hay sesión y escribe recursos.csv junto a sus
 * intentos, para exportarlo aparte. Es uno por proceso (la sesión también lo es) y sobrevive a que se recree la
 * pantalla; si Android cierra la app, al volver sigue agregando al mismo archivo.
 *
 * Tiene dueño ([iniciar] devuelve un token y solo [detener] con ese token lo para), se pausa mientras corre una prueba de
 * estrés (un solo muestreador a la vez: [pausar] y [reanudar]) y no guarda muestras en memoria: van al archivo y solo
 * queda la última ([ultima]) para verla en vivo. Abrir el archivo se hace en el hilo del monitor.
 */
object MonitorDeSesion {
    private val reglas = ReglasMonitor()
    private var monitor: MonitorRecursos? = null
    private var sesionDelMonitor = 0L
    private var app: Context? = null
    @Volatile private var fuente: () -> Diagnostico? = { null }
    @Volatile private var fase: () -> String = { "" }
    private val _ultima = MutableStateFlow<MuestraRecursos?>(null)

    /** La muestra más reciente de la sesión (null si no está corriendo). */
    val ultima: StateFlow<MuestraRecursos?> = _ultima.asStateFlow()

    fun archivo(contexto: Context): File = File(File(contexto.filesDir, "sesion"), "recursos.csv")

    private fun marca(contexto: Context): File = File(File(contexto.filesDir, "sesion"), "recursos.inicio")

    /**
     * Empieza a muestrear para la sesión que empezó en [inicioSesion] y devuelve el token del nuevo dueño. Si ya corre
     * para esa sesión solo cambia de dueño, de [fuente] y de [fase]. Si el archivo es de otra sesión empieza uno nuevo.
     */
    @Synchronized
    fun iniciar(contexto: Context, inicioSesion: Long, fuente: () -> Diagnostico?, fase: () -> String): Long {
        this.fuente = fuente
        this.fase = fase
        app = contexto.applicationContext
        val token = reglas.iniciar(inicioSesion)
        reconciliar()
        return token
    }

    /** Lo para si [token] es el del dueño actual; si otro ya lo tomó, no hace nada. */
    @Synchronized
    fun detener(token: Long) {
        if (reglas.detener(token)) reconciliar()
    }

    /** Mientras corre una prueba de estrés: deja de muestrear (cada [pausar] con su [reanudar]). */
    @Synchronized
    fun pausar() {
        reglas.pausar()
        reconciliar()
    }

    @Synchronized
    fun reanudar() {
        reglas.reanudar()
        reconciliar()
    }

    /** Terminó la sesión que empezó en [inicioSesion]: deja de medirla (si ya se mide otra, no la toca). */
    @Synchronized
    fun terminar(inicioSesion: Long) {
        if (reglas.sesion != inicioSesion) return
        reglas.terminar()
        reconciliar()
    }

    /**
     * Borra recursos.csv si es de la sesión [inicioSesion] (ya se exportó o se descartó); si ya es de una sesión nueva, lo
     * deja. Fuera del hilo principal.
     */
    fun borrar(contexto: Context, inicioSesion: Long) {
        val m = marca(contexto)
        synchronized(MonitorRecursos.BLOQUEO_ARCHIVO) {
            val deOtra = try { m.exists() && m.readText().trim() != inicioSesion.toString() } catch (e: Exception) { false }
            if (deOtra) return
            archivo(contexto).delete()
            m.delete()
        }
    }

    /** El contenido de recursos.csv completo, sin una fila a medias; null si no hay. Fuera del hilo principal. */
    fun leer(contexto: Context): String? {
        val a = archivo(contexto)
        if (!a.exists()) return null
        return synchronized(MonitorRecursos.BLOQUEO_ARCHIVO) { a.readText() }
    }

    /** Que el monitor corra (o no) según [reglas], y con la sesión que toca. */
    private fun reconciliar() {
        val m = monitor
        when {
            !reglas.debeCorrer -> if (m != null) detenerMonitor()
            m == null -> arrancar()
            sesionDelMonitor != reglas.sesion -> {
                detenerMonitor()
                arrancar()
            }
        }
    }

    private fun arrancar() {
        val a = app ?: return
        val inicio = reglas.sesion
        sesionDelMonitor = inicio
        _ultima.value = null
        monitor = MonitorRecursos(
            a,
            fuente = { fuente() },
            fase = { fase() },
            archivo = { archivo(a) },
            maximoEnMemoria = 0,
            alMuestrear = { _ultima.value = it },
            origenHoraMs = inicio,
            alPreparar = { prepararArchivo(a, inicio) },
        ).also { it.iniciar() }
    }

    private fun detenerMonitor() {
        monitor?.detener()
        monitor = null
        sesionDelMonitor = 0L
        _ultima.value = null
    }

    /** En el hilo del monitor: si recursos.csv es de otra sesión, empieza uno nuevo. */
    private fun prepararArchivo(contexto: Context, inicioSesion: Long) = synchronized(MonitorRecursos.BLOQUEO_ARCHIVO) {
        val a = archivo(contexto)
        val m = marca(contexto)
        val deEstaSesion = try { m.exists() && m.readText().trim() == inicioSesion.toString() } catch (e: Exception) { false }
        if (!deEstaSesion) {
            a.parentFile?.mkdirs()
            a.delete()
            try { m.writeText(inicioSesion.toString()) } catch (_: Exception) { }
        }
    }
}
