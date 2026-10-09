package mx.papeleriapos.escaner

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import mx.papeleriapos.escaner.nucleo.Aceptada
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Indicacion
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Simbologia
import java.nio.ByteBuffer

/** Los motores que pueden leer (docs/escaner.md, «Motores»). */
enum class Motor(val nombre: String) {
    /** zxing-cpp en cada cuadro y ML Kit de respaldo para los códigos difíciles. */
    AUTOMATICO("Automático"),
    ML_KIT("ML Kit"),
    ZXING_CPP("zxing-cpp"),
}

/** En qué va el escáner (docs/escaner.md, «Estados»). */
enum class Fase {
    /** Falta el permiso de la cámara. */
    SIN_PERMISO,
    /** La cámara se está abriendo: todavía no llega el primer cuadro analizado. */
    ABRIENDO,
    /** Ya llegan cuadros y no hay código a la vista. */
    ESCANEANDO,
    /** Hay un código a la vista que todavía no se lee (esquinas ámbar). */
    VIENDO,
    /** Acaba de leer (retícula verde unos 500 ms); la cámara sigue. */
    LEIDO,
    /** En pausa por inactividad: se toca para seguir. */
    PAUSADO,
    ERROR,
}

/** Lo que hay que saber para depurar y para la sesión de prueba. */
data class Diagnostico(
    val motor: Motor = Motor.AUTOMATICO,
    val resolucion: String = "",
    val franjaPx: String = "",
    val cuadrosPorSegundo: Float = 0f,
    val msMotor: Float = 0f,
    val msLocalizador: Float = 0f,
    val msCuadro: Float = 0f,
    val moduloPx: Float = 0f,
    val nitidez: Float = 0f,
    val brillo: Float = 0f,
    val zoom: Float = 1f,
    val rangoFps: String = "",
    val camaraFisica: String = "",
    val estadoEnfoque: String = "",
    /** Cuadros que entregó la cámara (producidos) y que se analizaron, desde que se abrió: la diferencia se descartó. */
    val cuadrosProducidos: Long = 0,
    val cuadrosAnalizados: Long = 0,
    /** El estado de CameraX (`CameraState`): abriendo, abierta, error y su código. */
    val estadoCamara: String = "",
)

/** Una lectura aceptada con lo que tardó (para la retroalimentación y la sesión de prueba). */
data class EventoLectura(
    val aceptada: Aceptada,
    /** Desde que apareció un candidato (o desde que se reanudó) hasta la lectura; −1 si no se sabe. */
    val msDesdeCandidato: Long,
    /** Indicaciones que se mostraron antes de leer, en orden. */
    val indicaciones: List<Indicacion>,
    val motor: Motor,
    /** Por dónde se leyó: zxing-cpp directo, con el umbral del localizador, o ML Kit de respaldo. */
    val via: mx.papeleriapos.escaner.nucleo.Via = mx.papeleriapos.escaner.nucleo.Via.DIRECTA,
    val moduloPx: Float,
    val zoom: Float,
    val linterna: Boolean,
)

/** Todo lo que la interfaz necesita para dibujar el escáner. Las cajas van en la región visible normalizada. */
data class EstadoEscaner(
    val fase: Fase = Fase.ABRIENDO,
    val indicacion: Indicacion = Indicacion.APUNTA,
    /** La franja de lectura (la retícula), en la región visible normalizada. */
    val franja: Caja = Caja(0.04f, 0.32f, 0.96f, 0.68f),
    /** El código a la vista (leído o no), para dibujar las esquinas que lo siguen. */
    val cajaCodigo: Caja? = null,
    val ultima: EventoLectura? = null,
    /** Sube con cada lectura aceptada: la interfaz lo usa para disparar el bip, la vibración y la animación. */
    val lecturas: Long = 0,
    val hayLinterna: Boolean = false,
    val linterna: Boolean = false,
    /** Hay poca luz y conviene ofrecer la linterna (botón que pulsa). */
    val sugerirLinterna: Boolean = false,
    val zoom: Float = 1f,
    val mensajeError: String? = null,
    val diagnostico: Diagnostico = Diagnostico(),
    /** Las marcas de la apertura en curso (docs/escaner.md, «Apertura»). */
    val apertura: Apertura = Apertura(),
)

/** Configuración del escáner; se puede cambiar en caliente con [ControladorEscaner.configurar]. */
data class ConfigEscaner(
    val motor: Motor = Motor.AUTOMATICO,
    val modo: Modo = Modo.VENTA,
    val simbologias: Set<Simbologia> = setOf(
        Simbologia.EAN_13, Simbologia.EAN_8, Simbologia.UPC_A, Simbologia.UPC_E, Simbologia.ITF,
        Simbologia.CODE_128, Simbologia.QR,
    ),
    /** Alto de la franja de lectura respecto a la región visible. */
    val altoFranja: Float = 0.36f,
    val msAusenciaParaRepetir: Long = 500,
    /** Sin candidatos durante esto, se pausa el análisis (toca para seguir); 0 = nunca. */
    val segundosParaPausar: Int = 20,
    /** En pausa durante esto, se apaga la cámara; 0 = nunca. */
    val segundosParaApagar: Int = 60,
    val linternaAutomatica: Boolean = false,
    val sonido: Boolean = true,
    val vibracion: Boolean = true,
    /** Tope del zoom propio (más de 2x puede cambiar al teleobjetivo, que enfoca más lejos). */
    val zoomMaximo: Float = 2f,
    /** Rango de cuadros por segundo preferido: 30 fijo (menos movido) o 15 a 30 (más luz). */
    val fpsFijo30: Boolean = true,
    /** Recortar la franja antes de pasarla al motor (más rápido) o pasar el cuadro completo (para comparar). */
    val recortarFranja: Boolean = true,
    val mostrarDiagnostico: Boolean = false,
)

/**
 * El escáner que usa la interfaz. Lo implementa la cámara real ([mx.papeleriapos.escaner.camara.ControladorCamara]) y,
 * para las vistas previas y las pruebas de la interfaz, uno de muestra.
 */
interface ControladorEscaner {
    val estado: StateFlow<EstadoEscaner>
    /** Cada lectura aceptada, una vez. */
    val eventos: SharedFlow<EventoLectura>
    /** La superficie para el visor (`CameraXViewfinder`); null mientras no hay cámara. */
    val superficie: StateFlow<SurfaceRequest?>
    val config: StateFlow<ConfigEscaner>

    /**
     * Abre la cámara ligada al ciclo de vida de [dueno]. [anchoVisor] y [altoVisor] son el tamaño del visor en
     * píxeles: definen la región visible (el `ViewPort`). Se puede volver a llamar si cambia el tamaño.
     */
    fun abrir(dueno: LifecycleOwner, anchoVisor: Int, altoVisor: Int, rotacionPantalla: Int)

    fun configurar(config: ConfigEscaner)
    fun alternarLinterna()
    /** Sale de la pausa (o de la retícula verde) y vuelve a escanear. */
    fun reanudar()
    /** Enfoca en un punto de la región visible normalizada (al tocar el visor). */
    fun enfocar(x: Float, y: Float)
    fun permisoConcedido(concedido: Boolean)
    /**
     * Libera la cámara y los motores. Lo llama quien creó el controlador (el ViewModel en `onCleared`), no la pantalla:
     * al salir de la composición basta con que su ciclo de vida se detenga.
     */
    fun cerrar()
}

/**
 * Un cuadro para un motor: el plano Y del buffer sin rotar y la región que hay que leer, en píxeles del buffer.
 * [proxy] es el `ImageProxy` en vivo (zxing-cpp lo lee directo); en el banco de pruebas es null.
 */
class CuadroY(
    val y: ByteBuffer,
    val pasoFila: Int,
    val ancho: Int,
    val alto: Int,
    val rotacion: Int,
    val region: mx.papeleriapos.escaner.nucleo.Rectangulo,
    val proxy: androidx.camera.core.ImageProxy? = null,
    /** Todo lo que se ve; el motor automático le da esto a ML Kit (ver [mx.papeleriapos.escaner.motores.MotorAutomatico]). */
    val regionVisible: mx.papeleriapos.escaner.nucleo.Rectangulo = region,
    /** El localizador (o los cuadros anteriores) dicen que hay un código: vale la pena el respaldo. */
    val codigoProbable: Boolean = true,
    /** El nivel entre barras y espacios que midió el localizador (0 a 255); −1 si no lo midió. */
    val umbralSugerido: Int = -1,
)

/** Un decodificador. Se usa desde un solo hilo (el de análisis) y se cierra al terminar. */
interface MotorDeLectura : AutoCloseable {
    val motor: Motor
    /** Lee la región del cuadro; devuelve las esquinas en píxeles del buffer sin rotar. */
    fun leer(cuadro: CuadroY): ResultadoMotor
    /** Corre una lectura en vacío para que la primera de verdad no tarde más (carga del modelo). */
    fun calentar() {}
}
