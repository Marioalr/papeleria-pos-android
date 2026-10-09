package mx.papeleriapos.android.sesion

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.ui.ALTO_TOCABLE

/** Los resultados con que el operador corrige el último intento leído. */
val MARCAS_DE_LECTURA = listOf(
    ResultadoIntento.OK, ResultadoIntento.LECTURA_INCORRECTA, ResultadoIntento.FALSO_POSITIVO, ResultadoIntento.DUPLICADO,
)

/**
 * La sesión de prueba sobre el visor: el último intento con su resultado (se corrige con un toque), la condición del
 * bloque, una nota para el último intento y el botón grande para registrar el que no leyó, sin salir del escáner. No
 * pasa de ~150 dp de alto para no tapar la franja (del 32 % al 68 % del alto).
 */
@Composable
fun BarraSesion(
    sesion: SesionPrueba,
    marcar: (ResultadoIntento) -> Unit,
    registrarFallo: (ResultadoIntento) -> Unit,
    cambiarEsperado: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anotaciones by AnotacionesSesion.estado.collectAsStateWithLifecycle()
    val notas = anotaciones.de(sesion)
    val editor = recordarEditorAnotaciones()
    var editando by remember { mutableStateOf(false) }
    var anotando by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.testTag("barra-sesion"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            val u = sesion.ultimo
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            val ultimoLeido = u != null && u.simbologia.isNotEmpty()
            // En una sola línea que se desliza: si se partiera en dos taparía la franja.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    onClick = { registrarFallo(ResultadoIntento.CANCELADO) },
                    label = { Text("Cancelado") },
                    modifier = Modifier.testTag("fallo-CANCELADO"),
                )
                AssistChip(
                    onClick = { anotando = true },
                    enabled = u != null,
                    label = { Text(if (u != null && notas.notaDe(u.n).isNotEmpty()) "Nota ✓" else "Nota") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                    modifier = Modifier.testTag("nota-ultimo"),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                SelectorCondicion(notas.condicionActual, alElegir = { editor.condicion(sesion, it) })
                Spacer(Modifier.width(8.dp))
                BotonNoLeyo(alPulsar = { registrarFallo(ResultadoIntento.TIMEOUT) }, modifier = Modifier.weight(1f))
            }
        }
    }
    if (editando) {
        DialogoEsperado(sesion.esperado, alTerminar = { nuevo -> nuevo?.let(cambiarEsperado); editando = false })
    }
    val u = sesion.ultimo
    if (anotando && u != null) {
        DialogoNota(u.n, notas.notaDe(u.n), alTerminar = { texto -> texto?.let { editor.nota(sesion, u.n, it) }; anotando = false })
    }
}

/** «No leyó»: el resultado que más se registra a mano, en grande y en el color de error para no fallarle con el pulgar. */
@Composable
fun BotonNoLeyo(alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = alPulsar,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier.heightIn(min = ALTO_TOCABLE).testTag("fallo-TIMEOUT"),
    ) {
        Icon(Icons.Filled.TimerOff, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("No leyó", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** La condición del bloque que sigue, en un menú (normal, poca luz, reflejo…). */
@Composable
fun SelectorCondicion(actual: Condicion, alElegir: (Condicion) -> Unit, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { abierto = true },
            label = { Text(actual.etiqueta, maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = "Condición del bloque") },
            modifier = Modifier.heightIn(min = 48.dp).testTag("condicion-bloque"),
        )
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            for (c in Condicion.entries) {
                DropdownMenuItem(
                    text = { Text(c.etiqueta, fontWeight = if (c == actual) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { abierto = false; alElegir(c) },
                    modifier = Modifier.testTag("condicion-${c.name}"),
                )
            }
        }
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

/** La nota libre de un intento («la etiqueta estaba arrugada»); null = se canceló, vacío = se borra. */
@Composable
fun DialogoNota(n: Int, actual: String, alTerminar: (String?) -> Unit) {
    var texto by remember { mutableStateOf(actual) }
    AlertDialog(
        onDismissRequest = { alTerminar(null) },
        title = { Text("Nota del intento #$n") },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it.take(200) },
                label = { Text("Qué pasó (opcional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { alTerminar(texto) }),
                modifier = Modifier.testTag("campo-nota"),
            )
        },
        confirmButton = { TextButton(onClick = { alTerminar(texto) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { alTerminar(null) }) { Text("Cancelar") } },
    )
}
