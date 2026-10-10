package mx.papeleriapos.android.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.ajustes.PantallaAjustes
import mx.papeleriapos.android.banco.BancoViewModel
import mx.papeleriapos.android.banco.PantallaBanco
import mx.papeleriapos.android.escanear.PestanaEscanear
import mx.papeleriapos.android.estres.EstresViewModel
import mx.papeleriapos.android.estres.PantallaEstres
import mx.papeleriapos.android.medicion.MonitorDeSesion
import mx.papeleriapos.android.sesion.PantallaSesion
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.camara.ARCHIVO_APERTURA
import mx.papeleriapos.escaner.ui.LocalAlVisorListo
import mx.papeleriapos.escaner.ui.LocalSoloVisor
import java.util.Locale

enum class Pestana(val corta: String, val titulo: String, val icono: ImageVector) {
    ESCANEAR("Escanear", "Escanear", Icons.Filled.QrCodeScanner),
    SESION("Sesión", "Sesión de prueba", Icons.AutoMirrored.Filled.FactCheck),
    BANCO("Banco", "Banco de pruebas", Icons.Filled.Science),
    ESTRES("Estrés", "Pruebas de estrés", Icons.Filled.Speed),
    AJUSTES("Ajustes", "Ajustes", Icons.Filled.Settings),
    ;

    /** Para la columna fase de recursos.csv (una vez, no en cada muestra). */
    val fase: String = name.lowercase(Locale.ROOT)
}

/** El nombre de cada fase del escáner en minúsculas, calculado una vez (el monitor lo pide cada segundo). */
private val FASES = Fase.entries.map { it.name.lowercase(Locale.ROOT) }

/**
 * La app de prueba del escáner: cinco pestañas con la barra de abajo, sin librería de navegación. [alCambiarPestana]
 * avisa a la Activity cuál se ve (para enlazar la cámara temprano al restaurar si era la del escáner).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEscaner(
    inicial: Pestana = Pestana.ESCANEAR,
    vm: EscanerViewModel = viewModel(),
    banco: BancoViewModel = viewModel(),
    estres: EstresViewModel = viewModel(),
    alCambiarPestana: (Pestana) -> Unit = {},
) {
    var pestana by rememberSaveable { mutableStateOf(inicial) }
    SideEffect { alCambiarPestana(pestana) }
    // Atrás desde otra pestaña regresa al escáner; desde el escáner sale de la app.
    BackHandler(enabled = pestana != Pestana.ESCANEAR) { pestana = Pestana.ESCANEAR }
    MonitorDeLaSesion(vm, pestana)

    // En el primer cuadro solo el visor (LocalSoloVisor): la barra va vacía pero con el alto que midió la vez anterior,
    // para que el visor quede ya en su lugar y su tamaño. Vacía sin más mide 24 dp menos (en el Pixel 8 Pro): el visor
    // cambiaba de tamaño al llegar lo demás y la cámara se volvía a enlazar. Si la pestaña no es la del escáner no hay
    // visor que esperar.
    val soloVisor = LocalSoloVisor.current
    val contexto = LocalContext.current
    val altoBarra = remember { AltoBarraPestanas.guardado(contexto) }
    if (soloVisor && pestana != Pestana.ESCANEAR) {
        val alVisorListo = LocalAlVisorListo.current
        LaunchedEffect(Unit) { alVisorListo() }
    }
    val modificadorBarra = when {
        !soloVisor -> Modifier.onSizeChanged { AltoBarraPestanas.guardar(contexto, it.height) }
        altoBarra > 0 -> Modifier.height(with(LocalDensity.current) { altoBarra.toDp() })
        else -> Modifier
    }
    Scaffold(
        topBar = {
            if (pestana != Pestana.ESCANEAR && !soloVisor) TopAppBar(title = { Text(pestana.titulo) })
        },
        bottomBar = {
            NavigationBar(modificadorBarra) {
                if (!soloVisor) {
                    for (p in Pestana.entries) {
                        NavigationBarItem(
                            selected = p == pestana,
                            onClick = { pestana = p },
                            icon = { Icon(p.icono, contentDescription = null) },
                            label = { Text(p.corta) },
                            modifier = Modifier.testTag("pestana-${p.name}"),
                        )
                    }
                }
            }
        },
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            when {
                pestana == Pestana.ESCANEAR -> PestanaEscanear(vm)
                soloVisor -> Unit
                pestana == Pestana.SESION -> PantallaSesion(vm)
                pestana == Pestana.BANCO -> PantallaBanco(banco)
                pestana == Pestana.ESTRES -> PantallaEstres(estres)
                pestana == Pestana.AJUSTES -> PantallaAjustes(vm)
            }
        }
    }
}

/**
 * El alto en píxeles de la barra de pestañas con sus botones, para dibujarla vacía del mismo alto en el primer cuadro.
 * Va en el archivo de la memoria de la apertura, que la Application empieza a leer al arrancar el proceso.
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
 * Mientras hay sesión de prueba, el monitor de recursos escribe recursos.csv cada segundo. La fase dice qué se veía:
 * la fase del escáner en la pestaña Escanear, otra pestaña o la app en segundo plano. Al salir solo lo detiene si esta
 * composición sigue siendo su dueña (una pantalla nueva pudo tomarlo antes de que esta se fuera).
 */
@Composable
private fun MonitorDeLaSesion(vm: EscanerViewModel, pestana: Pestana) {
    val sesion by vm.sesion.collectAsStateWithLifecycle()
    val inicio = sesion?.inicio
    val contexto = LocalContext.current.applicationContext
    val dueno = LocalLifecycleOwner.current
    val pestanaActual by rememberUpdatedState(pestana)
    DisposableEffect(inicio) {
        val token = if (inicio == null) 0L else MonitorDeSesion.iniciar(
            contexto,
            inicio,
            fuente = { vm.controlador.estado.value.diagnostico },
            fase = {
                when {
                    !dueno.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> "segundo_plano"
                    pestanaActual != Pestana.ESCANEAR -> pestanaActual.fase
                    else -> FASES[vm.controlador.estado.value.fase.ordinal]
                }
            },
        )
        onDispose { if (token != 0L) MonitorDeSesion.detener(token) }
    }
}
