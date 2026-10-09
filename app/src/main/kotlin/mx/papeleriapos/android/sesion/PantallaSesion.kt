package mx.papeleriapos.android.sesion

import android.os.Build
import androidx.compose.foundation.clickable
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
import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.exportar.Compartir
import mx.papeleriapos.android.medicion.MonitorDeSesion
import mx.papeleriapos.android.medicion.MuestraRecursos
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
    val anotaciones by AnotacionesSesion.estado.collectAsStateWithLifecycle()
    val recursos by MonitorDeSesion.ultima.collectAsStateWithLifecycle()
    val editor = recordarEditorAnotaciones()
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
                    val uri = ExportarSesion.intentos(contexto, s, vm.equipo)
                    Compartir.enviar(contexto, uri, "Sesión de prueba ${s.nombre}")
                }
            },
            alTerminar = vm::terminarSesion,
            anotaciones = anotaciones.de(s),
            alCambiarCondicion = { editor.condicion(s, it) },
            alAnotar = { n, texto -> editor.nota(s, n, texto) },
            recursos = recursos,
            alExportarRecursos = {
                alcance.launch {
                    ExportarSesion.recursos(contexto, s)?.let { Compartir.enviar(contexto, it, "Recursos de la sesión ${s.nombre}") }
                }
            },
        )
    }
}

@Composable
fun SinSesion(alIniciar: (String) -> Unit) {
    var nombre by rememberSaveable { mutableStateOf(Build.MODEL ?: "") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Cada lectura del escáner queda como un intento. Antes de cada bloque elige la condición (poca luz, " +
                "reflejo…); marca los que salgan mal y registra los que no lean. Mientras dure la sesión se miden CPU, " +
                "memoria, batería y temperatura cada segundo. Al final exporta los dos CSV y mándalos.",
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
    anotaciones: Anotaciones = Anotaciones(),
    alCambiarCondicion: (Condicion) -> Unit = {},
    alAnotar: (Int, String) -> Unit = { _, _ -> },
    recursos: MuestraRecursos? = null,
    alExportarRecursos: () -> Unit = {},
) {
    var confirmarFin by remember { mutableStateOf(false) }
    var anotando by remember { mutableStateOf<Intento?>(null) }
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
            TituloSeccion("Condición del bloque")
            Text(
                "Elígela antes de escanear el bloque; rige para los intentos que siguen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("condiciones")) {
                for (c in Condicion.entries) {
                    FilterChip(
                        selected = anotaciones.condicionActual == c,
                        onClick = { alCambiarCondicion(c) },
                        label = { Text(c.etiqueta) },
                        modifier = Modifier.testTag("condicion-${c.name}"),
                    )
                }
            }
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
                    FilterChip(
                        selected = anotaciones.notaDe(u.n).isNotEmpty(),
                        onClick = { anotando = u },
                        label = { Text("Nota") },
                    )
                }
            }
        }
        item {
            BotonNoLeyo(
                alPulsar = { alFallar(ResultadoIntento.TIMEOUT) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("registrar-timeout"),
            )
            OutlinedButton(
                onClick = { alFallar(ResultadoIntento.CANCELADO) },
                modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).padding(top = 8.dp),
            ) {
                Icon(Icons.Filled.Cancel, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Cancelado")
            }
        }
        item { TarjetaRecursos(recursos) }
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
                    Icon(Icons.Filled.Share, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Intentos")
                }
                OutlinedButton(
                    onClick = alExportarRecursos,
                    modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE).testTag("exportar-recursos"),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Recursos")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
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
            FilaIntento(i, hora.format(Date(i.hora)), anotaciones.condicionDe(i.n), anotaciones.notaDe(i.n), alTocar = { anotando = i })
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
    anotando?.let { i ->
        DialogoNota(i.n, anotaciones.notaDe(i.n), alTerminar = { texto -> texto?.let { alAnotar(i.n, it) }; anotando = null })
    }
}

/** Lo último que midió el monitor de la sesión, para ver en vivo que está midiendo. */
@Composable
private fun TarjetaRecursos(m: MuestraRecursos?) {
    TituloSeccion("Recursos (cada segundo)")
    if (m == null) {
        Text("Midiendo…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    fun real(v: Double, d: Int = 1) = if (v.isNaN()) "—" else Csv.decimal(v, d)
    FilaDato("CPU (% de un núcleo)", real(m.cpuPctNucleo))
    FilaDato("Cuadros/s cámara · análisis", "${real(m.fpsCamara)} · ${real(m.fpsAnalisis)}")
    FilaDato(
        "Memoria Java · nativa · RSS",
        "${m.heapJavaKb / 1024} · ${m.heapNativoKb / 1024} · ${if (m.rssKb >= 0) (m.rssKb / 1024).toString() else "—"} MB",
    )
    FilaDato("Hilos · descriptores", "${if (m.hilos >= 0) m.hilos.toString() else "—"} · ${if (m.fds >= 0) m.fds.toString() else "—"}")
    FilaDato(
        "Batería",
        buildString {
            if (m.bateriaPct in 0..100) append("${m.bateriaPct} %")
            if (m.tempBatDc != Int.MIN_VALUE) append(" · ${Csv.decimal(m.tempBatDc / 10.0, 1)} °C")
            if (!m.potenciaMw.isNaN()) append(" · ${m.potenciaMw.toInt()} mW")
            if (m.enchufado) append(" · conectado")
        }.ifEmpty { "—" },
    )
    FilaDato("Estado térmico", if (m.termico >= 0) m.termico.toString() else "—")
}

@Composable
private fun FilaIntento(i: Intento, hora: String, condicion: Condicion, nota: String, alTocar: () -> Unit) {
    val colores = LocalColoresEstado.current
    val color = when (i.resultado) {
        ResultadoIntento.OK -> colores.exito
        ResultadoIntento.LECTURA_INCORRECTA, ResultadoIntento.FALSO_POSITIVO -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Agregar nota", onClick = alTocar).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#${i.n}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (i.simbologia.isEmpty()) "—" else i.valor,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    hora,
                    i.simbologia.ifEmpty { null },
                    i.via.ifEmpty { null }?.lowercase(),
                    i.msDesdeCandidato.takeIf { it >= 0 }?.let { "$it ms" },
                    condicion.takeIf { it != Condicion.NORMAL }?.etiqueta,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (nota.isNotEmpty()) {
                Text("«$nota»", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(i.resultado.etiqueta, color = color, style = MaterialTheme.typography.labelLarge)
    }
}
