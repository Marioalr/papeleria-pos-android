package mx.papeleriapos.android.rapido

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.estimarVisor
import mx.papeleriapos.android.fabrica.crearControlador
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.android.sesion.CsvAperturas
import mx.papeleriapos.android.sesion.RegistroAperturas
import mx.papeleriapos.android.sesion.aperturaMasReciente
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.MarcasApertura
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.camara.ControladorCamara
import mx.papeleriapos.escaner.nucleo.Simbologia

/** Lo último que se leyó en esta apertura del escaneo rápido; [numero] sube con cada lectura (también la misma otra vez). */
data class LecturaRapida(val codigo: String, val simbologia: Simbologia, val numero: Int)

/**
 * El escaneo rápido: el verificador sobre el bloqueo (docs/propuesta-interfaz.md, «Del bolsillo al precio» y «Sobre el
 * bloqueo»). Siempre en Precio, aunque la app esté en Ticket o en Conteo: lo leído no entra a ninguna lista (no toca
 * [mx.papeleriapos.android.lista.ListasCompartidas]) y solo se enseña lo que el cliente ve en la tienda, de los productos
 * de prueba: el nombre, el precio, el mayoreo, la promoción y, de una presentación, su precio por pieza. Nunca la
 * existencia, el ticket ni el conteo: el celular puede estar bloqueado en manos de cualquiera. Lo único que se ve es lo
 * último leído en esta apertura ([leido]). Sin sesión de prueba, sin ajustes y sin exportar; sus aperturas sí quedan en
 * aperturas.csv de la sesión activa (solo en archivo).
 *
 * La Activity es singleTask y sigue viva en segundo plano (hasta que caduca): el mosaico la trae con este mismo ViewModel.
 * Antes de la 0.3 el modo se volvía a leer cada vez que volvía; ahora no cambia nunca.
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
    /** Los productos; las pruebas los dan cuando quieren (para ver lo que se enseña mientras cargan). */
    leerProductos: suspend () -> CatalogoDeProductos = { productosDePrueba(app) },
) : AndroidViewModel(app) {
    /** El que usa la app; las pruebas pasan un controlador de muestra. */
    constructor(app: Application) : this(app, ::crearControlador)

    // El archivo de los ajustes ya lo empezó a leer la Application en otro hilo: aquí no se espera al disco.
    private val prefs = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)

    /** Los ajustes guardados con el modo del núcleo de Precio (venta), sea cual sea el de la app. */
    private val config = PreferenciasEscaner(prefs).leer().copy(modo = ModoApp.PRECIO.delNucleo)
    val controlador: ControladorEscaner = fabrica(app, config)

    private val _leido = MutableStateFlow<LecturaRapida?>(null)

    /** Lo último leído en esta apertura (lo único que se enseña); null al abrir y cada vez que vuelve. */
    val leido: StateFlow<LecturaRapida?> = _leido.asStateFlow()
    private var lecturas = 0

    /** Los productos de prueba, para lo que se enseña; se leen con la primera lectura, en su hilo (no en la apertura). */
    private val productos = viewModelScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { leerProductos() }
    private val _catalogo = MutableStateFlow<CatalogoDeProductos?>(null)

    /** null mientras cargan: entonces lo leído sale con su código, sin decir todavía «No está». */
    val catalogo: StateFlow<CatalogoDeProductos?> = _catalogo.asStateFlow()

    /** Minutos sin escanear para cerrarse sola (0 = nunca, salvo con el celular bloqueado). */
    val minutosParaCerrar: Int = minutosParaCerrar(prefs)

    /** La última lectura (o la apertura), en `elapsedRealtime`: cuenta también con la pantalla apagada. */
    @Volatile private var ultimaActividad = SystemClock.elapsedRealtime()

    init {
        viewModelScope.launch {
            controlador.eventos.collect { e ->
                val codigo = e.aceptada.codigo
                _leido.value = LecturaRapida(codigo.normalizado, codigo.simbologia, ++lecturas)
                ultimaActividad = SystemClock.elapsedRealtime()
                val apertura = aperturaMasReciente(controlador.estado.value.apertura, MarcasApertura.apertura)
                if (apertura.marcas.containsKey(PasoApertura.PRIMERA_LECTURA)) {
                    RegistroAperturas.registrar(app, CsvAperturas.ORIGEN_RAPIDO, apertura)
                }
                cargarProductos()
            }
        }
    }

    private suspend fun cargarProductos(): CatalogoDeProductos =
        _catalogo.value ?: productos.await().also { _catalogo.value = it }

    /**
     * Lo que «Abrir la app» le pasa a la app ([mx.papeleriapos.android.MainActivity.EXTRA_PRODUCTO]): la clave del producto
     * que se ve o, si no está en el catálogo, el código leído; null si no se ha leído nada. Si los productos todavía no
     * llegan, los espera: con el código solo, la app no lo encontraría por sus variantes de UPC y EAN (un UPC-A leído es el
     * GTIN-13 con 0 adelante; en el catálogo puede estar con sus 12 dígitos).
     */
    suspend fun productoParaAbrir(): String? {
        val l = _leido.value ?: return null
        return cargarProductos().porLectura(l.codigo, l.simbologia)?.clave ?: l.codigo
    }

    /**
     * Volvió a verse (al encender la pantalla, o con el mosaico): lo leído antes ya no se enseña. El plazo sigue: encender
     * la pantalla no es usarla, y si no se cerrara nunca abriría la cámara en cada encendido.
     */
    fun alVolver() {
        _leido.value = null
    }

    /**
     * El mosaico la trajo otra vez: una apertura nueva, con lo leído y el plazo desde cero. Si no, volvería con el plazo de
     * la apertura anterior y se cerraría a los pocos segundos (o, ya vencido, sin abrir).
     */
    fun aperturaNueva() {
        _leido.value = null
        huboActividad()
    }

    /** Algo que cuenta como usarla aunque no se haya escaneado (cancelar el PIN de «Abrir la app»): el plazo empieza de nuevo. */
    fun huboActividad() {
        ultimaActividad = SystemClock.elapsedRealtime()
    }

    fun msParaCerrar(bloqueado: Boolean): Long =
        msParaCerrar(ultimaActividad, SystemClock.elapsedRealtime(), minutosParaCerrar, bloqueado)

    /** Enlaza la cámara desde `onCreate` (el visor ocupa toda la ventana), ligada a [dueno] (por omisión, la Activity). */
    fun enlazarTemprano(actividad: ComponentActivity, dueno: LifecycleOwner = actividad) {
        val c = controlador as? ControladorCamara ?: return
        c.enlazarTemprano(dueno, ControladorCamara.PANTALLA_RAPIDA) { estimarVisor(actividad, 0, pantallaCompleta = true) }
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

/** Los productos de prueba (assets), los mismos que lee la app. */
private fun productosDePrueba(app: Application): CatalogoDeProductos =
    CatalogoDeProductos.deCsv(app.assets.open(CatalogoDeProductos.ARCHIVO_DE_PRUEBA).bufferedReader().use { it.readText() })
