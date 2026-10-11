package mx.papeleriapos.android.banco

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.exportar.Compartir
import mx.papeleriapos.android.tema.LocalColoresEstado
import mx.papeleriapos.android.ui.ALTO_TOCABLE
import mx.papeleriapos.android.ui.FilaInterruptor
import mx.papeleriapos.android.ui.Opciones
import mx.papeleriapos.android.ui.TituloSeccion
import mx.papeleriapos.escaner.Motor

/** El banco de pruebas: escenas sintéticas por cada motor, con aciertos, errores y tiempos. */
@Composable
fun PantallaBanco(vm: BancoViewModel) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    ContenidoBanco(
        e,
        alCambiarRecorte = vm::cambiarRecorte,
        alAlternarMotor = vm::alternarMotor,
        alCorrer = vm::correr,
        alCancelar = vm::cancelar,
        alExportar = { alcance.launch { vm.csv()?.let { Compartir.enviar(contexto, it, "Banco de pruebas ${e.clave}") } } },
        alCambiarModo = vm::cambiarModo,
    )
}

@Composable
fun ContenidoBanco(
    e: EstadoBanco,
    alCambiarRecorte: (Boolean) -> Unit,
    alAlternarMotor: (Motor) -> Unit,
    alCorrer: () -> Unit,
    alCancelar: () -> Unit,
    alExportar: () -> Unit,
    alCambiarModo: (ModoBanco) -> Unit = {},
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("pantalla-banco"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Opciones(
                opciones = ModoBanco.entries,
                elegida = e.modo,
                etiqueta = { it.etiqueta },
                alElegir = alCambiarModo,
                habilitado = !e.corriendo,
                modifier = Modifier.testTag("modo-banco"),
            )
        }
        if (e.modo == ModoBanco.LIMITES) item {
            Text(
                "Para cada motor y cada condición (desenfoque, movido, reflejo, poca luz con ruido, tamaño, giro y " +
                    "perspectiva) sube la dificultad en pasos y busca el último grado que lee en al menos 4 de 5 " +
                    "variantes antes del primero que falla. Un EAN-13 de 3 px por módulo en 1280×720 girado 90°, como en vivo. Tarda varios minutos.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else item {
            Text(
                "Dibuja ${e.escenas} escenas (EAN-13, internos, UPC-A, EAN-8, ITF-14 y Code 128 de 1.5 a 5 px por módulo, " +
                    "girados, borrosos, con ruido, poca luz, reflejo, y sin código) y las pasa por cada motor como si " +
                    "vinieran de la cámara: 1280×720 girado 90°. Tarda unos minutos; no uses el celular mientras.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            FilaInterruptor(
                titulo = "Leer solo la franja central",
                descripcion = "Como en vivo. Apágalo para pasar el cuadro completo.",
                valor = e.recortarFranja,
                alCambiar = alCambiarRecorte,
                habilitado = !e.corriendo,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in MOTORES_DEL_BANCO) {
                    FilterChip(
                        selected = m in e.motores,
                        onClick = { alAlternarMotor(m) },
                        enabled = !e.corriendo,
                        label = { Text(m.nombre) },
                    )
                }
            }
        }
        item {
            if (e.corriendo) {
                OutlinedButton(onClick = alCancelar, modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE)) {
                    Icon(Icons.Filled.Stop, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Cancelar")
                }
                LinearProgressIndicator(
                    progress = { if (e.total > 0) e.hechos.toFloat() / e.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("progreso-banco"),
                )
                Text(
                    "${e.hechos} de ${e.total} · ${e.etiqueta}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Button(
                    onClick = alCorrer,
                    enabled = e.motores.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("correr-banco"),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null); Spacer(Modifier.width(8.dp))
                    Text(if (e.modo == ModoBanco.LIMITES) "Buscar límites" else "Correr banco")
                }
            }
        }
        if (e.modoResultado == ModoBanco.LIMITES && e.limites.isNotEmpty()) {
            item {
                TituloSeccion(if (e.cancelado) "Límites (cancelado a la mitad)" else "Límites")
                Text(
                    "El último grado que leyó en ≥ 80 % de las variantes antes del primero que falló; «≥» = leyó hasta el más " +
                        "difícil, «—» = ni el más fácil. Región: ${e.regionUsada}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TablaLimites(e.limites)
            }
            item {
                Button(
                    onClick = alExportar,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).padding(top = 8.dp).testTag("exportar-limites"),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Exportar CSV")
                }
            }
        }
        if (e.modoResultado == ModoBanco.CATALOGO && e.resumenes.isNotEmpty()) {
            item {
                TituloSeccion(if (e.cancelado) "Resultados (cancelado a la mitad)" else "Resultados")
                Text(
                    "Región: ${e.regionUsada} · ${e.simbologias.size} simbologías de Ajustes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TablaResumen(e.resumenes)
            }
            item {
                TituloSeccion("Aciertos por tamaño del módulo")
                TablaPorModulo(e.resumenes)
            }
            val malas = e.resultados.filter {
                it.veredicto == Veredicto.LECTURA_INCORRECTA || it.veredicto == Veredicto.FALSO_POSITIVO
            }
            if (malas.isNotEmpty()) {
                item { TituloSeccion("Lecturas incorrectas y falsos positivos") }
                items(malas.take(30)) { r ->
                    Text(
                        "${r.motor.nombre} · ${r.escena.id}: «${r.leido}»" + (r.escena.esperado?.let { " (esperado $it)" } ?: ""),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                Button(
                    onClick = alExportar,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).padding(top = 8.dp).testTag("exportar-banco"),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Exportar CSV")
                }
            }
        }
    }
}

@Composable
private fun TablaResumen(resumenes: List<ResumenMotor>) {
    val colores = LocalColoresEstado.current
    Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(Modifier.padding(12.dp).testTag("tabla-banco")) {
            Fila("", resumenes.map { it.motor.nombre }, encabezado = true)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Fila("Aciertos", resumenes.map { r -> if (r.conCodigo == 0) "—" else "${r.aciertos}/${r.conCodigo} (${r.aciertos * 100 / r.conCodigo} %)" })
            Fila(
                "Lecturas incorrectas",
                resumenes.map { it.incorrectas.toString() },
                color = { i -> if (resumenes[i].incorrectas > 0) MaterialTheme.colorScheme.error else colores.exito },
            )
            Fila("Sin lectura", resumenes.map { it.sinLectura.toString() })
            Fila(
                "Falsos positivos",
                resumenes.map { "${it.falsosPositivos}/${it.sinCodigo}" },
                color = { i -> if (resumenes[i].falsosPositivos > 0) MaterialTheme.colorScheme.error else colores.exito },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Fila("ms caliente, mediana", resumenes.map { ms(it.msCaliente?.mediana) })
            Fila("ms caliente, p95", resumenes.map { ms(it.msCaliente?.p95) })
            Fila("ms en frío, mediana", resumenes.map { ms(it.msFrio?.mediana) })
            Fila("ms en frío, p95", resumenes.map { ms(it.msFrio?.p95) })
            for (r in resumenes) {
                if (r.error != null) {
                    Text(
                        "${r.motor.nombre}: ${r.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TablaLimites(limites: List<LimiteMotor>) {
    val motores = limites.map { it.motor }.distinct()
    val porCondicion = limites.groupBy { it.condicion }
    Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(Modifier.padding(12.dp).testTag("tabla-limites")) {
            Fila("", motores.map { it.nombre }, encabezado = true)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            for ((condicion, filas) in porCondicion) {
                Fila(
                    "${condicion.etiqueta} (${condicion.unidad})",
                    motores.map { m -> filas.firstOrNull { it.motor == m }?.let { CsvLimites.textoLimite(it) } ?: "—" },
                )
            }
            for (l in limites.filter { it.error != null }.distinctBy { it.motor }) {
                Text(
                    "${l.motor.nombre}: ${l.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TablaPorModulo(resumenes: List<ResumenMotor>) {
    val modulos = resumenes.flatMap { it.aciertosPorModulo.keys }.distinct().sorted()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Fila("px/módulo", resumenes.map { it.motor.nombre }, encabezado = true)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            for (m in modulos) {
                Fila(
                    Csv.decimal(m, 1),
                    resumenes.map { r -> r.aciertosPorModulo[m]?.let { (a, t) -> "$a/$t" } ?: "—" },
                )
            }
        }
    }
}

@Composable
private fun Fila(
    etiqueta: String,
    valores: List<String>,
    encabezado: Boolean = false,
    color: @Composable (Int) -> androidx.compose.ui.graphics.Color = { MaterialTheme.colorScheme.onSurface },
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (encabezado) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1.4f),
        )
        valores.forEachIndexed { i, v ->
            Text(
                v,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (encabezado) FontWeight.SemiBold else FontWeight.Normal,
                color = if (encabezado) MaterialTheme.colorScheme.onSurface else color(i),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun ms(valor: Double?) = valor?.let { Csv.decimal(it, 1) } ?: "—"
