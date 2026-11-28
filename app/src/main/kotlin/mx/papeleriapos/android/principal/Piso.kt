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
 * El estado de la pantalla principal, sin Android (0.5: Precio y Ticket son una sola pantalla, la venta): el modo, lo
 * último que se leyó y no está en el catálogo, el ticket y el conteo de prueba (las listas que ya se guardan en disco),
 * lo que tapa el visor, los avisos y lo que se aprende del botón rápido. La pantalla lo dibuja; el ViewModel le pasa las
 * lecturas y calla la vibración mientras una hoja tapa el visor.
 */

/** Lo que tapa el visor. Mientras hay una, las lecturas no cuentan: el escáner pinta su verde, pero queda debajo. */
sealed interface Hoja {
    /** Lo que no se escanea: buscar por nombre. */
    data object Buscar : Hoja

    /** El teclado de números para la cantidad de un renglón del ticket («20» copias). */
    data class Cantidad(val idRenglon: Long) : Hoja

    /** El ticket completo (el asidero). */
    data object Ticket : Hoja

    /** «Este celular» (arriba a la izquierda): contar, actualizar, la conexión y Ajustes. */
    data object Menu : Hoja

    /** El precio a mano de un artículo común: lo que no está en el catálogo y se cobra de todos modos. */
    data object Comun : Hoja

    /** «Cobrar»: el total, cómo paga y el cambio. Desde ella, «Mejor que lo cobre una caja» abre [Mandar]. */
    data object Cobrar : Hoja

    /** «Cobrado»: el cambio, el folio y en el turno de qué caja entró. */
    data object Cobrado : Hoja

    /** ↑ «Enseñar al cliente»: el nombre y el precio a pantalla completa, para voltear el celular. */
    data class PrecioGrande(val producto: Producto) : Hoja

    /** ↑ «Mandar a una caja»: «¿A qué caja?», donde se elige y se toca «Mandar» (el gesto nunca manda solo). */
    data object Mandar : Hoja

    /** «Terminar conteo»: «¿A qué caja va el conteo?», o volver a vender con lo contado guardado. */
    data object MandarConteo : Hoja
}

/**
 * Lo último que se leyó y no está en el catálogo: no entra al ticket ni al conteo. La tarjeta lo dice en rojo, con el
 * código, y ofrece buscarlo por nombre o, en la venta, cobrarlo como artículo común. [deVarios]: sí está, pero el código
 * es de más de un producto y no se adivina (como el POS); también se busca por nombre.
 */
data class NoEsta(val codigo: String, val deVarios: Boolean = false)

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
    /** Lo que deshace es del conteo, no del ticket. */
    val enConteo: Boolean = false,
)

/** Lo que dura un aviso a la vista; con «Deshacer», el de Material para una acción. */
const val MS_AVISO = 4_200L

/**
 * Lo que hizo la última lectura, para TalkBack (docs/propuesta-interfaz.md, «Accesibilidad»): en la venta el producto,
 * su precio, cuántos van y el total; en Conteo el producto y el contador; lo que no está, que no entró. [id] cambia con cada lectura, para
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

    private val _noEsta = MutableStateFlow<NoEsta?>(null)

    /** Lo último leído que no está en el catálogo; se va con la siguiente lectura que sí está o al tocar el ticket. */
    val noEsta: StateFlow<NoEsta?> = _noEsta.asStateFlow()

    private val _hoja = MutableStateFlow<Hoja?>(null)
    val hoja: StateFlow<Hoja?> = _hoja.asStateFlow()

    private val _aviso = MutableStateFlow<Aviso?>(null)
    val aviso: StateFlow<Aviso?> = _aviso.asStateFlow()

    private val _anuncio = MutableStateFlow<Anuncio?>(null)
    val anuncio: StateFlow<Anuncio?> = _anuncio.asStateFlow()

    private val _imprimirTicket = MutableStateFlow(almacen.leerImprimirTicket())
    val imprimirTicket: StateFlow<Boolean> = _imprimirTicket.asStateFlow()

    private val _zurdo = MutableStateFlow(almacen.leerZurdo())
    val zurdo: StateFlow<Boolean> = _zurdo.asStateFlow()

    private val _pistasVistas = MutableStateFlow(almacen.leerPistasVistas())
    val pistasVistas: StateFlow<Set<ModoApp>> = _pistasVistas.asStateFlow()

    private val _elegidasDeslizando = MutableStateFlow(almacen.leerElegidasDeslizando())
    /** Mientras sea menos de [VECES_PARA_APRENDER], el menú abierto con toques dice «Pulsa y desliza sin soltar». */
    val elegidasDeslizando: StateFlow<Int> = _elegidasDeslizando.asStateFlow()

    private val _contadores = MutableStateFlow(almacen.leerContadores())
    val contadores: StateFlow<ContadoresRapido> = _contadores.asStateFlow()

    private val _deFuera = MutableStateFlow(0L)

    /**
     * Lo último que llegó de fuera con un producto ([ensenarDeFuera]), con un número que no se repite (ni entre procesos:
     * sale del reloj); 0 si nada. La pantalla regresa a la principal y cierra el menú una vez por cada uno.
     */
    val deFuera: StateFlow<Long> = _deFuera.asStateFlow()

    /** El producto que llegó de fuera antes que los productos: se enseña en cuanto lleguen ([cargarCatalogo]). */
    private var pendienteDeFuera: String? = null

    /** Lo que la pantalla dice del menú del botón rápido: mientras está abierto, los avisos esperan. */
    var menuAbierto = false
        private set

    /** La app está bloqueada hasta entrar con la huella ([mx.papeleriapos.android.entrada.Entrada]): las lecturas no cuentan. */
    @Volatile var bloqueada = false

    /**
     * El aviso que espera a que se cierre el menú (taparía ↑ y ↖) o el precio en grande (es para el cliente): sale al
     * cerrarlo, con su tiempo completo.
     */
    private var avisoEnEspera: Aviso? = null
    private var siguienteAviso = 1L
    private var siguienteAnuncio = 1L

    /** La región viva de los anuncios está en la pantalla y TalkBack ya la conoce ([alConocerseLosAnuncios]). */
    private var anunciosALaVista = false

    /** Lo último que se anunció sin ella: se repite cuando vuelva. */
    private var anuncioSinDecir = false

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
        val antes = _catalogo.value
        // Los productos de otra sucursal: el mismo producto tiene otro id, así que cada renglón se vuelve a encontrar por
        // su código de barras (el ticket que se llevaba se queda). Antes de publicar los productos: quien los espera (el
        // cambio de sucursal, que vuelve a cotizar) puede seguir en ese mismo instante (Main.immediate).
        if (antes != null && antes !== c) {
            venta.update { l -> l.conClaves { reclavar(it, antes, c) } }
            conteo.update { l -> l.conClaves { reclavar(it, antes, c) } }
        }
        _catalogo.value = c
        // Lo que ya estaba sin clave (de la 0.1 o del escaneo rápido de antes de la 0.3) la toma ahora, sin un paso para
        // deshacer.
        venta.update { it.completarClaves(::claveDe) }
        conteo.update { it.completarClaves(::claveDe) }
        pendienteDeFuera?.let {
            pendienteDeFuera = null
            mostrarDeFuera(it)
        }
    }

    fun cambiarModo(m: ModoApp) {
        if (_modo.value == m) return
        _modo.value = m
        _noEsta.value = null
        almacen.guardarModo(m)
    }

    /** La lista del modo de ahora: los gestos de la tarjeta y el teclado de la cantidad valen igual en la venta y al contar. */
    private val listaActual: MutableStateFlow<ListaLecturas> get() = if (enConteo) conteo else venta
    private val enConteo: Boolean get() = _modo.value == ModoApp.CONTEO
    private fun donde(): String = if (enConteo) "conteo" else "ticket"

    /**
     * Una lectura aceptada. Con una hoja encima no cuenta (la lectura confirma con su verde, pero el visor está tapado o
     * atenuado). Devuelve si contó; si contó, deja en [anuncio] lo que hizo.
     *
     * Lo que no está en el catálogo se queda fuera del ticket y del conteo (0.5): queda en [noEsta], para buscarlo por
     * nombre o cobrarlo como artículo común. Sin productos todavía (no pasa con la app: el ViewModel los espera antes de
     * llamar), la lectura entra con su código y toma su producto al llegar.
     */
    fun alLeer(normalizado: String, simbologia: Simbologia, hora: Long): Boolean {
        if (_hoja.value != null || bloqueada) return false
        val c = _catalogo.value
        val producto = c?.porLectura(normalizado, simbologia)
        if (c != null && producto == null) {
            val deVarios = c.deVarios(normalizado, simbologia)
            _noEsta.value = NoEsta(normalizado, deVarios)
            anunciar(if (deVarios) DE_VARIOS else "No está en el catálogo: no entró al ${donde()}")
            return true
        }
        _noEsta.value = null
        val texto = when (_modo.value) {
            ModoApp.VENTA -> {
                val l = venta.updateAndGet { it.completarClaves(::claveDe).sumar(normalizado, simbologia, hora, clave = producto?.clave) }
                val van = l.actual?.cantidad ?: 1
                val que = producto?.let { "${it.nombre}, ${pesos(it.precioUnitario(van))}" } ?: "Código sin producto"
                "$que, al ticket${if (van > 1) ", van $van" else ""}. Total ${pesos(totalDe(l, c))}"
            }
            ModoApp.CONTEO -> {
                val l = conteo.updateAndGet { it.completarClaves(::claveDe).sumar(normalizado, simbologia, hora, clave = producto?.clave) }
                val van = l.actual?.cantidad ?: 1
                "${producto?.nombre ?: normalizado}, ${if (van == 1) "va" else "van"} $van"
            }
        }
        anunciar(texto)
        return true
    }

    private fun claveDe(r: Renglon): String? = _catalogo.value?.de(r)?.clave

    // ---- Enseñar al cliente y lo que llega de fuera ----

    /** Uno más de [p] sin escanearlo (lo elegido en Buscar): el aviso sale arriba, nombra el producto y lo deshace. */
    fun alTicket(p: Producto) {
        val tras = venta.updateAndGet { it.completarClaves(::claveDe).agregarDelCatalogo(p.clave, reloj()) }
        avisoAlTicket(p, tras)
    }

    /** ↑ «Enseñar al cliente», con el producto que estaba en la tarjeta al bajar el dedo. */
    fun ensenar(p: Producto) = abrir(Hoja.PrecioGrande(p))

    /**
     * Lo que llegó de fuera: «Abrir la app» del escaneo rápido, que sigue en la venta con ese producto
     * (docs/propuesta-interfaz.md, «Del bolsillo al precio»). [producto] es su clave del catálogo o, si no estaba, el código
     * que se leyó. Se cierran las hojas (sin contarlas como «cerrada pronto»: no las cerró quien atiende), pasa a la venta y
     * el producto entra al ticket como si se hubiera leído (0.5: ya no hay un modo que solo consulte; si solo preguntaba,
     * la tarjeta se desliza y sale), con su anuncio para TalkBack; lo que no está, como «No está» con su código. No es una
     * lectura: no vibra y la sesión de prueba no la anota. Si los productos todavía no llegan, espera y entra al llegar.
     */
    fun ensenarDeFuera(producto: String) {
        hojaDeAbajo = null
        abiertaPor = null
        _hoja.value = null
        soltarAvisoEnEspera()
        cambiarModo(ModoApp.VENTA)
        _deFuera.value = maxOf(reloj(), _deFuera.value + 1)
        if (_catalogo.value == null) {
            pendienteDeFuera = producto
            _noEsta.value = null
            return
        }
        mostrarDeFuera(producto)
    }

    private fun mostrarDeFuera(producto: String) {
        val c = _catalogo.value
        val p = c?.porClave(producto)
        if (p == null) {
            _noEsta.value = NoEsta(producto)
            anunciar("No está en el catálogo: no entró al ticket")
            return
        }
        _noEsta.value = null
        val l = venta.updateAndGet { it.completarClaves(::claveDe).agregarDelCatalogo(p.clave, reloj()) }
        val van = l.actual?.cantidad ?: 1
        anunciar("${p.nombre}, ${pesos(p.precioUnitario(van))}, al ticket${if (van > 1) ", van $van" else ""}. Total ${pesos(totalDe(l, c))}")
    }

    // ---- Lo que se le dice a TalkBack ----

    private fun anunciar(texto: String) {
        _anuncio.value = Anuncio(siguienteAnuncio++, texto)
        anuncioSinDecir = !anunciosALaVista
    }

    /**
     * La región viva de los anuncios entró a la pantalla y TalkBack ya la conoce. Lo que se anunció mientras no estaba (lo
     * que llegó de fuera con Ajustes a la vista) o en cuanto apareció se repite ahora: una región que aparece ya con su
     * texto, o que lo cambia antes de que el delegado de accesibilidad la registre, no se anuncia.
     */
    fun alConocerseLosAnuncios() {
        anunciosALaVista = true
        if (!anuncioSinDecir) return
        anuncioSinDecir = false
        _anuncio.value?.let { _anuncio.value = it.copy(id = siguienteAnuncio++) }
    }

    /** La región viva de los anuncios salió de la pantalla (a Ajustes o a las herramientas). */
    fun alOcultarseLosAnuncios() {
        anunciosALaVista = false
    }

    // ---- Buscar: en la venta agrega al ticket y en Conteo cuenta ----

    /** Lo que encuentra [texto], con lo que se eligió antes con él primero; vacío mientras no hay productos. */
    fun buscar(texto: String): List<Producto> {
        val c = _catalogo.value ?: return emptyList()
        return c.buscar(texto, elegido = if (texto.isBlank()) null else elegidos[IndiceDeBusqueda.claveGuardada(texto)])
    }

    /** Se eligió [p] en Buscar después de escribir [texto] (vacío: de los rápidos o lo más vendido). */
    fun elegirDeBusqueda(p: Producto, texto: String = "") {
        if (texto.isNotBlank()) elegidos[IndiceDeBusqueda.claveGuardada(texto)] = p.clave
        cerrarHoja(hizoAlgo = true)
        _noEsta.value = null
        when (_modo.value) {
            ModoApp.VENTA -> alTicket(p)
            ModoApp.CONTEO -> conteo.update { it.completarClaves(::claveDe).agregarDelCatalogo(p.clave, reloj()) }
        }
    }

    // ---- El ticket y el conteo: la tarjeta de lo último y sus gestos ----

    /** Un toque en la tarjeta (o el + del ticket completo): uno más. */
    fun masUno(id: Long) = listaActual.update { it.masUno(id, reloj()) }

    /** Deslizar un tramo corto (o el − del ticket completo): uno menos; en 1 lo quita, con «Deshacer». */
    fun menosUno(id: Long) {
        var quitado: Pair<Renglon, Int>? = null
        val tras = listaActual.updateAndGet { l ->
            val i = l.renglones.indexOfFirst { it.id == id }
            quitado = if (i >= 0 && l.renglones[i].cantidad == 1) l.renglones[i] to i else null
            l.menosUno(id)
        }
        quitado?.let { (r, i) -> avisarQuitado(r, i, tras) }
    }

    /** Deslizar hasta el fondo: quita el renglón entero, con todas sus piezas; «Deshacer» lo regresa como estaba. */
    fun quitar(id: Long) {
        var quitado: Pair<Renglon, Int>? = null
        val tras = listaActual.updateAndGet { l ->
            val i = l.renglones.indexOfFirst { it.id == id }
            quitado = if (i >= 0) l.renglones[i] to i else null
            l.quitar(id)
        }
        quitado?.let { (r, i) -> avisarQuitado(r, i, tras) }
    }

    private fun avisarQuitado(r: Renglon, indice: Int, tras: ListaLecturas) = avisar(
        if (r.cantidad > 1) "Quitados los ${r.cantidad} del ${donde()}" else "Quitado del ${donde()}",
        "Deshacer",
        deshace = Deshace.Quitado(r, indice, tras),
    )

    /** La cantidad tecleada; 0 («Quitar») lo quita y, como deslizar, se puede deshacer. */
    fun conCantidad(id: Long, cantidad: Int) {
        var quitado: Pair<Renglon, Int>? = null
        val tras = listaActual.updateAndGet { l ->
            val i = l.renglones.indexOfFirst { it.id == id }
            quitado = if (i >= 0 && cantidad <= 0) l.renglones[i] to i else null
            l.conCantidad(id, cantidad, reloj())
        }
        cerrarHoja(hizoAlgo = true)
        quitado?.let { (r, i) -> avisarQuitado(r, i, tras) }
    }

    /** Tocar un renglón de la lista: sube a la tarjeta, sin sumarle; ahí se le suma y se le quita. */
    fun subir(id: Long) {
        listaActual.update { it.subir(id) }
        _noEsta.value = null
    }

    /** El artículo común con su precio a mano: entra al ticket (nunca al conteo) y cierra su hoja. */
    fun agregarComun(centavos: Long) {
        if (centavos <= 0) return
        venta.update { it.agregarComun(centavos, reloj()) }
        _noEsta.value = null
        cerrarHoja(hizoAlgo = true)
    }

    /**
     * La caja ya guardó el ticket en su espera: se vacía (sin «Deshacer»: ya está en la caja), se cierran «¿A qué caja?» y el
     * ticket completo, y el aviso lo dice («Enviado a Caja 2 · 4 art. · $129.50»). Lo siguiente que se escanee es otro ticket.
     */
    fun mandado(texto: String) {
        venta.update { it.vaciar() }
        _noEsta.value = null
        hojaDeAbajo = null
        cerrarHoja(hizoAlgo = true)
        avisar(texto)
    }

    /**
     * La caja principal recibió el conteo: se vacía (sin «Deshacer»: ya está en la caja, donde lo revisan) y se dice a
     * qué caja llegó. La pantalla regresa a vender.
     */
    fun conteoMandado(texto: String) {
        conteo.update { it.vaciar() }
        _noEsta.value = null
        hojaDeAbajo = null
        cerrarHoja(hizoAlgo = true)
        avisar(texto)
    }

    /**
     * El servicio ya cobró el ticket: se vacía (sin «Deshacer»: ya es una venta) y, en lugar del cobro, queda «Cobrado» con
     * el cambio. Lo siguiente que se escanee es otro ticket.
     */
    fun cobrado() {
        venta.update { it.vaciar() }
        _noEsta.value = null
        hojaDeAbajo = null
        abiertaPor = null
        _aviso.value = null
        avisoEnEspera = null
        _hoja.value = Hoja.Cobrado
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

    // ---- El conteo de prueba (el de hoy, en el celular; contra el POS llega después del piloto) ----

    /** «Empezar otro conteo» (en el menú): lo vacía; se puede deshacer (lo contado después queda arriba). */
    fun empezarOtroConteo() {
        var antes: List<Renglon> = emptyList()
        val tras = conteo.updateAndGet { l ->
            antes = l.renglones
            l.vaciar()
        }
        if (antes.isEmpty()) return
        avisar("Conteo vacío: empieza otro", "Deshacer", deshace = Deshace.Vaciado(antes, tras), enConteo = true)
    }

    // ---- Lo que tapa el visor ----

    /**
     * Abre [h]. La cantidad y Buscar abiertas desde el ticket completo se apilan sobre él: al cerrarlas, regresa. El precio
     * en grande es para el cliente: el aviso que se veía (del ticket, para quien atiende) espera a que se cierre.
     */
    fun abrir(h: Hoja) {
        val actual = _hoja.value
        hojaDeAbajo = when {
            actual == Hoja.Ticket && (h is Hoja.Cantidad || h == Hoja.Buscar || h == Hoja.Mandar || h == Hoja.Comun) -> actual
            // «Mejor que lo cobre una caja»: si no se manda, se regresa al cobro.
            actual == Hoja.Cobrar && h == Hoja.Mandar -> actual
            else -> null
        }
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

    /** Ajustes › Al cobrar: la casilla «Imprimir ticket» del cobro sale marcada o no. En cada venta se puede cambiar. */
    fun cambiarImprimirTicket(imprimir: Boolean) {
        _imprimirTicket.value = imprimir
        almacen.guardarImprimirTicket(imprimir)
    }

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

    private fun avisar(texto: String, accion: String? = null, arriba: Boolean = false, deshace: Deshace? = null, enConteo: Boolean = this.enConteo) {
        val a = Aviso(siguienteAviso++, texto, accion, arriba, deshace = deshace, enConteo = enConteo && deshace != null)
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
        (if (a.enConteo) conteo else venta).update { d.aplicar(it) }
    }

    companion object {
        /** Lo que cabe del nombre en el aviso de «Al ticket» (como el prototipo). */
        private const val LARGO_NOMBRE_AVISO = 26

        /** Lo que dice la tarjeta (y TalkBack) de un código que es de más de un producto, como el POS. */
        const val DE_VARIOS = "Ese código es de más de un producto: búscalo por nombre"
    }
}

/**
 * El total como lo calcula la caja; lo que no está en el catálogo no suma (la caja lo da de alta o lo cobra a mano). Lo
 * que no trae clave se busca por su código ([CatalogoDeProductos.de]).
 */
internal fun totalDe(ticket: ListaLecturas, catalogo: CatalogoDeProductos?): Long =
    ticket.renglones.sumOf { r -> catalogo?.de(r)?.importe(r.cantidad) ?: 0L }

/**
 * [r] con su clave en [ahora], si la de [antes] ya no está (llegaron los productos de otra sucursal, o se quitó ese
 * producto): lo escaneado se vuelve a encontrar por lo que se leyó; lo que se agregó buscando, por el código de barras que
 * tenía en [antes]. Si no está en [ahora], se queda sin clave y con su código, y sale como «no está en el catálogo».
 * Los artículos comunes y lo que sigue estando no cambian.
 */
internal fun reclavar(r: Renglon, antes: CatalogoDeProductos, ahora: CatalogoDeProductos): Renglon {
    if (r.comunCentavos != null) return r
    val clave = r.clave ?: return r
    if (ahora.porClave(clave) != null) return r
    val escaneado = r.simbologia != Simbologia.OTRA
    val codigo = if (escaneado) r.codigo else antes.de(r)?.codigo?.takeIf { it != clave }
    val p = codigo?.let { ahora.porLectura(it, r.simbologia) }
    return when {
        escaneado -> r.copy(clave = p?.clave)
        p != null -> r.copy(codigo = p.clave, clave = p.clave)
        codigo != null -> r.copy(codigo = codigo, clave = null)
        else -> r
    }
}
