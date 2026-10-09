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
 * corto). Por eso se cierra sola tras [minutosParaCerrar] sin escanear: si no, abriría la cámara en cada encendido.
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
    }

    override fun onRestart() {
        super.onRestart()
        // Volvió a verse (p. ej. al encender la pantalla) después de mucho sin escanear: se cierra sin abrir la cámara.
        if (vm.msParaCerrar() <= 0L) {
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
    }

    /** Mientras se ve, se cierra sola al cumplirse el plazo sin escanear. */
    private fun cerrarAlCaducar() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val restante = vm.msParaCerrar()
                    if (restante <= 0L) {
                        finish()
                        break
                    }
                    delay(restante)
                }
            }
        }
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
