package mx.papeleriapos.android.principal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import mx.papeleriapos.android.botonrapido.Lugar
import mx.papeleriapos.android.botonrapido.VECES_PARA_APRENDER
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.lista.Renglon
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.pos.busqueda.IndiceDeBusqueda

/*
 * El estado de la pantalla principal (docs/propuesta-interfaz.md, «La pantalla principal: el escáner con tres modos»),
 * sin Android: el modo, lo que enseña Precio, el ticket y el conteo de prueba (las listas que ya se guardan en disco),
 * lo que tapa el visor, los avisos y lo que se aprende del botón rápido. La pantalla lo dibuja; el ViewModel le pasa las
 * lecturas y calla la vibración mientras una hoja tapa el visor.
 */

/** Lo que tapa el visor. Mientras hay una, las lecturas no cuentan: el escáner pinta su verde, pero queda debajo. */
sealed interface Hoja {
    /** Lo que no se escanea: buscar por nombre. */
    data object Buscar : Hoja

    /** El teclado de números para la cantidad de un renglón del ticket («20» copias). */
    data class Cantidad(val idRenglon: Long) : Hoja

    /** El ticket completo (el asidero o el encabezado). */
    data object Ticket : Hoja

    /** ↑ «Enseñar al cliente»: el nombre y el precio a pantalla completa, para voltear el celular. */
    data class PrecioGrande(val producto: Producto) : Hoja
}

/** Lo que enseña la tarjeta de Precio. */
sealed interface ConsultaPrecio {
    data class Encontrado(val producto: Producto) : ConsultaPrecio

    /** Lo que no está en el catálogo: en rojo, con el código, y ofrece buscar por nombre. */
    data class NoEsta(val codigo: String) : ConsultaPrecio
}

/**
 * Lo que deshace el «Deshacer» de un aviso: exactamente lo que el aviso nombra, aunque mientras se veía entrara otra
 * lectura o se tocara − o + (deshacer el último paso del ticket quitaría eso y lo nombrado no regresaría).
 */
sealed interface Deshace {
    /**
     * La lista justo después del paso: si nada cambió desde entonces, se deshace ese paso tal cual (con su orden). Se
     * compara por sus renglones: el «Deshacer» del ticket completo regresa a esa misma lista de renglones.
     */
    val tras: ListaLecturas

    /** Lo que hace en [lista], la de ahora. */
    fun aplicar(lista: ListaLecturas): ListaLecturas =
        if (lista.renglones === tras.renglones) lista.deshacer() else enLaDeAhora(lista)

    fun enLaDeAhora(lista: ListaLecturas): ListaLecturas

    /** «Quitado del ticket»: [renglon], que estaba en [indice], regresa a su lugar entre los de [tras]. */
    data class Quitado(val renglon: Renglon, val indice: Int, override val tras: ListaLecturas) : Deshace {
        override fun enLaDeAhora(lista: ListaLecturas) = lista.regresar(renglon, indice, tras.renglones)
    }

    /** «… al ticket»: quita esa pieza de [clave]. */
    data class AlTicket(val clave: String, override val tras: ListaLecturas) : Deshace {
        override fun enLaDeAhora(lista: ListaLecturas) = lista.quitarUna(clave)
    }

    /** «Ticket vacío: empieza otro»: regresa [renglones], con lo escaneado después arriba. */
    data class Vaciado(val renglones: List<Renglon>, override val tras: ListaLecturas) : Deshace {
        override fun enLaDeAhora(lista: ListaLecturas) = lista.conLoDeAntes(renglones)
    }
}

/**
 * Un aviso con su acción («Deshacer»). [arriba]: sale arriba del panel y no tapa el botón que se acaba de tocar (otro
 * toque a «Al ticket» agrega otro). Su tiempo corre solo mientras se ve; [ms] es el de fábrica, que la pantalla alarga
 * con el «Tiempo para actuar» de Accesibilidad.
 */
data class Aviso(
    val id: Long,
    val texto: String,
    val accion: String? = null,
    val arriba: Boolean = false,
    val ms: Long = MS_AVISO,
    /** Lo que deshace su acción; null si no deshace nada (la razón de una opción apagada). */
    val deshace: Deshace? = null,
)

/** Lo que dura un aviso a la vista; con «Deshacer», el de Material para una acción. */
const val MS_AVISO = 4_200L

/**
 * Lo que hizo la última lectura, para TalkBack (docs/propuesta-interfaz.md, «Accesibilidad»): en Precio el producto y su
 * precio, en Ticket lo que se sumó y el total, en Conteo el producto y el contador. [id] cambia con cada lectura, para
 * que se vuelva a anunciar aunque diga lo mismo (el mismo producto otra vez en Precio).
 */
data class Anuncio(val id: Long, val texto: String)

class Piso(
    private val venta: MutableStateFlow<ListaLecturas>,
    private val conteo: MutableStateFlow<ListaLecturas>,
    private val almacen: AlmacenPiso,
    /** El de antes de esta versión, si nunca se eligió uno en la pantalla nueva. */
    modoAnterior: ModoApp,
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    private val _modo = MutableStateFlow(almacen.leerModo() ?: modoAnterior)
    val modo: StateFlow<ModoApp> = _modo.asStateFlow()

    private val _catalogo = MutableStateFlow<CatalogoDeProductos?>(null)
    /** null mientras se carga (se carga en su hilo; las primeras lecturas esperan sin perderse). */
    val catalogo: StateFlow<CatalogoDeProductos?> = _catalogo.asStateFlow()

    private val _precio = MutableStateFlow<ConsultaPrecio?>(null)
    val precio: StateFlow<ConsultaPrecio?> = _precio.asStateFlow()

    private val _hoja = MutableStateFlow<Hoja?>(null)
    val hoja: StateFlow<Hoja?> = _hoja.asStateFlow()

    private val _aviso = MutableStateFlow<Aviso?>(null)
    val aviso: StateFlow<Aviso?> = _aviso.asStateFlow()

    private val _anuncio = MutableStateFlow<Anuncio?>(null)
    val anuncio: StateFlow<Anuncio?> = _anuncio.asStateFlow()

    private val _zurdo = MutableStateFlow(almacen.leerZurdo())
    val zurdo: StateFlow<Boolean> = _zurdo.asStateFlow()

    private val _pistasVistas = MutableStateFlow(almacen.leerPistasVistas())
    val pistasVistas: StateFlow<Set<ModoApp>> = _pistasVistas.asStateFlow()

    private val _elegidasDeslizando = MutableStateFlow(almacen.leerElegidasDeslizando())
    /** Mientras sea menos de [VECES_PARA_APRENDER], el menú abierto con toques dice «Pulsa y desliza sin soltar». */
    val elegidasDeslizando: StateFlow<Int> = _elegidasDeslizando.asStateFlow()

    private val _contadores = MutableStateFlow(almacen.leerContadores())
    val contadores: StateFlow<ContadoresRapido> = _contadores.asStateFlow()

    /** Lo que la pantalla dice del menú del botón rápido: mientras está abierto, los avisos esperan. */
    var menuAbierto = false
        private set

    /**
     * El aviso que espera a que se cierre el menú (taparía ↑ y ↖) o el precio en grande (es para el cliente): sale al
     * cerrarlo, con su tiempo completo.
     */
    private var avisoEnEspera: Aviso? = null
    private var siguienteAviso = 1L
    private var siguienteAnuncio = 1L

    /**
     * La hoja de abajo, si la de encima se abrió desde ella: la cantidad o Buscar desde el ticket completo. Al cerrarse
     * (Listo, elegir, la × o atrás) se regresa a ella, no a la pantalla principal.
     */
    private var hojaDeAbajo: Hoja? = null

    /** Lo que abrió la última opción elegida y cuándo, para contar las que se cierran pronto sin hacer nada. */
    private var abiertaPor: Pair<Lugar, Long>? = null

    /** Lo que se eligió con cada texto de Buscar (por su clave guardada): sale primero la próxima vez, como en la caja. */
    private val elegidos = HashMap<String, String>()

    fun cargarCatalogo(c: CatalogoDeProductos) {
        _catalogo.value = c
        // Lo que ya estaba sin clave (del escaneo rápido o de la 0.1) la toma ahora, sin un paso para deshacer.
        venta.update { it.completarClaves(::claveDe) }
        conteo.update { it.completarClaves(::claveDe) }
    }

    fun cambiarModo(m: ModoApp) {
        if (_modo.value == m) return
        _modo.value = m
        almacen.guardarModo(m)
    }

    /**
     * Una lectura aceptada. Con una hoja encima no cuenta (la lectura confirma con su verde, pero el visor está tapado o
     * atenuado). Devuelve si contó; si contó, deja en [anuncio] lo que hizo.
     */
    fun alLeer(normalizado: String, simbologia: Simbologia, hora: Long): Boolean {
        if (_hoja.value != null) return false
        val c = _catalogo.value
        val producto = c?.porLectura(normalizado, simbologia)
        val texto = when (_modo.value) {
            ModoApp.PRECIO -> {
                _precio.value = producto?.let { ConsultaPrecio.Encontrado(it) } ?: ConsultaPrecio.NoEsta(normalizado)
                producto?.let { "${it.nombre}, ${pesos(it.precio)}" } ?: "No está en el catálogo"
            }
            ModoApp.TICKET -> {
                val l = venta.updateAndGet { it.completarClaves(::claveDe).sumar(normalizado, simbologia, hora, clave = producto?.clave) }
                val van = l.actual?.cantidad ?: 1
                "${producto?.nombre ?: "Código sin producto"} al ticket${if (van > 1) ", van $van" else ""}. Total ${pesos(totalDe(l, c))}"
            }
            ModoApp.CONTEO -> {
                val l = conteo.updateAndGet { it.completarClaves(::claveDe).sumar(normalizado, simbologia, hora, clave = producto?.clave) }
                val van = l.actual?.cantidad ?: 1
                "${producto?.nombre ?: "No está en el catálogo"}, ${if (van == 1) "va" else "van"} $van"
            }
        }
        _anuncio.value = Anuncio(siguienteAnuncio++, texto)
        return true
    }

    private fun claveDe(r: Renglon): String? = _catalogo.value?.de(r)?.clave

    // ---- Precio ----

    /** «Al ticket» de la tarjeta: uno más sin salir de Precio; el aviso sale arriba y nombra el producto. */
    fun alTicket(p: Producto) {
        val tras = venta.updateAndGet { it.completarClaves(::claveDe).agregarDelCatalogo(p.clave, reloj()) }
        avisoAlTicket(p, tras)
    }

    /** ↑ «Enseñar al cliente», con el producto que estaba en la tarjeta al bajar el dedo. */
    fun ensenar(p: Producto) = abrir(Hoja.PrecioGrande(p))

    // ---- Buscar: en Ticket agrega, en Precio enseña y en Conteo cuenta ----

    /** Lo que encuentra [texto], con lo que se eligió antes con él primero; vacío mientras no hay productos. */
    fun buscar(texto: String): List<Producto> {
        val c = _catalogo.value ?: return emptyList()
        return c.buscar(texto, elegido = if (texto.isBlank()) null else elegidos[IndiceDeBusqueda.claveGuardada(texto)])
    }

    /** Se eligió [p] en Buscar después de escribir [texto] (vacío: de los rápidos o lo más vendido). */
    fun elegirDeBusqueda(p: Producto, texto: String = "") {
        if (texto.isNotBlank()) elegidos[IndiceDeBusqueda.claveGuardada(texto)] = p.clave
        cerrarHoja(hizoAlgo = true)
        when (_modo.value) {
            ModoApp.PRECIO -> _precio.value = ConsultaPrecio.Encontrado(p)
            ModoApp.TICKET -> avisoAlTicket(p, venta.updateAndGet { it.completarClaves(::claveDe).agregarDelCatalogo(p.clave, reloj()) })
            ModoApp.CONTEO -> conteo.update { it.completarClaves(::claveDe).agregarDelCatalogo(p.clave, reloj()) }
        }
    }

    // ---- El ticket ----

    fun masUno(id: Long) = venta.update { it.masUno(id, reloj()) }

    /** En 1, el − es el bote: quita el renglón, con «Deshacer» (también en el ticket completo: el aviso va encima). */
    fun menosUno(id: Long) {
        var quitado: Pair<Renglon, Int>? = null
        val tras = venta.updateAndGet { l ->
            val i = l.renglones.indexOfFirst { it.id == id }
            quitado = if (i >= 0 && l.renglones[i].cantidad == 1) l.renglones[i] to i else null
            l.menosUno(id)
        }
        quitado?.let { (r, i) -> avisar("Quitado del ticket", "Deshacer", deshace = Deshace.Quitado(r, i, tras)) }
    }

    /** La cantidad tecleada; 0 («Quitar») lo quita y, como el bote, se puede deshacer. */
    fun conCantidad(id: Long, cantidad: Int) {
        var quitado: Pair<Renglon, Int>? = null
        val tras = venta.updateAndGet { l ->
            val i = l.renglones.indexOfFirst { it.id == id }
            quitado = if (i >= 0 && cantidad <= 0) l.renglones[i] to i else null
            l.conCantidad(id, cantidad, reloj())
        }
        cerrarHoja(hizoAlgo = true)
        quitado?.let { (r, i) -> avisar("Quitado del ticket", "Deshacer", deshace = Deshace.Quitado(r, i, tras)) }
    }

    /** «Empezar otro ticket»: lo vacía; se puede deshacer (lo leído después queda arriba). */
    fun empezarOtroTicket() {
        var antes: List<Renglon> = emptyList()
        val tras = venta.updateAndGet { l ->
            antes = l.renglones
            l.vaciar()
        }
        if (antes.isEmpty()) return
        avisar("Ticket vacío: empieza otro", "Deshacer", deshace = Deshace.Vaciado(antes, tras))
    }

    /**
     * El «Deshacer» del ticket completo: el último paso, sea cual sea (ahí se ve qué deshace). Si ese paso es el que nombra
     * el aviso (que se ve encima de la hoja), el aviso se va: su «Deshacer» lo haría dos veces (regresaría otra vez lo
     * quitado, o quitaría otra pieza).
     */
    fun deshacerTicket() {
        var antes: ListaLecturas? = null
        venta.update { l ->
            antes = l
            l.deshacer()
        }
        val deshecho = antes?.takeIf { it.puedeDeshacer }?.renglones ?: return
        fun yaDeshecho(a: Aviso?) = a?.deshace?.tras?.renglones === deshecho
        if (yaDeshecho(_aviso.value)) _aviso.value = null
        if (yaDeshecho(avisoEnEspera)) avisoEnEspera = null
    }

    // ---- El conteo de prueba (el de hoy, en el celular; contra el POS llega con la entrega 6) ----

    fun menosUnoConteo() = conteo.update { it.menosUno() }
    fun deshacerConteo() = conteo.update { it.deshacer() }
    fun vaciarConteo() = conteo.update { it.vaciar() }

    // ---- Lo que tapa el visor ----

    /**
     * Abre [h]. La cantidad y Buscar abiertas desde el ticket completo se apilan sobre él: al cerrarlas, regresa. El precio
     * en grande es para el cliente: el aviso que se veía (del ticket, para quien atiende) espera a que se cierre.
     */
    fun abrir(h: Hoja) {
        val actual = _hoja.value
        hojaDeAbajo = if (actual == Hoja.Ticket && (h is Hoja.Cantidad || h == Hoja.Buscar)) actual else null
        _hoja.value = h
        if (h is Hoja.PrecioGrande) esconderAviso()
    }

    /**
     * Cierra la hoja de encima (y regresa a la de abajo, si hay). Si la abrió el botón rápido y se cierra en menos de 2 s
     * sin [hizoAlgo], se eligió la que no era; eso solo se cuenta al volver a la pantalla principal.
     */
    fun cerrarHoja(hizoAlgo: Boolean = false) {
        if (_hoja.value == null) return
        val abajo = hojaDeAbajo
        hojaDeAbajo = null
        _hoja.value = abajo
        soltarAvisoEnEspera()
        if (abajo != null) return
        val (lugar, desde) = abiertaPor ?: return
        abiertaPor = null
        if (!hizoAlgo && reloj() - desde < ContadoresRapido.MS_CERRADA_PRONTO) contar { it.alCerrarPronto(lugar) }
    }

    // ---- El botón rápido ----

    /**
     * El menú se abrió (con un toque, o al pulsar para el gesto): la burbuja de la primera vez se va en ese modo. Un aviso
     * que ya se veía quedaría bajo el velo, con su tiempo corriendo: se esconde y espera, y sale completo al cerrarlo.
     */
    fun alAbrirMenu(conToque: Boolean) {
        if (!menuAbierto) esconderAviso()
        menuAbierto = true
        if (conToque) contar { it.alAbrirConToque() }
        val m = _modo.value
        if (m !in _pistasVistas.value) {
            _pistasVistas.value = _pistasVistas.value + m
            almacen.guardarPistasVistas(_pistasVistas.value)
        }
    }

    /** El menú se cerró (por donde sea; llamarlo de más no hace nada): el aviso que esperaba sale ahora, completo. */
    fun alCerrarMenu() {
        menuAbierto = false
        soltarAvisoEnEspera()
    }

    /** Se eligió la opción de [lugar], [conGesto] (deslizando) o con un toque en el menú abierto. */
    fun alElegir(lugar: Lugar, conGesto: Boolean) {
        contar { it.alElegir(lugar, conGesto) }
        if (conGesto && _elegidasDeslizando.value < VECES_PARA_APRENDER) {
            _elegidasDeslizando.value += 1
            almacen.guardarElegidasDeslizando(_elegidasDeslizando.value)
        }
        abiertaPor = lugar to reloj()
    }

    /** Se soltó en una opción apagada: el aviso dice por qué. */
    fun rechazar(razon: String) = avisar(razon)

    fun cambiarZurdo(z: Boolean) {
        _zurdo.value = z
        almacen.guardarZurdo(z)
    }

    /** Ajustes › «Volver a ver las pistas». */
    fun volverAVerPistas() {
        _pistasVistas.value = emptySet()
        almacen.guardarPistasVistas(emptySet())
        _elegidasDeslizando.value = 0
        almacen.guardarElegidasDeslizando(0)
    }

    private fun contar(cambio: (ContadoresRapido) -> ContadoresRapido) {
        _contadores.value = cambio(_contadores.value)
        almacen.guardarContadores(_contadores.value)
    }

    // ---- Los avisos ----

    private fun avisoAlTicket(p: Producto, lista: ListaLecturas) {
        val c = _catalogo.value
        val van = lista.renglones.firstOrNull { it.clave == p.clave || c?.de(it)?.clave == p.clave }?.cantidad ?: 1
        val nombre = if (p.nombre.length > LARGO_NOMBRE_AVISO) p.nombre.take(LARGO_NOMBRE_AVISO - 1).trimEnd() + "…" else p.nombre
        avisar(
            "$nombre al ticket${if (van > 1) " (van $van)" else ""} · ${lista.total} art. · ${pesos(totalDe(lista, c))}",
            "Deshacer",
            arriba = true,
            deshace = Deshace.AlTicket(p.clave, lista),
        )
    }

    private fun avisar(texto: String, accion: String? = null, arriba: Boolean = false, deshace: Deshace? = null) {
        val a = Aviso(siguienteAviso++, texto, accion, arriba, deshace = deshace)
        if (avisosEsperan) avisoEnEspera = a else _aviso.value = a
    }

    /**
     * Los avisos esperan con el menú abierto (taparían ↑ y ↖) y con el precio en grande (es para el cliente; el aviso es
     * del ticket, para quien atiende): no se ven y su tiempo no corre.
     */
    private val avisosEsperan: Boolean get() = menuAbierto || _hoja.value is Hoja.PrecioGrande

    /** El aviso que se veía se esconde y espera: sale con su tiempo completo cuando ya no haya qué espere. */
    private fun esconderAviso() {
        _aviso.value?.let {
            avisoEnEspera = it
            _aviso.value = null
        }
    }

    private fun soltarAvisoEnEspera() {
        if (avisosEsperan) return
        avisoEnEspera?.let {
            avisoEnEspera = null
            _aviso.value = it
        }
    }

    /** Se acabó el tiempo del aviso [id] (si sigue siendo el que se ve). */
    fun avisoVencido(id: Long) {
        if (_aviso.value?.id == id) _aviso.value = null
    }

    /** Se tocó la acción del aviso [id]: deshace lo que el aviso nombra (no el último paso del ticket). */
    fun accionDelAviso(id: Long) {
        val a = _aviso.value ?: return
        if (a.id != id) return
        _aviso.value = null
        val d = a.deshace ?: return
        venta.update { d.aplicar(it) }
    }

    private companion object {
        /** Lo que cabe del nombre en el aviso de «Al ticket» (como el prototipo). */
        const val LARGO_NOMBRE_AVISO = 26
    }
}

/**
 * El total como lo calcula la caja; lo que no está en el catálogo no suma (la caja lo da de alta o lo cobra a mano). Lo
 * que no trae clave se busca por su código ([CatalogoDeProductos.de]).
 */
internal fun totalDe(ticket: ListaLecturas, catalogo: CatalogoDeProductos?): Long =
    ticket.renglones.sumOf { r -> catalogo?.de(r)?.importe(r.cantidad) ?: 0L }
