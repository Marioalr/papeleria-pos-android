package mx.papeleriapos.android.ajustes

import android.content.Context
import android.util.Log
import java.lang.reflect.Method

/**
 * El enlace con `pedirMosaico(context)` de `rapido/Mosaico.kt` (pide al sistema agregar el mosaico de Ajustes rápidos).
 * Se busca por nombre para que Ajustes compile aunque esa parte todavía no esté en la rama; cuando esté, basta con
 * cambiar [buscar] por una llamada directa. En la versión optimizada R8 tiene que conservar `MosaicoKt.pedirMosaico`
 * (o se llama directo).
 */
class AccesoMosaico private constructor(private val metodo: Method) {
    fun pedir(contexto: Context) {
        try {
            metodo.invoke(null, contexto)
        } catch (e: Exception) {
            Log.w(ETIQUETA, "No se pudo pedir el mosaico", e)
        }
    }

    companion object {
        private const val ETIQUETA = "AccesoMosaico"
        const val CLASE = "mx.papeleriapos.android.rapido.MosaicoKt"
        const val METODO = "pedirMosaico"

        /** null si esta versión no trae el mosaico. */
        fun buscar(): AccesoMosaico? = try {
            AccesoMosaico(Class.forName(CLASE).getMethod(METODO, Context::class.java))
        } catch (e: ReflectiveOperationException) {
            null
        } catch (e: LinkageError) {
            null
        }
    }
}
