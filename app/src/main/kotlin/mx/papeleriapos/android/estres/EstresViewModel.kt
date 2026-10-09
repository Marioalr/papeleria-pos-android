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
import mx.papeleriapos.android.medicion.MonitorRecursos
import mx.papeleriapos.android.medicion.MuestraRecursos
import mx.papeleriapos.android.sesion.CsvSesion
import mx.papeleriapos.android.sesion.MedidorAndroid
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner
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
 * principal (CameraX lo pide) y sin bloquearlo. Una sola a la vez; se cancela si la app pasa a segundo plano.
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
    private var muestrasContinuo: List<MuestraRecursos> = emptyList()
    private var clave = ""

    fun cambiarCiclos(n: Int) = _estado.update { if (it.corriendo != null) it else it.copy(ciclosPedidos = n.coerceIn(10, 1000)) }
    fun cambiarControladorNuevo(v: Boolean) = _estado.update { if (it.corriendo != null) it else it.copy(controladorNuevo = v) }
    fun cambiarMinutos(m: Int) = _estado.update { if (it.corriendo != null) it else it.copy(minutosContinuo = m.coerceIn(1, 120)) }

    fun correr(prueba: PruebaEstres) {
        if (trabajo?.isActive == true) return
        cancelar = false
        clave = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        _estado.update { it.copy(corriendo = prueba, hechos = 0, total = 0, etiqueta = "Preparando…", aviso = null) }
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
                _estado.update { it.copy(corriendo = null, etiqueta = "") }
            }
        }
    }

    /** Detiene la prueba en curso; lo hecho hasta ahí se queda y se evalúa. */
    fun cancelar(motivo: String? = null) {
        if (trabajo?.isActive != true) return
        cancelar = true
        if (motivo != null) _estado.update { it.copy(aviso = motivo) }
    }

    private suspend fun ciclos() {
        val e = _estado.value
        val camara = CamaraEstres(getApplication(), viewModelScope, nuevoPorCiclo = e.controladorNuevo)
        try {
            camara.preparar()
            _estado.update { it.copy(total = e.ciclosPedidos, ciclos = emptyList(), memoriaCiclos = emptyList(), dictamenCiclos = null) }
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
            _estado.update { it.copy(dictamenCiclos = CriteriosCiclos.evaluar(hechos, memoria)) }
        } finally {
            camara.liberar()
        }
    }

    private suspend fun rafaga() {
        val camara = CamaraEstres(getApplication(), viewModelScope, nuevoPorCiclo = false)
        try {
            camara.preparar()
            _estado.update { it.copy(total = 60, segundosRafaga = emptyList(), finalRafaga = null, dictamenRafaga = null, etiqueta = "Abriendo la cámara…") }
            if (!camara.abrirYEsperar()) {
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
        val camara = CamaraEstres(getApplication(), viewModelScope, nuevoPorCiclo = false)
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
        val c = crearControlador(getApplication(), ConfigEscaner(segundosParaPausar = 0, segundosParaApagar = 0, sonido = false, vibracion = false))
        val m = MonitorRecursos(
            getApplication(),
            fuente = { c.estado.value.diagnostico },
            fase = { "continuo/" + c.estado.value.fase.name.lowercase() },
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
            // La pantalla del escáner también lo cierra al dejar de mostrarse (cerrar dos veces no hace nada).
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

    /** Los CSV de [prueba] (sus datos y el resumen de criterios), para compartir. */
    suspend fun exportar(prueba: PruebaEstres): List<Uri> = withContext(Dispatchers.IO) {
        val e = _estado.value
        val base = "${prueba.corta}-$clave-${equipo.dispositivo}"
        val archivos = ArrayList<Pair<String, String>>()
        val dictamen = when (prueba) {
            PruebaEstres.CICLOS -> e.dictamenCiclos.also { archivos += "estres-$base" to CsvEstres.ciclos(e.ciclos, e.memoriaCiclos) }
            PruebaEstres.RAFAGA -> e.dictamenRafaga.also { archivos += "estres-$base" to CsvEstres.rafaga(e.segundosRafaga) }
            PruebaEstres.MOTOR -> e.dictamenMotor.also { archivos += "estres-$base" to CsvEstres.motor(e.cambios, e.memoriaMotor) }
            PruebaEstres.CONTINUO -> e.dictamenContinuo.also {
                archivos += "estres-$base" to CsvRecursos.escribir(muestrasContinuo)
                e.termico?.let { t -> archivos += "minutos-$base" to CsvEstres.minutos(t.minutos) }
            }
        }
        if (dictamen != null) archivos += "resumen-$base" to CsvEstres.resumen(prueba.name, dictamen, equipo)
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

    override fun onCleared() {
        cancelar = true
        monitor?.detener()
    }

    private companion object {
        const val ETIQUETA = "Estres"
    }
}
