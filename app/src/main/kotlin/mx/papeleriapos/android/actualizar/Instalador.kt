package mx.papeleriapos.android.actualizar

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import java.io.File
import java.io.IOException

/*
 * Instalar el APK ya comprobado (docs/plan.md, «Versiones»): con el instalador de paquetes de Android, que enseña su
 * cuadro «¿Quieres actualizar esta app?» y vuelve a revisar el paquete y la firma. Los datos de la app (el alta, la
 * copia, el ticket) se conservan: es una actualización sobre la misma firma.
 */

/** Quien le entrega el APK a Android. */
interface Instalador {
    /** Si Android deja que esta app instale: «Instalar apps desconocidas» concedido para ella (desde Android 8). */
    fun puedeInstalar(): Boolean

    /** Le entrega [archivo] a Android, que contesta después ([ReceptorDeInstalacion]). Lanza [IOException] si no pudo. */
    fun instalar(archivo: File)
}

/** Lo que Android contestó de la instalación. */
enum class RespuestaDeAndroid { INSTALADA, CANCELADA, FALLO }

class InstaladorAndroid(private val contexto: Context) : Instalador {
    override fun puedeInstalar(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || contexto.packageManager.canRequestPackageInstalls()

    override fun instalar(archivo: File) {
        val instalador = contexto.packageManager.packageInstaller
        val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(contexto.packageName)
            setSize(archivo.length())
        }
        val id = try {
            instalador.createSession(parametros)
        } catch (e: RuntimeException) {
            throw IOException("Android no abrió la instalación", e)
        }
        try {
            instalador.openSession(id).use { sesion ->
                sesion.openWrite(NOMBRE_EN_LA_SESION, 0, archivo.length()).use { salida ->
                    archivo.inputStream().use { it.copyTo(salida) }
                    sesion.fsync(salida)
                }
                // Android contesta por este aviso, que solo recibe el receptor de la app (explícito y no exportado). Lo
                // llena el sistema con el estado y, si hace falta, con su cuadro de confirmación: por eso es mutable.
                val aviso = Intent(contexto, ReceptorDeInstalacion::class.java).setAction(ACCION).setPackage(contexto.packageName)
                val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pendiente = PendingIntent.getBroadcast(contexto, id, aviso, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
                sesion.commit(pendiente.intentSender)
            }
        } catch (e: IOException) {
            abandonar(instalador, id)
            throw e
        } catch (e: RuntimeException) {
            abandonar(instalador, id)
            throw IOException("Android no aceptó el APK", e)
        }
    }

    private fun abandonar(instalador: PackageInstaller, id: Int) {
        try {
            instalador.abandonSession(id)
        } catch (e: RuntimeException) {
            // Ya no existe: nada que abandonar.
        }
    }

    companion object {
        const val ACCION = "mx.papeleriapos.android.actualizar.RESPUESTA"
        private const val NOMBRE_EN_LA_SESION = "actualizacion.apk"

        /**
         * El ajuste de Android donde se le permite a esta app instalar («Instalar apps desconocidas»), o null antes de
         * Android 8, donde ese permiso no es por app.
         */
        fun ajusteParaPermitir(contexto: Context): Intent? =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) null
            else Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${contexto.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * Lo que Android contesta de la instalación ([InstaladorAndroid.instalar]). No exportado: solo llega el aviso que la app
 * misma le dio al instalador. Si Android pide la confirmación de la persona, abre su cuadro (la app está al frente: se
 * acaba de tocar «Descargar e instalar»); lo demás se le pasa al [Actualizador]. Si se instaló, Android cierra la app
 * para cambiarla y esta respuesta puede ni llegar.
 */
class ReceptorDeInstalacion : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != InstaladorAndroid.ACCION) return
        val actualizador = Actualizador.de(contexto)
        when (val estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val cuadro = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                try {
                    contexto.startActivity(checkNotNull(cuadro).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: RuntimeException) {
                    actualizador.alContestarAndroid(RespuestaDeAndroid.FALLO, "Android no pudo enseñar su cuadro para confirmar. Vuelve a intentar con la app abierta.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> actualizador.alContestarAndroid(RespuestaDeAndroid.INSTALADA)
            PackageInstaller.STATUS_FAILURE_ABORTED -> actualizador.alContestarAndroid(RespuestaDeAndroid.CANCELADA)
            else -> actualizador.alContestarAndroid(RespuestaDeAndroid.FALLO, razon(estado, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
        }
    }

    companion object {
        /** Por qué Android no la instaló, en español; al final, entre paréntesis, lo que dijo Android (para el registro del dueño). */
        fun razon(estado: Int, deAndroid: String?): String {
            val que = when (estado) {
                PackageInstaller.STATUS_FAILURE_STORAGE -> "Android no la instaló: no hay espacio en el celular."
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    "Android no la instaló: no es compatible con la app instalada (otra firma, o una versión más vieja)."
                PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android bloqueó la instalación en este celular."
                PackageInstaller.STATUS_FAILURE_INVALID -> "Android no la instaló: el APK no es válido."
                else -> "Android no la instaló."
            }
            return if (deAndroid.isNullOrBlank()) que else "$que ($deAndroid)"
        }
    }
}
