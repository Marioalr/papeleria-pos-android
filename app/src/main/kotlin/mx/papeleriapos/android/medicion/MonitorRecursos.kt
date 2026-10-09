package mx.papeleriapos.android.medicion

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.papeleriapos.escaner.Diagnostico
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Muestrea los recursos del proceso y del celular una vez por segundo (docs/escaner.md, «Consumo»): CPU, memoria,
 * hilos, descriptores, batería, temperatura y cuadros por segundo. Corre en un hilo propio de baja prioridad, nunca en
 * el de análisis, y mide lo que le cuesta a él mismo (`costo_muestra_us`).
 *
 * No usa `Debug.getMemoryInfo` ni `ActivityManager.getProcessMemoryInfo`: el primero recorre smaps completo y el
 * segundo repite el mismo dato durante 5 minutos.
 *
 * @param fuente el diagnóstico del escáner que esté abierto (cuadros producidos y analizados, estado de la cámara).
 * @param fase qué se está haciendo (escaneando, en pausa, segundo plano, la prueba de estrés…).
 * @param archivo si no es null, cada fila se agrega ahí (sobrevive a que Android cierre la app).
 */
class MonitorRecursos(
    contexto: Context,
    private val fuente: () -> Diagnostico? = { null },
    private val fase: () -> String = { "" },
    private val archivo: File? = null,
    private val periodoMs: Long = 1_000,
    /** Cuántas muestras se guardan en memoria (30 min = 1800); las más viejas se sueltan, el archivo las conserva. */
    private val maximoEnMemoria: Int = 4 * 3600,
    /** Se llama en el hilo del monitor con cada muestra. */
    private val alMuestrear: (MuestraRecursos) -> Unit = {},
    /**
     * Hora (`System.currentTimeMillis`) desde la que se cuenta `t_ms`; 0 = desde que empezó este monitor. La sesión pasa
     * su inicio, así `t_ms` sigue corriendo aunque el monitor se reinicie (se recreó la pantalla).
     */
    private val origenHoraMs: Long = 0,
) {
    private val app = contexto.applicationContext
    private val bateria = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
    private val energia = app.getSystemService(Context.POWER_SERVICE) as PowerManager?
    private val calculadora = CalculadoraRecursos()
    private val formatoHora = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT)
    private val filtroBateria = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    @Volatile private var hilo: HandlerThread? = null
    @Volatile private var manejador: Handler? = null
    private var escritor: BufferedWriter? = null
    private var escuchaTermica: Any? = null
    @Volatile private var termico = -1
    private var headroom = Float.NaN
    private var ultimoHeadroom = 0L
    private var siguiente = 0L

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

    @Synchronized
    fun iniciar() {
        if (hilo != null) return
        val h = HandlerThread("monitor-recursos", Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
        hilo = h
        manejador = Handler(h.looper)
        abrirArchivo()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) escucharTermico()
        siguiente = SystemClock.uptimeMillis()
        manejador?.post(tic)
    }

    @Synchronized
    fun detener() {
        val h = hilo ?: return
        manejador?.removeCallbacksAndMessages(null)
        manejador?.post { cerrarArchivo() }
        h.quitSafely()
        hilo = null
        manejador = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) dejarDeEscucharTermico()
    }

    /** Copia de las muestras en memoria. */
    fun muestras(): List<MuestraRecursos> = synchronized(lista) { lista.toList() }

    fun hora(ms: Long): String = synchronized(formatoHora) { formatoHora.format(Date(ms)) }

    private fun muestrear() {
        val c0 = Debug.threadCpuTimeNanos()
        val ahora = SystemClock.elapsedRealtime()
        // El intent pegajoso de la batería (receptor null): voltaje, temperatura y si está conectado.
        val pegajoso = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(null, filtroBateria, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(null, filtroBateria)
            }
        } catch (e: RuntimeException) {
            null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (ultimoHeadroom == 0L || ahora - ultimoHeadroom >= MS_HEADROOM)) {
            // La guía de ADPF: no más de una vez cada 10 s; NaN = sin dato (no soportado o muy seguido).
            headroom = try { energia?.getThermalHeadroom(0) ?: Float.NaN } catch (e: RuntimeException) { Float.NaN }
            ultimoHeadroom = ahora
        }
        val d = try { fuente() } catch (e: RuntimeException) { null }
        val rt = Runtime.getRuntime()
        val lectura = LecturaCruda(
            paredMs = ahora,
            cpuMs = Process.getElapsedCpuTime(),
            heapJavaBytes = rt.totalMemory() - rt.freeMemory(),
            heapNativoBytes = Debug.getNativeHeapAllocatedSize(),
            proceso = estadoProceso(),
            fds = contarFds(),
            corrienteCruda = propiedad(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            cargaUah = propiedad(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            voltajeMv = pegajoso?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1,
            bateriaPct = propiedad(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            tempBatDc = pegajoso?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE,
            enchufado = (pegajoso?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            termico = termico,
            headroom = headroom,
            // 0 = el escáner todavía no cuenta (o no ha llegado nada): sin dato, no «0 cuadros por segundo».
            cuadrosProducidos = d?.cuadrosProducidos?.takeIf { it > 0 } ?: -1,
            cuadrosAnalizados = d?.cuadrosAnalizados?.takeIf { it > 0 } ?: -1,
            estadoCamara = d?.estadoCamara ?: "",
            horaMs = System.currentTimeMillis(),
        )
        val muestra = calculadora.calcular(lectura, try { fase() } catch (e: RuntimeException) { "" })
            .let {
                it.copy(
                    tMs = if (origenHoraMs > 0) lectura.horaMs - origenHoraMs else it.tMs,
                    costoMuestraUs = (Debug.threadCpuTimeNanos() - c0) / 1000,
                )
            }
        synchronized(lista) {
            lista.addLast(muestra)
            while (lista.size > maximoEnMemoria) lista.removeFirst()
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

    private fun abrirArchivo() {
        val a = archivo ?: return
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
        try {
            synchronized(BLOQUEO_ARCHIVO) {
                w.write(CsvRecursos.renglon(m, ::hora))
                w.flush()
            }
        } catch (e: IOException) {
            Log.w(ETIQUETA, "No se pudo escribir la muestra", e)
        }
    }

    private fun cerrarArchivo() {
        try {
            escritor?.close()
        } catch (_: IOException) {
        }
        escritor = null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun escucharTermico() {
        val pm = energia ?: return
        termico = pm.currentThermalStatus
        // En AOSP el listener avisa el estado actual al registrarse.
        val escucha = PowerManager.OnThermalStatusChangedListener { termico = it }
        try {
            pm.addThermalStatusListener(ContextCompat.getMainExecutor(app), escucha)
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
        private const val MS_HEADROOM = 10_000L

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
