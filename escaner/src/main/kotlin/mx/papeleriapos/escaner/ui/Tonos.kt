package mx.papeleriapos.escaner.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.West
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.R
import mx.papeleriapos.escaner.nucleo.Indicacion

/**
 * Colores y medidas del escáner. Cada estado se distingue además por forma, icono y texto (no solo por color), y todo
 * texto va sobre fondo sólido o sobre la capa oscura para tener contraste ≥4.5:1 aunque la cámara vea algo blanco.
 */
internal object Tonos {
    /** Capa fuera de la franja: negro al 60%. Blanco encima da ≥5.7:1 aunque la escena sea blanca. */
    val capa = Color(0x99000000)
    val reticula = Color.White
    val laser = Color(0xFFFF1744)
    /** «Ya lo vi»: las esquinas que siguen al código y la pista de dirección. */
    val ambar = Color(0xFFFFC107)
    /** «Ya lo leí». Blanco sobre este verde da 5.1:1. */
    val verde = Color(0xFF2E7D32)
    /** Retícula punteada del error: claro para que se vea sobre la capa oscura. */
    val rojoTrazo = Color(0xFFFF5252)
    /** Fondo del banner de error. Blanco encima da 6.6:1. */
    val rojoFondo = Color(0xFFB71C1C)
    /** Fondo sólido de los chips y botones. */
    val fondoChip = Color(0xFF1E1E1E)
    val texto = Color.White
    /** Lo que se ve encima de la imagen en pausa. */
    val atenuado = Color(0x8C000000)

    val radioFranja = 12.dp
    val trazoReticula = 4.dp
    val largoEsquina = 28.dp
    val trazoCodigo = 3.dp
    val largoEsquinaCodigo = 20.dp
    val trazoLaser = 2.dp
    val brilloLaser = 10.dp
    val trazoBorde = 6.dp
    /** Mínimo de los botones: se usan con prisa y a veces con guantes. */
    val boton = 56.dp
}

/** El texto de cada indicación. */
@StringRes
internal fun textoDe(indicacion: Indicacion): Int = when (indicacion) {
    Indicacion.APUNTA -> R.string.escaner_apunta
    Indicacion.LEYENDO -> R.string.escaner_leyendo
    Indicacion.YA_LEIDO -> R.string.escaner_ya_leido
    Indicacion.ACERCATE -> R.string.escaner_acercate
    Indicacion.ALEJATE -> R.string.escaner_alejate
    Indicacion.IZQUIERDA -> R.string.escaner_izquierda
    Indicacion.DERECHA -> R.string.escaner_derecha
    Indicacion.ARRIBA -> R.string.escaner_arriba
    Indicacion.ABAJO -> R.string.escaner_abajo
    Indicacion.QUIETO -> R.string.escaner_quieto
    Indicacion.ENFOCANDO -> R.string.escaner_enfocando
    Indicacion.MAS_LUZ -> R.string.escaner_mas_luz
    Indicacion.REFLEJO -> R.string.escaner_reflejo
    Indicacion.LEIDO -> R.string.escaner_leido
}

/** El icono de cada indicación: las direcciones son flechas fijas (no se reflejan en RTL: es el movimiento real). */
internal fun iconoDe(indicacion: Indicacion): ImageVector = when (indicacion) {
    Indicacion.APUNTA -> Icons.Rounded.ViewWeek
    Indicacion.LEYENDO -> Icons.Rounded.CenterFocusWeak
    Indicacion.YA_LEIDO -> Icons.Rounded.DoneAll
    Indicacion.ACERCATE -> Icons.Rounded.ZoomIn
    Indicacion.ALEJATE -> Icons.Rounded.ZoomOut
    Indicacion.IZQUIERDA -> Icons.Rounded.West
    Indicacion.DERECHA -> Icons.Rounded.East
    Indicacion.ARRIBA -> Icons.Rounded.North
    Indicacion.ABAJO -> Icons.Rounded.South
    Indicacion.QUIETO -> Icons.Rounded.PanTool
    Indicacion.ENFOCANDO -> Icons.Rounded.CenterFocusStrong
    Indicacion.MAS_LUZ -> Icons.Rounded.FlashlightOn
    Indicacion.REFLEJO -> Icons.Rounded.Flare
    Indicacion.LEIDO -> Icons.Filled.CheckCircle
}

/** Las indicaciones que piden hacer algo (icono en ámbar); las demás solo informan. */
internal fun pideAccion(indicacion: Indicacion): Boolean = when (indicacion) {
    Indicacion.APUNTA, Indicacion.LEYENDO, Indicacion.ENFOCANDO, Indicacion.LEIDO -> false
    else -> true
}

/** Lo que TalkBack dice del visor en cada fase. */
@StringRes
internal fun descripcionDe(fase: Fase): Int = when (fase) {
    Fase.SIN_PERMISO -> R.string.escaner_fase_sin_permiso
    Fase.ABRIENDO -> R.string.escaner_fase_abriendo
    Fase.ESCANEANDO -> R.string.escaner_fase_escaneando
    Fase.VIENDO -> R.string.escaner_fase_viendo
    Fase.LEIDO -> R.string.escaner_fase_leido
    Fase.PAUSADO -> R.string.escaner_fase_pausado
    Fase.ERROR -> R.string.escaner_fase_error
}

/** Etiquetas de prueba estables (docs/escaner.md, «Pruebas de la interfaz»). */
object EtiquetasEscaner {
    const val FASE = "escaner_fase"
    const val INDICACION = "escaner_indicacion"
    const val LEIDO = "escaner_leido"
    const val LINTERNA = "escaner_linterna"
    const val PAUSA = "escaner_pausa"
    const val ZOOM = "escaner_zoom"
    const val ERROR = "escaner_error"
    const val SIN_PERMISO = "escaner_sin_permiso"
    const val CARGANDO = "escaner_cargando"
    const val SIN_SONIDO = "escaner_sin_sonido"
    const val DIAGNOSTICO = "escaner_diagnostico"
}
