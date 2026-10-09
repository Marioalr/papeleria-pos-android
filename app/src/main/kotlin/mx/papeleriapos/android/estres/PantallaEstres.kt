package mx.papeleriapos.android.estres

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.tema.LocalColoresEstado
import mx.papeleriapos.android.ui.ALTO_TOCABLE
import mx.papeleriapos.android.ui.CicloPropio
import mx.papeleriapos.android.ui.FilaDato
import mx.papeleriapos.android.ui.FilaInterruptor
import mx.papeleriapos.android.ui.Opciones
import mx.papeleriapos.android.ui.TituloSeccion
import mx.papeleriapos.escaner.ui.PantallaEscaner

/**
 * Las pruebas de estrés de la cámara: abrir y cerrar N veces, zoom y linterna rápidos, cambiar de motor en caliente y
 * escanear 30 minutos con el monitor de recursos. Se cancelan si la app pasa a segundo plano o se sale de la pestaña.
 */
@Composable
fun PantallaEstres(vm: EstresViewModel) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val continuo by vm.controladorContinuo.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val dueno = LocalLifecycleOwner.current
    var pendiente by remember { mutableStateOf<PruebaEstres?>(null) }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val p = pendiente
        pendiente = null
        if (ok && p != null) vm.correr(p)
    }
    // Con la app en segundo plano Android le quita la cámara: la prueba ya no mediría lo que debe.
    DisposableEffect(dueno) {
        val o = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_STOP) vm.cancelar("Se canceló: la app pasó a segundo plano.") }
        dueno.lifecycle.addObserver(o)
        onDispose {
            dueno.lifecycle.removeObserver(o)
            vm.cancelar("Se canceló al salir de la pestaña.")
        }
    }
    val correr: (PruebaEstres) -> Unit = { p ->
        if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            vm.correr(p)
        } else {
            pendiente = p
            permiso.launch(Manifest.permission.CAMERA)
        }
    }
    val c = continuo
    if (c != null) {
        // El escaneo continuo se ve: apunta a un código fijo y deja el celular quieto.
        Box(Modifier.fillMaxSize()) {
            CicloPropio { PantallaEscaner(c, Modifier.fillMaxSize()) }
            AvisoContinuo(e, alDetener = { vm.cancelar() }, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
        }
        return
    }
    ContenidoEstres(
        e,
        alCorrer = correr,
        alCancelar = { vm.cancelar() },
        alExportar = { vm.compartir(contexto, it) },
        alCambiarCiclos = vm::cambiarCiclos,
        alCambiarControladorNuevo = vm::cambiarControladorNuevo,
        alCambiarMinutos = vm::cambiarMinutos,
    )
}

@Composable
fun ContenidoEstres(
    e: EstadoEstres,
    alCorrer: (PruebaEstres) -> Unit,
    alCancelar: () -> Unit,
    alExportar: (PruebaEstres) -> Unit,
    alCambiarCiclos: (Int) -> Unit,
    alCambiarControladorNuevo: (Boolean) -> Unit,
    alCambiarMinutos: (Int) -> Unit,
) {
    val libre = e.corriendo == null
    LazyColumn(
        Modifier.fillMaxSize().testTag("pantalla-estres"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Cada prueba usa su propia cámara, sin la pestaña Escanear. No salgas de esta pantalla mientras corre: si la " +
                    "app pasa a segundo plano se cancela. Al terminar dice si pasa y por qué; exporta los CSV para revisarlos.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        e.aviso?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
        if (!libre) item { Progreso(e, alCancelar) }

        item {
            Prueba(
                titulo = PruebaEstres.CICLOS.titulo,
                descripcion = "${e.ciclosPedidos} veces: abrir → cámara abierta → primer cuadro → cerrar. Cada 25 ciclos, " +
                    "memoria tras GC, hilos y descriptores. Pasa con 0 errores sin recuperar, el p95 del final ≤ 1.2× el del " +
                    "inicio y sin crecimiento sostenido.",
                dictamen = e.dictamenCiclos,
                resumen = if (e.ciclos.isEmpty()) null else CriteriosCiclos.resumen(e.ciclos),
                habilitado = libre,
                alCorrer = { alCorrer(PruebaEstres.CICLOS) },
                alExportar = { alExportar(PruebaEstres.CICLOS) },
                etiqueta = PruebaEstres.CICLOS,
            ) {
                Opciones(listOf(50, 200, 500), e.ciclosPedidos.takeIf { it in listOf(50, 200, 500) } ?: 200, { "$it ciclos" }, alCambiarCiclos, habilitado = libre)
                FilaInterruptor(
                    titulo = "Controlador nuevo en cada ciclo",
                    descripcion = if (e.controladorNuevo) "Crea y cierra el escáner completo (fugas de hilos y motores)."
                    else "El mismo escáner va y vuelve de segundo plano (apertura en caliente).",
                    valor = e.controladorNuevo,
                    alCambiar = alCambiarControladorNuevo,
                    habilitado = libre,
                )
            }
        }
        item {
            Prueba(
                titulo = PruebaEstres.RAFAGA.titulo,
                descripcion = "Zoom cada 30 ms durante 60 s y la linterna 100 veces (cada 200 ms). Pasa si no hay error, no " +
                    "se detienen los cuadros más de 2 s y al final la interfaz dice lo mismo que la cámara.",
                dictamen = e.dictamenRafaga,
                resumen = e.finalRafaga?.let { f ->
                    "zoom ${Cuentas.texto(f.zoomUi.toDouble(), 2)} / ${Cuentas.texto(f.zoomReal.toDouble(), 2)} · " +
                        if (f.hayLinterna) "linterna ${if (f.linternaUi) "sí" else "no"} / ${if (f.linternaReal == true) "sí" else "no"}" else "sin linterna"
                },
                habilitado = libre,
                alCorrer = { alCorrer(PruebaEstres.RAFAGA) },
                alExportar = { alExportar(PruebaEstres.RAFAGA) },
                etiqueta = PruebaEstres.RAFAGA,
            )
        }
        item {
            Prueba(
                titulo = PruebaEstres.MOTOR.titulo,
                descripcion = "50 cambios en rueda (Automático, ML Kit, zxing-cpp) con la cámara abierta, midiendo cuánto " +
                    "tarda en leer con el nuevo. Pasa sin errores, p95 ≤ 3 s y sin fugas de memoria, hilos ni descriptores.",
                dictamen = e.dictamenMotor,
                resumen = if (e.cambios.isEmpty()) null else "p50 ${Cuentas.texto(Cuentas.mediana(e.cambios.map { it.ms }), 0)} ms · " +
                    "p95 ${Cuentas.texto(Cuentas.p95(e.cambios.map { it.ms }), 0)} ms",
                habilitado = libre,
                alCorrer = { alCorrer(PruebaEstres.MOTOR) },
                alExportar = { alExportar(PruebaEstres.MOTOR) },
                etiqueta = PruebaEstres.MOTOR,
            )
        }
        item {
            Prueba(
                titulo = PruebaEstres.CONTINUO.titulo,
                descripcion = "${e.minutosContinuo} min escaneando un código fijo con el monitor de recursos cada segundo. Hay " +
                    "estrangulamiento térmico si durante 60 s se cumplen 2 de: térmico ≥ MODERATE, headroom ≥ 0.85, cuadros/s " +
                    "< 85 % del minuto 1, CPU por cuadro +25 %. Sin cable, con el brillo fijo.",
                dictamen = e.dictamenContinuo,
                resumen = e.termico?.let { t -> "${t.minutos.size} min · cuadros/s al inicio ${Cuentas.texto(t.fpsBase)} y al final ${Cuentas.texto(t.minutos.lastOrNull()?.fpsAnalisis ?: Double.NaN)}" },
                habilitado = libre,
                alCorrer = { alCorrer(PruebaEstres.CONTINUO) },
                alExportar = { alExportar(PruebaEstres.CONTINUO) },
                etiqueta = PruebaEstres.CONTINUO,
            ) {
                Opciones(listOf(5, 15, 30), e.minutosContinuo.takeIf { it in listOf(5, 15, 30) } ?: 30, { "$it min" }, alCambiarMinutos, habilitado = libre)
            }
        }
        e.termico?.takeIf { it.minutos.isNotEmpty() }?.let { t ->
            item {
                TituloSeccion("Por minuto")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        FilaDato("min", "cuadros/s · ms CPU/cuadro · °C · térmico")
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        for (m in t.minutos) {
                            FilaDato(
                                m.minuto.toString(),
                                "${Cuentas.texto(m.fpsAnalisis)} · ${Cuentas.texto(m.cpuMsPorCuadro, 2)} · ${Cuentas.texto(m.tempBatC)} · " +
                                    if (m.termicoMaximo >= 0) m.termicoMaximo.toString() else "—",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Progreso(e: EstadoEstres, alCancelar: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(e.corriendo?.titulo ?: "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { if (e.total > 0) e.hechos.toFloat() / e.total else 0f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("progreso-estres"),
            )
            Text("${e.hechos} de ${e.total} · ${e.etiqueta}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            OutlinedButton(onClick = alCancelar, modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).padding(top = 8.dp)) {
                Icon(Icons.Filled.Stop, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Detener")
            }
        }
    }
}

@Composable
private fun Prueba(
    titulo: String,
    descripcion: String,
    dictamen: Dictamen?,
    resumen: String?,
    habilitado: Boolean,
    alCorrer: () -> Unit,
    alExportar: () -> Unit,
    etiqueta: PruebaEstres,
    opciones: @Composable () -> Unit = {},
) {
    val colores = LocalColoresEstado.current
    Card(Modifier.fillMaxWidth().testTag("prueba-${etiqueta.name}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            opciones()
            if (dictamen != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (dictamen.pasa) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = null,
                        tint = if (dictamen.pasa) colores.exito else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (dictamen.pasa) "Pasa" else "No pasa",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (dictamen.pasa) colores.exito else MaterialTheme.colorScheme.error,
                    )
                }
                for (c in dictamen.criterios) {
                    FilaDato((if (c.pasa) "✓ " else "✗ ") + c.nombre, "${c.valor} (${c.limite})")
                }
            }
            if (resumen != null) Text(resumen, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = alCorrer,
                    enabled = habilitado,
                    modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE).testTag("correr-${etiqueta.name}"),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Correr")
                }
                OutlinedButton(
                    onClick = alExportar,
                    enabled = habilitado && dictamen != null,
                    modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("CSV")
                }
            }
        }
    }
}

/** Sobre el escáner durante el escaneo continuo: cuánto lleva, lo último que midió el monitor y el botón para parar. */
@Composable
private fun AvisoContinuo(e: EstadoEstres, alDetener: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "Escaneo continuo · ${e.hechos / 60}:${(e.hechos % 60).toString().padStart(2, '0')} de ${e.total / 60}:00",
                style = MaterialTheme.typography.titleSmall,
            )
            val m = e.muestra
            if (m != null) {
                Text(
                    "CPU ${Cuentas.texto(m.cpuPctNucleo)} % · ${Cuentas.texto(m.fpsAnalisis)} cuadros/s · " +
                        (if (m.tempBatDc != Int.MIN_VALUE) "${Cuentas.texto(m.tempBatDc / 10.0)} °C · " else "") +
                        "térmico ${if (m.termico >= 0) m.termico.toString() else "—"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = alDetener, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 4.dp)) {
                Icon(Icons.Filled.Stop, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Detener")
            }
        }
    }
}
