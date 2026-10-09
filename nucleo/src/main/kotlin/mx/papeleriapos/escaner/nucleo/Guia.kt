package mx.papeleriapos.escaner.nucleo

/** La indicación que ve el empleado: una sola a la vez (docs/escaner.md, «Guía»). */
enum class Indicacion {
    /** No hay ningún código a la vista. */
    APUNTA,
    /** Hay un código, bien colocado: se está leyendo. */
    LEYENDO,
    ACERCATE,
    ALEJATE,
    /** Mover el celular hacia donde está el código (si el código se ve a la izquierda, mover a la izquierda). */
    IZQUIERDA,
    DERECHA,
    ARRIBA,
    ABAJO,
    QUIETO,
    ENFOCANDO,
    MAS_LUZ,
    REFLEJO,
    LEIDO,
}

/**
 * Lo observado en un cuadro, ya en la región visible normalizada (como se ve en pantalla) y respecto a la franja de
 * lectura. Objeto reutilizable: el analizador lo llena en cada cuadro.
 */
class Observacion {
    var nanos = 0L
    /** Hubo una lectura aceptada en este cuadro. */
    var leido = false
    /** Hay un código a la vista: potencial del motor o localizador verificado en varios cuadros. */
    var candidato = false
    /** (centro del código − centro de la franja) / ancho de la franja; positivo = el código está a la derecha. */
    var dx = 0f
    /** (centro del código − centro de la franja) / alto de la franja; positivo = el código está abajo. */
    var dy = 0f
    /** Largo del código / ancho de la franja. */
    var largoRelativo = 0f
    var cortado = false
    /** Toca el borde de la región visible por un solo lado (−1 izquierda/arriba, +1 derecha/abajo, 0 ninguno). */
    var bordeX = 0
    var bordeY = 0
    /** Píxeles del buffer por módulo; 0 = desconocido. */
    var moduloPx = 0f
    /** 0 a 1; −1 = desconocido. */
    var nitidez = -1f
    /** Luminancia media del centro de la imagen (0 a 255); −1 = desconocida. */
    var brillo = -1f
    var saturados = 0f
    /** `LENS_FOCUS_DISTANCE / LENS_INFO_MINIMUM_FOCUS_DISTANCE`; −1 = desconocido. */
    var proporcionEnfoque = -1f
    var enfocando = false
    var desenfocado = false
    /** Cuánto se movió el código entre cuadros, en fracciones de la franja. */
    var movimiento = 0f
    /** El zoom propio ya llegó a su tope (solo entonces se pide acercarse). */
    var zoomAlTope = true

    fun reiniciar() {
        nanos = 0L; leido = false; candidato = false; dx = 0f; dy = 0f; largoRelativo = 0f; cortado = false
        bordeX = 0; bordeY = 0; moduloPx = 0f; nitidez = -1f; brillo = -1f; saturados = 0f; proporcionEnfoque = -1f
        enfocando = false; desenfocado = false; movimiento = 0f; zoomAlTope = true
    }
}

/**
 * Decide la indicación con histéresis, suavizado y tiempo mínimo en pantalla para que no parpadee
 * (docs/escaner.md, «Umbrales»).
 *
 * CONTRATO (implementación pendiente, se porta del prototipo de la investigación).
 */
class Guia(val umbrales: UmbralesGuia = UmbralesGuia()) {
    /** La indicación que se muestra ahora. */
    var actual: Indicacion = Indicacion.APUNTA
        private set

    fun actualizar(o: Observacion): Indicacion = TODO("Se porta del prototipo")

    fun reiniciar(): Unit = TODO("Se porta del prototipo")
}

/** Umbrales de la guía: puntos de partida que se calibran con los celulares de la tienda. */
data class UmbralesGuia(
    val chicoEntra: Float = 0.30f,
    val chicoSale: Float = 0.36f,
    val grandeEntra: Float = 0.90f,
    val grandeSale: Float = 0.80f,
    val moduloEntra: Float = 2.0f,
    val moduloSale: Float = 2.5f,
    val descentradoEntra: Float = 0.20f,
    val descentradoSale: Float = 0.12f,
    val borrosoEntra: Float = 0.15f,
    val borrosoSale: Float = 0.22f,
    val nitidezConfiable: Float = 0.25f,
    val reflejoEntra: Float = 0.06f,
    val reflejoSale: Float = 0.03f,
    val oscuroEntra: Float = 45f,
    val oscuroSale: Float = 60f,
    val movimientoEntra: Float = 0.05f,
    val movimientoSale: Float = 0.025f,
    val enfoqueCercano: Float = 0.95f,
    /** Cuadros seguidos que debe durar una condición antes de mostrarse. */
    val cuadrosParaMostrar: Int = 3,
    /** Tiempo mínimo que debe durar una condición antes de mostrarse. */
    val msParaMostrar: Long = 300,
    /** Tiempo mínimo en pantalla de una indicación (salvo que la reemplace otra de mayor prioridad). */
    val msEnPantalla: Long = 700,
    /** Sin pistas de dirección en los primeros ms de un candidato nuevo (deja trabajar al enfoque). */
    val msGraciaCandidatoNuevo: Long = 500,
    /** La oscuridad debe durar esto antes de pedir luz. */
    val msOscuroParaPedirLuz: Long = 500,
    val cuadrosPerdidosParaReiniciar: Int = 6,
    val alfa: Float = 0.4f,
)
