package mx.papeleriapos.android.estres

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.exportar.Compartir
import mx.papeleriapos.android.fabrica.crearControlador
import mx.papeleriapos.android.medicion.CsvRecursos
import mx.papeleriapos.android.medicion.HoraIso
import mx.papeleriapos.android.medicion.MonitorDeSesion
import mx.papeleriapos.android.medicion.MonitorRecursos
import mx.papeleriapos.android.medicion.MuestraRecursos
import mx.papeleriapos.android.sesion.CsvSesion
import mx.papeleriapos.android.sesion.MedidorAndroid
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.Motor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Las pruebas de estrés de la cámara que corren dentro de la app. */
enum class PruebaEstres(val titulo: String, val corta: String) {
    CICLOS("Abrir y cerrar la cámara", "ciclos"),
    RAFAGA("Zoom y linterna rápidos", "rafaga"),
    MOTOR("Cambiar de motor en caliente", "motor"),
    CONTINUO("Escaneo continuo", "continuo"),
}

data class EstadoEstres(
    val corriendo: PruebaEstres? = null,
    val hechos: Int = 0,
    val total: Int = 0,
    val etiqueta: String = "",
    val ciclosPedidos: Int = 200,
    /** Ciclos con un controlador nuevo cada vez (si no, el mismo controlador yendo y volviendo de segundo plano). */
    val controladorNuevo: Boolean = true,
    val minutosContinuo: Int = 30,
    /** El motor y los cuadros por segundo con que corren los ciclos, la ráfaga y el escaneo continuo. */
    val camara: ConfigEstres = ConfigEstres(),
    /** Con qué cámara corrió cada prueba (va en sus CSV). */
    val usada: Map<PruebaEstres, ConfigEstres> = emptyMap(),
    /** Si los ciclos que se ven corrieron con un controlador nuevo cada vez. */
    val ciclosConControladorNuevo: Boolean = true,
    val ciclos: List<Ciclo> = emptyList(),
    val memoriaCiclos: List<PuntoMemoria> = emptyList(),
    val dictamenCiclos: Dictamen? = null,
    val segundosRafaga: List<SegundoRafaga> = emptyList(),
    val finalRafaga: FinalRafaga? = null,
    val dictamenRafaga: Dictamen? = null,
    val cambios: List<CambioMotor> = emptyList(),
    val memoriaMotor: List<PuntoMemoria> = emptyList(),
    val dictamenMotor: Dictamen? = null,
    val termico: ResultadoTermico? = null,
    val dictamenContinuo: Dictamen? = null,
    /** La muestra más reciente del monitor durante el escaneo continuo. */
    val muestra: MuestraRecursos? = null,
    val aviso: String? = null,
)

/**
 * Corre las pruebas de estrés (docs/pruebas.md): cada una sobre un controlador propio con su ciclo de vida, en el hilo
 * principal (CameraX lo pide) y sin bloquearlo. Una sola a la vez; se cancela si la app pasa a segundo plano. Mientras
 * corre, el monitor de la sesión de prueba se pausa: un solo muestreador a la vez (y no le suma CPU a la prueba).
 *
 * Es dueño de los controladores que crea: los cierra al terminar cada prueba y en [onCleared].
 */
class EstresViewModel(app: Application) : AndroidViewModel(app) {
    private val equipo = MedidorAndroid.equipo()
    private val _estado = MutableStateFlow(EstadoEstres())
    val estado: StateFlow<EstadoEstres> = _estado.asStateFlow()

    /** El controlador del escaneo continuo: lo muestra la pantalla mientras corre. */
    private val _controladorContinuo = MutableStateFlow<ControladorEscaner?>(null)
    val controladorContinuo: StateFlow<ControladorEscaner?> = _controladorContinuo.asStateFlow()

    private var trabajo: Job? = null
    @Volatile private var cancelar = false
    private var monitor: MonitorRecursos? = null
    /** La cámara de la prueba en curso (para cerrarla también si el ViewModel se va a media prueba). */
    private var camaraEnCurso: CamaraEstres? = null
    private var pausoSesion = false
    private var muestrasContinuo: List<MuestraRecursos> = emptyList()
    private var clave = ""

    fun cambiarCiclos(n: Int) = _estado.update { if (it.corriendo != null) it else it.copy(ciclosPedidos = n.coerceIn(10, 1000)) }
    fun cambiarControladorNuevo(v: Boolean) = _estado.update { if (it.corriendo != null) it else it.copy(controladorNuevo = v) }
    fun cambiarMinutos(m: Int) = _estado.update { if (it.corriendo != null) it else it.copy(minutosContinuo = m.coerceIn(1, 120)) }
    fun cambiarMotor(m: Motor) = _estado.update { if (it.corriendo != null) it else it.copy(camara = it.camara.copy(motor = m)) }
    fun cambiarFpsFijo30(v: Boolean) = _estado.update { if (it.corriendo != null) it else it.copy(camara = it.camara.copy(fpsFijo30 = v)) }

    fun correr(prueba: PruebaEstres) {
        if (trabajo?.isActive == true) return
        cancelar = false
        clave = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        _estado.update {
            val usada = if (prueba == PruebaEstres.MOTOR) it.camara.copy(motor = Motor.AUTOMATICO) else it.camara
            it.copy(corriendo = prueba, hechos = 0, total = 0, etiqueta = "Preparando…", aviso = null, usada = it.usada + (prueba to usada))
        }
        pausarSesion()
        trabajo = viewModelScope.launch {
            try {
                when (prueba) {
                    PruebaEstres.CICLOS -> ciclos()
                    PruebaEstres.RAFAGA -> rafaga()
                    PruebaEstres.MOTOR -> motor()
                    PruebaEstres.CONTINUO -> continuo()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(ETIQUETA, "Falló la prueba ${prueba.name}", e)
                _estado.update { it.copy(aviso = "La prueba falló: ${e::class.java.simpleName} ${e.message ?: ""}") }
            } finally {
                camaraEnCurso = null
                reanudarSesion()
                _estado.update { it.copy(corriendo = null, etiqueta = "") }
            }
        }
    }

    private fun pausarSesion() {
        if (pausoSesion) return
        pausoSesion = true
        MonitorDeSesion.pausar()
    }

    private fun reanudarSesion() {
        if (!pausoSesion) return
        pausoSesion = false
        MonitorDeSesion.reanudar()
    }

    private fun nuevaCamara(nuevoPorCiclo: Boolean, config: ConfigEstres = _estado.value.camara): CamaraEstres =
        CamaraEstres(getApplication(), viewModelScope, nuevoPorCiclo, config).also { camaraEnCurso = it }

    /** Detiene la prueba en curso; lo hecho hasta ahí se queda y se evalúa. */
    fun cancelar(motivo: String? = null) {
        if (trabajo?.isActive != true) return
        cancelar = true
        if (motivo != null) _estado.update { it.copy(aviso = motivo) }
    }

    private suspend fun ciclos() {
        val e = _estado.value
        val camara = nuevaCamara(nuevoPorCiclo = e.controladorNuevo)
        try {
            camara.preparar()
            _estado.update {
                it.copy(
                    total = e.ciclosPedidos, ciclos = emptyList(), memoriaCiclos = emptyList(), dictamenCiclos = null,
                    ciclosConControladorNuevo = e.controladorNuevo,
                )
            }
            val (hechos, memoria) = CiclosCamara(camara, ciclos = e.ciclosPedidos).correr(
                alCiclo = { c, m ->
                    _estado.update {
                        it.copy(
                            hechos = c.n,
                            etiqueta = "Ciclo ${c.n}: " + (c.error ?: "${c.primerCuadroMs.toInt()} ms al primer cuadro"),
                            ciclos = it.ciclos + c,
                            memoriaCiclos = if (m != null) it.memoriaCiclos + m else it.memoriaCiclos,
                        )
                    }
                },
                cancelado = { cancelar },
            )
            _estado.update { it.copy(dictamenCiclos = CriteriosCiclos.evaluar(hechos, memoria, nuevoPorCiclo = e.controladorNuevo)) }
        } finally {
            camara.liberar()
        }
    }

    private suspend fun rafaga() {
        val camara = nuevaCamara(nuevoPorCiclo = false)
        try {
            camara.preparar()
            _estado.update { it.copy(total = 60, segundosRafaga = emptyList(), finalRafaga = null, dictamenRafaga = null, etiqueta = "Abriendo la cámara…") }
            if (!camara.abrirYEsperar() || !camara.prepararRafaga()) {
                _estado.update { it.copy(aviso = "La cámara no abrió: ${camara.error() ?: "sin cuadros"}") }
                return
            }
            val (segundos, final) = RafagaControles(camara, SystemClock::elapsedRealtime, { delay(it) }).correr(
                alSegundo = { s ->
                    _estado.update {
                        it.copy(hechos = s.segundo, etiqueta = "${s.ordenesZoom} zooms, ${s.ordenesLinterna} linternas", segundosRafaga = it.segundosRafaga + s)
                    }
                },
                cancelado = { cancelar },
            )
            _estado.update { it.copy(finalRafaga = final, dictamenRafaga = CriteriosRafaga.evaluar(segundos, final)) }
        } finally {
            camara.liberar()
        }
    }

    private suspend fun motor() {
        // La rueda empieza en Automático (ya puesto); de lo elegido solo cuentan los cuadros por segundo.
        val camara = nuevaCamara(nuevoPorCiclo = false, config = _estado.value.camara.copy(motor = Motor.AUTOMATICO))
        try {
            camara.preparar()
            _estado.update { it.copy(total = 50, cambios = emptyList(), memoriaMotor = emptyList(), dictamenMotor = null, etiqueta = "Abriendo la cámara…") }
            if (!camara.abrirYEsperar()) {
                _estado.update { it.copy(aviso = "La cámara no abrió: ${camara.error() ?: "sin cuadros"}") }
                return
            }
            val (cambios, memoria) = CambiosMotor(camara).correr(
                alCambio = { c, m ->
                    _estado.update {
                        it.copy(
                            hechos = c.n,
                            etiqueta = "${c.motor}: " + (c.error ?: "${c.ms.toInt()} ms"),
                            cambios = it.cambios + c,
                            memoriaMotor = if (m != null) it.memoriaMotor + m else it.memoriaMotor,
                        )
                    }
                },
                cancelado = { cancelar },
            )
            _estado.update { it.copy(dictamenMotor = CriteriosMotor.evaluar(cambios, memoria)) }
        } finally {
            camara.liberar()
        }
    }

    /** (d) Escaneo continuo con el monitor; la pantalla muestra el escáner de [controladorContinuo] mientras dura. */
    private suspend fun continuo() {
        val minutos = _estado.value.minutosContinuo
        val cam = _estado.value.camara
        val c = crearControlador(
            getApplication(),
            ConfigEscaner(
                motor = cam.motor, fpsFijo30 = cam.fpsFijo30, segundosParaPausar = 0, segundosParaApagar = 0, sonido = false,
                vibracion = false,
            ),
        )
        val fases = FASES_CONTINUO
        val m = MonitorRecursos(
            getApplication(),
            fuente = { c.estado.value.diagnostico },
            fase = { fases[c.estado.value.fase.ordinal] },
            // Toda la prueba en memoria para el detector (30 min = 1800 muestras), y un poco más.
            maximoEnMemoria = minutos * 60 + 120,
            alMuestrear = { muestra -> _estado.update { it.copy(muestra = muestra) } },
        )
        monitor = m
        _estado.update { it.copy(total = minutos * 60, termico = null, dictamenContinuo = null, muestra = null) }
        _controladorContinuo.value = c
        try {
            m.iniciar()
            val inicio = SystemClock.elapsedRealtime()
            while (!cancelar) {
                val s = ((SystemClock.elapsedRealtime() - inicio) / 1000).toInt()
                if (s >= minutos * 60) break
                _estado.update { it.copy(hechos = s, etiqueta = "Minuto ${s / 60 + 1} de $minutos") }
                delay(1_000)
            }
        } finally {
            m.detener()
            monitor = null
            // Este ViewModel es el dueño del controlador: la pantalla solo lo muestra.
            _controladorContinuo.value = null
            c.cerrar()
            muestrasContinuo = m.muestras()
            val r = DetectorEstrangulamiento().evaluar(muestrasContinuo)
            _estado.update { it.copy(termico = r, dictamenContinuo = dictamenTermico(r)) }
        }
    }

    private fun dictamenTermico(r: ResultadoTermico): Dictamen = Dictamen(
        listOf(
            Criterio("Minutos medidos", r.minutos.size.toString(), "> 1", r.minutos.size > 1),
            Criterio(
                "Estrangulamiento térmico",
                if (r.detectado) "desde el segundo ${r.desdeSegundo}: ${r.condiciones.joinToString(", ")}" else "no",
                "no",
                !r.detectado,
            ),
            Criterio("Cuadros por segundo del minuto 1", Cuentas.texto(r.fpsBase), "—", true),
            Criterio("ms de CPU por cuadro del minuto 1", Cuentas.texto(r.cpuPorCuadroBase, 2), "—", true),
        ),
    )

    /** Los CSV de [prueba] (sus datos y el resumen de criterios, con el motor y los FPS con que corrió), para compartir. */
    suspend fun exportar(prueba: PruebaEstres): List<Uri> = withContext(Dispatchers.IO) {
        val e = _estado.value
        val cam = e.usada[prueba]
        val base = "${prueba.corta}-$clave-${cam?.motor?.name ?: ""}-${equipo.dispositivo}"
        val archivos = ArrayList<Pair<String, String>>()
        val dictamen = when (prueba) {
            PruebaEstres.CICLOS -> e.dictamenCiclos.also { archivos += "estres-$base" to CsvEstres.ciclos(e.ciclos, e.memoriaCiclos, cam) }
            PruebaEstres.RAFAGA -> e.dictamenRafaga.also { archivos += "estres-$base" to CsvEstres.rafaga(e.segundosRafaga, cam) }
            PruebaEstres.MOTOR -> e.dictamenMotor.also { archivos += "estres-$base" to CsvEstres.motor(e.cambios, e.memoriaMotor, cam) }
            PruebaEstres.CONTINUO -> e.dictamenContinuo.also {
                val hora = HoraIso()
                archivos += "estres-$base" to CsvRecursos.escribir(muestrasContinuo) { ms -> StringBuilder().also { hora.agregar(it, ms) }.toString() }
                e.termico?.let { t -> archivos += "minutos-$base" to CsvEstres.minutos(t.minutos, cam) }
            }
        }
        if (dictamen != null) archivos += "resumen-$base" to CsvEstres.resumen(prueba.name, dictamen, equipo, cam)
        archivos.map { (nombre, texto) ->
            Compartir.escribir(getApplication(), CsvSesion.nombreArchivo(nombre.substringBefore('-'), nombre.substringAfter('-')), texto)
        }
    }

    fun compartir(contexto: android.content.Context, prueba: PruebaEstres) {
        viewModelScope.launch {
            val uris = exportar(prueba)
            if (uris.isNotEmpty()) Compartir.enviarVarios(contexto, uris, "Estrés: ${prueba.titulo}")
        }
    }

    /** Se va el ViewModel (a media prueba, p. ej.): cierra lo que creó sin esperar a que la prueba lo note. */
    override fun onCleared() {
        cancelar = true
        trabajo?.cancel()
        monitor?.detener()
        monitor = null
        camaraEnCurso?.liberar()
        camaraEnCurso = null
        _controladorContinuo.value?.cerrar()
        _controladorContinuo.value = null
        reanudarSesion()
    }

    private companion object {
        const val ETIQUETA = "Estres"

        /** La fase del escaneo continuo para recursos.csv, armada una vez (el monitor la pide cada segundo). */
        val FASES_CONTINUO = mx.papeleriapos.escaner.Fase.entries.map { "continuo/" + it.name.lowercase(Locale.ROOT) }
    }
}
