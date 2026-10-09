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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.ajustes.PantallaAjustes
import mx.papeleriapos.android.banco.BancoViewModel
import mx.papeleriapos.android.banco.PantallaBanco
import mx.papeleriapos.android.escanear.PestanaEscanear
import mx.papeleriapos.android.sesion.PantallaSesion

enum class Pestana(val corta: String, val titulo: String, val icono: ImageVector) {
    ESCANEAR("Escanear", "Escanear", Icons.Filled.QrCodeScanner),
    SESION("Sesión", "Sesión de prueba", Icons.AutoMirrored.Filled.FactCheck),
    BANCO("Banco", "Banco de pruebas", Icons.Filled.Science),
    AJUSTES("Ajustes", "Ajustes", Icons.Filled.Settings),
}

/** La app de prueba del escáner: cuatro pestañas con la barra de abajo, sin librería de navegación. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEscaner(
    inicial: Pestana = Pestana.ESCANEAR,
    vm: EscanerViewModel = viewModel(),
    banco: BancoViewModel = viewModel(),
) {
    var pestana by rememberSaveable { mutableStateOf(inicial) }
    // Atrás desde otra pestaña regresa al escáner; desde el escáner sale de la app.
    BackHandler(enabled = pestana != Pestana.ESCANEAR) { pestana = Pestana.ESCANEAR }

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
                Pestana.AJUSTES -> PantallaAjustes(vm)
            }
        }
    }
}
