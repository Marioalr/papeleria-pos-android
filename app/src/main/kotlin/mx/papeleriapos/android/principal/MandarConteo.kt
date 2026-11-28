package mx.papeleriapos.android.principal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.papeleriapos.android.caja.CajaParaContarDto
import mx.papeleriapos.android.caja.CanalDeContar
import mx.papeleriapos.android.caja.ConteoMandadoDto
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.caja.RenglonContado
import mx.papeleriapos.android.caja.SolicitudMandarConteo
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.lista.ListaLecturas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/*
 * Mandar el conteo a una caja (docs/celular-como-caja.md del POS, «Mandar un conteo a una caja»). El celular nunca cambia
 * existencias: la lista llega a una caja de la sucursal y ahí quien tiene «Conteo completo de un departamento» la acepta
 * (tal cual o con cambios) o la rechaza. La cantidad de cada renglón es lo que hay en total; lo que no se contó no se manda.
 */

/**
 * Los renglones del conteo como los recibe el POS: cada producto por su variante y presentación de la copia (o, si no la
 * encuentra, por su código), en la presentación con que se escaneó (2 cajas de 12 van como 2000) y con la hora de su último
 * escaneo. Los artículos comunes no son del inventario: no van.
 */
fun renglonesDelConteo(conteo: ListaLecturas, catalogo: CatalogoDeProductos): List<RenglonContado> =
    conteo.renglones.reversed().filter { it.comunCentavos == null }.map { r ->
        val p = catalogo.de(r)
        RenglonContado(
            varianteId = p?.cobro?.presentacion?.varianteId,
            presentacionId = p?.cobro?.presentacion?.id,
            codigo = p?.codigo ?: r.clave ?: r.codigo,
            cantidadMilesimas = r.cantidad * 1000L,
            contadoUtc = utc(r.hora),
        )
    }

/** La hora (ms del reloj de pared) en ISO 8601 y UTC, como la espera el POS. */
fun utc(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

/**
 * El id de la lista que se está mandando, guardado con ella (en el disco: sobrevive a que Android cierre la app). La misma
 * lista mandada otra vez lleva el mismo id (si la respuesta se perdió, el POS no la duplica); si la lista cambió, otro.
 * [leer], [guardar] y [borrar]: el id y la firma de la lista con que se dio.
 */
class IdDeConteo(
    private val leer: () -> Pair<String, String>?,
    private val guardar: (id: String, firma: String) -> Unit,
    private val borrar: () -> Unit,
    private val nuevoId: () -> String = { UUID.randomUUID().toString() },
) {
    @Synchronized
    fun de(renglones: List<RenglonContado>): String {
        val firma = renglones.joinToString(";") { "${it.varianteId}|${it.presentacionId}|${it.codigo}|${it.cantidadMilesimas}" }
        leer()?.takeIf { it.second == firma }?.let { return it.first }
        return nuevoId().also { guardar(it, firma) }
    }

    /** La lista ya llegó: la siguiente es otra. */
    @Synchronized
    fun olvidar() = borrar()

    companion object {
        /** En memoria (las pruebas). */
        fun enMemoria(nuevoId: () -> String = { UUID.randomUUID().toString() }): IdDeConteo {
            var guardado: Pair<String, String>? = null
            return IdDeConteo({ guardado }, { id, firma -> guardado = id to firma }, { guardado = null }, nuevoId)
        }
    }
}

/** Lo que enseña «¿A qué caja va el conteo?». [cajas] null mientras se piden. */
data class EstadoMandarConteo(
    val cajas: List<CajaParaContarDto>? = null,
    val elegida: String? = null,
    val mandando: Boolean = false,
    /** Lo que salió mal, en español para enseñarlo tal cual; null si nada. */
    val error: String? = null,
    /** No contestó la caja principal al pedir las cajas: si hay otra sucursal enlazada, se ofrece cambiar a ella. */
    val otraSucursal: Boolean = false,
) {
    val cajaElegida: CajaParaContarDto? get() = cajas?.firstOrNull { it.id == elegida }
    val puedeMandar: Boolean get() = !mandando && cajaElegida?.recibe == true
}

/**
 * La hoja «¿A qué caja va el conteo?»: pide las cajas (con quién atiende o por qué no recibe), deja elegida la última usada
 * si recibe (si no, la única que reciba) y manda. La lista se queda en el celular hasta que el POS la recibe.
 *
 * @param ultima la caja a la que se mandó el último conteo (en el disco); [guardarUltima] la guarda.
 */
class Contador(
    private val canal: CanalDeContar,
    private val alcance: CoroutineScope,
    private val ultima: () -> String?,
    private val guardarUltima: (String) -> Unit,
    private val ids: IdDeConteo,
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    private val _estado = MutableStateFlow(EstadoMandarConteo())
    val estado: StateFlow<EstadoMandarConteo> = _estado.asStateFlow()

    private var trabajo: Job? = null

    /** Al abrir la hoja (y con «Volver a buscar»): pide las cajas. */
    fun cargar() {
        trabajo?.cancel()
        _estado.update { it.copy(cajas = null, error = null, mandando = false, otraSucursal = false) }
        trabajo = alcance.launch {
            try {
                val cajas = canal.cajasParaContar()
                val anterior = _estado.value.elegida ?: ultima()
                val elegida = cajas.firstOrNull { it.id.equals(anterior, ignoreCase = true) && it.recibe }?.id
                    ?: cajas.singleOrNull { it.recibe }?.id
                _estado.value = EstadoMandarConteo(
                    cajas = cajas,
                    elegida = elegida,
                    // Cada caja dice por qué no recibe; sin ninguna, la hoja lo dice abajo.
                    error = if (cajas.isEmpty()) SIN_CAJAS else null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSeMando) {
                _estado.value = EstadoMandarConteo(cajas = emptyList(), error = e.message, otraSucursal = e.sinRed)
            }
        }
    }

    fun elegir(id: String) = _estado.update { if (it.mandando) it else it.copy(elegida = id, error = null) }

    /**
     * Manda [conteo] a la caja elegida. Si el POS la recibió, [alMandar] con lo que contestó (la pantalla vacía el conteo y
     * lo dice); si no, el error se queda en la hoja y el conteo, en el celular.
     */
    fun mandar(conteo: ListaLecturas, catalogo: CatalogoDeProductos, alMandar: (ConteoMandadoDto) -> Unit) {
        val e = _estado.value
        val caja = e.cajaElegida ?: return
        if (!e.puedeMandar) return
        val renglones = renglonesDelConteo(conteo, catalogo)
        if (renglones.isEmpty()) {
            _estado.update { it.copy(error = "El conteo está vacío: escanea lo que vas a contar.") }
            return
        }
        val ahora = reloj()
        val solicitud = SolicitudMandarConteo(
            id = ids.de(renglones),
            destino = caja.id,
            // La de la lista: cuando se escaneó lo último. Cada renglón trae la suya.
            contadoUtc = conteo.renglones.maxOfOrNull { it.hora }?.let(::utc),
            enviadoUtc = utc(ahora),
            renglones = renglones,
        )
        _estado.update { it.copy(mandando = true, error = null) }
        trabajo = alcance.launch {
            try {
                val r = canal.mandarConteo(solicitud)
                guardarUltima(caja.id)
                ids.olvidar()
                _estado.update { it.copy(mandando = false) }
                alMandar(r)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: NoSeMando) {
                _estado.update { it.copy(mandando = false, error = ex.message) }
            }
        }
    }

    companion object {
        /** «Conteo mandado a la Caja 2 · 14 productos». */
        fun textoMandado(r: ConteoMandadoDto, renglones: Int): String {
            val n = if (r.productos > 0) r.productos else renglones
            return "Conteo mandado a ${r.caja.ifBlank { "la caja" }} · ${if (n == 1) "1 producto" else "$n productos"}"
        }

        const val SIN_CAJAS = "No hay cajas en esta sucursal que reciban conteos: la caja tiene que haber abierto turno alguna vez."
    }
}
