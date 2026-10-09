package mx.papeleriapos.android.sesion

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Guarda la sesión de prueba en curso para que sobreviva si Android cierra la app (en una prueba de 200 escaneos pasa).
 * Los intentos van en el mismo CSV que se exporta; el nombre, el inicio y el esperado en un archivo aparte. Se llama
 * fuera del hilo principal.
 */
class AlmacenSesion(context: Context) {
    private val carpeta = File(context.filesDir, "sesion")
    private val intentos = File(carpeta, "intentos.csv")
    private val datos = File(carpeta, "sesion.txt")

    fun guardar(sesion: SesionPrueba?, equipo: DatosEquipo) {
        try {
            if (sesion == null) {
                intentos.delete(); datos.delete()
                return
            }
            carpeta.mkdirs()
            escribirAtomico(datos, listOf(sesion.inicio.toString(), sesion.esperado, sesion.nombre).joinToString("\n"))
            escribirAtomico(intentos, CsvSesion.escribir(sesion, equipo))
        } catch (e: IOException) {
            Log.e(ETIQUETA, "No se pudo guardar la sesión de prueba", e)
        }
    }

    fun recuperar(): SesionPrueba? = try {
        if (!datos.exists()) {
            null
        } else {
            val lineas = datos.readText().split("\n", limit = 3)
            val inicio = lineas[0].toLong()
            val esperado = lineas.getOrElse(1) { "" }
            val nombre = lineas.getOrElse(2) { "" }
            val lista = if (intentos.exists()) CsvSesion.leerIntentos(intentos.readText()) else emptyList()
            SesionPrueba(nombre, inicio, lista, esperado)
        }
    } catch (e: Exception) {
        Log.e(ETIQUETA, "No se pudo recuperar la sesión de prueba", e)
        null
    }

    /** Escribe a un temporal y lo renombra: si la app muere a la mitad queda la versión anterior completa. */
    private fun escribirAtomico(destino: File, texto: String) {
        val temporal = File(destino.parentFile, destino.name + ".tmp")
        temporal.writeText(texto)
        if (!temporal.renameTo(destino)) {
            destino.delete()
            if (!temporal.renameTo(destino)) throw IOException("No se pudo renombrar ${temporal.name}")
        }
    }

    private companion object {
        const val ETIQUETA = "AlmacenSesion"
    }
}
