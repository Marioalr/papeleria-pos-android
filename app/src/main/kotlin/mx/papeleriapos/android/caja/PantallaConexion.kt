package mx.papeleriapos.android.caja

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.papeleriapos.android.entrada.Entrada
import mx.papeleriapos.android.ui.ALTO_TOCABLE
import mx.papeleriapos.android.ui.Fechas
import mx.papeleriapos.android.ui.FilaDato
import mx.papeleriapos.android.ui.Opciones
import mx.papeleriapos.android.ui.TituloSeccion

/**
 * Ajustes › La conexión (docs/propuesta-interfaz.md, «Ajustes y herramientas de medición»): con el celular dado de alta,
 * solo para leer (la caja principal, qué caja es este celular, la red, la copia y de cuándo); sin alta, el alta paso a
 * paso ([PasoAlta]). «Quitar el alta» se hace desde la caja (Configuración › Cajas › «Quitar PC»), como con cualquier caja.
 */
@Composable
fun PantallaConexion(vm: ConexionViewModel = viewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val paso by vm.paso.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    // Aquí el dueño ve el aviso de que la caja le quitó el acceso (esta pantalla no se dibuja con la entrada con la huella
    // encima): deja de guardarse y de salir en la pantalla principal.
    LaunchedEffect(estado.sinAcceso) { if (estado.sinAcceso) vm.avisoVisto() }
    ContenidoConexion(
        estado = estado,
        paso = paso,
        hayBloqueo = Entrada.hayBloqueoSeguro(contexto),
        alAbrirSeguridad = { contexto.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        acciones = AccionesAlta(
            empezar = vm::empezar,
            buscar = vm::buscar,
            escribirIp = vm::escribirIp,
            elegir = vm::elegir,
            escribirUsuario = vm::escribirUsuario,
            escribirPin = vm::escribirPin,
            identificarse = vm::identificarse,
            elegirSucursal = vm::elegirSucursal,
            escribirNombre = vm::escribirNombre,
            escribirSerie = vm::escribirSerie,
            darDeAlta = vm::darDeAlta,
            atras = vm::atras,
        ),
    )
}

class AccionesAlta(
    val empezar: () -> Unit = {},
    val buscar: () -> Unit = {},
    val escribirIp: (String) -> Unit = {},
    val elegir: (Direccion?) -> Unit = {},
    val escribirUsuario: (String) -> Unit = {},
    val escribirPin: (String) -> Unit = {},
    val identificarse: () -> Unit = {},
    val elegirSucursal: (String) -> Unit = {},
    val escribirNombre: (String) -> Unit = {},
    val escribirSerie: (String) -> Unit = {},
    val darDeAlta: () -> Unit = {},
    val atras: () -> Unit = {},
)

@Composable
fun ContenidoConexion(
    estado: EstadoConexion,
    paso: PasoAlta,
    /** Si el celular tiene bloqueo de pantalla: sin él no se da de alta (la app pide la huella al entrar). */
    hayBloqueo: Boolean,
    alAbrirSeguridad: () -> Unit,
    acciones: AccionesAlta,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("pantalla-conexion"),
    ) {
        val alta = estado.alta
        when {
            !estado.leida -> Text("Leyendo…", style = MaterialTheme.typography.bodyLarge)
            alta != null -> Conectada(alta, estado)
            paso is PasoAlta.Listo -> Conectada(paso.alta, estado)
            else -> Alta(estado, paso, hayBloqueo, alAbrirSeguridad, acciones)
        }
    }
}

/** Con alta: todo para leer. */
@Composable
private fun Conectada(alta: Alta, estado: EstadoConexion) {
    TituloSeccion("La caja principal")
    FilaDato("Dirección", alta.direccion.toString())
    FilaDato(
        "La red",
        when (estado.red) {
            Red.SIN_PROBAR -> "Sin probar todavía"
            Red.BUENA -> "Buena"
            Red.LENTA -> "Lenta"
            Red.CAIDA -> "Sin conexión"
        },
    )
    estado.ultimaVez?.let { FilaDato("Contestó", Fechas.hora(it)) }
    Nota("La app le avisa cada 30 segundos mientras está abierta; nunca en segundo plano. Sin red, Precio y Buscar siguen con la copia.")

    TituloSeccion("Este celular")
    FilaDato("Caja", "${alta.caja.nombre} (serie ${alta.caja.serie})")
    if (alta.caja.sucursal.isNotBlank()) FilaDato("Sucursal", alta.caja.sucursal)
    FilaDato("Equipo", alta.equipo)
    if (alta.altaEn > 0) FilaDato("Dado de alta", Fechas.fechaYHora(alta.altaEn))
    Nota("Para quitarle el acceso: en la caja, Configuración › Cajas › «Quitar PC» en su renglón. Este celular nunca abre turno ni vende.")

    TituloSeccion("La copia del catálogo")
    val copia = estado.copia
    when {
        estado.bajando && copia == null -> {
            Text("Bajando la copia completa…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("copia-bajando"))
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        copia == null -> Text("Todavía no hay copia: se baja en cuanto conteste la caja principal.", style = MaterialTheme.typography.bodyLarge)
        else -> {
            FilaDato("Productos", copia.variantes.toString())
            FilaDato("Códigos", copia.codigos.toString())
            FilaDato("De cuándo", Fechas.fechaYHora(copia.guardadaEn))
            copia.nombreTienda?.let { FilaDato("Tienda", it) }
            if (estado.bajando) {
                Text("Bajando la copia completa otra vez…", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
        }
    }
    Nota(
        "Solo los productos, sus códigos, precios, existencias, promociones y lo de la búsqueda. Nunca los usuarios ni sus " +
            "PIN, ni los clientes: eso se descarta al bajar. Se pone al día sola cuando la caja principal avisa que cambió.",
    )
    estado.aviso?.let { Aviso(it) }
}

/** Sin alta: los pasos. */
@Composable
private fun Alta(estado: EstadoConexion, paso: PasoAlta, hayBloqueo: Boolean, alAbrirSeguridad: () -> Unit, acciones: AccionesAlta) {
    TituloSeccion("Conectar con la caja principal")
    estado.aviso?.let { Aviso(it) }
    if (!hayBloqueo) {
        Text(
            "Este celular no tiene bloqueo de pantalla. Para darlo de alta ponle uno (huella, PIN o patrón): la app lo pide al entrar.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("sin-bloqueo"),
        )
        OutlinedButton(onClick = alAbrirSeguridad, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = ALTO_TOCABLE)) {
            Text("Abrir la seguridad del celular")
        }
        return
    }
    when (paso) {
        is PasoAlta.Buscar -> PasoBuscar(paso, acciones)
        is PasoAlta.Pin -> PasoPin(paso, acciones)
        is PasoAlta.Datos -> PasoDatos(paso, acciones)
        is PasoAlta.Listo -> Unit
    }
}

@Composable
private fun PasoBuscar(p: PasoAlta.Buscar, acciones: AccionesAlta) {
    Nota("Como una caja más del POS: el celular se da de alta con tu usuario y tu PIN, y baja la copia del catálogo para dar el precio al instante y sin red.")
    Text("1. La caja principal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    if (!p.busco && !p.trabajando) {
        Button(onClick = acciones.buscar, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = ALTO_TOCABLE).testTag("buscar-caja")) {
            Text("Buscar en el Wi-Fi de la tienda")
        }
    }
    if (p.trabajando) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Spacer(Modifier.size(12.dp))
            Text(if (p.busco) "Revisando…" else "Buscando…", style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (p.busco && !p.trabajando) {
        if (p.encontradas.isEmpty()) {
            Text("Ninguna caja principal contestó en esta red.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        } else {
            for (d in p.encontradas) {
                OutlinedButton(
                    onClick = { acciones.elegir(d) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = ALTO_TOCABLE).testTag("caja-$d"),
                ) { Text("Esta: $d") }
            }
        }
        TextButton(onClick = acciones.buscar, modifier = Modifier.padding(top = 4.dp)) { Text("Buscar otra vez") }
    }
    Text("O escribe su IP", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
    OutlinedTextField(
        value = p.ipEscrita,
        onValueChange = acciones.escribirIp,
        singleLine = true,
        enabled = !p.trabajando,
        placeholder = { Text("192.168.1.10") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("ip-caja"),
    )
    Button(
        onClick = { acciones.elegir(null) },
        enabled = !p.trabajando && p.ipEscrita.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = ALTO_TOCABLE).testTag("usar-ip"),
    ) { Text("Usar esta dirección") }
    p.error?.let { Aviso(it) }
}

@Composable
private fun PasoPin(p: PasoAlta.Pin, acciones: AccionesAlta) {
    Text("2. Quién da de alta", style = MaterialTheme.typography.titleMedium)
    Nota("El POS en ${p.direccion}${if (p.version.isNotBlank()) " (versión ${p.version})" else ""}. Tu usuario y tu PIN del POS, con «Dar de alta y quitar cajas». El PIN no se guarda en el celular.")
    OutlinedTextField(
        value = p.usuario,
        onValueChange = acciones.escribirUsuario,
        singleLine = true,
        enabled = !p.trabajando,
        label = { Text("Usuario") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("alta-usuario"),
    )
    OutlinedTextField(
        value = p.pin,
        onValueChange = acciones.escribirPin,
        singleLine = true,
        enabled = !p.trabajando,
        label = { Text("PIN") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("alta-pin"),
    )
    Botones(p.trabajando, "Continuar", acciones.identificarse, acciones.atras, "alta-continuar")
    p.error?.let { Aviso(it) }
}

@Composable
private fun PasoDatos(p: PasoAlta.Datos, acciones: AccionesAlta) {
    Text("3. Esta caja", style = MaterialTheme.typography.titleMedium)
    if (p.sucursales.size > 1) {
        Text("Sucursal", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        Opciones(p.sucursales, p.sucursal ?: p.sucursales[0], { it.nombre }, { acciones.elegirSucursal(it.id) }, habilitado = !p.trabajando)
    }
    val cajas = p.sucursal?.cajas.orEmpty()
    if (cajas.isNotEmpty()) {
        Nota("Las cajas que ya hay: " + cajas.joinToString(", ") { "${it.nombre} (serie ${it.serie})" } + ". El celular será una caja nueva; no toca a ninguna.")
    }
    OutlinedTextField(
        value = p.nombre,
        onValueChange = acciones.escribirNombre,
        singleLine = true,
        enabled = !p.trabajando,
        label = { Text("Nombre de la caja") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("alta-nombre"),
    )
    OutlinedTextField(
        value = p.serie,
        onValueChange = acciones.escribirSerie,
        singleLine = true,
        enabled = !p.trabajando,
        label = { Text("Serie de folios") },
        supportingText = { Text("De 1 a 3 letras, distinta de las otras cajas. El celular no vende: nunca se usa.") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("alta-serie"),
    )
    Botones(p.trabajando, "Dar de alta", acciones.darDeAlta, acciones.atras, "alta-dar")
    p.error?.let { Aviso(it) }
}

@Composable
private fun Botones(trabajando: Boolean, texto: String, alSeguir: () -> Unit, alRegresar: () -> Unit, etiqueta: String) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = alRegresar, enabled = !trabajando, modifier = Modifier.heightIn(min = ALTO_TOCABLE)) { Text("Atrás") }
        Button(onClick = alSeguir, enabled = !trabajando, modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE).testTag(etiqueta)) {
            if (trabajando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(texto)
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp).testTag("conexion-aviso"))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Nota(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
}

