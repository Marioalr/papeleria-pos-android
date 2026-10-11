package mx.papeleriapos.android.escanear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.sesion.ALTO_BARRA_COMPLETA
import mx.papeleriapos.android.sesion.BarraSesion
import mx.papeleriapos.android.sesion.ResultadoIntento
import mx.papeleriapos.android.sesion.SesionPrueba
import mx.papeleriapos.android.ui.CicloPropio
import mx.papeleriapos.escaner.ConfigEscaner
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
    val deshacerFallo: () -> Unit = {},
)

/**
 * El escáner a pantalla completa con lo leído encima. La pantalla encendida mientras se escanea la maneja el propio
 * escáner (según su fase); el controlador lo cierra su dueño, el ViewModel.
 */
@Composable
fun PestanaEscanear(vm: EscanerViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val venta by vm.venta.collectAsStateWithLifecycle()
    val conteo by vm.conteo.collectAsStateWithLifecycle()
    val sesion by vm.sesion.collectAsStateWithLifecycle()
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
            deshacerFallo = vm::deshacerFallo,
        )
    }
    // Ciclo de vida propio: la cámara se apaga al salir de esta pestaña.
    CicloPropio {
        PantallaEscaner(vm.controlador, Modifier.fillMaxSize()) {
            EncimaDelEscaner(config.modo, if (config.modo == Modo.VENTA) venta else conteo, sesion, acciones, config.altoFranja)
        }
    }
}

/** Dónde empieza y dónde termina la franja de lectura, como fracción del alto del visor (la misma cuenta del escáner). */
internal fun bordesFranja(altoFranja: Float): Pair<Float, Float> {
    val a = altoFranja.coerceIn(0.1f, 1f)
    return (0.5f - a / 2f) to (0.5f + a / 2f)
}

/**
 * Lo que va sobre el visor: arriba la barra de la sesión de prueba (si hay) y abajo la lista del modo, ninguna sobre la
 * franja de lectura. La barra va completa (dos filas) si cabe arriba de la franja; si no, en una sola.
 */
@Composable
fun BoxScope.EncimaDelEscaner(
    modo: Modo,
    lista: ListaLecturas,
    sesion: SesionPrueba?,
    acciones: AccionesEscaner,
    altoFranja: Float = ConfigEscaner().altoFranja,
) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val (arriba, abajo) = bordesFranja(altoFranja)
        val libreArriba: Dp = maxHeight * arriba - MARGEN * 2
        // Abajo, lo que queda bajo la franja menos un respiro (y nunca menos de un panel usable).
        val altoPanel = maxHeight * ((1f - abajo) - 0.01f).coerceAtLeast(PANEL_MINIMO)
        if (sesion != null) {
            BarraSesion(
                sesion = sesion,
                marcar = acciones.marcarUltimo,
                registrarFallo = acciones.registrarFallo,
                cambiarEsperado = acciones.cambiarEsperado,
                deshacerFallo = acciones.deshacerFallo,
                compacta = libreArriba < ALTO_BARRA_COMPLETA,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(MARGEN).heightIn(max = libreArriba.coerceAtLeast(0.dp)),
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = altoPanel)) {
            PanelLecturas(modo, lista, acciones)
        }
    }
}

private val MARGEN = 8.dp
private const val PANEL_MINIMO = 0.18f
