package mx.papeleriapos.escaner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.papeleriapos.escaner.EventoLectura
import mx.papeleriapos.escaner.R
import mx.papeleriapos.escaner.nucleo.Caja
import kotlin.math.roundToInt

internal enum class LugarEnFranja { ENCIMA, CENTRO }

/**
 * Coloca el contenido respecto a la franja de lectura: justo encima o en su centro. Ocupa todo el padre y lee la
 * franja al acomodar (no al componer), así que moverla solo reacomoda. Nunca se sale de la pantalla ni se mete bajo
 * la barra de estado ([minimoArriba], en píxeles).
 */
internal fun Modifier.enFranja(
    franja: () -> Caja,
    lugar: LugarEnFranja,
    minimoArriba: Int = 0,
    separacion: Dp = 16.dp,
    margen: Dp = 16.dp,
): Modifier = layout { medible, restricciones ->
    val ancho = restricciones.maxWidth
    val alto = restricciones.maxHeight
    val lateral = margen.roundToPx()
    val p = medible.measure(Constraints(maxWidth = (ancho - 2 * lateral).coerceAtLeast(0), maxHeight = alto))
    layout(ancho, alto) {
        val f = franja()
        val x = (f.centroX * ancho - p.width / 2f).roundToInt()
            .coerceIn(lateral, (ancho - lateral - p.width).coerceAtLeast(lateral))
        val y = when (lugar) {
            LugarEnFranja.ENCIMA -> f.arriba * alto - separacion.toPx() - p.height
            LugarEnFranja.CENTRO -> f.centroY * alto - p.height / 2f
        }.roundToInt().coerceIn(minimoArriba, (alto - p.height).coerceAtLeast(minimoArriba))
        p.place(x, y)
    }
}

/**
 * La indicación del momento: chip de 20sp sobre fondo sólido con su icono. Entra con escala 0.8→1 en 150 ms cada vez
 * que cambia [clave], y TalkBack la anuncia sin interrumpir (región viva cortés).
 */
@Composable
internal fun ChipIndicacion(
    texto: String,
    icono: ImageVector?,
    acento: Boolean,
    cargando: Boolean,
    clave: Any,
    modifier: Modifier = Modifier,
) {
    val escala = remember { Animatable(1f) }
    LaunchedEffect(clave) {
        escala.snapTo(0.8f)
        escala.animateTo(1f, tween(durationMillis = 150, easing = FastOutSlowInEasing))
    }
    Row(
        modifier
            .graphicsLayer {
                scaleX = escala.value
                scaleY = escala.value
            }
            .testTag(EtiquetasEscaner.INDICACION)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .heightIn(min = Tonos.boton)
            .background(Tonos.fondoChip, RoundedCornerShape(28.dp))
            .padding(start = 10.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (acento) Tonos.ambar else Color.White.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                cargando -> CircularProgressIndicator(
                    Modifier.size(24.dp).testTag(EtiquetasEscaner.CARGANDO), color = Color.White, strokeWidth = 3.dp,
                )
                icono != null -> Icon(icono, null, Modifier.size(26.dp), tint = if (acento) Color.Black else Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            texto,
            color = Tonos.texto,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * «Ya lo leí»: palomita grande en verde y el código normalizado en 28sp con su simbología. Va en el mismo cuadro en que
 * sube `lecturas`; junto con el bip y la vibración, no depende solo del color.
 */
@Composable
internal fun TarjetaLeido(evento: EventoLectura?, modifier: Modifier = Modifier) {
    val codigo = evento?.aceptada?.codigo
    val leido = stringResource(R.string.escaner_leido)
    Row(
        modifier
            .testTag(EtiquetasEscaner.LEIDO)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .background(Tonos.verde, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(60.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, Modifier.size(46.dp), tint = Tonos.verde)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                codigo?.normalizado ?: leido,
                color = Tonos.texto,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (codigo != null) "${codigo.simbologia.nombre} · $leido" else leido,
                color = Tonos.texto,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** El tache del error en el centro de la franja (la forma y el texto del banner dicen lo mismo sin color). */
@Composable
internal fun TacheError(modifier: Modifier = Modifier) {
    Box(modifier.size(72.dp).background(Tonos.rojoFondo, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Close, null, Modifier.size(52.dp), tint = Color.White)
    }
}
