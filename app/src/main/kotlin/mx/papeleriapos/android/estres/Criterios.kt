package mx.papeleriapos.android.estres

import mx.papeleriapos.android.banco.Estadistica
import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.medicion.MuestraRecursos
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.rangoFpsPedido
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Memoria, hilos y descriptores tras un GC en un punto de la prueba (el ciclo o cambio [en]). −1 = sin dato. */
data class PuntoMemoria(
    val en: Int,
    val heapJavaKb: Long = -1,
    val heapNativoKb: Long = -1,
    val pssKb: Long = -1,
    val rssKb: Long = -1,
    val hilos: Int = -1,
    val fds: Int = -1,
)

/** Con qué cámara corrió una prueba: el motor y el rango de cuadros por segundo (va en sus CSV). */
data class ConfigEstres(val motor: Motor = Motor.AUTOMATICO, val fpsFijo: Boolean = true, val fps60: Boolean = false) {
    val textoFps: String get() = ConfigEscaner(fpsFijo = fpsFijo, fps60 = fps60).rangoFpsPedido
}

/** Una regla de la prueba con lo medido, el límite y si pasa. */
data class Criterio(val nombre: String, val valor: String, val limite: String, val pasa: Boolean)

/** El resultado de una prueba de estrés: pasa si pasan todos sus criterios. */
data class Dictamen(val criterios: List<Criterio>) {
    val pasa: Boolean get() = criterios.isNotEmpty() && criterios.all { it.pasa }
    val fallidos: List<Criterio> get() = criterios.filter { !it.pasa }
}

/** Las cuentas que comparten las pruebas. */
object Cuentas {
    /** La pendiente de la recta de mínimos cuadrados; NaN con menos de dos puntos o todos en la misma x. */
    fun pendiente(xs: List<Double>, ys: List<Double>): Double {
        require(xs.size == ys.size) { "Distinto número de x y de y." }
        if (xs.size < 2) return Double.NaN
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        return if (den == 0.0) Double.NaN else num / den
    }

    fun p95(valores: List<Double>): Double = Estadistica.de(valores.filter { !it.isNaN() })?.p95 ?: Double.NaN

    fun mediana(valores: List<Double>): Double = Estadistica.de(valores.filter { !it.isNaN() })?.mediana ?: Double.NaN

    fun texto(v: Double, decimales: Int = 1): String = if (v.isNaN()) "—" else Csv.decimal(v, decimales)

    /**
     * Que la memoria, los hilos y los descriptores no crezcan de forma sostenida: la pendiente del heap Java, del nativo
     * y del RSS menor a [maxKbPor100] por cada 100 [unidad], y los hilos y descriptores al final a ± [tolerancia] de la
     * base (el primer punto, tomado cuando ya se creó todo una vez).
     */
    fun criteriosMemoria(
        puntos: List<PuntoMemoria>,
        unidad: String,
        maxKbPor100: Double = 2048.0,
        tolerancia: Int = 2,
    ): List<Criterio> {
        if (puntos.size < 2) return listOf(Criterio("Puntos de memoria", puntos.size.toString(), "≥ 2", false))
        val salida = ArrayList<Criterio>()
        fun pendientePor100(sel: (PuntoMemoria) -> Long, nombre: String) {
            val validos = puntos.filter { sel(it) >= 0 }
            if (validos.size < 2) return
            val p = pendiente(validos.map { it.en.toDouble() }, validos.map { sel(it).toDouble() }) * 100
            salida += Criterio("$nombre (KB por 100 $unidad)", texto(p, 0), "< ${maxKbPor100.roundToInt()}", p.isNaN() || p < maxKbPor100)
        }
        pendientePor100({ it.heapJavaKb }, "Heap Java")
        pendientePor100({ it.heapNativoKb }, "Heap nativo")
        pendientePor100({ it.rssKb }, "RSS")
        val base = puntos.first()
        val final = puntos.last()
        if (base.hilos >= 0 && final.hilos >= 0) {
            salida += Criterio("Hilos (base → final)", "${base.hilos} → ${final.hilos}", "≤ base + $tolerancia", final.hilos - base.hilos <= tolerancia)
        }
        if (base.fds >= 0 && final.fds >= 0) {
            salida += Criterio("Descriptores (base → final)", "${base.fds} → ${final.fds}", "≤ base + $tolerancia", final.fds - base.fds <= tolerancia)
        }
        return salida
    }
}

// ---------------------------------------------------------------- (a) Ciclos abrir → primer cuadro → cerrar

/** Un ciclo: ms desde que se pidió abrir hasta OPEN y hasta el primer cuadro analizado, y de cerrar hasta CLOSED. */
data class Ciclo(
    val n: Int,
    /** NaN = no se pudo observar el estado de la cámara. */
    val abrirMs: Double,
    val primerCuadroMs: Double,
    val cerrarMs: Double,
    val error: String? = null,
)

object CriteriosCiclos {
    /**
     * Ciclos de calentamiento que no entran a la ventana de inicio del p95: el 1 es frío (el proveedor de CameraX, las
     * bibliotecas nativas y la primera carga de ML Kit) e inflaría el p95 inicial, haciendo indulgente el ×1.2.
     */
    const val CALENTAMIENTO = 1

    /** Con menos ciclos buenos (sin contar el calentamiento) el p95 no dice nada: el criterio falla, no se omite. */
    const val MINIMO_PARA_P95 = 10

    /** Lo más de ciclos con error que se acepta: el 1 % (y al menos 2, para pruebas cortas). */
    fun maximoConError(ciclos: Int): Int = max(2, ciclos / 100)

    /**
     * Los errores que no se recuperaron. Con el mismo controlador ([nuevoPorCiclo] = false), un error se recuperó si el
     * ciclo siguiente salió bien con ese controlador. Con uno nuevo en cada ciclo la «recuperación» sería automática (el
     * siguiente empieza de cero), así que todo error cuenta.
     */
    fun erroresSinRecuperar(ciclos: List<Ciclo>, nuevoPorCiclo: Boolean): Int {
        if (nuevoPorCiclo) return ciclos.count { it.error != null }
        val orden = ciclos.sortedBy { it.n }
        var n = 0
        for (i in orden.indices) {
            if (orden[i].error == null) continue
            val siguiente = orden.getOrNull(i + 1)
            val recuperado = siguiente != null && siguiente.n == orden[i].n + 1 && siguiente.error == null
            if (!recuperado) n++
        }
        return n
    }

    /**
     * Los primeros y los últimos [fraccion] de los ciclos buenos (al menos 5), para comparar el p95. El inicio empieza
     * después de [CALENTAMIENTO].
     */
    fun extremos(ciclos: List<Ciclo>, fraccion: Double = 0.1): Pair<List<Ciclo>, List<Ciclo>> {
        val buenos = buenosSinCalentamiento(ciclos)
        val k = max(5, (ciclos.size * fraccion).roundToInt()).coerceAtMost(buenos.size / 2)
        return buenos.take(k) to buenos.takeLast(k)
    }

    private fun buenosSinCalentamiento(ciclos: List<Ciclo>) =
        ciclos.filter { it.error == null && it.n > CALENTAMIENTO }.sortedBy { it.n }

    /**
     * Ciclos con error ≤ 1 % (o ≤ 2), 0 errores sin recuperar ([erroresSinRecuperar]), el p95 del primer cuadro al
     * final ≤ [razonMaxima] veces el del inicio (con al menos [MINIMO_PARA_P95] ciclos buenos; si no, falla) y sin
     * crecimiento sostenido de memoria, hilos ni descriptores.
     */
    fun evaluar(ciclos: List<Ciclo>, memoria: List<PuntoMemoria>, nuevoPorCiclo: Boolean, razonMaxima: Double = 1.2): Dictamen {
        val criterios = ArrayList<Criterio>()
        criterios += Criterio("Ciclos hechos", ciclos.size.toString(), "> 0", ciclos.isNotEmpty())
        val conError = ciclos.count { it.error != null }
        val maximo = maximoConError(ciclos.size)
        val pct = if (ciclos.isEmpty()) 0.0 else 100.0 * conError / ciclos.size
        criterios += Criterio(
            "Ciclos con error",
            "$conError de ${ciclos.size} (${Cuentas.texto(pct, 1)} %)",
            "≤ $maximo (1 %, al menos 2)",
            conError <= maximo,
        )
        val sinRecuperar = erroresSinRecuperar(ciclos, nuevoPorCiclo)
        criterios += Criterio(
            if (nuevoPorCiclo) "Errores sin recuperar (controlador nuevo: todos cuentan)" else "Errores sin recuperar",
            "$sinRecuperar (de $conError errores)",
            "0",
            sinRecuperar == 0,
        )
        val nombreP95 = "p95 al primer cuadro (inicio → final, ms)"
        val buenos = buenosSinCalentamiento(ciclos).size
        if (buenos < MINIMO_PARA_P95) {
            criterios += Criterio(nombreP95, "solo $buenos ciclos buenos", "≥ $MINIMO_PARA_P95 ciclos buenos", false)
        } else {
            val (inicio, fin) = extremos(ciclos)
            val p0 = Cuentas.p95(inicio.map { it.primerCuadroMs })
            val p1 = Cuentas.p95(fin.map { it.primerCuadroMs })
            val razon = p1 / p0
            criterios += Criterio(
                nombreP95,
                "${Cuentas.texto(p0, 0)} → ${Cuentas.texto(p1, 0)} (×${Cuentas.texto(razon, 2)})",
                "≤ ×${Cuentas.texto(razonMaxima, 1)}",
                !razon.isNaN() && razon <= razonMaxima,
            )
        }
        criterios += Cuentas.criteriosMemoria(memoria, "ciclos")
        return Dictamen(criterios)
    }

    fun resumen(ciclos: List<Ciclo>): String {
        val buenos = ciclos.filter { it.error == null }
        return "abrir p50 ${Cuentas.texto(Cuentas.mediana(buenos.map { it.abrirMs }), 0)} ms · " +
            "primer cuadro p50 ${Cuentas.texto(Cuentas.mediana(buenos.map { it.primerCuadroMs }), 0)} / " +
            "p95 ${Cuentas.texto(Cuentas.p95(buenos.map { it.primerCuadroMs }), 0)} ms · " +
            "cerrar p50 ${Cuentas.texto(Cuentas.mediana(buenos.map { it.cerrarMs }), 0)} ms"
    }
}

// ---------------------------------------------------------------- (b) Zoom y linterna rápidos

/** Lo que se ve del escáner en un segundo de la ráfaga. */
data class SegundoRafaga(val segundo: Int, val ordenesZoom: Int, val ordenesLinterna: Int, val cuadros: Long, val fase: String)

/**
 * Al terminar la ráfaga: lo que dice la interfaz y lo que hace la cámara. NaN/null = no se sabe.
 *
 * @param zoomUi el zoom que dice el escáner; al final se le devuelve a la cámara (la ráfaga la manejó directo) y
 *   [zoomReal] es el que la cámara reporta después.
 * @param zoomsDistintos cuántos `zoomRatio` distintos reportó la cámara durante la ráfaga (si es 1, no estresó nada).
 * @param zoomCancelados órdenes que la siguiente canceló (`OperationCanceledException`): lo esperado a 30 ms.
 * @param zoomFallidos órdenes que fallaron por otra cosa.
 * @param linternaReal la linterna según la cámara; la ráfaga termina pidiéndola apagada.
 */
data class FinalRafaga(
    val zoomUi: Float,
    val zoomReal: Float,
    val hayLinterna: Boolean,
    val linternaUi: Boolean,
    val linternaReal: Boolean?,
    val fase: String,
    val error: String?,
    val zoomsDistintos: Int = 0,
    val zoomCancelados: Int = 0,
    val zoomFallidos: Int = 0,
    val linternaFallidas: Int = 0,
)

object CriteriosRafaga {
    /** El hueco más largo, en segundos, en que no llegó ningún cuadro nuevo. */
    fun huecoMaximo(segundos: List<SegundoRafaga>): Int {
        var maximo = 0
        var actual = 0
        var previo = -1L
        for (s in segundos) {
            if (previo >= 0 && s.cuadros <= previo) actual++ else actual = 0
            maximo = max(maximo, actual)
            previo = s.cuadros
        }
        return maximo
    }

    fun evaluar(segundos: List<SegundoRafaga>, final: FinalRafaga?, huecoMaximoS: Int = 2): Dictamen {
        val c = ArrayList<Criterio>()
        val zoom = segundos.lastOrNull()?.ordenesZoom ?: 0
        val linterna = segundos.lastOrNull()?.ordenesLinterna ?: 0
        c += Criterio("Órdenes de zoom y de linterna", "$zoom y $linterna", "> 0", zoom > 0)
        c += Criterio("Sin error del escáner", final?.error ?: "ninguno", "ninguno", final != null && final.error == null && final.fase != "ERROR")
        val hueco = huecoMaximo(segundos)
        c += Criterio("Segundos seguidos sin cuadros nuevos", hueco.toString(), "≤ $huecoMaximoS", hueco <= huecoMaximoS)
        if (final != null) {
            c += Criterio("Zooms distintos que reportó la cámara", final.zoomsDistintos.toString(), "≥ 2", final.zoomsDistintos >= 2)
            c += Criterio("Órdenes de zoom canceladas por la siguiente", final.zoomCancelados.toString(), "—", true)
            c += Criterio("Órdenes de zoom con error", final.zoomFallidos.toString(), "0", final.zoomFallidos == 0)
        }
        if (final != null && !final.zoomReal.isNaN()) {
            c += Criterio(
                "Zoom de la interfaz = el de la cámara",
                "${Cuentas.texto(final.zoomUi.toDouble(), 2)} / ${Cuentas.texto(final.zoomReal.toDouble(), 2)}",
                "± 0.05",
                abs(final.zoomUi - final.zoomReal) <= 0.05f,
            )
        }
        if (final != null && final.hayLinterna && final.linternaReal != null) {
            c += Criterio("Linterna al final (la última orden la apaga)", si(final.linternaReal), "apagada", !final.linternaReal)
            c += Criterio(
                "Linterna de la interfaz = la de la cámara",
                "${si(final.linternaUi)} / ${si(final.linternaReal)}",
                "iguales",
                final.linternaUi == final.linternaReal,
            )
            c += Criterio("Órdenes de linterna con error", final.linternaFallidas.toString(), "0", final.linternaFallidas == 0)
        }
        return Dictamen(c)
    }

    private fun si(b: Boolean) = if (b) "encendida" else "apagada"
}

// ---------------------------------------------------------------- (c) Cambiar de motor en caliente

/** Un cambio de motor: ms hasta el primer cuadro analizado con el motor nuevo. */
data class CambioMotor(val n: Int, val motor: String, val ms: Double, val error: String? = null)

object CriteriosMotor {
    fun evaluar(cambios: List<CambioMotor>, memoria: List<PuntoMemoria>, msMaximo: Double = 3_000.0): Dictamen {
        val c = ArrayList<Criterio>()
        val errores = cambios.count { it.error != null }
        c += Criterio("Cambios hechos", cambios.size.toString(), "> 0", cambios.isNotEmpty())
        c += Criterio("Cambios con error", errores.toString(), "0", errores == 0)
        val p95 = Cuentas.p95(cambios.filter { it.error == null }.map { it.ms })
        c += Criterio("p95 hasta leer con el motor nuevo (ms)", Cuentas.texto(p95, 0), "≤ ${msMaximo.roundToInt()}", !p95.isNaN() && p95 <= msMaximo)
        c += Cuentas.criteriosMemoria(memoria, "cambios")
        return Dictamen(c)
    }
}

// ---------------------------------------------------------------- (d) 30 min escaneando: estrangulamiento térmico

/** Un minuto de la prueba larga, para la tabla. */
data class MinutoContinuo(
    val minuto: Int,
    val fpsAnalisis: Double,
    val cpuMsPorCuadro: Double,
    val cpuPct: Double,
    val tempBatC: Double,
    val termicoMaximo: Int,
    val headroom: Double,
)

/** Cuándo se considera que el celular se estranguló por calor. */
data class ResultadoTermico(
    val detectado: Boolean,
    /** El segundo en que se cumplieron por primera vez las condiciones durante toda la ventana; −1 = nunca. */
    val desdeSegundo: Int,
    /** Qué condiciones se cumplían entonces. */
    val condiciones: List<String>,
    val fpsBase: Double,
    val cpuPorCuadroBase: Double,
    val minutos: List<MinutoContinuo>,
)

/**
 * Detecta el estrangulamiento térmico con la carga constante (la misma escena todo el tiempo): hay estrangulamiento
 * cuando, en una ventana de [ventanaS] segundos, se cumplen dos o más de: estado térmico ≥ MODERATE (2) toda la
 * ventana, headroom ≥ 0.85, cuadros analizados por segundo < 85 % de los del minuto 1, o ms de CPU por cuadro ≥ 25 %
 * más que en el minuto 1. Los umbrales salen de la investigación y se ajustan con datos.
 */
class DetectorEstrangulamiento(
    private val ventanaS: Int = 60,
    private val termicoMinimo: Int = 2,
    private val headroomMaximo: Double = 0.85,
    private val fpsMinimo: Double = 0.85,
    private val cpuMaximo: Double = 1.25,
    private val condicionesMinimas: Int = 2,
) {
    fun evaluar(muestras: List<MuestraRecursos>): ResultadoTermico {
        val minuto1 = muestras.filter { it.tMs in 0 until 60_000 }
        val fpsBase = promedio(minuto1.map { it.fpsAnalisis })
        val cpuBase = cpuPorCuadro(minuto1)
        val porSegundo = muestras.sortedBy { it.tMs }
        var desde = -1
        var condiciones = emptyList<String>()
        if (porSegundo.isNotEmpty()) {
            var i0 = 0
            for (i in porSegundo.indices) {
                val t = porSegundo[i].tMs
                if (t < 60_000L + ventanaS * 1000L) continue
                while (porSegundo[i0].tMs <= t - ventanaS * 1000L) i0++
                val ventana = porSegundo.subList(i0, i + 1)
                val cumplidas = condicionesEn(ventana, fpsBase, cpuBase)
                if (cumplidas.size >= condicionesMinimas) {
                    desde = (t / 1000).toInt()
                    condiciones = cumplidas
                    break
                }
            }
        }
        return ResultadoTermico(desde >= 0, desde, condiciones, fpsBase, cpuBase, minutos(porSegundo))
    }

    /** Las condiciones que se cumplen en [ventana] respecto al minuto 1. */
    fun condicionesEn(ventana: List<MuestraRecursos>, fpsBase: Double, cpuBase: Double): List<String> {
        val c = ArrayList<String>()
        val termicos = ventana.map { it.termico }
        if (termicos.isNotEmpty() && termicos.all { it >= termicoMinimo }) c += "térmico ≥ MODERATE"
        val headrooms = ventana.map { it.headroom.toDouble() }.filter { !it.isNaN() }
        if (headrooms.isNotEmpty() && headrooms.all { it >= headroomMaximo }) c += "headroom ≥ ${Csv.decimal(headroomMaximo, 2)}"
        val fps = promedio(ventana.map { it.fpsAnalisis })
        if (!fps.isNaN() && !fpsBase.isNaN() && fpsBase > 0 && fps < fpsMinimo * fpsBase) c += "cuadros/s < ${(fpsMinimo * 100).roundToInt()} % del minuto 1"
        val cpu = cpuPorCuadro(ventana)
        if (!cpu.isNaN() && !cpuBase.isNaN() && cpuBase > 0 && cpu >= cpuMaximo * cpuBase) c += "CPU por cuadro +${((cpuMaximo - 1) * 100).roundToInt()} %"
        return c
    }

    fun minutos(muestras: List<MuestraRecursos>): List<MinutoContinuo> =
        muestras.groupBy { (it.tMs / 60_000).toInt() }.toSortedMap().map { (m, filas) ->
            MinutoContinuo(
                minuto = m + 1,
                fpsAnalisis = promedio(filas.map { it.fpsAnalisis }),
                cpuMsPorCuadro = cpuPorCuadro(filas),
                cpuPct = promedio(filas.map { it.cpuPctNucleo }),
                tempBatC = promedio(filas.filter { it.tempBatDc != Int.MIN_VALUE }.map { it.tempBatDc / 10.0 }),
                termicoMaximo = filas.maxOf { it.termico },
                headroom = promedio(filas.map { it.headroom.toDouble() }),
            )
        }

    private fun promedio(v: List<Double>): Double = v.filter { !it.isNaN() }.let { if (it.isEmpty()) Double.NaN else it.average() }

    /** Σ CPU ÷ Σ cuadros analizados (no el promedio de cocientes, que pesa igual un segundo con 2 cuadros que con 30). */
    private fun cpuPorCuadro(filas: List<MuestraRecursos>): Double {
        var cpu = 0L
        var cuadros = 0L
        for (f in filas) if (f.cpuMs >= 0 && f.analizadosDelta > 0) {
            cpu += f.cpuMs
            cuadros += f.analizadosDelta
        }
        return if (cuadros == 0L) Double.NaN else cpu.toDouble() / cuadros
    }
}

/** Los CSV de las pruebas de estrés: los datos de cada una y el resumen con sus criterios. */
object CsvEstres {
    /** Al final de cada CSV: con qué motor y rango de cuadros por segundo corrió (para comparar entre motores). */
    private val CONFIG = listOf("motor", "rango_fps")

    val COLUMNAS_CICLOS = listOf(
        "ciclo", "abrir_ms", "primer_cuadro_ms", "cerrar_ms", "error", "heap_java_kb", "heap_nativo_kb", "pss_kb", "rss_kb",
        "hilos", "fds",
    ) + CONFIG
    val COLUMNAS_RAFAGA = listOf("segundo", "ordenes_zoom", "ordenes_linterna", "cuadros", "fase") + CONFIG
    val COLUMNAS_MOTOR = listOf("cambio", "motor", "ms", "error", "heap_java_kb", "heap_nativo_kb", "pss_kb", "rss_kb", "hilos", "fds", "rango_fps")
    val COLUMNAS_MINUTOS = listOf("minuto", "fps_analisis", "cpu_ms_por_cuadro", "cpu_pct_nucleo", "temp_bat_c", "termico_max", "headroom") + CONFIG
    val COLUMNAS_RESUMEN = listOf("prueba", "criterio", "valor", "limite", "pasa", "dispositivo", "sdk", "version_app", "commit") + CONFIG

    private fun config(c: ConfigEstres?): List<String> = if (c == null) listOf("", "") else listOf(c.motor.name, c.textoFps)

    fun ciclos(ciclos: List<Ciclo>, memoria: List<PuntoMemoria>, c: ConfigEstres? = null): String {
        val porCiclo = memoria.associateBy { it.en }
        return tabla(COLUMNAS_CICLOS, ciclos.map { ci ->
            listOf(ci.n.toString(), ms(ci.abrirMs), ms(ci.primerCuadroMs), ms(ci.cerrarMs), ci.error ?: "") + memoriaDe(porCiclo[ci.n]) + config(c)
        })
    }

    fun rafaga(segundos: List<SegundoRafaga>, c: ConfigEstres? = null): String = tabla(COLUMNAS_RAFAGA, segundos.map {
        listOf(it.segundo.toString(), it.ordenesZoom.toString(), it.ordenesLinterna.toString(), it.cuadros.toString(), it.fase) + config(c)
    })

    fun motor(cambios: List<CambioMotor>, memoria: List<PuntoMemoria>, c: ConfigEstres? = null): String {
        val porCambio = memoria.associateBy { it.en }
        return tabla(COLUMNAS_MOTOR, cambios.map { ca ->
            listOf(ca.n.toString(), ca.motor, ms(ca.ms), ca.error ?: "") + memoriaDe(porCambio[ca.n]) + (c?.textoFps ?: "")
        })
    }

    fun minutos(m: List<MinutoContinuo>, c: ConfigEstres? = null): String = tabla(COLUMNAS_MINUTOS, m.map {
        listOf(
            it.minuto.toString(), ms(it.fpsAnalisis), ms(it.cpuMsPorCuadro, 2), ms(it.cpuPct), ms(it.tempBatC),
            if (it.termicoMaximo >= 0) it.termicoMaximo.toString() else "", ms(it.headroom, 3),
        ) + config(c)
    })

    fun resumen(prueba: String, dictamen: Dictamen, equipo: mx.papeleriapos.android.sesion.DatosEquipo, c: ConfigEstres? = null): String =
        tabla(COLUMNAS_RESUMEN, dictamen.criterios.map {
            listOf(prueba, it.nombre, Csv.textoSeguro(it.valor), Csv.textoSeguro(it.limite), if (it.pasa) "si" else "no",
                Csv.textoSeguro(equipo.dispositivo), equipo.sdk.toString(), equipo.versionApp, equipo.commit) + config(c)
        })

    private fun memoriaDe(p: PuntoMemoria?): List<String> =
        if (p == null) List(6) { "" }
        else listOf(p.heapJavaKb, p.heapNativoKb, p.pssKb, p.rssKb, p.hilos.toLong(), p.fds.toLong()).map { if (it >= 0) it.toString() else "" }

    private fun ms(v: Double, decimales: Int = 1) = if (v.isNaN()) "" else Csv.decimal(v, decimales)

    private fun tabla(columnas: List<String>, filas: List<List<String>>): String {
        val sb = StringBuilder(Csv.BOM)
        Csv.renglon(columnas, sb)
        for (f in filas) Csv.renglon(f, sb)
        return sb.toString()
    }
}
