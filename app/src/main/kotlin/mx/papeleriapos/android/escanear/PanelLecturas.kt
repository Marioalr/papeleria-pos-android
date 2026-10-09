package mx.papeleriapos.android.escanear

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.papeleriapos.android.ajustes.etiqueta
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.lista.Renglon
import mx.papeleriapos.android.ui.ALTO_TOCABLE
import mx.papeleriapos.android.ui.Opciones
import mx.papeleriapos.escaner.nucleo.Modo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** El panel de abajo: selector de modo y la lista de venta o el contador de conteo. */
@Composable
fun PanelLecturas(modo: Modo, lista: ListaLecturas, acciones: AccionesEscaner, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Opciones(
                    opciones = Modo.entries,
                    elegida = modo,
                    etiqueta = { it.etiqueta },
                    alElegir = acciones.cambiarModo,
                    modifier = Modifier.weight(1f).testTag("selector-modo"),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${lista.total} pzs",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("total"),
                )
                IconButton(onClick = acciones.vaciar, enabled = lista.renglones.isNotEmpty()) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Vaciar la lista")
                }
            }
            when (modo) {
                Modo.VENTA -> Venta(lista, acciones)
                Modo.CONTEO -> Conteo(lista, acciones)
            }
        }
    }
}

@Composable
private fun Venta(lista: ListaLecturas, acciones: AccionesEscaner) {
    Column {
        ListaRenglones(lista, Modifier.weight(1f, fill = false))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val hay = lista.actual != null
            FilledTonalButton(
                onClick = acciones.masUno, enabled = hay,
                modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE).testTag("mas-uno"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null); Text("1", fontSize = 18.sp)
            }
            FilledTonalButton(
                onClick = acciones.menosUno, enabled = hay,
                modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE).testTag("menos-uno"),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null); Text("1", fontSize = 18.sp)
            }
            BotonDeshacer(lista, acciones, Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun Conteo(lista: ListaLecturas, acciones: AccionesEscaner) {
    val actual = lista.actual
    val escala = remember { Animatable(1f) }
    var totalAnterior by remember { mutableIntStateOf(lista.total) }
    // Crece un instante (150 ms) con cada suma; al restar o deshacer no.
    LaunchedEffect(lista.total) {
        if (lista.total > totalAnterior) {
            escala.snapTo(1f)
            escala.animateTo(1.3f, tween(75))
            escala.animateTo(1f, tween(75))
        }
        totalAnterior = lista.total
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (actual != null) "×${actual.cantidad}" else "×0",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .graphicsLayer { scaleX = escala.value; scaleY = escala.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("contador"),
                )
                Text(
                    actual?.let { "${it.codigo} · ${it.simbologia.nombre}" } ?: "Escanea para contar",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(
                onClick = acciones.menosUno, enabled = actual != null,
                modifier = Modifier.heightIn(min = ALTO_TOCABLE).testTag("menos-uno"),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null); Text("1", fontSize = 18.sp)
            }
            Spacer(Modifier.width(8.dp))
            BotonDeshacer(lista, acciones)
        }
        ListaRenglones(lista, Modifier.weight(1f, fill = false).padding(top = 4.dp))
    }
}

@Composable
private fun BotonDeshacer(lista: ListaLecturas, acciones: AccionesEscaner, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = acciones.deshacer, enabled = lista.puedeDeshacer,
        modifier = modifier.heightIn(min = ALTO_TOCABLE).testTag("deshacer"),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text("Deshacer")
    }
}

/** Los códigos leídos, el último arriba y resaltado. */
@Composable
private fun ListaRenglones(lista: ListaLecturas, modifier: Modifier = Modifier) {
    val estado = rememberLazyListState()
    // Al sumar, el renglón sube: se regresa arriba para que el último siempre se vea.
    LaunchedEffect(lista.actual?.id, lista.actual?.cantidad) { if (lista.renglones.isNotEmpty()) estado.scrollToItem(0) }
    if (lista.renglones.isEmpty()) {
        Text(
            "Lo que escanees aparece aquí.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 10.dp),
        )
        return
    }
    val hora = remember { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }
    LazyColumn(modifier.testTag("lista"), state = estado) {
        items(lista.renglones, key = { it.id }) { r ->
            FilaRenglon(r, ultimo = r.id == lista.actual?.id, hora = hora.format(Date(r.hora)))
        }
    }
}

@Composable
private fun FilaRenglon(r: Renglon, ultimo: Boolean, hora: String) {
    val fondo = if (ultimo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    val texto = if (ultimo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .background(fondo, RoundedCornerShape(8.dp))
            .heightIn(min = 40.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${r.codigo}, ${r.simbologia.nombre}, ${r.cantidad} piezas, $hora"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                r.codigo,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (ultimo) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (ultimo) 18.sp else 15.sp,
                color = texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${r.simbologia.nombre} · $hora", style = MaterialTheme.typography.bodySmall, color = texto.copy(alpha = 0.75f))
        }
        Text(
            "×${r.cantidad}",
            fontWeight = FontWeight.Bold,
            fontSize = if (ultimo) 20.sp else 16.sp,
            color = texto,
        )
    }
}
