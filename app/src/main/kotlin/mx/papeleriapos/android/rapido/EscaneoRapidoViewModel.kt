package mx.papeleriapos.android.rapido

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.estimarVisor
import mx.papeleriapos.android.fabrica.crearControlador
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.android.principal.PreferenciasPiso
import mx.papeleriapos.android.sesion.CsvAperturas
import mx.papeleriapos.android.sesion.RegistroAperturas
import mx.papeleriapos.android.sesion.aperturaMasReciente
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.MarcasApertura
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.camara.ControladorCamara

/**
 * El escaneo rápido: solo el escáner con los ajustes guardados, en el modo de la app ([modo]). En Ticket y en Conteo lo
 * leído suma en la lista del modo (la misma de la app, guardada en disco), pero en pantalla solo se ve lo de esta
 * apertura ([aqui]): puede estar sobre el bloqueo y la venta completa no se le muestra a cualquiera. En Precio solo
 * enseña lo leído, con su nombre y su precio: «Escanear solo enseña. Nada entra al ticket» (docs/propuesta-interfaz.md,
 * «Precio»), y el núcleo guarda Precio como venta. Sin sesión de prueba, sin ajustes y sin exportar; sus aperturas sí
 * quedan en aperturas.csv de la sesión activa (solo en archivo).
 *
 * La Activity es singleTask y sigue viva en segundo plano (hasta que caduca): el mosaico la trae con este mismo ViewModel.
 * Por eso el modo no se lee una sola vez, sino cada vez que la pantalla vuelve ([alVolver]): mientras tanto la app pudo
 * pasar a Precio, y lo leído ahí no debe entrar al ticket.
 *
 * Su controlador es propio (este ViewModel es su dueño y lo cierra en [onCleared]): si la app también está abierta en
 * otra tarea hay dos motores cargados (ML Kit con sus modelos, zxing-cpp y un hilo de análisis cada uno). No se comparte
 * uno por proceso porque el controlador sigue a un solo dueño y la pantalla de la app solo llama a `abrir` cuando cambia
 * su tamaño o su ciclo: si el escaneo rápido se lo llevara, al volver a la app la cámara quedaría ligada a una Activity
 * cerrada. El costo dura lo que dure el escaneo rápido arriba (se cierra solo, ver [msParaCerrar]).
 */
class EscaneoRapidoViewModel(
    app: Application,
    fabrica: (Context, ConfigEscaner) -> ControladorEscaner,
    listas: () -> ListasCompartidas,
) : AndroidViewModel(app) {
    /** El que usa la app; las pruebas pasan un controlador de muestra y listas sin disco. */
    constructor(app: Application) : this(app, ::crearControlador, { ListasCompartidas.delProceso(app) })

    // El archivo de los ajustes ya lo empezó a leer la Application en otro hilo: aquí no se espera al disco.
    private val prefs = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
    private val config = PreferenciasEscaner(prefs).leer()
    val controlador: ControladorEscaner = fabrica(app, config)

    private val _modo = MutableStateFlow(modoDeLaApp())

    /**
     * El de la app al abrir o al volver ([alVolver]); el núcleo solo sabe de venta y conteo: Precio y Ticket escanean
     * como venta. Cada lectura suma en la lista de este modo (en Precio, en ninguna).
     */
    val modo: StateFlow<ModoApp> = _modo.asStateFlow()

    private val _aqui = MutableStateFlow(ListaLecturas())

    /** Lo leído en esta apertura del escaneo rápido (lo único que se muestra). */
    val aqui: StateFlow<ListaLecturas> = _aqui.asStateFlow()

    /** Los productos de prueba, para el nombre y el precio en Precio; se leen con la primera lectura, en su hilo. */
    private val productos = viewModelScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        CatalogoDeProductos.deCsv(app.assets.open(CatalogoDeProductos.ARCHIVO_DE_PRUEBA).bufferedReader().use { it.readText() })
    }
    private val _catalogo = MutableStateFlow<CatalogoDeProductos?>(null)
    val catalogo: StateFlow<CatalogoDeProductos?> = _catalogo.asStateFlow()

    /** Minutos sin escanear para cerrarse sola (0 = nunca, salvo con el celular bloqueado). */
    val minutosParaCerrar: Int = minutosParaCerrar(prefs)

    /** La última lectura (o la apertura), en `elapsedRealtime`: cuenta también con la pantalla apagada. */
    @Volatile private var ultimaActividad = SystemClock.elapsedRealtime()

    init {
        viewModelScope.launch {
            controlador.eventos.collect { e ->
                val codigo = e.aceptada.codigo
                val hora = System.currentTimeMillis()
                val m = _modo.value
                // La lista del modo de ahora; en Precio, ninguna: nada entra al ticket.
                if (m != ModoApp.PRECIO) listas().de(m.delNucleo).update { it.sumar(codigo.normalizado, codigo.simbologia, hora) }
                _aqui.update { it.sumar(codigo.normalizado, codigo.simbologia, hora) }
                ultimaActividad = SystemClock.elapsedRealtime()
                val apertura = aperturaMasReciente(controlador.estado.value.apertura, MarcasApertura.apertura)
                if (apertura.marcas.containsKey(PasoApertura.PRIMERA_LECTURA)) {
                    RegistroAperturas.registrar(app, CsvAperturas.ORIGEN_RAPIDO, apertura)
                }
                if (m == ModoApp.PRECIO && _catalogo.value == null) _catalogo.value = productos.await()
            }
        }
    }

    /** El que guardó la app (la pantalla principal lo guarda al cambiarlo); el de antes de la 0.2, por el del núcleo. */
    private fun modoDeLaApp(): ModoApp = PreferenciasPiso(prefs).leerModo() ?: ModoApp.deNucleo(PreferenciasEscaner(prefs).leer().modo)

    /**
     * La pantalla volvió: el mosaico la trajo otra vez (`onNewIntent`) o volvió a verse, p. ej. al encender la pantalla
     * (`onRestart`). Es otra apertura: lo de [aqui] empieza de cero y el modo es el de la app ahora. Si cambió, el
     * escáner toma los ajustes guardados, que ya traen el modo del núcleo que le toca.
     */
    fun alVolver() {
        _aqui.value = ListaLecturas()
        val m = modoDeLaApp()
        if (m == _modo.value) return
        _modo.value = m
        controlador.configurar(PreferenciasEscaner(prefs).leer())
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
