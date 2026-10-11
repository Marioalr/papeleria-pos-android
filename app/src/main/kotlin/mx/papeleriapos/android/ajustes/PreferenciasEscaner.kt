package mx.papeleriapos.android.ajustes

import android.content.SharedPreferences
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.Lente
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.ResolucionAnalisis
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.nucleo.Simbologia

/**
 * Guarda los ajustes del escáner en SharedPreferences. Lo que no se ajusta en la pantalla se queda con el valor por
 * omisión de [ConfigEscaner]; un valor guardado que ya no existe (p. ej. un motor que se quitó) también.
 */
class PreferenciasEscaner(private val prefs: SharedPreferences) {
    fun leer(): ConfigEscaner {
        val d = ConfigEscaner()
        return d.copy(
            motor = enumO(prefs.getString(MOTOR, null), d.motor),
            modo = enumO(prefs.getString(MODO, null), d.modo),
            simbologias = prefs.getStringSet(SIMBOLOGIAS, null)
                ?.mapNotNullTo(HashSet()) { runCatching { Simbologia.valueOf(it) }.getOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: d.simbologias,
            sonido = prefs.getBoolean(SONIDO, d.sonido),
            vibracion = prefs.getBoolean(VIBRACION, d.vibracion),
            linternaAutomatica = prefs.getBoolean(LINTERNA_AUTOMATICA, d.linternaAutomatica),
            recortarFranja = prefs.getBoolean(RECORTAR_FRANJA, d.recortarFranja),
            fpsFijo = prefs.getBoolean(FPS_FIJO, d.fpsFijo),
            fps60 = prefs.getBoolean(FPS_60, d.fps60),
            lente = prefs.getString(LENTE, null)?.let { runCatching { Lente.valueOf(it) }.getOrNull() } ?: d.lente,
            resolucion = enumO(prefs.getString(RESOLUCION, null), d.resolucion),
            leerGirados = prefs.getBoolean(LEER_GIRADOS, d.leerGirados),
            medirLuzEnMira = prefs.getBoolean(LUZ_EN_MIRA, d.medirLuzEnMira),
            segundosParaPausar = prefs.getInt(SEGUNDOS_PARA_PAUSAR, d.segundosParaPausar).coerceIn(0, MAX_SEGUNDOS_PAUSA),
            mostrarDiagnostico = prefs.getBoolean(MOSTRAR_DIAGNOSTICO, d.mostrarDiagnostico),
            altoFranja = prefs.getFloat(ALTO_FRANJA, d.altoFranja).coerceIn(MIN_ALTO_FRANJA, MAX_ALTO_FRANJA),
        )
    }

    fun guardar(c: ConfigEscaner) {
        prefs.edit()
            .putString(MOTOR, c.motor.name)
            .putString(MODO, c.modo.name)
            .putStringSet(SIMBOLOGIAS, c.simbologias.mapTo(HashSet()) { it.name })
            .putBoolean(SONIDO, c.sonido)
            .putBoolean(VIBRACION, c.vibracion)
            .putBoolean(LINTERNA_AUTOMATICA, c.linternaAutomatica)
            .putBoolean(RECORTAR_FRANJA, c.recortarFranja)
            .putBoolean(FPS_FIJO, c.fpsFijo)
            .putBoolean(FPS_60, c.fps60)
            .putString(LENTE, c.lente.name)
            .putString(RESOLUCION, c.resolucion.name)
            .putBoolean(LEER_GIRADOS, c.leerGirados)
            .putBoolean(LUZ_EN_MIRA, c.medirLuzEnMira)
            .putInt(SEGUNDOS_PARA_PAUSAR, c.segundosParaPausar)
            .putBoolean(MOSTRAR_DIAGNOSTICO, c.mostrarDiagnostico)
            .putFloat(ALTO_FRANJA, c.altoFranja)
            .apply()
    }

    fun borrar() {
        prefs.edit().clear().apply()
    }

    private inline fun <reified E : Enum<E>> enumO(nombre: String?, porOmision: E): E =
        nombre?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: porOmision

    companion object {
        const val ARCHIVO = "escaner"
        const val MIN_ALTO_FRANJA = 0.20f
        const val MAX_ALTO_FRANJA = 0.60f
        const val MAX_SEGUNDOS_PAUSA = 120

        /** Las simbologías que se ofrecen: las de la tienda (docs/escaner.md). Nunca «todas»: Code 39 y Codabar leen mal. */
        val OFRECIDAS = listOf(
            Simbologia.EAN_13, Simbologia.UPC_A, Simbologia.EAN_8, Simbologia.UPC_E, Simbologia.ITF,
            Simbologia.CODE_128, Simbologia.QR,
        )

        private const val MOTOR = "motor"
        private const val MODO = "modo"
        private const val SIMBOLOGIAS = "simbologias"
        // Llave nueva desde que el bip viene apagado: lo guardado con la anterior («sonido») era el valor por omisión
        // de entonces, no algo que alguien eligió.
        private const val SONIDO = "sonido_al_leer"
        private const val VIBRACION = "vibracion"
        private const val LINTERNA_AUTOMATICA = "linterna_automatica"
        private const val RECORTAR_FRANJA = "recortar_franja"
        // La llave de cuando el tope era 30: lo que eligió cada quien se respeta.
        private const val FPS_FIJO = "fps_fijo_30"
        private const val FPS_60 = "fps_60"
        private const val LENTE = "lente"
        private const val RESOLUCION = "resolucion"
        private const val LEER_GIRADOS = "leer_girados"
        private const val LUZ_EN_MIRA = "luz_en_mira"
        private const val SEGUNDOS_PARA_PAUSAR = "segundos_para_pausar"
        private const val MOSTRAR_DIAGNOSTICO = "mostrar_diagnostico"
        private const val ALTO_FRANJA = "alto_franja"
    }
}

/** Para mostrar el modo y el motor en la interfaz. */
val Modo.etiqueta: String get() = when (this) {
    Modo.VENTA -> "Venta"
    Modo.CONTEO -> "Conteo"
}

val Motor.etiqueta: String get() = nombre
