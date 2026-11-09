package mx.papeleriapos.android.entrada

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Entrar a la app con la huella (docs/propuesta-interfaz.md, «Entrar»): es local, del celular. Con el celular dado de
 * alta, la app se bloquea al abrirse y cuando vuelve del fondo después de [MS_SIN_PEDIR]; se desbloquea con lo que el
 * celular acepte como seguro: la huella o la cara, y si no, el PIN, el patrón o la contraseña del celular. Sin bloqueo
 * de pantalla no hay qué pedir (el alta lo avisa). El escaneo rápido sobre el bloqueo no entra aquí: solo enseña lo que
 * ve el cliente.
 *
 * Mientras está bloqueada, la pantalla principal queda tapada y las lecturas no cuentan ni vibran. La cámara sí se
 * enlaza: en cuanto se entra, ya está escaneando.
 */
object Entrada {
    /** Después de este rato en segundo plano se vuelve a pedir (como la sesión de la propuesta: 10 minutos). */
    const val MS_SIN_PEDIR = 10 * 60_000L

    private val _bloqueada = MutableStateFlow(false)
    val bloqueada: StateFlow<Boolean> = _bloqueada.asStateFlow()

    /** Cuándo se fue al fondo por última vez (`elapsedRealtime`); 0 si todavía no se ha entrado en este proceso. */
    private var alFondoDesde = 0L
    private var entroAlgunaVez = false
    private var pidiendo: CancellationSignal? = null

    /** Si el celular tiene bloqueo de pantalla (PIN, patrón, contraseña o huella): sin él, no se puede pedir nada. */
    fun hayBloqueoSeguro(contexto: Context): Boolean =
        contexto.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * La app pasa al frente con el celular dado de alta ([conAlta]): si es la primera vez en el proceso o estuvo más de
     * [MS_SIN_PEDIR] en el fondo, se bloquea y se pide entrar. Sin alta, o sin bloqueo de pantalla, no se pide.
     */
    fun alFrente(actividad: Activity, conAlta: Boolean, pedirCredencial: (Intent) -> Unit, ahora: Long = SystemClock.elapsedRealtime()) {
        if (!conAlta || !hayBloqueoSeguro(actividad)) {
            _bloqueada.value = false
            return
        }
        if (hayQuePedir(ahora)) {
            _bloqueada.value = true
            pedir(actividad, pedirCredencial)
        }
    }

    /**
     * Al abrir, antes de leer el alta entera (la llave cifrada y la copia, que tarda): si hay un alta guardada ([conAlta]),
     * el celular tiene bloqueo y toca pedir, la app se bloquea ya, desde el primer cuadro; así no se ve el ticket ni cuentan
     * las lecturas mientras se lee la copia. [alFrente] pide entrar (o desbloquea si el alta no se pudo leer).
     */
    fun bloquearAlAbrir(contexto: Context, conAlta: Boolean, ahora: Long = SystemClock.elapsedRealtime()) {
        if (conAlta && hayQuePedir(ahora) && hayBloqueoSeguro(contexto)) _bloqueada.value = true
    }

    /** Si toca pedir: nunca se ha entrado en este proceso, o se estuvo más de [MS_SIN_PEDIR] en el fondo. */
    internal fun hayQuePedir(ahora: Long): Boolean = !entroAlgunaVez || (alFondoDesde != 0L && ahora - alFondoDesde >= MS_SIN_PEDIR)

    /** Para las pruebas: como un proceso nuevo. */
    internal fun reiniciar() {
        entroAlgunaVez = false
        alFondoDesde = 0L
        pidiendo = null
        _bloqueada.value = false
    }

    fun alFondo(ahora: Long = SystemClock.elapsedRealtime()) {
        alFondoDesde = ahora
        pidiendo?.cancel()
        pidiendo = null
    }

    /** Lo que el sistema aceptó: se entra. También lo llama la Activity cuando regresa el PIN del celular. */
    fun desbloquear() {
        entroAlgunaVez = true
        _bloqueada.value = false
        pidiendo = null
    }

    /**
     * Pide entrar: en Android 11+ el diálogo del sistema con la huella y, si no, el PIN del celular; en 9 y 10, la huella y
     * el PIN como respaldo; antes de Android 9, el PIN del celular con la pantalla de bloqueo ([pedirCredencial]).
     */
    fun pedir(actividad: Activity, pedirCredencial: (Intent) -> Unit) {
        if (pidiendo != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            pedirConLaPantallaDeBloqueo(actividad, pedirCredencial)
            return
        }
        val senal = CancellationSignal()
        pidiendo = senal
        val constructor = BiometricPrompt.Builder(actividad)
            .setTitle("Entrar")
            .setSubtitle("Con tu huella o el bloqueo del celular")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            constructor.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            constructor.setDeviceCredentialAllowed(true)
        } else {
            constructor.setNegativeButton("Usar el PIN del celular", ContextCompat.getMainExecutor(actividad)) { _, _ ->
                pidiendo = null
                pedirConLaPantallaDeBloqueo(actividad, pedirCredencial)
            }
        }
        constructor.build().authenticate(
            senal,
            ContextCompat.getMainExecutor(actividad),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = desbloquear()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Canceló, o no hay huella ni bloqueo que el sistema acepte: la pantalla sigue tapada, con «Entrar».
                    pidiendo = null
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS) {
                        pedirConLaPantallaDeBloqueo(actividad, pedirCredencial)
                    }
                }
            },
        )
    }

    private fun pedirConLaPantallaDeBloqueo(actividad: Activity, pedirCredencial: (Intent) -> Unit) {
        val km = actividad.getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val intent = km?.createConfirmDeviceCredentialIntent("Entrar", "Con el bloqueo del celular")
        if (intent == null) {
            // Sin bloqueo de pantalla no hay qué pedir.
            desbloquear()
            return
        }
        pedirCredencial(intent)
    }
}
