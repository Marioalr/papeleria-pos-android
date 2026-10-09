package mx.papeleriapos.android.rapido

import android.content.SharedPreferences
import mx.papeleriapos.escaner.Fase

/**
 * Milisegundos que le quedan al escaneo rápido antes de cerrarse solo: mientras siga arriba, cada vez que se enciende la
 * pantalla aparece sobre el bloqueo y abre la cámara. ≤ 0: ya caducó. [minutos] ≤ 0: nunca se cierra solo (salvo con
 * [bloqueado]).
 *
 * Con el celular [bloqueado] el plazo no pasa de [MS_PARA_CERRAR_CON_BLOQUEO]: lo que queda sobre el bloqueo lo ve
 * cualquiera que tome el celular, y la pantalla encendida gasta batería.
 */
fun msParaCerrar(ultimaActividadMs: Long, ahoraMs: Long, minutos: Int, bloqueado: Boolean = false): Long {
    val normal = if (minutos <= 0) Long.MAX_VALUE else minutos * MS_POR_MINUTO
    val plazo = if (bloqueado) minOf(normal, MS_PARA_CERRAR_CON_BLOQUEO) else normal
    if (plazo == Long.MAX_VALUE) return Long.MAX_VALUE
    return plazo - (ahoraMs - ultimaActividadMs).coerceAtLeast(0L)
}

/**
 * La cámara se apagó por inactividad (en pausa y sin superficie, después de haber tenido una): el escaneo rápido ya no
 * tiene nada que hacer y se cierra en lugar de quedarse con la lista a la vista.
 */
fun camaraApagadaPorInactividad(fase: Fase, haySuperficie: Boolean, huboSuperficie: Boolean): Boolean =
    fase == Fase.PAUSADO && !haySuperficie && huboSuperficie

private const val MS_POR_MINUTO = 60_000L

/** Sin escanear con el celular bloqueado, a lo más esto antes de cerrarse (un minuto). */
const val MS_PARA_CERRAR_CON_BLOQUEO = 60_000L

/**
 * Los minutos sin escanear tras los que el escaneo rápido se cierra solo (5 si nadie lo cambió). Se guardan en el mismo
 * archivo que los ajustes del escáner (`PreferenciasEscaner.ARCHIVO`), que la app empieza a leer en otro hilo al crear
 * el proceso: así la apertura rápida no lee el disco en el hilo principal.
 */
fun minutosParaCerrar(prefs: SharedPreferences): Int = prefs.getInt(MINUTOS_PARA_CERRAR, MINUTOS_PARA_CERRAR_POR_OMISION)

/** Para los ajustes: 0 = nunca se cierra solo (con el celular bloqueado igual se cierra al minuto). */
fun guardarMinutosParaCerrar(prefs: SharedPreferences, minutos: Int) {
    prefs.edit().putInt(MINUTOS_PARA_CERRAR, minutos.coerceAtLeast(0)).apply()
}

const val MINUTOS_PARA_CERRAR_POR_OMISION = 5
private const val MINUTOS_PARA_CERRAR = "rapido_minutos_para_cerrar"
