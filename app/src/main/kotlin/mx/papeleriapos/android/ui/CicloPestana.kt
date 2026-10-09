package mx.papeleriapos.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Da a [contenido] un ciclo de vida propio que sigue al de la actividad mientras está en pantalla y se destruye al salir.
 * La cámara se liga al ciclo de vida, así que con esto se apaga al cambiar de pestaña (y no gasta batería ni le roba CPU
 * al banco de pruebas) aunque la actividad siga activa.
 */
@Composable
fun CicloPropio(contenido: @Composable () -> Unit) {
    val padre = LocalLifecycleOwner.current
    val propio = remember { DuenoDePestana() }
    DisposableEffect(padre) {
        val observador = LifecycleEventObserver { _, _ -> propio.seguir(padre.lifecycle.currentState) }
        padre.lifecycle.addObserver(observador)
        onDispose { padre.lifecycle.removeObserver(observador) }
    }
    DisposableEffect(propio) { onDispose { propio.destruir() } }
    CompositionLocalProvider(LocalLifecycleOwner provides propio, content = contenido)
}

private class DuenoDePestana : LifecycleOwner {
    private val registro = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registro

    fun seguir(estado: Lifecycle.State) {
        // La destrucción la decide la pestaña, no la actividad (al girar se recrea la composición entera).
        if (registro.currentState == Lifecycle.State.DESTROYED || estado == Lifecycle.State.DESTROYED) return
        registro.currentState = estado
    }

    fun destruir() {
        if (registro.currentState == Lifecycle.State.DESTROYED) return
        if (registro.currentState == Lifecycle.State.INITIALIZED) registro.currentState = Lifecycle.State.CREATED
        registro.currentState = Lifecycle.State.DESTROYED
    }
}
