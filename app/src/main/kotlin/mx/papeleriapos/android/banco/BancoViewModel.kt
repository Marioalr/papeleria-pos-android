package mx.papeleriapos.android.banco

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.exportar.Compartir
import mx.papeleriapos.android.sesion.CsvSesion
import mx.papeleriapos.android.sesion.MedidorAndroid
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.motores.crearMotor
import mx.papeleriapos.escaner.nucleo.Simbologia
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** El catálogo de escenas fijas, o los límites: la dificultad sube por grados hasta que el motor deja de leer. */
enum class ModoBanco(val etiqueta: String) { CATALOGO("Catálogo"), LIMITES("Límites") }

/** Los motores que compara el banco: los de verdad («Lado a lado» es para compararlos en vivo, con la cámara). */
val MOTORES_DEL_BANCO: List<Motor> = Motor.entries.filter { it != Motor.LADO_A_LADO }

data class EstadoBanco(
    val modo: ModoBanco = ModoBanco.CATALOGO,
    val corriendo: Boolean = false,
    val hechos: Int = 0,
    val total: Int = 0,
    val etiqueta: String = "",
    /** Leer solo la franja central, como en vivo; si no, el cuadro completo. */
    val recortarFranja: Boolean = true,
    val motores: Set<Motor> = MOTORES_DEL_BANCO.toSet(),
    val escenas: Int = 0,
    val simbologias: Set<Simbologia> = emptySet(),
    val resultados: List<ResultadoEscena> = emptyList(),
    val resumenes: List<ResumenMotor> = emptyList(),
    /** Fecha y hora de la última corrida; identifica el CSV. */
    val clave: String = "",
    val regionUsada: String = "",
    val cancelado: Boolean = false,
    /** El resultado del modo «Límites»: por motor y condición. */
    val limites: List<LimiteMotor> = emptyList(),
    /** De qué modo es el último resultado (lo que se exporta). */
    val modoResultado: ModoBanco = ModoBanco.CATALOGO,
)

/** Corre el banco de pruebas en su propio hilo (los motores se usan desde uno solo) y publica el avance. */
class BancoViewModel(app: Application) : AndroidViewModel(app) {
    private val hilo = Executors.newSingleThreadExecutor { Thread(it, "banco-de-pruebas") }
    private val despachador = hilo.asCoroutineDispatcher()
    private val preferencias = PreferenciasEscaner(app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE))
    private val equipo = MedidorAndroid.equipo()

    private val _estado = MutableStateFlow(EstadoBanco(escenas = CatalogoEscenas.escenas().size))
    val estado: StateFlow<EstadoBanco> = _estado.asStateFlow()

    private var trabajo: Job? = null
    @Volatile private var cancelar = false

    fun cambiarRecorte(recortar: Boolean) = _estado.update { if (it.corriendo) it else it.copy(recortarFranja = recortar) }

    fun alternarMotor(motor: Motor) = _estado.update {
        if (it.corriendo) it else it.copy(motores = if (motor in it.motores) it.motores - motor else it.motores + motor)
    }

    fun cambiarModo(modo: ModoBanco) = _estado.update { if (it.corriendo) it else it.copy(modo = modo) }

    fun correr() {
        if (trabajo?.isActive == true) return
        val e = _estado.value
        if (e.motores.isEmpty()) return
        if (e.modo == ModoBanco.LIMITES) {
            correrLimites(e)
            return
        }
        val config = preferencias.leer()
        val banco = Banco(
            crearMotor = ::crearMotor,
            motores = Motor.entries.filter { it in e.motores },
            simbologias = config.simbologias,
            recortarFranja = e.recortarFranja,
            altoFranja = config.altoFranja,
        )
        val escenas = CatalogoEscenas.escenas(banco.anchoDerecho, banco.altoDerecho)
        val region = if (e.recortarFranja) "franja ${(config.altoFranja * 100).toInt()}%" else "cuadro completo"
        cancelar = false
        _estado.update {
            it.copy(
                corriendo = true, hechos = 0, total = banco.pasos(escenas), etiqueta = "Preparando…",
                escenas = escenas.size, simbologias = config.simbologias, resultados = emptyList(),
                resumenes = emptyList(), clave = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()),
                regionUsada = region, cancelado = false,
            )
        }
        trabajo = viewModelScope.launch {
            val (resultados, resumenes) = withContext(despachador) {
                banco.correr(
                    escenas,
                    progreso = { hechos, etiqueta -> _estado.update { it.copy(hechos = hechos, etiqueta = etiqueta) } },
                    cancelado = { cancelar },
                )
            }
            _estado.update {
                it.copy(
                    corriendo = false, etiqueta = "", resultados = resultados, resumenes = resumenes, cancelado = cancelar,
                    limites = emptyList(), modoResultado = ModoBanco.CATALOGO,
                )
            }
        }
    }

    private fun correrLimites(e: EstadoBanco) {
        val config = preferencias.leer()
        val banco = BancoLimites(
            crearMotor = ::crearMotor,
            motores = Motor.entries.filter { it in e.motores },
            simbologias = config.simbologias,
            recortarFranja = e.recortarFranja,
            altoFranja = config.altoFranja,
        )
        val region = if (e.recortarFranja) "franja ${(config.altoFranja * 100).toInt()}%" else "cuadro completo"
        cancelar = false
        _estado.update {
            it.copy(
                corriendo = true, hechos = 0, total = banco.pasos(), etiqueta = "Preparando…",
                simbologias = config.simbologias, resultados = emptyList(), resumenes = emptyList(), limites = emptyList(),
                clave = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()), regionUsada = region,
                cancelado = false,
            )
        }
        trabajo = viewModelScope.launch {
            val limites = withContext(despachador) {
                banco.correr(
                    progreso = { hechos, etiqueta -> _estado.update { it.copy(hechos = hechos, etiqueta = etiqueta) } },
                    cancelado = { cancelar },
                )
            }
            _estado.update {
                it.copy(corriendo = false, etiqueta = "", limites = limites, cancelado = cancelar, modoResultado = ModoBanco.LIMITES)
            }
        }
    }

    fun cancelar() {
        cancelar = true
    }

    suspend fun csv(): Uri? {
        val e = _estado.value
        if (e.modoResultado == ModoBanco.LIMITES && e.limites.isNotEmpty()) {
            return withContext(Dispatchers.IO) {
                val clave = "${e.clave}-${equipo.dispositivo}"
                Compartir.escribir(getApplication(), CsvSesion.nombreArchivo("limites", clave), CsvLimites.escribir(clave, e.limites, equipo))
            }
        }
        if (e.resultados.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            val clave = "${e.clave}-${equipo.dispositivo}"
            Compartir.escribir(
                getApplication(),
                CsvSesion.nombreArchivo("banco", clave),
                CsvBanco.escribir(clave, e.regionUsada, e.resultados, equipo),
            )
        }
    }

    override fun onCleared() {
        cancelar = true
        hilo.shutdown()
    }
}
