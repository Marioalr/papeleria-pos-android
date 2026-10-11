package mx.papeleriapos.android.archivo

import java.io.File
import java.io.IOException

/**
 * Escribe a un temporal junto al destino y lo renombra: si la app muere a la mitad queda la versión anterior completa.
 * Lo usan las listas de venta y conteo, la sesión de prueba y el catálogo de prueba. Fuera del hilo principal.
 */
fun File.escribirAtomico(bytes: ByteArray) {
    val temporal = File(parentFile, "$name.tmp")
    temporal.writeBytes(bytes)
    if (!temporal.renameTo(this)) {
        delete()
        if (!temporal.renameTo(this)) throw IOException("No se pudo renombrar ${temporal.name}")
    }
}

fun File.escribirAtomico(texto: String) = escribirAtomico(texto.toByteArray(Charsets.UTF_8))
