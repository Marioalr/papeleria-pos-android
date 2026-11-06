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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalAccessibilityManager
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import mx.papeleriapos.android.EscanerViewModel
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
import mx.papeleriapos.android.sesion.ALTO_BARRA_COMPLETA
import mx.papeleriapos.android.sesion.BarraSesion
import mx.papeleriapos.android.tema.LocalColoresModo
import mx.papeleriapos.android.ui.ALTO_BARRA_MODOS
import mx.papeleriapos.android.ui.CicloPropio
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.camara.franjaPara
import mx.papeleriapos.escaner.ui.LocalSoloVisor
import mx.papeleriapos.escaner.ui.PantallaEscaner

/*
 * La pantalla principal (docs/propuesta-interfaz.md, «La pantalla principal: el escáner con tres modos»): el escáner de
 * hoy, del mismo tamaño, con el panel del modo bajo la franja y, abajo, los modos con el botón rápido. Nada tapa la franja.
 */

/** Entre el final de la franja y el panel, como en el prototipo. */
private val RESPIRO_FRANJA = 10.dp

@Composable
fun PantallaPrincipal(vm: EscanerViewModel, relleno: PaddingValues, alAbrirAjustes: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val sesion by vm.sesion.collectAsStateWithLifecycle()
    // Solo la fase: el escáner publica un estado nuevo en cada cuadro con un código a la vista (la caja) y cada 500 ms con
    // el diagnóstico. Leído entero aquí, recomponía toda la pantalla en cada cuadro (PantallaEscaner hace lo mismo).
    val estadoEscaner = vm.controlador.estado.collectAsStateWithLifecycle()
    val sinPermiso by remember(estadoEscaner) { derivedStateOf { estadoEscaner.value.fase == Fase.SIN_PERMISO } }
    val piso = vm.piso
    val modo by piso.modo.collectAsStateWithLifecycle()
    val precio by piso.precio.collectAsStateWithLifecycle()
    val ticket by vm.venta.collectAsStateWithLifecycle()
    val conteo by vm.conteo.collectAsStateWithLifecycle()
    val catalogo by piso.catalogo.collectAsStateWithLifecycle()
    val hoja by piso.hoja.collectAsStateWithLifecycle()
    val aviso by piso.aviso.collectAsStateWithLifecycle()
    val anuncio by piso.anuncio.collectAsStateWithLifecycle()
    val zurdo by piso.zurdo.collectAsStateWithLifecycle()
    val pistasVistas by piso.pistasVistas.collectAsStateWithLifecycle()
    val deslizando by piso.elegidasDeslizando.collectAsStateWithLifecycle()
    val soloVisor = LocalSoloVisor.current
    LaunchedEffect(soloVisor) { if (!soloVisor) vm.prepararProductos() }

    val boton = remember { EstadoBotonRapido() }
    var origenCapa by remember { mutableStateOf(Offset.Zero) }
    var panel by remember { mutableStateOf(Rect.Zero) }
    // «Enseñar» enseña el producto que estaba en la tarjeta al bajar el dedo en el botón (también con el menú ya abierto),
    // no uno leído a medio gesto; con un toque en ↑ del menú abierto o desde TalkBack, el de la tarjeta en ese momento.
    var productoAlPulsar by remember { mutableStateOf<ConsultaPrecio?>(null) }
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
    // Al cambiar de modo (en Conteo no hay botón con qué cerrarlo) o si algo abrió una hoja con el menú abierto.
    LaunchedEffect(modo, hoja) { if (boton.abierto) cerrarMenu() }
    // Un botón cerrado, también uno nuevo (la Activity se recreó con el menú abierto): Piso tampoco espera.
    LaunchedEffect(boton.abierto) { if (!boton.abierto) piso.alCerrarMenu() }
    // La pantalla sale con el menú abierto (a Ajustes con TalkBack o el teclado, o se recrea la Activity).
    DisposableEffect(piso) { onDispose { piso.alCerrarMenu() } }

    // Mientras el dedo está abajo en el botón, ↑ se enciende con lo que había al pulsar, no con lo que se lea a medio gesto.
    val precioDeLasOpciones = if (boton.pulsando) productoAlPulsar else precio
    val opciones = opcionesDe(
        modo,
        EstadoOpciones(hayProducto = precioDeLasOpciones is ConsultaPrecio.Encontrado, ticketVacio = ticket.renglones.isEmpty()),
    )
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
                        val p = if (conGesto) productoAlPulsar else piso.precio.value
                        productoAlPulsar = null
                        (p as? ConsultaPrecio.Encontrado)?.let { piso.ensenar(it.producto) }
                    }
                    // Apagadas en esta entrega (sin la conexión al POS): no llegan aquí.
                    Accion.MANDAR, Accion.IMPRESIONES -> Unit
                }
            },
            alRechazar = { o -> o.razonApagada?.let(piso::rechazar) },
            alPulsar = { productoAlPulsar = piso.precio.value },
        )
    }
    // Las mismas en cada pasada: así el panel se salta cuando no cambia lo suyo.
    val accionesPanel = remember(piso) {
        AccionesPanel(
            alTicket = piso::alTicket,
            buscar = { piso.abrir(Hoja.Buscar) },
            verTicket = { piso.abrir(Hoja.Ticket) },
            masUno = piso::masUno,
            menosUno = piso::menosUno,
            teclearCantidad = { piso.abrir(Hoja.Cantidad(it)) },
            menosUnoConteo = piso::menosUnoConteo,
            deshacerConteo = piso::deshacerConteo,
            vaciarConteo = piso::vaciarConteo,
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
        // Arriba a la izquierda, Ajustes: la única entrada a las herramientas, encima de lo que dibuja el escáner. Dentro
        // de lo de encima del visor quedaba debajo de su aviso de sin volumen, su diagnóstico y su error (se dibujan
        // después) y no se veía. Sin permiso de cámara también se alcanza: el banco no necesita cámara.
        BotonAjustes(
            alAbrirAjustes,
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = arriba + 12.dp)
                .focusProperties { canFocus = !menuAbierto }
                .then(if (tapado) Modifier.clearAndSetSemantics { } else Modifier),
        )
        // Sin permiso de cámara el escáner pide el permiso a pantalla completa (y no dibuja lo de encima): el panel lo
        // taparía.
        if (sinPermiso) return@BoxWithConstraints

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
                Asidero(alAbrir = if (modo == ModoApp.TICKET) accionesPanel.verTicket else null)
                ContenidoPanel(
                    modo = modo,
                    precio = precio,
                    ticket = ticket,
                    conteo = conteo,
                    catalogo = catalogo,
                    acciones = accionesPanel,
                    modifier = Modifier.weight(1f),
                    zurdo = zurdo,
                )
                BarraModos(
                    modo = modo,
                    articulos = ticket.total,
                    zurdo = zurdo,
                    alElegir = elegirModo,
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
        AnuncioDeLectura(anuncio, Modifier.align(Alignment.TopStart).padding(top = inicioPanel))

        // ---- La burbuja de la primera vez en cada modo, sobre el botón: solo con el panel vacío (no tapa renglones) ----
        val pista = pistaDe(modo, zurdo)
        val panelVacio = when (modo) {
            ModoApp.PRECIO -> precio == null
            ModoApp.TICKET -> ticket.renglones.isEmpty()
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
        // El alto del aviso, medido. Con una hoja va bajo su barra (a 64 dp, la barra mide 56 o más): el ticket completo le
        // deja ese lugar arriba de su lista, para que no tape el − y el + del primer renglón que se ve.
        var altoAviso by remember { mutableStateOf(0.dp) }
        if (h != null) {
            val reportarEquis = remember(h) { { r: Rect -> zonaCerrar.fijar(h, r) } }
            CompositionLocalProvider(LocalZonaCerrarHoja provides reportarEquis) {
                when (h) {
                    Hoja.Buscar -> HojaBuscar(modo, catalogo, piso::buscar, piso::elegirDeBusqueda) { piso.cerrarHoja() }
                    Hoja.Ticket -> HojaTicket(
                        ticket, catalogo, accionesTicket,
                        huecoArriba = if (aviso != null) altoAviso + 8.dp else 0.dp,
                    ) { piso.cerrarHoja() }
                    is Hoja.Cantidad -> {
                        val r = ticket.renglones.firstOrNull { it.id == h.idRenglon }
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
 */
@Composable
private fun AnuncioDeLectura(anuncio: Anuncio?, modifier: Modifier = Modifier) {
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

/** Arriba a la izquierda, Ajustes (con la entrega 2, la ficha de quien entró y su menú, «Este celular»). */
@Composable
private fun BotonAjustes(alTocar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = alTocar,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
        modifier = modifier.size(56.dp).semantics { contentDescription = "Ajustes" }.testTag("boton-ajustes"),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Settings, contentDescription = null) }
    }
}

/**
 * Desde esta escala de la letra, en los 80 dp de la barra no caben el ícono, el nombre y el número del ticket encima
 * (medido en el chico: al 200 % el número salía 9 dp arriba de la barra, sobre la lista).
 */
private const val BARRA_SOLO_ICONOS = 1.6f

/**
 * Los modos: Precio · Ticket · Conteo, y al final el hueco del botón rápido (al principio, en zurdo). Se cambia tocando el
 * nombre: deslizar sobre la cámara no cambia de modo. Si hay un ticket armado, Ticket dice cuántos artículos trae, sobre su
 * icono (como las burbujas de Material: junto al nombre apretaría a los otros dos). Van abajo, con el botón a 4 dp de la
 * barra de gestos (docs/propuesta-interfaz.md, «Medidas») y los modos centrados con él.
 *
 * Con la letra muy grande, como dice la propuesta («Letra al 200 %»), los modos no elegidos se quedan con su ícono (el
 * nombre, para TalkBack) y el elegido con los dos; elegido, Ticket no lleva el número (lo dice el encabezado del panel).
 */
@Composable
private fun BarraModos(modo: ModoApp, articulos: Int, zurdo: Boolean, alElegir: (ModoApp) -> Unit, hueco: @Composable () -> Unit) {
    val colores = LocalColoresModo.current
    val soloIconos = LocalDensity.current.fontScale >= BARRA_SOLO_ICONOS
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(ALTO_BARRA_MODOS)
            .padding(horizontal = 8.dp)
            .testTag("barra-modos"),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (zurdo) hueco()
        for (m in ModoApp.entries) {
            val elegido = m == modo
            val c = colores.de(m)
            val conNombre = !soloIconos || elegido
            val conNumero = m == ModoApp.TICKET && articulos > 0 && (!soloIconos || !elegido)
            Column(
                Modifier
                    .weight(1f)
                    .padding(bottom = 4.dp)
                    .heightIn(min = 56.dp)
                    // Sin recortar: el número del ticket, sobre el ícono, crece con la letra grande y el recorte lo cortaba.
                    .background(if (elegido) c.contenedor else Color.Transparent, RoundedCornerShape(16.dp))
                    .selectable(selected = elegido, role = Role.Tab, onClick = { alElegir(m) })
                    // TalkBack: «Ticket, 2 artículos», no «2 Ticket»; sin el nombre a la vista, lo dice igual.
                    .semantics {
                        if (!conNombre) contentDescription = m.nombre
                        if (m == ModoApp.TICKET && articulos > 0) stateDescription = if (articulos == 1) "1 artículo" else "$articulos artículos"
                    }
                    .padding(vertical = 6.dp)
                    .testTag("modo-${m.name}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tinta = if (elegido) c.enContenedor else MaterialTheme.colorScheme.onSurfaceVariant
                if (conNumero) {
                    BadgedBox(badge = { Badge { Text(articulos.toString(), Modifier.testTag("numero-ticket").clearAndSetSemantics { }) } }) {
                        Icon(iconoDe(m), contentDescription = null, tint = tinta)
                    }
                } else {
                    Icon(iconoDe(m), contentDescription = null, tint = tinta)
                }
                if (conNombre) Text(m.nombre, style = MaterialTheme.typography.labelLarge, color = tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!zurdo) hueco()
    }
}

private fun iconoDe(m: ModoApp): ImageVector = when (m) {
    ModoApp.PRECIO -> Icons.Filled.Sell
    ModoApp.TICKET -> Icons.AutoMirrored.Filled.ReceiptLong
    ModoApp.CONTEO -> Icons.Filled.Inventory2
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
