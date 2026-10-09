package mx.papeleriapos.android

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.exportar.Compartir
import mx.papeleriapos.android.fabrica.crearControlador
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.medicion.MonitorDeSesion
import mx.papeleriapos.android.sesion.AlmacenSesion
import mx.papeleriapos.android.sesion.AnotacionesSesion
import mx.papeleriapos.android.sesion.Antirrebote
import mx.papeleriapos.android.sesion.AperturaPorIntento
import mx.papeleriapos.android.sesion.Contexto
import mx.papeleriapos.android.sesion.CsvAperturas
import mx.papeleriapos.android.sesion.CsvSesion
import mx.papeleriapos.android.sesion.EditorAnotaciones
import mx.papeleriapos.android.sesion.MedidorAndroid
import mx.papeleriapos.android.sesion.RegistroAperturas
import mx.papeleriapos.android.sesion.ResultadoIntento
import mx.papeleriapos.android.sesion.SesionPrueba
import mx.papeleriapos.android.sesion.aperturaMasReciente
import mx.papeleriapos.escaner.Apertura
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.EventoLectura
import mx.papeleriapos.escaner.MarcasApertura
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.camara.ControladorCamara
import mx.papeleriapos.escaner.camara.Proporcion
import mx.papeleriapos.escaner.camara.proporcionInicial
import mx.papeleriapos.escaner.nucleo.Modo
import kotlin.math.roundToInt

/**
 * El estado de la app de prueba: el controlador del escáner (este ViewModel es su dueño: lo cierra en [onCleared]), los
 * ajustes, las listas de venta y conteo (guardadas en disco, ver [ListasCompartidas]) y la sesión de prueba. Cada lectura
 * aceptada suma en la lista del modo actual y, si hay sesión, queda como intento.
 */
class EscanerViewModel(
    app: Application,
    fabrica: (Context, ConfigEscaner) -> ControladorEscaner,
    private val listas: ListasCompartidas = ListasCompartidas(),
) : AndroidViewModel(app) {
    /** El que usa la app (con las listas del proceso); las pruebas pasan un controlador de muestra. */
    constructor(app: Application) : this(app, ::crearControlador, ListasCompartidas.delProceso(app))

    private val preferencias = PreferenciasEscaner(app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE))
    private val _config = MutableStateFlow(preferencias.leer())
    val config: StateFlow<ConfigEscaner> = _config.asStateFlow()

    val controlador: ControladorEscaner = fabrica(app, _config.value)

    private val _venta = listas.venta
    val venta: StateFlow<ListaLecturas> = _venta.asStateFlow()
    private val _conteo = listas.conteo
    val conteo: StateFlow<ListaLecturas> = _conteo.asStateFlow()

    private val _sesion = MutableStateFlow<SesionPrueba?>(null)
    val sesion: StateFlow<SesionPrueba?> = _sesion.asStateFlow()

    val equipo = MedidorAndroid.equipo()
    private val medidor = MedidorAndroid(app)
    private val almacen = AlmacenSesion(app)
    private val aperturas = AperturaPorIntento()
    private val antirreboteFallo = Antirrebote()

    init {
        viewModelScope.launch {
            val recuperada = withContext(Dispatchers.IO) { almacen.recuperar() }
            // Una sesión que sigue tras cerrar la app: su CPU cuenta desde aquí, no desde que se creó la pantalla.
            if (recuperada != null && _sesion.value == null) medidor.reiniciarVentana()
            _sesion.update { it ?: recuperada }
            // Desde aquí cada cambio se respalda; si llegan varios juntos basta con el último.
            _sesion.drop(1).conflate().collect { s -> withContext(Dispatchers.IO) { almacen.guardar(s, equipo) } }
        }
        viewModelScope.launch { controlador.eventos.collect { alLeer(it) } }
    }

    private fun alLeer(e: EventoLectura) {
        val hora = System.currentTimeMillis()
        val codigo = e.aceptada.codigo
        listaActual().update { it.sumar(codigo.normalizado, codigo.simbologia, hora) }
        val apertura = aperturaActual()
        _sesion.update { it?.registrarLectura(e, contexto(hora, apertura)) }
        // La apertura ya está completa (sacar del bolsillo → primera lectura): a aperturas.csv si hay sesión.
        if (apertura.marcas.containsKey(PasoApertura.PRIMERA_LECTURA)) {
            RegistroAperturas.registrar(getApplication(), CsvAperturas.ORIGEN_APP, apertura)
        }
    }

    /** La apertura de este escáner, con las marcas más recientes (la primera lectura llega justo después del evento). */
    private fun aperturaActual(): Apertura = aperturaMasReciente(controlador.estado.value.apertura, MarcasApertura.apertura)

    /** La app pasó a segundo plano: la apertura en curso se registra aunque no haya leído nada. */
    fun alDetenerse() {
        RegistroAperturas.registrar(getApplication(), CsvAperturas.ORIGEN_APP, aperturaActual())
    }

    private fun listaActual() = if (_config.value.modo == Modo.VENTA) _venta else _conteo

    private fun contexto(hora: Long, apertura: Apertura = aperturaActual()): Contexto {
        val estado = controlador.estado.value
        val c = _config.value
        return Contexto(
            hora = hora,
            modo = c.modo,
            motor = c.motor,
            // El rango que de verdad eligió la cámara; si todavía no lo dice, el que se pidió.
            rangoFps = estado.diagnostico.rangoFps.ifEmpty { if (c.fpsFijo30) "30-30" else "15-30" },
            // Las columnas de la apertura solo en el primer intento de cada apertura.
            medicion = medidor.medir(aperturas.medidas(apertura)),
            zoom = estado.zoom,
            linterna = estado.linterna,
        )
    }

    // Ajustes

    fun cambiarConfig(nueva: ConfigEscaner) {
        if (nueva == _config.value) return
        _config.value = nueva
        preferencias.guardar(nueva)
        controlador.configurar(nueva)
    }

    fun cambiarModo(modo: Modo) = cambiarConfig(_config.value.copy(modo = modo))

    /**
     * Enlaza la cámara desde el `onCreate` de [actividad], antes de que haya interfaz (ver
     * [ControladorCamara.enlazarTemprano]). [descuentoAbajoDp]: lo que la pantalla pone debajo del visor (la barra de
     * pestañas), para estimar la proporción la primera vez; después se usa la que midió Compose.
     */
    fun enlazarTemprano(actividad: ComponentActivity, pantalla: String, descuentoAbajoDp: Int, pantallaCompleta: Boolean = false) {
        val c = controlador as? ControladorCamara ?: return
        c.enlazarTemprano(actividad, pantalla) { estimarVisor(actividad, descuentoAbajoDp, pantallaCompleta) }
    }

    fun restablecerAjustes() = cambiarConfig(ConfigEscaner().copy(modo = _config.value.modo))

    // Lista de venta o conteo (la del modo actual)

    fun masUno() = listaActual().update { it.masUno(System.currentTimeMillis()) }
    fun menosUno() = listaActual().update { it.menosUno() }
    fun deshacer() = listaActual().update { it.deshacer() }

    /** «Nueva venta» (o «Nuevo conteo»): vacía la lista del modo; se puede deshacer. */
    fun vaciar() = listaActual().update { it.vaciar() }

    // Sesión de prueba

    fun iniciarSesion(nombre: String) {
        if (_sesion.value != null) return
        val app = getApplication<Application>()
        // La CPU del primer intento cuenta desde aquí; su apertura va en él; aperturas.csv empieza vacío.
        medidor.reiniciarVentana()
        aperturas.olvidar()
        RegistroAperturas.borrar(app)
        _sesion.value = SesionPrueba(nombre.trim().ifEmpty { equipo.dispositivo }, System.currentTimeMillis())
    }

    /** Termina la sesión: sus archivos (intentos, recursos y aperturas) se borran; ya se exportaron o se descartaron. */
    fun terminarSesion() {
        val s = _sesion.value ?: return
        val app = getApplication<Application>()
        _sesion.value = null
        MonitorDeSesion.terminar(s.inicio)
        RegistroAperturas.borrar(app)
        viewModelScope.launch(Dispatchers.IO) { MonitorDeSesion.borrar(app, s.inicio) }
    }

    fun cambiarEsperado(codigo: String) = _sesion.update { it?.conEsperado(codigo) }

    fun marcarUltimo(resultado: ResultadoIntento) = _sesion.update { it?.marcarUltimo(resultado) }

    /** Un intento que no leyó o se canceló; un segundo toque a menos de 1 s del anterior se ignora (doble toque). */
    fun registrarFallo(resultado: ResultadoIntento) {
        if (_sesion.value == null || !antirreboteFallo.permite(SystemClock.elapsedRealtime())) return
        val hora = System.currentTimeMillis()
        _sesion.update { it?.registrarFallo(resultado, contexto(hora)) }
    }

    /** Deshace el último intento si fue un fallo registrado a mano (con su nota, si tenía). */
    fun deshacerFallo() {
        val s = _sesion.value ?: return
        val u = s.ultimo ?: return
        if (!s.puedeDeshacerFallo) return
        _sesion.value = s.quitarUltimoFallo()
        // Si llevaba las columnas de la apertura, las lleva el siguiente; su nota no pasa al que tome su número.
        if (u.medicion.apertura.tipo.isNotEmpty()) aperturas.olvidar()
        AnotacionesSesion.cambiar(s) { it.conNota(u.n, "") }
        EditorAnotaciones.guardar(getApplication())
        antirreboteFallo.olvidar()
    }

    /** Escribe el CSV de la sesión en la caché y devuelve su URI para compartirlo. */
    suspend fun csvSesion(): Uri? {
        val s = _sesion.value ?: return null
        return withContext(Dispatchers.IO) {
            Compartir.escribir(getApplication(), CsvSesion.nombreArchivo("sesion", s.clave()), CsvSesion.escribir(s, equipo))
        }
    }

    override fun onCleared() {
        controlador.cerrar()
    }
}

/**
 * La proporción del visor antes del primer layout: la ventana menos las barras del sistema y [descuentoAbajoDp]; con
 * [pantallaCompleta], la ventana entera (el visor va detrás de las barras).
 */
internal fun estimarVisor(actividad: Activity, descuentoAbajoDp: Int, pantallaCompleta: Boolean): Proporcion? {
    val densidad = actividad.resources.displayMetrics.density
    val descuento = (descuentoAbajoDp * densidad).roundToInt()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val m = actividad.windowManager.currentWindowMetrics
        val ancho = m.bounds.width()
        val alto = m.bounds.height()
        if (pantallaCompleta) return proporcionInicial(null, ancho, alto, 0, 0)
        val barras = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        return proporcionInicial(null, ancho, alto, barras.top, barras.bottom, descuento)
    }
    // API 24–29: el tamaño real de la pantalla y las barras de los recursos del sistema.
    val tamano = android.graphics.Point()
    @Suppress("DEPRECATION")
    actividad.windowManager.defaultDisplay.getRealSize(tamano)
    if (pantallaCompleta) return proporcionInicial(null, tamano.x, tamano.y, 0, 0)
    return proporcionInicial(
        null, tamano.x, tamano.y, dimensionDelSistema(actividad, "status_bar_height"),
        dimensionDelSistema(actividad, "navigation_bar_height"), descuento,
    )
}

@android.annotation.SuppressLint("DiscouragedApi", "InternalInsetResource")
private fun dimensionDelSistema(actividad: Activity, nombre: String): Int {
    val id = actividad.resources.getIdentifier(nombre, "dimen", "android")
    return if (id > 0) actividad.resources.getDimensionPixelSize(id) else 0
}
