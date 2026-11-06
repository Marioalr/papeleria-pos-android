package mx.papeleriapos.android.principal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mx.papeleriapos.android.botonrapido.Lugar
import mx.papeleriapos.android.botonrapido.VECES_PARA_APRENDER
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.android.lista.ListaLecturas
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
 * Un aviso con su acción («Deshacer»). [arriba]: sale arriba del panel y no tapa el botón que se acaba de tocar (otro
 * toque a «Al ticket» agrega otro). Su tiempo corre solo mientras se ve.
 */
data class Aviso(
    val id: Long,
    val texto: String,
    val accion: String? = null,
    val arriba: Boolean = false,
    val ms: Long = MS_AVISO,
)

/** Lo que dura un aviso a la vista; con «Deshacer», el de Material para una acción. */
const val MS_AVISO = 4_200L

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

    private val _zurdo = MutableStateFlow(almacen.leerZurdo())
    val zurdo: StateFlow<Boolean> = _zurdo.asStateFlow()

    private val _pistasVistas = MutableStateFlow(almacen.leerPistasVistas())
    val pistasVistas: StateFlow<Set<ModoApp>> = _pistasVistas.asStateFlow()

    private val _elegidasDeslizando = MutableStateFlow(almacen.leerElegidasDeslizando())
    /** Mientras sea menos de [VECES_PARA_APRENDER], el menú abierto con toques dice «Pulsa y desliza sin soltar». */
    val elegidasDeslizando: StateFlow<Int> = _elegidasDeslizando.asStateFlow()

    private val _contadores = MutableStateFlow(almacen.leerContadores())
    val contadores: StateFlow<ContadoresRapido> = _contadores.asStateFlow()

    private var menuAbierto = false

    /** El aviso que llegó con el menú abierto: sale al cerrarlo, con su tiempo completo (taparía ↑ y ↖). */
    private var avisoEnEspera: Aviso? = null
    private var siguienteAviso = 1L

    /** Lo que abrió la última opción elegida y cuándo, para contar las que se cierran pronto sin hacer nada. */
    private var abiertaPor: Pair<Lugar, Long>? = null

    /** Lo que se eligió con cada texto de Buscar (por su clave guardada): sale primero la próxima vez, como en la caja. */
    private val elegidos = HashMap<String, String>()

    fun cargarCatalogo(c: CatalogoDeProductos) {
        _catalogo.value = c
    }

    fun cambiarModo(m: ModoApp) {
        if (_modo.value == m) return
        _modo.value = m
        almacen.guardarModo(m)
    }

    /**
     * Una lectura aceptada. Con una hoja encima no cuenta (la lectura confirma con su verde, pero el visor está tapado o
     * atenuado). Devuelve si contó.
     */
    fun alLeer(normalizado: String, simbologia: Simbologia, hora: Long): Boolean {
        if (_hoja.value != null) return false
        val producto = _catalogo.value?.porLectura(normalizado, simbologia)
        when (_modo.value) {
            ModoApp.PRECIO -> _precio.value = producto?.let { ConsultaPrecio.Encontrado(it) } ?: ConsultaPrecio.NoEsta(normalizado)
            ModoApp.TICKET -> venta.update { it.sumar(normalizado, simbologia, hora, clave = producto?.clave) }
            ModoApp.CONTEO -> conteo.update { it.sumar(normalizado, simbologia, hora, clave = producto?.clave) }
        }
        return true
    }

    // ---- Precio ----

    /** «Al ticket» de la tarjeta: uno más sin salir de Precio; el aviso sale arriba y nombra el producto. */
    fun alTicket(p: Producto) {
        venta.update { it.agregarDelCatalogo(p.clave, reloj()) }
        avisoAlTicket(p)
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
            ModoApp.TICKET -> {
                venta.update { it.agregarDelCatalogo(p.clave, reloj()) }
                avisoAlTicket(p)
            }
            ModoApp.CONTEO -> conteo.update { it.agregarDelCatalogo(p.clave, reloj()) }
        }
    }

    // ---- El ticket ----

    fun masUno(id: Long) = venta.update { it.masUno(id, reloj()) }

    /** En 1, el − es el bote: quita el renglón, con «Deshacer». */
    fun menosUno(id: Long) {
        val r = venta.value.renglones.firstOrNull { it.id == id } ?: return
        venta.update { it.menosUno(id) }
        if (r.cantidad == 1) avisar("Quitado del ticket", "Deshacer")
    }

    fun conCantidad(id: Long, cantidad: Int) {
        venta.update { it.conCantidad(id, cantidad, reloj()) }
        cerrarHoja(hizoAlgo = true)
    }

    /** «Empezar otro ticket»: lo vacía; se puede deshacer. */
    fun empezarOtroTicket() {
        if (venta.value.renglones.isEmpty()) return
        venta.update { it.vaciar() }
        avisar("Ticket vacío: empieza otro", "Deshacer")
    }

    fun deshacerTicket() = venta.update { it.deshacer() }

    // ---- El conteo de prueba (el de hoy, en el celular; contra el POS llega con la entrega 6) ----

    fun menosUnoConteo() = conteo.update { it.menosUno() }
    fun deshacerConteo() = conteo.update { it.deshacer() }
    fun vaciarConteo() = conteo.update { it.vaciar() }

    // ---- Lo que tapa el visor ----

    fun abrir(h: Hoja) {
        _hoja.value = h
    }

    /** Cierra la hoja. Si la abrió el botón rápido y se cierra en menos de 2 s sin [hizoAlgo], se eligió la que no era. */
    fun cerrarHoja(hizoAlgo: Boolean = false) {
        if (_hoja.value == null) return
        _hoja.value = null
        val (lugar, desde) = abiertaPor ?: return
        abiertaPor = null
        if (!hizoAlgo && reloj() - desde < ContadoresRapido.MS_CERRADA_PRONTO) contar { it.alCerrarPronto(lugar) }
    }

    // ---- El botón rápido ----

    /** El menú se abrió (con un toque, o al pulsar para el gesto): la burbuja de la primera vez se va en ese modo. */
    fun alAbrirMenu(conToque: Boolean) {
        menuAbierto = true
        if (conToque) contar { it.alAbrirConToque() }
        val m = _modo.value
        if (m !in _pistasVistas.value) {
            _pistasVistas.value = _pistasVistas.value + m
            almacen.guardarPistasVistas(_pistasVistas.value)
        }
    }

    /** El menú se cerró: el aviso que esperaba sale ahora, con su tiempo completo. */
    fun alCerrarMenu() {
        menuAbierto = false
        avisoEnEspera?.let {
            avisoEnEspera = null
            _aviso.value = it
        }
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

    private fun avisoAlTicket(p: Producto) {
        val lista = venta.value
        val van = lista.renglones.firstOrNull { it.clave == p.clave }?.cantidad ?: 1
        val total = lista.renglones.sumOf { r -> (r.clave?.let { _catalogo.value?.porClave(it) })?.importe(r.cantidad) ?: 0L }
        val nombre = if (p.nombre.length > LARGO_NOMBRE_AVISO) p.nombre.take(LARGO_NOMBRE_AVISO - 1).trimEnd() + "…" else p.nombre
        avisar("$nombre al ticket${if (van > 1) " (van $van)" else ""} · ${lista.total} art. · ${pesos(total)}", "Deshacer", arriba = true)
    }

    private fun avisar(texto: String, accion: String? = null, arriba: Boolean = false) {
        val a = Aviso(siguienteAviso++, texto, accion, arriba)
        if (menuAbierto) avisoEnEspera = a else _aviso.value = a
    }

    /** Se acabó el tiempo del aviso [id] (si sigue siendo el que se ve). */
    fun avisoVencido(id: Long) {
        if (_aviso.value?.id == id) _aviso.value = null
    }

    /** Se tocó la acción del aviso [id]: todos los de esta pantalla deshacen el último paso del ticket. */
    fun accionDelAviso(id: Long) {
        val a = _aviso.value ?: return
        if (a.id != id) return
        _aviso.value = null
        if (a.accion == "Deshacer") deshacerTicket()
    }

    private companion object {
        /** Lo que cabe del nombre en el aviso de «Al ticket» (como el prototipo). */
        const val LARGO_NOMBRE_AVISO = 26
    }
}
