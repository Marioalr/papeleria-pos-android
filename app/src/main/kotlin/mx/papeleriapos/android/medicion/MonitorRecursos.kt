package mx.papeleriapos.android.medicion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.ExecutorCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.papeleriapos.escaner.Diagnostico
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.RandomAccessFile

/**
 * Muestrea los recursos del proceso y del celular una vez por segundo (docs/escaner.md, «Consumo»): CPU, memoria,
 * hilos, descriptores, batería, temperatura y cuadros por segundo. Corre en un hilo propio de baja prioridad, nunca en
 * el de análisis, y mide lo que le cuesta a él mismo (`costo_muestra_us`, lo que cuesta leer).
 *
 * Nada en el hilo principal: [iniciar] solo crea el hilo; abrir el archivo, el estado térmico (binder) y la batería se
 * preparan en el hilo del monitor, y [detener] suelta todo ahí mismo.
 *
 * Poca basura por muestra: `/proc/self/status` se relee en un buffer propio, la batería llega por un receptor (no se
 * pide el intent pegajoso cada segundo), los descriptores se cuentan cada [CADA_FDS] muestras, la fila se arma en un
 * StringBuilder reutilizado y, en la sesión, no se guardan muestras en memoria ([maximoEnMemoria] = 0: ya van al archivo).
 *
 * No usa `Debug.getMemoryInfo` ni `ActivityManager.getProcessMemoryInfo`: el primero recorre smaps completo y el
 * segundo repite el mismo dato durante 5 minutos.
 *
 * @param fuente el diagnóstico del escáner que esté abierto (cuadros producidos y analizados, estado de la cámara).
 * @param fase qué se está haciendo (escaneando, en pausa, segundo plano, la prueba de estrés…).
 * @param archivo si no es null, dice en qué archivo se agrega cada fila (sobrevive a que Android cierre la app); se
 *   pide en el hilo del monitor (`filesDir` toca el disco).
 */
class MonitorRecursos(
    contexto: Context,
    private val fuente: () -> Diagnostico? = { null },
    private val fase: () -> String = { "" },
    private val archivo: (() -> File)? = null,
    private val periodoMs: Long = 1_000,
    /** Cuántas muestras se guardan en memoria para [muestras]; 0 = ninguna (solo el archivo y [ultima]). */
    private val maximoEnMemoria: Int = 0,
    /** Se llama en el hilo del monitor con cada muestra. */
    private val alMuestrear: (MuestraRecursos) -> Unit = {},
    /**
     * Hora (`System.currentTimeMillis`) desde la que se cuenta `t_ms`; 0 = desde que empezó este monitor. La sesión pasa
     * su inicio, así `t_ms` sigue corriendo aunque el monitor se reinicie (se recreó la pantalla).
     */
    private val origenHoraMs: Long = 0,
    /** Se llama en el hilo del monitor antes de abrir [archivo] (p. ej. empezar uno nuevo si era de otra sesión). */
    private val alPreparar: () -> Unit = {},
) {
    private val app = contexto.applicationContext
    private val bateria = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
    private val energia = app.getSystemService(Context.POWER_SERVICE) as PowerManager?
    private val calculadora = CalculadoraRecursos()
    private val horaIso = HoraIso()
    private val escribirHora: (StringBuilder, Long) -> Unit = horaIso::agregar
    private val fila = StringBuilder(512)
    private val fuenteHeadroom = FuenteHeadroom {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) energia?.getThermalHeadroom(0) ?: Float.NaN else Float.NaN
    }

    @Volatile private var hilo: HandlerThread? = null
    @Volatile private var manejador: Handler? = null
    // Solo en el hilo del monitor.
    private var escritor: BufferedWriter? = null
    private var escuchaTermica: Any? = null
    private var status: RandomAccessFile? = null
    private val bufferStatus = ByteArray(4096)
    private var siguiente = 0L
    private var muestrasHechas = 0L
    private var fds = -1
    @Volatile private var termico = -1

    // Lo último que avisó la batería (receptor en el hilo del monitor).
    @Volatile private var voltajeMv = -1
    @Volatile private var tempBatDc = Int.MIN_VALUE
    @Volatile private var enchufado = false
    private var receptorBateria: BroadcastReceiver? = null

    private val lista = ArrayDeque<MuestraRecursos>()
    private val _ultima = MutableStateFlow<MuestraRecursos?>(null)

    /** La muestra más reciente, para mostrarla en pantalla. */
    val ultima: StateFlow<MuestraRecursos?> = _ultima.asStateFlow()

    val corriendo: Boolean get() = hilo != null

    private val tic = object : Runnable {
        override fun run() {
            val h = manejador ?: return
            try {
                muestrear()
            } catch (e: Exception) {
                Log.w(ETIQUETA, "Falló una muestra", e)
            }
            siguiente += periodoMs
            val ahora = SystemClock.uptimeMillis()
            // Si el hilo se atrasó (el celular durmió), no se ponen al corriente las muestras perdidas.
            if (siguiente <= ahora) siguiente = ahora + periodoMs
            h.postAtTime(this, siguiente)
        }
    }

    /** Arranca el hilo; lo demás se prepara en él. Se puede llamar en el hilo principal. */
    @Synchronized
    fun iniciar() {
        if (hilo != null) return
        val h = HandlerThread("monitor-recursos", Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
        val m = Handler(h.looper)
        hilo = h
        manejador = m
        m.post {
            try {
                alPreparar()
            } catch (e: Exception) {
                Log.w(ETIQUETA, "No se pudo preparar el archivo", e)
            }
            abrirArchivo()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) escucharTermico(m)
            escucharBateria(m)
            siguiente = SystemClock.uptimeMillis()
            tic.run()
        }
    }

    @Synchronized
    fun detener() {
        val h = hilo ?: return
        val m = manejador
        hilo = null
        manejador = null
        if (m != null) {
            m.removeCallbacksAndMessages(null)
            m.post { soltar() }
        }
        h.quitSafely()
    }

    /** Copia de las muestras en memoria (solo si se pidió guardarlas con [maximoEnMemoria]). */
    fun muestras(): List<MuestraRecursos> = synchronized(lista) { lista.toList() }

    private fun muestrear() {
        val c0 = Debug.threadCpuTimeNanos()
        val ahora = SystemClock.elapsedRealtime()
        val headroom = CacheHeadroom.delProceso.leer(ahora, fuenteHeadroom)
        // Los descriptores cambian poco y listarlos arma un arreglo con todos: cada CADA_FDS muestras.
        if (muestrasHechas++ % CADA_FDS == 0L) fds = contarFds()
        val d = try { fuente() } catch (e: RuntimeException) { null }
        val rt = Runtime.getRuntime()
        val lectura = LecturaCruda(
            paredMs = ahora,
            cpuMs = Process.getElapsedCpuTime(),
            heapJavaBytes = rt.totalMemory() - rt.freeMemory(),
            heapNativoBytes = Debug.getNativeHeapAllocatedSize(),
            proceso = leerStatus(),
            fds = fds,
            corrienteCruda = propiedad(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            cargaUah = propiedad(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            voltajeMv = voltajeMv,
            bateriaPct = propiedad(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            tempBatDc = tempBatDc,
            enchufado = enchufado,
            termico = termico,
            headroom = headroom,
            // Con escáner, 0 es un dato (todavía no llega nada); sin escáner, −1.
            cuadrosProducidos = d?.cuadrosProducidos ?: -1,
            cuadrosAnalizados = d?.cuadrosAnalizados ?: -1,
            estadoCamara = d?.estadoCamara ?: "",
            horaMs = System.currentTimeMillis(),
        )
        val faseActual = try { fase() } catch (e: RuntimeException) { "" }
        val costo = (Debug.threadCpuTimeNanos() - c0) / 1000
        val muestra = calculadora.calcular(lectura, faseActual, origenHoraMs, costo)
        if (maximoEnMemoria > 0) {
            synchronized(lista) {
                lista.addLast(muestra)
                while (lista.size > maximoEnMemoria) lista.removeFirst()
            }
        }
        _ultima.value = muestra
        escribir(muestra)
        alMuestrear(muestra)
    }

    private fun propiedad(id: Int): Int = try {
        bateria?.getIntProperty(id) ?: Int.MIN_VALUE
    } catch (e: RuntimeException) {
        Int.MIN_VALUE
    }

    /** `/proc/self/status` desde el principio, en el buffer propio (procfs lo vuelve a generar al regresar a 0). */
    private fun leerStatus(): EstadoProceso {
        return try {
            val f = status ?: RandomAccessFile("/proc/self/status", "r").also { status = it }
            f.seek(0)
            var n = 0
            while (n < bufferStatus.size) {
                val leidos = f.read(bufferStatus, n, bufferStatus.size - n)
                if (leidos <= 0) break
                n += leidos
            }
            EstadoProceso.leer(bufferStatus, n)
        } catch (e: IOException) {
            EstadoProceso()
        }
    }

    private fun abrirArchivo() {
        val a = archivo?.invoke() ?: return
        try {
            a.parentFile?.mkdirs()
            val nuevo = !a.exists() || a.length() == 0L
            escritor = BufferedWriter(OutputStreamWriter(FileOutputStream(a, true), Charsets.UTF_8)).also {
                if (nuevo) {
                    it.write(CsvRecursos.encabezado())
                    it.flush()
                }
            }
        } catch (e: IOException) {
            Log.e(ETIQUETA, "No se pudo abrir ${a.name}", e)
            escritor = null
        }
    }

    private fun escribir(m: MuestraRecursos) {
        val w = escritor ?: return
        fila.setLength(0)
        CsvRecursos.agregar(m, fila, escribirHora)
        try {
            synchronized(BLOQUEO_ARCHIVO) {
                w.append(fila)
                w.flush()
            }
        } catch (e: IOException) {
            Log.w(ETIQUETA, "No se pudo escribir la muestra", e)
        }
    }

    /** En el hilo del monitor, al detenerse: archivo, estado térmico, batería y `/proc/self/status`. */
    private fun soltar() {
        try {
            escritor?.close()
        } catch (_: IOException) {
        }
        escritor = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) dejarDeEscucharTermico()
        receptorBateria?.let { r -> try { app.unregisterReceiver(r) } catch (_: IllegalArgumentException) { } }
        receptorBateria = null
        try {
            status?.close()
        } catch (_: IOException) {
        }
        status = null
    }

    /** El voltaje, la temperatura y si está conectado llegan cuando cambian (el registro devuelve el último). */
    private fun escucharBateria(m: Handler) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) tomarBateria(intent)
            }
        }
        try {
            val pegajoso = ContextCompat.registerReceiver(
                app, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), null, m, ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receptorBateria = r
            pegajoso?.let(::tomarBateria)
        } catch (e: RuntimeException) {
            Log.w(ETIQUETA, "Sin avisos de la batería", e)
        }
    }

    private fun tomarBateria(i: Intent) {
        voltajeMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        tempBatDc = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        enchufado = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun escucharTermico(m: Handler) {
        val pm = energia ?: return
        termico = try { pm.currentThermalStatus } catch (e: RuntimeException) { -1 }
        // En AOSP el listener avisa el estado actual al registrarse.
        val escucha = PowerManager.OnThermalStatusChangedListener { termico = it }
        try {
            pm.addThermalStatusListener(ExecutorCompat.create(m), escucha)
            escuchaTermica = escucha
        } catch (e: RuntimeException) {
            Log.w(ETIQUETA, "Sin aviso de estado térmico", e)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun dejarDeEscucharTermico() {
        val escucha = escuchaTermica as? PowerManager.OnThermalStatusChangedListener ?: return
        try {
            energia?.removeThermalStatusListener(escucha)
        } catch (_: RuntimeException) {
        }
        escuchaTermica = null
    }

    companion object {
        private const val ETIQUETA = "MonitorRecursos"

        /** Cada cuántas muestras se cuentan los descriptores abiertos. */
        private const val CADA_FDS = 5L

        /** Para que copiar el archivo no lea una fila a medias mientras se escribe. */
        val BLOQUEO_ARCHIVO = Any()

        /** VmRSS, RssAnon e hilos del proceso; lo que falte queda en −1. */
        fun estadoProceso(): EstadoProceso = try {
            EstadoProceso.leer(File("/proc/self/status").readText())
        } catch (e: Exception) {
            EstadoProceso()
        }

        /** Descriptores abiertos: si crecen ciclo tras ciclo, algo no se cierra. */
        fun contarFds(): Int = try {
            File("/proc/self/fd").list()?.size ?: -1
        } catch (e: Exception) {
            -1
        }
    }
}
