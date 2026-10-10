package mx.papeleriapos.escaner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.nucleo.Barras
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Indicacion

/**
 * Una escena falsa para el visor (las vistas previas y las capturas de prueba no tienen cámara): una mesa, un producto
 * y su etiqueta con un EAN-13 de verdad en [codigo] (región visible normalizada). [luz] < 1 la oscurece.
 */
@Composable
internal fun EscenaDeMuestra(codigo: Caja?, modifier: Modifier = Modifier, luz: Float = 1f) {
    val modulos = remember { Barras.ean13("7501234567893") }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF9E8A78), Color(0xFF5D4E42))))
        if (codigo != null) {
            val l = codigo.izquierda * size.width
            val t = codigo.arriba * size.height
            val r = codigo.derecha * size.width
            val b = codigo.abajo * size.height
            val w = r - l
            val h = b - t
            // El empaque alrededor de la etiqueta.
            drawRoundRect(
                Color(0xFF1E88E5), Offset(l - w * 0.35f, t - h * 1.6f), Size(w * 1.7f, h * 4.2f), CornerRadius(w * 0.06f),
            )
            drawRect(Color.White, Offset(l, t), Size(w, h))
            // 95 módulos más 9 en blanco de cada lado.
            val m = w / (modulos.size + 18)
            for (i in modulos.indices) {
                if (modulos[i]) drawRect(Color(0xFF111111), Offset(l + (9 + i) * m, t + h * 0.08f), Size(m, h * 0.84f))
            }
        }
        if (luz < 1f) drawRect(Color.Black, alpha = 1f - luz)
    }
}

@Composable
private fun Muestra(estado: EstadoEscaner, config: ConfigEscaner = ConfigEscaner(), escena: @Composable BoxScope.() -> Unit) {
    val controlador = remember { ControladorDeMuestra(estado, config) }
    PantallaEscanerInterna(controlador, visor = escena)
}

@Preview(name = "Escaneando", widthDp = 360, heightDp = 780)
@Composable
private fun VistaEscaneando() = Muestra(EstadoEscaner(fase = Fase.ESCANEANDO)) { EscenaDeMuestra(null) }

@Preview(name = "Código a la izquierda", widthDp = 360, heightDp = 780)
@Composable
private fun VistaIzquierda() {
    val caja = Caja(0.02f, 0.45f, 0.40f, 0.55f)
    Muestra(EstadoEscaner(fase = Fase.VIENDO, indicacion = Indicacion.IZQUIERDA, cajaCodigo = caja)) { EscenaDeMuestra(caja) }
}

@Preview(name = "Leído", widthDp = 360, heightDp = 780)
@Composable
private fun VistaLeido() {
    val caja = Caja(0.22f, 0.44f, 0.78f, 0.56f)
    val controlador = remember {
        ControladorDeMuestra(EstadoEscaner(fase = Fase.LEIDO, cajaCodigo = caja)).apply { emitirLectura(caja = caja) }
    }
    PantallaEscanerInterna(controlador, visor = { EscenaDeMuestra(caja) })
}

@Preview(name = "Poca luz", widthDp = 360, heightDp = 780)
@Composable
private fun VistaPocaLuz() = Muestra(
    EstadoEscaner(fase = Fase.ESCANEANDO, indicacion = Indicacion.MAS_LUZ, hayLinterna = true, sugerirLinterna = true),
) { EscenaDeMuestra(Caja(0.3f, 0.46f, 0.7f, 0.54f), luz = 0.35f) }

@Preview(name = "En pausa", widthDp = 360, heightDp = 780)
@Composable
private fun VistaPausa() = Muestra(EstadoEscaner(fase = Fase.PAUSADO)) { EscenaDeMuestra(null) }

@Preview(name = "Error", widthDp = 360, heightDp = 780)
@Composable
private fun VistaError() = Muestra(EstadoEscaner(fase = Fase.ERROR, mensajeError = "La cámara está ocupada por otra app")) {
    EscenaDeMuestra(null)
}

@Preview(name = "Diagnóstico", widthDp = 360, heightDp = 780)
@Composable
private fun VistaDiagnostico() = Muestra(
    EstadoEscaner(fase = Fase.ESCANEANDO, zoom = 1.5f, hayLinterna = true, diagnostico = ControladorDeMuestra.DIAGNOSTICO_DE_MUESTRA),
    ConfigEscaner(mostrarDiagnostico = true),
) { EscenaDeMuestra(null) }
