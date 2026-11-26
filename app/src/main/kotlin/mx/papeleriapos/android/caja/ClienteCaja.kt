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

    /** `GET /api/cajas/{id}/para-mandar`: las cajas a las que se puede mandar el ticket. */
    suspend fun paraMandar(direccion: Direccion, llave: String, cajaId: String): List<CajaParaMandarDto> =
        pedir(direccion, "api/cajas/$cajaId/para-mandar", llave, msRespuesta)

    /** `POST /api/cajas/{id}/mandar`: el servicio espera a que la caja lo guarde (hasta 10 s), así que se le dan [MS_MANDAR]. */
    suspend fun mandar(direccion: Direccion, llave: String, cajaId: String, solicitud: SolicitudMandarTicket): TicketMandadoDto =
        pedir(direccion, "api/cajas/$cajaId/mandar", llave, MS_MANDAR, cuerpo = JsonDelPos.encodeToString(SolicitudMandarTicket.serializer(), solicitud))

    @OptIn(ExperimentalSerializationApi::class)
    /** `POST /api/cajas/{id}/cotizar`: lo que costaría el ticket, sin cobrarlo. */
    suspend fun cotizar(direccion: Direccion, llave: String, cajaId: String, solicitud: SolicitudCotizar): CotizacionDto =
        pedir(direccion, "api/cajas/$cajaId/cotizar", llave, msRespuesta, cuerpo = JsonDelPos.encodeToString(SolicitudCotizar.serializer(), solicitud))

    /** `POST /api/cajas/{id}/cobrar`: en el piloto el servicio espera a eleventa, así que se le dan [MS_COBRAR]. */
    suspend fun cobrar(direccion: Direccion, llave: String, cajaId: String, solicitud: SolicitudCobrar): VentaCobradaDto =
        pedir(direccion, "api/cajas/$cajaId/cobrar", llave, MS_COBRAR, cuerpo = JsonDelPos.encodeToString(SolicitudCobrar.serializer(), solicitud))

    // ---- La foto del producto (paso 12) ----

    /** `PUT /api/fotos/{varianteId}`: el servicio las prepara de una en una y cada una espera su turno hasta 30 s. */
    suspend fun ponerFoto(direccion: Direccion, llave: String, varianteId: String, solicitud: SolicitudFoto): FotoGuardadaDto =
        pedir(direccion, "api/fotos/$varianteId", llave, MS_FOTO, cuerpo = JsonDelPos.encodeToString(SolicitudFoto.serializer(), solicitud), metodo = "PUT")

    /** `POST /api/fotos/{varianteId}/quitar` con `{}`: contesta 204 sin cuerpo, también si no tenía foto. */
    suspend fun quitarFoto(direccion: Direccion, llave: String, varianteId: String) = withContext(Dispatchers.IO) {
        val peticion = Request.Builder().url(direccion.url + "api/fotos/$varianteId/quitar").header(ENCABEZADO_LLAVE, llave).post("{}".toRequestBody(JSON)).build()
        conTiempo(msRespuesta).newCall(peticion).execute().use { if (!it.isSuccessful) throw errorDe(it) }
    }

    /** `GET /api/fotos/{varianteId}`: el JPEG guardado (unos 20 KB); null si ese producto no tiene foto (404). */
    suspend fun bajarFoto(direccion: Direccion, llave: String, varianteId: String): ByteArray? = withContext(Dispatchers.IO) {
        val peticion = Request.Builder().url(direccion.url + "api/fotos/$varianteId").header(ENCABEZADO_LLAVE, llave).build()
        conTiempo(msRespuesta).newCall(peticion).execute().use { respuesta ->
            if (respuesta.code == 404) return@use null
            if (!respuesta.isSuccessful) throw errorDe(respuesta)
            val cuerpo = respuesta.body ?: throw IOException("Sin respuesta")
            // Una foto guardada pesa unos 20 KB: más de 1 MB no es una foto del POS.
            if (cuerpo.contentLength() > MAXIMO_FOTO) throw IOException("La foto pesa demasiado")
            cuerpo.source().use { fuente ->
                if (fuente.request(MAXIMO_FOTO + 1)) throw IOException("La foto pesa demasiado")
                fuente.readByteArray()
            }
        }
    }

    private fun conTiempo(msLeer: Long): OkHttpClient =
        base.newBuilder().readTimeout(msLeer, TimeUnit.MILLISECONDS).callTimeout(msLeer + MS_CONEXION, TimeUnit.MILLISECONDS).build()

    private suspend inline fun <reified T> pedir(direccion: Direccion, ruta: String, llave: String?, msLeer: Long, cuerpo: String? = null, metodo: String = "POST"): T =
        withContext(Dispatchers.IO) {
            val peticion = Request.Builder().url(direccion.url + ruta).apply {
                if (llave != null) header(ENCABEZADO_LLAVE, llave)
                if (cuerpo != null) method(metodo, cuerpo.toRequestBody(JSON))
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
        const val MS_MANDAR = 15_000L

        /** Subir una foto: hasta 30 s de fila en el servicio, más lo que tarde en llegar por el Wi-Fi. */
        const val MS_FOTO = 45_000L
        const val MAXIMO_FOTO = 1_000_000L

        /** El servicio contesta el cobro en hasta unos 15 s (docs/celular-como-caja.md, paso 10); un poco más de margen. */
        const val MS_COBRAR = 20_000L

        /** Un solo cliente por proceso (sus hilos y su piscina de conexiones); cada llamada ajusta su tiempo de lectura. */
        fun clienteBase(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(MS_CONEXION, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .build()
    }
}
