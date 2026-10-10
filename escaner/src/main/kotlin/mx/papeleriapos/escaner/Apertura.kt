package mx.papeleriapos.escaner

import android.annotation.SuppressLint
import android.app.ApplicationStartInfo
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
 * Cada marca se pone una sola vez por apertura; [nueva] empieza otra (al volver de segundo plano) con otro [numero].
 *
 * Puede haber dos controladores vivos (el de la app y el del escaneo rápido): cada uno marca solo en la apertura que
 * es suya ([marcarSi] con el número que tomó al empezar), así los cuadros en vuelo del otro no se mezclan.
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

    /** El número de la apertura en curso: cambia con cada [nueva] (0 = todavía ninguna). */
    @Volatile var numero = 0
        private set

    @Synchronized
    fun nueva(tipo: TipoArranque, conservar: Set<PasoApertura> = emptySet()) {
        actual = Apertura(tipo, actual.marcas.filterKeys { it in conservar })
        numero++
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

    /** Como [marcar], pero solo si [apertura] (el [numero] que tomó quien marca) sigue siendo la apertura en curso. */
    @Synchronized
    fun marcarSi(apertura: Int, paso: PasoApertura, nanos: Long = SystemClock.elapsedRealtimeNanos()): Boolean =
        apertura != 0 && apertura == numero && marcar(paso, nanos)

    /** Si [apertura] es la apertura en curso. Sin candado: se puede preguntar en cada cuadro. */
    fun esActual(apertura: Int): Boolean = apertura != 0 && apertura == numero

    /**
     * Si [paso] ya está en esta apertura. Sin candado ni asignaciones: se pregunta en cada cuadro (la cámara y el
     * análisis) antes de intentar [marcar].
     */
    fun tiene(paso: PasoApertura): Boolean = actual.marcas.containsKey(paso)

    /** Si [paso] ya está en la apertura [apertura] y esta sigue en curso (la app espera el primer análisis de la suya). */
    fun tieneEn(apertura: Int, paso: PasoApertura): Boolean = esActual(apertura) && tiene(paso)

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

/** Por qué nació el proceso, según el registro de arranques del sistema (`ApplicationStartInfo.getReason`, API 35+). */
enum class OrigenProceso {
    /** Para abrir una Activity (lanzador, recientes, un atajo o `am start`): su primera Activity es un arranque en frío. */
    ACTIVIDAD,
    /**
     * Para otra cosa (el mosaico de Ajustes rápidos al abrir el panel, un aviso, un trabajo): cuando llega la Activity
     * el proceso ya vivía, y la apertura es tibia.
     */
    OTRO,
    /** No se sabe (API < 35, o el sistema no respondió a tiempo): se decide por el tiempo desde `Application.onCreate`. */
    NO_SE,
}

/** El origen del proceso a partir de `ApplicationStartInfo.getReason()`. */
@SuppressLint("InlinedApi")
fun origenDeRazon(razon: Int): OrigenProceso = when (razon) {
    ApplicationStartInfo.START_REASON_LAUNCHER,
    ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS,
    ApplicationStartInfo.START_REASON_START_ACTIVITY,
    -> OrigenProceso.ACTIVIDAD
    else -> OrigenProceso.OTRO
}

/**
 * El tipo de arranque visto desde el `onCreate` de una Activity del escáner. Si no es la primera Activity del proceso,
 * tibio. Si lo es, manda el sistema ([origen], API 35+): frío solo si el proceso nació para abrir una Activity. Sin ese
 * dato (API < 35), frío si entre `Application.onCreate` y la Activity pasó a lo más [umbralNanos]: un frío lento (gama
 * baja sin el perfil compilado) sigue contando como frío, y un proceso que arrancó mucho antes (el mosaico al abrir el
 * panel, un servicio o un aviso) cuenta como tibio. Con el mosaico tocado en menos de [umbralNanos] no hay forma de
 * saberlo antes de API 35 y cuenta como frío.
 *
 * @param nanosAplicacion la marca de [PasoApertura.APLICACION] (0 = no se marcó).
 */
fun tipoDeArranque(
    primeraActividad: Boolean,
    origen: OrigenProceso,
    nanosAplicacion: Long,
    nanosActividad: Long,
    umbralNanos: Long = UMBRAL_PROCESO_PRECALENTADO_NS,
): TipoArranque = when {
    !primeraActividad -> TipoArranque.TIBIO
    origen == OrigenProceso.ACTIVIDAD -> TipoArranque.FRIO
    origen == OrigenProceso.OTRO -> TipoArranque.TIBIO
    nanosAplicacion <= 0L || nanosActividad <= 0L -> TipoArranque.DESCONOCIDO
    nanosActividad - nanosAplicacion > umbralNanos -> TipoArranque.TIBIO
    else -> TipoArranque.FRIO
}

/**
 * Sin el registro del sistema: en un arranque en frío la Activity se crea decenas o cientos de ms después de la
 * Application, y en gama baja sin el perfil compilado puede pasar del segundo; más de 5 s ya no es el mismo arranque.
 */
const val UMBRAL_PROCESO_PRECALENTADO_NS = 5_000_000_000L
