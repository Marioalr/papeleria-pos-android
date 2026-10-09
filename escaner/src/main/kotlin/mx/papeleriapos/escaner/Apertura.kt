package mx.papeleriapos.escaner

import android.os.Build
import android.os.SystemClock
import android.os.Trace

/** Cómo arrancó esta apertura del escáner (docs/escaner.md, «Apertura»). */
enum class TipoArranque {
    /** El proceso no existía: lo creó el toque. */
    FRIO,
    /** El proceso vivía pero la Activity se creó de nuevo. */
    TIBIO,
    /** La Activity ya existía: volvió de segundo plano o de la pantalla apagada. */
    CALIENTE,
    DESCONOCIDO,
}

/** Los pasos de la apertura que se marcan, en el orden en que deben ocurrir. */
enum class PasoApertura {
    PROCESO, APLICACION, ACTIVIDAD, PRIMER_CUADRO_UI, ENLACE_INICIO, ENLACE_FIN, CAMARA_ABIERTA, PRIMER_CUADRO_SENSOR,
    PRIMER_ANALISIS, PRIMERA_LECTURA,
}

/**
 * Las marcas de una apertura, en `SystemClock.elapsedRealtimeNanos()` (0 = no ocurrió). Se miden desde el primer paso
 * que haya: en frío desde que nació el proceso, en tibio desde la Activity, en caliente desde que volvió a verse.
 */
data class Apertura(
    val tipo: TipoArranque = TipoArranque.DESCONOCIDO,
    val marcas: Map<PasoApertura, Long> = emptyMap(),
) {
    val inicio: Long get() = marcas.values.filter { it > 0L }.minOrNull() ?: 0L

    /** Milisegundos desde el inicio de la apertura hasta [paso]; −1 si no ocurrió. */
    fun ms(paso: PasoApertura): Long {
        val t = marcas[paso] ?: return -1L
        val i = inicio
        return if (t <= 0L || i <= 0L) -1L else (t - i) / 1_000_000L
    }

    /** «frío: actividad 120 · enlace 180–420 · cámara 510 · primer análisis 640 · lectura 900» para el registro. */
    fun resumen(): String = buildString {
        append(tipo.name.lowercase())
        append(':')
        for (p in PasoApertura.entries) {
            val ms = ms(p)
            if (ms >= 0) append(' ').append(p.name.lowercase()).append('=').append(ms)
        }
    }
}

/**
 * Las marcas de la apertura en curso, compartidas entre la app (proceso, Application, Activity, primer cuadro de la
 * interfaz) y el controlador de la cámara (enlace, cámara abierta, primer cuadro, primer análisis, primera lectura).
 * Cada marca se pone una sola vez por apertura; [nueva] empieza otra (al volver de segundo plano).
 *
 * Cada apertura abre además la sección de trace asíncrona [SECCION_HASTA_PRIMER_CUADRO] y la cierra con
 * [PasoApertura.PRIMER_ANALISIS]: así Macrobenchmark (`TraceSectionMetric`) mide también el arranque en caliente, donde
 * `reportFullyDrawn` ya no reporta.
 */
object MarcasApertura {
    /** La sección de trace de cada apertura: de su inicio al primer cuadro analizado (API 29+). */
    const val SECCION_HASTA_PRIMER_CUADRO = "escaner.hastaPrimerCuadro"

    @Volatile private var actual = Apertura()
    private var cookie = 0
    private var seccionAbierta = false

    val apertura: Apertura get() = actual

    @Synchronized
    fun nueva(tipo: TipoArranque, conservar: Set<PasoApertura> = emptySet()) {
        actual = Apertura(tipo, actual.marcas.filterKeys { it in conservar })
        cerrarSeccion()
        if (PasoApertura.PRIMER_ANALISIS !in conservar) abrirSeccion()
    }

    /** Pone [paso] con la hora [nanos] si todavía no estaba en esta apertura. Devuelve si la puso. */
    @Synchronized
    fun marcar(paso: PasoApertura, nanos: Long = SystemClock.elapsedRealtimeNanos()): Boolean {
        val a = actual
        if ((a.marcas[paso] ?: 0L) > 0L || nanos <= 0L) return false
        actual = a.copy(marcas = a.marcas + (paso to nanos))
        if (paso == PasoApertura.PRIMER_ANALISIS) cerrarSeccion()
        return true
    }

    /**
     * Si [paso] ya está en esta apertura. Sin candado ni asignaciones: se pregunta en cada cuadro (la cámara y el
     * análisis) antes de intentar [marcar].
     */
    fun tiene(paso: PasoApertura): Boolean = actual.marcas.containsKey(paso)

    @Synchronized
    fun tipo(tipo: TipoArranque) {
        actual = actual.copy(tipo = tipo)
    }

    private fun abrirSeccion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        cookie++
        Trace.beginAsyncSection(SECCION_HASTA_PRIMER_CUADRO, cookie)
        seccionAbierta = true
    }

    private fun cerrarSeccion() {
        if (!seccionAbierta || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        Trace.endAsyncSection(SECCION_HASTA_PRIMER_CUADRO, cookie)
        seccionAbierta = false
    }
}

/**
 * El tipo de arranque visto desde el `onCreate` de una Activity del escáner. Frío solo si es la primera Activity del
 * proceso y el proceso nació para ella: si entre `Application.onCreate` y la Activity pasó más de [umbralNanos], el
 * proceso ya estaba vivo (lo arrancó el mosaico de Ajustes rápidos al abrir el panel, un servicio o un aviso) y la
 * apertura cuenta como tibia, que es lo que de verdad le costó al usuario.
 *
 * @param nanosAplicacion la marca de [PasoApertura.APLICACION] (0 = no se marcó).
 */
fun tipoDeArranque(
    primeraActividad: Boolean,
    nanosAplicacion: Long,
    nanosActividad: Long,
    umbralNanos: Long = UMBRAL_PROCESO_PRECALENTADO_NS,
): TipoArranque = when {
    !primeraActividad -> TipoArranque.TIBIO
    nanosAplicacion <= 0L || nanosActividad <= 0L -> TipoArranque.DESCONOCIDO
    nanosActividad - nanosAplicacion > umbralNanos -> TipoArranque.TIBIO
    else -> TipoArranque.FRIO
}

/** En un arranque en frío la Activity se crea decenas o pocos cientos de ms después de la Application. */
const val UMBRAL_PROCESO_PRECALENTADO_NS = 1_000_000_000L
