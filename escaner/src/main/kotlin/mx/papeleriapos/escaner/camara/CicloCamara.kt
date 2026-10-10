package mx.papeleriapos.escaner.camara

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * El ciclo de vida al que se liga la cámara: copia el estado del dueño que pidió abrir (la Activity al enlazar en
 * `onCreate`, después el de la pantalla del escáner). Cambiar de dueño no obliga a desenlazar y volver a enlazar, que
 * cuesta cerrar y abrir la cámara.
 *
 * Que el dueño se destruya solo cierra la cámara (queda en CREATED, los casos de uso siguen enlazados); el controlador lo
 * destruye al cerrar. Solo en el hilo principal.
 */
internal class CicloCamara : LifecycleOwner {
    private val registro = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registro

    val resumido: Boolean get() = registro.currentState.isAtLeast(Lifecycle.State.RESUMED)

    fun seguir(estado: Lifecycle.State) {
        if (registro.currentState == Lifecycle.State.DESTROYED) return
        registro.currentState = when (estado) {
            Lifecycle.State.DESTROYED, Lifecycle.State.INITIALIZED -> Lifecycle.State.CREATED
            else -> estado
        }
    }

    fun destruir() {
        if (registro.currentState == Lifecycle.State.DESTROYED) return
        if (registro.currentState == Lifecycle.State.INITIALIZED) registro.currentState = Lifecycle.State.CREATED
        registro.currentState = Lifecycle.State.DESTROYED
    }
}
