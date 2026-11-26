package mx.papeleriapos.android.principal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import mx.papeleriapos.android.caja.ImpresionDto
import mx.papeleriapos.android.caja.VentaCobradaDto
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.android.tema.LocalColoresEstado
import mx.papeleriapos.android.tema.LocalColoresModo

/*
 * La hoja de cobro (0.5.1; docs/propuesta-interfaz/prototipo-0.5.html y docs/celular-como-caja.md del POS, paso 10): el
 * total, cómo paga (efectivo con lo que entregó y su cambio, tarjeta o transferencia) y «Cobrar». Lo que más se usa va
 * primero y ya elegido: efectivo y exacto, así que casi siempre es tocar «Cobrar».
 */

/** Lo que la pantalla le pide a la hoja de cobro. */
class AccionesCobro(
    /** Cotizar y pedir las cajas otra vez (sin red, o cuando la caja principal no contestó). */
    val reintentar: () -> Unit = {},
    val forma: (FormaDePago) -> Unit = {},
    /** Lo que entregó el cliente, en centavos; null: exacto. */
    val recibir: (Long?) -> Unit = {},
    val referencia: (String) -> Unit = {},
    val caja: (String) -> Unit = {},
    /** La casilla «Imprimir ticket», para esta venta. */
    val imprimir: (Boolean) -> Unit = {},
    val cobrar: () -> Unit = {},
    /** «Mejor que lo cobre una caja»: abre «¿A qué caja?». */
    val mandar: () -> Unit = {},
)

/**
 * Por 300 ms desde que se abre, «Cobrar» no acepta el toque: el botón de la pantalla principal que la abre queda justo
 * donde sale el de la hoja, y un segundo toque por reflejo cobraría.
 */
const val MS_REBOTE_COBRAR = 300L

/** Lo que «Siguiente cliente» tarda en aceptar el toque: lo suficiente para que un doble toque no cierre el cambio. */
const val MS_REBOTE_COBRADO = 700L

@Composable
fun HojaCobrar(estado: EstadoCobro, acciones: AccionesCobro, alCerrar: () -> Unit) {
    val color = LocalColoresModo.current.ticket
    val colores = LocalColoresEstado.current
    var listo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MS_REBOTE_COBRAR)
        listo = true
    }
    // «Otro…»: el teclado para lo que entregó el cliente, en la misma hoja.
    var tecleando by rememberSaveable { mutableStateOf(false) }
    var tecleado by rememberSaveable { mutableStateOf("") }
    val efectivo = estado.forma == FormaDePago.EFECTIVO
    HojaCompleta("hoja-cobrar") {
        val articulos = if (estado.articulos == 1) "1 art." else "${estado.articulos} art."
        BarraHoja("Cobrar", articulos, alCerrar)
        // Todo va abajo, pegado a «Cobrar»: lo que se toca queda al alcance del pulgar (visto en el emulador: arriba, los
        // billetes quedaban a media pantalla). Si no cabe (el teclado de «Otro…», la letra grande), se desplaza.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
        ) {
            // El total: en efectivo, ya redondeado al peso, que es lo que se le dice al cliente.
            Column {
                Text(
                    pesos(estado.aCobrar),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    modifier = Modifier.testTag("cobro-total"),
                )
                val detalle = when {
                    estado.cotizacion == null && estado.error == null -> "Confirmando el total con la caja principal…"
                    estado.redondeo != 0L -> "Redondeado al peso, como en la caja: el exacto es ${pesos(estado.total)}"
                    else -> null
                }
                if (detalle != null) {
                    Text(
                        detalle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("cobro-detalle"),
                    )
                }
            }
            // Cómo paga.
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (f in FormaDePago.entries) {
                    val elegida = f == estado.forma
                    Text(
                        f.nombre,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (elegida) color.enContenedor else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (elegida) color.contenedor else MaterialTheme.colorScheme.surfaceVariant)
                            .selectable(selected = elegida, enabled = !estado.cobrando, role = Role.RadioButton) { acciones.forma(f) }
                            .padding(horizontal = 4.dp, vertical = 14.dp)
                            .testTag("forma-${f.name}"),
                    )
                }
            }
            when (estado.forma) {
                FormaDePago.EFECTIVO -> {
                    Text("¿Cuánto te dio?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (tecleando) {
                        val centavos = centavosDe(tecleado)
                        Text(
                            "$" + tecleado.ifEmpty { "0" },
                            fontSize = 36.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("recibido-tecleado"),
                        )
                        Column(Modifier.padding(horizontal = 0.dp)) { TeclasDePrecio(tecleado) { tecleado = it } }
                        Button(
                            onClick = {
                                acciones.recibir(centavos)
                                tecleando = false
                            },
                            enabled = centavos > 0,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("recibido-listo"),
                        ) { Text("Listo") }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val sugeridos = Efectivo.sugeridos(estado.efectivo)
                            Billete("Exacto", estado.recibidoCentavos == null, "recibido-exacto") { acciones.recibir(null) }
                            for (b in sugeridos) {
                                Billete("$" + "%,d".format(java.util.Locale.US, b / 100), estado.recibidoCentavos == b, "recibido-${b / 100}") { acciones.recibir(b) }
                            }
                            val otro = estado.recibidoCentavos?.takeIf { it !in sugeridos }
                            Billete(otro?.let { pesos(it) } ?: "Otro…", otro != null, "recibido-otro") {
                                tecleado = ""
                                tecleando = true
                            }
                        }
                    }
                    // El cambio, en grande: es lo que se necesita ver con el dinero en la mano.
                    val falta = estado.falta > 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (falta) MaterialTheme.colorScheme.errorContainer else colores.exito.copy(alpha = 0.18f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (falta) "Faltan" else "Cambio",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (falta) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            pesos(if (falta) estado.falta else estado.cambio),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                            color = if (falta) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("cobro-cambio"),
                        )
                    }
                }
                FormaDePago.TARJETA -> {
                    Nota("Cóbrala en la terminal por ${pesos(estado.total)} y confirma aquí.")
                    OutlinedTextField(
                        value = estado.referencia,
                        onValueChange = acciones.referencia,
                        singleLine = true,
                        label = { Text("Referencia de la terminal (opcional)") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().testTag("cobro-referencia"),
                    )
                }
                FormaDePago.TRANSFERENCIA -> Nota("Queda por verificar: alguien con permiso confirma en la caja que llegó el dinero, como siempre.")
            }
            // En el turno de qué caja entra: con una sola con turno, se dice; con varias, se elige.
            val conTurno = estado.conTurno
            if (conTurno.size > 1) {
                Text("¿En el turno de qué caja entra?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in conTurno) Billete(c.nombre, c.id == estado.cajaDelCobro?.id, "caja-cobro-${c.serie}") { acciones.caja(c.id) }
                }
            }
            // Imprimir el ticket: marcada como diga Ajustes; aquí se cambia solo para esta venta. Si esa caja no puede
            // imprimirlo (el interruptor apagado, sin impresora, su ventana cerrada), sale apagada con su razón.
            val puedeImprimir = estado.impresionPosible?.puede != false
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .toggleable(value = estado.pideTicket, enabled = !estado.cobrando && puedeImprimir, role = Role.Checkbox, onValueChange = acciones.imprimir)
                    .testTag("cobro-imprimir"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = estado.pideTicket, onCheckedChange = null, enabled = puedeImprimir)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Imprimir ticket", style = MaterialTheme.typography.bodyLarge, color = if (puedeImprimir) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    // Dónde va a salir o por qué no se puede, como lo dice la caja principal.
                    estado.impresionPosible?.mensaje?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("cobro-imprimir-donde"))
                    }
                }
            }
            estado.cajaDelCobro?.let { c ->
                Nota(
                    if (efectivo) "Entra al turno de la ${c.nombre}: el efectivo va a su cajón." else "Entra al turno de la ${c.nombre}.",
                    Modifier.testTag("cobro-caja"),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                estado.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("cobro-error"),
                    )
                }
                if (estado.error != null && (estado.cotizacion == null || estado.cajaDelCobro == null)) {
                    OutlinedButton(onClick = acciones.reintentar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cobro-reintentar")) {
                        Text("Volver a intentar")
                    }
                }
                Button(
                    onClick = acciones.cobrar,
                    enabled = listo && estado.puedeCobrar && !tecleando,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("cobrar-ahora"),
                    colors = ButtonDefaults.buttonColors(containerColor = color.color, contentColor = color.enColor),
                ) {
                    Text(
                        if (estado.cobrando) "Cobrando…" else "Cobrar ${pesos(estado.aCobrar)}",
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                TextButton(onClick = acciones.mandar, enabled = !estado.cobrando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cobro-mandar")) {
                    Text("Mejor que lo cobre una caja")
                }
            }
        }
    }
}

/** Una ficha para elegir: lo que entregó el cliente o la caja. */
@Composable
private fun Billete(texto: String, elegido: Boolean, etiqueta: String, alTocar: () -> Unit) {
    val color = LocalColoresModo.current.ticket
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        color = if (elegido) color.enContenedor else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (elegido) color.contenedor else MaterialTheme.colorScheme.surfaceVariant)
            .selectable(selected = elegido, role = Role.RadioButton, onClick = alTocar)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(etiqueta),
    )
}

@Composable
private fun Nota(texto: String, modifier: Modifier = Modifier) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/**
 * «Cobrado»: lo que hay que ver con el dinero en la mano. El cambio en grande, cuánto se cobró y cómo, el folio y en el
 * turno de qué caja entró (ahí se deja el efectivo). El celular no imprime: el ticket se saca en esa caja, desde sus
 * ventas del día.
 */
@Composable
fun HojaCobrado(
    venta: VentaCobradaDto,
    pidioImprimir: Boolean = false,
    /** Qué pasó con el ticket; null: el POS no lo dijo (uno anterior al paso 11). */
    impresion: ImpresionDto? = venta.impresion,
    imprimiendo: Boolean = false,
    errorDeImpresion: String? = null,
    alImprimir: () -> Unit = {},
    alCerrar: () -> Unit,
) {
    val colores = LocalColoresEstado.current
    val color = LocalColoresModo.current.ticket
    val enEfectivo = venta.cobros.any { it.forma == FormaDePago.EFECTIVO.numero }
    // «Siguiente cliente» sale justo donde estaba «Cobrar»: un segundo toque por reflejo cerraría esto sin ver el cambio.
    var listo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MS_REBOTE_COBRADO)
        listo = true
    }
    HojaCompleta("hoja-cobrado") {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Row(
                Modifier.size(72.dp).clip(CircleShape).background(colores.exito),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) { Icon(Icons.Filled.Check, contentDescription = null, tint = colores.enExito, modifier = Modifier.size(40.dp)) }
            Text(
                if (venta.yaCobrada) "Ya estaba cobrado" else "Cobrado",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("cobrado-titulo"),
            )
            if (venta.yaCobrada) {
                Text("Ese ticket ya se había cobrado: no se cobró otra vez.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            if (enEfectivo && venta.cambioCentavos > 0) {
                Text("Cambio", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    pesos(venta.cambioCentavos),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    modifier = Modifier.testTag("cobrado-cambio"),
                )
            }
            val articulos = if (venta.articulos % 1.0 == 0.0) venta.articulos.toLong().toString() else venta.articulos.toString()
            Text(
                "${pesos(venta.cobradoCentavos)} · $articulos art. · ${Cobrador.comoPago(venta)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("cobrado-detalle"),
            )
            Text(
                "Folio ${venta.folio} · ${venta.caja}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("cobrado-folio"),
            )
            Text(
                (if (enEfectivo) "Deja el efectivo en el cajón de la ${venta.caja}. " else "") + when {
                    // Lo que dijo la caja principal del ticket, tal cual.
                    impresion != null && impresion.mensaje.isNotBlank() -> impresion.mensaje
                    // Un POS que todavía no lo dice: lo que se pidió y qué hacer si no sale.
                    pidioImprimir -> "Se pidió imprimir el ticket. Si no sale, imprímelo en la ${venta.caja}, desde sus ventas del día."
                    else -> "Sin ticket. Si lo piden, se imprime en la ${venta.caja}, desde sus ventas del día."
                },
                modifier = Modifier.testTag("cobrado-ticket"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                errorDeImpresion?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("cobrado-error-ticket"))
                }
                // Mientras el ticket no haya salido se puede pedir (otra vez, o por primera si no se pidió). Ya salido,
                // otro es una reimpresión, que pide motivo y permiso: esa se hace en la caja.
                if (impresion != null && !impresion.salio) {
                    OutlinedButton(
                        onClick = alImprimir,
                        enabled = listo && !imprimiendo,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("imprimir-ticket"),
                    ) { Text(if (imprimiendo) "Pidiendo el ticket…" else if (impresion.estado == "no-pedida") "Imprimir el ticket" else "Imprimir otra vez") }
                }
                Button(
                    onClick = alCerrar,
                    enabled = listo,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("siguiente-cliente"),
                    colors = ButtonDefaults.buttonColors(containerColor = color.color, contentColor = color.enColor),
                ) { Text("Siguiente cliente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
