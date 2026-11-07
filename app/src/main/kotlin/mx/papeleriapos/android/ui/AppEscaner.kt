package mx.papeleriapos.android.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.ajustes.PantallaAjustes
import mx.papeleriapos.android.ajustes.PantallaAjustesEscaner
import mx.papeleriapos.android.banco.BancoViewModel
import mx.papeleriapos.android.banco.PantallaBanco
import mx.papeleriapos.android.estres.EstresViewModel
import mx.papeleriapos.android.estres.PantallaEstres
import mx.papeleriapos.android.medicion.MonitorDeSesion
import mx.papeleriapos.android.principal.PantallaPrincipal
import mx.papeleriapos.android.sesion.PantallaSesion
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.camara.ARCHIVO_APERTURA
import mx.papeleriapos.escaner.ui.LocalAlVisorListo
import mx.papeleriapos.escaner.ui.LocalSoloVisor
import java.util.Locale

/**
 * Las pantallas de la app, sin librería de navegación (docs/propuesta-interfaz.md, «Ajustes y herramientas de
 * medición»): la principal (el escáner con sus tres modos) y Ajustes, con las herramientas de medición que antes eran
 * cuatro de las cinco pestañas de abajo. Atrás sube al [padre].
 */
enum class Destino(val titulo: String, val padre: Destino?) {
    PRINCIPAL("Escanear", null),
    AJUSTES("Ajustes", PRINCIPAL),
    ESCANER("El escáner", AJUSTES),
    SESION("Sesión de prueba", AJUSTES),
    BANCO("Banco de pruebas", AJUSTES),
    ESTRES("Pruebas de estrés", AJUSTES),
    ;

    /** Para la columna fase de recursos.csv (una vez, no en cada muestra). */
    val fase: String = name.lowercase(Locale.ROOT)
}

/** El nombre de cada fase del escáner en minúsculas, calculado una vez (el monitor lo pide cada segundo). */
private val FASES = Fase.entries.map { it.name.lowercase(Locale.ROOT) }

/** El alto de la barra de los modos, donde antes iba la de las pestañas (`NavigationBar` de Material 3, sin los insets). */
val ALTO_BARRA_MODOS = 80.dp

/**
 * La app: la pantalla principal y, desde su botón de Ajustes, lo demás. [alCambiarDestino] avisa a la Activity cuál se ve
 * (para enlazar la cámara temprano al restaurar si era la principal).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEscaner(
    inicial: Destino = Destino.PRINCIPAL,
    vm: EscanerViewModel = viewModel(),
    banco: BancoViewModel = viewModel(),
    estres: EstresViewModel = viewModel(),
    alCambiarDestino: (Destino) -> Unit = {},
) {
    var destino by rememberSaveable { mutableStateOf(inicial) }
    SideEffect { alCambiarDestino(destino) }
    // «Abrir la app» con un producto (desde el escaneo rápido) regresa a la principal, también desde Ajustes o las
    // herramientas; una vez por cada uno: lo atendido se guarda con la pantalla, así que recrearla no vuelve a llevarla.
    val deFuera by vm.piso.deFuera.collectAsStateWithLifecycle()
    var atendido by rememberSaveable { mutableLongStateOf(deFuera) }
    LaunchedEffect(deFuera) {
        if (deFuera != 0L && deFuera != atendido) {
            atendido = deFuera
            destino = Destino.PRINCIPAL
        }
    }
    // Atrás sube un nivel; en la principal, lo maneja ella (cierra lo de encima) o sale de la app.
    BackHandler(enabled = destino != Destino.PRINCIPAL) { destino = destino.padre ?: Destino.PRINCIPAL }
    MonitorDeLaSesion(vm, destino)

    // En el primer cuadro solo el visor (LocalSoloVisor). Si la pantalla no es la principal no hay visor que esperar.
    val soloVisor = LocalSoloVisor.current
    if (soloVisor && destino != Destino.PRINCIPAL) {
        val alVisorListo = LocalAlVisorListo.current
        LaunchedEffect(Unit) { alVisorListo() }
    }
    Scaffold(
        topBar = {
            if (destino != Destino.PRINCIPAL && !soloVisor) {
                TopAppBar(
                    title = { Text(destino.titulo) },
                    navigationIcon = {
                        IconButton(onClick = { destino = destino.padre ?: Destino.PRINCIPAL }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                        }
                    },
                )
            }
        },
        bottomBar = { if (destino == Destino.PRINCIPAL) HuecoDeLaBarra(soloVisor) },
    ) { relleno ->
        when (destino) {
            // A pantalla completa: el visor deja libre el hueco de abajo y el panel del modo se dibuja encima de él.
            Destino.PRINCIPAL -> PantallaPrincipal(vm, relleno, alAbrirAjustes = { destino = Destino.AJUSTES })
            else -> if (!soloVisor) {
                Box(Modifier.fillMaxSize().padding(relleno)) {
                    when (destino) {
                        Destino.AJUSTES -> PantallaAjustes(vm, alAbrir = { destino = it })
                        Destino.ESCANER -> PantallaAjustesEscaner(vm)
                        Destino.SESION -> PantallaSesion(vm)
                        Destino.BANCO -> PantallaBanco(banco)
                        Destino.ESTRES -> PantallaEstres(estres)
                        Destino.PRINCIPAL -> Unit
                    }
                }
            }
        }
    }
}

/**
 * Abajo de la principal, un hueco del alto de la barra de pestañas de antes: el visor queda del mismo tamaño (la cámara
 * no se vuelve a enlazar al actualizar la app) y ahí van los modos y el botón rápido, que son parte del panel. No es una
 * Surface: no se queda con los toques, que llegan al panel de abajo.
 *
 * En el primer cuadro, con el alto que midió la vez anterior: los insets todavía no llegan y sin ellos medía 24 dp menos
 * (en el Pixel 8 Pro), así que el visor cambiaba de tamaño al llegar lo demás y la cámara se volvía a enlazar.
 */
@Composable
private fun HuecoDeLaBarra(soloVisor: Boolean) {
    val contexto = LocalContext.current
    val guardado = remember { AltoBarraPestanas.guardado(contexto) }
    val m = if (soloVisor && guardado > 0) {
        Modifier.height(with(LocalDensity.current) { guardado.toDp() })
    } else {
        (if (soloVisor) Modifier else Modifier.onSizeChanged { AltoBarraPestanas.guardar(contexto, it.height) })
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .height(ALTO_BARRA_MODOS)
    }
    Spacer(Modifier.fillMaxWidth().then(m))
}

/**
 * El alto en píxeles de la barra de abajo (antes la de las pestañas; ahora el hueco de los modos), para dibujarla del
 * mismo alto en el primer cuadro. Va en el archivo de la memoria de la apertura, que la Application empieza a leer al
 * arrancar el proceso. La llave es la de antes: así el visor no cambia de tamaño al actualizar desde la 0.1.
 */
internal object AltoBarraPestanas {
    private const val LLAVE = "barra_pestanas_px"

    fun guardado(contexto: Context): Int = prefs(contexto).getInt(LLAVE, 0)

    fun guardar(contexto: Context, px: Int) {
        val p = prefs(contexto)
        if (px > 0 && p.getInt(LLAVE, 0) != px) p.edit().putInt(LLAVE, px).apply()
    }

    private fun prefs(contexto: Context) = contexto.applicationContext.getSharedPreferences(ARCHIVO_APERTURA, Context.MODE_PRIVATE)
}

/**
 * Mientras hay sesión de prueba, el monitor de recursos escribe recursos.csv cada segundo. La fase dice qué se veía: la
 * fase del escáner en la principal, otra pantalla o la app en segundo plano. Al salir solo lo detiene si esta composición
 * sigue siendo su dueña (una pantalla nueva pudo tomarlo antes de que esta se fuera).
 */
@Composable
private fun MonitorDeLaSesion(vm: EscanerViewModel, destino: Destino) {
    val sesion by vm.sesion.collectAsStateWithLifecycle()
    val inicio = sesion?.inicio
    val contexto = LocalContext.current.applicationContext
    val dueno = LocalLifecycleOwner.current
    val destinoActual by rememberUpdatedState(destino)
    DisposableEffect(inicio) {
        val token = if (inicio == null) 0L else MonitorDeSesion.iniciar(
            contexto,
            inicio,
            fuente = { vm.controlador.estado.value.diagnostico },
            fase = {
                when {
                    !dueno.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> "segundo_plano"
                    destinoActual != Destino.PRINCIPAL -> destinoActual.fase
                    else -> FASES[vm.controlador.estado.value.fase.ordinal]
                }
            },
        )
        onDispose { if (token != 0L) MonitorDeSesion.detener(token) }
    }
}
