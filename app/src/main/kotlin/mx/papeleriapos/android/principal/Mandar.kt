package mx.papeleriapos.android.principal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.papeleriapos.android.botonrapido.SIN_CONEXION_MANDAR
import mx.papeleriapos.android.caja.CajaParaMandarDto
import mx.papeleriapos.android.caja.CanalDeMandar
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.caja.RenglonMandado
import mx.papeleriapos.android.caja.SolicitudMandarTicket
import mx.papeleriapos.android.caja.TicketMandadoDto
import mx.papeleriapos.android.catalogo.ARTICULO_COMUN
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.catalogo.pesos
import java.util.UUID

/*
 * Mandar el ticket a una caja (docs/propuesta-interfaz.md, «Mandar a una caja»; docs/celular-como-caja.md del POS, paso 9):
 * ↑ abre «¿A qué caja?», donde se elige y se toca «Mandar». El celular manda qué y cuánto; los precios los pone la caja.
 */

/** Con los productos de prueba no se manda: la caja no los conoce. */
const val CON_PRODUCTOS_DE_PRUEBA = "Con los productos de prueba no se manda: apágalos en Ajustes › Herramientas de medición › El escáner."

/** Lo que dice «Cobrar» (y «Mandar a una caja») sin nada que mandar. */
const val TICKET_VACIO = "El ticket está vacío: escanea lo que lleva el cliente."

/**
 * Por qué todavía no se puede mandar el ticket (lo que dice «Mandar» apagado), o null si sí. El ticket vacío lo dice el botón
 * rápido aparte. Sin alta, [SIN_CONEXION_MANDAR].
 */
fun razonParaMandar(ticket: ListaLecturas, catalogo: CatalogoDeProductos?, conAlta: Boolean, dePrueba: Boolean): String? = when {
    !conAlta -> SIN_CONEXION_MANDAR
    dePrueba -> CON_PRODUCTOS_DE_PRUEBA
    ticket.renglones.isEmpty() -> null
    catalogo == null -> "Esperando la copia del catálogo para mandarlo."
    else -> {
        // El artículo común viaja con su descripción y su precio; lo demás tiene que ser de la copia.
        val sinProducto = ticket.renglones.filter { it.comunCentavos == null && catalogo.de(it)?.cobro == null }
        when (sinProducto.size) {
            0 -> null
            1 -> "El código ${sinProducto[0].codigo} no está en el catálogo: quítalo del ticket para mandarlo (en la caja se da de alta)."
            else -> "${sinProducto.size} códigos no están en el catálogo: quítalos del ticket para mandarlo (en la caja se dan de alta)."
        }
    }
}

/**
 * Los renglones como los recibe el servicio, los mismos al mandar, cotizar y cobrar: la variante y la presentación de la
 * copia, el código y la cantidad, sin precios. El artículo común va sin variante, con su descripción y su precio a mano.
 */
fun renglonesParaMandar(ticket: ListaLecturas, catalogo: CatalogoDeProductos): List<RenglonMandado> =
    ticket.renglones.reversed().map { r ->
        if (r.comunCentavos != null) {
            return@map RenglonMandado(descripcion = ARTICULO_COMUN, precioCentavos = r.comunCentavos, cantidadMilesimas = r.cantidad * 1000L)
        }
        val p = catalogo.de(r)
        RenglonMandado(
            varianteId = p?.cobro?.presentacion?.varianteId,
            presentacionId = p?.cobro?.presentacion?.id,
            codigo = p?.codigo ?: r.clave ?: r.codigo,
            cantidadMilesimas = r.cantidad * 1000L,
        )
    }

/**
 * El Id de cada ticket, uno nuevo por ticket: el mismo ticket mandado o cobrado otra vez lleva el mismo (si la respuesta se
 * perdió, la caja no lo recibe ni lo cobra dos veces; y uno que se mandó y luego se cobra en el celular es la misma venta);
 * si el ticket cambia, otro. Lo comparten [Mandador] y [Cobrador].
 */
class IdsDeTicket(private val nuevoId: () -> String = { UUID.randomUUID().toString() }) {
    private var intento: Pair<List<RenglonMandado>, String>? = null

    @Synchronized
    fun de(renglones: List<RenglonMandado>): String =
        intento?.takeIf { it.first == renglones }?.second ?: nuevoId().also { intento = renglones to it }

    /** El ticket ya llegó a donde iba (o ya se cobró): el siguiente es otro. */
    @Synchronized
    fun olvidar() {
        intento = null
    }
}

/** Lo que enseña «¿A qué caja?». [cajas] null mientras se piden. */
data class EstadoMandar(
    val cajas: List<CajaParaMandarDto>? = null,
    val elegida: String? = null,
    val mandando: Boolean = false,
    /** Lo que salió mal, en español para enseñarlo tal cual; null si nada. */
    val error: String? = null,
) {
    val cajaElegida: CajaParaMandarDto? get() = cajas?.firstOrNull { it.id == elegida }
    val puedeMandar: Boolean get() = !mandando && cajaElegida?.recibe == true
}

/**
 * La hoja «¿A qué caja?»: pide las cajas, deja elegida la última usada (si tiene la venta abierta; si no, la única que la
 * tenga) y manda. El mismo ticket mandado otra vez lleva el mismo Id (si la respuesta se perdió, la caja no lo recibe dos
 * veces); si el ticket cambia, otro.
 *
 * @param ultima la caja que se usó la última vez (en el disco); [guardarUltima] la guarda.
 */
class Mandador(
    private val canal: CanalDeMandar,
    private val alcance: CoroutineScope,
    private val ultima: () -> String?,
    private val guardarUltima: (String) -> Unit,
    /** El Id de cada ticket, el mismo que usa el cobro: mandarlo y luego cobrarlo no lo duplica. */
    private val ids: IdsDeTicket = IdsDeTicket(),
) {
    private val _estado = MutableStateFlow(EstadoMandar())
    val estado: StateFlow<EstadoMandar> = _estado.asStateFlow()

    private var trabajo: Job? = null

    /** Al abrir la hoja (y con «Volver a buscar»): pide las cajas. */
    fun cargar() {
        trabajo?.cancel()
        _estado.update { it.copy(cajas = null, error = null, mandando = false) }
        trabajo = alcance.launch {
            try {
                val cajas = canal.cajasParaMandar()
                val anterior = _estado.value.elegida ?: ultima()
                val elegida = cajas.firstOrNull { it.id.equals(anterior, ignoreCase = true) && it.recibe }?.id
                    ?: cajas.singleOrNull { it.recibe }?.id
                    ?: cajas.firstOrNull { it.id.equals(anterior, ignoreCase = true) }?.id
                _estado.value = EstadoMandar(cajas = cajas, elegida = elegida)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSeMando) {
                _estado.value = EstadoMandar(cajas = emptyList(), error = e.message)
            }
        }
    }

    fun elegir(id: String) = _estado.update { it.copy(elegida = id, error = null) }

    /**
     * Manda [ticket] a la caja elegida. Si la caja lo guardó, [alMandar] con el texto del aviso («Enviado a Caja 2 · 4 art. ·
     * $129.50»); si no, el error se queda en la hoja y el ticket, en el celular.
     */
    fun mandar(ticket: ListaLecturas, catalogo: CatalogoDeProductos, alMandar: (String) -> Unit) {
        val caja = _estado.value.cajaElegida ?: return
        if (_estado.value.mandando) return
        val renglones = renglonesParaMandar(ticket, catalogo)
        val id = ids.de(renglones)
        _estado.update { it.copy(mandando = true, error = null) }
        trabajo = alcance.launch {
            try {
                val r = canal.mandar(SolicitudMandarTicket(id, caja.id, renglones))
                guardarUltima(caja.id)
                if (r.recibido) {
                    ids.olvidar()
                    _estado.update { it.copy(mandando = false) }
                    alMandar(textoEnviado(r))
                } else {
                    _estado.update {
                        it.copy(
                            mandando = false,
                            error = "La ${r.caja} lo recogió pero no alcanzó a confirmar: revisa que esté en su espera. Si no está, vuelve a mandarlo (no se duplica).",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSeMando) {
                // Ya se cobró en la caja (se mandó, se perdió la respuesta y se cobró): ya no hay nada que mandar.
                if (e.codigo == "ya-cobrado") ids.olvidar()
                _estado.update { it.copy(mandando = false, error = e.message) }
            }
        }
    }

    companion object {
        fun textoEnviado(r: TicketMandadoDto): String {
            val articulos = if (r.articulos % 1.0 == 0.0) r.articulos.toLong().toString() else r.articulos.toString()
            return "Enviado a ${r.caja} · $articulos art. · ${pesos(r.totalCentavos)}"
        }
    }
}
