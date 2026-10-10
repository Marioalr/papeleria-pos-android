package mx.papeleriapos.escaner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.R
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Indicacion
import kotlin.math.min

/**
 * La capa sobre el visor: oscurece fuera de la franja, dibuja la retícula (blanca que pulsa al escanear, verde al leer,
 * roja punteada en error), el láser, el borde hacia donde hay que moverse y las esquinas ámbar que siguen al código.
 *
 * El estado cambia hasta ~30 veces por segundo, así que aquí nada se recompone por eso: todo lo que cambia en cada
 * cuadro se lee en la fase de dibujo (solo se redibuja) y las figuras se arman una vez en [drawWithCache] (solo se
 * rehacen si cambia la franja o el tamaño). En el dibujo no se crea ningún objeto.
 *
 * Tocar la capa llama [alTocar] con el punto en la región visible normalizada (el visor ocupa lo mismo que la capa).
 */
@Composable
internal fun CapaEscaner(
    estado: State<EstadoEscaner>,
    leido: State<Boolean>,
    destello: () -> Float,
    alTocar: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val franja = remember(estado) { derivedStateOf { estado.value.franja } }
    val faseActual = remember(estado) { derivedStateOf { estado.value.fase } }
    val indicacion = remember(estado) { derivedStateOf { estado.value.indicacion } }
    val fase = faseActual.value
    val pulso = pulsoReticula(fase == Fase.ESCANEANDO)

    val codigo = remember { CajaAnimada() }
    LaunchedEffect(codigo, estado) { codigo.seguir { estado.value.cajaCodigo } }
    val cajaDibujo = remember { FloatArray(4) }

    val foco = remember { Animatable(0f) }
    val puntoFoco = remember { FloatArray(2) }
    val ambito = rememberCoroutineScope()
    val tocar by rememberUpdatedState(alTocar)

    val descripcion = stringResource(R.string.escaner_visor)
    val estadoTexto = stringResource(descripcionDe(fase))

    Spacer(
        modifier
            .fillMaxSize()
            .testTag(EtiquetasEscaner.FASE)
            .semantics {
                contentDescription = descripcion
                stateDescription = estadoTexto
            }
            .pointerInput(Unit) {
                detectTapGestures { punto ->
                    puntoFoco[0] = punto.x
                    puntoFoco[1] = punto.y
                    ambito.launch {
                        foco.snapTo(1f)
                        foco.animateTo(0f, tween(durationMillis = 700))
                    }
                    tocar(punto.x / size.width, punto.y / size.height)
                }
            }
            .drawWithCache {
                val f = franja.value
                val r = Rect(f.izquierda * size.width, f.arriba * size.height, f.derecha * size.width, f.abajo * size.height)
                val radio = Tonos.radioFranja.toPx()
                val forma = RoundRect(r, CornerRadius(radio))
                val capa = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(forma)
                }
                val contorno = Path().apply { addRoundRect(forma) }
                val largo = min(Tonos.largoEsquina.toPx(), min(r.width, r.height) / 3f)
                val esquinas = Path().apply { esquinasDeReticula(r, largo, radio) }
                val trazo = Stroke(Tonos.trazoReticula.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val trazoFino = Stroke(1.dp.toPx())
                val trazoLeido = Stroke(5.dp.toPx(), join = StrokeJoin.Round)
                val guion = 12.dp.toPx()
                val trazoError = Stroke(
                    3.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(guion, guion * 0.75f)),
                )
                val margenLaser = 10.dp.toPx()
                val laserDesde = Offset(r.left + margenLaser, r.center.y)
                val laserHasta = Offset(r.right - margenLaser, r.center.y)
                // Se desvanece en las puntas: se ve como luz y no como una raya pintada.
                val laser = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.12f to Tonos.laser, 0.88f to Tonos.laser, 1f to Color.Transparent,
                    startX = laserDesde.x, endX = laserHasta.x,
                )
                val anchoLaser = Tonos.trazoLaser.toPx()
                val anchoBrillo = Tonos.brilloLaser.toPx()
                val anchoBorde = Tonos.trazoBorde.toPx()
                val anchoCodigo = Tonos.trazoCodigo.toPx()
                val anchoSombra = anchoCodigo + 3.dp.toPx()
                val largoCodigo = Tonos.largoEsquinaCodigo.toPx()
                val radioFoco = 34.dp.toPx()
                val trazoFoco = Stroke(2.dp.toPx())

                onDrawBehind {
                    val fase = faseActual.value
                    if (fase == Fase.SIN_PERMISO) return@onDrawBehind
                    val esLeido = leido.value
                    drawPath(capa, Tonos.capa)

                    when {
                        esLeido -> {
                            drawPath(contorno, Tonos.verde, alpha = 0.22f)
                            drawPath(contorno, Tonos.verde, style = trazoLeido)
                        }
                        fase == Fase.ERROR -> drawPath(contorno, Tonos.rojoTrazo, style = trazoError)
                        else -> drawPath(contorno, Tonos.reticula, alpha = 0.35f, style = trazoFino)
                    }
                    if (fase != Fase.ERROR || esLeido) {
                        val alfa = when {
                            esLeido -> 1f
                            fase == Fase.ESCANEANDO -> pulso.value
                            fase == Fase.PAUSADO -> 0.5f
                            fase == Fase.ABRIENDO -> 0.8f
                            else -> 1f
                        }
                        drawPath(esquinas, if (esLeido) Tonos.verde else Tonos.reticula, alpha = alfa, style = trazo)
                    }

                    if ((fase == Fase.ESCANEANDO || fase == Fase.VIENDO) && !esLeido) {
                        // El láser marca la línea que leen los decodificadores 1D; el brillo pulsa con la retícula.
                        drawLine(laser, laserDesde, laserHasta, anchoBrillo, StrokeCap.Round, alpha = 0.12f + 0.25f * pulso.value)
                        drawLine(laser, laserDesde, laserHasta, anchoLaser, StrokeCap.Round)
                        bordeHacia(indicacion.value, r, largo, anchoBorde)
                    }

                    val visible = codigo.visibilidad
                    if (visible > 0.01f && fase != Fase.PAUSADO && fase != Fase.ERROR && fase != Fase.ABRIENDO) {
                        codigo.escribir(cajaDibujo)
                        val l = cajaDibujo[0] * size.width
                        val t = cajaDibujo[1] * size.height
                        val d = cajaDibujo[2] * size.width
                        val b = cajaDibujo[3] * size.height
                        // Una sombra debajo para que el ámbar se vea aunque el empaque sea amarillo o blanco.
                        esquinasDeCodigo(l, t, d, b, largoCodigo, Color.Black, 0.45f * visible, anchoSombra)
                        esquinasDeCodigo(l, t, d, b, largoCodigo, if (esLeido) Tonos.verde else Tonos.ambar, visible, anchoCodigo)
                    }

                    val f0 = foco.value
                    if (f0 > 0f) {
                        drawCircle(
                            Color.White, radius = radioFoco * (0.7f + 0.3f * f0), center = Offset(puntoFoco[0], puntoFoco[1]),
                            alpha = f0, style = trazoFoco,
                        )
                    }

                    val d = destello()
                    if (d > 0f) drawRect(Tonos.verde, alpha = d)
                }
            },
    )
}

/** El pulso de la retícula (ciclo de 2.5 s); solo corre mientras se escanea, para no redibujar de más. */
@Composable
private fun pulsoReticula(activo: Boolean): State<Float> =
    if (activo) {
        // Empieza en 1: el primer cuadro de ESCANEANDO ya muestra la retícula y el láser completos.
        rememberInfiniteTransition(label = "pulso").animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(1250, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulso",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

/**
 * Sigue la caja del código con una animación corta para que las esquinas no salten entre cuadros. Guarda el origen y
 * el destino en arreglos fijos y anima solo el avance (0 a 1): en cada cuadro no se crea ninguna caja.
 */
@Stable
internal class CajaAnimada {
    private val desde = FloatArray(4)
    private val hacia = FloatArray(4)
    private val avance = Animatable(1f)
    private val alfa = Animatable(0f)
    private val seguimiento = tween<Float>(durationMillis = 120, easing = LinearOutSlowInEasing)
    private val salida = tween<Float>(durationMillis = 180)

    /** 0 = no hay código; 1 = a la vista. */
    val visibilidad: Float get() = alfa.value

    /** Escribe en [destino] (izquierda, arriba, derecha, abajo) la caja de este cuadro, normalizada. */
    fun escribir(destino: FloatArray) {
        val p = avance.value
        for (i in 0..3) destino[i] = desde[i] + (hacia[i] - desde[i]) * p
    }

    suspend fun seguir(caja: () -> Caja?) {
        snapshotFlow(caja).collectLatest { c ->
            if (c == null) {
                alfa.animateTo(0f, salida)
                return@collectLatest
            }
            if (alfa.value < 0.05f) {
                // Reaparece: va directo a su lugar en vez de viajar desde donde se perdió.
                poner(desde, c)
                poner(hacia, c)
                avance.snapTo(1f)
                alfa.snapTo(1f)
            } else {
                escribir(desde)
                poner(hacia, c)
                alfa.snapTo(1f)
                avance.snapTo(0f)
                avance.animateTo(1f, seguimiento)
            }
        }
    }

    private fun poner(destino: FloatArray, c: Caja) {
        destino[0] = c.izquierda; destino[1] = c.arriba; destino[2] = c.derecha; destino[3] = c.abajo
    }
}

/** Cuatro escuadras con la esquina redondeada, sobre el contorno de la franja. */
private fun Path.esquinasDeReticula(r: Rect, largo: Float, radio: Float) {
    moveTo(r.left, r.top + largo); lineTo(r.left, r.top + radio)
    quadraticTo(r.left, r.top, r.left + radio, r.top); lineTo(r.left + largo, r.top)

    moveTo(r.right - largo, r.top); lineTo(r.right - radio, r.top)
    quadraticTo(r.right, r.top, r.right, r.top + radio); lineTo(r.right, r.top + largo)

    moveTo(r.right, r.bottom - largo); lineTo(r.right, r.bottom - radio)
    quadraticTo(r.right, r.bottom, r.right - radio, r.bottom); lineTo(r.right - largo, r.bottom)

    moveTo(r.left + largo, r.bottom); lineTo(r.left + radio, r.bottom)
    quadraticTo(r.left, r.bottom, r.left, r.bottom - radio); lineTo(r.left, r.bottom - largo)
}

/** Ilumina el lado de la franja hacia donde hay que mover el celular (refuerza la flecha del chip). */
private fun DrawScope.bordeHacia(indicacion: Indicacion, r: Rect, largo: Float, ancho: Float) {
    val c = Tonos.ambar
    when (indicacion) {
        Indicacion.IZQUIERDA -> drawLine(c, Offset(r.left, r.top + largo), Offset(r.left, r.bottom - largo), ancho, StrokeCap.Round)
        Indicacion.DERECHA -> drawLine(c, Offset(r.right, r.top + largo), Offset(r.right, r.bottom - largo), ancho, StrokeCap.Round)
        Indicacion.ARRIBA -> drawLine(c, Offset(r.left + largo, r.top), Offset(r.right - largo, r.top), ancho, StrokeCap.Round)
        Indicacion.ABAJO -> drawLine(c, Offset(r.left + largo, r.bottom), Offset(r.right - largo, r.bottom), ancho, StrokeCap.Round)
        else -> Unit
    }
}

/** Las cuatro esquinas de la caja del código: escuadras cortas que no tapan las barras. */
private fun DrawScope.esquinasDeCodigo(
    l: Float, t: Float, r: Float, b: Float, largo: Float, color: Color, alfa: Float, ancho: Float,
) {
    val lx = min(largo, (r - l) / 3f)
    val ly = min(largo, (b - t) / 3f)
    drawLine(color, Offset(l, t), Offset(l + lx, t), ancho, StrokeCap.Round, alpha = alfa)
    drawLine(color, Offset(l, t), Offset(l, t + ly), ancho, StrokeCap.Round, alpha = alfa)
    drawLine(color, Offset(r, t), Offset(r - lx, t), ancho, StrokeCap.Round, alpha = alfa)
    drawLine(color, Offset(r, t), Offset(r, t + ly), ancho, StrokeCap.Round, alpha = alfa)
    drawLine(color, Offset(r, b), Offset(r - lx, b), ancho, StrokeCap.Round, alpha = alfa)
    drawLine(color, Offset(r, b), Offset(r, b - ly), ancho, StrokeCap.Round, alpha = alfa)
    drawLine(color, Offset(l, b), Offset(l + lx, b), ancho, StrokeCap.Round, alpha = alfa)
    drawLine(color, Offset(l, b), Offset(l, b - ly), ancho, StrokeCap.Round, alpha = alfa)
}
