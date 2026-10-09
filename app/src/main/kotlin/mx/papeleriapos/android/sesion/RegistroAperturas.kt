package mx.papeleriapos.android.sesion

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.escaner.Apertura
import mx.papeleriapos.escaner.PasoApertura
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Lo que identifica una apertura: la marca de la Activity (la ponen todas, en frío, tibio y caliente) o, si no hay, la
 * primera marca. 0 = sin marcas.
 */
fun claveApertura(a: Apertura): Long = a.marcas[PasoApertura.ACTIVIDAD]?.takeIf { it > 0L } ?: a.inicio

/**
 * La apertura del escáner ([delEscaner], la que publicó su estado) con las marcas más recientes: si las compartidas
 * ([compartida], `MarcasApertura.apertura`) son de la misma apertura y traen más pasos, esas. La primera lectura se marca
 * justo después de avisar la lectura, así que el estado puede llegar sin ella; y si las compartidas ya son de otra
 * apertura (p. ej. el escaneo rápido se abrió encima), se queda la del escáner.
 */
fun aperturaMasReciente(delEscaner: Apertura, compartida: Apertura): Apertura {
    val clave = claveApertura(delEscaner)
    return if (clave > 0L && clave == claveApertura(compartida) && compartida.marcas.size > delEscaner.marcas.size) compartida
    else delEscaner
}

/**
 * Las columnas de la apertura van solo en el primer intento de cada apertura; en los demás quedan vacías. Si no, al
 * promediar por intento cada apertura pesaría según cuántas lecturas tuvo. Se usa en el hilo principal.
 */
class AperturaPorIntento {
    private var ultima = 0L

    fun medidas(a: Apertura): MedidasApertura {
        val clave = claveApertura(a)
        if (clave <= 0L || clave == ultima) return MedidasApertura()
        ultima = clave
        return MedidasApertura.de(a)
    }

    /** Se deshizo el intento que llevaba la apertura: el siguiente la vuelve a llevar. */
    fun olvidar() {
        ultima = 0L
    }
}

/** aperturas.csv: una fila por apertura del escáner con el origen, el tipo y los ms desde su inicio hasta cada paso. */
object CsvAperturas {
    /** De dónde se abrió el escáner. */
    const val ORIGEN_APP = "app"
    const val ORIGEN_RAPIDO = "rapido"

    val COLUMNAS: List<String> = listOf("sesion", "hora", "origen", "tipo_arranque") +
        PasoApertura.entries.map { "ms_" + it.name.lowercase(Locale.ROOT) } +
        listOf("dispositivo", "sdk", "version_app", "commit")

    fun encabezado(): String = StringBuilder(Csv.BOM).also { Csv.renglon(COLUMNAS, it) }.toString()

    fun renglon(
        inicioSesion: Long,
        horaMs: Long,
        origen: String,
        a: Apertura,
        equipo: DatosEquipo,
        zona: TimeZone = TimeZone.getDefault(),
    ): String {
        val hora = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).apply { timeZone = zona }
        val campos = ArrayList<String>(COLUMNAS.size)
        campos += inicioSesion.toString()
        campos += hora.format(Date(horaMs))
        campos += origen
        campos += a.tipo.name
        for (p in PasoApertura.entries) campos += a.ms(p).let { if (it >= 0) it.toString() else "" }
        campos += Csv.textoSeguro(equipo.dispositivo)
        campos += equipo.sdk.toString()
        campos += equipo.versionApp
        campos += equipo.commit
        return StringBuilder().also { Csv.renglon(campos, it) }.toString()
    }
}

/**
 * Registra cada apertura del escáner en aperturas.csv de la sesión de prueba activa, también las del escaneo rápido
 * (sobre el bloqueo, sin sesión a la vista): sacar el celular del bolsillo y escanear es la medida principal. Solo
 * escribe a archivo, nunca muestra nada. Sin sesión activa no hace nada.
 *
 * Desde el hilo principal solo se encarga la fila; el disco se toca en un hilo propio, en orden.
 */
object RegistroAperturas {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** La clave de la última apertura registrada (en el hilo principal): cada una va una sola vez. */
    private var ultima = 0L

    /**
     * El inicio de la sesión activa según la app ([SIN_SESION] si no hay); [DESCONOCIDA] mientras este proceso no lo sepa
     * (el escaneo rápido abierto sin la app): entonces se lee de disco. Así no depende de que `sesion.txt` ya se haya
     * escrito o borrado, que pasa en otro hilo.
     */
    @Volatile private var sesionEnMemoria = DESCONOCIDA

    /** Lo dice la app cada vez que la sesión empieza, se recupera o termina. */
    fun sesionActiva(inicio: Long?) {
        sesionEnMemoria = inicio ?: SIN_SESION
    }

    fun archivo(contexto: Context): File = File(File(contexto.filesDir, "sesion"), "aperturas.csv")

    /** Registra [apertura] si no se registró ya. [origen]: [CsvAperturas.ORIGEN_APP] o [CsvAperturas.ORIGEN_RAPIDO]. */
    fun registrar(contexto: Context, origen: String, apertura: Apertura) {
        val clave = claveApertura(apertura)
        if (clave <= 0L || clave == ultima) return
        ultima = clave
        val app = contexto.applicationContext
        val hora = System.currentTimeMillis()
        val enMemoria = sesionEnMemoria
        if (enMemoria == SIN_SESION) return
        alcance.launch {
            val inicio = (if (enMemoria == DESCONOCIDA) AlmacenSesion(app).inicioActivo() else enMemoria) ?: return@launch
            try {
                val a = archivo(app)
                a.parentFile?.mkdirs()
                val texto = CsvAperturas.renglon(inicio, hora, origen, apertura, MedidorAndroid.equipo())
                if (!a.exists() || a.length() == 0L) a.writeText(CsvAperturas.encabezado() + texto) else a.appendText(texto)
            } catch (e: IOException) {
                Log.w(ETIQUETA, "No se pudo registrar la apertura", e)
            }
        }
    }

    /** Borra el archivo (al empezar o terminar una sesión), en orden con lo que esté por escribirse. */
    fun borrar(contexto: Context) {
        val app = contexto.applicationContext
        alcance.launch { archivo(app).delete() }
    }

    /** El contenido para exportarlo; null si no hay. En el mismo hilo que escribe, así no se lee a medias. */
    suspend fun leer(contexto: Context): String? =
        withContext(alcance.coroutineContext) { archivo(contexto).takeIf { it.exists() }?.readText() }

    private const val ETIQUETA = "RegistroAperturas"
    private const val DESCONOCIDA = -1L
    private const val SIN_SESION = -2L
}
