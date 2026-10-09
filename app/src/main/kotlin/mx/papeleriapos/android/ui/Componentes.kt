package mx.papeleriapos.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Alto mínimo de lo que se toca con el celular en una mano (docs/escaner.md, «Accesibilidad»). */
val ALTO_TOCABLE = 56.dp

@Composable
fun TituloSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

/** Un renglón con interruptor: se toca todo el renglón, no solo el switch. */
@Composable
fun FilaInterruptor(
    titulo: String,
    valor: Boolean,
    alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    descripcion: String? = null,
    habilitado: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_TOCABLE)
            .toggleable(value = valor, enabled = habilitado, role = Role.Switch, onValueChange = alCambiar)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            if (descripcion != null) {
                Text(descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        // El renglón ya maneja el toque; el switch solo muestra el estado.
        Switch(checked = valor, onCheckedChange = null, enabled = habilitado)
    }
}

/** Elegir una opción entre pocas (motor, modo). */
@Composable
fun <T> Opciones(
    opciones: List<T>,
    elegida: T,
    etiqueta: (T) -> String,
    alElegir: (T) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        opciones.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == elegida,
                onClick = { alElegir(o) },
                enabled = habilitado,
                shape = SegmentedButtonDefaults.itemShape(i, opciones.size),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(etiqueta(o)) }
        }
    }
}

@Composable
fun FilaDato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}
