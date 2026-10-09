package mx.papeleriapos.android.rapido

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.papeleriapos.android.ListasCompartidas
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.estimarVisor
import mx.papeleriapos.android.fabrica.crearControlador
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.camara.ControladorCamara
import mx.papeleriapos.escaner.nucleo.Modo

/**
 * El escaneo rápido: solo el escáner con los ajustes guardados y la lista del modo actual (la misma que ve la app, vive
 * en el proceso). Sin sesión de prueba, sin ajustes y sin exportar: puede estar sobre la pantalla de bloqueo.
 */
class EscaneoRapidoViewModel(app: Application) : AndroidViewModel(app) {
    private val config = PreferenciasEscaner(app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)).leer()
    val controlador: ControladorEscaner = crearControlador(app, config)
    val modo: Modo = config.modo
    private val listaModo = ListasCompartidas.delProceso.de(modo)
    val lista: StateFlow<ListaLecturas> = listaModo.asStateFlow()

    /** Minutos sin escanear para cerrarse sola (0 = nunca). */
    val minutosParaCerrar: Int = minutosParaCerrar(app)

    /** La última lectura (o la apertura), en `elapsedRealtime`: cuenta también con la pantalla apagada. */
    @Volatile private var ultimaActividad = SystemClock.elapsedRealtime()

    init {
        viewModelScope.launch {
            controlador.eventos.collect { e ->
                val codigo = e.aceptada.codigo
                listaModo.update { it.sumar(codigo.normalizado, codigo.simbologia, System.currentTimeMillis()) }
                ultimaActividad = SystemClock.elapsedRealtime()
            }
        }
    }

    fun msParaCerrar(): Long = msParaCerrar(ultimaActividad, SystemClock.elapsedRealtime(), minutosParaCerrar)

    /** Enlaza la cámara desde `onCreate` (el visor ocupa toda la ventana). */
    fun enlazarTemprano(actividad: ComponentActivity) {
        val c = controlador as? ControladorCamara ?: return
        c.enlazarTemprano(actividad, ControladorCamara.PANTALLA_RAPIDA) { estimarVisor(actividad, 0, pantallaCompleta = true) }
    }

    override fun onCleared() {
        controlador.cerrar()
    }
}
