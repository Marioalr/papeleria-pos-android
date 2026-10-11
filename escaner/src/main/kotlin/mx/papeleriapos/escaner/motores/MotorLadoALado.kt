package mx.papeleriapos.escaner.motores

import android.os.SystemClock
import mx.papeleriapos.escaner.ComparacionMotores
import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.LadoMotor
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Confirmador
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.nucleo.Via
import java.util.Locale
import kotlin.math.ceil

/**
 * zxing-cpp y ML Kit en cada cuadro, sobre la misma región (docs/escaner.md, «Motores»; parte 9 del plan): para medir
 * con productos reales la velocidad y los aciertos de cada uno. Lee lo que lea cualquiera, zxing-cpp primero (directo y
 * con el umbral del localizador, como el automático) y ML Kit si no; y anota por cuadro qué leyó cada uno y cuánto
 * tardó ([bitacora]), de donde sale la comparación de cada lectura aceptada. Más lento que el automático (ML Kit en
 * todos los cuadros): es para medir, no para la tienda.
 */
class MotorLadoALado(
    simbologias: Set<Simbologia>,
    probarGiros: Boolean = false,
    capacidad: Int = 512,
    private val reloj: () -> Long = SystemClock::elapsedRealtimeNanos,
) : MotorDeLectura {
    override val motor = Motor.LADO_A_LADO

    private val zxing = MotorZxingCpp(simbologias, probarGiros)
    private val mlKit: MotorMlKit? = (simbologias - Simbologia.ITF)
        .takeIf { s -> s.any { aFormatoMlKit(it) != null } }
        ?.let { MotorMlKit(it) }
    val bitacora = BitacoraLadoALado(capacidad)

    override fun leer(cuadro: CuadroY): ResultadoMotor {
        val ahora = reloj()
        var zx = zxing.leer(cuadro)
        var via = Via.DIRECTA
        var nanosZx = zx.nanos
        if (!algunaValida(zx.lecturas) && cuadro.umbralSugerido in 1..254) {
            val conUmbral = zxing.leerConUmbral(cuadro, cuadro.umbralSugerido)
            nanosZx += conUmbral.nanos
            if (algunaValida(conUmbral.lecturas)) {
                zx = conUmbral
                via = Via.UMBRAL
            }
        }
        val ml = mlKit?.leer(cuadro) ?: ResultadoMotor.VACIO
        val textoZx = textoValido(zx.lecturas)
        val textoMl = textoValido(ml.lecturas)
        bitacora.anotar(ahora, textoZx, nanosZx, textoMl, ml.nanos)
        val nanos = nanosZx + ml.nanos
        return when {
            textoZx != null -> ResultadoMotor(zx.lecturas, ml.potencialesBuffer, nanos, via)
            textoMl != null -> ResultadoMotor(ml.lecturas, ml.potencialesBuffer, nanos, Via.RESPALDO)
            else -> ResultadoMotor(emptyList(), ml.potencialesBuffer, nanos)
        }
    }

    override fun calentar() {
        zxing.calentar()
        mlKit?.calentar()
    }

    override fun close() {
        zxing.close()
        mlKit?.close()
    }

    /** El normalizado de la primera lectura válida; null si no leyó nada válido. */
    private fun textoValido(lecturas: List<Lectura>): String? {
        for (i in lecturas.indices) Confirmador.normalizar(lecturas[i])?.let { return it.normalizado }
        return null
    }
}

/**
 * Lo que hizo cada motor en los últimos cuadros (un anillo de [capacidad]): cuándo fue el cuadro, qué leyó cada uno (su
 * normalizado, o null) y cuánto tardó. Sin Android: se prueba en la JVM.
 */
class BitacoraLadoALado(private val capacidad: Int = 512) {
    private val nanos = LongArray(capacidad)
    private val zxTexto = arrayOfNulls<String>(capacidad)
    private val mlTexto = arrayOfNulls<String>(capacidad)
    private val zxNanos = LongArray(capacidad)
    private val mlNanos = LongArray(capacidad)
    private var siguiente = 0
    private var total = 0

    /** Cuántos cuadros tiene anotados (a lo más [capacidad]). */
    val cuadros: Int get() = total

    fun anotar(ahora: Long, zx: String?, nanosZx: Long, ml: String?, nanosMl: Long) {
        val i = siguiente
        nanos[i] = ahora
        zxTexto[i] = zx
        zxNanos[i] = nanosZx
        mlTexto[i] = ml
        mlNanos[i] = nanosMl
        siguiente = (i + 1) % capacidad
        if (total < capacidad) total++
    }

    /**
     * Cada motor frente al código [normalizado] que se aceptó, con los cuadros de [desde] a [hasta] (nanos del reloj):
     * en cuántos lo leyó, en cuántos leyó otra cosa, a los cuántos ms desde [desde] lo leyó por primera vez y la mediana
     * de lo que costó por cuadro.
     */
    fun comparar(desde: Long, hasta: Long, normalizado: String): ComparacionMotores {
        var cuadros = 0
        var zxLeidos = 0
        var zxOtras = 0
        var zxPrimera = -1L
        var mlLeidos = 0
        var mlOtras = 0
        var mlPrimera = -1L
        val zxMs = ArrayList<Float>()
        val mlMs = ArrayList<Float>()
        for (k in 0 until total) {
            val i = indice(k)
            val t = nanos[i]
            if (t < desde || t > hasta) continue
            cuadros++
            zxMs += zxNanos[i] / 1e6f
            mlMs += mlNanos[i] / 1e6f
            val zx = zxTexto[i]
            if (zx != null) {
                if (zx == normalizado) {
                    zxLeidos++
                    if (zxPrimera < 0) zxPrimera = (t - desde) / 1_000_000L
                } else {
                    zxOtras++
                }
            }
            val ml = mlTexto[i]
            if (ml != null) {
                if (ml == normalizado) {
                    mlLeidos++
                    if (mlPrimera < 0) mlPrimera = (t - desde) / 1_000_000L
                } else {
                    mlOtras++
                }
            }
        }
        return ComparacionMotores(
            cuadros,
            LadoMotor(zxLeidos, zxOtras, zxPrimera, mediana(zxMs)),
            LadoMotor(mlLeidos, mlOtras, mlPrimera, mediana(mlMs)),
        )
    }

    /** Los últimos [n] cuadros, para el diagnóstico: «zxing 0.45 ms, lee 12/30 · ML Kit 38 ms, lee 9/30». */
    fun resumenReciente(n: Int = 30): String {
        val cuantos = minOf(n, total)
        if (cuantos == 0) return ""
        var zxLee = 0
        var mlLee = 0
        var zxNs = 0L
        var mlNs = 0L
        for (k in total - cuantos until total) {
            val i = indice(k)
            if (zxTexto[i] != null) zxLee++
            if (mlTexto[i] != null) mlLee++
            zxNs += zxNanos[i]
            mlNs += mlNanos[i]
        }
        return String.format(
            Locale.ROOT, "zxing %.2f ms, lee %d/%d · ML Kit %.0f ms, lee %d/%d",
            zxNs / 1e6 / cuantos, zxLee, cuantos, mlNs / 1e6 / cuantos, mlLee, cuantos,
        )
    }

    /** La posición del cuadro [k] contando desde el más viejo. */
    private fun indice(k: Int): Int = if (total < capacidad) k else (siguiente + k) % capacidad

    /** Rango más cercano al 50 %; 0 sin datos. */
    private fun mediana(valores: ArrayList<Float>): Float {
        if (valores.isEmpty()) return 0f
        valores.sort()
        val k = ceil(0.5 * valores.size).toInt().coerceIn(1, valores.size)
        return valores[k - 1]
    }
}
