package mx.papeleriapos.android.medicion

import mx.papeleriapos.android.csv.Csv
import kotlin.math.abs

/**
 * Lo que dice `/proc/self/status` del proceso. −1 = el kernel no trae el campo: `RssAnon` existe desde Linux 4.5 y con
 * minSdk 24 hay equipos de gama baja con 3.18 o 4.4.
 */
data class EstadoProceso(val rssKb: Long = -1, val rssAnonKb: Long = -1, val hilos: Int = -1) {
    companion object {
        /** Lee las líneas «VmRSS:   123456 kB», «RssAnon: …» y «Threads: 42»; lo que falte o no se entienda queda en −1. */
        fun leer(texto: String): EstadoProceso {
            var rss = -1L
            var anon = -1L
            var hilos = -1
            for (linea in texto.lineSequence()) {
                val dosPuntos = linea.indexOf(':')
                if (dosPuntos <= 0) continue
                val clave = linea.substring(0, dosPuntos)
                if (clave != "VmRSS" && clave != "RssAnon" && clave != "Threads") continue
                val numero = linea.substring(dosPuntos + 1).trim().substringBefore(' ').substringBefore('\t').toLongOrNull() ?: continue
                when (clave) {
                    "VmRSS" -> rss = numero
                    "RssAnon" -> anon = numero
                    else -> hilos = numero.toInt()
                }
            }
            return EstadoProceso(rss, anon, hilos)
        }
    }
}

/**
 * Lleva `BATTERY_PROPERTY_CURRENT_NOW` a µA con la convención de Android (negativo = descarga). healthd pasa el valor
 * del driver sin normalizar y hay drivers con el signo al revés, solo positivos o en mA; por eso se calibra en cada
 * sesión en lugar de tener una lista por marca:
 * - el signo, con el equipo desconectado (tiene que estar descargando);
 * - la unidad, por magnitud: con pantalla y cámara un celular gasta cientos de mA, así que una mediana de menos de
 *   10 000 solo puede ser mA;
 * - y se comprueba con lo que bajó `CHARGE_COUNTER` (µAh) en ventanas de 5 min o más: si da ~1000 veces otra cosa, se
 *   corrige la unidad.
 *
 * `Integer.MIN_VALUE` (Android 9+) o un 0 constante (Android 7 y 8 sin soporte) = el equipo no tiene medidor.
 */
class NormalizadorCorriente(private val muestrasParaCalibrar: Int = 15) {
    /** 1000 si el equipo reporta mA. */
    var factor = 1
        private set

    /** −1 si el equipo invierte el signo o solo da positivos. */
    var signo = 1
        private set

    var calibrado = false
        private set

    var sinMedidor = false
        private set

    /** Lo que dio la última comprobación con el contador de carga (carga ÷ corriente); NaN = todavía no hay. */
    var razonCarga = Double.NaN
        private set

    private val muestras = IntArray(muestrasParaCalibrar)
    private var n = 0
    private var ceros = 0
    private var distintas = 0

    /** Toma una lectura cruda; solo calibra con el equipo desconectado. */
    fun observar(crudo: Int, enchufado: Boolean) {
        if (crudo == Int.MIN_VALUE) {
            sinMedidor = true
            return
        }
        if (crudo == 0) {
            ceros++
            if (distintas == 0 && ceros >= CEROS_SIN_MEDIDOR) sinMedidor = true
            return
        }
        distintas++
        sinMedidor = false
        if (calibrado || enchufado) return
        muestras[n++] = crudo
        if (n < muestras.size) return
        val ordenadas = muestras.copyOf().also { it.sort() }
        val mediana = ordenadas[ordenadas.size / 2]
        signo = if (mediana > 0) -1 else 1
        factor = if (abs(mediana) < UMBRAL_MILIAMPERES) 1000 else 1
        calibrado = true
    }

    /** La corriente en µA (negativa al descargar); null mientras no esté calibrado o si no hay medidor. */
    fun microamperes(crudo: Int): Long? =
        if (!calibrado || sinMedidor || crudo == Int.MIN_VALUE) null else crudo.toLong() * factor * signo

    /**
     * Compara la descarga media que dice el contador de carga con la que dio la corriente en la misma ventana (ambas en
     * µA, positivas al descargar). Si una es ~1000 veces la otra, la unidad estaba mal y se corrige. Devuelve la razón.
     */
    fun comprobar(descargaPorCargaUa: Double, descargaMediaUa: Double): Double {
        if (descargaMediaUa <= 0.0 || descargaPorCargaUa <= 0.0) return razonCarga
        val razon = descargaPorCargaUa / descargaMediaUa
        if (razon in 300.0..3000.0 && factor == 1) factor = 1000
        if (razon in (1 / 3000.0)..(1 / 300.0) && factor == 1000) factor = 1
        razonCarga = razon
        return razon
    }

    companion object {
        const val UMBRAL_MILIAMPERES = 10_000
        const val CEROS_SIN_MEDIDOR = 10

        /** La descarga media (µA, positiva) según lo que bajó el contador de carga (µAh) entre dos lecturas. */
        fun descargaPorCarga(q0Uah: Long, q1Uah: Long, t0Ms: Long, t1Ms: Long): Double =
            if (t1Ms <= t0Ms) Double.NaN else (q0Uah - q1Uah) * 3_600_000.0 / (t1Ms - t0Ms)
    }
}

/** Lo que el muestreador lee del celular en un instante, tal cual. −1 (o MIN_VALUE en la batería) = no hay. */
data class LecturaCruda(
    /** `SystemClock.elapsedRealtime()`. */
    val paredMs: Long,
    /** `Process.getElapsedCpuTime()`: CPU de todos los hilos del proceso (no cuenta cameraserver ni el HAL). */
    val cpuMs: Long,
    val heapJavaBytes: Long,
    val heapNativoBytes: Long,
    val proceso: EstadoProceso,
    val fds: Int,
    val corrienteCruda: Int,
    val cargaUah: Int,
    val voltajeMv: Int,
    val bateriaPct: Int,
    val tempBatDc: Int,
    val enchufado: Boolean,
    /** `PowerManager.currentThermalStatus` (API 29+); −1 = no hay. */
    val termico: Int,
    /** `getThermalHeadroom` (API 30+, cada 10 s); NaN = sin dato, nunca 0. */
    val headroom: Float,
    /** De `Diagnostico`: cuadros que entregó la cámara y que se analizaron desde que abrió; −1 = no hay escáner. */
    val cuadrosProducidos: Long,
    val cuadrosAnalizados: Long,
    val estadoCamara: String,
    /** Hora del reloj del celular, para cruzar con los intentos de la sesión. */
    val horaMs: Long = 0,
)

/** Una fila de recursos.csv: deltas desde la muestra anterior, no acumulados. NaN o −1 = sin dato. */
data class MuestraRecursos(
    /** Desde que empezó el monitor. */
    val tMs: Long,
    val fase: String,
    val cpuMs: Long,
    val cpuPctNucleo: Double,
    val heapJavaKb: Long,
    val heapNativoKb: Long,
    val rssKb: Long,
    val rssAnonKb: Long,
    val hilos: Int,
    val fds: Int,
    val corrienteCruda: Int,
    val corrienteUa: Long?,
    val voltajeMv: Int,
    val potenciaMw: Double,
    val cargaUah: Int,
    val bateriaPct: Int,
    val tempBatDc: Int,
    val enchufado: Boolean,
    val termico: Int,
    val headroom: Float,
    val fpsCamara: Double,
    val fpsAnalisis: Double,
    val estadoCamara: String,
    val costoMuestraUs: Long = 0,
    val factorCorriente: Int = 1,
    val signoCorriente: Int = 1,
    val razonCarga: Double = Double.NaN,
    val horaMs: Long = 0,
    /** Para las cuentas, no van al CSV: ms de reloj y cuadros analizados desde la muestra anterior. */
    val paredDeltaMs: Long = 0,
    val analizadosDelta: Long = -1,
) {
    /** ms de CPU del proceso por cuadro analizado: se compara mejor entre celulares que el % de un núcleo. */
    val cpuMsPorCuadro: Double
        get() = if (analizadosDelta > 0 && cpuMs >= 0) cpuMs.toDouble() / analizadosDelta else Double.NaN
}

/**
 * Pasa de lecturas crudas a muestras: deltas de CPU y de cuadros, % de un núcleo (100·Δcpu/Δpared), la corriente
 * calibrada y su comprobación con el contador de carga. Sin Android: se prueba en la JVM.
 */
class CalculadoraRecursos(
    val corriente: NormalizadorCorriente = NormalizadorCorriente(),
    /** La ventana mínima para comprobar la corriente con el contador de carga. */
    private val ventanaCargaMs: Long = 5 * 60_000L,
) {
    private var inicio = -1L
    private var previa: LecturaCruda? = null

    // Ventana de comprobación con CHARGE_COUNTER: desconectado y calibrado.
    private var ventanaInicioMs = -1L
    private var ventanaCarga0 = 0L
    private var sumaDescargaUa = 0.0
    private var muestrasVentana = 0

    fun calcular(l: LecturaCruda, fase: String): MuestraRecursos {
        if (inicio < 0) inicio = l.paredMs
        val p = previa
        val dt = if (p == null) 0L else (l.paredMs - p.paredMs).coerceAtLeast(0)
        val dCpu = if (p == null || l.cpuMs < 0 || p.cpuMs < 0) -1L else (l.cpuMs - p.cpuMs).coerceAtLeast(0)
        val pct = if (dCpu < 0 || dt <= 0) Double.NaN else 100.0 * dCpu / dt
        val dProd = delta(l.cuadrosProducidos, p?.cuadrosProducidos)
        val dAna = delta(l.cuadrosAnalizados, p?.cuadrosAnalizados)

        corriente.observar(l.corrienteCruda, l.enchufado)
        val ua = corriente.microamperes(l.corrienteCruda)
        // Solo desconectado es el consumo del celular; conectado, la corriente es la de la carga.
        val mw = if (ua != null && l.voltajeMv > 0 && !l.enchufado) abs(ua) * l.voltajeMv / 1_000_000.0 else Double.NaN
        comprobarConCarga(l, ua)
        previa = l
        return MuestraRecursos(
            tMs = l.paredMs - inicio,
            fase = fase,
            cpuMs = dCpu,
            cpuPctNucleo = pct,
            heapJavaKb = if (l.heapJavaBytes >= 0) l.heapJavaBytes / 1024 else -1,
            heapNativoKb = if (l.heapNativoBytes >= 0) l.heapNativoBytes / 1024 else -1,
            rssKb = l.proceso.rssKb,
            rssAnonKb = l.proceso.rssAnonKb,
            hilos = l.proceso.hilos,
            fds = l.fds,
            corrienteCruda = l.corrienteCruda,
            corrienteUa = ua,
            voltajeMv = l.voltajeMv,
            potenciaMw = mw,
            cargaUah = l.cargaUah,
            bateriaPct = l.bateriaPct,
            tempBatDc = l.tempBatDc,
            enchufado = l.enchufado,
            termico = l.termico,
            headroom = l.headroom,
            fpsCamara = if (dProd < 0 || dt <= 0) Double.NaN else dProd * 1000.0 / dt,
            fpsAnalisis = if (dAna < 0 || dt <= 0) Double.NaN else dAna * 1000.0 / dt,
            estadoCamara = l.estadoCamara,
            factorCorriente = corriente.factor,
            signoCorriente = corriente.signo,
            razonCarga = corriente.razonCarga,
            horaMs = l.horaMs,
            paredDeltaMs = dt,
            analizadosDelta = dAna,
        )
    }

    /** Un contador que bajó (otro controlador, la cámara se volvió a abrir) no da un delta: queda sin dato. */
    private fun delta(actual: Long, previo: Long?): Long =
        if (previo == null || actual < 0 || previo < 0 || actual < previo) -1 else actual - previo

    private fun comprobarConCarga(l: LecturaCruda, ua: Long?) {
        val cargaValida = l.cargaUah != Int.MIN_VALUE && l.cargaUah > 0
        if (l.enchufado || ua == null || !cargaValida) {
            ventanaInicioMs = -1
            return
        }
        if (ventanaInicioMs < 0) {
            ventanaInicioMs = l.paredMs
            ventanaCarga0 = l.cargaUah.toLong()
            sumaDescargaUa = 0.0
            muestrasVentana = 0
            return
        }
        sumaDescargaUa += -ua.toDouble()
        muestrasVentana++
        if (l.paredMs - ventanaInicioMs >= ventanaCargaMs && muestrasVentana > 0) {
            val porCarga = NormalizadorCorriente.descargaPorCarga(ventanaCarga0, l.cargaUah.toLong(), ventanaInicioMs, l.paredMs)
            corriente.comprobar(porCarga, sumaDescargaUa / muestrasVentana)
            ventanaInicioMs = -1
        }
    }
}

/** recursos.csv: una fila por segundo, con el mismo formato que los demás CSV de la app. */
object CsvRecursos {
    val COLUMNAS = listOf(
        "t_ms", "fase", "cpu_ms", "cpu_pct_nucleo", "heap_java_kb", "heap_nativo_kb", "rss_kb", "rss_anon_kb", "hilos",
        "fds", "corriente_cruda", "corriente_ua", "voltaje_mv", "potencia_mw", "carga_uah", "bateria_pct", "temp_bat_dc",
        "enchufado", "termico", "headroom", "fps_camara", "fps_analisis", "estado_camara", "costo_muestra_us",
        // Agregadas al final: cómo se calibró la corriente y la hora, para cruzar con la sesión.
        "factor_corriente", "signo_corriente", "razon_carga", "hora",
    )

    fun encabezado(): String = StringBuilder(Csv.BOM).also { Csv.renglon(COLUMNAS, it) }.toString()

    fun renglon(m: MuestraRecursos, hora: (Long) -> String = { it.toString() }): String {
        val sb = StringBuilder(256)
        Csv.renglon(
            listOf(
                m.tMs.toString(),
                Csv.textoSeguro(m.fase),
                entero(m.cpuMs),
                real(m.cpuPctNucleo, 1),
                entero(m.heapJavaKb),
                entero(m.heapNativoKb),
                entero(m.rssKb),
                entero(m.rssAnonKb),
                entero(m.hilos.toLong()),
                entero(m.fds.toLong()),
                if (m.corrienteCruda == Int.MIN_VALUE) "" else m.corrienteCruda.toString(),
                m.corrienteUa?.toString() ?: "",
                if (m.voltajeMv > 0) m.voltajeMv.toString() else "",
                real(m.potenciaMw, 1),
                if (m.cargaUah == Int.MIN_VALUE || m.cargaUah <= 0) "" else m.cargaUah.toString(),
                if (m.bateriaPct in 0..100) m.bateriaPct.toString() else "",
                if (m.tempBatDc == Int.MIN_VALUE) "" else m.tempBatDc.toString(),
                if (m.enchufado) "si" else "no",
                entero(m.termico.toLong()),
                real(m.headroom.toDouble(), 3),
                real(m.fpsCamara, 1),
                real(m.fpsAnalisis, 1),
                Csv.textoSeguro(m.estadoCamara),
                m.costoMuestraUs.toString(),
                m.factorCorriente.toString(),
                m.signoCorriente.toString(),
                real(m.razonCarga, 3),
                if (m.horaMs > 0) hora(m.horaMs) else "",
            ),
            sb,
        )
        return sb.toString()
    }

    fun escribir(muestras: List<MuestraRecursos>, hora: (Long) -> String = { it.toString() }): String {
        val sb = StringBuilder(encabezado())
        for (m in muestras) sb.append(renglon(m, hora))
        return sb.toString()
    }

    private fun entero(v: Long) = if (v >= 0) v.toString() else ""
    private fun real(v: Double, decimales: Int) = if (v.isNaN() || v.isInfinite()) "" else Csv.decimal(v, decimales)
}
