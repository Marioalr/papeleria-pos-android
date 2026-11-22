package mx.papeleriapos.android.actualizar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/*
 * Lo que la app lee de GitHub para actualizarse (docs/plan.md, «Versiones»): la última versión publicada del repositorio,
 * que GitHub Actions crea al etiquetar `vX.Y.Z` con el APK de release firmado adjunto (`escaner-<versión>-<commit>.apk`) y
 * las notas de CAMBIOS.md. Aquí no hay Android ni red: solo leer ese JSON, elegir el APK y comparar versiones.
 */

/** Lo que no se pudo y por qué, escrito para enseñarse tal cual en la pantalla. */
class ErrorDeActualizacion(mensaje: String) : Exception(mensaje)

/** `MAYOR.MENOR.PARCHE`, como `versionApp` en `app/build.gradle.kts`. */
data class Version(val mayor: Int, val menor: Int, val parche: Int) : Comparable<Version> {
    /** El código de versión de Android que le toca: el mismo cálculo que el build (`MAYOR·10000 + MENOR·100 + PARCHE`). */
    val codigo: Long get() = mayor * 10_000L + menor * 100L + parche

    override fun compareTo(other: Version): Int = compareValuesBy(this, other, Version::mayor, Version::menor, Version::parche)

    override fun toString(): String = "$mayor.$menor.$parche"

    companion object {
        private val FORMA = Regex("""v?(\d{1,4})\.(\d{1,2})\.(\d{1,2})""")

        /** «0.5.1» o la etiqueta «v0.5.1»; null si no es una versión (una etiqueta de otra cosa, «v0.5.1-rc1»…). */
        fun de(texto: String?): Version? {
            val (a, b, c) = FORMA.matchEntire(texto?.trim().orEmpty())?.destructured ?: return null
            return Version(a.toInt(), b.toInt(), c.toInt())
        }
    }
}

/** El APK de una versión publicada. [sha256], en hexadecimal y minúsculas, solo si GitHub lo da (`digest`). */
data class ApkPublicado(val nombre: String, val tamano: Long, val url: String, val sha256: String?)

/** La última versión publicada, lista para enseñarse: [notas] es la sección de CAMBIOS.md, ya sin el Markdown. */
data class VersionPublicada(
    val version: Version,
    val notas: List<String>,
    /** Cuándo se publicó (ms del reloj de pared), o null si GitHub no lo dijo. */
    val publicada: Long?,
    val apk: ApkPublicado,
)

/** `GET /repos/{dueño}/{repo}/releases/latest`: solo los campos que la app usa. */
@Serializable
data class VersionGithubDto(
    @SerialName("tag_name") val etiqueta: String = "",
    @SerialName("body") val notas: String? = null,
    @SerialName("published_at") val publicada: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ArchivoGithubDto> = emptyList(),
)

@Serializable
data class ArchivoGithubDto(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val url: String = "",
    /** «sha256:…» (GitHub lo da desde 2025); sin él, la descarga se comprueba solo por tamaño, paquete y firma. */
    val digest: String? = null,
)

object Versiones {
    private val JSON = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    const val NO_SE_ENTIENDE = "GitHub contestó algo que la app no entiende. Vuelve a buscar más tarde."

    /**
     * La versión publicada que dice [json]. Lanza [ErrorDeActualizacion] si no se entiende, si la etiqueta no es una
     * versión o si no trae el APK de release.
     */
    fun leer(json: String): VersionPublicada {
        val dto = try {
            JSON.decodeFromString<VersionGithubDto>(json)
        } catch (e: SerializationException) {
            throw ErrorDeActualizacion(NO_SE_ENTIENDE)
        } catch (e: IllegalArgumentException) {
            throw ErrorDeActualizacion(NO_SE_ENTIENDE)
        }
        val version = Version.de(dto.etiqueta) ?: throw ErrorDeActualizacion(NO_SE_ENTIENDE)
        val apk = elegirApk(dto.assets, version)
            ?: throw ErrorDeActualizacion("La versión $version está publicada sin el APK para instalar. Vuelve a buscar más tarde.")
        return VersionPublicada(version, notasLegibles(dto.notas), fecha(dto.publicada), apk)
    }

    /**
     * El APK de release de [version]: `escaner-<versión>-<commit>.apk`, el que firma GitHub con la llave del proyecto. Nunca
     * el `-depuracion` (lleva la llave de depuración de GitHub: Android no lo instalaría encima) ni un archivo con otro
     * nombre. null si no hay ninguno o si no trae de dónde bajarlo.
     */
    fun elegirApk(archivos: List<ArchivoGithubDto>, version: Version): ApkPublicado? {
        val forma = Regex("""escaner-${Regex.escape(version.toString())}-[0-9a-f]{4,40}\.apk""")
        val a = archivos.firstOrNull { forma.matches(it.name) && it.size > 0 && it.url.isNotBlank() } ?: return null
        return ApkPublicado(a.name, a.size, a.url, sha256De(a.digest))
    }

    /** «sha256:ABC…» → «abc…» (64 dígitos hexadecimales); null si no viene o es de otro algoritmo. */
    fun sha256De(digest: String?): String? {
        val hex = digest?.trim()?.lowercase(Locale.ROOT)?.removePrefix("sha256:") ?: return null
        return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    private val FECHA = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?Z""")

    /** «2026-11-21T21:04:36Z» → ms. A mano: `java.time` no existe antes de Android 8 y el minSdk es 24. */
    fun fecha(iso: String?): Long? {
        val m = FECHA.matchEntire(iso?.trim().orEmpty()) ?: return null
        val n = m.groupValues.drop(1).map { it.toInt() }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(n[0], n[1] - 1, n[2], n[3], n[4], n[5])
        }.timeInMillis
    }

    private val ENLACE = Regex("""\[([^\]]+)]\([^)]*\)""")

    /**
     * Las notas de la versión (la sección de CAMBIOS.md, en Markdown) como renglones para leer: cada párrafo y cada punto
     * de lista en un renglón (los puntos, con «• »; los de segundo nivel, con «– »), sin negritas, comillas de código ni
     * enlaces (queda su texto). Los renglones partidos a 120 columnas se vuelven a juntar.
     */
    fun notasLegibles(markdown: String?): List<String> {
        val renglones = mutableListOf<String>()
        var abierto = false
        for (linea in markdown.orEmpty().lines()) {
            val t = linea.trim()
            if (t.isEmpty()) {
                abierto = false
                continue
            }
            val punto = t.startsWith("- ") || t.startsWith("* ")
            val texto = limpiar(if (punto) t.drop(2) else t.trimStart('#', ' '))
            if (texto.isEmpty()) continue
            when {
                punto -> renglones += (if (linea.startsWith(" ")) "– " else "• ") + texto
                abierto && !t.startsWith("#") -> renglones[renglones.lastIndex] = renglones.last() + " " + texto
                else -> renglones += texto
            }
            abierto = true
        }
        return renglones
    }

    private fun limpiar(texto: String): String = ENLACE.replace(texto, "$1").replace("**", "").replace("`", "").trim()

    /** «15.4 MB», con punto decimal como el resto de la app. */
    fun megas(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
}
