package mx.papeleriapos.android.fabrica

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.EventoLectura
import mx.papeleriapos.escaner.Fase

/**
 * Un escáner sin cámara: para las pruebas de la interfaz y para cuando falta el controlador real (entonces queda en
 * [Fase.ERROR] con [error]). [simular] emite una lectura como si la hubiera aceptado la cámara.
 */
class ControladorDeMuestra(config: ConfigEscaner = ConfigEscaner(), private val error: String? = null) : ControladorEscaner {
    private val _estado = MutableStateFlow(
        EstadoEscaner(fase = if (error != null) Fase.ERROR else Fase.ESCANEANDO, mensajeError = error),
    )
    override val estado: StateFlow<EstadoEscaner> = _estado.asStateFlow()

    private val _eventos = MutableSharedFlow<EventoLectura>(extraBufferCapacity = 16)
    override val eventos: SharedFlow<EventoLectura> = _eventos.asSharedFlow()

    override val superficie: StateFlow<SurfaceRequest?> = MutableStateFlow(null)

    private val _config = MutableStateFlow(config)
    override val config: StateFlow<ConfigEscaner> = _config.asStateFlow()

    var cerrado = false
        private set

    override fun abrir(dueno: LifecycleOwner, anchoVisor: Int, altoVisor: Int, rotacionPantalla: Int) {}

    override fun configurar(config: ConfigEscaner) {
        _config.value = config
    }

    override fun alternarLinterna() = _estado.update { it.copy(linterna = !it.linterna) }

    override fun reanudar() = _estado.update { if (error == null) it.copy(fase = Fase.ESCANEANDO) else it }

    override fun enfocar(x: Float, y: Float) {}

    override fun permisoConcedido(concedido: Boolean) {}

    override fun cerrar() {
        cerrado = true
    }

    fun simular(evento: EventoLectura) {
        _estado.update { it.copy(fase = Fase.LEIDO, ultima = evento, lecturas = it.lecturas + 1) }
        _eventos.tryEmit(evento)
    }
}
