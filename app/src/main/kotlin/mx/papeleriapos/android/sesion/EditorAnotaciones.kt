package mx.papeleriapos.android.sesion

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.exportar.Compartir
import mx.papeleriapos.android.medicion.MonitorDeSesion

/** Cambia la condición del bloque o la nota de un intento y lo guarda fuera del hilo principal. */
class EditorAnotaciones(private val contexto: Context, private val alcance: CoroutineScope) {
    /** La condición de los intentos que siguen. */
    fun condicion(sesion: SesionPrueba, condicion: Condicion) =
        guardar(AnotacionesSesion.cambiar(sesion) { it.conCondicion(condicion, sesion.intentos.size + 1) })

    fun nota(sesion: SesionPrueba, n: Int, texto: String) = guardar(AnotacionesSesion.cambiar(sesion) { it.conNota(n, texto) })

    private fun guardar(a: Anotaciones) {
        alcance.launch(Dispatchers.IO) { AlmacenSesion(contexto).guardarAnotaciones(a) }
    }
}

@Composable
fun recordarEditorAnotaciones(): EditorAnotaciones {
    val contexto = LocalContext.current.applicationContext
    val alcance = rememberCoroutineScope()
    return remember(contexto, alcance) { EditorAnotaciones(contexto, alcance) }
}

/** Los CSV de la sesión, listos para compartir. */
object ExportarSesion {
    /** Los intentos con sus anotaciones (condición y notas). */
    suspend fun intentos(contexto: Context, sesion: SesionPrueba, equipo: DatosEquipo): Uri = withContext(Dispatchers.IO) {
        Compartir.escribir(
            contexto,
            CsvSesion.nombreArchivo("sesion", sesion.clave()),
            CsvSesion.escribir(sesion, equipo, anotaciones = AnotacionesSesion.de(sesion)),
        )
    }

    /** recursos.csv del monitor (una fila por segundo); null si no hay. */
    suspend fun recursos(contexto: Context, sesion: SesionPrueba): Uri? = withContext(Dispatchers.IO) {
        MonitorDeSesion.leer(contexto)?.let { Compartir.escribir(contexto, CsvSesion.nombreArchivo("recursos", sesion.clave()), it) }
    }
}
