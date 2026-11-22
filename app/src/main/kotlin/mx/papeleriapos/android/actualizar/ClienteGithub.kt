package mx.papeleriapos.android.actualizar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** De dónde salen las versiones: GitHub ([ClienteGithub]) o, en las pruebas, una de mentira. */
interface FuenteDeVersiones {
    /** La última versión publicada. Lanza [ErrorDeActualizacion] (con qué decir) o [IOException] (sin Internet). */
    suspend fun ultima(): VersionPublicada

    /**
     * Baja [apk] a [destino] y comprueba su tamaño y su sha256 (si GitHub lo dio); [alAvanzar] recibe los bytes que van.
     * Si algo falla o no cuadra, [destino] no queda en el disco.
     */
    suspend fun bajar(apk: ApkPublicado, destino: File, alAvanzar: (Long) -> Unit = {})
}

/**
 * La única salida de la app fuera de la red de la tienda (docs/plan.md, «Red y privacidad»), y solo cuando el dueño toca
 * «Buscar versión nueva» o «Descargar e instalar»: lee la última versión publicada del repositorio (que es público: sin
 * llave ni cuenta) y baja su APK.
 *
 * - **Solo HTTPS y solo a GitHub** ([esDeGithub]): `api.github.com` para la lista, `github.com` para el APK y los dos
 *   hosts a los que GitHub redirige la descarga. Las redirecciones se siguen a mano, una por una, y cada salto pasa por
 *   la misma regla: a cualquier otro host, o a `http://`, no se le pide nada.
 * - **No manda nada del celular ni de la tienda:** sin cookies, sin llaves, sin cuerpo; solo el `User-Agent` de OkHttp
 *   (GitHub exige uno) y el `Accept` de su API. Es un cliente aparte del de la caja ([mx.papeleriapos.android.caja.ClienteCaja]),
 *   que sigue aceptando solo direcciones privadas: no comparten conexiones ni configuración.
 *
 * @param permitida La regla de a dónde se puede pedir; las pruebas la cambian para hablar con su servidor local.
 */
class ClienteGithub(
    private val base: OkHttpClient = clienteBase(),
    private val urlUltima: String = URL_ULTIMA,
    private val permitida: (HttpUrl) -> Boolean = ::esDeGithub,
) : FuenteDeVersiones {
    override suspend fun ultima(): VersionPublicada = withContext(Dispatchers.IO) {
        pedir(urlUltima, "application/vnd.github+json").use { r ->
            when (r.code) {
                200 -> Versiones.leer(leerTexto(r))
                404 -> throw ErrorDeActualizacion("Todavía no hay ninguna versión publicada en GitHub.")
                403, 429 -> throw ErrorDeActualizacion("GitHub no deja preguntar tan seguido desde esta red. Espera unos minutos y vuelve a buscar.")
                else -> throw ErrorDeActualizacion("GitHub contestó con un error (HTTP ${r.code}). Vuelve a buscar más tarde.")
            }
        }
    }

    override suspend fun bajar(apk: ApkPublicado, destino: File, alAvanzar: (Long) -> Unit) = withContext(Dispatchers.IO) {
        if (apk.tamano <= 0 || apk.tamano > MAXIMO_APK) throw ErrorDeActualizacion("El APK publicado dice pesar ${Versiones.megas(apk.tamano)}: no se baja.")
        destino.parentFile?.mkdirs()
        try {
            val sha = pedir(apk.url, "application/octet-stream").use { r ->
                if (r.code != 200) throw ErrorDeActualizacion("GitHub no entregó el APK (HTTP ${r.code}). Vuelve a intentar más tarde.")
                guardar(r, destino, apk.tamano, alAvanzar)
            }
            if (destino.length() != apk.tamano) {
                throw ErrorDeActualizacion("La descarga llegó incompleta (${destino.length()} de ${apk.tamano} bytes). Vuelve a intentar.")
            }
            if (apk.sha256 != null && sha != apk.sha256) {
                throw ErrorDeActualizacion("Lo que se bajó no es el archivo que GitHub publicó (su huella sha256 no cuadra): se borró y no se instala.")
            }
        } catch (e: Throwable) {
            destino.delete()
            throw e
        }
    }

    /** Escribe el cuerpo en [destino] y devuelve su sha256; se detiene si llega más de lo publicado o si se cancela. */
    private suspend fun guardar(r: Response, destino: File, esperado: Long, alAvanzar: (Long) -> Unit): String {
        val resumen = MessageDigest.getInstance("SHA-256")
        val bloque = ByteArray(64 * 1024)
        var van = 0L
        var avisado = 0L
        val cuerpo = r.body ?: throw IOException("Sin cuerpo")
        cuerpo.byteStream().use { entrada ->
            destino.outputStream().use { salida ->
                while (true) {
                    coroutineContext.ensureActive()
                    val n = entrada.read(bloque)
                    if (n < 0) break
                    van += n
                    if (van > esperado) throw ErrorDeActualizacion("La descarga trae más de lo que GitHub publicó: se borró y no se instala.")
                    salida.write(bloque, 0, n)
                    resumen.update(bloque, 0, n)
                    if (van - avisado >= PASO_DEL_AVANCE) {
                        avisado = van
                        alAvanzar(van)
                    }
                }
            }
        }
        alAvanzar(van)
        return resumen.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * `GET` de [url], siguiendo las redirecciones a mano (hasta [MAXIMO_DE_SALTOS]): cada dirección, la primera y la de cada
     * salto, tiene que pasar [permitida] antes de pedirle nada. Devuelve la respuesta final, sin leer.
     */
    private fun pedir(url: String, acepta: String): Response {
        var destino = url.toHttpUrlOrNull() ?: throw ErrorDeActualizacion(FUERA_DE_GITHUB)
        repeat(MAXIMO_DE_SALTOS + 1) {
            if (!permitida(destino)) throw ErrorDeActualizacion(FUERA_DE_GITHUB)
            val r = base.newCall(Request.Builder().url(destino).header("Accept", acepta).build()).execute()
            if (r.code !in REDIRECCIONES) return r
            val siguiente = r.use { it.header("Location")?.let(destino::resolve) }
            destino = siguiente ?: throw ErrorDeActualizacion("GitHub mandó a otra dirección sin decir cuál. Vuelve a intentar más tarde.")
        }
        throw ErrorDeActualizacion("GitHub mandó de una dirección a otra demasiadas veces. Vuelve a intentar más tarde.")
    }

    private fun leerTexto(r: Response): String {
        val fuente = r.body?.source() ?: throw IOException("Sin cuerpo")
        // La lista de una versión pesa unos KB: si no cabe en el tope, no es lo que se pidió.
        if (fuente.request(MAXIMO_JSON + 1)) throw ErrorDeActualizacion(Versiones.NO_SE_ENTIENDE)
        return fuente.readUtf8()
    }

    companion object {
        /** La última versión publicada (no borrador ni de prueba) del repositorio de la app, que es público. */
        const val URL_ULTIMA = "https://api.github.com/repos/Marioalr/papeleria-pos-android/releases/latest"

        /** A dónde puede salir la app: la API, la página de la versión y los dos hosts que entregan los archivos. */
        val HOSTS = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

        const val FUERA_DE_GITHUB = "La descarga quiso salir a una dirección que no es de GitHub: no se pidió ni se bajó nada."

        private val REDIRECCIONES = setOf(301, 302, 303, 307, 308)
        private const val MAXIMO_DE_SALTOS = 5
        private const val MAXIMO_JSON = 2L * 1024 * 1024
        private const val PASO_DEL_AVANCE = 256L * 1024

        /** El APK pesa unos 15 MB; más que esto no es la app. */
        const val MAXIMO_APK = 200L * 1024 * 1024

        /** Solo HTTPS, en su puerto, y solo a los hosts de [HOSTS] (el nombre exacto: ningún otro subdominio). */
        fun esDeGithub(url: HttpUrl): Boolean = url.isHttps && url.port == 443 && url.host in HOSTS

        /**
         * Sin seguir redirecciones por su cuenta (las sigue [pedir], revisando cada una), sin cookies (OkHttp no las guarda
         * si no se le da dónde) y sin caché en disco.
         */
        fun clienteBase(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }
}
