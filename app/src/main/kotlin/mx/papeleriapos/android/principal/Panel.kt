package mx.papeleriapos.android.principal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.catalogo.cantidadCorta
import mx.papeleriapos.android.catalogo.desdeMayoreo
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.android.catalogo.porPieza
import mx.papeleriapos.android.catalogo.pzs
import mx.papeleriapos.android.catalogo.quedan
import mx.papeleriapos.android.catalogo.quedanPocos
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.lista.Renglon
import mx.papeleriapos.android.tema.ColorModo
import mx.papeleriapos.android.tema.LocalColoresEstado
import mx.papeleriapos.android.tema.LocalColoresModo

/*
 * El panel, bajo la franja (0.5): el asidero, la tarjeta de lo último con sus gestos ([TarjetaActual]), los demás renglones
 * y, en el hueco donde iba la barra de pestañas, «Cobrar» con el botón rápido. Es una Surface: se queda con los toques que
 * no son de sus botones, para que no lleguen al visor (que con un toque enfoca o reanuda).
 */

/** Lo que la pantalla le pide al panel; los renglones van por su id, en la lista del modo de ahora. */
class AccionesPanel(
    val buscar: () -> Unit = {},
    val verTicket: () -> Unit = {},
    val masUno: (Long) -> Unit = {},
    val menosUno: (Long) -> Unit = {},
    /** El renglón entero, con todas sus piezas. */
    val quitar: (Long) -> Unit = {},
    val teclearCantidad: (Long) -> Unit = {},
    /** Tocar un renglón de la lista lo sube a la tarjeta, sin sumarle. */
    val subir: (Long) -> Unit = {},
    /** El precio a mano de lo que no está en el catálogo. */
    val comun: () -> Unit = {},
)

/**
 * Desde esta escala de la letra el panel se acomoda (docs/propuesta-interfaz.md, «Letra al 200 %»): en el chico, la
 * tarjeta no cabe con el nombre en dos renglones y el precio a 30 sp.
 */
internal const val LETRA_GRANDE = 1.3f

@Composable
fun ContenidoPanel(
    modo: ModoApp,
    noEsta: NoEsta?,
    ticket: ListaLecturas,
    conteo: ListaLecturas,
    catalogo: CatalogoDeProductos?,
    acciones: AccionesPanel,
    modifier: Modifier = Modifier,
    /** «Botón rápido a la izquierda»: el panel vacío dice dónde está. */
    zurdo: Boolean = false,
    /** La caja le quitó el acceso a este celular y el dueño no lo ha visto ([mx.papeleriapos.android.caja.EstadoConexion.sinAcceso]). */
    sinAcceso: Boolean = false,
    /** Una hoja tapa el panel: la tarjeta no le quita el «atrás» desde la orilla a la hoja de encima. */
    tapado: Boolean = false,
) {
    val color = LocalColoresModo.current.de(modo)
    val venta = modo == ModoApp.VENTA
    val lista = if (venta) ticket else conteo
    // Mientras los productos se cargan (después de la apertura), lo guardado no sale en rojo ni el importe en $0.00.
    val cargando = catalogo == null
    Column(modifier.fillMaxSize()) {
        val actual = lista.actual
        when {
            // Nada se pierde en el chico ni con la letra al 200 %: si la tarjeta no cabe en el panel, se desplaza dentro de
            // él. Cada producto empieza arriba: el precio a la vista aunque el de antes se haya desplazado.
            noEsta != null -> Box(Modifier.verticalScroll(remember(noEsta) { ScrollState(0) })) {
                NoEstaEnElCatalogo(noEsta, venta, sinAcceso, acciones)
            }
            actual != null -> Box(Modifier.verticalScroll(remember(actual.id) { ScrollState(0) })) {
                TarjetaActual(actual, catalogo?.de(actual), cargando, color, venta, acciones, excluirAtras = !tapado)
            }
            venta -> Vacio(
                "Escanea lo que lleva el cliente",
                "Sale su precio y entra al ticket. Si solo preguntaba, desliza la tarjeta a la izquierda. Buscar y " +
                    "enseñar: en el botón de ${ladoDelBoton(zurdo)}.",
            )
            else -> Vacio(
                "Escanea para contar",
                "Cada lectura suma una pieza, en este celular; las demás se suman con un toque en la tarjeta.",
            )
        }
        // Lo último va fijo arriba, en la tarjeta, y lo de antes abajo. Con «no está» a la vista, todo el ticket va abajo.
        val resto = if (noEsta != null) lista.renglones else lista.renglones.drop(1)
        if (resto.isEmpty()) return@Column
        val estado = rememberLazyListState()
        LaunchedEffect(actual?.id, actual?.cantidad) { estado.scrollToItem(0) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(if (venta) "renglones-ticket" else "renglones-conteo"), state = estado) {
            items(resto, key = { it.id }) { r -> RenglonCorto(r, catalogo?.de(r), cargando, venta) { acciones.subir(r.id) } }
        }
    }
}

/** Dónde está el botón rápido, para los textos del panel vacío («Cómo se aprende»). */
internal fun ladoDelBoton(zurdo: Boolean): String = if (zurdo) "abajo a la izquierda" else "abajo a la derecha"

/**
 * Lo que dice la lista debajo del nombre: el departamento y el código (los productos de prueba), o solo el código (la
 * copia del catálogo no trae el departamento); vacío si no tiene código.
 */
internal fun detalleDe(p: Producto): String = listOfNotNull(p.departamento.ifBlank { null }, p.codigo).joinToString(" · ")

/** Lo mismo, para un renglón de la lista de Buscar, donde cabe menos: el departamento o, si no tiene, el código. */
internal fun detalleCorto(p: Producto): String = p.departamento.ifBlank { p.codigo.orEmpty() }

/**
 * Lo que no está en el catálogo (0.5): no entró al ticket ni al conteo. En rojo, con el código, y dos salidas: buscarlo
 * por nombre o, en la venta, cobrarlo como artículo común, con el precio a mano. Con [NoEsta.deVarios], sí está pero el
 * código es de más de un producto: no se adivina, como el POS. Con [sinAcceso], abajo y discreto, por qué no encuentra
 * nada: la caja le quitó el acceso y se borró la copia.
 */
@Composable
private fun NoEstaEnElCatalogo(noEsta: NoEsta, venta: Boolean, sinAcceso: Boolean, acciones: AccionesPanel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("no-esta"),
    ) {
        val tinta = MaterialTheme.colorScheme.onErrorContainer
        Text(if (noEsta.deVarios) Piso.DE_VARIOS else "No está en el catálogo", style = MaterialTheme.typography.titleMedium, color = tinta)
        Text(
            "${noEsta.codigo} · no entró al ${if (venta) "ticket" else "conteo"}",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = tinta,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            OutlinedButton(onClick = acciones.buscar, modifier = Modifier.heightIn(min = 48.dp).testTag("buscar-por-nombre")) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = tinta)
                Spacer(Modifier.width(6.dp))
                Text("Buscar por nombre", color = tinta, maxLines = 1)
            }
            if (venta) {
                OutlinedButton(onClick = acciones.comun, modifier = Modifier.heightIn(min = 48.dp).testTag("articulo-comun")) {
                    Text("Artículo común", color = tinta, maxLines = 1)
                }
            }
        }
        if (sinAcceso) {
            Text(SIN_ACCESO_EN_PRECIO, style = MaterialTheme.typography.bodySmall, color = tinta, modifier = Modifier.padding(top = 6.dp).testTag("sin-acceso"))
        }
    }
}

internal const val SIN_ACCESO_EN_PRECIO = "Este celular ya no tiene acceso a la caja y se borró la copia: Ajustes › La conexión."

/**
 * Lo que dice el ticket completo de un renglón: «Queda 1» primero (para que no se corte en el chico), el precio por pieza
 * y «Mayoreo» al llegar a la escala. Mientras los productos se cargan ([cargando]), solo «…»: todavía no se sabe si está.
 */
internal fun detalleDe(r: Renglon, p: Producto?, cargando: Boolean = false): String {
    if (p == null) return if (cargando) "…" else "${r.codigo} · ya no está en el catálogo: quítalo"
    val partes = mutableListOf<String>()
    if (quedanPocos(p)) partes += quedan(p.existencia!!)
    partes += "${pesos(p.precioUnitario(r.cantidad))} c/u"
    if (p.esMayoreo(r.cantidad)) partes += "Mayoreo"
    partes += pesos(p.importe(r.cantidad))
    return partes.joinToString(" · ")
}

/** Un renglón de los de antes: se toca para subirlo a la tarjeta (sin sumarle), donde están los gestos. */
@Composable
private fun RenglonCorto(r: Renglon, p: Producto?, cargando: Boolean, venta: Boolean, alTocar: () -> Unit) {
    val sinProducto = p == null && !cargando
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClickLabel = "Subir a la tarjeta", onClick = alTocar)
            .padding(horizontal = 20.dp)
            .testTag("renglon-${r.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            p?.nombre ?: if (cargando) r.codigo else "Código sin producto · ${r.codigo}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (sinProducto) MaterialTheme.colorScheme.error else Color.Unspecified,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("×${r.cantidad}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.padding(horizontal = 8.dp))
        if (venta) {
            Text(
                p?.let { pesos(it.importe(r.cantidad)) } ?: if (cargando) "…" else "—",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.widthIn(min = 72.dp),
                textAlign = TextAlign.End,
            )
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

/** Lo que hay que jalar hacia arriba, desde el asidero o el encabezado, para que abra lo completo. */
private val JALAR = 32.dp

/**
 * Jalar hacia arriba abre lo completo (docs/propuesta-interfaz.md: «el asidero: se jala para ver todo»; el arrastre del
 * panel vive en el asidero y el encabezado). Pasado el margen de un toque se queda con el dedo, así que un jalón no
 * cuenta como toque. [alJalar] es un estado: un panel que se recompone a medio jalón no lo corta.
 */
private fun Modifier.jalarParaVerTodo(alJalar: State<() -> Unit>) = pointerInput(Unit) {
    var arrastre = 0f
    detectVerticalDragGestures(
        onDragStart = { arrastre = 0f },
        onDragEnd = { if (arrastre <= -JALAR.toPx()) alJalar.value() },
        onDragCancel = { arrastre = 0f },
    ) { cambio, dy ->
        cambio.consume()
        arrastre += dy
    }
}

/**
 * El asidero: se toca o se jala para ver el ticket completo (con el − y el + de cada renglón, deshacer, «Empezar otro
 * ticket» y mandarlo). El panel ya no lleva encabezado (el total va en «Cobrar»), así que TalkBack lo ofrece aquí.
 */
@Composable
fun Asidero(alAbrir: (() -> Unit)?, modifier: Modifier = Modifier) {
    val accion = rememberUpdatedState(alAbrir ?: {})
    Box(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .then(
                if (alAbrir == null) Modifier
                else Modifier
                    .jalarParaVerTodo(accion)
                    .pointerInput(Unit) { detectTapGestures { accion.value() } }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ver el ticket completo"
                        onClick { accion.value(); true }
                    },
            )
            .testTag("asidero"),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 32.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
    }
}
