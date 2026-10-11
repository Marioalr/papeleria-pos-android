package mx.papeleriapos.android.sesion

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Guarda la sesión de prueba en curso para que sobreviva si Android cierra la app (en una prueba de 200 escaneos pasa).
 * Los intentos van en el mismo CSV que se exporta; el nombre, el inicio y el esperado en un archivo aparte, y la
 * condición de los bloques y las notas en otro (cambian sin que cambie la sesión). Se llama fuera del hilo principal.
 */
class AlmacenSesion(context: Context) {
    private val app = context.applicationContext
    // Perezosos: se crea en el hilo principal (con el ViewModel) y filesDir revisa el disco; se usa en otro hilo.
    private val carpeta by lazy { File(app.filesDir, "sesion") }
    private val intentos by lazy { File(carpeta, "intentos.csv") }
    private val datos by lazy { File(carpeta, "sesion.txt") }
    private val notas by lazy { File(carpeta, "anotaciones.csv") }

    fun guardar(sesion: SesionPrueba?, equipo: DatosEquipo) {
        try {
            if (sesion == null) {
                intentos.delete(); datos.delete(); notas.delete()
                AnotacionesSesion.reemplazar(Anotaciones())
                // recursos.csv y aperturas.csv los borra quien terminó la sesión (EscanerViewModel.terminarSesion): sabe
                // cuál terminó y no se lleva los de una sesión nueva que ya empezó.
                return
            }
            carpeta.mkdirs()
            escribirAtomico(datos, listOf(sesion.inicio.toString(), sesion.esperado, sesion.nombre).joinToString("\n"))
            escribirAtomico(intentos, CsvSesion.escribir(sesion, equipo, anotaciones = AnotacionesSesion.de(sesion)))
        } catch (e: IOException) {
            Log.e(ETIQUETA, "No se pudo guardar la sesión de prueba", e)
        }
    }

    /** Guarda la condición de los bloques y las notas. Fuera del hilo principal. */
    fun guardarAnotaciones(anotaciones: Anotaciones) {
        try {
            carpeta.mkdirs()
            escribirAtomico(notas, anotaciones.aTexto())
        } catch (e: IOException) {
            Log.e(ETIQUETA, "No se pudieron guardar las anotaciones", e)
        }
    }

    /**
     * La sesión guardada y, como efecto, sus anotaciones en [AnotacionesSesion]. Si anotaciones.csv falta (o es de otra
     * sesión), se reconstruyen de las columnas condicion y nota de intentos.csv, que se escriben con cada intento.
     */
    fun recuperar(): SesionPrueba? = try {
        if (!datos.exists()) {
            null
        } else {
            val lineas = datos.readText().split("\n", limit = 3)
            val inicio = lineas[0].toLong()
            val esperado = lineas.getOrElse(1) { "" }
            val nombre = lineas.getOrElse(2) { "" }
            val textoIntentos = if (intentos.exists()) intentos.readText() else ""
            val lista = if (textoIntentos.isNotEmpty()) CsvSesion.leerIntentos(textoIntentos) else emptyList()
            val sesion = SesionPrueba(nombre, inicio, lista, esperado)
            val guardadas = if (notas.exists()) Anotaciones.deTexto(notas.readText()).takeIf { it.inicioSesion == inicio } else null
            AnotacionesSesion.reemplazar(guardadas ?: CsvSesion.leerAnotaciones(textoIntentos, inicio))
            sesion
        }
    } catch (e: Exception) {
        Log.e(ETIQUETA, "No se pudo recuperar la sesión de prueba", e)
        null
    }

    /** El inicio de la sesión activa (la que está guardada); null si no hay. Fuera del hilo principal. */
    fun inicioActivo(): Long? = try {
        if (datos.exists()) datos.readText().substringBefore('\n').trim().toLongOrNull() else null
    } catch (e: IOException) {
        null
    }

    /** Escribe a un temporal y lo renombra: si la app muere a la mitad queda la versión anterior completa. */
    private fun escribirAtomico(destino: File, texto: String) = synchronized(BLOQUEO) {
        val temporal = File(destino.parentFile, destino.name + ".tmp")
        temporal.writeText(texto)
        if (!temporal.renameTo(destino)) {
            destino.delete()
            if (!temporal.renameTo(destino)) throw IOException("No se pudo renombrar ${temporal.name}")
        }
    }

    private companion object {
        const val ETIQUETA = "AlmacenSesion"

        /** El ViewModel y la pantalla pueden guardar a la vez. */
        val BLOQUEO = Any()
    }
}
