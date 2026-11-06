package mx.papeleriapos.android.principal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import mx.papeleriapos.android.botonrapido.Accion
import mx.papeleriapos.android.botonrapido.EstadoOpciones
import mx.papeleriapos.android.botonrapido.opcionesDe
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

/**
 * Dónde quedó la × de la hoja de encima, respecto a la raíz: el rebote del botón rápido la deja pasar
 * (docs/propuesta-interfaz.md, «Cómo se usa»: «la × de arriba de la hoja sí responde»). La pantalla lo da por hoja.
 */
val LocalZonaCerrarHoja = staticCompositionLocalOf<(Rect) -> Unit> { {} }

/**
 * La barra de arriba de una hoja: la × (que siempre responde), el título y, si hay, una acción a la derecha. Va encima de
 * lo de abajo también para TalkBack: la lista de Buscar, que crece hacia arriba, no le tapa el subtítulo.
 */
@Composable
private fun BarraHoja(
    titulo: String,
    subtitulo: String? = null,
    alCerrar: () -> Unit,
    /** El subtítulo se anuncia al cambiar (en Buscar, cuántos hay). */
    subtituloVivo: Boolean = false,
    accion: (@Composable () -> Unit)? = null,
) {
    val zona = LocalZonaCerrarHoja.current
    Row(
        Modifier.fillMaxWidth().zIndex(1f).heightIn(min = 56.dp).padding(end = if (accion == null) 16.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = alCerrar,
            modifier = Modifier.size(48.dp).onGloballyPositioned { zona(it.boundsInRoot()) }.testTag("cerrar-hoja"),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitulo != null) {
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (subtituloVivo) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                )
            }
        }
        accion?.invoke()
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
 * Conteo cuenta. TalkBack no va por la geometría (empezaría por el peor): lee la barra, el campo y luego los resultados
 * del mejor al peor.
 */
@Composable
fun HojaBuscar(
    modo: ModoApp,
    /** Para volver a buscar cuando llegan los productos. */
    catalogo: CatalogoDeProductos?,
    buscar: (String) -> List<Producto>,
    alElegir: (Producto, texto: String) -> Unit,
    alCerrar: () -> Unit,
) {
    var texto by rememberSaveable { mutableStateOf("") }
    val resultados = remember(texto, catalogo) { buscar(texto) }
    val foco = remember { FocusRequester() }
    val titulo = when (modo) {
        ModoApp.TICKET -> "Agregar al ticket"
        ModoApp.PRECIO -> "Ver el precio"
        ModoApp.CONTEO -> "Contar un producto"
    }
    HojaCompleta("hoja-buscar") {
        val cuantos = when {
            texto.isEmpty() -> "Los rápidos y lo más vendido"
            resultados.size == 1 -> "1 encontrado"
            else -> "${resultados.size} encontrados"
        }
        BarraHoja(titulo, cuantos, alCerrar, subtituloVivo = true)
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Después del campo, aunque se dibuje encima de él.
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 1f
                },
            reverseLayout = true,
        ) {
            itemsIndexed(resultados, key = { _, p -> p.clave }) { i, p -> Resultado(p, modo, lugar = i) { alElegir(p, texto) } }
        }
        if (catalogo != null && resultados.isEmpty()) {
            Text(
                "Nada con «$texto». Prueba con un pedazo de palabra.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).semantics { traversalIndex = 1f },
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
            keyboardActions = KeyboardActions(onSearch = { resultados.firstOrNull()?.let { alElegir(it, texto) } }),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(12.dp)
                .focusRequester(foco)
                // TalkBack lo dice al llegar al campo: cuántos encontró. Lo siguiente que lee es el mejor.
                .semantics { stateDescription = cuantos }
                .testTag("campo-buscar"),
        )
    }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
}

/** [lugar]: 0 el mejor. La lista va de abajo hacia arriba; para TalkBack va del mejor al peor. */
@Composable
private fun Resultado(p: Producto, modo: ModoApp, lugar: Int, alTocar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { traversalIndex = lugar.toFloat() }
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
        // El velo y la hoja son hermanos: un toque en la hoja no le llega al velo (que cierra). Para TalkBack no es un botón
        // (sería uno sin nombre, de media pantalla, y el primero): la × de la hoja ya cierra.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                .pointerInput(Unit) { detectTapGestures { alCerrar() } }
                .testTag("velo-cantidad"),
        )
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("hoja-cantidad"),
        ) {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 8.dp)) {
                val van = if (renglon.cantidad == 1) "Va 1" else "Van ${renglon.cantidad}"
                BarraHoja(producto?.nombre ?: renglon.codigo, "$van: teclea cuántos lleva", alCerrar)
                Text(
                    tecleado.ifEmpty { renglon.cantidad.toString() },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    color = if (tecleado.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    // Lo tecleado se anuncia con cada tecla.
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics {
                        contentDescription = "Cantidad: ${tecleado.ifEmpty { renglon.cantidad.toString() }}"
                        liveRegion = LiveRegionMode.Polite
                    },
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
 * El ticket entero (jalar el asidero o tocar el encabezado), como el del prototipo: todos los renglones con − y + e
 * importe, «Buscar» e «Impresiones» abajo de ellos, deshacer arriba, el total, «Mandar a una caja» (el botón principal,
 * en el color del modo) y «Empezar otro ticket». «Mandar» e «Impresiones» se ven apagadas en su lugar, con su razón:
 * llegan con la conexión al POS (docs/propuesta-interfaz.md, «Entregas»). Lo que se abre desde aquí (la cantidad,
 * Buscar) regresa aquí al cerrarse.
 *
 * [huecoArriba]: lo que tapa el aviso que sale encima, bajo la barra («Quitado del ticket», «… al ticket»). La lista
 * empieza abajo de él mientras se ve (también desplazada: no es un relleno de su contenido), para que un toque al − o al
 * + del primer renglón no le caiga al «Deshacer» del aviso, que haría lo contrario.
 */
@Composable
fun HojaTicket(
    ticket: ListaLecturas,
    catalogo: CatalogoDeProductos?,
    acciones: AccionesTicket,
    huecoArriba: Dp = 0.dp,
    alCerrar: () -> Unit,
) {
    val color = LocalColoresModo.current.ticket
    val cargando = catalogo == null
    // Las razones de lo apagado, las mismas del botón rápido.
    val apagadas = opcionesDe(ModoApp.TICKET, EstadoOpciones(ticketVacio = ticket.renglones.isEmpty())).associateBy { it.accion }
    HojaCompleta("hoja-ticket") {
        BarraHoja("Ticket · ${ticket.total} art.", "Los precios los pone la caja al recibirlo", alCerrar) {
            IconButton(onClick = acciones.deshacer, enabled = ticket.puedeDeshacer, modifier = Modifier.size(48.dp).testTag("deshacer-ticket")) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Deshacer lo último")
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = huecoArriba).testTag("lista-ticket")) {
            if (ticket.renglones.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("El ticket está vacío", style = MaterialTheme.typography.titleMedium)
                        Text("Regresa al escáner y escanea lo que lleva el cliente.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(ticket.renglones, key = { it.id }) { r -> RenglonDelTicket(r, catalogo?.de(r), cargando, acciones) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = acciones.buscar, modifier = Modifier.heightIn(min = 48.dp).testTag("buscar-ticket")) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Buscar", maxLines = 1)
                        }
                        val impresiones = apagadas[Accion.IMPRESIONES]
                        FilledTonalButton(
                            onClick = {},
                            enabled = impresiones?.encendida == true,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics { impresiones?.razonApagada?.let { stateDescription = "apagada: $it" } }
                                .testTag("impresiones-ticket"),
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Impresiones", maxLines = 1)
                        }
                    }
                    apagadas[Accion.IMPRESIONES]?.razonApagada?.let { RazonApagada(it) }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (cargando && ticket.renglones.isNotEmpty()) "…" else pesos(totalDe(ticket, catalogo)),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    )
                }
                val mandar = apagadas[Accion.MANDAR]
                Button(
                    onClick = {},
                    enabled = mandar?.encendida == true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { mandar?.razonApagada?.let { stateDescription = "apagada: $it" } }
                        .testTag("mandar-ticket"),
                    colors = ButtonDefaults.buttonColors(containerColor = color.color, contentColor = color.enColor),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mandar a una caja", maxLines = 1)
                }
                mandar?.razonApagada?.let { RazonApagada(it) }
                OutlinedButton(
                    onClick = acciones.empezarOtro,
                    enabled = ticket.renglones.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("empezar-otro"),
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Empezar otro ticket", maxLines = 1)
                }
            }
        }
    }
}

/** Un renglón del ticket completo, con − (en 1, el bote), la cantidad (se toca para teclearla) y +. */
@Composable
private fun RenglonDelTicket(r: Renglon, p: Producto?, cargando: Boolean, acciones: AccionesTicket) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                p?.nombre ?: if (cargando) r.codigo else "Código sin producto",
                style = MaterialTheme.typography.bodyLarge,
                color = if (p == null && !cargando) MaterialTheme.colorScheme.error else Color.Unspecified,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detalleDe(r, p, cargando), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** La razón de lo apagado, abajo de su botón (TalkBack la lee en el botón: «apagada: …»). */
@Composable
private fun RazonApagada(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { hideFromAccessibility() })
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
