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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.ui.PantallaEscaner

/** El escáner a pantalla completa con lo leído en esta apertura abajo y la salida a la app completa. */
@Composable
internal fun PantallaRapida(vm: EscaneoRapidoViewModel, alAbrirApp: () -> Unit, alCerrar: () -> Unit) {
    val aqui by vm.aqui.collectAsStateWithLifecycle()
    PantallaEscaner(vm.controlador, Modifier.fillMaxSize()) {
        PanelRapido(vm.modo, aqui, alAbrirApp, alCerrar)
    }
}

/**
 * Lo mínimo sobre el bloqueo: cuántas piezas se leyeron en esta apertura y la última (para confirmar lo que se escaneó).
 * La venta o el conteo completos (que pueden traer lecturas de antes) solo se ven en la app, ya desbloqueada; ahí
 * también se corrige, se vacía o se exporta. No pasa del 31 % del alto para no tapar la franja (del 32 % al 68 %).
 */
@Composable
private fun BoxScope.PanelRapido(modo: Modo, lista: ListaLecturas, alAbrirApp: () -> Unit, alCerrar: () -> Unit) {
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
            Column(
                Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${if (modo == Modo.VENTA) "Venta" else "Conteo"} · ${lista.total} pzs leídas ahora",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("rapido-total"),
                )
                val actual = lista.actual
                Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    if (actual != null) {
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
                            Text("×${actual.cantidad}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Apunta a un código de barras", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = alCerrar, modifier = Modifier.heightIn(min = 56.dp).testTag("rapido-cerrar")) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Text("Cerrar", Modifier.padding(start = 6.dp))
                    }
                    Button(
                        onClick = alAbrirApp,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("rapido-abrir-app"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Text("Abrir la app", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}
