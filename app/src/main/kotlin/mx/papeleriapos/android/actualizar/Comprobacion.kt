package mx.papeleriapos.android.actualizar

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/*
 * Lo que se revisa del APK ya bajado, antes de dárselo a Android: que sea esta misma app, más nueva que la instalada, la
 * versión que GitHub dijo y con la misma firma. Android vuelve a revisar el paquete y la firma al instalar; aquí se
 * revisa antes para no llegar a su cuadro con un archivo que no es, y para decir en español por qué.
 */

/** Lo que identifica a un APK: su paquete, su código de versión y la huella sha256 de cada certificado que lo firma. */
data class DatosDeApk(val paquete: String, val codigo: Long, val firmas: Set<String>)

/** Revisa el archivo bajado. Devuelve por qué no se instala, o null si todo cuadra. */
fun interface ComprobadorDeApk {
    fun revisar(archivo: File, esperada: Version): String?
}

object Comprobacion {
    const val NO_ES_UN_APK = "Lo que se bajó no es un APK que Android pueda leer: se borró y no se instala."
    const val OTRA_FIRMA = "El APK bajado no está firmado con la misma llave que la app instalada: se borró y no se instala."
    const val SIN_FIRMA = "No se pudo leer la firma del APK bajado: se borró y no se instala."

    /**
     * Por qué [bajado] no se instala sobre [instalada], o null si se puede: el mismo paquete, el código de versión de
     * [esperada] (el que GitHub publicó), mayor que el instalado, y exactamente los mismos certificados de firma.
     */
    fun razonDeRechazo(instalada: DatosDeApk, bajado: DatosDeApk?, esperada: Version): String? = when {
        bajado == null -> NO_ES_UN_APK
        bajado.paquete != instalada.paquete -> "El APK bajado es de otra app (${bajado.paquete}): se borró y no se instala."
        bajado.codigo != esperada.codigo -> "El APK bajado no es la versión $esperada que GitHub publicó: se borró y no se instala."
        bajado.codigo <= instalada.codigo -> "El APK bajado no es más nuevo que la app instalada: se borró y no se instala."
        bajado.firmas.isEmpty() || instalada.firmas.isEmpty() -> SIN_FIRMA
        bajado.firmas != instalada.firmas -> OTRA_FIRMA
        else -> null
    }
}

/** El de verdad: lee el APK bajado y la app instalada con el `PackageManager`. */
class ComprobadorAndroid(private val contexto: Context) : ComprobadorDeApk {
    override fun revisar(archivo: File, esperada: Version): String? {
        val pm = contexto.packageManager
        val instalada = datos(leerInstalada(pm, firmasNuevas = true)).let {
            if (it != null && it.firmas.isNotEmpty()) it else datos(leerInstalada(pm, firmasNuevas = false))
        } ?: return Comprobacion.SIN_FIRMA
        // En algunas versiones de Android, de un archivo no salen los certificados con la bandera nueva: se prueba la de antes.
        val bajado = datos(leerArchivo(pm, archivo, firmasNuevas = true)).let {
            if (it != null && it.firmas.isNotEmpty()) it else datos(leerArchivo(pm, archivo, firmasNuevas = false)) ?: it
        }
        return Comprobacion.razonDeRechazo(instalada, bajado, esperada)
    }

    private fun leerInstalada(pm: PackageManager, firmasNuevas: Boolean): PackageInfo? = try {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(contexto.packageName, banderas(firmasNuevas))
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun leerArchivo(pm: PackageManager, archivo: File, firmasNuevas: Boolean): PackageInfo? =
        @Suppress("DEPRECATION")
        pm.getPackageArchiveInfo(archivo.absolutePath, banderas(firmasNuevas))

    @Suppress("DEPRECATION")
    private fun banderas(firmasNuevas: Boolean): Int =
        if (firmasNuevas && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    private fun datos(info: PackageInfo?): DatosDeApk? {
        info ?: return null
        val certificados = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners?.forEach { add(it.toByteArray()) }
            @Suppress("DEPRECATION")
            if (isEmpty()) info.signatures?.forEach { add(it.toByteArray()) }
        }
        return DatosDeApk(info.packageName.orEmpty(), PackageInfoCompat.getLongVersionCode(info), certificados.map(::huella).toSet())
    }

    private fun huella(certificado: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificado).joinToString("") { "%02x".format(it) }
}
