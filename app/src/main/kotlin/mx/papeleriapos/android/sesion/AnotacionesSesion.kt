package mx.papeleriapos.android.sesion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Las anotaciones de la sesión en curso (condición del bloque y notas), una por proceso como la sesión. Las cambian la
 * pantalla de la sesión y la barra sobre el visor; [AlmacenSesion] las guarda y las recupera junto con los intentos.
 */
object AnotacionesSesion {
    private val _estado = MutableStateFlow(Anotaciones())
    val estado: StateFlow<Anotaciones> = _estado.asStateFlow()

    /** Las de [sesion] (vacías si las guardadas eran de otra). */
    fun de(sesion: SesionPrueba?): Anotaciones = _estado.value.de(sesion)

    /** Aplica [cambio] a las de [sesion] y devuelve cómo quedaron, para guardarlas. */
    fun cambiar(sesion: SesionPrueba, cambio: (Anotaciones) -> Anotaciones): Anotaciones =
        _estado.updateAndGet { cambio(it.de(sesion)) }

    fun reemplazar(anotaciones: Anotaciones) {
        _estado.value = anotaciones
    }
}
