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
 * La copia en la memoria privada de la app (`files/copia/copia.json`), como JSON propio: solo lo que [Copia] tiene, así
 * que nunca hay usuarios ni clientes en el disco. Se escribe entera y atómica ([mx.papeleriapos.android.archivo.escribirAtomico]):
 * si la app muere a la mitad queda la de antes. Bloquea: se llama en el hilo de la conexión. Sin respaldo en la nube
 * (`allowBackup="false"`).
 */
class AlmacenCopia(private val carpeta: File) {
    private val archivo get() = File(carpeta, ARCHIVO)

    /** La copia guardada; null si no hay o no se puede leer (entonces se vuelve a bajar completa). */
    @OptIn(ExperimentalSerializationApi::class)
    fun leer(): Copia? {
        if (!archivo.isFile) return null
        return try {
            archivo.inputStream().buffered().use { JsonDelPos.decodeFromStream<Copia>(it) }
        } catch (e: Exception) {
            // IOException, o un JSON de otra versión de la app que ya no se entiende: se baja otra vez.
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun guardar(copia: Copia) {
        carpeta.mkdirs()
        val bytes = ByteArrayOutputStream(1 shl 20).also { JsonDelPos.encodeToStream(copia, it) }.toByteArray()
        try {
            archivo.escribirAtomico(bytes)
        } catch (e: IOException) {
            // Sin disco: la copia sigue en memoria y se vuelve a intentar con el siguiente cambio.
        }
    }

    fun borrar() {
        archivo.delete()
        File(carpeta, "$ARCHIVO.tmp").delete()
    }

    companion object {
        const val CARPETA = "copia"
        private const val ARCHIVO = "copia.json"
    }
}
