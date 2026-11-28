package mx.papeleriapos.android.principal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mx.papeleriapos.android.caja.CanalDeContar
import mx.papeleriapos.android.caja.ConteoMandadoDto
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.caja.RenglonDeConteoDto
import mx.papeleriapos.android.ui.Fechas
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Lo que enseña «Conteos mandados»: [conteos] null mientras se piden. */
data class EstadoConteos(val conteos: List<ConteoMandadoDto>? = null, val error: String? = null)

/**
 * «Conteos mandados» (Este celular): las últimas listas de este celular y cómo van en la caja. Se pregunta al abrir la
 * pantalla y con «Volver a preguntar», nunca sola: la caja puede tardar horas en revisarlas.
 */
@Composable
fun PantallaConteos(canal: CanalDeContar) {
    var estado by remember { mutableStateOf(EstadoConteos()) }
    val alcance = rememberCoroutineScope()
    val preguntar: () -> Unit = {
        estado = estado.copy(conteos = null, error = null)
        alcance.launch {
            estado = try {
                EstadoConteos(conteos = canal.conteosMandados())
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSeMando) {
                EstadoConteos(conteos = emptyList(), error = e.message)
            }
        }
    }
    LaunchedEffect(Unit) { preguntar() }
    ContenidoConteos(estado, preguntar)
}

@Composable
fun ContenidoConteos(estado: EstadoConteos, alPreguntar: () -> Unit) {
    var abierto by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("pantalla-conteos")) {
        item {
            Text(
                "Lo que mandaste a las cajas, del más reciente. Ahí lo revisa quien puede aceptar conteos; aquí ves cómo quedó cada producto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val conteos = estado.conteos
        when {
            conteos == null -> item { Text("Preguntando a la caja principal…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp).testTag("conteos-preguntando")) }
            conteos.isEmpty() && estado.error == null -> item {
                Text("Todavía no has mandado ningún conteo desde este celular.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
            else -> items(conteos, key = { it.id }) { c ->
                Conteo(c, abierto == c.id) { abierto = if (abierto == c.id) null else c.id }
                HorizontalDivider()
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                estado.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("conteos-error")) }
                if (conteos != null) {
                    OutlinedButton(onClick = alPreguntar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("conteos-preguntar")) {
                        Text("Volver a preguntar")
                    }
                }
            }
        }
    }
}

/** Un conteo: a qué caja, cuándo se contó, cuántos productos y cómo va; tocándolo, cada producto con su nota. */
@Composable
private fun Conteo(c: ConteoMandadoDto, abierto: Boolean, alTocar: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = if (abierto) "Cerrar" else "Ver los productos", onClick = alTocar)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("conteo-${c.id}"),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(c.caja.ifBlank { "Caja" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(textoEstado(c.estado), style = MaterialTheme.typography.labelLarge, color = colorEstado(c.estado))
        }
        val cuando = ConteosFechas.ms(c.contadoUtc)?.let { "contado ${Fechas.fechaYHora(it)}" }
        Text(
            listOfNotNull(cuando, if (c.productos == 1) "1 producto" else "${c.productos} productos", c.resolvio?.let { "revisó $it" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (c.estado == "rechazada" && !c.motivo.isNullOrBlank()) {
            Text("Motivo: ${c.motivo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (abierto) {
            if (c.renglones.isEmpty()) {
                Text("La caja no mandó los productos de esta lista.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            for (r in c.renglones) Producto(r)
        }
    }
}

@Composable
private fun Producto(r: RenglonDeConteoDto) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp)) {
        Text(r.descripcion.ifBlank { "Producto" }, style = MaterialTheme.typography.bodyMedium)
        val cantidades = buildString {
            append("contaste ${piezas(r.contado)}")
            r.aceptado?.let { append(" · quedó ${piezas(it)}") }
        }
        Text(cantidades, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        r.nota?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (r.eleventa == "por-revisar") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** En piezas, sin «.0» cuando es entero. */
private fun piezas(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

/** El estado como se le dice al dueño; uno desconocido, tal cual. */
fun textoEstado(estado: String): String = when (estado) {
    "recibida" -> "Por revisar en la caja"
    "aceptada" -> "Aplicado"
    "aceptada-con-cambios" -> "Aplicado con cambios"
    "rechazada" -> "Rechazado"
    else -> estado
}

@Composable
private fun colorEstado(estado: String): Color = when (estado) {
    "rechazada" -> MaterialTheme.colorScheme.error
    "recibida" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.primary
}

/** Las horas del POS («2026-11-22T17:42:00Z», a veces con fracción o con desfase) a ms. */
object ConteosFechas {
    fun ms(iso: String?): Long? {
        if (iso.isNullOrBlank() || iso.length < 19) return null
        val base = iso.substring(0, 19)
        val resto = iso.substring(19).replace(Regex("^\\.\\d+"), "")
        val zona = when {
            resto.isEmpty() || resto == "Z" -> "UTC"
            Regex("^[+-]\\d{2}:\\d{2}$").matches(resto) -> "GMT$resto"
            else -> "UTC"
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone(zona) }.parse(base)?.time
        } catch (e: ParseException) {
            null
        }
    }
}
