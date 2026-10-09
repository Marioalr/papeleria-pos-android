package mx.papeleriapos.android.rapido

import android.content.Context

/**
 * Milisegundos que le quedan al escaneo rápido antes de cerrarse solo: mientras siga arriba, cada vez que se enciende la
 * pantalla aparece sobre el bloqueo y abre la cámara. ≤ 0: ya caducó. [minutos] ≤ 0: nunca se cierra solo.
 */
fun msParaCerrar(ultimaActividadMs: Long, ahoraMs: Long, minutos: Int): Long {
    if (minutos <= 0) return Long.MAX_VALUE
    return minutos * MS_POR_MINUTO - (ahoraMs - ultimaActividadMs).coerceAtLeast(0L)
}

private const val MS_POR_MINUTO = 60_000L

/** Los minutos sin escanear tras los que el escaneo rápido se cierra solo (5 si nadie lo cambió). */
fun minutosParaCerrar(context: Context): Int =
    context.getSharedPreferences(ARCHIVO_RAPIDO, Context.MODE_PRIVATE).getInt(MINUTOS_PARA_CERRAR, MINUTOS_PARA_CERRAR_POR_OMISION)

/** Para los ajustes: 0 = nunca se cierra solo. */
fun guardarMinutosParaCerrar(context: Context, minutos: Int) {
    context.getSharedPreferences(ARCHIVO_RAPIDO, Context.MODE_PRIVATE).edit()
        .putInt(MINUTOS_PARA_CERRAR, minutos.coerceAtLeast(0))
        .apply()
}

const val MINUTOS_PARA_CERRAR_POR_OMISION = 5
private const val ARCHIVO_RAPIDO = "escaneo_rapido"
private const val MINUTOS_PARA_CERRAR = "minutos_para_cerrar"
