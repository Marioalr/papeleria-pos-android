package mx.papeleriapos.android.principal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.papeleriapos.android.botonrapido.SIN_CONEXION_MANDAR
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.catalogo.cantidadCorta
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.lista.Renglon
import mx.papeleriapos.android.tema.LocalColoresModo

/*
 * Lo que tapa el visor (docs/propuesta-interfaz.md, «Ticket: la preventa» y «Lo que no se escanea: buscar»). Mientras una
 * está abierta las lecturas no cuentan y el escáner no vibra. Van dentro de la ventana (no son diálogos aparte): así el
 * teclado mueve solo la hoja y el visor no cambia de tamaño.
 */

/** La barra de arriba de una hoja: la × (que siempre responde) y el título. */
@Composable
private fun BarraHoja(titulo: String, subtitulo: String? = null, alCerrar: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = alCerrar, modifier = Modifier.size(48.dp).testTag("cerrar-hoja")) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitulo != null) {
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Una hoja a pantalla completa. */
@Composable
private fun HojaCompleta(etiqueta: String, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize().testTag(etiqueta), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) { contenido() }
    }
}

// ---- Buscar ----

/**
 * El campo va abajo, pegado al teclado, y los resultados encima: el mejor queda junto al campo y junto al pulgar (con el
 * teclado abierto, la mitad de arriba de la pantalla no se alcanza con una mano). En Ticket agrega, en Precio enseña y en
 * Conteo cuenta.
 */
@Composable
fun HojaBuscar(modo: ModoApp, catalogo: CatalogoDeProductos?, alElegir: (Producto) -> Unit, alCerrar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    val resultados = remember(texto, catalogo) { catalogo?.buscar(texto).orEmpty() }
    val foco = remember { FocusRequester() }
    val titulo = when (modo) {
        ModoApp.TICKET -> "Agregar al ticket"
        ModoApp.PRECIO -> "Ver el precio"
        ModoApp.CONTEO -> "Contar un producto"
    }
    HojaCompleta("hoja-buscar") {
        BarraHoja(titulo, if (texto.isEmpty()) "Los rápidos y lo más vendido" else "${resultados.size} encontrados", alCerrar)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
            items(resultados, key = { it.clave }) { p -> Resultado(p, modo, texto) { alElegir(p) } }
        }
        if (catalogo != null && resultados.isEmpty()) {
            Text(
                "Nada con «$texto». Prueba con un pedazo de palabra.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            singleLine = true,
            placeholder = { Text("Nombre o código: «lus az»") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (texto.isNotEmpty()) {
                    IconButton(onClick = { texto = "" }) { Icon(Icons.Filled.Close, contentDescription = "Borrar lo escrito") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { resultados.firstOrNull()?.let(alElegir) }),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(12.dp)
                .focusRequester(foco)
                .testTag("campo-buscar"),
        )
    }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
}

@Composable
private fun Resultado(p: Producto, modo: ModoApp, texto: String, alTocar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = alTocar)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("resultado-${p.clave}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.nombre, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val existencia = if (modo == ModoApp.CONTEO || p.existencia == null) "" else " · existencia ${cantidadCorta(p.existencia)}"
            Text(
                p.departamento + existencia,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(pesos(p.precio), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
            p.mayoreo?.let {
                Text("desde ${it.desde}: ${pesos(it.centavos)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- La cantidad ----

/** El teclado de números para un renglón: «20» copias. Teclas de 52 dp, a la altura del pulgar. */
@Composable
fun HojaCantidad(renglon: Renglon, producto: Producto?, alListo: (Int) -> Unit, alCerrar: () -> Unit) {
    var tecleado by rememberSaveable(renglon.id) { mutableStateOf("") }
    val numero = tecleado.toIntOrNull()
    Box(Modifier.fillMaxSize()) {
        // El velo y la hoja son hermanos: un toque en la hoja no le llega al velo (que cierra).
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)).clickable(onClickLabel = "Cerrar", onClick = alCerrar))
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("hoja-cantidad"),
        ) {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 8.dp)) {
                BarraHoja(producto?.nombre ?: renglon.codigo, "Van ${renglon.cantidad}: teclea cuántos lleva", alCerrar)
                Text(
                    tecleado.ifEmpty { renglon.cantidad.toString() },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    color = if (tecleado.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { contentDescription = "Cantidad: ${tecleado.ifEmpty { renglon.cantidad.toString() }}" },
                )
                val filas = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "Listo"))
                for (fila in filas) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (t in fila) {
                            val modifier = Modifier.weight(1f).height(52.dp)
                            when (t) {
                                "⌫" -> OutlinedButton(onClick = { tecleado = tecleado.dropLast(1) }, modifier = modifier) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Borrar")
                                }
                                "Listo" -> Button(
                                    onClick = { numero?.let(alListo) ?: alCerrar() },
                                    modifier = modifier.testTag("cantidad-listo"),
                                ) { Text(if (numero == 0) "Quitar" else "Listo") }
                                else -> OutlinedButton(
                                    onClick = { if (tecleado.length < MAXIMO_DIGITOS) tecleado = (tecleado + t).trimStart('0').ifEmpty { "0" } },
                                    modifier = modifier.testTag("tecla-$t"),
                                ) { Text(t, fontSize = 22.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Más de 9999 en un renglón es un error de dedo. */
private const val MAXIMO_DIGITOS = 4

// ---- El ticket completo ----

class AccionesTicket(
    val masUno: (Long) -> Unit,
    val menosUno: (Long) -> Unit,
    val teclearCantidad: (Long) -> Unit,
    val buscar: () -> Unit,
    val empezarOtro: () -> Unit,
    val deshacer: () -> Unit,
)

/**
 * El ticket entero (jalar el asidero o tocar el encabezado): todos los renglones con − y + e importe, deshacer, el total y
 * «Empezar otro ticket». «Mandar a una caja» llega con la conexión al POS.
 */
@Composable
fun HojaTicket(ticket: ListaLecturas, catalogo: CatalogoDeProductos?, acciones: AccionesTicket, alCerrar: () -> Unit) {
    val color = LocalColoresModo.current.ticket
    HojaCompleta("hoja-ticket") {
        BarraHoja("Ticket · ${ticket.total} art.", "Los precios los pone la caja al recibirlo", alCerrar)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(ticket.renglones, key = { it.id }) { r ->
                val p = r.clave?.let { catalogo?.porClave(it) }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            p?.nombre ?: "Código sin producto",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (p == null) MaterialTheme.colorScheme.error else Color.Unspecified,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(detalleDe(r, p), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { acciones.menosUno(r.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(if (r.cantidad == 1) Icons.Filled.DeleteOutline else Icons.Filled.Remove, contentDescription = if (r.cantidad == 1) "Quitar" else "Uno menos")
                    }
                    Text(
                        r.cantidad.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(min = 44.dp)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClickLabel = "Teclear la cantidad") { acciones.teclearCantidad(r.id) }
                            .padding(top = 12.dp),
                    )
                    IconButton(onClick = { acciones.masUno(r.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Uno más")
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(pesos(totalDe(ticket, catalogo)), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                }
                Text(SIN_CONEXION_MANDAR, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = acciones.buscar, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buscar")
                    }
                    OutlinedButton(onClick = acciones.deshacer, enabled = ticket.puedeDeshacer, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Deshacer")
                    }
                }
                Button(
                    onClick = acciones.empezarOtro,
                    enabled = ticket.renglones.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("empezar-otro"),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = color.color, contentColor = color.enColor),
                ) { Text("Empezar otro ticket") }
            }
        }
    }
}

// ---- Enseñar al cliente ----

/** El nombre y el precio a pantalla completa, para voltear el celular. Se toca para regresar. */
@Composable
fun PrecioGrande(p: Producto, alCerrar: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "Regresar", onClick = alCerrar).testTag("precio-grande"),
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(p.nombre, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                pesos(p.precio),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(vertical = 12.dp),
            )
            p.mayoreo?.let { Text("Desde ${it.desde}: ${pesos(it.centavos)} c/u", style = MaterialTheme.typography.headlineSmall) }
            p.promocion?.let { Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(32.dp))
            TextButton(onClick = alCerrar) { Text("Toca para regresar") }
        }
    }
}
