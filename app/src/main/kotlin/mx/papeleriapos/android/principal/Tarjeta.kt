package mx.papeleriapos.android.principal

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mx.papeleriapos.android.botonrapido.Vibracion
import mx.papeleriapos.android.botonrapido.vibrar
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.catalogo.cantidadCorta
import mx.papeleriapos.android.catalogo.desdeMayoreo
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.android.catalogo.porPieza
import mx.papeleriapos.android.catalogo.quedan
import mx.papeleriapos.android.catalogo.quedanPocos
import mx.papeleriapos.android.lista.Renglon
import mx.papeleriapos.android.tema.ColorModo
import mx.papeleriapos.android.tema.LocalColoresEstado
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * La tarjeta de lo último (0.5): el producto que se acaba de leer, con su precio en grande, y los gestos de la cantidad,
 * que el dueño eligió el 2026-11-22 después de probar el prototipo (docs/propuesta-interfaz/prototipo-0.5.html):
 *
 * - Un toque en cualquier parte: uno más.
 * - Deslizar a la izquierda un tramo corto: uno menos (con 1, sale).
 * - Deslizar a la izquierda hasta el fondo: sale el renglón entero.
 * - Dejar el dedo: el teclado de la cantidad.
 *
 * Sumar piezas escaneando el mismo producto fue problemático en las pruebas: ahora el mismo código solo vuelve a contar
 * si se retiró de la mira, y las demás piezas se suman aquí, con el pulgar. La tarjeta dice qué va a pasar antes de
 * soltar: lo que asoma atrás («−1», «Quitar los 3») y el renglón de abajo.
 */

/** Hasta dónde llegó el deslizamiento a la izquierda: lo que pasa al soltar. */
enum class Grado { NADA, UNO, TODO }

/** Las medidas del gesto de la tarjeta, las del prototipo. */
object MedidasTarjeta {
    /** Desde aquí, deslizar a la izquierda quita uno. */
    val UNO: Dp = 48.dp

    /** Desde esta parte del ancho de la tarjeta, quita el renglón entero. */
    const val TODO = 0.55f
}

/** El grado de un deslizamiento de [izquierda] px en una tarjeta de [ancho] px; [uno]: [MedidasTarjeta.UNO] en px. */
fun gradoDe(izquierda: Float, ancho: Float, uno: Float): Grado = when {
    ancho > 0f && izquierda >= ancho * MedidasTarjeta.TODO -> Grado.TODO
    izquierda >= uno -> Grado.UNO
    else -> Grado.NADA
}

/** Lo que dice el renglón de abajo de la tarjeta: en reposo, los gestos; a medio deslizar, lo que pasa al soltar. */
fun pistaDeLaTarjeta(grado: Grado, cantidad: Int, donde: String): String = when (grado) {
    Grado.NADA -> "Toca: uno más · Desliza ←: quita · Mantén: cantidad"
    Grado.UNO -> if (cantidad == 1) "Suelta: sale del $donde" else "Suelta: quita uno · sigue hasta el fondo para quitar todo"
    Grado.TODO -> if (cantidad == 1) "Suelta: sale del $donde" else "Suelta: sale del $donde con sus $cantidad"
}

/** Lo que asoma atrás de la tarjeta al deslizarla. */
fun atrasDeLaTarjeta(grado: Grado, cantidad: Int): String = when {
    cantidad == 1 -> "Quitar"
    grado == Grado.TODO -> "Quitar los $cantidad"
    else -> "−1"
}

private enum class Paso { TOQUE, MANTENER, ARRASTRE, NADA }

/**
 * @param venta En la venta enseña el precio, el mayoreo, la existencia y el importe; al contar, solo el producto y cuántos van.
 * @param p El producto del renglón; null mientras se cargan los productos ([cargando]) o si el renglón es de un código que
 * ya no está en el catálogo (lo guardado por una versión anterior): en rojo, y se puede quitar.
 */
@Composable
fun TarjetaActual(
    r: Renglon,
    p: Producto?,
    cargando: Boolean,
    color: ColorModo,
    venta: Boolean,
    acciones: AccionesPanel,
    /** Deslizar desde cerca de la orilla derecha no es el «atrás» del sistema; con una hoja encima, sí (es de la hoja). */
    excluirAtras: Boolean = true,
) {
    val vista = LocalView.current
    val alcance = rememberCoroutineScope()
    val densidad = LocalDensity.current
    val grande = densidad.fontScale >= LETRA_GRANDE
    val donde = if (venta) "ticket" else "conteo"
    // El renglón y sus acciones de ahora: el gesto no se rehace con cada pieza que se suma.
    val id by rememberUpdatedState(r.id)
    val ahora by rememberUpdatedState(acciones)
    var desliz by remember { mutableFloatStateOf(0f) }
    var grado by remember { mutableStateOf(Grado.NADA) }
    var ancho by remember { mutableFloatStateOf(0f) }
    var regreso by remember { mutableStateOf<Job?>(null) }

    val sinProducto = p == null && !cargando
    val error = MaterialTheme.colorScheme.error
    val fondo = when {
        sinProducto -> MaterialTheme.colorScheme.errorContainer
        p == null -> MaterialTheme.colorScheme.surfaceVariant
        else -> color.contenedor
    }
    val tinta = when {
        sinProducto -> MaterialTheme.colorScheme.onErrorContainer
        p == null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> color.enContenedor
    }
    val forma = RoundedCornerShape(16.dp)

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .clip(forma)
                .background(
                    when (grado) {
                        Grado.TODO -> error
                        Grado.UNO -> MaterialTheme.colorScheme.errorContainer
                        Grado.NADA -> MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .onSizeChanged { ancho = it.width.toFloat() }
                .then(if (excluirAtras) Modifier.systemGestureExclusion() else Modifier)
                // El gesto se mide en la caja, que no se mueve: el frente se desliza con el dedo, y medido en él el recorrido
                // se quedaba en la mitad (visto en el emulador: 80 dp no quitaban nada y hasta el fondo quitaba solo uno).
                .pointerInput(Unit) {
                    val uno = MedidasTarjeta.UNO.toPx()
                    awaitEachGesture {
                        val abajo = awaitFirstDown()
                        regreso?.cancel()
                        // Hasta que se suelte (un toque), se mueva de lado (deslizar) o pase el tiempo de mantener.
                        val paso = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var p0: Paso? = null
                            while (p0 == null) {
                                val c = awaitPointerEvent().changes.firstOrNull { it.id == abajo.id }
                                p0 = when {
                                    c == null -> Paso.NADA
                                    !c.pressed -> Paso.TOQUE
                                    else -> {
                                        val d = c.position - abajo.position
                                        when {
                                            abs(d.x) > viewConfiguration.touchSlop && abs(d.x) >= abs(d.y) -> Paso.ARRASTRE
                                            d.getDistance() > viewConfiguration.touchSlop -> Paso.NADA
                                            else -> null
                                        }
                                    }
                                }
                            }
                            p0
                        } ?: Paso.MANTENER
                        when (paso) {
                            Paso.TOQUE -> {
                                vista.vibrar(Vibracion.CAMBIAR)
                                ahora.masUno(id)
                            }
                            Paso.MANTENER -> {
                                vista.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                ahora.teclearCantidad(id)
                                // Lo que falta del gesto es de la tarjeta: al soltar no toca nada de la hoja que subió.
                                do {
                                    val e = awaitPointerEvent()
                                    e.changes.forEach { it.consume() }
                                } while (e.changes.any { it.pressed })
                            }
                            Paso.ARRASTRE -> {
                                var soltado = false
                                while (true) {
                                    val c = awaitPointerEvent().changes.firstOrNull { it.id == abajo.id } ?: break
                                    val dx = c.position.x - abajo.position.x
                                    c.consume()
                                    desliz = dx.coerceIn(-ancho, 0f)
                                    val g = gradoDe(-desliz, ancho, uno)
                                    if (g != grado) {
                                        when {
                                            g == Grado.TODO -> vista.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            g > grado -> vista.vibrar(Vibracion.ARMARSE)
                                            else -> vista.vibrar(Vibracion.DESARMARSE)
                                        }
                                        grado = g
                                    }
                                    if (!c.pressed) {
                                        soltado = true
                                        break
                                    }
                                }
                                val g = grado
                                grado = Grado.NADA
                                if (soltado && g != Grado.NADA) {
                                    vista.vibrar(Vibracion.ELEGIR)
                                    if (g == Grado.TODO) ahora.quitar(id) else ahora.menosUno(id)
                                }
                                val desde = desliz
                                regreso = alcance.launch { animate(desde, 0f) { v, _ -> desliz = v } }
                            }
                            Paso.NADA -> Unit
                        }
                    }
                }
                .testTag("tarjeta"),
        ) {
            Text(
                atrasDeLaTarjeta(grado, r.cantidad),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when (grado) {
                    Grado.TODO -> MaterialTheme.colorScheme.onError
                    Grado.UNO -> MaterialTheme.colorScheme.onErrorContainer
                    Grado.NADA -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).semantics { hideFromAccessibility() },
            )
            Row(
                Modifier
                    .offset { IntOffset(desliz.roundToInt(), 0) }
                    .fillMaxWidth()
                    .background(fondo, forma)
                    // Para TalkBack y el teclado, lo mismo sin gestos: el doble toque suma y las acciones hacen lo demás.
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        stateDescription = if (r.cantidad == 1) "va 1" else "van ${r.cantidad}"
                        onClick(label = "Sumar uno") { ahora.masUno(id); true }
                        onLongClick(label = "Teclear la cantidad") { ahora.teclearCantidad(id); true }
                        customActions = listOf(
                            CustomAccessibilityAction("Uno más") { ahora.masUno(id); true },
                            CustomAccessibilityAction(if (r.cantidad == 1) "Quitar del $donde" else "Quitar uno") { ahora.menosUno(id); true },
                            CustomAccessibilityAction(if (r.cantidad == 1) "Quitar el renglón" else "Quitar los ${r.cantidad} del $donde") { ahora.quitar(id); true },
                            CustomAccessibilityAction("Teclear la cantidad") { ahora.teclearCantidad(id); true },
                        )
                    }
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .testTag("renglon-actual"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p?.nombre ?: if (cargando) r.codigo else "Código sin producto",
                        style = MaterialTheme.typography.titleSmall,
                        color = tinta,
                        maxLines = if (grande) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("nombre-actual"),
                    )
                    when {
                        p == null -> Text(
                            if (cargando) "…" else "${r.codigo} · ya no está en el catálogo: deslízalo para quitarlo",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = tinta,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        venta -> DatosDeVenta(p, r.cantidad, tinta, grande)
                        else -> p.codigo?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = tinta, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                // La cantidad, en el color del modo: lo que cambia con cada gesto.
                Column(
                    Modifier
                        .defaultMinSize(minWidth = 84.dp, minHeight = if (venta) 72.dp else 60.dp)
                        .background(if (sinProducto) error else color.color, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val enPastilla = if (sinProducto) MaterialTheme.colorScheme.onError else color.enColor
                    Text(
                        "×${r.cantidad}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum", lineHeight = 1.1.em),
                        color = enPastilla,
                        maxLines = 1,
                        modifier = Modifier.testTag("cantidad"),
                    )
                    if (venta && p != null) {
                        Text(
                            pesos(p.importe(r.cantidad)),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = enPastilla,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("importe-actual"),
                        )
                    }
                }
            }
        }
        Text(
            pistaDeLaTarjeta(grado, r.cantidad, donde),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 3.dp, bottom = 1.dp).semantics { hideFromAccessibility() }.testTag("pista-tarjeta"),
        )
    }
}

/**
 * Lo que el cliente pregunta, como en el verificador de la caja: el precio de cada una al llevar [cantidad] (ya con el
 * mayoreo o la promoción), cuánto sale la pieza de una presentación, desde cuántas hay mayoreo y lo que queda.
 */
@Composable
private fun DatosDeVenta(p: Producto, cantidad: Int, tinta: Color, grande: Boolean) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            pesos(p.precioUnitario(cantidad)),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum", lineHeight = 1.15.em),
            autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = if (grande) 26.sp else 30.sp),
            color = tinta,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false).testTag("precio"),
        )
        Text(" c/u", style = MaterialTheme.typography.labelLarge, color = tinta, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
    }
    val estado = LocalColoresEstado.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        porPieza(p)?.let { Dato(it) }
        if (p.esMayoreo(cantidad)) Dato("Mayoreo", fondo = estado.exito.copy(alpha = 0.22f)) else desdeMayoreo(p)?.let { Dato(it, fondo = estado.exito.copy(alpha = 0.16f)) }
        p.existencia?.let { e ->
            if (quedanPocos(p)) Dato(quedan(e), fondo = estado.aviso.copy(alpha = 0.18f)) else Dato("Existencia: ${cantidadCorta(e)}")
        }
    }
    p.promocion?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = tinta, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
internal fun Dato(texto: String, fondo: Color = MaterialTheme.colorScheme.surface) {
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).background(fondo).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
