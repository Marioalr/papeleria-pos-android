package mx.papeleriapos.android.principal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mx.papeleriapos.android.BuildConfig
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.actualizar.Actualizador
import mx.papeleriapos.android.actualizar.EstadoActualizacion
import mx.papeleriapos.android.ajustes.textoConexion
import mx.papeleriapos.android.botonrapido.Accion
import mx.papeleriapos.android.botonrapido.AccionesBotonRapido
import mx.papeleriapos.android.botonrapido.BotonRapido
import mx.papeleriapos.android.botonrapido.EstadoBotonRapido
import mx.papeleriapos.android.botonrapido.EstadoOpciones
import mx.papeleriapos.android.botonrapido.MenuRapido
import mx.papeleriapos.android.botonrapido.VECES_PARA_APRENDER
import mx.papeleriapos.android.botonrapido.ZONA_DE_TOQUE
import mx.papeleriapos.android.botonrapido.huecoDelBoton
import mx.papeleriapos.android.botonrapido.nombreDelBoton
import mx.papeleriapos.android.botonrapido.opcionesDe
import mx.papeleriapos.android.botonrapido.pistaDe
import mx.papeleriapos.android.caja.Conexion
import mx.papeleriapos.android.catalogo.FuenteDeProductos
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.android.catalogo.pzs
import mx.papeleriapos.android.sesion.ALTO_BARRA_COMPLETA
import mx.papeleriapos.android.sesion.BarraSesion
import mx.papeleriapos.android.tema.LocalColoresModo
import mx.papeleriapos.android.ui.ALTO_BARRA_MODOS
import mx.papeleriapos.android.ui.CicloPropio
import mx.papeleriapos.android.ui.Destino
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.camara.franjaPara
import mx.papeleriapos.escaner.ui.LocalSoloVisor
import mx.papeleriapos.escaner.ui.PantallaEscaner

/*
 * La pantalla principal (0.5; docs/propuesta-interfaz/prototipo-0.5.html): el escáner de siempre, del mismo tamaño, con el
 * panel bajo la franja (la tarjeta de lo último y el ticket) y, abajo, «Cobrar» con el botón rápido. Precio y Ticket son
 * una sola pantalla, la venta; Conteo se abre desde el menú «Este celular», arriba a la izquierda. Nada tapa la franja.
 */

/** Entre el final de la franja y el panel, como en el prototipo. */
private val RESPIRO_FRANJA = 10.dp

@Composable
fun PantallaPrincipal(vm: EscanerViewModel, relleno: PaddingValues, alAbrir: (Destino) -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val sesion by vm.sesion.collectAsStateWithLifecycle()
    // Solo la fase: el escáner publica un estado nuevo en cada cuadro con un código a la vista (la caja) y cada 500 ms con
    // el diagnóstico. Leído entero aquí, recomponía toda la pantalla en cada cuadro (PantallaEscaner hace lo mismo).
    val estadoEscaner = vm.controlador.estado.collectAsStateWithLifecycle()
    val sinPermiso by remember(estadoEscaner) { derivedStateOf { estadoEscaner.value.fase == Fase.SIN_PERMISO } }
    val piso = vm.piso
    val modo by piso.modo.collectAsStateWithLifecycle()
    val noEsta by piso.noEsta.collectAsStateWithLifecycle()
    val ticket by vm.venta.collectAsStateWithLifecycle()
    val conteo by vm.conteo.collectAsStateWithLifecycle()
    val catalogo by piso.catalogo.collectAsStateWithLifecycle()
    val hoja by piso.hoja.collectAsStateWithLifecycle()
    val aviso by piso.aviso.collectAsStateWithLifecycle()
    val anuncio by piso.anuncio.collectAsStateWithLifecycle()
    val zurdo by piso.zurdo.collectAsStateWithLifecycle()
    val pistasVistas by piso.pistasVistas.collectAsStateWithLifecycle()
    val deslizando by piso.elegidasDeslizando.collectAsStateWithLifecycle()
    // Solo cambia cuando la caja le quita el acceso o el dueño lo ve en La conexión: no recompone nada más.
    val contextoApp = LocalContext.current.applicationContext
    val sinAccesoFlujo = remember(contextoApp) { Conexion.del(contextoApp).estado.map { it.sinAcceso }.distinctUntilChanged() }
    val sinAcceso by sinAccesoFlujo.collectAsStateWithLifecycle(initialValue = false)
    // «Mandar a una caja» se enciende con el alta y un ticket que la caja conoce.
    val conAltaFlujo = remember(contextoApp) { Conexion.del(contextoApp).estado.map { it.alta != null }.distinctUntilChanged() }
    val conAlta by conAltaFlujo.collectAsStateWithLifecycle(initialValue = false)
    // Las otras sucursales enlazadas: «¿Estás en Sucursal 2? Cambiar» cuando no contesta la caja principal.
    val otrasFlujo = remember(contextoApp) { Conexion.del(contextoApp).estado.map { it.otras }.distinctUntilChanged() }
    val otrasSucursales by otrasFlujo.collectAsStateWithLifecycle(initialValue = emptyList())
    val cambio by vm.cambioDeSucursal.estado.collectAsStateWithLifecycle()
    val dePrueba by FuenteDeProductos.dePrueba.collectAsStateWithLifecycle()
    val razonMandar = razonParaMandar(ticket, catalogo, conAlta, dePrueba)
    // Lo que dice «Cobrar» apagado: lo mismo que impide mandarlo (sin alta, los productos de prueba, un código sin producto).
    val razonCobrar = if (ticket.renglones.isEmpty()) TICKET_VACIO else razonMandar
    val soloVisor = LocalSoloVisor.current
    LaunchedEffect(soloVisor) { if (!soloVisor) vm.prepararProductos() }

    val boton = remember { EstadoBotonRapido() }
    var origenCapa by remember { mutableStateOf(Offset.Zero) }
    var panel by remember { mutableStateOf(Rect.Zero) }
    // «Enseñar» enseña el producto que estaba en la tarjeta al bajar el dedo en el botón (también con el menú ya abierto),
    // no uno leído a medio gesto; con un toque en ↑ del menú abierto o desde TalkBack, el de la tarjeta en ese momento.
    var productoAlPulsar by remember { mutableStateOf<Producto?>(null) }
    // El producto de la tarjeta: el del renglón de arriba del ticket, si la tarjeta lo enseña (no con «no está» encima).
    val deLaTarjeta = if (modo == ModoApp.VENTA && noEsta == null) ticket.actual?.let { catalogo?.de(it) } else null
    val deLaTarjetaAhora by rememberUpdatedState(deLaTarjeta)
    val zonaCerrar = remember { ZonaCerrar() }

    // ---- El menú se cierra con lo que lo deja atrás, y Piso (los avisos que esperan) siempre dice lo mismo que él ----
    val cerrarMenu: () -> Unit = remember(boton, piso) {
        {
            if (boton.abierto) boton.cerrar(marcarRebote = false)
            piso.alCerrarMenu()
        }
    }
    // Al salir de la app o apagarse la pantalla (docs/propuesta-interfaz.md, «Cómo se usa»).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { cerrarMenu() }
    // Al cambiar de modo (en Conteo no hay botón con qué cerrarlo), si algo abrió una hoja con el menú abierto o si llegó
    // un producto de fuera («Abrir la app» del escaneo rápido).
    val deFuera by piso.deFuera.collectAsStateWithLifecycle()
    LaunchedEffect(modo, hoja, deFuera) { if (boton.abierto) cerrarMenu() }
    // Un botón cerrado, también uno nuevo (la Activity se recreó con el menú abierto): Piso tampoco espera.
    LaunchedEffect(boton.abierto) { if (!boton.abierto) piso.alCerrarMenu() }
    // La pantalla sale con el menú abierto (a Ajustes con TalkBack o el teclado, o se recrea la Activity).
    DisposableEffect(piso) { onDispose { piso.alCerrarMenu() } }

    // Mientras el dedo está abajo en el botón, ↑ se enciende con lo que había al pulsar, no con lo que se lea a medio gesto.
    val opciones = opcionesDe(modo, EstadoOpciones(hayProducto = (if (boton.pulsando) productoAlPulsar else deLaTarjeta) != null))
    val colorModo = LocalColoresModo.current.de(modo)
    val accionesBoton = remember(piso) {
        AccionesBotonRapido(
            alAbrir = piso::alAbrirMenu,
            alCerrar = piso::alCerrarMenu,
            alElegir = { o, conGesto ->
                piso.alElegir(o.lugar, conGesto)
                when (o.accion) {
                    Accion.BUSCAR -> piso.abrir(Hoja.Buscar)
                    Accion.ENSENAR -> {
                        val p = if (conGesto) productoAlPulsar else deLaTarjetaAhora
                        productoAlPulsar = null
                        p?.let(piso::ensenar)
                    }
                    // Apagada (las impresiones llegan en una entrega posterior): no llega aquí.
                    Accion.IMPRESIONES -> Unit
                }
            },
            alRechazar = { o -> o.razonApagada?.let(piso::rechazar) },
            alPulsar = { productoAlPulsar = deLaTarjetaAhora },
        )
    }
    // Las mismas en cada pasada: así el panel se salta cuando no cambia lo suyo.
    val accionesPanel = remember(piso) {
        AccionesPanel(
            buscar = { piso.abrir(Hoja.Buscar) },
            verTicket = { piso.abrir(Hoja.Ticket) },
            masUno = piso::masUno,
            menosUno = piso::menosUno,
            quitar = piso::quitar,
            teclearCantidad = { piso.abrir(Hoja.Cantidad(it)) },
            subir = piso::subir,
            comun = { piso.abrir(Hoja.Comun) },
        )
    }
    val accionesCobro = remember(piso, vm) {
        AccionesCobro(
            reintentar = { piso.catalogo.value?.let { c -> vm.cobrador.abrir(vm.venta.value, c) } },
            forma = { vm.cobrador.elegirForma(it) },
            recibir = { vm.cobrador.recibir(it) },
            referencia = { vm.cobrador.referencia(it) },
            caja = { vm.cobrador.elegirCaja(it) },
            imprimir = { vm.cobrador.imprimir(it) },
            cobrar = { vm.cobrador.cobrar { piso.cobrado() } },
            mandar = { piso.abrir(Hoja.Mandar) },
        )
    }
    val alAbrirAhora by rememberUpdatedState(alAbrir)
    val accionesMenu = remember(piso, vm) {
        // Ir a otra pantalla cierra el menú: al volver, la principal está como se dejó.
        fun ir(d: Destino) {
            piso.cerrarHoja(hizoAlgo = true)
            alAbrirAhora(d)
        }
        AccionesMenu(
            contar = {
                piso.cerrarHoja(hizoAlgo = true)
                vm.cambiarModoApp(ModoApp.CONTEO)
            },
            vender = {
                piso.cerrarHoja(hizoAlgo = true)
                vm.cambiarModoApp(ModoApp.VENTA)
            },
            otroConteo = {
                piso.cerrarHoja(hizoAlgo = true)
                vm.cambiarModoApp(ModoApp.CONTEO)
                piso.empezarOtroConteo()
            },
            actualizar = { ir(Destino.ACTUALIZAR) },
            fotos = { ir(Destino.FOTOS) },
            conexion = { ir(Destino.CONEXION) },
            ajustes = { ir(Destino.AJUSTES) },
        )
    }
    val accionesTicket = remember(piso) {
        AccionesTicket(
            masUno = piso::masUno,
            menosUno = piso::menosUno,
            teclearCantidad = { piso.abrir(Hoja.Cantidad(it)) },
            buscar = { piso.abrir(Hoja.Buscar) },
            empezarOtro = {
                piso.empezarOtroTicket()
                piso.cerrarHoja(hizoAlgo = true)
            },
            deshacer = piso::deshacerTicket,
            mandar = { piso.abrir(Hoja.Mandar) },
        )
    }
    val elegirModo: (ModoApp) -> Unit = remember(vm) { { m -> vm.cambiarModoApp(m) } }

    // «Atrás» cierra lo de encima: la hoja (y regresa a la de abajo, si hay); el menú se cierra en su propia capa.
    BackHandler(enabled = hoja != null) { piso.cerrarHoja() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origenCapa = it.positionInRoot() }
            // El rebote: por 300 ms desde que el botón eligió o se cerró, solo él responde (un segundo toque por reflejo
            // confirmaría «Mandar» o «Aplicar», que quedan justo donde suelta el dedo). Con una hoja o el precio en grande
            // encima el botón no está, así que su zona tampoco se exime; la × de la hoja sí responde.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type != PointerEventType.Press) continue
                        // El dedo que bajó: con varios, el primero de la lista puede ser uno que ya estaba abajo.
                        val c = e.changes.firstOrNull { it.changedToDownIgnoreConsumed() } ?: continue
                        if (!boton.rebote.esRebote(c.uptimeMillis)) continue
                        val encima = piso.hoja.value
                        val delBoton = encima == null && boton.zona.contains(c.position)
                        val laEquis = encima != null && zonaCerrar.de(encima)?.contains(c.position + origenCapa) == true
                        if (!delBoton && !laEquis) c.consume()
                    }
                }
            },
    ) {
        val densidad = LocalDensity.current
        val arriba = relleno.calculateTopPadding()
        val abajo = relleno.calculateBottomPadding()
        val visor = maxHeight - arriba - abajo
        val franja = franjaPara(config.altoFranja)
        val inicioPanel: Dp = arriba + visor * franja.abajo + RESPIRO_FRANJA
        val libreArriba: Dp = visor * franja.arriba - 16.dp
        val menuAbierto = boton.abierto
        // Lo que tapa una hoja sale del árbol de TalkBack: solo se recorre la hoja.
        val tapado = hoja != null

        // El visor, del mismo tamaño que con la barra de pestañas: deja libre abajo el hueco de la barra. El relleno ya
        // trae la barra de estado: el escáner no la vuelve a sumar (la linterna queda a la barra + 12 dp, junto a Ajustes).
        Box(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .consumeWindowInsets(relleno)
                .sinFocoSi(menuAbierto)
                .then(if (tapado) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            // Ciclo de vida propio: la cámara se apaga al ir a Ajustes o a las herramientas.
            CicloPropio {
                PantallaEscaner(vm.controlador, Modifier.fillMaxSize()) {
                    val s = sesion
                    if (s != null) {
                        BarraSesion(
                            sesion = s,
                            marcar = vm::marcarUltimo,
                            registrarFallo = vm::registrarFallo,
                            cambiarEsperado = vm::cambiarEsperado,
                            deshacerFallo = vm::deshacerFallo,
                            compacta = libreArriba < ALTO_BARRA_COMPLETA,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(start = 80.dp, end = 80.dp, top = 8.dp)
                                .heightIn(max = libreArriba.coerceAtLeast(0.dp)),
                        )
                    }
                }
            }
        }
        if (soloVisor) return@BoxWithConstraints
        // Arriba a la izquierda, el menú «Este celular»: contar, actualizar, la conexión y Ajustes (la única entrada a las
        // herramientas), encima de lo que dibuja el escáner. Dentro de lo de encima del visor quedaba debajo de su aviso de
        // sin volumen, su diagnóstico y su error (se dibujan después) y no se veía. Sin permiso de cámara también se
        // alcanza: el banco no necesita cámara.
        BotonMenu(
            { piso.abrir(Hoja.Menu) },
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = arriba + 12.dp)
                .focusProperties { canFocus = !menuAbierto }
                .then(if (tapado) Modifier.clearAndSetSemantics { } else Modifier),
        )
        // Sin permiso de cámara el escáner pide el permiso a pantalla completa (y no dibuja lo de encima): el panel lo
        // taparía.
        if (sinPermiso) {
            // El menú se abre igual, sobre el aviso del permiso.
            if (hoja == Hoja.Menu) ElMenu(vm, piso, modo, conteo.total, catalogo, dePrueba, accionesMenu)
            return@BoxWithConstraints
        }
        // Mientras se cuenta, la pantalla lo dice arriba y ofrece volver (con la sesión de prueba, ahí va su barra).
        if (modo == ModoApp.CONTEO && sesion == null) {
            Contando(
                alVolver = { vm.cambiarModoApp(ModoApp.VENTA) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 80.dp, end = 80.dp, top = arriba + 16.dp)
                    .focusProperties { canFocus = !menuAbierto }
                    .then(if (tapado) Modifier.clearAndSetSemantics { } else Modifier),
            )
        }

        // ---- El panel del modo ----
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height((maxHeight - inicioPanel).coerceAtLeast(ALTO_BARRA_MODOS + 48.dp))
                .onGloballyPositioned { c ->
                    val p = c.positionInRoot() - origenCapa
                    panel = Rect(p, Size(c.size.width.toFloat(), c.size.height.toFloat()))
                }
                .testTag("panel")
                // Con el menú abierto el panel queda bajo el velo: sale del árbol de accesibilidad y el foco del teclado no
                // entra (como `inert` en el prototipo). Con una hoja encima, igual para TalkBack.
                .sinFocoSi(menuAbierto)
                .then(if (menuAbierto || tapado) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            Column(Modifier.fillMaxSize()) {
                Asidero(alAbrir = if (modo == ModoApp.VENTA) accionesPanel.verTicket else null)
                ContenidoPanel(
                    modo = modo,
                    noEsta = noEsta,
                    ticket = ticket,
                    conteo = conteo,
                    catalogo = catalogo,
                    acciones = accionesPanel,
                    modifier = Modifier.weight(1f),
                    tapado = tapado,
                    zurdo = zurdo,
                    sinAcceso = sinAcceso,
                )
                BarraDeAbajo(
                    modo = modo,
                    articulos = ticket.total,
                    // Mientras los productos se cargan, el total todavía no se sabe.
                    total = if (catalogo == null && ticket.renglones.isNotEmpty()) "…" else pesos(totalDe(ticket, catalogo)),
                    razonCobrar = razonCobrar,
                    contadas = conteo.total,
                    zurdo = zurdo,
                    alCobrar = { if (razonCobrar == null) piso.abrir(Hoja.Cobrar) else piso.rechazar(razonCobrar) },
                    alTerminarConteo = { vm.cambiarModoApp(ModoApp.VENTA) },
                    hueco = {
                        // El botón va en la capa de encima, sobre este hueco (arriba del velo del menú): a 16 dp de la
                        // orilla (la barra trae 8 de margen y la zona de 64 deja 4 alrededor del botón de 56).
                        Box(
                            Modifier
                                .padding(start = if (zurdo) 4.dp else 0.dp, end = if (zurdo) 0.dp else 4.dp)
                                .size(ZONA_DE_TOQUE)
                                .huecoDelBoton(boton) { origenCapa },
                        )
                    },
                )
            }
        }

        // ---- Lo que hizo cada lectura, para TalkBack (fuera del panel: con el menú abierto las lecturas siguen contando) ----
        AnuncioDeLectura(anuncio, piso, Modifier.align(Alignment.TopStart).padding(top = inicioPanel))

        // ---- La burbuja de la primera vez en cada modo, sobre el botón: solo con el panel vacío (no tapa renglones) ----
        val pista = pistaDe(modo, zurdo)
        val panelVacio = noEsta == null && when (modo) {
            ModoApp.VENTA -> ticket.renglones.isEmpty()
            ModoApp.CONTEO -> conteo.renglones.isEmpty()
        }
        if (pista != null && panelVacio && modo !in pistasVistas && !menuAbierto && aviso == null && hoja == null && boton.centro != Offset.Unspecified) {
            Text(
                pista,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(if (zurdo) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = with(densidad) { (constraints.maxHeight - boton.zona.top).toDp() } + 4.dp)
                    .widthIn(max = 300.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("pista-boton"),
            )
        }

        // ---- El menú y el botón, arriba del velo: un grupo, para TalkBack y para el teclado ----
        // Abierto, va primero y en su orden (↑, ↖, ← y la × al final) y el foco no sale de él; cerrado, el botón va al final.
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = if (menuAbierto) -1f else 1f
                }
                .focusProperties { onExit = { if (boton.abierto) cancelFocusChange() } }
                .focusGroup(),
        ) {
            MenuRapido(
                estado = boton,
                opciones = opciones,
                zurdo = zurdo,
                color = colorModo,
                panel = panel,
                pistaDeslizar = deslizando < VECES_PARA_APRENDER,
                acciones = accionesBoton,
            )
            // Con una hoja o el precio en grande encima, el botón no se compone: ni se toca a través de la hoja, ni su
            // exclusión del gesto de atrás le quita ese «atrás» a la hoja (que oprimiría lo que esté bajo el dedo).
            if (hoja == null && boton.centro != Offset.Unspecified) {
                BotonRapido(
                    estado = boton,
                    nombre = nombreDelBoton(modo),
                    opciones = opciones,
                    zurdo = zurdo,
                    color = colorModo,
                    acciones = accionesBoton,
                    modifier = Modifier.offset {
                        IntOffset(boton.zona.left.toInt(), boton.zona.top.toInt())
                    },
                )
            }
        }

        // ---- Lo que tapa el visor ----
        val h = hoja
        // El alto del aviso, medido. Con una hoja va bajo su barra (a 64 dp, la barra mide 56 o más): el ticket completo y
        // Buscar le dejan ese lugar arriba de su lista, para que no tape el − y el + del primer renglón que se ve ni los
        // resultados de arriba.
        var altoAviso by remember { mutableStateOf(0.dp) }
        if (h != null) {
            val reportarEquis = remember(h) { { r: Rect -> zonaCerrar.fijar(h, r) } }
            val huecoDelAviso = if (aviso != null) altoAviso + 8.dp else 0.dp
            CompositionLocalProvider(LocalZonaCerrarHoja provides reportarEquis) {
                when (h) {
                    Hoja.Buscar -> HojaBuscar(modo, catalogo, piso::buscar, piso::elegirDeBusqueda, huecoArriba = huecoDelAviso) {
                        piso.cerrarHoja()
                    }
                    Hoja.Ticket -> HojaTicket(ticket, catalogo, accionesTicket, huecoArriba = huecoDelAviso, razonMandar = razonMandar) { piso.cerrarHoja() }
                    Hoja.Mandar -> {
                        val estadoMandar by vm.mandador.estado.collectAsStateWithLifecycle()
                        HojaMandar(
                            estado = estadoMandar,
                            ticket = ticket,
                            catalogo = catalogo,
                            alCargar = vm.mandador::cargar,
                            alElegir = vm.mandador::elegir,
                            alMandar = { catalogo?.let { c -> vm.mandador.mandar(ticket, c, piso::mandado) } },
                            otraSucursal = {
                                BotonesOtraSucursal(otrasSucursales, cambio) { id -> vm.cambioDeSucursal.cambiar(id, piso.catalogo) { vm.mandador.cargar() } }
                            },
                        ) { piso.cerrarHoja() }
                        DisposableEffect(Unit) { onDispose { vm.cambioDeSucursal.olvidar() } }
                    }
                    Hoja.Cobrar -> {
                        val estadoCobro by vm.cobrador.estado.collectAsStateWithLifecycle()
                        // Al abrirse (y al volver de «¿A qué caja?»): lo que costaría y en qué caja entra.
                        LaunchedEffect(Unit) { piso.catalogo.value?.let { c -> vm.cobrador.abrir(vm.venta.value, c) } }
                        HojaCobrar(
                            estadoCobro, accionesCobro,
                            otraSucursal = {
                                BotonesOtraSucursal(otrasSucursales, cambio) { id -> vm.cambioDeSucursal.cambiar(id, piso.catalogo) { c -> vm.cobrador.abrir(vm.venta.value, c) } }
                            },
                        ) { piso.cerrarHoja() }
                        DisposableEffect(Unit) { onDispose { vm.cambioDeSucursal.olvidar() } }
                    }
                    Hoja.Cobrado -> {
                        val cobro by vm.cobrador.estado.collectAsStateWithLifecycle()
                        val cobrada = cobro.cobrada
                        if (cobrada == null) LaunchedEffect(Unit) { piso.cerrarHoja() } else HojaCobrado(
                            cobrada, pidioImprimir = cobro.pideTicket, impresion = cobro.impresion, imprimiendo = cobro.imprimiendo,
                            errorDeImpresion = cobro.errorDeImpresion, alImprimir = { vm.cobrador.imprimirTicket() },
                        ) { piso.cerrarHoja(hizoAlgo = true) }
                    }
                    Hoja.Menu -> ElMenu(vm, piso, modo, conteo.total, catalogo, dePrueba, accionesMenu)
                    Hoja.Comun -> HojaComun(piso::agregarComun) { piso.cerrarHoja() }
                    is Hoja.Cantidad -> {
                        // El renglón de la tarjeta: del ticket o, al contar, del conteo.
                        val r = (if (modo == ModoApp.CONTEO) conteo else ticket).renglones.firstOrNull { it.id == h.idRenglon }
                        if (r == null) {
                            LaunchedEffect(h) { piso.cerrarHoja() }
                        } else {
                            HojaCantidad(r, catalogo?.de(r), { piso.conCantidad(r.id, it) }) { piso.cerrarHoja() }
                        }
                    }
                    is Hoja.PrecioGrande -> PrecioGrande(h.producto) { piso.cerrarHoja() }
                }
            }
        }

        // ---- El aviso, encima de todo: arriba del panel (para no tapar el botón que se tocó) o abajo, sobre los modos;
        // con una hoja, bajo su barra, sin tapar su acción principal (abajo) ----
        aviso?.let { a ->
            // Con la sesión de prueba, arriba va su barra: el aviso le taparía «No leyó» y su «Deshacer» y se quedaría con
            // el toque, sin que nada lo diga. Entonces el de arriba sale abajo, sobre los modos, como los demás (bajo la barra
            // taparía la indicación del escáner), y sigue compacto: así tapa menos de la tarjeta de Precio.
            val vaArriba = a.arriba && sesion == null
            AvisoFlotante(
                aviso = a,
                maxLineas = if (a.arriba) 2 else 4,
                compacto = a.arriba && h == null,
                alVencer = { piso.avisoVencido(a.id) },
                alAccion = { piso.accionDelAviso(a.id) },
                modifier = when {
                    h != null -> Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp)
                    // Bajo Ajustes y la linterna (a la barra + 12, de 56 dp), 4 dp abajo de ellos; a lo más dos renglones
                    // compactos, para que no llegue a la franja: tapa a lo más el relleno de arriba de la indicación.
                    vaArriba -> Modifier.align(Alignment.TopCenter).padding(top = arriba + 72.dp)
                    else -> Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = ALTO_BARRA_MODOS + 8.dp)
                }.onSizeChanged { altoAviso = with(densidad) { it.height.toDp() } },
            )
        }
    }
}

/** Con [inerte], el foco del teclado no entra aquí: con el menú abierto solo se recorre el menú. */
private fun Modifier.sinFocoSi(inerte: Boolean) = focusProperties { onEnter = { if (inerte) cancelFocusChange() } }.focusGroup()

/** La × de la hoja de encima, respecto a la raíz, y de qué hoja es (la de otra no cuenta). */
private class ZonaCerrar {
    private var hoja: Hoja? = null
    private var zona = Rect.Zero

    fun fijar(h: Hoja, r: Rect) {
        hoja = h
        zona = r
    }

    fun de(h: Hoja): Rect? = if (h == hoja) zona else null
}

/**
 * Lo que hizo la última lectura, para TalkBack (docs/propuesta-interfaz.md, «Accesibilidad»): una región viva que no se
 * ve, de 1 dp, como el contador de la 0.1, que se anunciaba con cada lectura. Siempre está, así cada cambio se anuncia;
 * antes de la primera lectura no dice nada (sin descripción: no es una parada vacía al recorrer la pantalla).
 *
 * Al volver a la pantalla (de Ajustes, con un producto que llegó de fuera) aparece ya con su texto, y eso no se anuncia:
 * cuando el delegado de accesibilidad de Compose ya la registró (junta los cambios cada 100 ms), [Piso] repite lo que se
 * anunció sin ella ([Piso.alConocerseLosAnuncios]).
 */
@Composable
private fun AnuncioDeLectura(anuncio: Anuncio?, piso: Piso, modifier: Modifier = Modifier) {
    LaunchedEffect(piso) {
        delay(MS_PARA_CONOCER_EL_ANUNCIO)
        piso.alConocerseLosAnuncios()
    }
    DisposableEffect(piso) { onDispose { piso.alOcultarseLosAnuncios() } }
    // Un carácter que no se oye y cambia con cada lectura: el mismo producto otra vez en Precio se vuelve a anunciar.
    val texto = anuncio?.let { if (it.id % 2L == 0L) it.texto else it.texto + "\u200B" }
    Box(
        modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (texto != null) contentDescription = texto
            }
            .testTag("anuncio"),
    )
}

/** Lo que tarda en repetir lo que se anunció sin la región viva: más que dos vueltas del delegado de accesibilidad. */
private const val MS_PARA_CONOCER_EL_ANUNCIO = 250L

/** «Este celular», con cómo está la conexión: se lee solo mientras el menú está abierto (cambia con cada latido). */
@Composable
private fun ElMenu(vm: EscanerViewModel, piso: Piso, modo: ModoApp, contadas: Int, catalogo: mx.papeleriapos.android.catalogo.CatalogoDeProductos?, dePrueba: Boolean, acciones: AccionesMenu) {
    val contexto = LocalContext.current.applicationContext
    val conexion by remember(contexto) { Conexion.del(contexto).estado }.collectAsStateWithLifecycle()
    // Si ya se buscó y hay una versión nueva, el menú lo dice; la app nunca busca sola.
    val actualizacion by remember(contexto) { Actualizador.de(contexto).estado }.collectAsStateWithLifecycle()
    val nueva = (actualizacion as? EstadoActualizacion.Hay)?.version?.version
    HojaMenu(
        modo = modo,
        contadas = contadas,
        version = BuildConfig.VERSION_NAME + if (nueva != null) " · hay una nueva: $nueva" else "",
        conexion = textoConexion(conexion, catalogo, dePrueba),
        acciones = acciones,
    ) { piso.cerrarHoja() }
}

/** Arriba a la izquierda, el menú «Este celular»: contar, actualizar la app, la conexión y Ajustes. */
@Composable
private fun BotonMenu(alTocar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = alTocar,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
        modifier = modifier.size(56.dp).semantics { contentDescription = "Este celular: contar, actualizar y ajustes" }.testTag("boton-menu"),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Menu, contentDescription = null) }
    }
}

/** Mientras se cuenta: lo que se lee no entra al ticket. Se toca para volver a vender. */
@Composable
private fun Contando(alVolver: () -> Unit, modifier: Modifier = Modifier) {
    val color = LocalColoresModo.current.conteo
    Surface(
        onClick = alVolver,
        shape = CircleShape,
        color = color.contenedor,
        contentColor = color.enContenedor,
        modifier = modifier.heightIn(min = 48.dp).testTag("contando"),
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text("Contando · Volver a vender", style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Desde esta escala de la letra, en los 80 dp de la barra no caben los dos renglones de «Cobrar»: se queda el primero. */
private const val BARRA_UN_RENGLON = 1.6f

/**
 * Abajo, donde iban los modos (0.5): «Cobrar», con el total y cuántos artículos, y al final el hueco del botón rápido (al
 * principio, en zurdo). El objetivo de la pantalla es agregar productos y cobrar rápido: lo que más se toca va bajo el
 * pulgar. Abre el cobro (el total, cómo paga y el cambio), desde donde también se puede mandar a una caja. Apagado se ve
 * apagado y, al tocarlo, dice por qué ([razonCobrar]). Al contar, el botón termina el conteo y
 * regresa a vender; lo contado se queda guardado.
 */
@Composable
private fun BarraDeAbajo(
    modo: ModoApp,
    articulos: Int,
    total: String,
    razonCobrar: String?,
    contadas: Int,
    zurdo: Boolean,
    alCobrar: () -> Unit,
    alTerminarConteo: () -> Unit,
    hueco: @Composable () -> Unit,
) {
    val color = LocalColoresModo.current.de(modo)
    val unRenglon = LocalDensity.current.fontScale >= BARRA_UN_RENGLON
    val venta = modo == ModoApp.VENTA
    val apagado = venta && razonCobrar != null
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(ALTO_BARRA_MODOS)
            .padding(horizontal = 8.dp)
            .testTag("barra-abajo"),
        // Abajo, como iban los modos: el botón rápido queda a 4 dp de la barra de gestos, donde se aprendió el gesto.
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (zurdo) hueco()
        Column(
            Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                .heightIn(min = 56.dp)
                .background(if (apagado) MaterialTheme.colorScheme.surfaceVariant else color.color, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = if (venta) alCobrar else alTerminarConteo)
                .semantics { if (apagado) razonCobrar?.let { stateDescription = "apagado: $it" } }
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .testTag(if (venta) "cobrar" else "terminar-conteo"),
            verticalArrangement = Arrangement.Center,
        ) {
            val tinta = if (apagado) MaterialTheme.colorScheme.onSurfaceVariant else color.enColor
            Text(
                when {
                    !venta -> "Terminar conteo · ${pzs(contadas)}"
                    articulos == 0 -> "Cobrar"
                    else -> "Cobrar · $total"
                },
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = tinta,
                maxLines = 1,
                // El total nunca se corta: con la letra muy grande o un total largo, se achica lo que haga falta.
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 22.sp),
                modifier = Modifier.testTag(if (venta) "total-ticket" else "total-conteo"),
            )
            if (!unRenglon) {
                Text(
                    when {
                        !venta -> "Lo contado se queda guardado en este celular"
                        articulos == 0 -> "Ticket vacío"
                        articulos == 1 -> "1 artículo"
                        else -> "$articulos artículos"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = tinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("detalle-cobrar"),
                )
            }
        }
        if (!zurdo) hueco()
    }
}

/**
 * Un aviso con su acción; su tiempo corre solo mientras se ve (con el menú abierto se esconde y espera, en [Piso]). Dura
 * lo que pida el «Tiempo para actuar» de Accesibilidad: con TalkBack o con interruptores, 4.2 s no alcanzan para oírlo y
 * llegar a «Deshacer» (el Snackbar de Material hace lo mismo).
 */
@Composable
private fun AvisoFlotante(
    aviso: Aviso,
    maxLineas: Int,
    alVencer: () -> Unit,
    alAccion: () -> Unit,
    modifier: Modifier = Modifier,
    /** Arriba, sobre la indicación del escáner: dos renglones caben en los 48 dp de la acción. */
    compacto: Boolean = false,
) {
    val accesibilidad = LocalAccessibilityManager.current
    LaunchedEffect(aviso.id) {
        val ms = accesibilidad?.calculateRecommendedTimeoutMillis(
            aviso.ms,
            containsIcons = false,
            containsText = true,
            containsControls = aviso.accion != null,
        ) ?: aviso.ms
        delay(ms)
        alVencer()
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("aviso"),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                aviso.texto,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = if (compacto) 4.dp else 8.dp),
                maxLines = maxLineas,
                overflow = TextOverflow.Ellipsis,
            )
            aviso.accion?.let {
                TextButton(onClick = alAccion, modifier = Modifier.heightIn(min = 48.dp).testTag("aviso-accion")) {
                    Text(it, color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}
