package mx.papeleriapos.android.rapido

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.catalogo.desdeMayoreo
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.android.catalogo.porPieza
import mx.papeleriapos.android.principal.LETRA_GRANDE
import mx.papeleriapos.android.tema.LocalColoresEstado
import mx.papeleriapos.escaner.ui.PantallaEscaner

/** El escáner a pantalla completa con lo último leído abajo y la salida a la app completa. */
@Composable
internal fun PantallaRapida(vm: EscaneoRapidoViewModel, alAbrirApp: () -> Unit, alCerrar: () -> Unit) {
    val leido by vm.leido.collectAsStateWithLifecycle()
    val catalogo by vm.catalogo.collectAsStateWithLifecycle()
    PantallaEscaner(vm.controlador, Modifier.fillMaxSize()) {
        PanelRapido(leido, catalogo, alAbrirApp, alCerrar)
    }
}

/**
 * Sobre el bloqueo solo se ve el precio (docs/propuesta-interfaz.md, «Del bolsillo al precio»): de lo último leído, el
 * nombre, el precio, el precio por pieza de una presentación, el mayoreo y la promoción, como en la tarjeta de Precio;
 * nunca la existencia, el ticket ni el conteo. Lo que no está, «No está en el catálogo» con su código; mientras cargan los
 * productos, solo el código (todavía no se sabe si está). No pasa del 31 % del alto para no tapar la franja (del 32 % al
 * 68 %), así que no lleva título: el mosaico ya dice «Precio».
 *
 * Con la letra grande no cabe todo en ese 31 % (docs/propuesta-interfaz.md, «Una mano, letra grande y TalkBack»): cede
 * lo leído, con el nombre en un renglón y lo de abajo desplazable, y no los botones, que siguen de 56 dp; «Cerrar» se
 * queda con su ícono para que «Abrir la app» quepa entero.
 */
@Composable
private fun BoxScope.PanelRapido(
    leido: LecturaRapida?,
    catalogo: CatalogoDeProductos?,
    alAbrirApp: () -> Unit,
    alCerrar: () -> Unit,
) {
    val grande = LocalDensity.current.fontScale >= LETRA_GRANDE
    val producto = leido?.let { catalogo?.porLectura(it.codigo, it.simbologia) }
    BoxWithConstraints(Modifier.matchParentSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.31f)
                .testTag("rapido-panel"),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            // Con el alfa ya no es un color del tema: sin esto el texto sale con el negro por defecto.
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Box {
                Column(
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Si no cabe todo, cede esto (se desplaza) y no los botones. Cada lectura empieza arriba, con el nombre
                    // y el precio a la vista. Se anuncia aparte, en [AnuncioPrecio].
                    val desplazamiento = remember(leido?.numero) { ScrollState(0) }
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(desplazamiento)
                            .testTag("rapido-leido"),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when {
                            producto != null -> ProductoRapido(producto, grande)
                            leido != null -> CodigoRapido(leido.codigo, noEsta = catalogo != null)
                            else -> Text("Apunta al código para ver su precio", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (grande) {
                            OutlinedIconButton(onClick = alCerrar, modifier = Modifier.size(56.dp).testTag("rapido-cerrar")) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                            }
                        } else {
                            OutlinedButton(onClick = alCerrar, modifier = Modifier.heightIn(min = 56.dp).testTag("rapido-cerrar")) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                                Text("Cerrar", Modifier.padding(start = 6.dp), maxLines = 1)
                            }
                        }
                        Button(
                            onClick = alAbrirApp,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("rapido-abrir-app"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Text("Abrir la app", Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                AnuncioPrecio(leido, producto, catalogo, Modifier.align(Alignment.TopStart))
            }
        }
    }
}

/** El producto, como en la tarjeta de Precio pero sin la existencia ni «Al ticket». */
@Composable
private fun ProductoRapido(p: Producto, grande: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            p.nombre,
            style = MaterialTheme.typography.titleMedium,
            maxLines = if (grande) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("rapido-producto"),
        )
        Text(pesos(p.precio), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("rapido-precio"))
    }
    val estado = LocalColoresEstado.current
    val porPieza = porPieza(p)
    val mayoreo = desdeMayoreo(p)
    if (porPieza != null || mayoreo != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            porPieza?.let { DatoRapido(it, MaterialTheme.colorScheme.surfaceVariant, "rapido-por-pieza") }
            mayoreo?.let { DatoRapido(it, estado.exito.copy(alpha = 0.16f), "rapido-mayoreo") }
        }
    }
    p.promocion?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = estado.exito, modifier = Modifier.testTag("rapido-promocion"))
    }
}

@Composable
private fun DatoRapido(texto: String, fondo: Color, etiqueta: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fondo)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(etiqueta),
    )
}

/** Lo leído que no se encontró: «No está en el catálogo» con su código; mientras cargan los productos, solo el código. */
@Composable
private fun CodigoRapido(codigo: String, noEsta: Boolean) {
    if (noEsta) {
        Text(
            "No está en el catálogo",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("rapido-no-esta"),
        )
    }
    Text(
        codigo,
        fontFamily = FontFamily.Monospace,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("rapido-codigo"),
    )
}

/**
 * Lo leído para TalkBack (docs/propuesta-interfaz.md, «Accesibilidad»: cada lectura se anuncia), como en Precio de la app:
 * el nombre y el precio, o «No está en el catálogo». Una región viva que no se ve, de 1 dp, como el anuncio de la pantalla
 * principal: el panel no basta, porque con el mismo producto otra vez queda igual y TalkBack no repetiría el precio. Un
 * carácter que no se oye cambia con cada lectura. Mientras cargan los productos no dice nada: lo dice al llegar.
 */
@Composable
private fun AnuncioPrecio(leido: LecturaRapida?, producto: Producto?, catalogo: CatalogoDeProductos?, modifier: Modifier = Modifier) {
    val texto = when {
        leido == null -> null
        producto != null -> "${producto.nombre}, ${pesos(producto.precio)}"
        catalogo != null -> "No está en el catálogo"
        else -> null
    }?.let { if (leido!!.numero % 2 == 0) it else it + "​" }
    Box(
        modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (texto != null) contentDescription = texto
            }
            .testTag("rapido-anuncio"),
    )
}
