package mx.papeleriapos.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

enum class Pestana(val corta: String, val titulo: String, val icono: ImageVector) {
    ESCANEAR("Escanear", "Escanear", Icons.Filled.QrCodeScanner),
    SESION("Sesión", "Sesión de prueba", Icons.AutoMirrored.Filled.FactCheck),
    BANCO("Banco", "Banco de pruebas", Icons.Filled.Science),
    ESTRES("Estrés", "Pruebas de estrés", Icons.Filled.Speed),
    AJUSTES("Ajustes", "Ajustes", Icons.Filled.Settings),
}

/** La app de prueba del escáner: cinco pestañas con la barra de abajo, sin librería de navegación. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEscaner(
    inicial: Pestana = Pestana.ESCANEAR,
    vm: EscanerViewModel = viewModel(),
    banco: BancoViewModel = viewModel(),
    estres: EstresViewModel = viewModel(),
) {
    var pestana by rememberSaveable { mutableStateOf(inicial) }
    // Atrás desde otra pestaña regresa al escáner; desde el escáner sale de la app.
    BackHandler(enabled = pestana != Pestana.ESCANEAR) { pestana = Pestana.ESCANEAR }
    MonitorDeLaSesion(vm, pestana)

    Scaffold(
        topBar = {
            if (pestana != Pestana.ESCANEAR) TopAppBar(title = { Text(pestana.titulo) })
        },
        bottomBar = {
            NavigationBar {
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
        },
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            when (pestana) {
                Pestana.ESCANEAR -> PestanaEscanear(vm)
                Pestana.SESION -> PantallaSesion(vm)
                Pestana.BANCO -> PantallaBanco(banco)
                Pestana.ESTRES -> PantallaEstres(estres)
                Pestana.AJUSTES -> PantallaAjustes(vm)
            }
        }
    }
}

/**
 * Mientras hay sesión de prueba, el monitor de recursos escribe recursos.csv cada segundo. La fase dice qué se veía:
 * la fase del escáner en la pestaña Escanear, otra pestaña o la app en segundo plano.
 */
@Composable
private fun MonitorDeLaSesion(vm: EscanerViewModel, pestana: Pestana) {
    val sesion by vm.sesion.collectAsStateWithLifecycle()
    val inicio = sesion?.inicio
    val contexto = LocalContext.current.applicationContext
    val dueno = LocalLifecycleOwner.current
    val pestanaActual by rememberUpdatedState(pestana)
    DisposableEffect(inicio) {
        if (inicio != null) {
            MonitorDeSesion.iniciar(
                contexto,
                inicio,
                fuente = { vm.controlador.estado.value.diagnostico },
                fase = {
                    when {
                        !dueno.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> "segundo_plano"
                        pestanaActual != Pestana.ESCANEAR -> pestanaActual.name.lowercase()
                        else -> vm.controlador.estado.value.fase.name.lowercase()
                    }
                },
            )
        }
        onDispose { if (inicio != null) MonitorDeSesion.detener() }
    }
}
