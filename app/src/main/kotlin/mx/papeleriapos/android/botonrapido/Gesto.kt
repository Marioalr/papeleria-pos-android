package mx.papeleriapos.android.botonrapido

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/*
 * El gesto del botón rápido (docs/propuesta-interfaz.md, «El botón rápido»): pulsar el botón abre tres opciones; sin
 * soltar, deslizar hacia una y soltar la elige. Aquí va solo la geometría y las reglas, en dp y milisegundos, sin
 * Android: la pantalla traduce los toques y las vibraciones. Es lo mismo que el prototipo (prototipo.html, «gesto»),
 * donde se probó.
 */

/** Las tres direcciones del botón rápido: ↑ entrega, ↖ lo propio del modo, ← busca. En zurdo, en espejo (↑, ↗, →). */
enum class Lugar { ARRIBA, DIAGONAL, LADO }

/** Un desplazamiento en dp. La y crece hacia abajo, como en la pantalla. */
data class Punto(val x: Float, val y: Float) {
    operator fun minus(otro: Punto) = Punto(x - otro.x, y - otro.y)
    fun distancia(): Float = hypot(x, y)
    fun espejo(zurdo: Boolean) = if (zurdo) Punto(-x, y) else this
}

/** Las medidas del gesto, en dp, grados y milisegundos. Cada una dice por qué en la propuesta («Medidas»). */
object MedidasGesto {
    /** Deslizar esto desde donde se pulsó arma el gesto hacia una opción: cuatro veces el slop táctil (8 dp). */
    const val ARMA = 32f

    /** Ya armado, bajar de esto vuelve al centro: que el resaltado no tiemble en el borde de los 32. */
    const val DESARMA = 24f

    /** Grados de más, de cada lado, para la opción que ya estaba resaltada. */
    const val HISTERESIS = 4.0

    /** Con el dedo a esto o menos del centro de una opción, gana esa, diga lo que diga el ángulo. */
    const val ZONA = 28f

    /** De centro a centro, del botón a cada opción. */
    const val DISTANCIA = 88f

    /** Después de que el botón elige o se cierra, por esto solo él responde: un segundo toque por reflejo no confirma. */
    const val REBOTE_MS = 300L

    /** El slop táctil de Android en dp, si la pantalla no lo puede preguntar (`ViewConfiguration`). */
    const val SLOP_DE_FABRICA = 8f

    /** La pulsación larga de Android, si la pantalla no la puede preguntar: un toque suelta antes. */
    const val TOQUE_DE_FABRICA_MS = 400L
}

private val DIAGONAL = MedidasGesto.DISTANCIA * 0.70710678f

/** Dónde queda el centro de cada opción respecto al centro del botón (a 90°, 135° y 180°). */
val CENTROS: Map<Lugar, Punto> = mapOf(
    Lugar.ARRIBA to Punto(0f, -MedidasGesto.DISTANCIA),
    Lugar.DIAGONAL to Punto(-DIAGONAL, -DIAGONAL),
    Lugar.LADO to Punto(-MedidasGesto.DISTANCIA, 0f),
)

/** Un sector del ángulo, de [de] a [hasta] grados (sin incluir [hasta]). */
private class Sector(val lugar: Lugar, val de: Double, val hasta: Double)

/** Cada frontera va a medio camino entre dos opciones; ↑ se abre a la derecha y ← hacia abajo, donde no hay nada. */
private val SECTORES = listOf(
    Sector(Lugar.ARRIBA, 45.0, 112.5),
    Sector(Lugar.DIAGONAL, 112.5, 157.5),
    Sector(Lugar.LADO, 157.5, 225.0),
)

/** El ángulo de [d] en grados, de 0 a 360: 0° a la derecha y 90° arriba. */
fun angulo(d: Punto): Double = (Math.toDegrees(atan2(-d.y.toDouble(), d.x.toDouble())) + 360.0) % 360.0

/**
 * La opción del ángulo [grados], o null si cae en cancelar (la mitad de abajo a la derecha). La de [antes] se revisa
 * primero y con [MedidasGesto.HISTERESIS] de más de cada lado: si se revisaran en orden, la histéresis solo valdría de un
 * lado de cada frontera.
 */
fun sectorDe(grados: Double, antes: Lugar?): Lugar? {
    SECTORES.firstOrNull { it.lugar == antes && grados >= it.de - MedidasGesto.HISTERESIS && grados < it.hasta + MedidasGesto.HISTERESIS }
        ?.let { return it.lugar }
    return SECTORES.firstOrNull { grados >= it.de && grados < it.hasta }?.lugar
}

/** Lo que está resaltado: una opción, la × (el dedo va a cancelar) o nada (en el centro, o sin armarse). */
sealed interface Resaltado {
    data class Opcion(val lugar: Lugar) : Resaltado
    data object Cancelar : Resaltado
    data object Nada : Resaltado
}

/** Cómo vibra cada momento del gesto; la pantalla lo traduce a la constante de Android que toque según la versión. */
enum class Vibracion {
    /** Se armó hacia una opción o llegó encima de ella. */
    ARMARSE,

    /** Pasó de una opción a otra. */
    CAMBIAR,

    /** Volvió al centro o fue a cancelar. */
    DESARMARSE,

    /** Soltó en una opción encendida. */
    ELEGIR,

    /** Soltó en una apagada. */
    RECHAZAR,
}

/** La vibración al pasar de [antes] a [despues], o null si no cambió nada que se sienta. */
fun vibracionAlCambiar(antes: Resaltado, despues: Resaltado): Vibracion? = when {
    antes == despues -> null
    despues is Resaltado.Opcion && antes is Resaltado.Opcion -> Vibracion.CAMBIAR
    despues is Resaltado.Opcion -> Vibracion.ARMARSE
    antes is Resaltado.Opcion -> Vibracion.DESARMARSE
    else -> null
}

/** Cómo terminó el gesto. */
sealed interface Final {
    /** Soltó en una opción: la pantalla la abre, o vibra de rechazo y dice por qué si está apagada. */
    data class Elegir(val lugar: Lugar) : Final

    /** Fue un toque (menos que el slop y antes de la pulsación larga): las tres quedan abiertas para tocar una. */
    data object DejarAbierto : Final

    /** Cualquier otro soltar: sostuvo, se movió sin elegir, fue a cancelar, tocó la × o el sistema se llevó el dedo. */
    data object Cerrar : Final
}

/**
 * Un gesto sobre el botón rápido, desde que baja el dedo hasta que se suelta. Las posiciones van en dp respecto al centro
 * del botón, como las mide la pantalla (sin espejo: el espejo del zurdo lo pone esta clase).
 *
 * @param pulso donde bajó el dedo.
 * @param tPulsoMs cuándo bajó.
 * @param hay las opciones del modo; la dirección de una que no hay (un hueco) se porta como cancelar.
 * @param yaAbierto el menú ya estaba abierto (se abrió con un toque): tocar el botón otra vez lo cierra.
 * @param zurdo «Botón rápido a la izquierda»: las opciones salen en espejo.
 * @param slop el slop táctil del sistema, en dp: un toque se mueve menos.
 * @param toqueMs la pulsación larga del sistema: un toque suelta antes (si se alargó en Accesibilidad, se respeta).
 */
class GestoRapido(
    private val pulso: Punto,
    private val tPulsoMs: Long,
    private val hay: Set<Lugar>,
    private val yaAbierto: Boolean,
    private val zurdo: Boolean = false,
    private val slop: Float = MedidasGesto.SLOP_DE_FABRICA,
    private val toqueMs: Long = MedidasGesto.TOQUE_DE_FABRICA_MS,
) {
    /** Lo que está resaltado ahora. */
    var resaltado: Resaltado = Resaltado.Nada
        private set

    /**
     * El dedo va armado hacia una opción: a [MedidasGesto.ARMA] o más de donde se pulsó y, ya armado, hasta que baje de
     * [MedidasGesto.DESARMA] (que el resaltado no tiemble en el borde de los 32). Como el `lugarDe` de «Cómo se hace»:
     * mientras está armado, el ángulo se vuelve a mirar en cada movimiento, con la histéresis.
     */
    var armado = false
        private set

    /** Lo más lejos que llegó desde donde se pulsó, para saber si fue un toque. */
    private var lejos = 0f

    /** El dedo está en [p]. Devuelve cómo vibrar si el resaltado cambió. */
    fun mover(p: Punto): Vibracion? {
        val desdePulso = (p - pulso).espejo(zurdo)
        val desdeBoton = p.espejo(zurdo)
        val distancia = desdePulso.distancia()
        lejos = max(lejos, distancia)
        val antes = resaltado
        armado = distancia >= if (armado) MedidasGesto.DESARMA else MedidasGesto.ARMA
        var nuevo: Resaltado = if (armado) {
            sectorDe(angulo(desdePulso), (antes as? Resaltado.Opcion)?.lugar)?.let { Resaltado.Opcion(it) } ?: Resaltado.Cancelar
        } else {
            Resaltado.Nada
        }
        // Con el dedo sobre una opción gana esa, aunque el ángulo desde donde se pulsó diga otra (o no esté armado): quien
        // apunta a lo que ve desde una esquina del botón encendía la de al lado.
        CENTROS.entries.firstOrNull { (lugar, centro) -> lugar in hay && (desdeBoton - centro).distancia() <= MedidasGesto.ZONA }
            ?.let { nuevo = Resaltado.Opcion(it.key) }
        // Un hueco (el modo no tiene opción ahí) se porta como cancelar: las demás no se recorren.
        if (nuevo is Resaltado.Opcion && nuevo.lugar !in hay) nuevo = Resaltado.Cancelar
        resaltado = nuevo
        return vibracionAlCambiar(antes, nuevo)
    }

    /**
     * Se soltó en [p] en [tMs]. Solo un toque deja el menú abierto; cualquier otro soltar decide: elige o cierra. Así el
     * pulgar que se apoya al acomodar la mano no lo deja abierto, y cancelar hacia la orilla funciona aunque no haya 32 dp.
     */
    fun soltar(p: Punto, tMs: Long): Final {
        mover(p)
        val elegido = resaltado
        if (elegido is Resaltado.Opcion) return Final.Elegir(elegido.lugar)
        // Lo que se alejó más que el margen de un toque ya no es un toque, aunque haya vuelto al centro.
        val toque = lejos < slop && tMs - tPulsoMs < toqueMs
        return if (yaAbierto || !toque) Final.Cerrar else Final.DejarAbierto
    }
}

/**
 * El rebote: por [MedidasGesto.REBOTE_MS] desde que el botón eligió o se cerró, solo él responde. La hoja que abrió, el
 * precio en grande, el panel y los avisos no aceptan toques que empiecen en ese tiempo (en el chico, «Mandar a Caja 2»
 * queda justo donde el dedo suelta ↑).
 */
class Rebote {
    private var desdeMs: Long? = null

    /** El botón acaba de elegir o de cerrarse. */
    fun marcar(tMs: Long) {
        desdeMs = tMs
    }

    /** Ya no hace falta (se cerró con el teclado, que no rebota). */
    fun olvidar() {
        desdeMs = null
    }

    /** Un toque que bajó en [tBajaMs] es de rebote y no cuenta (salvo en el botón, que la pantalla deja pasar). */
    fun esRebote(tBajaMs: Long): Boolean {
        val desde = desdeMs ?: return false
        return tBajaMs >= desde && tBajaMs - desde < MedidasGesto.REBOTE_MS
    }
}
