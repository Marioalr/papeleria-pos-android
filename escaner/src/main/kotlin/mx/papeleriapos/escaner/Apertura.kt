package mx.papeleriapos.escaner

import android.os.SystemClock

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
 */
object MarcasApertura {
    @Volatile private var actual = Apertura()

    val apertura: Apertura get() = actual

    @Synchronized
    fun nueva(tipo: TipoArranque, conservar: Set<PasoApertura> = emptySet()) {
        actual = Apertura(tipo, actual.marcas.filterKeys { it in conservar })
    }

    /** Pone [paso] con la hora [nanos] si todavía no estaba en esta apertura. Devuelve si la puso. */
    @Synchronized
    fun marcar(paso: PasoApertura, nanos: Long = SystemClock.elapsedRealtimeNanos()): Boolean {
        val a = actual
        if ((a.marcas[paso] ?: 0L) > 0L || nanos <= 0L) return false
        actual = a.copy(marcas = a.marcas + (paso to nanos))
        return true
    }

    @Synchronized
    fun tipo(tipo: TipoArranque) {
        actual = actual.copy(tipo = tipo)
    }
}
