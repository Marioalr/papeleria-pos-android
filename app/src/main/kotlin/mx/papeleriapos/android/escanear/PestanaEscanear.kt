package mx.papeleriapos.android.escanear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.sesion.BarraSesion
import mx.papeleriapos.android.sesion.ResultadoIntento
import mx.papeleriapos.android.sesion.SesionPrueba
import mx.papeleriapos.android.ui.CicloPropio
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.ui.PantallaEscaner

/** Lo que se puede hacer desde encima del visor. */
class AccionesEscaner(
    val cambiarModo: (Modo) -> Unit = {},
    val masUno: () -> Unit = {},
    val menosUno: () -> Unit = {},
    val deshacer: () -> Unit = {},
    val vaciar: () -> Unit = {},
    val marcarUltimo: (ResultadoIntento) -> Unit = {},
    val registrarFallo: (ResultadoIntento) -> Unit = {},
    val cambiarEsperado: (String) -> Unit = {},
)

/** El escáner a pantalla completa con lo leído encima. */
@Composable
fun PestanaEscanear(vm: EscanerViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val venta by vm.venta.collectAsStateWithLifecycle()
    val conteo by vm.conteo.collectAsStateWithLifecycle()
    val sesion by vm.sesion.collectAsStateWithLifecycle()
    val mantener by vm.mantenerPantalla.collectAsStateWithLifecycle()
    MantenerPantallaEncendida(mantener)
    val acciones = remember(vm) {
        AccionesEscaner(
            cambiarModo = vm::cambiarModo,
            masUno = vm::masUno,
            menosUno = vm::menosUno,
            deshacer = vm::deshacer,
            vaciar = vm::vaciar,
            marcarUltimo = vm::marcarUltimo,
            registrarFallo = vm::registrarFallo,
            cambiarEsperado = vm::cambiarEsperado,
        )
    }
    // Ciclo de vida propio: la cámara se apaga al salir de esta pestaña.
    CicloPropio {
        PantallaEscaner(vm.controlador, Modifier.fillMaxSize()) {
            EncimaDelEscaner(config.modo, if (config.modo == Modo.VENTA) venta else conteo, sesion, acciones)
        }
    }
}

/**
 * Lo que va sobre el visor: arriba la barra de la sesión de prueba (si hay) y abajo la lista del modo. El panel de abajo
 * no pasa del 31 % del alto para no tapar la franja de lectura (del 32 % al 68 %).
 */
@Composable
fun BoxScope.EncimaDelEscaner(modo: Modo, lista: ListaLecturas, sesion: SesionPrueba?, acciones: AccionesEscaner) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val altoPanel = maxHeight * 0.31f
        if (sesion != null) {
            BarraSesion(
                sesion = sesion,
                marcar = acciones.marcarUltimo,
                registrarFallo = acciones.registrarFallo,
                cambiarEsperado = acciones.cambiarEsperado,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = altoPanel)) {
            PanelLecturas(modo, lista, acciones)
        }
    }
}

/** Mientras se escanea la pantalla no se apaga sola (no se toca entre lecturas); en pausa sí. */
@Composable
private fun MantenerPantallaEncendida(activo: Boolean) {
    val vista = LocalView.current
    DisposableEffect(vista, activo) {
        vista.keepScreenOn = activo
        onDispose { vista.keepScreenOn = false }
    }
}
