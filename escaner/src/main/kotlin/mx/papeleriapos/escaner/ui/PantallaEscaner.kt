package mx.papeleriapos.escaner.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import mx.papeleriapos.escaner.ControladorEscaner

/**
 * El escáner completo: visor de la cámara, retícula, guía, confirmación de lo leído (con bip y vibración), linterna y
 * pausa (docs/escaner.md, «Experiencia»). Pide el permiso de la cámara si hace falta. [encima] se dibuja sobre el visor
 * (p. ej. el último producto en modo venta o el contador en modo conteo).
 *
 * CONTRATO (implementación pendiente).
 */
@Composable
fun PantallaEscaner(
    controlador: ControladorEscaner,
    modifier: Modifier = Modifier,
    encima: @Composable BoxScope.() -> Unit = {},
) {
    TODO("Pendiente")
}
