package mx.papeleriapos.android.rapido

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mx.papeleriapos.android.Arranque
import mx.papeleriapos.android.ContenidoEnDosCuadros
import mx.papeleriapos.android.MainActivity
import mx.papeleriapos.android.tema.TemaEscaner

/**
 * El escaneo rápido: se abre desde el mosaico de Ajustes rápidos, también con el celular bloqueado
 * (`android:showWhenLocked` en el manifiesto, que es lo que SystemUI revisa; en tiempo de ejecución no basta). Solo
 * escanea y muestra lo leído en el modo actual; nada de ajustes, sesión, banco ni exportar, y no guarda imágenes.
 * «Abrir la app» pide desbloquear y abre la app completa.
 *
 * Si queda arriba al apagar la pantalla, al encenderla aparece directo sobre el bloqueo y escaneando (el camino más
 * corto). Por eso se cierra sola: tras [minutosParaCerrar] sin escanear (un minuto con el celular bloqueado, ver
 * [msParaCerrar]) o en cuanto la cámara se apaga por inactividad. Si no, abriría la cámara en cada encendido y dejaría
 * la pantalla encendida con lo leído a la vista sobre el bloqueo. Lo que muestra es lo mínimo: lo leído en esta apertura.
 */
class EscaneoRapidoActivity : ComponentActivity() {
    private val vm: EscaneoRapidoViewModel by viewModels()
    private var detenida = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Arranque.alCrearActividad()
        // Barras con íconos claros: todo es negro hasta que llega el visor.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            // API 24–26: los atributos del manifiesto no existen; la bandera de ventana hace lo mismo.
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val controlador = vm.controlador
        Arranque.reportarAlPrimerAnalisis(this, controlador)
        vm.enlazarTemprano(this)
        Arranque.marcarPrimerCuadroUi(window.decorView)
        setContent {
            ContenidoEnDosCuadros(controlador) {
                TemaEscaner(oscuro = true) {
                    PantallaRapida(vm, alAbrirApp = ::abrirApp, alCerrar = ::finish)
                }
            }
        }
        cerrarAlCaducar()
        cerrarAlApagarseLaCamara()
    }

    override fun onRestart() {
        super.onRestart()
        // Volvió a verse (p. ej. al encender la pantalla) después de mucho sin escanear: se cierra sin abrir la cámara.
        if (vm.msParaCerrar(bloqueado()) <= 0L) {
            vm.controlador.cerrar()
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        if (detenida && !isFinishing) {
            detenida = false
            Arranque.alVolver(window.decorView)
        }
    }

    override fun onStop() {
        super.onStop()
        detenida = true
        vm.alDetenerse()
    }

    private fun bloqueado(): Boolean = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /**
     * Mientras se ve, se cierra sola al cumplirse el plazo sin escanear. Se vuelve a revisar al menos cada
     * [REVISAR_BLOQUEO_MS]: si bloquean o desbloquean el celular, el plazo cambia.
     */
    private fun cerrarAlCaducar() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val restante = vm.msParaCerrar(bloqueado())
                    if (restante <= 0L) {
                        finish()
                        break
                    }
                    delay(restante.coerceAtMost(REVISAR_BLOQUEO_MS))
                }
            }
        }
    }

    /**
     * La cámara se apagó por inactividad (en pausa y sin superficie): no queda nada que hacer aquí. Se revisa solo cuando
     * cambia la superficie (al enlazar y al soltar), no en cada cuadro.
     */
    private fun cerrarAlApagarseLaCamara() {
        val c = vm.controlador
        lifecycleScope.launch {
            var hubo = false
            c.superficie.first { s ->
                if (s != null) hubo = true
                camaraApagadaPorInactividad(c.estado.value.fase, s != null, hubo)
            }
            if (!isFinishing) finish()
        }
    }

    private companion object {
        const val REVISAR_BLOQUEO_MS = 10_000L
    }

    /** Pide desbloquear (si hace falta) y abre la app completa; esta pantalla se cierra. */
    private fun abrirApp() {
        val abrir = {
            // Como desde el lanzador: si la app ya está abierta, se trae su tarea tal como estaba.
            startActivity(
                Intent.makeMainActivity(ComponentName(this, MainActivity::class.java))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguard != null && keyguard.isKeyguardLocked) {
            // Sin bloqueo seguro (o con Smart Lock) se quita al instante; con PIN o huella, los pide.
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = abrir()
                },
            )
        } else {
            // API 24–25: la app aparece al desbloquear.
            abrir()
        }
    }
}
