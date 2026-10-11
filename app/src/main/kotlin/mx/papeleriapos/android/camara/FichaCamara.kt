package mx.papeleriapos.android.camara

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Log
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.archivo.escribirAtomico
import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.sesion.DatosEquipo
import mx.papeleriapos.android.sesion.MedidorAndroid
import mx.papeleriapos.escaner.camara.zoomSugerido
import java.io.File
import java.io.IOException

/**
 * Una lente de la cámara trasera tal como la describe Camera2 (docs/escaner.md, «Ficha de la cámara»): la lógica y
 * cada una de sus físicas. Con esto se elige con cuál leer: para los códigos chicos puede servir más la principal con
 * zoom a la distancia donde ya enfoca que acercarse. Sin Android: se arma desde Camera2 y se prueba en la JVM.
 */
data class FichaLente(
    val id: String,
    /** La cámara lógica a la que pertenece; null si esta es la lógica (o una cámara sin físicas). */
    val logica: String?,
    /** `INFO_SUPPORTED_HARDWARE_LEVEL`: LEGACY, LIMITED, FULL, NIVEL_3 o EXTERNAL. */
    val nivel: String,
    val focalesMm: List<Float>,
    val aperturas: List<Float>,
    /** `LENS_INFO_MINIMUM_FOCUS_DISTANCE`, en dioptrías (1/m): 0 = foco fijo; null = no lo dice. */
    val enfoqueMinimoDioptrias: Float?,
    val hiperfocalDioptrias: Float?,
    /** `LENS_INFO_FOCUS_DISTANCE_CALIBRATION`: [SIN_CALIBRAR], [APROXIMADA], [CALIBRADA] o [DESCONOCIDA]. */
    val calibracion: String,
    val modosAf: List<String>,
    /** `CONTROL_ZOOM_RATIO_RANGE` (API 30+); antes, de 1 al zoom digital máximo. */
    val zoomMinimo: Float?,
    val zoomMaximo: Float?,
    /** `SENSOR_INFO_PHYSICAL_SIZE`: lado largo × lado corto, en mm. */
    val sensorMm: Pair<Float, Float>?,
    /** `SENSOR_INFO_PIXEL_ARRAY_SIZE`. */
    val pixeles: Pair<Int, Int>?,
    /** Salidas YUV_420_888 (lo que recibe el análisis), de mayor a menor. */
    val resolucionesAnalisis: List<Pair<Int, Int>>,
    /** Salidas para el visor (SurfaceTexture), de mayor a menor. */
    val resolucionesVisor: List<Pair<Int, Int>>,
    val rangosFps: List<Pair<Int, Int>>,
    val linterna: Boolean,
    val capacidades: List<String>,
) {
    val focoFijo: Boolean get() = enfoqueMinimoDioptrias == 0f

    /** Lo más cerca que enfoca, en cm; null con foco fijo o sin dato. */
    val enfoqueMinimoCm: Float? get() = enfoqueMinimoDioptrias?.takeIf { it > 0f }?.let { 100f / it }

    /** La distancia de enfoque viene calibrada o al menos aproximada (sin calibrar, los cm no sirven). */
    val calibrada: Boolean get() = calibracion == CALIBRADA || calibracion == APROXIMADA

    /** mm que abarca el lado corto del recorte 16:9 (a lo ancho del celular en vertical) por cada mm de distancia. */
    private val campoPorMm: Float?
        get() {
            val sensor = sensorMm ?: return null
            val focal = focalesMm.firstOrNull() ?: return null
            if (focal <= 0f || sensor.first <= 0f) return null
            return sensor.first * 9f / 16f / focal
        }

    /** Lo que abarca la cámara a lo ancho del celular en vertical a [distanciaCm], en cm (modelo de agujero). */
    fun campoCm(distanciaCm: Float): Float? = campoPorMm?.let { it * distanciaCm }

    /** Píxeles por módulo de [moduloMm] a [distanciaCm] con el análisis a 1280×720 ([pxAncho] a lo ancho del celular). */
    fun pxPorModulo(distanciaCm: Float, moduloMm: Float = 0.33f, pxAncho: Int = 720): Float? =
        campoCm(distanciaCm)?.takeIf { it > 0f }?.let { pxAncho * moduloMm / (it * 10f) }

    /** La distancia, en cm, a la que un código de [anchoCodigoMm] llena [llenado] del ancho visible. */
    fun distanciaParaLlenar(anchoCodigoMm: Float = 30f, llenado: Float = 0.6f): Float? =
        campoPorMm?.takeIf { it > 0f }?.let { (anchoCodigoMm / llenado) / it / 10f }

    /** El zoom con que la app abre esta lente (la misma cuenta que el escáner: [zoomSugerido] con tope 2×). */
    val zoomSugerido: Float
        get() = zoomSugerido(enfoqueMinimoDioptrias, sensorMm?.first, focalesMm.firstOrNull(), calibrada, tope = 2f)

    companion object {
        const val SIN_CALIBRAR = "sin_calibrar"
        const val APROXIMADA = "aproximada"
        const val CALIBRADA = "calibrada"
        const val DESCONOCIDA = "desconocida"
    }
}

/** ficha-camara.csv: una fila por lente, y el resumen que se ve en Ajustes. */
object CsvFicha {
    val COLUMNAS = listOf(
        "huella", "lente", "logica", "nivel", "focal_mm", "focales_mm", "aperturas", "enfoque_minimo_dioptrias",
        "enfoque_minimo_cm", "hiperfocal_dioptrias", "calibracion", "modos_af", "zoom_minimo", "zoom_maximo", "sensor_mm",
        "pixeles", "campo_cm_al_minimo", "px_por_modulo_033_al_minimo", "cm_para_30mm_al_60", "zoom_sugerido",
        "resoluciones_analisis", "resoluciones_visor", "rangos_fps", "linterna", "capacidades", "dispositivo", "sdk",
        "version_app", "commit",
    )

    fun escribir(lentes: List<FichaLente>, huella: String, equipo: DatosEquipo): String {
        val salida = StringBuilder(Csv.BOM)
        Csv.renglon(COLUMNAS, salida)
        for (l in lentes) {
            val minimo = l.enfoqueMinimoCm
            Csv.renglon(
                listOf(
                    Csv.textoSeguro(huella),
                    l.id,
                    l.logica ?: "",
                    l.nivel,
                    l.focalesMm.firstOrNull()?.let { Csv.decimal(it) } ?: "",
                    l.focalesMm.joinToString("|") { Csv.decimal(it) },
                    l.aperturas.joinToString("|") { Csv.decimal(it) },
                    l.enfoqueMinimoDioptrias?.let { Csv.decimal(it, 3) } ?: "",
                    minimo?.let { Csv.decimal(it, 1) } ?: "",
                    l.hiperfocalDioptrias?.let { Csv.decimal(it, 3) } ?: "",
                    l.calibracion,
                    l.modosAf.joinToString("|"),
                    l.zoomMinimo?.let { Csv.decimal(it) } ?: "",
                    l.zoomMaximo?.let { Csv.decimal(it) } ?: "",
                    l.sensorMm?.let { (w, h) -> "${Csv.decimal(w)}x${Csv.decimal(h)}" } ?: "",
                    l.pixeles?.let { (w, h) -> "${w}x$h" } ?: "",
                    minimo?.let { l.campoCm(it) }?.let { Csv.decimal(it, 1) } ?: "",
                    minimo?.let { l.pxPorModulo(it) }?.let { Csv.decimal(it, 1) } ?: "",
                    l.distanciaParaLlenar()?.let { Csv.decimal(it, 1) } ?: "",
                    Csv.decimal(l.zoomSugerido),
                    l.resolucionesAnalisis.joinToString("|") { (w, h) -> "${w}x$h" },
                    l.resolucionesVisor.joinToString("|") { (w, h) -> "${w}x$h" },
                    l.rangosFps.joinToString("|") { (a, b) -> "$a-$b" },
                    if (l.linterna) "si" else "no",
                    l.capacidades.joinToString("|"),
                    Csv.textoSeguro(equipo.dispositivo),
                    equipo.sdk.toString(),
                    equipo.versionApp,
                    equipo.commit,
                ),
                salida,
            )
        }
        return salida.toString()
    }

    /**
     * Para Ajustes: por lente, qué es y, si enfoca de cerca, qué tan grande se ve un código ahí y con qué zoom abre la
     * app. Con punto decimal sin importar el idioma, como los CSV.
     */
    fun resumen(lentes: List<FichaLente>): String = buildString {
        for ((i, l) in lentes.withIndex()) {
            if (i > 0) append('\n')
            append(if (l.logica == null) "Lógica ${l.id}" else "Física ${l.id} (de ${l.logica})")
            append(" · ").append(l.nivel)
            l.focalesMm.firstOrNull()?.let { append(" · ").append(Csv.decimal(it, 1)).append(" mm") }
            l.sensorMm?.let { (w, h) -> append(" · sensor ").append(Csv.decimal(w, 1)).append('×').append(Csv.decimal(h, 1)).append(" mm") }
            append(" · ").append(textoEnfoque(l))
            if (l.zoomMinimo != null || l.zoomMaximo != null) {
                append(" · zoom ").append(zoom(l.zoomMinimo)).append('–').append(zoom(l.zoomMaximo)).append('×')
            }
            if (l.modosAf.isNotEmpty()) append(" · AF ").append(l.modosAf.joinToString(", ") { it.replace('_', ' ') })
            if (l.linterna) append(" · linterna")
            l.resolucionesAnalisis.firstOrNull()?.let { (w, h) -> append(" · análisis hasta ").append(w).append('×').append(h) }
            val minimo = l.enfoqueMinimoCm
            val campo = minimo?.let { l.campoCm(it) }
            val px = minimo?.let { l.pxPorModulo(it) }
            if (minimo != null && campo != null && px != null) {
                append("\n  a ").append(Csv.decimal(minimo, 1)).append(" cm abarca ").append(Csv.decimal(campo, 1))
                    .append(" cm: 0.33 mm = ").append(Csv.decimal(px, 1)).append(" px")
                l.distanciaParaLlenar()?.let {
                    append("; 30 mm al 60 % desde ").append(Csv.decimal(it, 1)).append(" cm → zoom ")
                        .append(Csv.decimal(l.zoomSugerido, 1)).append('×')
                }
            }
        }
    }

    private fun textoEnfoque(l: FichaLente): String = when {
        l.focoFijo -> "foco fijo"
        l.enfoqueMinimoCm == null -> "enfoque sin dato"
        else -> "enfoca desde ${Csv.decimal(l.enfoqueMinimoCm!!, 1)} cm (${l.calibracion.replace('_', ' ')})"
    }

    private fun zoom(v: Float?): String = if (v == null) "?" else Csv.decimal(v, 1)
}

/**
 * La ficha de las lentes traseras del celular (docs/escaner.md, «Ficha de la cámara»). Se arma una vez por proceso,
 * fuera del hilo principal y nunca en la apertura: con el primer cuadro analizado o al abrir Ajustes. Queda en
 * `files/camara/ficha-camara.csv` (se rehace si cambia el sistema o la app) y va con la medición de la sesión. No
 * necesita el permiso de la cámara: solo lee características, no abre nada.
 */
object FichaCamara {
    private val _estado = MutableStateFlow<List<FichaLente>?>(null)

    /** null mientras no se ha leído. */
    val estado: StateFlow<List<FichaLente>?> = _estado.asStateFlow()

    private val candado = Mutex()

    fun archivo(contexto: Context): File = File(File(contexto.filesDir, "camara"), "ficha-camara.csv")

    /** La ficha; la primera vez la lee de Camera2 y la deja en disco si cambió. Se puede llamar desde cualquier hilo. */
    suspend fun asegurar(contexto: Context): List<FichaLente> = candado.withLock {
        _estado.value ?: withContext(Dispatchers.IO) {
            val app = contexto.applicationContext
            val lentes = try {
                leer(app.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
            } catch (e: RuntimeException) {
                Log.w(ETIQUETA, "No se pudo leer la cámara", e)
                emptyList()
            }
            try {
                val texto = csv(lentes)
                val a = archivo(app)
                if (!a.exists() || a.readText() != texto) {
                    a.parentFile?.mkdirs()
                    a.escribirAtomico(texto)
                }
            } catch (e: IOException) {
                Log.w(ETIQUETA, "No se pudo guardar la ficha de la cámara", e)
            }
            lentes
        }.also { _estado.value = it }
    }

    /** El CSV para exportarlo con la medición de la sesión. */
    suspend fun csv(contexto: Context): String = csv(asegurar(contexto))

    private fun csv(lentes: List<FichaLente>): String =
        CsvFicha.escribir(lentes, Build.FINGERPRINT ?: "", MedidorAndroid.equipo())

    /** Las lentes traseras: cada cámara lógica y, en API 28+, sus físicas (en el Pixel, las 2, 3 y 4 de la 0). */
    fun leer(manager: CameraManager): List<FichaLente> {
        val lentes = ArrayList<FichaLente>()
        for (id in manager.cameraIdList) {
            val c = caracteristicas(manager, id) ?: continue
            if (c.get(CameraCharacteristics.LENS_FACING) != CameraMetadata.LENS_FACING_BACK) continue
            lentes += ficha(id, logica = null, c)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                for (fisica in c.physicalCameraIds) {
                    val cf = caracteristicas(manager, fisica) ?: continue
                    lentes += ficha(fisica, logica = id, cf)
                }
            }
        }
        return lentes
    }

    private fun caracteristicas(manager: CameraManager, id: String): CameraCharacteristics? = try {
        manager.getCameraCharacteristics(id)
    } catch (e: Exception) {
        // CameraAccessException (el servicio no responde) o IllegalArgumentException (un id que ya no existe).
        Log.w(ETIQUETA, "Sin características de la cámara $id", e)
        null
    }

    private fun ficha(id: String, logica: String?, c: CameraCharacteristics): FichaLente {
        val mapa = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val zoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
        return FichaLente(
            id = id,
            logica = logica,
            nivel = nivel(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)),
            focalesMm = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList(),
            aperturas = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList() ?: emptyList(),
            enfoqueMinimoDioptrias = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE),
            hiperfocalDioptrias = c.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE),
            calibracion = calibracion(c.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)),
            modosAf = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.map(::modoAf) ?: emptyList(),
            zoomMinimo = zoom?.lower ?: 1f,
            zoomMaximo = zoom?.upper ?: c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM),
            sensorMm = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let { it.width to it.height },
            pixeles = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let { it.width to it.height },
            resolucionesAnalisis = tamanos(mapa?.getOutputSizes(ImageFormat.YUV_420_888)),
            resolucionesVisor = tamanos(mapa?.getOutputSizes(SurfaceTexture::class.java)),
            rangosFps = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.map { it.lower to it.upper } ?: emptyList(),
            linterna = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            capacidades = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.map(::capacidad) ?: emptyList(),
        )
    }

    private fun tamanos(sizes: Array<Size>?): List<Pair<Int, Int>> =
        sizes?.map { it.width to it.height }?.sortedByDescending { (w, h) -> w.toLong() * h } ?: emptyList()

    private fun nivel(n: Int?): String = when (n) {
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "NIVEL_3"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        null -> "?"
        else -> "nivel_$n"
    }

    private fun calibracion(c: Int?): String = when (c) {
        CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED -> FichaLente.SIN_CALIBRAR
        CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE -> FichaLente.APROXIMADA
        CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED -> FichaLente.CALIBRADA
        else -> FichaLente.DESCONOCIDA
    }

    private fun modoAf(m: Int): String = when (m) {
        CameraMetadata.CONTROL_AF_MODE_OFF -> "fijo"
        CameraMetadata.CONTROL_AF_MODE_AUTO -> "auto"
        CameraMetadata.CONTROL_AF_MODE_MACRO -> "macro"
        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "continuo_video"
        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "continuo_foto"
        CameraMetadata.CONTROL_AF_MODE_EDOF -> "edof"
        else -> "af_$m"
    }

    // Constantes que se copian al compilar: solo se comparan con lo que dio el sistema, que en versiones viejas no las da.
    @SuppressLint("InlinedApi")
    private fun capacidad(c: Int): String = when (c) {
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "basica"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "sensor_manual"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "proceso_manual"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "raw"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "lee_sensor"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "rafaga"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "yuv_reproceso"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "profundidad"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "video_rapido"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> "seguimiento"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "logica_multicamara"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "monocromo"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "imagen_segura"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> "camara_del_sistema"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING -> "proceso_aparte"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR -> "ultra_alta_resolucion"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING -> "remosaico"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT -> "diez_bits"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE -> "uso_de_flujo"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES -> "perfiles_de_color"
        else -> "cap_$c"
    }

    private const val ETIQUETA = "FichaCamara"
}
