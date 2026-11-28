package mx.papeleriapos.android.principal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.papeleriapos.android.catalogo.pzs
import mx.papeleriapos.android.tema.LocalColoresModo

/**
 * «¿A qué caja va el conteo?»: cada caja de la sucursal con quién atiende y cuántas listas esperan ahí; la que no recibe
 * (sin turno, o quien atiende no puede aceptar conteos) sale apagada con la razón que da el POS. Se elige y se toca
 * «Mandar»; abajo, «Volver a vender sin mandar» deja lo contado guardado en el celular.
 */
@Composable
fun HojaMandarConteo(
    estado: EstadoMandarConteo,
    contadas: Int,
    productos: Int,
    alCargar: () -> Unit,
    alElegir: (String) -> Unit,
    alMandar: () -> Unit,
    alVolverAVender: () -> Unit,
    /** «¿Estás en Sucursal 2? Cambiar» ([BotonesOtraSucursal]), cuando no contestó la caja principal. */
    otraSucursal: @Composable () -> Unit = {},
    alCerrar: () -> Unit,
) {
    val color = LocalColoresModo.current.conteo
    var listo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        alCargar()
        // Como «Mandar» del ticket: el toque que abrió la hoja no cae en el botón.
        delay(MS_REBOTE_MANDAR)
        listo = true
    }
    HojaCompleta("hoja-mandar-conteo") {
        BarraHoja("¿A qué caja va el conteo?", "${pzs(contadas)} · ${if (productos == 1) "1 producto" else "$productos productos"}", alCerrar)
        val cajas = estado.cajas
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 4.dp)) {
            Text(
                "Ahí lo revisa quien puede aceptar conteos: lo aplica tal cual, con cambios, o lo rechaza. El celular no cambia " +
                    "existencias. Cada cantidad es lo que hay en total de ese producto; lo que no contaste no se toca.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            when {
                cajas == null -> Text("Buscando las cajas…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp).testTag("conteo-buscando"))
                else -> for (c in cajas) {
                    val detalle = when {
                        !c.recibe -> c.porQue ?: "No recibe conteos ahora."
                        else -> listOfNotNull(
                            c.atiende?.let { "Atiende $it" },
                            c.pendientes.takeIf { it > 0 }?.let { if (it == 1) "1 conteo por revisar" else "$it conteos por revisar" },
                        ).joinToString(" · ").ifEmpty { "Lista para recibir" }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .selectable(selected = c.id == estado.elegida, enabled = c.recibe && !estado.mandando, role = Role.RadioButton) { alElegir(c.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("conteo-caja-${c.serie}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c.id == estado.elegida, onClick = null, enabled = c.recibe)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nombre, style = MaterialTheme.typography.bodyLarge, color = if (c.recibe) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (cajas != null) {
                TextButton(onClick = alCargar, enabled = !estado.mandando, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp).testTag("conteo-actualizar")) {
                    Text("Volver a buscar")
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                estado.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("conteo-error"),
                    )
                }
                if (estado.otraSucursal && !estado.mandando) otraSucursal()
                val elegida = estado.cajaElegida
                Button(
                    onClick = alMandar,
                    enabled = listo && estado.puedeMandar,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("mandar-conteo"),
                    colors = ButtonDefaults.buttonColors(containerColor = color.color, contentColor = color.enColor),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            estado.mandando -> "Mandando…"
                            elegida != null -> "Mandar a ${elegida.nombre}"
                            else -> "Mandar"
                        },
                        maxLines = 1,
                    )
                }
                TextButton(
                    onClick = alVolverAVender,
                    enabled = !estado.mandando,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("conteo-sin-mandar"),
                ) { Text("Volver a vender sin mandar") }
            }
        }
    }
}
