package mx.papeleriapos.android.sesion

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.ajustes.etiqueta
import mx.papeleriapos.android.exportar.Compartir
import mx.papeleriapos.android.tema.LocalColoresEstado
import mx.papeleriapos.android.ui.ALTO_TOCABLE
import mx.papeleriapos.android.ui.FilaDato
import mx.papeleriapos.android.ui.TituloSeccion
import mx.papeleriapos.escaner.ConfigEscaner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** La sesión de prueba para la tienda (sábado y domingo): iniciar, marcar, registrar fallos y exportar el CSV. */
@Composable
fun PantallaSesion(vm: EscanerViewModel) {
    val sesion by vm.sesion.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val s = sesion
    if (s == null) {
        SinSesion(alIniciar = vm::iniciarSesion)
    } else {
        ConSesion(
            sesion = s,
            config = config,
            alCambiarEsperado = vm::cambiarEsperado,
            alMarcar = vm::marcarUltimo,
            alFallar = vm::registrarFallo,
            alExportar = {
                alcance.launch {
                    vm.csvSesion()?.let { Compartir.enviar(contexto, it, "Sesión de prueba ${s.nombre}") }
                }
            },
            alTerminar = vm::terminarSesion,
        )
    }
}

@Composable
fun SinSesion(alIniciar: (String) -> Unit) {
    var nombre by rememberSaveable { mutableStateOf(Build.MODEL ?: "") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Cada lectura del escáner queda como un intento. Marca los que salgan mal y registra los que no lean; al " +
                "final exporta el CSV y mándalo.",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Celular y operador") },
            supportingText = { Text("Por ejemplo: Moto G24 Lupita") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("campo-nombre"),
        )
        Button(
            onClick = { alIniciar(nombre) },
            modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("iniciar-sesion"),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Iniciar sesión")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConSesion(
    sesion: SesionPrueba,
    config: ConfigEscaner,
    alCambiarEsperado: (String) -> Unit,
    alMarcar: (ResultadoIntento) -> Unit,
    alFallar: (ResultadoIntento) -> Unit,
    alExportar: () -> Unit,
    alTerminar: () -> Unit,
) {
    var confirmarFin by remember { mutableStateOf(false) }
    val hora = remember { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }
    val contadores = sesion.contadores()
    LazyColumn(
        Modifier.fillMaxSize().testTag("pantalla-sesion"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(sesion.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FilaDato("Inicio", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).format(Date(sesion.inicio)))
                    FilaDato("Motor y modo", "${config.motor.etiqueta} · ${config.modo.etiqueta}")
                    FilaDato("Cuadros por segundo", if (config.fpsFijo30) "30 fijos" else "15 a 30")
                    FilaDato("Intentos", sesion.intentos.size.toString())
                }
            }
        }
        item {
            OutlinedTextField(
                value = sesion.esperado,
                onValueChange = { alCambiarEsperado(it.trim()) },
                label = { Text("Código esperado (opcional)") },
                supportingText = { Text("Se compara con cada lectura hasta que lo cambies.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth().testTag("campo-esperado"),
            )
        }
        item {
            TituloSeccion("Último intento")
            val u = sesion.ultimo
            if (u == null) {
                Text("Todavía no hay intentos. Ve a Escanear.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "#${u.n} · ${hora.format(Date(u.hora))} · " +
                        (if (u.simbologia.isEmpty()) u.resultado.etiqueta else "${u.simbologia} ${u.valor}"),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val leido = u.simbologia.isNotEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in MARCAS_DE_LECTURA) {
                        FilterChip(
                            selected = leido && u.resultado == r,
                            onClick = { alMarcar(r) },
                            enabled = leido,
                            label = { Text(r.etiqueta) },
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { alFallar(ResultadoIntento.TIMEOUT) },
                    modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE).testTag("registrar-timeout"),
                ) {
                    Icon(Icons.Filled.TimerOff, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("No leyó")
                }
                OutlinedButton(
                    onClick = { alFallar(ResultadoIntento.CANCELADO) },
                    modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE),
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Cancelado")
                }
            }
        }
        item {
            TituloSeccion("Resumen")
            val leidos = sesion.intentos.size
            val bien = contadores.getValue(ResultadoIntento.OK)
            FilaDato("Leídos bien", if (leidos == 0) "—" else "$bien de $leidos (${bien * 100 / leidos} %)")
            for ((r, n) in contadores) if (r != ResultadoIntento.OK) FilaDato(r.etiqueta, n.toString())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = alExportar,
                    enabled = sesion.intentos.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE).testTag("exportar-sesion"),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Exportar CSV")
                }
                OutlinedButton(
                    onClick = { confirmarFin = true },
                    modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE).testTag("terminar-sesion"),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Terminar")
                }
            }
        }
        if (sesion.intentos.isNotEmpty()) item { TituloSeccion("Intentos (el último arriba)") }
        items(sesion.intentos.asReversed(), key = { it.n }) { i ->
            FilaIntento(i, hora.format(Date(i.hora)))
            HorizontalDivider()
        }
    }
    if (confirmarFin) {
        AlertDialog(
            onDismissRequest = { confirmarFin = false },
            title = { Text("¿Terminar la sesión?") },
            text = {
                Text("Se borran los ${sesion.intentos.size} intentos de este celular. Si no has exportado el CSV, hazlo antes.")
            },
            confirmButton = {
                TextButton(onClick = { confirmarFin = false; alTerminar() }, modifier = Modifier.testTag("confirmar-terminar")) {
                    Text("Terminar")
                }
            },
            dismissButton = { TextButton(onClick = { confirmarFin = false }) { Text("Seguir") } },
        )
    }
}

@Composable
private fun FilaIntento(i: Intento, hora: String) {
    val colores = LocalColoresEstado.current
    val color = when (i.resultado) {
        ResultadoIntento.OK -> colores.exito
        ResultadoIntento.LECTURA_INCORRECTA, ResultadoIntento.FALSO_POSITIVO -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#${i.n}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (i.simbologia.isEmpty()) "—" else i.valor,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(hora, i.simbologia.ifEmpty { null }, i.msDesdeCandidato.takeIf { it >= 0 }?.let { "$it ms" })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(i.resultado.etiqueta, color = color, style = MaterialTheme.typography.labelLarge)
    }
}
