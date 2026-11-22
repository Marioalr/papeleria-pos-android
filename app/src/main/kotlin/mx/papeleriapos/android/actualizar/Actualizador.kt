package mx.papeleriapos.android.actualizar

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.BuildConfig
import java.io.File
import java.io.IOException

/** En qué va la actualización; la pantalla ([PantallaActualizar]) dibuja cada uno. */
sealed interface EstadoActualizacion {
    /** Nadie ha tocado «Buscar versión nueva» desde que se abrió la app: la app nunca busca sola. */
    data object SinBuscar : EstadoActualizacion

    data object Buscando : EstadoActualizacion

    /** Lo publicado en GitHub ([publicada]) no es más nuevo que lo instalado. */
    data class AlDia(val publicada: Version) : EstadoActualizacion

    /** Hay una versión más nueva. [aviso]: por qué se volvió aquí (se canceló la descarga o el cuadro de Android). */
    data class Hay(val version: VersionPublicada, val aviso: String? = null) : EstadoActualizacion

    /** [bajado] bytes de `version.apk.tamano`. */
    data class Bajando(val version: VersionPublicada, val bajado: Long) : EstadoActualizacion {
        /** De 0 a 1. */
        val avance: Float get() = (bajado.toFloat() / version.apk.tamano.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /** Ya bajó: se revisa que sea la misma app, más nueva y con la misma firma. */
    data class Comprobando(val version: VersionPublicada) : EstadoActualizacion

    /** El APK ya está en manos de Android, que enseña su cuadro para confirmar. */
    data class EsperandoAAndroid(val version: VersionPublicada) : EstadoActualizacion

    /** No se pudo. Con [version], se puede volver a intentar la descarga; sin ella, hay que volver a buscar. */
    data class Error(val mensaje: String, val version: VersionPublicada? = null) : EstadoActualizacion
}

/**
 * Actualizar la app desde la propia app (docs/plan.md, «Versiones»), solo cuando el dueño lo pide: [buscar] le pregunta a
 * GitHub cuál es la última versión publicada y [descargarEInstalar] baja su APK, lo comprueba y se lo da a Android. Nada
 * de esto corre solo ni en segundo plano; el estado vive en memoria (al cerrar la app se vuelve a [EstadoActualizacion.SinBuscar]).
 *
 * @param carpeta Donde se baja el APK (`cacheDir/actualizar`): se vacía al crear el actualizador y después de entregar
 * el archivo a Android, que guarda su propia copia.
 * @param io Donde corre lo que bloquea (leer el APK, entregárselo a Android); la red corre en la suya ([ClienteGithub]).
 */
class Actualizador(
    val instalada: Version,
    private val fuente: FuenteDeVersiones,
    private val carpeta: File,
    private val comprobador: ComprobadorDeApk,
    private val instalador: Instalador,
    private val ambito: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _estado = MutableStateFlow<EstadoActualizacion>(EstadoActualizacion.SinBuscar)
    val estado: StateFlow<EstadoActualizacion> = _estado.asStateFlow()

    private var trabajo: Job? = null

    init {
        vaciarCarpeta()
    }

    /** Si Android deja que esta app instale; si no, la pantalla lo explica y abre su ajuste. */
    fun puedeInstalar(): Boolean = instalador.puedeInstalar()

    /** «Buscar versión nueva»: lo único que hace que la app salga a GitHub. Mientras busca o baja, no hace nada. */
    fun buscar() {
        if (ocupado()) return
        _estado.value = EstadoActualizacion.Buscando
        trabajo = ambito.launch {
            _estado.value = try {
                val ultima = fuente.ultima()
                if (ultima.version > instalada) EstadoActualizacion.Hay(ultima) else EstadoActualizacion.AlDia(ultima.version)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErrorDeActualizacion) {
                registrar("Buscar: ${e.message}")
                EstadoActualizacion.Error(e.message ?: SIN_GITHUB)
            } catch (e: IOException) {
                registrar("Buscar: ${e.javaClass.simpleName}: ${e.message}")
                EstadoActualizacion.Error(SIN_GITHUB)
            }
        }
    }

    /**
     * «Descargar e instalar» la versión que se encontró (también después de un error con ella): la baja, comprueba el
     * tamaño y la huella, que sea esta app, más nueva y con la misma firma, y se la entrega a Android. Si algo no cuadra,
     * el archivo se borra y el estado dice por qué.
     */
    fun descargarEInstalar() {
        if (ocupado()) return
        val version = when (val e = _estado.value) {
            is EstadoActualizacion.Hay -> e.version
            is EstadoActualizacion.Error -> e.version ?: return
            else -> return
        }
        if (!instalador.puedeInstalar()) {
            _estado.value = EstadoActualizacion.Error(SIN_PERMISO, version)
            return
        }
        _estado.value = EstadoActualizacion.Bajando(version, 0)
        trabajo = ambito.launch {
            val archivo = File(carpeta, ARCHIVO)
            try {
                vaciarCarpeta()
                fuente.bajar(version.apk, archivo) { van ->
                    // Solo mientras se baja: un aviso de avance que llegue tarde (llega de otro hilo) no pisa lo que siguió.
                    val antes = _estado.value
                    if (antes is EstadoActualizacion.Bajando) _estado.compareAndSet(antes, EstadoActualizacion.Bajando(version, van))
                }
                _estado.value = EstadoActualizacion.Comprobando(version)
                val razon = withContext(io) { comprobador.revisar(archivo, version.version) }
                if (razon != null) {
                    registrar("Comprobar la ${version.version}: $razon")
                    _estado.value = EstadoActualizacion.Error(razon, version)
                    return@launch
                }
                // Antes de entregarlo: Android puede contestar antes de que `instalar` regrese.
                _estado.value = EstadoActualizacion.EsperandoAAndroid(version)
                withContext(io) { instalador.instalar(archivo) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErrorDeActualizacion) {
                registrar("Bajar la ${version.version}: ${e.message}")
                _estado.value = EstadoActualizacion.Error(e.message ?: SIN_GITHUB, version)
            } catch (e: IOException) {
                registrar("Bajar o instalar la ${version.version}: ${e.javaClass.simpleName}: ${e.message}")
                val esperabaAAndroid = _estado.value is EstadoActualizacion.EsperandoAAndroid
                _estado.value = EstadoActualizacion.Error(if (esperabaAAndroid) ANDROID_NO_LO_ACEPTO else SE_CORTO, version)
            } finally {
                // Android ya tiene su copia (o no se instala): aquí no queda nada.
                archivo.delete()
            }
        }
    }

    /** «Cancelar» mientras baja: se deja de bajar, se borra lo bajado y se vuelve a la versión encontrada. */
    fun cancelar() {
        val e = _estado.value as? EstadoActualizacion.Bajando ?: return
        trabajo?.cancel()
        trabajo = null
        _estado.value = EstadoActualizacion.Hay(e.version, DESCARGA_CANCELADA)
    }

    /** Lo que Android contestó de la instalación ([ReceptorDeInstalacion]); solo cuenta si se le estaba esperando. */
    fun alContestarAndroid(respuesta: RespuestaDeAndroid, mensaje: String? = null) {
        val e = _estado.value as? EstadoActualizacion.EsperandoAAndroid ?: return
        if (respuesta != RespuestaDeAndroid.INSTALADA) registrar("Android: $respuesta ${mensaje.orEmpty()}")
        _estado.value = when (respuesta) {
            // Android cierra la app para cambiarla: esto casi nunca alcanza a verse.
            RespuestaDeAndroid.INSTALADA -> EstadoActualizacion.AlDia(e.version.version)
            RespuestaDeAndroid.CANCELADA -> EstadoActualizacion.Hay(e.version, INSTALACION_CANCELADA)
            RespuestaDeAndroid.FALLO -> EstadoActualizacion.Error(mensaje ?: "Android no la instaló.", e.version)
        }
    }

    private fun ocupado(): Boolean = when (_estado.value) {
        EstadoActualizacion.Buscando, is EstadoActualizacion.Bajando, is EstadoActualizacion.Comprobando -> true
        // Esperando a Android no: si su cuadro se perdió (se apagó la pantalla), se puede volver a buscar.
        else -> false
    }

    private fun vaciarCarpeta() {
        carpeta.listFiles()?.forEach { it.delete() }
    }

    companion object {
        /** Dentro de `cacheDir`: Android puede borrarla si le falta espacio, y la app la vacía sola. */
        const val CARPETA = "actualizar"
        private const val ARCHIVO = "actualizacion.apk"

        const val SIN_GITHUB = "No se pudo llegar a GitHub. Revisa que el celular tenga Internet (el Wi-Fi o los datos) y vuelve a intentar."
        const val SE_CORTO = "La descarga se cortó. Revisa que el celular tenga Internet y vuelve a intentar."
        const val ANDROID_NO_LO_ACEPTO = "Android no aceptó el APK para instalarlo. Vuelve a intentar."
        const val SIN_PERMISO = "Android todavía no deja que esta app instale: dale el permiso «Instalar apps desconocidas» y vuelve a intentar."
        const val DESCARGA_CANCELADA = "Se canceló la descarga: no se instaló nada."
        const val INSTALACION_CANCELADA = "Se canceló en el cuadro de Android: no se instaló nada."

        /** La etiqueta del registro: `adb logcat -s Actualizar`. Solo qué falló; nada del celular ni de la tienda. */
        const val ETIQUETA = "Actualizar"

        /** Al registro de Android; en la JVM sin Robolectric (`Log` sin implementar) no escribe nada. */
        internal fun registrar(texto: String) {
            try {
                Log.w(ETIQUETA, texto)
            } catch (e: RuntimeException) {
                // Pruebas en la JVM pura.
            }
        }

        @Volatile private var proceso: Actualizador? = null

        /** El del proceso: lo usan la pantalla y el receptor de lo que contesta Android. */
        fun de(contexto: Context): Actualizador = proceso ?: synchronized(this) {
            proceso ?: crear(contexto.applicationContext).also { proceso = it }
        }

        /** Para las pruebas: como un proceso nuevo, o con [otro]. */
        internal fun reiniciar(otro: Actualizador? = null) {
            proceso = otro
        }

        private fun crear(app: Context) = Actualizador(
            instalada = Version.de(BuildConfig.VERSION_NAME) ?: Version(0, 0, 0),
            fuente = ClienteGithub(),
            carpeta = File(app.cacheDir, CARPETA),
            comprobador = ComprobadorAndroid(app),
            instalador = InstaladorAndroid(app),
        )
    }
}
