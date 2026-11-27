package mx.papeleriapos.android.principal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mx.papeleriapos.android.caja.Conexion
import mx.papeleriapos.android.caja.Enlace
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.catalogo.CatalogoDeProductos

/** «¿Estás en Sucursal 2? Cambiar»: [buscando] es el id de la caja del enlace que se está buscando; [error], si no se encontró. */
data class EstadoOtraSucursal(val buscando: String? = null, val error: String? = null)

/**
 * Cambiar a otra sucursal enlazada desde Cobrar o «¿A qué caja?», cuando no contestó la caja principal. Solo al tocarlo: se
 * busca una vez la caja principal de esa sucursal ([Conexion.buscarYCambiarA]) y, si prueba quién es, se cambia y se espera
 * a que los productos sean los de su copia antes de volver a cotizar (los ids de un producto son otros en cada sucursal).
 */
class CambioDeSucursal(
    private val buscarYCambiar: suspend (String) -> Unit,
    private val alcance: CoroutineScope,
    /** Cuánto se espera a que se armen los productos de la otra copia antes de seguir con lo que haya. */
    private val msProductos: Long = 10_000,
) {
    constructor(conexion: Conexion, alcance: CoroutineScope) : this({ conexion.buscarYCambiarA(it) }, alcance)

    private val _estado = MutableStateFlow(EstadoOtraSucursal())
    val estado: StateFlow<EstadoOtraSucursal> = _estado.asStateFlow()
    private var trabajo: Job? = null

    /**
     * Busca y cambia a la sucursal de [cajaId]; con los productos ya de esa sucursal (o, si tardan más de [msProductos], con
     * los que haya), [alCambiar].
     */
    fun cambiar(cajaId: String, productos: StateFlow<CatalogoDeProductos?>, alCambiar: (CatalogoDeProductos) -> Unit) {
        if (_estado.value.buscando != null) return
        val antes = productos.value
        _estado.value = EstadoOtraSucursal(buscando = cajaId)
        trabajo = alcance.launch {
            try {
                buscarYCambiar(cajaId)
                val nuevos = withTimeoutOrNull(msProductos) { productos.first { it != null && it !== antes } } ?: productos.value
                _estado.value = EstadoOtraSucursal()
                nuevos?.let(alCambiar)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSeMando) {
                _estado.value = EstadoOtraSucursal(error = e.message)
            }
        }
    }

    /** Se cerró la hoja: lo que dijo la búsqueda ya no aplica. */
    fun olvidar() {
        trabajo?.cancel()
        _estado.value = EstadoOtraSucursal()
    }
}

/** Un botón por cada otra sucursal enlazada: «¿Estás en Sucursal 2? Cambiar», con lo que salió mal abajo. */
@Composable
fun BotonesOtraSucursal(otras: List<Enlace>, estado: EstadoOtraSucursal, alCambiar: (String) -> Unit) {
    if (otras.isEmpty()) return
    for (e in otras) {
        val buscando = estado.buscando?.equals(e.cajaId, ignoreCase = true) == true
        OutlinedButton(
            onClick = { alCambiar(e.cajaId) },
            enabled = estado.buscando == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("otra-sucursal-${e.cajaId}"),
        ) {
            if (buscando) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Buscando ${e.sucursal.ifBlank { "la otra sucursal" }}…", maxLines = 1)
            } else {
                Text("¿Estás en ${e.sucursal.ifBlank { "otra sucursal" }}? Cambiar", maxLines = 1)
            }
        }
    }
    estado.error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("otra-sucursal-error"))
    }
}
