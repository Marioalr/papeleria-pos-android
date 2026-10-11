package mx.papeleriapos.android.catalogo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.archivo.escribirAtomico
import mx.papeleriapos.escaner.nucleo.Catalogo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

/** De dónde salió el catálogo de prueba. */
enum class OrigenCatalogo { EJEMPLO, ARCHIVO }

/** El catálogo de prueba cargado, con lo que muestra Ajustes. */
data class EstadoCatalogo(
    val origen: OrigenCatalogo,
    /** El nombre del archivo que eligió el dueño; vacío con el de ejemplo. */
    val nombre: String,
    val catalogo: Catalogo,
    /** Lo que salió mal la última vez (archivo vacío, muy grande o ilegible); null si nada. */
    val aviso: String? = null,
    /** Copiando y leyendo un archivo nuevo: mientras, se sigue buscando en este. */
    val cargando: Boolean = false,
)

/**
 * El catálogo en la memoria privada de la app (`files/catalogo`): el archivo que eligió el dueño, copiado tal cual, y su
 * nombre. Sin él, el de ejemplo de los assets. Todo bloquea: lo llama [CatalogoPrueba] en su hilo. Lo que falla se
 * avisa en Ajustes ([EstadoCatalogo.aviso]) y no va al registro: ni el contenido ni el nombre del archivo.
 */
class AlmacenCatalogo(private val carpeta: File, private val abrirEjemplo: () -> InputStream) {
    private val archivo get() = File(carpeta, ARCHIVO)
    private val nombre get() = File(carpeta, NOMBRE)

    /** El que quedó cargado (el del dueño, si lo hay) o el de ejemplo. */
    fun leer(): EstadoCatalogo {
        if (!archivo.isFile) return ejemplo()
        return try {
            val catalogo = Catalogo.deTexto(decodificar(archivo.readBytes()))
            val n = if (nombre.isFile) nombre.readText().trim() else ""
            EstadoCatalogo(OrigenCatalogo.ARCHIVO, n.ifEmpty { "archivo cargado" }, catalogo)
        } catch (e: IOException) {
            ejemplo().copy(aviso = "No se pudo leer el archivo cargado: se usa el de ejemplo.")
        }
    }

    /**
     * Copia lo que da [abrir] (el archivo [nombreArchivo]) a la memoria de la app y lo carga. Si no se puede leer, pasa
     * de [TOPE_BYTES] o no trae códigos, queda [anterior] con un aviso.
     */
    fun copiar(nombreArchivo: String, abrir: () -> InputStream, anterior: EstadoCatalogo): EstadoCatalogo {
        val bytes = try {
            abrir().use { leerConTope(it) }
        } catch (e: Exception) {
            // IOException, SecurityException del proveedor del archivo…
            return anterior.copy(aviso = "No se pudo abrir el archivo.", cargando = false)
        } ?: return anterior.copy(aviso = "El archivo pasa de 8 MB: no parece una lista de códigos.", cargando = false)
        val catalogo = Catalogo.deTexto(decodificar(bytes))
        if (catalogo.tamano == 0) {
            return anterior.copy(aviso = "El archivo no trae códigos en la primera columna.", cargando = false)
        }
        return try {
            carpeta.mkdirs()
            archivo.escribirAtomico(bytes)
            nombre.escribirAtomico(nombreArchivo)
            EstadoCatalogo(OrigenCatalogo.ARCHIVO, nombreArchivo, catalogo)
        } catch (e: IOException) {
            anterior.copy(aviso = "No se pudo guardar el archivo en el celular.", cargando = false)
        }
    }

    /** Borra la copia del archivo del dueño y vuelve al de ejemplo. */
    fun olvidar(): EstadoCatalogo {
        archivo.delete()
        nombre.delete()
        return ejemplo()
    }

    private fun ejemplo(): EstadoCatalogo = try {
        EstadoCatalogo(OrigenCatalogo.EJEMPLO, "", Catalogo.deTexto(decodificar(abrirEjemplo().use { it.readBytes() })))
    } catch (e: IOException) {
        EstadoCatalogo(OrigenCatalogo.EJEMPLO, "", Catalogo.VACIO, aviso = "No se pudo leer el catálogo de ejemplo.")
    }

    /** Hasta [TOPE_BYTES]; null si pasa. */
    private fun leerConTope(entrada: InputStream): ByteArray? {
        val salida = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = entrada.read(buffer)
            if (n < 0) break
            if (salida.size() + n > TOPE_BYTES) return null
            salida.write(buffer, 0, n)
        }
        return salida.toByteArray()
    }

    companion object {
        /** Un catálogo de decenas de miles de productos pesa ~1 MB; más de esto no es una lista de códigos. */
        const val TOPE_BYTES = 8 * 1024 * 1024

        private const val ARCHIVO = "catalogo.txt"
        private const val NOMBRE = "nombre.txt"

        /**
         * UTF-8 (con o sin BOM, que quita el catálogo); si no lo es, Windows-1252, como guarda Excel un «CSV» en
         * Windows. Los códigos son ASCII: importa para reconocer un encabezado como «Código».
         */
        fun decodificar(bytes: ByteArray): String {
            val utf8 = String(bytes, Charsets.UTF_8)
            return if ('�' in utf8) String(bytes, Charset.forName("windows-1252")) else utf8
        }
    }
}

/**
 * El catálogo de prueba del proceso (docs/escaner.md, «Búsqueda en el catálogo»). Se carga perezoso, en su propio hilo:
 * con la primera lectura o al abrir Ajustes, nunca en la apertura de la cámara. Lo usan el ViewModel (cada lectura de la
 * sesión de prueba) y el panel de lecturas (la marca «no está en el catálogo»).
 *
 * El archivo del dueño puede traer códigos reales: se copia a la memoria privada de la app (sin respaldo en la nube:
 * `allowBackup="false"`), no va al registro ni se exporta. A los CSV de la sesión solo llega la clave que coincidió con
 * cada lectura.
 */
object CatalogoPrueba {
    const val ARCHIVO_EJEMPLO = "catalogo-ejemplo.csv"
    private const val CARPETA = "catalogo"

    private val _estado = MutableStateFlow<EstadoCatalogo?>(null)
    /** null mientras no se ha cargado. */
    val estado: StateFlow<EstadoCatalogo?> = _estado.asStateFlow()

    /**
     * Un solo hilo: cargar, copiar y volver al de ejemplo van en orden. Perezosos: el panel de lecturas lee [estado] al
     * dibujarse y eso no debe crear nada más.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val hilo by lazy { Dispatchers.IO.limitedParallelism(1) }
    private val alcance by lazy { CoroutineScope(SupervisorJob() + hilo) }

    @Volatile private var almacen: AlmacenCatalogo? = null

    /** Empieza a cargarlo si falta, sin esperar. */
    fun precargar(contexto: Context) {
        if (_estado.value != null) return
        val app = contexto.applicationContext
        alcance.launch { cargarSiFalta(app) }
    }

    /** El catálogo; si todavía no está, lo carga en su hilo y espera (solo la primera vez). */
    suspend fun listo(contexto: Context): Catalogo {
        _estado.value?.let { return it.catalogo }
        val app = contexto.applicationContext
        return withContext(hilo) { cargarSiFalta(app) }.catalogo
    }

    /** Copia el archivo que eligió el dueño ([uri], del selector del sistema) y lo usa desde ahora. */
    fun cargarArchivo(contexto: Context, uri: Uri) {
        val app = contexto.applicationContext
        alcance.launch {
            val anterior = cargarSiFalta(app).copy(aviso = null, cargando = false)
            _estado.value = anterior.copy(cargando = true)
            val abrir = { app.contentResolver.openInputStream(uri) ?: throw IOException("Sin contenido") }
            _estado.value = almacen(app).copiar(nombreDe(app, uri), abrir, anterior)
        }
    }

    /** Vuelve al de ejemplo y borra la copia del archivo del dueño. */
    fun usarEjemplo(contexto: Context) {
        val app = contexto.applicationContext
        alcance.launch { _estado.value = almacen(app).olvidar() }
    }

    private fun cargarSiFalta(app: Context): EstadoCatalogo = _estado.value ?: almacen(app).leer().also { _estado.value = it }

    private fun almacen(app: Context): AlmacenCatalogo = almacen ?: AlmacenCatalogo(File(app.filesDir, CARPETA)) {
        app.assets.open(ARCHIVO_EJEMPLO)
    }.also { almacen = it }

    /** El nombre que muestra el selector («productos.csv»); si el proveedor no lo da, uno genérico. */
    private fun nombreDe(app: Context, uri: Uri): String = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: RuntimeException) {
        null
    }.orEmpty().trim().ifEmpty { "archivo elegido" }
}
