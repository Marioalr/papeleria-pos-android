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
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Los resultados con que el operador corrige el último intento leído. */
val MARCAS_DE_LECTURA = listOf(
    ResultadoIntento.OK, ResultadoIntento.LECTURA_INCORRECTA, ResultadoIntento.FALSO_POSITIVO, ResultadoIntento.DUPLICADO,
)

/**
 * Lo que mide la barra completa: el renglón del título (40 dp de botón de texto), el de las acciones (48 dp, lo mínimo
 * para el pulgar), el espacio entre ellos y el relleno. Si arriba de la franja no cabe, va en una sola fila.
 */
val ALTO_BARRA_COMPLETA: Dp = 108.dp

/**
 * La sesión de prueba sobre el visor, sin salir del escáner: el último intento con su resultado (se corrige con un
 * toque), el botón grande para registrar el que no leyó y deshacerlo, la condición del bloque y una nota para el último
 * intento. Mide [ALTO_BARRA_COMPLETA] (dos filas, ~104 dp) o, [compacta], una sola fila de ~60 dp con lo que se toca:
 * así no tapa la franja (que empieza en el 32 % del alto con la franja por omisión). Las acciones van en una fila que se
 * desliza para no partirse en dos.
 */
@Composable
fun BarraSesion(
    sesion: SesionPrueba,
    marcar: (ResultadoIntento) -> Unit,
    registrarFallo: (ResultadoIntento) -> Unit,
    cambiarEsperado: (String) -> Unit,
    modifier: Modifier = Modifier,
    deshacerFallo: () -> Unit = {},
    compacta: Boolean = false,
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
        // Con el alfa ya no es un color del tema: sin esto el texto sale con el negro por defecto.
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val u = sesion.ultimo
            if (!compacta) {
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
            }
            val ultimoLeido = u != null && u.simbologia.isNotEmpty()
            // En una sola línea que se desliza: si se partiera en dos taparía la franja.
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (compacta) {
                    Text("#${sesion.intentos.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("intentos-compacta"))
                }
                BotonNoLeyo(alPulsar = { registrarFallo(ResultadoIntento.TIMEOUT) })
                AssistChip(
                    onClick = deshacerFallo,
                    enabled = sesion.puedeDeshacerFallo,
                    label = { Text("Deshacer") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("deshacer-fallo"),
                )
                SelectorCondicion(notas.condicionActual, alElegir = { editor.condicion(sesion, it) })
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
                if (compacta) {
                    TextButton(onClick = { editando = true }) { Text(if (sesion.esperado.isEmpty()) "Esperado…" else "Esp.") }
                }
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

/**
 * «No leyó»: el resultado que más se registra a mano, en el color de error y de 48 dp o más para no fallarle con el
 * pulgar. Un segundo toque a menos de 1 s no cuenta (lo ignora el ViewModel) y el último se puede deshacer.
 */
@Composable
fun BotonNoLeyo(alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = alPulsar,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier.heightIn(min = 48.dp).testTag("fallo-TIMEOUT"),
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
