package mx.papeleriapos.android.rapido

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.estimarVisor
import mx.papeleriapos.android.fabrica.crearControlador
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.sesion.CsvAperturas
import mx.papeleriapos.android.sesion.RegistroAperturas
import mx.papeleriapos.android.sesion.aperturaMasReciente
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.MarcasApertura
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.camara.ControladorCamara
import mx.papeleriapos.escaner.nucleo.Modo

/**
 * El escaneo rápido: solo el escáner con los ajustes guardados. Lo leído suma en la lista del modo actual (la misma de
 * la app, guardada en disco), pero en pantalla solo se ve lo de esta apertura ([aqui]): puede estar sobre el bloqueo y
 * la venta completa no se le muestra a cualquiera. Sin sesión de prueba, sin ajustes y sin exportar; sus aperturas sí
 * quedan en aperturas.csv de la sesión activa (solo en archivo).
 *
 * Su controlador es propio (este ViewModel es su dueño y lo cierra en [onCleared]): si la app también está abierta en
 * otra tarea hay dos motores cargados (ML Kit con sus modelos, zxing-cpp y un hilo de análisis cada uno). No se comparte
 * uno por proceso porque el controlador sigue a un solo dueño y la pantalla de la app solo llama a `abrir` cuando cambia
 * su tamaño o su ciclo: si el escaneo rápido se lo llevara, al volver a la app la cámara quedaría ligada a una Activity
 * cerrada. El costo dura lo que dure el escaneo rápido arriba (se cierra solo, ver [msParaCerrar]).
 */
class EscaneoRapidoViewModel(app: Application) : AndroidViewModel(app) {
    // El archivo de los ajustes ya lo empezó a leer la Application en otro hilo: aquí no se espera al disco.
    private val prefs = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
    private val config = PreferenciasEscaner(prefs).leer()
    val controlador: ControladorEscaner = crearControlador(app, config)
    val modo: Modo = config.modo
    private val listaModo = ListasCompartidas.delProceso(app).de(modo)

    private val _aqui = MutableStateFlow(ListaLecturas())

    /** Lo leído en esta apertura del escaneo rápido (lo único que se muestra). */
    val aqui: StateFlow<ListaLecturas> = _aqui.asStateFlow()

    /** Minutos sin escanear para cerrarse sola (0 = nunca, salvo con el celular bloqueado). */
    val minutosParaCerrar: Int = minutosParaCerrar(prefs)

    /** La última lectura (o la apertura), en `elapsedRealtime`: cuenta también con la pantalla apagada. */
    @Volatile private var ultimaActividad = SystemClock.elapsedRealtime()

    init {
        viewModelScope.launch {
            controlador.eventos.collect { e ->
                val codigo = e.aceptada.codigo
                val hora = System.currentTimeMillis()
                listaModo.update { it.sumar(codigo.normalizado, codigo.simbologia, hora) }
                _aqui.update { it.sumar(codigo.normalizado, codigo.simbologia, hora) }
                ultimaActividad = SystemClock.elapsedRealtime()
                val apertura = aperturaMasReciente(controlador.estado.value.apertura, MarcasApertura.apertura)
                if (apertura.marcas.containsKey(PasoApertura.PRIMERA_LECTURA)) {
                    RegistroAperturas.registrar(app, CsvAperturas.ORIGEN_RAPIDO, apertura)
                }
            }
        }
    }

    fun msParaCerrar(bloqueado: Boolean): Long =
        msParaCerrar(ultimaActividad, SystemClock.elapsedRealtime(), minutosParaCerrar, bloqueado)

    /** Enlaza la cámara desde `onCreate` (el visor ocupa toda la ventana). */
    fun enlazarTemprano(actividad: ComponentActivity) {
        val c = controlador as? ControladorCamara ?: return
        c.enlazarTemprano(actividad, ControladorCamara.PANTALLA_RAPIDA) { estimarVisor(actividad, 0, pantallaCompleta = true) }
    }

    /** Pasó a segundo plano (o se cierra): la apertura en curso va a aperturas.csv aunque no haya leído nada. */
    fun alDetenerse() {
        val apertura = aperturaMasReciente(controlador.estado.value.apertura, MarcasApertura.apertura)
        RegistroAperturas.registrar(getApplication(), CsvAperturas.ORIGEN_RAPIDO, apertura)
    }

    override fun onCleared() {
        controlador.cerrar()
    }
}
