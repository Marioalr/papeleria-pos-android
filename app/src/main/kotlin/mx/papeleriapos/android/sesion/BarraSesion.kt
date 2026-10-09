package mx.papeleriapos.android.sesion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Los resultados con que el operador corrige el último intento leído. */
val MARCAS_DE_LECTURA = listOf(
    ResultadoIntento.OK, ResultadoIntento.LECTURA_INCORRECTA, ResultadoIntento.FALSO_POSITIVO, ResultadoIntento.DUPLICADO,
)

/**
 * La sesión de prueba sobre el visor: el último intento con su resultado (se corrige con un toque) y los botones para
 * registrar los que no leyeron, sin salir del escáner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BarraSesion(
    sesion: SesionPrueba,
    marcar: (ResultadoIntento) -> Unit,
    registrarFallo: (ResultadoIntento) -> Unit,
    cambiarEsperado: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editando by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.testTag("barra-sesion"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val u = sesion.ultimo
                Text(
                    "Prueba · ${sesion.intentos.size} intentos" + (u?.let { " · #${it.n} ${it.resultado.etiqueta}" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editando = true }) {
                    Text(
                        if (sesion.esperado.isEmpty()) "Esperado…" else "Esp.: ${sesion.esperado}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val u = sesion.ultimo
            val ultimoLeido = u != null && u.simbologia.isNotEmpty()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in MARCAS_DE_LECTURA) {
                    FilterChip(
                        selected = ultimoLeido && u?.resultado == r,
                        onClick = { marcar(r) },
                        enabled = ultimoLeido,
                        label = { Text(r.etiqueta) },
                        modifier = Modifier.testTag("marcar-${r.name}"),
                    )
                }
                AssistChip(
                    onClick = { registrarFallo(ResultadoIntento.TIMEOUT) },
                    label = { Text("No leyó") },
                    modifier = Modifier.testTag("fallo-TIMEOUT"),
                )
                AssistChip(
                    onClick = { registrarFallo(ResultadoIntento.CANCELADO) },
                    label = { Text("Cancelado") },
                    modifier = Modifier.testTag("fallo-CANCELADO"),
                )
            }
        }
    }
    if (editando) {
        DialogoEsperado(sesion.esperado, alTerminar = { nuevo -> nuevo?.let(cambiarEsperado); editando = false })
    }
}

/** Para teclear el código que se va a escanear; null = se canceló. */
@Composable
fun DialogoEsperado(actual: String, alTerminar: (String?) -> Unit) {
    var texto by remember { mutableStateOf(actual) }
    AlertDialog(
        onDismissRequest = { alTerminar(null) },
        title = { Text("Código esperado") },
        text = {
            Column {
                Text(
                    "Se compara con cada lectura hasta que lo cambies. Déjalo vacío para no comparar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.trim() },
                    singleLine = true,
                    label = { Text("Código") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { alTerminar(texto) }),
                    modifier = Modifier.padding(top = 8.dp).testTag("campo-esperado"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { alTerminar(texto) }) { Text("Listo") } },
        dismissButton = { TextButton(onClick = { alTerminar("") }) { Text("Quitar") } },
    )
}
