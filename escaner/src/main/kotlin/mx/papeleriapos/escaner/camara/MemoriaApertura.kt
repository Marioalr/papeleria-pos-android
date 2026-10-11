package mx.papeleriapos.escaner.camara

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

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

    fun rangoFps(fijo30: Boolean): RangoEnCache = decodificarRango(prefs.getString(llaveFps(fijo30), null), huella())

    fun guardarRangoFps(fijo30: Boolean, rango: Pair<Int, Int>?) = guardar(fijo30, codificarRango(huella(), rango))

    /** La sesión rechazó el FPS fijo (STREAM_CONFIG): sin rango con este sistema, aunque la cámara lo anuncie. */
    fun guardarAutoPorError(fijo30: Boolean) = guardar(fijo30, codificarRango(huella(), null, porError = true))

    private fun guardar(fijo30: Boolean, texto: String) {
        if (prefs.getString(llaveFps(fijo30), null) == texto) return
        prefs.edit().putString(llaveFps(fijo30), texto).apply()
    }

    fun olvidarRangoFps(fijo30: Boolean) {
        prefs.edit().remove(llaveFps(fijo30)).apply()
    }

    private fun llaveFps(fijo30: Boolean) = if (fijo30) FPS_30 else FPS_15

    private fun huella(): String = Build.FINGERPRINT ?: ""

    private companion object {
        const val VISOR = "visor_"
        const val FPS_30 = "fps_30"
        const val FPS_15 = "fps_15_30"
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
