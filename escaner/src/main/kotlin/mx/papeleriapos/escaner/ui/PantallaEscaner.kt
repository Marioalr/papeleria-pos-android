package mx.papeleriapos.escaner.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.R

/** Lo que dura la confirmación de una lectura (retícula verde, palomita y código). */
internal const val MS_CONFIRMACION = 550L

/** Nunca más de 3 destellos por segundo (WCAG 2.3.1), aunque se lea más rápido en modo conteo. */
private const val MS_ENTRE_DESTELLOS = 334L

/**
 * El escáner completo: visor de la cámara, retícula, guía, confirmación de lo leído (con bip y vibración), linterna y
 * pausa (docs/escaner.md, «Experiencia»). Pide el permiso de la cámara si hace falta. [encima] se dibuja sobre el visor
 * (p. ej. el último producto en modo venta o el contador en modo conteo).
 *
 * Abre la cámara con el tamaño del visor y la rotación de la pantalla (`Surface.ROTATION_*`) en cuanto los tiene y la
 * vuelve a abrir si cambian. NO cierra el controlador al salir de la composición: lo cierra quien lo creó (el
 * ViewModel); la cámara se apaga sola cuando el ciclo de vida de la pantalla se detiene (p. ej. `CicloPropio` al
 * cambiar de pestaña) y al volver reabre sin volver a enlazar. Si se cerrara aquí, al volver a esta pantalla el
 * controlador del ViewModel ya no abriría y el visor quedaría negro.
 *
 * La pantalla no se apaga sola mientras se abre la cámara, se escanea o se confirma una lectura; en pausa, con error o
 * sin permiso sí (no se queda prendida sobre el bloqueo ni gasta batería).
 */
@Composable
fun PantallaEscaner(
    controlador: ControladorEscaner,
    modifier: Modifier = Modifier,
    encima: @Composable BoxScope.() -> Unit = {},
) {
    PantallaEscanerInterna(controlador, modifier, visor = { VisorCamara(controlador) }, encima = encima)
}

/**
 * true mientras se compone solo el visor, sin lo de encima: el primer cuadro de la app (`ContenidoEnDosCuadros` en
 * :app). La sesión de la cámara espera la superficie del visor; si el visor saliera junto con toda la interfaz, la
 * cámara ya abierta esperaría a que se mida y se dibuje todo (160 ms en frío en el Pixel 8 Pro, docs/plan.md,
 * experimento 1). Lo de encima llega cuando el visor avisa con [LocalAlVisorListo].
 */
val LocalSoloVisor = staticCompositionLocalOf { false }

/** Se llama cuando el visor ya entregó su superficie, o cuando no va a haber visor (sin permiso, otra pestaña). */
val LocalAlVisorListo = staticCompositionLocalOf<() -> Unit> { {} }

/** El visor de CameraX: SurfaceView (menos consumo) recortado igual que el ViewPort (FILL_CENTER). */
@Composable
private fun VisorCamara(controlador: ControladorEscaner) {
    val solicitud by controlador.superficie.collectAsStateWithLifecycle()
    DisposableEffect(controlador) { onDispose { controlador.visorRetirado() } }
    val s = solicitud ?: return
    CameraXViewfinder(
        surfaceRequest = s,
        modifier = Modifier.fillMaxSize(),
        implementationMode = ImplementationMode.EXTERNAL,
        contentScale = ContentScale.Crop,
    )
    if (LocalSoloVisor.current) {
        val listo = LocalAlVisorListo.current
        // El SurfaceView crea su superficie en el recorrido del cuadro en que se acomoda y el visor la entrega enseguida:
        // dos cuadros después CameraX ya la tiene y puede crear la sesión.
        LaunchedEffect(s) {
            withFrameNanos {}
            withFrameNanos {}
            listo()
        }
    }
}

/** [PantallaEscaner] con el visor intercambiable: las vistas previas y las capturas ponen una escena de muestra. */
@Composable
internal fun PantallaEscanerInterna(
    controlador: ControladorEscaner,
    modifier: Modifier = Modifier,
    visor: @Composable BoxScope.() -> Unit,
    encima: @Composable BoxScope.() -> Unit = {},
) {
    val contexto = LocalContext.current
    val vista = LocalView.current
    val dueno = LocalLifecycleOwner.current
    val enVistaPrevia = LocalInspectionMode.current
    val estado = controlador.estado.collectAsStateWithLifecycle()
    val config by controlador.config.collectAsStateWithLifecycle()

    // Solo lo que cambia poco se lee al componer; lo de cada cuadro (cajas, diagnóstico) se lee más abajo, al dibujar.
    val fase by remember(estado) { derivedStateOf { estado.value.fase } }
    val lecturas by remember(estado) { derivedStateOf { estado.value.lecturas } }
    val franja = remember(estado) { derivedStateOf { estado.value.franja } }

    // --- Permiso de la cámara ---
    var permiso by remember { mutableStateOf(enVistaPrevia || tienePermisoDeCamara(contexto)) }
    var negado by remember { mutableStateOf(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permiso = ok
        negado = !ok
        controlador.permisoConcedido(ok)
    }
    LaunchedEffect(Unit) {
        if (permiso) controlador.permisoConcedido(true) else lanzador.launch(Manifest.permission.CAMERA)
    }
    // Al volver de Ajustes con el permiso ya dado.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!permiso && tienePermisoDeCamara(contexto)) {
            permiso = true
            negado = false
            controlador.permisoConcedido(true)
        }
    }

    // --- Abrir la cámara (la cierra quien creó el controlador, no esta pantalla) ---
    var tamano by remember { mutableStateOf(IntSize.Zero) }
    val configuracion = LocalConfiguration.current
    val rotacion = remember(configuracion, vista) { vista.display?.rotation ?: Surface.ROTATION_0 }
    LaunchedEffect(permiso, tamano, rotacion, dueno) {
        if (permiso && tamano.width > 0 && tamano.height > 0) {
            controlador.abrir(dueno, tamano.width, tamano.height, rotacion)
        }
    }

    // Con la pantalla apagada no se escanea: se queda prendida solo mientras abre, escanea o confirma.
    val mantenerEncendida = permiso && mantenerPantallaEncendida(fase)
    DisposableEffect(vista, mantenerEncendida) {
        if (!mantenerEncendida) return@DisposableEffect onDispose {}
        vista.keepScreenOn = true
        onDispose { vista.keepScreenOn = false }
    }

    // --- Bip, vibración y aviso de volumen ---
    val retro = remember(contexto) { if (enVistaPrevia) null else Retroalimentacion(contexto.applicationContext) }
    DisposableEffect(retro) { onDispose { retro?.close() } }
    var sinVolumen by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { sinVolumen = retro?.sinVolumen() ?: false }
    DisposableEffect(retro) {
        // Los cambios de volumen se guardan en Settings.System: así el aviso se va en cuanto suben el volumen.
        val observador = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(propio: Boolean) {
                sinVolumen = retro?.sinVolumen() ?: false
            }
        }
        if (retro != null) contexto.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observador)
        onDispose { if (retro != null) contexto.contentResolver.unregisterContentObserver(observador) }
    }

    // --- Confirmación de lectura ---
    // Las lecturas que ya había al entrar no se confirman; cada una nueva se ve en el mismo cuadro en que llega
    // (hayNueva) y se sostiene MS_CONFIRMACION aunque el controlador ya haya vuelto a escanear.
    var lecturasVistas by remember { mutableLongStateOf(controlador.estado.value.lecturas) }
    var confirmando by remember { mutableStateOf(false) }
    val leido = remember {
        derivedStateOf { lecturas > lecturasVistas || confirmando || fase == Fase.LEIDO }
    }
    val destello = remember { Animatable(0f) }
    var ultimoDestello by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
    val ambito = rememberCoroutineScope()
    LaunchedEffect(lecturas) {
        if (lecturas < lecturasVistas) lecturasVistas = lecturas // El controlador empezó de cero.
        if (lecturas <= lecturasVistas) return@LaunchedEffect
        lecturasVistas = lecturas
        confirmando = true
        if (config.sonido) {
            retro?.bip()
            sinVolumen = retro?.sinVolumen() ?: false
        }
        if (config.vibracion) retro?.vibrarConfirmacion(vista)
        val ahora = SystemClock.uptimeMillis()
        if (ahora - ultimoDestello >= MS_ENTRE_DESTELLOS) {
            ultimoDestello = ahora
            // En otro ámbito: si llega otra lectura, el destello en curso termina en vez de quedarse a medias.
            ambito.launch {
                destello.snapTo(0.3f)
                destello.animateTo(0f, tween(durationMillis = 280, easing = LinearEasing))
            }
        }
        delay(MS_CONFIRMACION)
        confirmando = false
    }

    // --- Error: doble tono grave y vibración de rechazo, una vez por error ---
    LaunchedEffect(fase) {
        if (fase == Fase.ERROR) {
            if (config.sonido) retro?.error()
            if (config.vibracion) retro?.vibrarRechazo(vista)
        }
    }

    // --- «Abriendo cámara…» con spinner si tarda más de 1 s ---
    var tardando by remember { mutableStateOf(false) }
    LaunchedEffect(fase == Fase.ABRIENDO) {
        tardando = false
        if (fase == Fase.ABRIENDO) {
            delay(1000)
            tardando = true
        }
    }

    val densidad = LocalDensity.current
    val minimoArriba = WindowInsets.safeDrawing.getTop(densidad) + with(densidad) { 8.dp.roundToPx() }
    val alTocar: (Float, Float) -> Unit = remember(controlador) {
        { x, y ->
            val f = estado.value.fase
            if (f == Fase.PAUSADO || f == Fase.LEIDO) controlador.reanudar() else controlador.enfocar(x, y)
        }
    }

    val soloVisor = LocalSoloVisor.current
    val alVisorListo = LocalAlVisorListo.current
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { tamano = it },
    ) {
        if ((!permiso && negado) || fase == Fase.SIN_PERMISO) {
            if (soloVisor) LaunchedEffect(Unit) { alVisorListo() }
            VistaSinPermiso(
                alPedir = { lanzador.launch(Manifest.permission.CAMERA) },
                alAbrirAjustes = { abrirAjustes(contexto) },
            )
            return@Box
        }
        // Sin permiso todavía: la ventana del sistema está encima, no hay nada que mostrar.
        if (!permiso) {
            if (soloVisor) LaunchedEffect(Unit) { alVisorListo() }
            return@Box
        }

        visor()
        // Primero el visor solo, para que la cámara no espere a que se dibuje lo demás.
        if (soloVisor) return@Box
        CapaEscaner(estado, leido, destello = { destello.value }, alTocar = alTocar)

        if (fase == Fase.PAUSADO) CapaPausa({ franja.value }, controlador::reanudar, minimoArriba)

        if (leido.value) {
            val ultima by remember(estado) { derivedStateOf { estado.value.ultima } }
            TarjetaLeido(ultima, Modifier.enFranja({ franja.value }, LugarEnFranja.CENTRO, minimoArriba))
        } else if (fase == Fase.ABRIENDO || fase == Fase.ESCANEANDO || fase == Fase.VIENDO) {
            IndicacionActual(estado, fase, tardando, Modifier.enFranja({ franja.value }, LugarEnFranja.ENCIMA, minimoArriba))
        }

        if (fase == Fase.ERROR) {
            TacheError(Modifier.enFranja({ franja.value }, LugarEnFranja.CENTRO, minimoArriba))
        }

        encima()

        if (fase == Fase.ERROR) {
            val mensaje by remember(estado) { derivedStateOf { estado.value.mensajeError } }
            BannerError(
                mensaje ?: stringResource(R.string.escaner_error_generico),
                alReintentar = { if (tamano.width > 0) controlador.abrir(dueno, tamano.width, tamano.height, rotacion) },
                Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(12.dp),
            )
        } else {
            ControlesCamara(estado, controlador::alternarLinterna, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp))
        }

        Column(
            Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (config.sonido && sinVolumen && fase != Fase.ERROR) AvisoSinSonido()
            if (config.mostrarDiagnostico) PanelDiagnostico(estado)
        }
    }
}

/** El chip de la indicación; solo él se recompone cuando cambia la indicación. */
@Composable
private fun IndicacionActual(estado: State<EstadoEscaner>, fase: Fase, tardando: Boolean, modifier: Modifier) {
    val indicacion by remember(estado) { derivedStateOf { estado.value.indicacion } }
    if (fase == Fase.ABRIENDO) {
        ChipIndicacion(
            texto = stringResource(R.string.escaner_abriendo),
            icono = Icons.Rounded.PhotoCamera,
            acento = false,
            cargando = tardando,
            clave = R.string.escaner_abriendo,
            modifier = modifier,
        )
    } else {
        val texto = textoDe(indicacion)
        ChipIndicacion(
            texto = stringResource(texto),
            icono = iconoDe(indicacion),
            acento = pideAccion(indicacion),
            cargando = false,
            clave = texto,
            modifier = modifier,
        )
    }
}

/** Linterna (si hay) y etiqueta de zoom (si pasa de 1×), pegadas a la orilla. */
@Composable
private fun ControlesCamara(estado: State<EstadoEscaner>, alternarLinterna: () -> Unit, modifier: Modifier) {
    val hayLinterna by remember(estado) { derivedStateOf { estado.value.hayLinterna } }
    val linterna by remember(estado) { derivedStateOf { estado.value.linterna } }
    val sugerir by remember(estado) { derivedStateOf { estado.value.sugerirLinterna } }
    // Redondeado a décimas: el zoom continuo no debe recomponer en cada cuadro.
    val zoom by remember(estado) { derivedStateOf { (estado.value.zoom * 10f).toInt() / 10f } }
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hayLinterna) BotonLinterna(linterna, sugerir, alternarLinterna)
        if (zoom > 1.05f) EtiquetaZoom(zoom)
    }
}

/** Las fases en que la pantalla no se apaga sola: abrir, escanear, ver un código y confirmar la lectura. */
internal fun mantenerPantallaEncendida(fase: Fase): Boolean =
    fase == Fase.ABRIENDO || fase == Fase.ESCANEANDO || fase == Fase.VIENDO || fase == Fase.LEIDO

internal fun tienePermisoDeCamara(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun abrirAjustes(contexto: Context) {
    val intento = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { contexto.startActivity(intento) }
}
