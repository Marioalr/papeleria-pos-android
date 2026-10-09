package mx.papeleriapos.android.medicion

import mx.papeleriapos.android.csv.Csv
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
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

        /**
         * Lo mismo que [leer] pero de los bytes tal cual (el monitor relee `/proc/self/status` cada segundo en un buffer
         * propio): sin pasar a texto ni partir en líneas, que es basura en cada muestra.
         */
        fun leer(b: ByteArray, largo: Int): EstadoProceso {
            var rss = -1L
            var anon = -1L
            var hilos = -1
            var i = 0
            val fin = largo.coerceAtMost(b.size)
            while (i < fin) {
                var finLinea = i
                while (finLinea < fin && b[finLinea] != '\n'.code.toByte()) finLinea++
                when {
                    empiezaCon(b, i, finLinea, VMRSS) -> rss = numero(b, i + VMRSS.size, finLinea)
                    empiezaCon(b, i, finLinea, RSS_ANON) -> anon = numero(b, i + RSS_ANON.size, finLinea)
                    empiezaCon(b, i, finLinea, THREADS) -> hilos = numero(b, i + THREADS.size, finLinea).toInt()
                }
                i = finLinea + 1
            }
            return EstadoProceso(rss, anon, hilos)
        }

        private val VMRSS = "VmRSS:".toByteArray(Charsets.US_ASCII)
        private val RSS_ANON = "RssAnon:".toByteArray(Charsets.US_ASCII)
        private val THREADS = "Threads:".toByteArray(Charsets.US_ASCII)

        private fun empiezaCon(b: ByteArray, desde: Int, hasta: Int, prefijo: ByteArray): Boolean {
            if (hasta - desde < prefijo.size) return false
            for (k in prefijo.indices) if (b[desde + k] != prefijo[k]) return false
            return true
        }

        /** El primer número después de espacios o tabuladores; −1 si no hay. */
        private fun numero(b: ByteArray, desde: Int, hasta: Int): Long {
            var i = desde
            while (i < hasta && (b[i] == ' '.code.toByte() || b[i] == '\t'.code.toByte())) i++
            var v = 0L
            var digitos = 0
            while (i < hasta && b[i] in '0'.code.toByte()..'9'.code.toByte()) {
                v = v * 10 + (b[i] - '0'.code.toByte())
                digitos++
                i++
            }
            return if (digitos == 0) -1 else v
        }
    }
}

/**
 * Cuadros por segundo con un contador del diagnóstico del escáner (cuadros producidos o analizados). Tolera lo que hace
 * el escáner con ellos:
 * - vuelven a 0 en cada enlace: lo contado desde ahí es el delta (no se pierde el segundo ni queda sin dato);
 * - el diagnóstico se publica cada ~500 ms, así que lo que se ve cada segundo es de hace 0 a 500 ms y el delta de un
 *   segundo salta ±50 %: la tasa se saca sobre los últimos [ventanaMs] (±15 % en el peor caso por fila). Los deltas no se
 *   pierden, así que los promedios largos (por minuto) salen exactos.
 *
 * Sin asignaciones por muestra: un anillo de primitivos. Se usa desde un solo hilo (el del monitor).
 */
class TasaCuadros(private val ventanaMs: Long = 3_000) {
    private val tiempos = LongArray(CAPACIDAD)
    private val acumulados = LongArray(CAPACIDAD)
    private var cuantos = 0
    private var cabeza = 0
    private var previo = -1L
    private var acumulado = 0L

    /** Cuadros contados desde la observación anterior; −1 = sin dato (la primera, o sin escáner). */
    var delta = -1L
        private set

    /** Toma [contador] (−1 = no hay escáner) visto en [tMs]; devuelve los cuadros por segundo de la ventana (NaN = sin dato). */
    fun observar(contador: Long, tMs: Long): Double {
        if (contador < 0) {
            previo = -1L
            cuantos = 0
            delta = -1L
            return Double.NaN
        }
        delta = when {
            previo < 0 -> -1L
            contador >= previo -> contador - previo
            // Se volvió a enlazar: empezó de cero dentro del intervalo y lo que lleva es lo de este intervalo.
            else -> contador
        }
        previo = contador
        if (delta > 0) acumulado += delta
        // El punto más viejo dentro de la ventana (al menos el anterior), antes de agregar este.
        var tViejo = Long.MIN_VALUE
        var aViejo = 0L
        for (k in 1..cuantos) {
            val i = (cabeza - k + CAPACIDAD) % CAPACIDAD
            if (tiempos[i] >= tMs) continue
            if (tViejo != Long.MIN_VALUE && tiempos[i] < tMs - ventanaMs) break
            tViejo = tiempos[i]
            aViejo = acumulados[i]
        }
        tiempos[cabeza] = tMs
        acumulados[cabeza] = acumulado
        cabeza = (cabeza + 1) % CAPACIDAD
        if (cuantos < CAPACIDAD) cuantos++
        if (tViejo == Long.MIN_VALUE) return Double.NaN
        return (acumulado - aViejo) * 1000.0 / (tMs - tViejo)
    }

    private companion object {
        const val CAPACIDAD = 16
    }
}

/** Pregunta el headroom térmico (`PowerManager.getThermalHeadroom(0)`); NaN = sin dato. */
fun interface FuenteHeadroom {
    fun consultar(): Float
}

/**
 * El headroom térmico compartido por el proceso: el sistema devuelve NaN si se le pregunta otra vez muy seguido (y la
 * guía de ADPF pide no más de una vez cada 10 s), así que dos muestreadores que preguntaran por su cuenta (la sesión y una
 * prueba de estrés) se quitarían el dato uno a otro. Se pregunta a lo más una vez cada [intervaloMs] y los demás reciben
 * el último valor.
 */
class CacheHeadroom(private val intervaloMs: Long = 10_000) {
    private var ultimaConsulta = Long.MIN_VALUE
    private var valor = Float.NaN

    @Synchronized
    fun leer(ahoraMs: Long, fuente: FuenteHeadroom): Float {
        if (ultimaConsulta == Long.MIN_VALUE || ahoraMs < ultimaConsulta || ahoraMs - ultimaConsulta >= intervaloMs) {
            valor = try { fuente.consultar() } catch (e: RuntimeException) { Float.NaN }
            ultimaConsulta = ahoraMs
        }
        return valor
    }

    companion object {
        val delProceso = CacheHeadroom()
    }
}

/**
 * La hora como `yyyy-MM-dd'T'HH:mm:ss.SSSZ` (la de los demás CSV) escrita directo en un StringBuilder con un calendario
 * reutilizado: `SimpleDateFormat` arma objetos en cada llamada y el monitor la escribe cada segundo. Un solo hilo.
 */
class HoraIso(zona: TimeZone = TimeZone.getDefault()) {
    private val calendario = GregorianCalendar(zona)

    fun agregar(sb: StringBuilder, ms: Long) {
        val c = calendario
        c.timeInMillis = ms
        digitos(sb, c.get(Calendar.YEAR), 4); sb.append('-')
        digitos(sb, c.get(Calendar.MONTH) + 1, 2); sb.append('-')
        digitos(sb, c.get(Calendar.DAY_OF_MONTH), 2); sb.append('T')
        digitos(sb, c.get(Calendar.HOUR_OF_DAY), 2); sb.append(':')
        digitos(sb, c.get(Calendar.MINUTE), 2); sb.append(':')
        digitos(sb, c.get(Calendar.SECOND), 2); sb.append('.')
        digitos(sb, c.get(Calendar.MILLISECOND), 3)
        val minutos = (c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 60_000
        sb.append(if (minutos < 0) '-' else '+')
        digitos(sb, abs(minutos) / 60, 2)
        digitos(sb, abs(minutos) % 60, 2)
    }

    private fun digitos(sb: StringBuilder, v: Int, ancho: Int) {
        var divisor = 1
        repeat(ancho - 1) { divisor *= 10 }
        var resto = v
        if (resto >= divisor * 10) {
            sb.append(resto)
            return
        }
        while (divisor > 0) {
            sb.append(('0'.code + resto / divisor).toChar())
            resto %= divisor
            divisor /= 10
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
    private val producidos = TasaCuadros()
    private val analizados = TasaCuadros()

    // Ventana de comprobación con CHARGE_COUNTER: desconectado y calibrado.
    private var ventanaInicioMs = -1L
    private var ventanaCarga0 = 0L
    private var sumaDescargaUa = 0.0
    private var muestrasVentana = 0

    /**
     * La muestra de [l]. [origenHoraMs] > 0: `t_ms` cuenta desde esa hora (la del inicio de la sesión) y no desde la
     * primera lectura; [costoUs]: lo que costó leer, para su columna.
     */
    fun calcular(l: LecturaCruda, fase: String, origenHoraMs: Long = 0, costoUs: Long = 0): MuestraRecursos {
        if (inicio < 0) inicio = l.paredMs
        val p = previa
        val dt = if (p == null) 0L else (l.paredMs - p.paredMs).coerceAtLeast(0)
        val dCpu = if (p == null || l.cpuMs < 0 || p.cpuMs < 0) -1L else (l.cpuMs - p.cpuMs).coerceAtLeast(0)
        val pct = if (dCpu < 0 || dt <= 0) Double.NaN else 100.0 * dCpu / dt
        val fpsCamara = producidos.observar(l.cuadrosProducidos, l.paredMs)
        val fpsAnalisis = analizados.observar(l.cuadrosAnalizados, l.paredMs)

        corriente.observar(l.corrienteCruda, l.enchufado)
        val ua = corriente.microamperes(l.corrienteCruda)
        // Solo desconectado es el consumo del celular; conectado, la corriente es la de la carga.
        val mw = if (ua != null && l.voltajeMv > 0 && !l.enchufado) abs(ua) * l.voltajeMv / 1_000_000.0 else Double.NaN
        comprobarConCarga(l, ua)
        previa = l
        return MuestraRecursos(
            tMs = if (origenHoraMs > 0) l.horaMs - origenHoraMs else l.paredMs - inicio,
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
            fpsCamara = fpsCamara,
            fpsAnalisis = fpsAnalisis,
            estadoCamara = l.estadoCamara,
            costoMuestraUs = costoUs,
            factorCorriente = corriente.factor,
            signoCorriente = corriente.signo,
            razonCarga = corriente.razonCarga,
            horaMs = l.horaMs,
            paredDeltaMs = dt,
            analizadosDelta = analizados.delta,
        )
    }

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

    /**
     * Agrega la fila de [m] a [sb] campo por campo, sin listas ni `String.format` (el monitor escribe una por segundo
     * con el mismo StringBuilder). [hora] escribe la hora del celular.
     */
    fun agregar(m: MuestraRecursos, sb: StringBuilder, hora: (StringBuilder, Long) -> Unit) {
        sb.append(m.tMs).append(',')
        sb.append(Csv.campo(Csv.textoSeguro(m.fase))).append(',')
        entero(sb, m.cpuMs); sb.append(',')
        real(sb, m.cpuPctNucleo, 1); sb.append(',')
        entero(sb, m.heapJavaKb); sb.append(',')
        entero(sb, m.heapNativoKb); sb.append(',')
        entero(sb, m.rssKb); sb.append(',')
        entero(sb, m.rssAnonKb); sb.append(',')
        entero(sb, m.hilos.toLong()); sb.append(',')
        entero(sb, m.fds.toLong()); sb.append(',')
        if (m.corrienteCruda != Int.MIN_VALUE) sb.append(m.corrienteCruda)
        sb.append(',')
        m.corrienteUa?.let { sb.append(it) }
        sb.append(',')
        if (m.voltajeMv > 0) sb.append(m.voltajeMv)
        sb.append(',')
        real(sb, m.potenciaMw, 1); sb.append(',')
        if (m.cargaUah != Int.MIN_VALUE && m.cargaUah > 0) sb.append(m.cargaUah)
        sb.append(',')
        if (m.bateriaPct in 0..100) sb.append(m.bateriaPct)
        sb.append(',')
        if (m.tempBatDc != Int.MIN_VALUE) sb.append(m.tempBatDc)
        sb.append(',')
        sb.append(if (m.enchufado) "si" else "no").append(',')
        entero(sb, m.termico.toLong()); sb.append(',')
        real(sb, m.headroom.toDouble(), 3); sb.append(',')
        real(sb, m.fpsCamara, 1); sb.append(',')
        real(sb, m.fpsAnalisis, 1); sb.append(',')
        sb.append(Csv.campo(Csv.textoSeguro(m.estadoCamara))).append(',')
        sb.append(m.costoMuestraUs).append(',')
        sb.append(m.factorCorriente).append(',')
        sb.append(m.signoCorriente).append(',')
        real(sb, m.razonCarga, 3); sb.append(',')
        if (m.horaMs > 0) hora(sb, m.horaMs)
        sb.append(Csv.FIN_DE_LINEA)
    }

    fun renglon(m: MuestraRecursos, hora: (Long) -> String = { it.toString() }): String {
        val sb = StringBuilder(256)
        agregar(m, sb) { s, ms -> s.append(hora(ms)) }
        return sb.toString()
    }

    fun escribir(muestras: List<MuestraRecursos>, hora: (Long) -> String = { it.toString() }): String {
        val sb = StringBuilder(encabezado())
        for (m in muestras) sb.append(renglon(m, hora))
        return sb.toString()
    }

    private fun entero(sb: StringBuilder, v: Long) {
        if (v >= 0) sb.append(v)
    }

    private fun real(sb: StringBuilder, v: Double, decimales: Int) {
        if (!v.isNaN() && !v.isInfinite()) agregarDecimal(sb, v, decimales)
    }

    /** [v] con [decimales] (redondeo a la mitad hacia arriba) y punto decimal, sin `String.format`. */
    fun agregarDecimal(sb: StringBuilder, v: Double, decimales: Int) {
        var escala = 1L
        repeat(decimales) { escala *= 10 }
        val redondeado = Math.round(abs(v) * escala)
        if (v < 0 && redondeado != 0L) sb.append('-')
        sb.append(redondeado / escala)
        if (decimales == 0) return
        sb.append('.')
        val fraccion = redondeado % escala
        var divisor = escala / 10
        while (divisor > 0) {
            sb.append(('0'.code + ((fraccion / divisor) % 10).toInt()).toChar())
            divisor /= 10
        }
    }
}
