package mx.papeleriapos.android.exportar

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Comparte un CSV (por WhatsApp, correo o Drive) sin pedir permisos de almacenamiento. */
object Compartir {
    /** Escribe el CSV en la caché (cache/exports, la ruta que publica el FileProvider). Fuera del hilo principal. */
    fun escribir(context: Context, nombre: String, contenido: String): Uri {
        val carpeta = File(context.cacheDir, "exports").apply { mkdirs() }
        val archivo = File(carpeta, nombre)
        archivo.writeText(contenido, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
    }

    /** Abre el selector para mandar el archivo. */
    fun enviar(context: Context, uri: Uri, asunto: String) {
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, asunto)
            // Sin ClipData algunas apps no reciben el permiso de lectura del URI.
            clipData = ClipData.newRawUri(asunto, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val selector = Intent.createChooser(envio, "Compartir CSV")
        if (context !is android.app.Activity) selector.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(selector)
    }
}
