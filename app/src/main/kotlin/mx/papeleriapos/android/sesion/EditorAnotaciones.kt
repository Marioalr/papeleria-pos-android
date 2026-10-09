package mx.papeleriapos.android.sesion

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.exportar.Compartir
import mx.papeleriapos.android.medicion.MonitorDeSesion

/**
 * Cambia la condición del bloque o la nota de un intento y lo guarda fuera del hilo principal. Guardar no depende de la
 * pantalla: va en un alcance de la aplicación (si la composición sale antes de que empiece la escritura, igual se
 * escribe) y en un solo hilo, escribiendo siempre lo más reciente de [AnotacionesSesion] (dos cambios seguidos no dejan
 * la versión vieja al último).
 */
class EditorAnotaciones(private val contexto: Context) {
    /** La condición de los intentos que siguen. */
    fun condicion(sesion: SesionPrueba, condicion: Condicion) {
        AnotacionesSesion.cambiar(sesion) { it.conCondicion(condicion, sesion.intentos.size + 1) }
        guardar(contexto)
    }

    fun nota(sesion: SesionPrueba, n: Int, texto: String) {
        AnotacionesSesion.cambiar(sesion) { it.conNota(n, texto) }
        guardar(contexto)
    }

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

        /** Encarga guardar las anotaciones en curso (las más recientes al momento de escribir). */
        fun guardar(contexto: Context) {
            val app = contexto.applicationContext
            alcance.launch {
                val a = AnotacionesSesion.estado.value
                // Sin sesión (ya se terminó) no hay nada que guardar: no se revive el archivo.
                if (a.inicioSesion != 0L) AlmacenSesion(app).guardarAnotaciones(a)
            }
        }
    }
}

@Composable
fun recordarEditorAnotaciones(): EditorAnotaciones {
    val contexto = LocalContext.current.applicationContext
    return remember(contexto) { EditorAnotaciones(contexto) }
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

    /**
     * La medición de la sesión: recursos.csv del monitor (una fila por segundo) y aperturas.csv (una fila por apertura
     * del escáner, también las del escaneo rápido). Los que no haya no van.
     */
    suspend fun medicion(contexto: Context, sesion: SesionPrueba): List<Uri> = withContext(Dispatchers.IO) {
        listOfNotNull(
            MonitorDeSesion.leer(contexto)?.let { Compartir.escribir(contexto, CsvSesion.nombreArchivo("recursos", sesion.clave()), it) },
            RegistroAperturas.leer(contexto)?.let { Compartir.escribir(contexto, CsvSesion.nombreArchivo("aperturas", sesion.clave()), it) },
        )
    }
}
