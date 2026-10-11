package mx.papeleriapos.escaner.camara

import android.util.Log
import android.util.Range
import android.view.Surface
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.Lente
import mx.papeleriapos.escaner.ResolucionAnalisis
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Rotacion
import mx.papeleriapos.escaner.nucleo.Transformacion
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.tan

/**
 * La configuración de CameraX para la `Application` (`CameraXConfig.Provider`): solo la cámara trasera (no enumera ni
 * abre las demás al arrancar) y sin el ruido de los registros de CameraX.
 */
fun configuracionCameraX(): CameraXConfig =
    CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
        .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
        .setMinimumLoggingLevel(Log.ERROR)
        .build()

/** La franja de lectura en la región visible: casi todo el ancho y [alto] del alto, centrada. Pública: la app acomoda lo que va encima con la misma cuenta. */
fun franjaPara(alto: Float): Caja {
    val a = alto.coerceIn(0.1f, 1f)
    return Caja(0.04f, 0.5f - a / 2f, 0.96f, 0.5f + a / 2f)
}

/** `rotacionPantalla` puede llegar como `Surface.ROTATION_*` (0 a 3) o en grados; el `ViewPort` pide lo primero. */
internal fun rotacionSurface(rotacion: Int): Int = when (rotacion) {
    Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270 -> rotacion
    90 -> Surface.ROTATION_90
    180 -> Surface.ROTATION_180
    270 -> Surface.ROTATION_270
    else -> Surface.ROTATION_0
}

/**
 * Qué rango de cuadros por segundo se quiere: [fijo] (el mínimo más alto: exposición corta, menos movido) o variable
 * (desde 15: más luz, más movido), y el [tope] (30, o 60 donde la cámara los da, como el Pixel 8 Pro). Lleva también
 * la [resolucion] del análisis: los rangos que acepta la sesión dependen de los flujos que lleva. Es la llave con que
 * se guarda en la memoria de la apertura y se valida una vez por proceso.
 */
internal data class PreferenciaFps(val fijo: Boolean, val tope: Int, val resolucion: ResolucionAnalisis = ResolucionAnalisis.HD) {
    /** Las de 30 a 720p conservan las llaves de antes de que hubiera 60; otra resolución es otra llave. */
    val llave: String get() {
        val base = when {
            tope == 30 && fijo -> "fps_30"
            tope == 30 -> "fps_15_30"
            fijo -> "fps_$tope"
            else -> "fps_15_$tope"
        }
        return if (resolucion == ResolucionAnalisis.HD) base else base + "_" + resolucion.etiqueta
    }
}

internal val ConfigEscaner.preferenciaFps: PreferenciaFps get() = PreferenciaFps(fpsFijo, if (fps60) 60 else 30, resolucion)

/**
 * El rango de cuadros por segundo, de entre los que la cámara acepta con esta sesión (si no sale de ahí, el enlace
 * lanza IAE). Se usa el máximo más alto que no pase del tope ([60, 60] o [30, 30] fijos; [15, 60] o [15, 30]
 * variables); si la cámara no llega al tope, el más rápido que tenga. Null = que decida CameraX.
 */
internal fun elegirRangoFps(rangos: List<Pair<Int, Int>>, pref: PreferenciaFps): Pair<Int, Int>? {
    val validos = rangos.filter { it.first in 1..it.second && it.second <= pref.tope }
    val alto = validos.maxOfOrNull { it.second } ?: return null
    val conAlto = validos.filter { it.second == alto }
    return if (pref.fijo) {
        conAlto.maxByOrNull { it.first }
    } else {
        conAlto.firstOrNull { it.first == 15 }
            ?: conAlto.filter { it.first >= 15 }.minByOrNull { it.first }
            ?: conAlto.minByOrNull { it.first }
    }
}

internal fun elegirRangoFps(rangos: Set<Range<Int>>, pref: PreferenciaFps): Range<Int>? {
    val par = elegirRangoFps(rangos.map { it.lower to it.upper }, pref) ?: return null
    return rangos.first { it.lower == par.first && it.upper == par.second }
}

/** Una cámara física de la trasera lógica: su id de Camera2, su focal y el área de su sensor. */
internal data class LenteFisica(val id: String, val focalMm: Float, val areaSensorMm2: Float)

/**
 * El id de la cámara física para [lente], o null para usar la lógica (también si hay menos de dos físicas). PRINCIPAL:
 * de las que tienen la focal de la lógica (±0.5 mm; si no hay, todas), la de sensor más grande. En el Pixel 8 Pro hay
 * dos de 6.9 mm, la completa y un recorte del mismo sensor. GRAN_ANGULAR: la de focal más corta.
 */
internal fun elegirLenteFisica(fisicas: List<LenteFisica>, focalLogicaMm: Float?, lente: Lente): String? {
    if (lente == Lente.SISTEMA || fisicas.size < 2) return null
    return when (lente) {
        Lente.GRAN_ANGULAR -> fisicas.minByOrNull { it.focalMm }?.id
        else -> {
            val mismaFocal = if (focalLogicaMm == null) emptyList() else fisicas.filter { abs(it.focalMm - focalLogicaMm) <= 0.5f }
            mismaFocal.ifEmpty { fisicas }.maxByOrNull { it.areaSensorMm2 }?.id
        }
    }
}

/**
 * El zoom inicial a partir de la distancia mínima de enfoque (idea de AVCamBarcode, WWDC21): si para que un código de
 * [anchoCodigoMm] llene [llenado] del ancho visible habría que acercarse más de lo que el lente enfoca, se pone zoom para
 * que el empleado se aleje hasta donde sí enfoca. Sin datos confiables (lente sin calibrar, nulos o ceros) da 1.5x.
 *
 * @param dioptriasMinimas `LENS_INFO_MINIMUM_FOCUS_DISTANCE` (1/m); 0 = foco fijo.
 * @param anchoSensorMm el lado largo de `SENSOR_INFO_PHYSICAL_SIZE`.
 * @param focalMm la primera de `LENS_INFO_AVAILABLE_FOCAL_LENGTHS`.
 * @param tope el zoom máximo propio (más de 2x puede cambiar al teleobjetivo, que enfoca más lejos).
 *
 * Pública: la ficha de la cámara (en :app) muestra con qué zoom abre cada lente, con esta misma cuenta.
 */
fun zoomSugerido(
    dioptriasMinimas: Float?,
    anchoSensorMm: Float?,
    focalMm: Float?,
    calibrado: Boolean,
    tope: Float,
    anchoCodigoMm: Float = 30f,
    llenado: Float = 0.6f,
): Float {
    val maximo = tope.coerceAtLeast(1f)
    if (dioptriasMinimas == null || dioptriasMinimas <= 0f || anchoSensorMm == null || anchoSensorMm <= 0f ||
        focalMm == null || focalMm <= 0f || !calibrado
    ) return ZOOM_SIN_DATOS.coerceIn(1f, maximo)
    val enfoqueMinimoMm = 1000f / dioptriasMinimas
    // En vertical con el recorte 16:9, el ancho visible usa ~9/16 del lado largo del sensor.
    val anchoVisibleMm = anchoSensorMm * 9f / 16f
    val medioCampo = atan(anchoVisibleMm / (2f * focalMm))
    val distanciaNecesariaMm = (anchoCodigoMm / llenado) / (2f * tan(medioCampo))
    val zoom = if (distanciaNecesariaMm < enfoqueMinimoMm) enfoqueMinimoMm / distanciaNecesariaMm else 1f
    return zoom.coerceIn(1f, maximo)
}

internal const val ZOOM_SIN_DATOS = 1.5f

/** Promedio móvil exponencial para el diagnóstico (fps, ms del motor…). */
internal class PromedioMovil(private val alfa: Float = 0.1f) {
    var valor = 0f
        private set
    private var vacio = true

    fun agregar(x: Float) {
        if (vacio) {
            valor = x
            vacio = false
        } else {
            valor += alfa * (x - valor)
        }
    }

    fun reiniciar() {
        valor = 0f
        vacio = true
    }
}

/*
 * Las mismas cuentas que Transformacion.aVisible/aBuffer, sin el Pair que esas asignan en cada llamada: se usan en cada
 * cuadro.
 */

/** Un punto del buffer a la región visible normalizada. */
internal fun aVisible(t: Transformacion, xBuffer: Float, yBuffer: Float, salida: FloatArray) {
    val v = t.visible
    Rotacion.aDerecho(xBuffer - v.izquierda, yBuffer - v.arriba, v.ancho, v.alto, t.rotacion, salida)
    salida[0] /= t.anchoDerecho
    salida[1] /= t.altoDerecho
}

/** Un punto de la región visible normalizada al buffer. */
internal fun aBuffer(t: Transformacion, xVisible: Float, yVisible: Float, salida: FloatArray) {
    val v = t.visible
    Rotacion.aBuffer(xVisible * t.anchoDerecho, yVisible * t.altoDerecho, v.ancho, v.alto, t.rotacion, salida)
    salida[0] += v.izquierda
    salida[1] += v.arriba
}

/** La caja visible que encierra [n] esquinas del buffer (x0, y0, x1, y1, …); solo asigna la caja. */
internal fun cajaVisible(t: Transformacion, xy: FloatArray, punto: FloatArray, n: Int = xy.size / 2): Caja {
    var izquierda = Float.MAX_VALUE
    var arriba = Float.MAX_VALUE
    var derecha = -Float.MAX_VALUE
    var abajo = -Float.MAX_VALUE
    var i = 0
    while (i < n) {
        aVisible(t, xy[2 * i], xy[2 * i + 1], punto)
        if (punto[0] < izquierda) izquierda = punto[0]
        if (punto[0] > derecha) derecha = punto[0]
        if (punto[1] < arriba) arriba = punto[1]
        if (punto[1] > abajo) abajo = punto[1]
        i++
    }
    return Caja(izquierda, arriba, derecha, abajo)
}
