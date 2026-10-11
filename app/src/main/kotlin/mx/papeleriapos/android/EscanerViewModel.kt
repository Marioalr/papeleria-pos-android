package mx.papeleriapos.android

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.exportar.Compartir
import mx.papeleriapos.android.fabrica.crearControlador
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.sesion.AlmacenSesion
import mx.papeleriapos.android.sesion.Contexto
import mx.papeleriapos.android.sesion.CsvSesion
import mx.papeleriapos.android.sesion.MedidorAndroid
import mx.papeleriapos.android.sesion.ResultadoIntento
import mx.papeleriapos.android.sesion.SesionPrueba
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.EventoLectura
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.nucleo.Modo

/**
 * El estado de la app de prueba: el controlador del escáner (uno por proceso de pantalla), los ajustes, las listas de
 * venta y conteo, y la sesión de prueba. Cada lectura aceptada suma en la lista del modo actual y, si hay sesión, queda
 * como intento.
 */
class EscanerViewModel(
    app: Application,
    fabrica: (Context, ConfigEscaner) -> ControladorEscaner,
) : AndroidViewModel(app) {
    /** El que usa la app; las pruebas pasan un controlador de muestra. */
    constructor(app: Application) : this(app, ::crearControlador)

    private val preferencias = PreferenciasEscaner(app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE))
    private val _config = MutableStateFlow(preferencias.leer())
    val config: StateFlow<ConfigEscaner> = _config.asStateFlow()

    val controlador: ControladorEscaner = fabrica(app, _config.value)

    private val _venta = MutableStateFlow(ListaLecturas())
    val venta: StateFlow<ListaLecturas> = _venta.asStateFlow()
    private val _conteo = MutableStateFlow(ListaLecturas())
    val conteo: StateFlow<ListaLecturas> = _conteo.asStateFlow()

    private val _sesion = MutableStateFlow<SesionPrueba?>(null)
    val sesion: StateFlow<SesionPrueba?> = _sesion.asStateFlow()

    val equipo = MedidorAndroid.equipo()
    private val medidor = MedidorAndroid(app)
    private val almacen = AlmacenSesion(app)

    /** La pantalla se queda encendida mientras se escanea (sin tocarla se apagaría a los 30 s), pero no en pausa. */
    val mantenerPantalla: StateFlow<Boolean> = controlador.estado
        .map { it.fase == Fase.ABRIENDO || it.fase == Fase.ESCANEANDO || it.fase == Fase.VIENDO || it.fase == Fase.LEIDO }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            _sesion.value = withContext(Dispatchers.IO) { almacen.recuperar() }
            // Desde aquí cada cambio se respalda; si llegan varios juntos basta con el último.
            _sesion.drop(1).conflate().collect { s -> withContext(Dispatchers.IO) { almacen.guardar(s, equipo) } }
        }
        viewModelScope.launch { controlador.eventos.collect { alLeer(it) } }
    }

    private fun alLeer(e: EventoLectura) {
        val hora = System.currentTimeMillis()
        val codigo = e.aceptada.codigo
        listaActual().update { it.sumar(codigo.normalizado, codigo.simbologia, hora) }
        _sesion.update { it?.registrarLectura(e, contexto(hora)) }
    }

    private fun listaActual() = if (_config.value.modo == Modo.VENTA) _venta else _conteo

    private fun contexto(hora: Long): Contexto {
        val estado = controlador.estado.value
        val c = _config.value
        return Contexto(
            hora = hora,
            modo = c.modo,
            motor = c.motor,
            // El rango que de verdad eligió la cámara; si todavía no lo dice, el que se pidió.
            rangoFps = estado.diagnostico.rangoFps.ifEmpty { if (c.fpsFijo30) "30-30" else "15-30" },
            medicion = medidor.medir(),
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

    fun restablecerAjustes() = cambiarConfig(ConfigEscaner().copy(modo = _config.value.modo))

    // Lista de venta o conteo (la del modo actual)

    fun masUno() = listaActual().update { it.masUno(System.currentTimeMillis()) }
    fun menosUno() = listaActual().update { it.menosUno() }
    fun deshacer() = listaActual().update { it.deshacer() }
    fun vaciar() = listaActual().update { it.vaciar() }

    // Sesión de prueba

    fun iniciarSesion(nombre: String) {
        if (_sesion.value != null) return
        _sesion.value = SesionPrueba(nombre.trim().ifEmpty { equipo.dispositivo }, System.currentTimeMillis())
    }

    fun terminarSesion() {
        _sesion.value = null
    }

    fun cambiarEsperado(codigo: String) = _sesion.update { it?.conEsperado(codigo) }

    fun marcarUltimo(resultado: ResultadoIntento) = _sesion.update { it?.marcarUltimo(resultado) }

    fun registrarFallo(resultado: ResultadoIntento) {
        val hora = System.currentTimeMillis()
        _sesion.update { it?.registrarFallo(resultado, contexto(hora)) }
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
