package mx.papeleriapos.android.botonrapido

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.papeleriapos.android.tema.ColorModo
import kotlin.math.roundToInt

/*
 * El botón rápido en la pantalla (docs/propuesta-interfaz.md, «El botón rápido» y «Cómo se hace»). El botón y el menú van
 * en la capa de encima de todo, sobre el hueco que les deja la barra de modos: así quedan arriba del velo que atenúa el
 * panel. La geometría y las reglas del gesto están en [GestoRapido]; aquí solo se traducen los toques, se dibuja y se
 * vibra.
 */

/** Lo que comparten el botón (en su hueco de la barra) y el menú (encima del panel). */
@Stable
class EstadoBotonRapido {
    var abierto by mutableStateOf(false)
        private set

    /** Se abrió con un toque (no se está deslizando): arriba del menú puede salir «Pulsa y desliza sin soltar». */
    var conToque by mutableStateOf(false)
        private set

    var resaltado by mutableStateOf<Resaltado>(Resaltado.Nada)

    /** El centro del hueco del botón, respecto a la capa (px). */
    var centro by mutableStateOf(Offset.Unspecified)

    /** La zona de toque del botón, respecto a la capa (px): ahí el rebote no aplica. */
    var zona by mutableStateOf(Rect.Zero)

    /** Mientras se abrió con el teclado, el foco va a la primera opción. */
    var enfocarPrimera by mutableStateOf(false)

    val rebote = Rebote()

    fun abrir() {
        abierto = true
        conToque = false
        resaltado = Resaltado.Nada
    }

    fun quedarseAbierto() {
        conToque = true
        resaltado = Resaltado.Nada
    }

    /** Se cerró (eligió o no): por 300 ms solo responde el botón ([Rebote]). */
    fun cerrar(marcarRebote: Boolean = true) {
        abierto = false
        conToque = false
        enfocarPrimera = false
        resaltado = Resaltado.Nada
        if (marcarRebote) rebote.marcar(SystemClock.uptimeMillis()) else rebote.olvidar()
    }
}

/** Lo que hace la pantalla con el botón. */
class AccionesBotonRapido(
    /** El menú se abrió (al pulsar): la burbuja de la primera vez se va; [conToque] cuando se quedó abierto con un toque. */
    val alAbrir: (conToque: Boolean) -> Unit,
    /** El menú se cerró: los avisos que esperaban salen. */
    val alCerrar: () -> Unit,
    /** Se eligió una opción encendida, con el gesto o con un toque. */
    val alElegir: (OpcionRapida, conGesto: Boolean) -> Unit,
    /** Se soltó (o se tocó) una apagada: el aviso dice por qué. */
    val alRechazar: (OpcionRapida) -> Unit,
)

/** El botón: 56 dp, esquinas de 16, del color del modo; su zona de toque, 64. Sin opciones, el lugar queda vacío. */
@Composable
fun BotonRapido(
    estado: EstadoBotonRapido,
    nombre: String,
    opciones: List<OpcionRapida>,
    zurdo: Boolean,
    color: ColorModo,
    acciones: AccionesBotonRapido,
    modifier: Modifier = Modifier,
) {
    if (opciones.isEmpty()) return
    val vista = LocalView.current
    val opcionesActuales by rememberUpdatedState(opciones)
    val accionesActuales by rememberUpdatedState(acciones)
    val hay = opciones.map { it.lugar }.toSet()
    val foco = remember { FocusRequester() }
    Box(
        modifier
            .size(ZONA_DE_TOQUE)
            .systemGestureExclusion() // «atrás» no se lleva lo que empieza en el botón
            .pointerInput(zurdo, hay) {
                awaitEachGesture {
                    val abajo = awaitFirstDown(requireUnconsumed = false)
                    abajo.consume() // el panel no se arrastra
                    val yaAbierto = estado.abierto
                    if (!yaAbierto) {
                        estado.abrir() // al instante, como pidió el dueño
                        accionesActuales.alAbrir(false)
                    }
                    val centro = Offset(size.width / 2f, size.height / 2f)
                    val enDp = { o: Offset -> Punto(o.x / density, o.y / density) }
                    val gesto = GestoRapido(
                        pulso = enDp(abajo.position - centro),
                        tPulsoMs = abajo.uptimeMillis,
                        hay = hay,
                        yaAbierto = yaAbierto,
                        zurdo = zurdo,
                        slop = viewConfiguration.touchSlop / density,
                        toqueMs = viewConfiguration.longPressTimeoutMillis,
                    )
                    var final: Final = Final.Cerrar
                    while (true) {
                        val evento = awaitPointerEvent()
                        val c = evento.changes.firstOrNull { it.id == abajo.id }
                        // El sistema se llevó el dedo (un gesto de atrás o de inicio): se cierra sin hacer nada.
                        if (c == null || (!c.pressed && c.isConsumed)) break
                        if (c.pressed) {
                            val v = gesto.mover(enDp(c.position - centro))
                            estado.resaltado = gesto.resaltado
                            val sobre = (gesto.resaltado as? Resaltado.Opcion)?.lugar
                            val apagada = opcionesActuales.firstOrNull { it.lugar == sobre }?.encendida == false
                            if (v != null && !(v == Vibracion.ARMARSE && apagada)) vista.vibrar(v)
                            c.consume()
                        } else {
                            final = gesto.soltar(enDp(c.position - centro), c.uptimeMillis)
                            c.consume()
                            break
                        }
                    }
                    when (val f = final) {
                        is Final.Elegir -> elegir(f.lugar, conGesto = true, estado, opcionesActuales, accionesActuales, vista)
                        Final.DejarAbierto -> {
                            estado.quedarseAbierto()
                            accionesActuales.alAbrir(true)
                        }
                        Final.Cerrar -> {
                            estado.cerrar()
                            accionesActuales.alCerrar()
                        }
                    }
                }
            }
            .focusRequester(foco)
            .focusable()
            .onKeyEvent {
                val tecla = it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Spacebar
                if (tecla && it.type == KeyEventType.KeyUp) {
                    if (estado.abierto) {
                        estado.cerrar(marcarRebote = false)
                        accionesActuales.alCerrar()
                    } else {
                        estado.abrir()
                        estado.enfocarPrimera = true
                        accionesActuales.alAbrir(false)
                    }
                }
                tecla // se queda con la tecla al bajar y al subir
            }
            .semantics {
                role = Role.Button
                contentDescription = nombre
                stateDescription = if (estado.abierto) "abiertas: " + opciones.joinToString { it.nombre } else "cerradas"
                traversalIndex = 3f // en el grupo del menú: después de ↑, ↖ y ←
                onClick {
                    if (estado.abierto) {
                        estado.cerrar(marcarRebote = false)
                        accionesActuales.alCerrar()
                    } else {
                        estado.abrir()
                        accionesActuales.alAbrir(true)
                    }
                    true
                }
                // El atajo que equivale al gesto: elegir sin abrir el menú. Las apagadas dicen por qué.
                customActions = opciones.map { o ->
                    CustomAccessibilityAction(o.razonApagada?.let { "${o.nombre} (apagada: $it)" } ?: o.nombre) {
                        elegir(o.lugar, conGesto = false, estado, opcionesActuales, accionesActuales, vista)
                        true
                    }
                }
            }
            .testTag("boton-rapido"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = color.color,
            contentColor = color.enColor,
            shadowElevation = 3.dp,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (estado.abierto) Icon(Icons.Filled.Close, contentDescription = null) else Abanico(color.enColor, zurdo)
            }
        }
    }
}

private fun elegir(
    lugar: Lugar,
    conGesto: Boolean,
    estado: EstadoBotonRapido,
    opciones: List<OpcionRapida>,
    acciones: AccionesBotonRapido,
    vista: View,
) {
    val o = opciones.firstOrNull { it.lugar == lugar }
    estado.cerrar()
    acciones.alCerrar()
    if (o == null) return
    if (o.encendida) {
        vista.vibrar(Vibracion.ELEGIR)
        acciones.alElegir(o, conGesto)
    } else {
        vista.vibrar(Vibracion.RECHAZAR)
        acciones.alRechazar(o)
    }
}

/** Tres puntos en abanico, hacia donde salen las opciones (no es un «+»: el + es el «Uno más» del renglón). */
@Composable
private fun Abanico(color: Color, zurdo: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val r = size.minDimension * 0.11f
        val radio = size.minDimension * 0.34f
        val base = Offset(if (zurdo) size.width * 0.3f else size.width * 0.7f, size.height * 0.7f)
        for (grados in listOf(90.0, 135.0, 180.0)) {
            val a = Math.toRadians(grados)
            val x = (kotlin.math.cos(a) * radio).toFloat() * (if (zurdo) -1 else 1)
            val y = (-kotlin.math.sin(a) * radio).toFloat()
            drawCircle(color, r, base + Offset(x, y))
        }
    }
}

/**
 * El menú abierto: el velo sobre el panel (la franja sigue a la vista), una capa invisible que cierra al tocar fuera sin
 * enfocar el visor, las tres opciones con su etiqueta y, mientras se abra con toques, «Pulsa y desliza sin soltar».
 */
@Composable
fun MenuRapido(
    estado: EstadoBotonRapido,
    opciones: List<OpcionRapida>,
    zurdo: Boolean,
    color: ColorModo,
    /** El panel, respecto a la capa: lo que se atenúa. */
    panel: Rect,
    /** Mientras se abra con toques y no se haya elegido deslizando tres veces. */
    pistaDeslizar: Boolean,
    acciones: AccionesBotonRapido,
) {
    if (!estado.abierto || estado.centro == Offset.Unspecified) return
    val vista = LocalView.current
    val densidad = LocalDensity.current
    val velo = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
    val primera = remember { FocusRequester() }
    BackHandler {
        estado.cerrar(marcarRebote = false)
        acciones.alCerrar()
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    estado.cerrar()
                    acciones.alCerrar()
                }
            }
            .drawBehindPanel(panel, velo)
            .semantics { isTraversalGroup = true }
            .testTag("menu-rapido"),
    ) {
        val sx = if (zurdo) -1f else 1f
        opciones.forEachIndexed { i, o ->
            val c = CENTROS.getValue(o.lugar)
            val centro = estado.centro + with(densidad) { Offset(c.x.dp.toPx() * sx, c.y.dp.toPx()) }
            val elegida = (estado.resaltado as? Resaltado.Opcion)?.lugar == o.lugar
            OpcionDelMenu(o, centro, elegida, color, zurdo, i, estado, acciones, vista, if (i == 0) primera else null)
        }
        if (pistaDeslizar && estado.conToque) {
            // Arriba de la etiqueta de ↑ (su botón a 88 dp, 24 de radio, 8 de aire y la etiqueta de 32), sin encimarse.
            val arriba = estado.centro + with(densidad) { Offset(0f, (CENTROS.getValue(Lugar.ARRIBA).y - 24f - 8f - 32f - 8f).dp.toPx()) }
            Text(
                PISTA_DESLIZAR,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .anclado(arriba, margen = 16.dp) { tam -> IntOffset(-tam.width / 2, -tam.height) }
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    LaunchedEffect(estado.enfocarPrimera) {
        if (estado.enfocarPrimera) runCatching { primera.requestFocus() }
    }
}

@Composable
private fun OpcionDelMenu(
    o: OpcionRapida,
    centro: Offset,
    elegida: Boolean,
    color: ColorModo,
    zurdo: Boolean,
    orden: Int,
    estado: EstadoBotonRapido,
    acciones: AccionesBotonRapido,
    vista: View,
    /** La primera, con el teclado: Enter abre y lleva el foco aquí. */
    foco: FocusRequester?,
) {
    val lado = if (elegida) 56.dp else 48.dp
    val fondo = when {
        !o.encendida -> MaterialTheme.colorScheme.surfaceVariant
        elegida -> color.color
        else -> color.contenedor
    }
    val tinta = when {
        !o.encendida -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        elegida -> color.enColor
        else -> color.enContenedor
    }
    Box(
        Modifier
            .anclado(centro) { tam -> IntOffset(-tam.width / 2, -tam.height / 2) }
            .size(lado)
            .clip(CircleShape)
            .background(fondo)
            .then(if (foco != null) Modifier.focusRequester(foco) else Modifier)
            .clickable(role = Role.Button, onClickLabel = o.nombre) {
                elegir(o.lugar, conGesto = false, estado, listOf(o), acciones, vista)
            }
            .semantics {
                contentDescription = o.razonApagada?.let { "${o.nombre}, apagada: $it" } ?: o.nombre
                traversalIndex = orden.toFloat()
            }
            .testTag("opcion-${o.lugar.name.lowercase()}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(flecha(o.lugar, zurdo), color = tinta, fontSize = 20.sp)
    }
    // La etiqueta: la de ↑ arriba de su botón; las de ↖ y ←, a su izquierda (en zurdo, a la derecha).
    val etiqueta = etiqueta(o, zurdo)
    val radio = with(LocalDensity.current) { (lado / 2).toPx() }
    val separacion = with(LocalDensity.current) { 8.dp.toPx() }
    Text(
        etiqueta,
        style = MaterialTheme.typography.labelLarge,
        color = if (o.encendida) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .anclado(centro, margen = 16.dp) { tam ->
                when {
                    o.lugar == Lugar.ARRIBA -> IntOffset(-tam.width / 2, (-radio - separacion).roundToInt() - tam.height)
                    zurdo -> IntOffset((radio + separacion).roundToInt(), -tam.height / 2)
                    else -> IntOffset((-radio - separacion).roundToInt() - tam.width, -tam.height / 2)
                }
            }
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (elegida) color.color else MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clearAndSetSemantics { }, // TalkBack ya lo lee en la opción
    )
}

/**
 * Coloca algo con su punto de [ancla] (px, respecto al padre) más el desplazamiento que diga [desde] según su tamaño. Con
 * [margen], no se sale de los lados del padre (las etiquetas y la pista quedan dentro del margen de 16 dp).
 */
private fun Modifier.anclado(
    ancla: Offset,
    margen: androidx.compose.ui.unit.Dp? = null,
    desde: (androidx.compose.ui.unit.IntSize) -> IntOffset,
) = layout { medible, restricciones ->
    val ancho = restricciones.maxWidth
    val p = medible.measure(restricciones.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        val d = desde(androidx.compose.ui.unit.IntSize(p.width, p.height))
        var x = ancla.x.roundToInt() + d.x
        if (margen != null) {
            val m = margen.roundToPx()
            x = x.coerceAtMost(ancho - m - p.width).coerceAtLeast(m)
        }
        p.place(x, ancla.y.roundToInt() + d.y)
    }
}

/** El velo, solo sobre el panel: la franja y el visor siguen a la vista. */
private fun Modifier.drawBehindPanel(panel: Rect, velo: Color) = this.then(
    Modifier.drawBehind { if (panel != Rect.Zero) drawRect(velo, panel.topLeft, panel.size) },
)

/** La zona de toque del botón: que el pulgar le atine a la esquina sin mirar. */
val ZONA_DE_TOQUE = 64.dp

/**
 * Registra dónde quedó el hueco del botón (su zona de toque), respecto a la capa [capa] (la posición de la capa en la
 * raíz).
 */
fun Modifier.huecoDelBoton(estado: EstadoBotonRapido, capa: () -> Offset) = onGloballyPositioned { c ->
    val origen = c.positionInRoot() - capa()
    val tam = c.size
    estado.zona = Rect(origen, androidx.compose.ui.geometry.Size(tam.width.toFloat(), tam.height.toFloat()))
    estado.centro = origen + Offset(tam.width / 2f, tam.height / 2f)
}

/**
 * La vibración de cada momento, distinta de la del escáner (que confirma con CONFIRM): el menú nunca usa esa, para no
 * confundir «leído» con «elegido». Antes de Android 14 no hay GESTURE_THRESHOLD_* ni SEGMENT_TICK; antes de Android 11, ni
 * GESTURE_END ni REJECT. Todas respetan la «Respuesta táctil» del sistema.
 */
fun View.vibrar(v: Vibracion) {
    val constante = when (v) {
        Vibracion.ARMARSE -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK
        Vibracion.CAMBIAR -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        Vibracion.DESARMARSE -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE else return
        Vibracion.ELEGIR -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CONTEXT_CLICK
        Vibracion.RECHAZAR -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constante)
}
