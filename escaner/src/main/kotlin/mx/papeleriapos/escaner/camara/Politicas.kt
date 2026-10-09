package mx.papeleriapos.escaner.camara

import androidx.camera.core.CameraState
import kotlin.math.abs

/*
 * Las decisiones de la apertura y de la recuperación de la cámara, como funciones puras: se prueban en la JVM
 * (escaner/src/test) y el controlador solo las aplica.
 */

/** El tamaño del visor en píxeles: el `ViewPort` solo usa su proporción (ancho / alto). */
data class Proporcion(val ancho: Int, val alto: Int) {
    val valida: Boolean get() = ancho > 0 && alto > 0
    val valor: Float get() = if (alto > 0) ancho.toFloat() / alto else 0f

    /** «1080x2208», como se guarda en las preferencias. */
    fun texto(): String = "${ancho}x$alto"

    companion object {
        fun de(texto: String?): Proporcion? {
            if (texto == null) return null
            val i = texto.indexOf('x')
            if (i <= 0) return null
            val p = Proporcion(texto.substring(0, i).toIntOrNull() ?: return null, texto.substring(i + 1).toIntOrNull() ?: return null)
            return p.takeIf { it.valida }
        }
    }
}

/**
 * Dos proporciones que dan casi el mismo recorte: menos de [tolerancia] relativa (0.5 % ≈ 10 px de alto en un visor de
 * 2000 px) no vale volver a enlazar, que cuesta cerrar y abrir la cámara.
 */
fun mismaProporcion(a: Proporcion, b: Proporcion, tolerancia: Float = TOLERANCIA_PROPORCION): Boolean {
    if (!a.valida || !b.valida) return false
    return abs(a.valor - b.valor) <= tolerancia * b.valor
}

const val TOLERANCIA_PROPORCION = 0.005f

/**
 * La proporción con que se enlaza en `onCreate`, antes de que Compose mida el visor: la que midió la vez anterior o,
 * la primera vez, la ventana menos las barras del sistema y lo que la pantalla ponga abajo (p. ej. la barra de
 * pestañas). Null si no hay con qué estimarla.
 */
fun proporcionInicial(
    guardada: Proporcion?,
    anchoVentana: Int,
    altoVentana: Int,
    barraArriba: Int,
    barraAbajo: Int,
    descuentoAbajo: Int = 0,
): Proporcion? {
    if (guardada != null && guardada.valida) return guardada
    val estimada = Proporcion(anchoVentana, altoVentana - barraArriba.coerceAtLeast(0) - barraAbajo.coerceAtLeast(0) - descuentoAbajo.coerceAtLeast(0))
    return estimada.takeIf { it.valida }
}

/** Qué hacer cuando la pantalla pide abrir con un tamaño (o al dar el permiso). */
internal enum class DecisionEnlace {
    /** Ya está enlazada (o enlazándose) con lo mismo: no se toca. */
    NADA,
    /** No hay enlace o hay que rehacerlo ya (otra rotación, reintento explícito). */
    AHORA,
    /**
     * Ya hay enlace pero la proporción cambió: se espera un momento por si el tamaño sigue cambiando (al arrancar Compose
     * mide primero sin las barras y luego con ellas) y se enlaza una sola vez con el último.
     */
    DESPUES,
}

internal fun decidirEnlace(
    hayEnlace: Boolean,
    enlazada: Proporcion?,
    rotacionEnlazada: Int,
    pedida: Proporcion,
    rotacionPedida: Int,
    forzar: Boolean,
): DecisionEnlace = when {
    forzar || !hayEnlace || enlazada == null -> DecisionEnlace.AHORA
    rotacionEnlazada != rotacionPedida -> DecisionEnlace.AHORA
    mismaProporcion(enlazada, pedida) -> DecisionEnlace.NADA
    else -> DecisionEnlace.DESPUES
}

/** Lo que se sabe del rango de FPS de este celular sin preguntarle a la cámara (que cuesta en el camino de la apertura). */
internal sealed interface RangoEnCache {
    /** No se ha guardado, o se guardó con otra versión del sistema: hay que calcularlo. */
    data object SinDato : RangoEnCache
    /** Se enlaza sin rango fijo (que decida CameraX). */
    data object Auto : RangoEnCache
    data class Fijo(val minimo: Int, val maximo: Int) : RangoEnCache
}

/**
 * Lo que se guarda por preferencia de FPS: «<huella>|30-30» o «<huella>|auto». La huella es `Build.FINGERPRINT`: si el
 * sistema se actualiza, los rangos de la cámara pueden cambiar y se vuelve a calcular.
 */
internal fun codificarRango(huella: String, rango: Pair<Int, Int>?): String =
    huella + SEPARADOR_HUELLA + if (rango == null) AUTO else "${rango.first}-${rango.second}"

internal fun decodificarRango(texto: String?, huella: String): RangoEnCache {
    if (texto == null) return RangoEnCache.SinDato
    val i = texto.lastIndexOf(SEPARADOR_HUELLA)
    if (i < 0 || texto.substring(0, i) != huella) return RangoEnCache.SinDato
    val valor = texto.substring(i + 1)
    if (valor == AUTO) return RangoEnCache.Auto
    val guion = valor.indexOf('-')
    if (guion <= 0) return RangoEnCache.SinDato
    val minimo = valor.substring(0, guion).toIntOrNull() ?: return RangoEnCache.SinDato
    val maximo = valor.substring(guion + 1).toIntOrNull() ?: return RangoEnCache.SinDato
    return if (minimo in 1..maximo) RangoEnCache.Fijo(minimo, maximo) else RangoEnCache.SinDato
}

private const val SEPARADOR_HUELLA = '|'
private const val AUTO = "auto"

/** Qué hace el controlador ante un cambio de `CameraState` (docs/escaner.md, «Cámara»). */
internal enum class AccionEstado {
    NINGUNA,
    /** Abrió: se limpia el error de cámara que hubiera y se reinician los reintentos. */
    ABIERTA,
    /** Fase ERROR con el mensaje, pero CameraX reintenta solo y al abrir se limpia. */
    AVISO_RECUPERABLE,
    /** Fase ERROR con el mensaje; solo «Reintentar» (o volver a la pantalla) vuelve a enlazar. */
    AVISO,
    /** La sesión no aceptó la configuración: otra vez sin rango de FPS fijo. */
    REENLAZAR_SIN_FPS,
    /** Error crítico: soltar y volver a enlazar tras [ReaccionEstado.esperaMs]. */
    REENLAZAR,
    ERROR_FINAL,
}

internal data class ReaccionEstado(val accion: AccionEstado, val mensaje: String? = null, val esperaMs: Long = 0L)

/**
 * La política ante el estado de la cámara. [abierta]: el tipo es OPEN; [codigoError]: `CameraState.ERROR_*` (0 = sin
 * error); [reintentosHechos]: cuántas veces se ha vuelto a enlazar por un error crítico desde que abrió bien;
 * [fpsFijo]: el enlace actual lleva un rango de FPS fijo.
 */
internal fun reaccionEstado(abierta: Boolean, codigoError: Int, reintentosHechos: Int, fpsFijo: Boolean): ReaccionEstado = when {
    abierta -> ReaccionEstado(AccionEstado.ABIERTA)
    codigoError == 0 -> ReaccionEstado(AccionEstado.NINGUNA)
    codigoError == CameraState.ERROR_CAMERA_IN_USE || codigoError == CameraState.ERROR_MAX_CAMERAS_IN_USE ->
        ReaccionEstado(AccionEstado.AVISO_RECUPERABLE, MENSAJE_EN_USO)
    // CameraX vuelve a intentar abrir solo; no se avisa para no hacer ruido por un tropiezo.
    codigoError == CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> ReaccionEstado(AccionEstado.NINGUNA)
    codigoError == CameraState.ERROR_CAMERA_DISABLED -> ReaccionEstado(AccionEstado.AVISO, MENSAJE_DESACTIVADA)
    codigoError == CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> ReaccionEstado(AccionEstado.AVISO, MENSAJE_NO_MOLESTAR)
    codigoError == CameraState.ERROR_STREAM_CONFIG && fpsFijo -> ReaccionEstado(AccionEstado.REENLAZAR_SIN_FPS)
    reintentosHechos < MAX_REINTENTOS_CRITICOS ->
        ReaccionEstado(AccionEstado.REENLAZAR, esperaMs = ESPERA_REINTENTO_MS * (reintentosHechos + 1))
    else -> ReaccionEstado(AccionEstado.ERROR_FINAL, MENSAJE_FALLO)
}

internal const val MAX_REINTENTOS_CRITICOS = 2
internal const val ESPERA_REINTENTO_MS = 500L
internal const val MENSAJE_EN_USO = "Otra app está usando la cámara"
internal const val MENSAJE_DESACTIVADA = "La cámara está desactivada en este celular (privacidad o política). Actívala y toca Reintentar."
internal const val MENSAJE_NO_MOLESTAR = "Desactiva No molestar y toca Reintentar"
internal const val MENSAJE_FALLO = "La cámara falló. Si sigue, reinicia el celular."

/** El texto de `Diagnostico.estadoCamara`: «OPEN» o «OPENING · error 2 CAMERA_IN_USE». */
internal fun textoEstadoCamara(tipo: String, codigoError: Int): String =
    if (codigoError == 0) tipo else "$tipo · error $codigoError ${nombreError(codigoError)}"

internal fun nombreError(codigo: Int): String = when (codigo) {
    CameraState.ERROR_MAX_CAMERAS_IN_USE -> "MAX_CAMERAS_IN_USE"
    CameraState.ERROR_CAMERA_IN_USE -> "CAMERA_IN_USE"
    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> "OTHER_RECOVERABLE"
    CameraState.ERROR_STREAM_CONFIG -> "STREAM_CONFIG"
    CameraState.ERROR_CAMERA_DISABLED -> "CAMERA_DISABLED"
    CameraState.ERROR_CAMERA_FATAL_ERROR -> "CAMERA_FATAL"
    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "DO_NOT_DISTURB"
    CameraState.ERROR_CAMERA_REMOVED -> "CAMERA_REMOVED"
    else -> "DESCONOCIDO"
}

/**
 * El vigilante: con la pantalla al frente ([resumida]) y la cámara enlazada, si en [msSinCuadros] no salió ningún cuadro
 * del sensor, se vuelve a enlazar una vez por apertura. No aplica en pausa ni si CameraX ya está reintentando por su
 * cuenta (otra app tiene la cámara).
 */
internal fun vigilanteDebeReenlazar(
    resumida: Boolean,
    enlazada: Boolean,
    cuadrosNuevos: Long,
    msSinCuadros: Long,
    yaReenlazo: Boolean,
    enPausa: Boolean,
    errorRecuperable: Boolean,
    plazoMs: Long = PLAZO_VIGILANTE_MS,
): Boolean = resumida && enlazada && cuadrosNuevos == 0L && msSinCuadros >= plazoMs && !yaReenlazo && !enPausa &&
    !errorRecuperable

internal const val PLAZO_VIGILANTE_MS = 2_000L
