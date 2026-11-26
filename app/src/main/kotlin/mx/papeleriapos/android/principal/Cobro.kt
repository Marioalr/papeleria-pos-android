package mx.papeleriapos.android.principal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.papeleriapos.android.caja.CajaParaMandarDto
import mx.papeleriapos.android.caja.CanalDeCobrar
import mx.papeleriapos.android.caja.CobroPedido
import mx.papeleriapos.android.caja.CotizacionDto
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.caja.RenglonMandado
import mx.papeleriapos.android.caja.SolicitudCobrar
import mx.papeleriapos.android.caja.SolicitudCotizar
import mx.papeleriapos.android.caja.VentaCobradaDto
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.lista.ListaLecturas

/*
 * Cobrar en el celular (0.5.1; docs/celular-como-caja.md del POS, paso 10). El dueño lo decidió el 2026-11-22: la app es de
 * uso personal y el celular cobra. El celular dice qué y cuánto; los precios y las promociones los pone el servicio. Lo
 * cobrado entra al turno abierto de una caja de la sucursal (el efectivo se deja en el cajón de esa caja) y solo se cobra
 * con la caja principal contestando: sin red, el ticket se queda en el celular.
 */

/** Cómo paga el cliente, con el número que espera el servicio. Lo fiado (3) no se cobra desde el celular. */
enum class FormaDePago(val numero: Int, val nombre: String) {
    EFECTIVO(0, "Efectivo"),
    TARJETA(1, "Tarjeta"),
    TRANSFERENCIA(2, "Transferencia"),
    ;

    companion object {
        fun deNumero(n: Int): FormaDePago? = entries.firstOrNull { it.numero == n }
    }
}

/**
 * El redondeo al peso del efectivo, el del POS (`Venta.Efectivo` y `Redondeo.AlPeso`), para enseñar lo que se va a cobrar
 * antes de que conteste la cotización: al peso más cercano, los 50 centavos hacia arriba ($70.49 → $70; $70.50 → $71), y
 * nunca menos de $1. Tarjeta y transferencia van exactas.
 */
object Efectivo {
    fun aCobrar(exactoCentavos: Long): Long = if (exactoCentavos <= 0) 0 else maxOf(100L, ((exactoCentavos + 50) / 100) * 100)

    private val BILLETES = listOf(2_000L, 5_000L, 10_000L, 20_000L, 50_000L, 100_000L)

    /** Con qué billete pagaría: los tres que siguen a lo que se cobra ($159 → $200, $500 y $1,000). */
    fun sugeridos(efectivoCentavos: Long): List<Long> = BILLETES.filter { it > efectivoCentavos }.take(3)
}

/** La referencia de la tarjeta va hasta 60 letras, como en la caja. */
const val LARGO_REFERENCIA = 60

/** Ese servicio todavía no tiene las rutas del cobro (un POS anterior al paso 10). */
const val POS_SIN_COBRO = "La caja principal todavía no cobra desde el celular: hay que actualizar Papelería POS en esa PC. Mientras, mándalo a una caja."

const val SIN_TURNO_PARA_COBRAR = "Ninguna caja tiene un turno abierto: abre el turno en la caja principal y vuelve a cobrar."

/** Lo que enseña la hoja de cobro. */
data class EstadoCobro(
    /** El total con la copia del celular, mientras contesta la cotización. */
    val totalLocal: Long = 0,
    val articulos: Int = 0,
    /** Lo que costaría según el servicio; null mientras no contesta (y entonces no se cobra). */
    val cotizacion: CotizacionDto? = null,
    /** Las cajas de la sucursal; null mientras se piden. */
    val cajas: List<CajaParaMandarDto>? = null,
    /** En el turno de qué caja entra, si hay más de una con turno. */
    val elegida: String? = null,
    val forma: FormaDePago = FormaDePago.EFECTIVO,
    /** Lo que entregó el cliente; null: exacto. */
    val recibidoCentavos: Long? = null,
    val referencia: String = "",
    /** La casilla «Imprimir ticket» de esta venta; al abrir sale como diga Ajustes. */
    val imprimir: Boolean = true,
    val cobrando: Boolean = false,
    /** Lo que salió mal, en español para enseñarlo tal cual; null si nada. */
    val error: String? = null,
    /** La venta ya cobrada: lo que enseña «Cobrado». */
    val cobrada: VentaCobradaDto? = null,
) {
    /** El total exacto: el de tarjeta y transferencia. */
    val total: Long get() = cotizacion?.totalCentavos ?: totalLocal

    /** Lo que se cobra si todo es en efectivo, ya redondeado al peso. */
    val efectivo: Long get() = cotizacion?.efectivoCentavos ?: Efectivo.aCobrar(total)
    val aCobrar: Long get() = if (forma == FormaDePago.EFECTIVO) efectivo else total
    val redondeo: Long get() = if (forma == FormaDePago.EFECTIVO) efectivo - total else 0
    val recibido: Long get() = recibidoCentavos ?: efectivo
    val cambio: Long get() = if (forma == FormaDePago.EFECTIVO) (recibido - efectivo).coerceAtLeast(0) else 0
    val falta: Long get() = if (forma == FormaDePago.EFECTIVO) (efectivo - recibido).coerceAtLeast(0) else 0

    val conTurno: List<CajaParaMandarDto> get() = cajas.orEmpty().filter { it.turnoAbierto }

    /** La caja en cuyo turno entra el cobro: la elegida o, si solo una tiene turno, esa. */
    val cajaDelCobro: CajaParaMandarDto? get() = conTurno.firstOrNull { it.id == elegida } ?: conTurno.singleOrNull()
    val cargando: Boolean get() = error == null && (cotizacion == null || cajas == null)
    val puedeCobrar: Boolean get() = !cobrando && cotizacion != null && cajaDelCobro != null && falta == 0L && aCobrar > 0
}

/**
 * La hoja «Cobrar»: al abrirse pide lo que costaría (con los precios del servicio) y las cajas con turno; se elige cómo
 * paga y se cobra. El mismo ticket cobrado otra vez lleva el mismo Id ([IdsDeTicket]): si la respuesta se perdió, no se
 * cobra dos veces.
 *
 * @param ultima la caja en que entró el último cobro (en el disco); [guardarUltima] la guarda.
 */
class Cobrador(
    private val canal: CanalDeCobrar,
    private val alcance: CoroutineScope,
    private val ultima: () -> String?,
    private val guardarUltima: (String) -> Unit,
    private val ids: IdsDeTicket = IdsDeTicket(),
    /** Ajustes › Al cobrar › «Imprimir ticket»: cómo sale la casilla en cada cobro. */
    private val imprimirDeFabrica: () -> Boolean = { true },
) {
    private val _estado = MutableStateFlow(EstadoCobro(imprimir = imprimirDeFabrica()))
    val estado: StateFlow<EstadoCobro> = _estado.asStateFlow()

    private var trabajo: Job? = null
    private var renglones: List<RenglonMandado> = emptyList()

    /** Al abrir la hoja (y con «Volver a intentar»): en qué caja puede entrar y lo que costaría. */
    fun abrir(ticket: ListaLecturas, catalogo: CatalogoDeProductos) {
        trabajo?.cancel()
        renglones = renglonesParaMandar(ticket, catalogo)
        val antes = _estado.value
        _estado.value = EstadoCobro(
            totalLocal = totalDe(ticket, catalogo),
            articulos = ticket.total,
            // Lo elegido se conserva al volver a intentar: no se le pregunta otra vez cómo paga.
            forma = antes.forma.takeIf { antes.cobrada == null } ?: FormaDePago.EFECTIVO,
            elegida = antes.elegida ?: ultima(),
            // Otro ticket: como diga Ajustes. El mismo, al volver a intentar: como se dejó.
            imprimir = if (antes.cobrada == null && antes.error != null) antes.imprimir else imprimirDeFabrica(),
        )
        val pedido = renglones
        trabajo = alcance.launch {
            try {
                // Una tras otra: la primera llamada con la llave hace que la dirección pruebe quién es (Conexion), y dos a la
                // vez se estorbaban en esa comprobación (visto en el emulador: «No se encontró la caja principal»).
                val lista = canal.cajasParaMandar()
                val c = canal.cotizar(SolicitudCotizar(pedido))
                _estado.update { e ->
                    val conTurno = lista.filter { it.turnoAbierto }
                    e.copy(
                        cotizacion = c,
                        cajas = lista,
                        elegida = conTurno.firstOrNull { it.id.equals(e.elegida, ignoreCase = true) }?.id ?: conTurno.singleOrNull()?.id,
                        error = if (conTurno.isEmpty()) SIN_TURNO_PARA_COBRAR else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSeMando) {
                _estado.update { it.copy(error = mensajeDe(e)) }
            }
        }
    }

    fun elegirForma(forma: FormaDePago) = _estado.update { if (it.cobrando) it else it.copy(forma = forma, error = it.error.takeIf { _ -> it.cotizacion == null }) }

    /** Lo que entregó el cliente en efectivo; null: exacto. */
    fun recibir(centavos: Long?) = _estado.update { if (it.cobrando) it else it.copy(recibidoCentavos = centavos?.takeIf { c -> c > 0 }) }

    fun referencia(texto: String) = _estado.update { it.copy(referencia = texto.take(LARGO_REFERENCIA)) }

    /** La casilla «Imprimir ticket», solo para esta venta. */
    fun imprimir(si: Boolean) = _estado.update { if (it.cobrando) it else it.copy(imprimir = si) }

    fun elegirCaja(id: String) = _estado.update { if (it.cobrando) it else it.copy(elegida = id, error = null) }

    /**
     * Cobra. Si el servicio lo cobró, [alCobrar] (la pantalla vacía el ticket y enseña «Cobrado»); si no, el error se queda
     * en la hoja y el ticket, en el celular.
     */
    fun cobrar(alCobrar: (VentaCobradaDto) -> Unit) {
        val e = _estado.value
        val caja = e.cajaDelCobro ?: return
        if (!e.puedeCobrar) return
        val cobro = when (e.forma) {
            FormaDePago.EFECTIVO -> CobroPedido(e.forma.numero, e.recibido)
            FormaDePago.TARJETA -> CobroPedido(e.forma.numero, e.total, e.referencia.trim().ifEmpty { null })
            FormaDePago.TRANSFERENCIA -> CobroPedido(e.forma.numero, e.total)
        }
        val solicitud = SolicitudCobrar(ids.de(renglones), renglones, listOf(cobro), caja = caja.id, totalVisto = e.total, imprimir = e.imprimir)
        _estado.update { it.copy(cobrando = true, error = null) }
        trabajo = alcance.launch {
            try {
                val venta = canal.cobrar(solicitud)
                guardarUltima(caja.id)
                ids.olvidar()
                _estado.update { it.copy(cobrando = false, cobrada = venta) }
                alCobrar(venta)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: NoSeMando) {
                _estado.update { it.copy(cobrando = false, error = mensajeDe(ex)) }
                // El total del servicio es otro (cambió un precio o una promoción): se vuelve a cotizar para enseñarlo.
                if (ex.codigo == "total-distinto") recotizar()
            }
        }
    }

    private suspend fun recotizar() {
        try {
            val c = canal.cotizar(SolicitudCotizar(renglones))
            _estado.update { it.copy(cotizacion = c, recibidoCentavos = null) }
        } catch (e: NoSeMando) {
            // El aviso del total distinto ya está a la vista; con «Volver a intentar» se cotiza otra vez.
        }
    }

    private fun mensajeDe(e: NoSeMando): String = when {
        e.http == 404 && e.codigo == null -> POS_SIN_COBRO
        // Sin respuesta al cobrar: no se sabe si se cobró. Con el mismo Id, volver a cobrar no cobra dos veces.
        e.sinRed && _estado.value.cobrando -> "${e.message} Si no sabes si se cobró, vuelve a tocar «Cobrar»: no se cobra dos veces."
        else -> e.message ?: "La caja principal no lo aceptó."
    }

    companion object {
        /** «Efectivo», «Tarjeta» o «Transferencia por verificar», como lo dice «Cobrado». */
        fun comoPago(venta: VentaCobradaDto): String = venta.cobros.mapNotNull { c ->
            when (FormaDePago.deNumero(c.forma)) {
                FormaDePago.EFECTIVO -> "efectivo"
                FormaDePago.TARJETA -> "tarjeta"
                FormaDePago.TRANSFERENCIA -> "transferencia, por verificar"
                null -> null
            }
        }.distinct().joinToString(" y ").ifEmpty { "cobrado" }
    }
}
