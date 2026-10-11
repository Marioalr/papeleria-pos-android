package mx.papeleriapos.escaner.ui

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
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.nucleo.Aceptada
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.CodigoLeido
import mx.papeleriapos.escaner.nucleo.Indicacion
import mx.papeleriapos.escaner.nucleo.Simbologia
import java.util.concurrent.atomic.AtomicInteger

/**
 * Un escáner sin cámara para las vistas previas y las pruebas de la interfaz: el estado se fija a mano con [fijar] o
 * [cambiar], las lecturas se simulan con [emitirLectura] y cada llamada de la interfaz queda contada para revisarla.
 *
 * Solo hace lo mínimo que haría el de verdad para que una prueba pueda seguir el flujo: [reanudar] sale de la pausa,
 * [alternarLinterna] cambia la linterna, [abrir] tras un error vuelve a ABRIENDO y [permisoConcedido] entra o sale de
 * SIN_PERMISO. Nunca cambia de fase por su cuenta.
 */
class ControladorDeMuestra(
    inicial: EstadoEscaner = EstadoEscaner(fase = Fase.ESCANEANDO),
    config: ConfigEscaner = ConfigEscaner(),
) : ControladorEscaner {
    /** Una llamada a [abrir]. */
    data class Apertura(val ancho: Int, val alto: Int, val rotacionPantalla: Int)

    private val _estado = MutableStateFlow(inicial)
    override val estado: StateFlow<EstadoEscaner> = _estado.asStateFlow()

    private val _eventos = MutableSharedFlow<EventoLectura>(extraBufferCapacity = 16)
    override val eventos: SharedFlow<EventoLectura> = _eventos.asSharedFlow()

    override val superficie: StateFlow<SurfaceRequest?> = MutableStateFlow(null)

    private val _config = MutableStateFlow(config)
    override val config: StateFlow<ConfigEscaner> = _config.asStateFlow()

    // Contadores: la interfaz llama desde el hilo principal y la prueba lee desde el suyo.
    private val _aperturas = AtomicInteger()
    private val _reanudaciones = AtomicInteger()
    private val _linternazos = AtomicInteger()
    private val _cierres = AtomicInteger()
    val aperturas: Int get() = _aperturas.get()
    val reanudaciones: Int get() = _reanudaciones.get()
    val cambiosDeLinterna: Int get() = _linternazos.get()
    val cierres: Int get() = _cierres.get()

    @Volatile var ultimaApertura: Apertura? = null
        private set

    /** El último punto que se pidió enfocar (región visible normalizada). */
    @Volatile var ultimoEnfoque: Pair<Float, Float>? = null
        private set

    /** Lo último que avisó la interfaz sobre el permiso; null si todavía no avisa. */
    @Volatile var permiso: Boolean? = null
        private set

    fun fijar(estado: EstadoEscaner) {
        _estado.value = estado
    }

    fun cambiar(transformar: (EstadoEscaner) -> EstadoEscaner) {
        _estado.update(transformar)
    }

    /**
     * Simula una lectura aceptada: sube [EstadoEscaner.lecturas], la deja en [EstadoEscaner.ultima] y la emite en
     * [eventos]. No cambia la fase (el de verdad pasa a LEIDO unos 500 ms; eso se fija aparte si se quiere ver).
     */
    fun emitirLectura(
        texto: String = "7509993456786",
        simbologia: Simbologia = Simbologia.EAN_13,
        normalizado: String = texto,
        caja: Caja? = Caja(0.25f, 0.42f, 0.75f, 0.58f),
    ): EventoLectura {
        val codigo = CodigoLeido(texto, normalizado, simbologia, listOf(normalizado))
        val anterior = _estado.value.ultima?.aceptada?.codigo?.normalizado
        val evento = EventoLectura(
            aceptada = Aceptada(codigo, caja, System.nanoTime(), repetida = anterior == normalizado),
            msDesdeCandidato = -1,
            indicaciones = emptyList(),
            motor = _config.value.motor,
            moduloPx = 0f,
            zoom = _estado.value.zoom,
            linterna = _estado.value.linterna,
        )
        _estado.update { it.copy(ultima = evento, lecturas = it.lecturas + 1, cajaCodigo = caja ?: it.cajaCodigo) }
        _eventos.tryEmit(evento)
        return evento
    }

    override fun abrir(dueno: LifecycleOwner, anchoVisor: Int, altoVisor: Int, rotacionPantalla: Int) {
        ultimaApertura = Apertura(anchoVisor, altoVisor, rotacionPantalla)
        _aperturas.incrementAndGet()
        _estado.update { if (it.fase == Fase.ERROR) it.copy(fase = Fase.ABRIENDO, mensajeError = null) else it }
    }

    override fun configurar(config: ConfigEscaner) {
        _config.value = config
    }

    override fun alternarLinterna() {
        _linternazos.incrementAndGet()
        _estado.update { if (it.hayLinterna) it.copy(linterna = !it.linterna) else it }
    }

    override fun reanudar() {
        _reanudaciones.incrementAndGet()
        _estado.update {
            if (it.fase == Fase.PAUSADO || it.fase == Fase.LEIDO) {
                it.copy(fase = Fase.ESCANEANDO, indicacion = Indicacion.APUNTA, cajaCodigo = null)
            } else {
                it
            }
        }
    }

    override fun enfocar(x: Float, y: Float) {
        ultimoEnfoque = x to y
    }

    override fun permisoConcedido(concedido: Boolean) {
        permiso = concedido
        _estado.update {
            when {
                !concedido -> it.copy(fase = Fase.SIN_PERMISO)
                it.fase == Fase.SIN_PERMISO -> it.copy(fase = Fase.ABRIENDO)
                else -> it
            }
        }
    }

    override fun cerrar() {
        _cierres.incrementAndGet()
    }

    companion object {
        /** Un diagnóstico creíble para ver el panel en las vistas previas. */
        val DIAGNOSTICO_DE_MUESTRA = mx.papeleriapos.escaner.Diagnostico(
            motor = Motor.ML_KIT, resolucion = "1280×720", franjaPx = "576×259", cuadrosPorSegundo = 29.8f,
            msMotor = 32.4f, msLocalizador = 0.25f, msCuadro = 34.1f, moduloPx = 2.7f, nitidez = 0.42f, brillo = 118f,
            zoom = 1.5f, rangoFps = "30-30", camaraFisica = "0", estadoEnfoque = "ENFOCADO",
        )
    }
}
