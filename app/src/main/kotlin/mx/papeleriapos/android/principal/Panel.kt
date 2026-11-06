package mx.papeleriapos.android.principal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.catalogo.cantidadCorta
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.android.catalogo.quedanPocos
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.lista.Renglon
import mx.papeleriapos.android.tema.ColorModo
import mx.papeleriapos.android.tema.LocalColoresEstado
import mx.papeleriapos.android.tema.LocalColoresModo

/*
 * El panel del modo, bajo la franja (docs/propuesta-interfaz.md, «La pantalla principal»): el asidero, lo del modo y, en
 * el hueco donde iba la barra de pestañas, los modos con el botón rápido. Es una Surface: se queda con los toques que no
 * son de sus botones, para que no lleguen al visor (que con un toque enfoca o reanuda).
 */

/** Lo que la pantalla le pide al panel. */
class AccionesPanel(
    val alTicket: (Producto) -> Unit = {},
    val buscar: () -> Unit = {},
    val verTicket: () -> Unit = {},
    val masUno: (Long) -> Unit = {},
    val menosUno: (Long) -> Unit = {},
    val teclearCantidad: (Long) -> Unit = {},
    val menosUnoConteo: () -> Unit = {},
    val deshacerConteo: () -> Unit = {},
    val vaciarConteo: () -> Unit = {},
)

@Composable
fun ContenidoPanel(
    modo: ModoApp,
    precio: ConsultaPrecio?,
    ticket: ListaLecturas,
    conteo: ListaLecturas,
    catalogo: CatalogoDeProductos?,
    acciones: AccionesPanel,
    modifier: Modifier = Modifier,
) {
    val color = LocalColoresModo.current.de(modo)
    Column(modifier.fillMaxSize()) {
        when (modo) {
            ModoApp.PRECIO -> PanelPrecio(precio, color, acciones)
            ModoApp.TICKET -> PanelTicket(ticket, catalogo, color, acciones)
            ModoApp.CONTEO -> PanelConteo(conteo, catalogo, color, acciones)
        }
    }
}

// ---- Precio: el verificador (F9) en el celular ----

@Composable
private fun PanelPrecio(precio: ConsultaPrecio?, color: ColorModo, acciones: AccionesPanel) {
    when (precio) {
        null -> Vacio(
            "Escanea para ver el precio",
            "Es el verificador (F9) de la caja: escanear no agrega nada. Enseñar y buscar: en el botón de abajo a la " +
                "derecha. Si se lo lleva, «Al ticket» en la tarjeta.",
        )
        is ConsultaPrecio.NoEsta -> NoEstaEnElCatalogo(precio.codigo, acciones.buscar)
        is ConsultaPrecio.Encontrado -> TarjetaPrecio(precio.producto, color, acciones)
    }
}

@Composable
private fun TarjetaPrecio(p: Producto, color: ColorModo, acciones: AccionesPanel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("tarjeta-precio")) {
        Row(verticalAlignment = Alignment.Top) {
            Iniciales(p.nombre, color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${p.departamento} · ${p.clave}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // El precio en grande y, a su derecha, «Al ticket», como «Agregarlo al ticket» del F9: lo que se agregue abajo no
        // lo baja ni lo saca del panel.
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pesos(p.precio),
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(1f).testTag("precio"),
                maxLines = 1,
            )
            FilledTonalButton(
                onClick = { acciones.alTicket(p) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("al-ticket"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Al ticket")
            }
        }
        val estado = LocalColoresEstado.current
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
            if (p.piezas > 1) Dato("${pesos(p.precio / p.piezas)} c/u (${p.piezas})")
            p.mayoreo?.let { Dato("Desde ${it.desde}: ${pesos(it.centavos)} c/u", fondo = estado.exito.copy(alpha = 0.16f)) }
            p.existencia?.let { e ->
                if (quedanPocos(p)) Dato("Quedan ${cantidadCorta(e)}", fondo = estado.aviso.copy(alpha = 0.18f))
                else Dato("Existencia: ${cantidadCorta(e)}")
            }
        }
        p.promocion?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = estado.exito, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Dato(texto: String, fondo: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(fondo).padding(horizontal = 8.dp, vertical = 4.dp),
        maxLines = 1,
    )
}

/** La foto del producto (cuando el catálogo la traiga) o sus iniciales, en el color del modo. */
@Composable
private fun Iniciales(nombre: String, color: ColorModo) {
    val letras = nombre.split(' ').filter { it.isNotEmpty() && it[0].isLetter() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(color.contenedor),
        contentAlignment = Alignment.Center,
    ) { Text(letras, color = color.enContenedor, style = MaterialTheme.typography.titleLarge) }
}

/** Lo que no está en el catálogo: en rojo, con el código, y ofrece buscar por nombre. */
@Composable
private fun NoEstaEnElCatalogo(codigo: String, buscar: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("no-esta")) {
        Text("No está en el catálogo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(codigo, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
        OutlinedButton(onClick = buscar, modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Buscar por nombre")
        }
    }
}

// ---- Ticket: la preventa ----

@Composable
private fun PanelTicket(ticket: ListaLecturas, catalogo: CatalogoDeProductos?, color: ColorModo, acciones: AccionesPanel) {
    val total = totalDe(ticket, catalogo)
    // El encabezado (o el asidero) abre el ticket completo.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Ver el ticket completo", onClick = acciones.verTicket)
            .padding(horizontal = 16.dp)
            .semantics { heading() }
            .testTag("encabezado-ticket"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Ticket · ${ticket.total} art.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(pesos(total), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
    }
    if (ticket.renglones.isEmpty()) {
        Vacio("Escanea lo que lleva el cliente", "Buscar, impresiones y mandar: en el botón de abajo a la derecha.")
        return
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        items(ticket.renglones, key = { it.id }) { r ->
            val p = r.clave?.let { catalogo?.porClave(it) }
            if (r.id == ticket.actual?.id) RenglonActual(r, p, color, acciones) else RenglonCorto(r, p)
        }
    }
}

/** El total como lo calcula la caja; lo que no está en el catálogo no suma (la caja lo da de alta o lo cobra a mano). */
internal fun totalDe(ticket: ListaLecturas, catalogo: CatalogoDeProductos?): Long =
    ticket.renglones.sumOf { r -> r.clave?.let { catalogo?.porClave(it) }?.importe(r.cantidad) ?: 0L }

/** Lo último, resaltado, con − y + (en 1, el − es el bote). Tocar la cantidad abre el teclado. */
@Composable
private fun RenglonActual(r: Renglon, p: Producto?, color: ColorModo, acciones: AccionesPanel) {
    val error = MaterialTheme.colorScheme.error
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (p == null) MaterialTheme.colorScheme.errorContainer else color.contenedor)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .testTag("renglon-actual"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                p?.nombre ?: "Código sin producto",
                style = MaterialTheme.typography.titleSmall,
                color = if (p == null) error else color.enContenedor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detalleDe(r, p),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = if (p == null) error else color.enContenedor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { acciones.menosUno(r.id) }, modifier = Modifier.size(48.dp).testTag("menos-uno")) {
            Icon(
                if (r.cantidad == 1) Icons.Filled.DeleteOutline else Icons.Filled.Remove,
                contentDescription = if (r.cantidad == 1) "Quitar del ticket" else "Uno menos",
            )
        }
        Text(
            r.cantidad.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier
                .widthIn(min = 48.dp)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "Teclear la cantidad") { acciones.teclearCantidad(r.id) }
                .padding(top = 10.dp)
                .semantics { contentDescription = "${r.cantidad}. Tocar para teclear la cantidad" }
                .testTag("cantidad"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { acciones.masUno(r.id) }, modifier = Modifier.size(48.dp).testTag("mas-uno")) {
            Icon(Icons.Filled.Add, contentDescription = "Uno más")
        }
    }
}

/** «Quedan 1» primero (para que no se corte en el chico), el precio por pieza y «Mayoreo» al llegar a la escala. */
internal fun detalleDe(r: Renglon, p: Producto?): String {
    if (p == null) return "${r.codigo} · en la caja se da de alta o se cobra aparte"
    val partes = mutableListOf<String>()
    if (quedanPocos(p)) partes += "Quedan ${cantidadCorta(p.existencia!!)}"
    partes += "${pesos(p.precioUnitario(r.cantidad))} c/u"
    if (p.esMayoreo(r.cantidad)) partes += "Mayoreo"
    partes += pesos(p.importe(r.cantidad))
    return partes.joinToString(" · ")
}

@Composable
private fun RenglonCorto(r: Renglon, p: Producto?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            p?.nombre ?: "Código sin producto · ${r.codigo}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (p == null) MaterialTheme.colorScheme.error else Color.Unspecified,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("×${r.cantidad}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.padding(horizontal = 8.dp))
        Text(
            p?.let { pesos(it.importe(r.cantidad)) } ?: "—",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.widthIn(min = 72.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

// ---- Conteo de prueba: el de hoy, en el celular ----

@Composable
private fun PanelConteo(conteo: ListaLecturas, catalogo: CatalogoDeProductos?, color: ColorModo, acciones: AccionesPanel) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Conteo de prueba · ${conteo.total} pzs",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = acciones.deshacerConteo, enabled = conteo.puedeDeshacer, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Deshacer")
        }
        OutlinedButton(onClick = acciones.vaciarConteo, enabled = conteo.renglones.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Nuevo")
        }
    }
    val actual = conteo.actual
    if (actual == null) {
        Vacio(
            "Escanea para contar",
            "Cada lectura suma una pieza, en este celular. Contar contra el POS (por lugares y a ciegas) llega después del piloto.",
        )
        return
    }
    val p = actual.clave?.let { catalogo?.porClave(it) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p?.nombre ?: actual.codigo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p == null) Text("No está en el catálogo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        IconButton(onClick = acciones.menosUnoConteo, modifier = Modifier.size(52.dp).testTag("menos-uno-conteo")) {
            Icon(Icons.Filled.Remove, contentDescription = "Quitar la última lectura")
        }
        Surface(shape = RoundedCornerShape(16.dp), color = color.contenedor, contentColor = color.enContenedor) {
            Text(
                "×${actual.cantidad}",
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp).testTag("contador"),
            )
        }
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        items(conteo.renglones.drop(1), key = { it.id }) { r ->
            val q = r.clave?.let { catalogo?.porClave(it) }
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(q?.nombre ?: r.codigo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("×${r.cantidad}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
            }
        }
    }
}

/** El panel vacío nombra lo que se hace y dónde está lo demás (docs/propuesta-interfaz.md, «Cómo se aprende»). */
@Composable
private fun Vacio(titulo: String, texto: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("panel-vacio")) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** El asidero: se toca (o se jala) para ver el modo completo. */
@Composable
fun Asidero(alTocar: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .then(if (alTocar != null) Modifier.clickable(role = Role.Button, onClickLabel = "Ver todo", onClick = alTocar) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 32.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
    }
}
