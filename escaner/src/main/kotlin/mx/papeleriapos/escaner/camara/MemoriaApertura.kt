package mx.papeleriapos.escaner.camara

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import mx.papeleriapos.escaner.Lente

/**
 * Lo que la apertura recuerda de la vez anterior para no preguntárselo a la cámara ni esperar a Compose: la proporción
 * del visor de cada pantalla (para enlazar en `onCreate`) y el rango de FPS que aceptó la sesión (para no llamar a
 * `getSupportedFrameRateRanges` en el camino crítico). Se escribe con `apply()` (en segundo plano).
 */
internal class MemoriaApertura(contexto: Context) {
    private val app = contexto.applicationContext
    // Perezoso: getSharedPreferences lee el archivo en otro hilo; EscanerApp ya lo pidió al arrancar el proceso.
    private val prefs: SharedPreferences by lazy { app.getSharedPreferences(ARCHIVO_APERTURA, Context.MODE_PRIVATE) }

    fun proporcion(pantalla: String): Proporcion? = Proporcion.de(prefs.getString(VISOR + pantalla, null))

    fun guardarProporcion(pantalla: String, p: Proporcion) {
        if (!p.valida || proporcion(pantalla) == p) return
        prefs.edit().putString(VISOR + pantalla, p.texto()).apply()
    }

    fun rangoFps(pref: PreferenciaFps): RangoEnCache = decodificarRango(prefs.getString(pref.llave, null), huella())

    fun guardarRangoFps(pref: PreferenciaFps, rango: Pair<Int, Int>?) = guardar(pref, codificarRango(huella(), rango))

    /** La sesión rechazó el FPS fijo (STREAM_CONFIG): sin rango con este sistema, aunque la cámara lo anuncie. */
    fun guardarAutoPorError(pref: PreferenciaFps) = guardar(pref, codificarRango(huella(), null, porError = true))

    private fun guardar(pref: PreferenciaFps, texto: String) {
        if (prefs.getString(pref.llave, null) == texto) return
        prefs.edit().putString(pref.llave, texto).apply()
    }

    fun olvidarRangoFps(pref: PreferenciaFps) {
        prefs.edit().remove(pref.llave).apply()
    }

    /**
     * La cámara física que se eligió para [lente] en este sistema: el id, «» si no hay física (se usa la lógica), o null
     * si no se ha calculado (o cambió el sistema).
     */
    fun idFisico(lente: Lente): String? {
        val texto = prefs.getString(LENTE + lente.name, null) ?: return null
        val partes = texto.split('|', limit = 2)
        return if (partes.size == 2 && partes[0] == huella()) partes[1] else null
    }

    fun guardarIdFisico(lente: Lente, id: String?) {
        val texto = huella() + "|" + (id ?: "")
        if (prefs.getString(LENTE + lente.name, null) != texto) prefs.edit().putString(LENTE + lente.name, texto).apply()
    }

    private fun huella(): String = Build.FINGERPRINT ?: ""

    private companion object {
        const val VISOR = "visor_"
        const val LENTE = "lente_"
    }
}

/** El archivo de [MemoriaApertura]. */
const val ARCHIVO_APERTURA = "escaner_apertura"

/** Empieza a leer el archivo de la apertura en otro hilo (lo hace `getSharedPreferences`): llamarlo al crear el proceso. */
fun precargarMemoriaApertura(contexto: Context) {
    contexto.getSharedPreferences(ARCHIVO_APERTURA, Context.MODE_PRIVATE)
}

/** La proporción del visor que se midió la última vez en [pantalla] (null la primera vez). */
fun proporcionGuardada(contexto: Context, pantalla: String): Proporcion? = MemoriaApertura(contexto).proporcion(pantalla)
