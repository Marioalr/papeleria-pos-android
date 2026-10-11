package mx.papeleriapos.escaner.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.R
import mx.papeleriapos.escaner.nucleo.Caja
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * La linterna: 56dp, encendida en ámbar con icono negro y apagada en oscuro con icono blanco (se distingue por icono y
 * relleno, no solo por color). Si hay poca luz y está apagada, pulsa con un aro ámbar para ofrecerse.
 */
@Composable
internal fun BotonLinterna(encendida: Boolean, sugerir: Boolean, alTocar: () -> Unit, modifier: Modifier = Modifier) {
    val ofrecer = sugerir && !encendida
    val escala = if (ofrecer) {
        rememberInfiniteTransition(label = "linterna").animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "linterna",
        )
    } else {
        null
    }
    val nombre = stringResource(R.string.escaner_linterna)
    val estadoTexto = stringResource(if (encendida) R.string.escaner_linterna_encendida else R.string.escaner_linterna_apagada)
    Box(
        modifier
            .graphicsLayer {
                val e = escala?.value ?: 1f
                scaleX = e
                scaleY = e
            }
            .size(Tonos.boton)
            .testTag(EtiquetasEscaner.LINTERNA)
            .clip(CircleShape)
            .background(if (encendida) Tonos.ambar else Tonos.fondoChip)
            .border(if (ofrecer) 3.dp else 1.5.dp, if (ofrecer) Tonos.ambar else Color.White.copy(alpha = 0.6f), CircleShape)
            .toggleable(value = encendida, role = Role.Switch, onValueChange = { alTocar() })
            .semantics {
                contentDescription = nombre
                stateDescription = estadoTexto
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (encendida) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = if (encendida) Color.Black else Color.White,
        )
    }
}

/** «1.5×»: 2.0 se escribe «2×». Con el separador decimal del idioma del celular. */
internal fun textoZoom(zoom: Float): String =
    if (abs(zoom - zoom.roundToInt()) < 0.05f) zoom.roundToInt().toString()
    else String.format(Locale.getDefault(), "%.1f", zoom)

/** La etiqueta del zoom propio (solo si pasa de 1×), para que el empleado sepa por qué la imagen se ve más cerca. */
@Composable
internal fun EtiquetaZoom(zoom: Float, modifier: Modifier = Modifier) {
    val texto = textoZoom(zoom)
    val descripcion = stringResource(R.string.escaner_zoom_descripcion, texto)
    Box(
        modifier
            .heightIn(min = Tonos.boton)
            .widthIn(min = Tonos.boton)
            .testTag(EtiquetasEscaner.ZOOM)
            .semantics(mergeDescendants = true) { contentDescription = descripcion }
            .background(Tonos.fondoChip, RoundedCornerShape(28.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.escaner_zoom, texto), color = Tonos.texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** El bip no se va a oír: la confirmación en pantalla basta, pero conviene saberlo. */
@Composable
internal fun AvisoSinSonido(modifier: Modifier = Modifier) {
    Row(
        modifier
            .testTag(EtiquetasEscaner.SIN_SONIDO)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 40.dp)
            .background(Tonos.fondoChip, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeOff, null, Modifier.size(20.dp), tint = Tonos.ambar)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.escaner_sin_sonido), color = Tonos.texto, fontSize = 15.sp)
    }
}

/** Panel de diagnóstico (config.mostrarDiagnostico): solo él se recompone con los números de cada cuadro. */
@Composable
internal fun PanelDiagnostico(estado: State<EstadoEscaner>, modifier: Modifier = Modifier) {
    val d by remember(estado) { derivedStateOf { estado.value.diagnostico } }
    Column(
        modifier
            .testTag(EtiquetasEscaner.DIAGNOSTICO)
            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        @Composable
        fun linea(texto: String) = Text(texto, color = Tonos.texto, fontSize = 12.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace)
        linea(stringResource(R.string.escaner_diagnostico))
        linea(stringResource(R.string.escaner_diag_motor, d.motor.nombre, d.cuadrosPorSegundo))
        linea(stringResource(R.string.escaner_diag_tiempos, d.msCuadro, d.msMotor, d.msLocalizador))
        linea(stringResource(R.string.escaner_diag_imagen, d.moduloPx, d.nitidez, d.brillo))
        linea(stringResource(R.string.escaner_diag_camara, d.zoom, d.resolucion.ifEmpty { "?" }, d.rangoFps.ifEmpty { "?" }))
        linea(stringResource(R.string.escaner_diag_franja, d.franjaPx.ifEmpty { "?" }, d.camaraFisica.ifEmpty { "?" }))
        linea(stringResource(R.string.escaner_diag_enfoque, d.estadoEnfoque.ifEmpty { "?" }))
        if (d.ladoALado.isNotEmpty()) linea(d.ladoALado)
    }
}

/** En pausa: la imagen se atenúa y todo el visor es un botón para seguir. */
@Composable
internal fun CapaPausa(franja: () -> Caja, alTocar: () -> Unit, minimoArriba: Int) {
    val etiqueta = stringResource(R.string.escaner_seguir)
    Box(
        Modifier
            .fillMaxSize()
            .testTag(EtiquetasEscaner.PAUSA)
            .background(Tonos.atenuado)
            .clickable(onClickLabel = etiqueta, role = Role.Button, interactionSource = null, indication = null) { alTocar() },
    ) {
        Column(
            Modifier
                .enFranja(franja, LugarEnFranja.CENTRO, minimoArriba)
                .background(Tonos.fondoChip, RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.TouchApp, null, Modifier.size(48.dp), tint = Color.White)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.escaner_pausado),
                color = Tonos.texto,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.escaner_pausado_detalle),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** El banner del error: tache, el mensaje (TalkBack lo anuncia de inmediato) y «Reintentar» de 56dp. */
@Composable
internal fun BannerError(mensaje: String, alReintentar: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .testTag(EtiquetasEscaner.ERROR)
            .fillMaxWidth()
            .background(Tonos.rojoFondo, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Cancel, null, Modifier.size(32.dp), tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text(
                mensaje,
                Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                color = Tonos.texto,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = alReintentar,
            modifier = Modifier.align(Alignment.End).heightIn(min = Tonos.boton),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Tonos.rojoFondo),
        ) {
            Text(stringResource(R.string.escaner_reintentar), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Sin permiso: qué pasa, para qué es la cámara y cómo darlo (de nuevo o desde Ajustes si ya no sale la ventana). */
@Composable
internal fun VistaSinPermiso(alPedir: () -> Unit, alAbrirAjustes: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .safeDrawingPadding()
            .testTag(EtiquetasEscaner.SIN_PERMISO),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.NoPhotography, null, Modifier.size(72.dp), tint = Color.White)
            Text(
                stringResource(R.string.escaner_sin_permiso_titulo),
                color = Tonos.texto,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.escaner_sin_permiso_texto),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = alPedir,
                modifier = Modifier.fillMaxWidth().heightIn(min = Tonos.boton),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                Text(stringResource(R.string.escaner_dar_permiso), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = alAbrirAjustes,
                modifier = Modifier.fillMaxWidth().heightIn(min = Tonos.boton),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f)),
            ) {
                Text(stringResource(R.string.escaner_abrir_ajustes), fontSize = 18.sp)
            }
            Text(
                stringResource(R.string.escaner_sin_permiso_ajustes),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
