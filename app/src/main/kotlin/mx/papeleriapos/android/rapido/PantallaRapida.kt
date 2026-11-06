package mx.papeleriapos.android.rapido

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.android.catalogo.pzs
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.principal.LETRA_GRANDE
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.escaner.ui.PantallaEscaner

/** El escáner a pantalla completa con lo leído en esta apertura abajo y la salida a la app completa. */
@Composable
internal fun PantallaRapida(vm: EscaneoRapidoViewModel, alAbrirApp: () -> Unit, alCerrar: () -> Unit) {
    // El de ahora: esta pantalla puede volver después de que la app cambió de modo.
    val modo by vm.modo.collectAsStateWithLifecycle()
    val aqui by vm.aqui.collectAsStateWithLifecycle()
    val catalogo by vm.catalogo.collectAsStateWithLifecycle()
    PantallaEscaner(vm.controlador, Modifier.fillMaxSize()) {
        PanelRapido(modo, aqui, catalogo, alAbrirApp, alCerrar)
    }
}

/**
 * Lo mínimo sobre el bloqueo: cuántas piezas se leyeron en esta apertura y la última (para confirmar lo que se escaneó).
 * El ticket o el conteo completos (que pueden traer lecturas de antes) solo se ven en la app, ya desbloqueada; ahí
 * también se corrige, se vacía o se exporta. En Precio, lo leído con su nombre y su precio (si ya están los productos):
 * nada se suma. No pasa del 31 % del alto para no tapar la franja (del 32 % al 68 %).
 *
 * Con la letra grande no cabe todo en ese 31 % (docs/propuesta-interfaz.md, «Una mano, letra grande y TalkBack»): cede
 * lo leído, con el nombre en un renglón, y no los botones, que siguen de 56 dp; «Cerrar» se queda con su ícono para que
 * «Abrir la app» quepa entero.
 */
@Composable
private fun BoxScope.PanelRapido(
    modo: ModoApp,
    lista: ListaLecturas,
    catalogo: CatalogoDeProductos?,
    alAbrirApp: () -> Unit,
    alCerrar: () -> Unit,
) {
    val grande = LocalDensity.current.fontScale >= LETRA_GRANDE
    val actual = lista.actual
    val producto = if (modo == ModoApp.PRECIO && actual != null) catalogo?.de(actual) else null
    BoxWithConstraints(Modifier.matchParentSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.31f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            // Con el alfa ya no es un color del tema: sin esto el texto sale con el negro por defecto.
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Box {
                Column(
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val leidas = if (lista.total == 1) "leída" else "leídas"
                    Text(
                        if (modo == ModoApp.PRECIO) modo.nombre else "${modo.nombre} · ${pzs(lista.total)} $leidas ahora",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.testTag("rapido-total"),
                    )
                    // Si no cabe todo, cede esto y no los botones. En Ticket y Conteo se anuncia aquí (el ×N cambia con
                    // cada lectura); en Precio, en [AnuncioPrecio].
                    Box(
                        Modifier
                            .weight(1f, fill = false)
                            .then(if (modo == ModoApp.PRECIO) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite }),
                    ) {
                        if (producto != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    producto.nombre,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = if (grande) 1 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).testTag("rapido-producto"),
                                )
                                Text(pesos(producto.precio), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("rapido-precio"))
                            }
                        } else if (actual != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    actual.codigo,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).testTag("rapido-ultimo"),
                                )
                                // En Precio no se cuenta: si no está (o los productos no llegan), solo el código.
                                if (modo != ModoApp.PRECIO) Text("×${actual.cantidad}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                else if (catalogo != null) Text("No está", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Text("Apunta a un código de barras", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (grande) {
                            OutlinedIconButton(onClick = alCerrar, modifier = Modifier.size(56.dp).testTag("rapido-cerrar")) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                            }
                        } else {
                            OutlinedButton(onClick = alCerrar, modifier = Modifier.heightIn(min = 56.dp).testTag("rapido-cerrar")) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                                Text("Cerrar", Modifier.padding(start = 6.dp), maxLines = 1)
                            }
                        }
                        Button(
                            onClick = alAbrirApp,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("rapido-abrir-app"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Text("Abrir la app", Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (modo == ModoApp.PRECIO) AnuncioPrecio(lista, producto, catalogo, Modifier.align(Alignment.TopStart))
            }
        }
    }
}

/**
 * En Precio, lo leído para TalkBack (docs/propuesta-interfaz.md, «Accesibilidad»: cada lectura se anuncia): una región viva
 * que no se ve, de 1 dp, como el anuncio de la pantalla principal. El panel no basta: con el mismo producto otra vez queda
 * igual y TalkBack no repetiría el precio. Un carácter que no se oye cambia con cada lectura (el total de esta apertura
 * sube con cada una). Mientras cargan los productos no dice nada: lo dice al llegar.
 */
@Composable
private fun AnuncioPrecio(lista: ListaLecturas, producto: Producto?, catalogo: CatalogoDeProductos?, modifier: Modifier = Modifier) {
    val texto = when {
        producto != null -> "${producto.nombre}, ${pesos(producto.precio)}"
        lista.actual != null && catalogo != null -> "No está en el catálogo"
        else -> null
    }?.let { if (lista.total % 2 == 0) it else it + "\u200B" }
    Box(
        modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (texto != null) contentDescription = texto
            }
            .testTag("rapido-anuncio"),
    )
}
