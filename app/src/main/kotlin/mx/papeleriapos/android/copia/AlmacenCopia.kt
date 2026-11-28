package mx.papeleriapos.android.copia

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import mx.papeleriapos.android.archivo.escribirAtomico
import mx.papeleriapos.android.caja.JsonDelPos
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Las copias en la memoria privada de la app, una por sucursal enlazada (`files/copia/<id de la caja>/copia.json`), como
 * JSON propio: solo lo que [Copia] tiene, así que nunca hay usuarios ni clientes en el disco. Cada una se escribe entera y
 * atómica ([mx.papeleriapos.android.archivo.escribirAtomico]): si la app muere a la mitad queda la de antes. Bloquea: se
 * llama en el hilo de la conexión. Sin respaldo en la nube (`allowBackup="false"`).
 *
 * La de antes de la 0.5.5 (`files/copia/copia.json`, una sola) se sigue leyendo para su caja hasta que se guarda otra.
 */
class AlmacenCopia(private val carpeta: File) {
    private fun archivo(cajaId: String) = File(File(carpeta, limpio(cajaId)), ARCHIVO)
    private val deAntes get() = File(carpeta, ARCHIVO)

    /** La copia de esa caja; null si no hay o no se puede leer (entonces se vuelve a bajar completa). */
    fun leer(cajaId: String): Copia? {
        leerDe(archivo(cajaId))?.let { return it }
        return leerDe(deAntes)?.takeIf { it.caja.id.equals(cajaId, ignoreCase = true) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun leerDe(f: File): Copia? {
        if (!f.isFile) return null
        return try {
            f.inputStream().buffered().use { JsonDelPos.decodeFromStream<Copia>(it) }
        } catch (e: Exception) {
            // IOException, o un JSON de otra versión de la app que ya no se entiende: se baja otra vez.
            null
        }
    }

    /** Guarda la copia en la carpeta de su caja. */
    @OptIn(ExperimentalSerializationApi::class)
    fun guardar(copia: Copia) {
        val f = archivo(copia.caja.id)
        f.parentFile?.mkdirs()
        val bytes = ByteArrayOutputStream(1 shl 20).also { JsonDelPos.encodeToStream(copia, it) }.toByteArray()
        try {
            f.escribirAtomico(bytes)
            // La de antes ya no hace falta: la de su caja está en su carpeta.
            if (deAntes.isFile && leerDe(deAntes)?.caja?.id.equals(copia.caja.id, ignoreCase = true)) borrarDeAntes()
        } catch (e: IOException) {
            // Sin disco: la copia sigue en memoria y se vuelve a intentar con el siguiente cambio.
        }
    }

    /** Borra la copia de esa caja (y la de antes, si era suya). */
    fun borrar(cajaId: String) {
        val f = archivo(cajaId)
        f.delete()
        File(f.parentFile, "$ARCHIVO.tmp").delete()
        f.parentFile?.delete()
        if (leerDe(deAntes)?.caja?.id.equals(cajaId, ignoreCase = true)) borrarDeAntes()
    }

    private fun borrarDeAntes() {
        deAntes.delete()
        File(carpeta, "$ARCHIVO.tmp").delete()
    }

    /** Solo letras, cifras y guiones: el id de la caja nunca arma otra ruta. */
    private fun limpio(cajaId: String) = cajaId.lowercase().filter { it.isLetterOrDigit() || it == '-' }.take(64).ifEmpty { "caja" }

    companion object {
        const val CARPETA = "copia"
        private const val ARCHIVO = "copia.json"
    }
}
