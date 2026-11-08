package mx.papeleriapos.android.caja

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromStream
import mx.papeleriapos.android.copia.Copia
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** El servicio contestó que no: su mensaje está escrito para enseñarse tal cual; [codigo] dice qué fue (puede venir vacío). */
class ErrorDelPos(val http: Int, mensaje: String, val codigo: String? = null) : Exception(mensaje) {
    /** 401 «caja-sin-alta»: el dueño le quitó la llave a este celular (o la caja principal es otra base). */
    val sinAlta: Boolean get() = http == 401 && codigo == CAJA_SIN_ALTA

    companion object {
        const val CAJA_SIN_ALTA = "caja-sin-alta"
        const val UNA_SOLA_CAJA = "una-sola-caja"
    }
}

/**
 * Las llamadas a la API de la caja principal (docs/celular-como-caja.md, «La secuencia de la app»): HTTP con JSON, la
 * llave en el encabezado `X-Caja-Llave`. Cada una bloquea en el hilo de entrada y salida y lanza [ErrorDelPos] si el
 * servicio contestó que no, o [IOException] si no contestó (sin red, otra IP, tiempo agotado). Nada de lo que viaja se
 * escribe en el registro.
 *
 * @param msRespuesta Lo que se espera a que empiece a contestar en las llamadas cortas: con esto se decide si la red
 * está buena, lenta o caída ([Conexion]).
 */
class ClienteCaja(private val base: OkHttpClient = clienteBase(), private val msRespuesta: Long = MS_RESPUESTA) {
    /** `GET /api/estado`: sin llave, para saber si es el POS; con la llave y [cajaId], el latido. */
    suspend fun estado(direccion: Direccion, llave: String? = null, cajaId: String? = null): EstadoServicioDto {
        val ruta = if (cajaId != null) "api/estado?caja=$cajaId" else "api/estado"
        return pedir(direccion, ruta, llave, msRespuesta)
    }

    /** `POST /api/alta/opciones`: las sucursales con sus cajas, solo con el usuario y el PIN de quien da de alta. */
    suspend fun opcionesDeAlta(direccion: Direccion, usuario: String, pin: String): List<SucursalAltaDto> =
        pedir(direccion, "api/alta/opciones", null, MS_ALTA, cuerpo = JsonDelPos.encodeToString(SolicitudCredenciales.serializer(), SolicitudCredenciales(usuario, pin)))

    /** `POST /api/alta`: la caja nueva con su llave. */
    suspend fun darDeAlta(direccion: Direccion, solicitud: SolicitudAltaCaja): AltaCajaDto =
        pedir(direccion, "api/alta", null, MS_ALTA, cuerpo = JsonDelPos.encodeToString(SolicitudAltaCaja.serializer(), solicitud))

    /** `GET /api/identidad`: la prueba de que ese servicio dio de alta a esta caja ([Identidad]); sin llave. */
    suspend fun identidad(direccion: Direccion, cajaId: String, reto: String): IdentidadServicioDto =
        pedir(direccion, "api/identidad?caja=$cajaId&reto=$reto", null, msRespuesta)

    /** `GET /api/cajas/{id}/paquete`: la copia completa, que pesa varios MB. */
    suspend fun paquete(direccion: Direccion, llave: String, cajaId: String): PaqueteDto =
        pedir(direccion, "api/cajas/$cajaId/paquete", llave, MS_PAQUETE)

    /** `GET /api/cajas/{id}/cambios`: lo que cambió desde las versiones de [copia]. */
    suspend fun cambios(direccion: Direccion, llave: String, copia: Copia): CambiosDto {
        val ruta = "api/cajas/${copia.caja.id}/cambios?desde=${copia.version}&existencias=${copia.versionExistencias}" +
            "&base=${copia.base.orEmpty()}&formato=${copia.formato ?: 0}&vendidos=${copia.versionVendidos}&elegidos=${copia.versionElegidos}" +
            (copia.huellaPromociones?.let { "&promociones=$it" } ?: "")
        return pedir(direccion, ruta, llave, MS_CAMBIOS)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend inline fun <reified T> pedir(direccion: Direccion, ruta: String, llave: String?, msLeer: Long, cuerpo: String? = null): T =
        withContext(Dispatchers.IO) {
            val peticion = Request.Builder().url(direccion.url + ruta).apply {
                if (llave != null) header(ENCABEZADO_LLAVE, llave)
                if (cuerpo != null) post(cuerpo.toRequestBody(JSON))
            }.build()
            val cliente = base.newBuilder().readTimeout(msLeer, TimeUnit.MILLISECONDS).callTimeout(msLeer + MS_CONEXION, TimeUnit.MILLISECONDS).build()
            cliente.newCall(peticion).execute().use { respuesta ->
                if (!respuesta.isSuccessful) throw errorDe(respuesta)
                val flujo = respuesta.body?.byteStream() ?: throw IOException("Sin respuesta")
                try {
                    JsonDelPos.decodeFromStream<T>(flujo)
                } catch (e: SerializationException) {
                    throw IOException("No se entendió lo que contestó el servicio", e)
                } catch (e: IllegalArgumentException) {
                    throw IOException("No se entendió lo que contestó el servicio", e)
                }
            }
        }

    private fun errorDe(respuesta: Response): ErrorDelPos {
        val texto = try {
            respuesta.body?.string().orEmpty()
        } catch (e: IOException) {
            ""
        }
        val error = try {
            JsonDelPos.decodeFromString(ErrorDto.serializer(), texto)
        } catch (e: Exception) {
            ErrorDto()
        }
        val mensaje = error.mensaje.ifBlank {
            when (respuesta.code) {
                401 -> "El servicio no reconoce la llave de este celular."
                403 -> "El servicio no lo permite."
                404 -> "No está."
                else -> "El servicio contestó ${respuesta.code}."
            }
        }
        return ErrorDelPos(respuesta.code, mensaje, error.codigo?.ifBlank { null })
    }

    companion object {
        const val ENCABEZADO_LLAVE = "X-Caja-Llave"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Conectar: en la red de la tienda es cosa de milisegundos; más que esto es que no está. */
        const val MS_CONEXION = 3_000L

        /** Las llamadas cortas (el latido, la identidad): más de esto y la red está lenta ([Conexion.LENTA_MS] la mide aparte). */
        const val MS_RESPUESTA = 5_000L
        const val MS_ALTA = 15_000L
        const val MS_CAMBIOS = 10_000L
        const val MS_PAQUETE = 60_000L

        /** Un solo cliente por proceso (sus hilos y su piscina de conexiones); cada llamada ajusta su tiempo de lectura. */
        fun clienteBase(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(MS_CONEXION, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .build()
    }
}
