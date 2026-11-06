package mx.papeleriapos.android.principal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.botonrapido.AccionesBotonRapido
import mx.papeleriapos.android.botonrapido.Accion
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
    val estadoEscaner by vm.controlador.estado.collectAsStateWithLifecycle()
    val piso = vm.piso
    val modo by piso.modo.collectAsStateWithLifecycle()
    val precio by piso.precio.collectAsStateWithLifecycle()
    val ticket by vm.venta.collectAsStateWithLifecycle()
    val conteo by vm.conteo.collectAsStateWithLifecycle()
    val catalogo by piso.catalogo.collectAsStateWithLifecycle()
    val hoja by piso.hoja.collectAsStateWithLifecycle()
    val aviso by piso.aviso.collectAsStateWithLifecycle()
    val zurdo by piso.zurdo.collectAsStateWithLifecycle()
    val pistasVistas by piso.pistasVistas.collectAsStateWithLifecycle()
    val deslizando by piso.elegidasDeslizando.collectAsStateWithLifecycle()
    val soloVisor = LocalSoloVisor.current
    LaunchedEffect(soloVisor) { if (!soloVisor) vm.prepararProductos() }

    val boton = remember { EstadoBotonRapido() }
    var origenCapa by remember { mutableStateOf(Offset.Zero) }
    var panel by remember { mutableStateOf(Rect.Zero) }
    // «Enseñar» enseña el producto que estaba en la tarjeta al bajar el dedo, no uno leído a medio gesto.
    var productoAlPulsar by remember { mutableStateOf<ConsultaPrecio?>(null) }

    val opciones = opcionesDe(
        modo,
        EstadoOpciones(hayProducto = precio is ConsultaPrecio.Encontrado, ticketVacio = ticket.renglones.isEmpty()),
    )
    val colorModo = LocalColoresModo.current.de(modo)
    val accionesBoton = AccionesBotonRapido(
        alAbrir = { conToque ->
            if (!conToque) productoAlPulsar = piso.precio.value
            piso.alAbrirMenu(conToque)
        },
        alCerrar = piso::alCerrarMenu,
        alElegir = { o, conGesto ->
            piso.alElegir(o.lugar, conGesto)
            when (o.accion) {
                Accion.BUSCAR -> piso.abrir(Hoja.Buscar)
                Accion.ENSENAR -> ((productoAlPulsar ?: piso.precio.value) as? ConsultaPrecio.Encontrado)?.let { piso.ensenar(it.producto) }
                // Apagadas en esta entrega (sin la conexión al POS): no llegan aquí.
                Accion.MANDAR, Accion.IMPRESIONES -> Unit
            }
        },
        alRechazar = { o -> o.razonApagada?.let(piso::rechazar) },
    )

    // «Atrás» cierra lo de encima: la hoja; el menú se cierra en su propia capa.
    BackHandler(enabled = hoja != null) { piso.cerrarHoja() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origenCapa = it.positionInRoot() }
            // El rebote: por 300 ms desde que el botón eligió o se cerró, solo él responde (un segundo toque por reflejo
            // confirmaría «Mandar» o «Aplicar», que quedan justo donde suelta el dedo).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type != PointerEventType.Press) continue
                        val c = e.changes.firstOrNull() ?: continue
                        if (boton.rebote.esRebote(c.uptimeMillis) && !boton.zona.contains(c.position)) {
                            e.changes.forEach { it.consume() }
                        }
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

        // El visor, del mismo tamaño que con la barra de pestañas: deja libre abajo el hueco de la barra.
        Box(Modifier.fillMaxSize().padding(relleno)) {
            // Ciclo de vida propio: la cámara se apaga al ir a Ajustes o a las herramientas.
            CicloPropio {
                PantallaEscaner(vm.controlador, Modifier.fillMaxSize()) {
                    // Lo de encima del visor va abajo de los controles del escáner (la linterna, el aviso de sin volumen y
                    // el diagnóstico quedan encima).
                    BotonAjustes(alAbrirAjustes, Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp))
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
        // Sin permiso de cámara el escáner pide el permiso a pantalla completa (y no dibuja lo de encima): el panel lo
        // taparía. Ajustes se sigue alcanzando: el banco no necesita cámara.
        if (estadoEscaner.fase == Fase.SIN_PERMISO) {
            BotonAjustes(alAbrirAjustes, Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp))
            return@BoxWithConstraints
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
                // Con el menú abierto, el panel de atrás sale del árbol de accesibilidad.
                .then(if (boton.abierto) Modifier.clearAndSetSemantics { } else Modifier)
                .testTag("panel"),
        ) {
            Column(Modifier.fillMaxSize()) {
                Asidero(alTocar = if (modo == ModoApp.TICKET) ({ piso.abrir(Hoja.Ticket) }) else null)
                ContenidoPanel(
                    modo = modo,
                    precio = precio,
                    ticket = ticket,
                    conteo = conteo,
                    catalogo = catalogo,
                    acciones = AccionesPanel(
                        alTicket = piso::alTicket,
                        buscar = { piso.abrir(Hoja.Buscar) },
                        verTicket = { piso.abrir(Hoja.Ticket) },
                        masUno = piso::masUno,
                        menosUno = piso::menosUno,
                        teclearCantidad = { piso.abrir(Hoja.Cantidad(it)) },
                        menosUnoConteo = piso::menosUnoConteo,
                        deshacerConteo = piso::deshacerConteo,
                        vaciarConteo = piso::vaciarConteo,
                    ),
                    modifier = Modifier.weight(1f),
                )
                BarraModos(
                    modo = modo,
                    articulos = ticket.total,
                    zurdo = zurdo,
                    alElegir = vm::cambiarModoApp,
                    hueco = {
                        // El botón va en la capa de encima, sobre este hueco (arriba del velo del menú).
                        Box(Modifier.size(ZONA_DE_TOQUE).huecoDelBoton(boton) { origenCapa })
                    },
                )
            }
        }

        // ---- El aviso: arriba del panel (para no tapar el botón que se tocó) o abajo, sobre los modos ----
        aviso?.let { a ->
            AvisoFlotante(
                aviso = a,
                alVencer = { piso.avisoVencido(a.id) },
                alAccion = { piso.accionDelAviso(a.id) },
                // Arriba: bajo los botones de arriba (Ajustes y la linterna), como en el prototipo; a lo más dos renglones,
                // para que no llegue a la franja.
                modifier = if (a.arriba) {
                    Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(top = arriba + 80.dp)
                } else {
                    Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = ALTO_BARRA_MODOS + 8.dp)
                },
            )
        }

        // ---- La burbuja de la primera vez en cada modo, sobre el botón ----
        val pista = pistaDe(modo, zurdo)
        if (pista != null && modo !in pistasVistas && !boton.abierto && aviso == null && hoja == null && boton.centro != Offset.Unspecified) {
            Text(
                pista,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 2,
                modifier = Modifier
                    .align(if (zurdo) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = with(densidad) { (constraints.maxHeight - boton.zona.top).toDp() } + 4.dp)
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("pista-boton"),
            )
        }

        // ---- El menú y el botón, arriba del velo ----
        MenuRapido(
            estado = boton,
            opciones = opciones,
            zurdo = zurdo,
            color = colorModo,
            panel = panel,
            pistaDeslizar = deslizando < VECES_PARA_APRENDER,
            acciones = accionesBoton,
        )
        if (boton.centro != Offset.Unspecified) {
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

        // ---- Lo que tapa el visor ----
        when (val h = hoja) {
            null -> Unit
            Hoja.Buscar -> HojaBuscar(modo, catalogo, piso::elegirDeBusqueda) { piso.cerrarHoja() }
            Hoja.Ticket -> HojaTicket(
                ticket,
                catalogo,
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
                ),
            ) { piso.cerrarHoja() }
            is Hoja.Cantidad -> {
                val r = ticket.renglones.firstOrNull { it.id == h.idRenglon }
                if (r == null) {
                    LaunchedEffect(h) { piso.cerrarHoja() }
                } else {
                    HojaCantidad(r, r.clave?.let { catalogo?.porClave(it) }, { piso.conCantidad(r.id, it) }) { piso.cerrarHoja() }
                }
            }
            is Hoja.PrecioGrande -> PrecioGrande(h.producto) { piso.cerrarHoja() }
        }
    }
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
 * Los modos: Precio · Ticket · Conteo, y al final el hueco del botón rápido (al principio, en zurdo). Se cambia tocando el
 * nombre: deslizar sobre la cámara no cambia de modo. Si hay un ticket armado, Ticket dice cuántos artículos trae, sobre su
 * icono (como las burbujas de Material: junto al nombre apretaría a los otros dos).
 */
@Composable
private fun BarraModos(modo: ModoApp, articulos: Int, zurdo: Boolean, alElegir: (ModoApp) -> Unit, hueco: @Composable () -> Unit) {
    val colores = LocalColoresModo.current
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(ALTO_BARRA_MODOS)
            .padding(horizontal = 8.dp)
            .testTag("barra-modos"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (zurdo) hueco()
        for (m in ModoApp.entries) {
            val elegido = m == modo
            val c = colores.de(m)
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(selected = elegido, role = Role.Tab, onClick = { alElegir(m) })
                    .background(if (elegido) c.contenedor else Color.Transparent)
                    .padding(vertical = 6.dp)
                    .testTag("modo-${m.name}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tinta = if (elegido) c.enContenedor else MaterialTheme.colorScheme.onSurfaceVariant
                if (m == ModoApp.TICKET && articulos > 0) {
                    BadgedBox(badge = { Badge { Text(articulos.toString()) } }) { Icon(iconoDe(m), contentDescription = null, tint = tinta) }
                } else {
                    Icon(iconoDe(m), contentDescription = null, tint = tinta)
                }
                Text(m.nombre, style = MaterialTheme.typography.labelLarge, color = tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Un aviso con su acción; su tiempo corre solo mientras se ve (con el menú abierto espera, en [Piso]). */
@Composable
private fun AvisoFlotante(aviso: Aviso, alVencer: () -> Unit, alAccion: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(aviso.id) {
        delay(aviso.ms)
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
            Text(aviso.texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            aviso.accion?.let {
                TextButton(onClick = alAccion, modifier = Modifier.heightIn(min = 48.dp).testTag("aviso-accion")) {
                    Text(it, color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}
