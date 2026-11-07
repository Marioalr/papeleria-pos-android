package mx.papeleriapos.android.rapido

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mx.papeleriapos.android.Arranque
import mx.papeleriapos.android.ContenidoEnDosCuadros
import mx.papeleriapos.android.MainActivity
import mx.papeleriapos.android.tema.TemaEscaner

/**
 * El escaneo rápido: el verificador que abre el mosaico «Precio», también con el celular bloqueado
 * (`android:showWhenLocked` en el manifiesto, que es lo que SystemUI revisa; en tiempo de ejecución no basta). Siempre en
 * Precio ([EscaneoRapidoViewModel]); nada de ajustes, sesión, banco ni exportar, y no guarda imágenes (tampoco la de
 * Recientes, en API 33+). «Abrir la app» pide desbloquear y abre la app en Precio con ese producto
 * (docs/propuesta-interfaz.md, «Del bolsillo al precio»).
 *
 * Si queda arriba al apagar la pantalla, al encenderla aparece directo sobre el bloqueo y escaneando (el camino más
 * corto). Por eso se cierra sola: tras [EscaneoRapidoViewModel.minutosParaCerrar] sin escanear (un minuto con el celular
 * bloqueado, ver [EscaneoRapidoViewModel.msParaCerrar]) o en cuanto la cámara se apaga por inactividad. Si no, abriría la
 * cámara en cada encendido y dejaría la pantalla encendida con lo leído a la vista sobre el bloqueo. Nunca mientras se
 * pide el PIN de «Abrir la app»: si se cerrara, el aviso de SystemUI se perdería y al desbloquear no se abriría nada.
 *
 * Cada vez que vuelve a verse, lo leído se borra. Si la trajo el mosaico es una apertura nueva, con el plazo desde cero
 * (aunque ya hubiera caducado: un toque al mosaico siempre abre); si solo se encendió la pantalla, el plazo sigue y, si
 * ya venció, se cierra sin abrir la cámara.
 */
class EscaneoRapidoActivity : ComponentActivity() {
    private val vm: EscaneoRapidoViewModel by viewModels()
    private var detenida = false

    /**
     * El ciclo de vida que sigue la cámara (y la pantalla): el de la Activity, salvo al volver ya caducada, mientras se
     * sabe si la trajo el mosaico ([caducadaAlVolver]).
     */
    internal val compuerta = CompuertaDeCiclo(this)

    /**
     * «Abrir la app» en curso: pidiendo el PIN o abriendo. Un solo pedido a la vez (cada uno registraría su propio aviso en
     * SystemUI y dos toques abrirían la app dos veces), y mientras tanto no se cierra sola.
     */
    private val abriendoLaApp = MutableStateFlow(false)

    /**
     * Volvió a verse con el plazo vencido (`onRestart`) y todavía no se sabe si la trajo el mosaico: en Android 10 o más su
     * intent llega después, en `onNewIntent`. Hasta `onResume` la cámara espera y no se cierra; ahí se decide.
     */
    private val caducadaAlVolver = MutableStateFlow(false)

    /** Algo la retiene: no se cierra sola, ni por el plazo ni por la cámara. */
    private val retenida = combine(abriendoLaApp, caducadaAlVolver) { a, c -> a || c }

    private fun retenidaAhora() = abriendoLaApp.value || caducadaAlVolver.value

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
        // Lo que se ve aquí lo decidió la propuesta para cualquiera, así que sin FLAG_SECURE; pero la imagen que el sistema
        // guarda al apagarse la pantalla (la de Recientes y la de arranque) no: en su lugar, el fondo negro del tema.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        val controlador = vm.controlador
        Arranque.reportarAlPrimerAnalisis(this, controlador)
        vm.enlazarTemprano(this, compuerta)
        Arranque.marcarPrimerCuadroUi(window.decorView)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides compuerta) {
                ContenidoEnDosCuadros(controlador) {
                    TemaEscaner(oscuro = true) {
                        PantallaRapida(vm, alAbrirApp = ::abrirApp, alCerrar = ::finish)
                    }
                }
            }
        }
        cerrarAlCaducar()
        cerrarAlApagarseLaCamara()
    }

    /**
     * El mosaico la trajo otra vez (singleTask: la misma Activity y el mismo ViewModel): una apertura nueva. Si estaba
     * detenida, en Android 10 o más llega después de [onRestart] (que ya la pudo encontrar caducada) y en 7 a 9 antes.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isFinishing) return
        // Primero el plazo: al soltarla, lo que espera a que deje de estar retenida ya la ve con el plazo nuevo.
        vm.aperturaNueva()
        caducadaAlVolver.value = false
        compuerta.detenida = false
        // Volvía caducada (Android 10 o más): onStart no marcó la apertura porque no se sabía si la habría; ahora sí.
        if (detenida && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) alVolverAVerse()
    }

    override fun onRestart() {
        super.onRestart()
        // Volvió a verse: al encender la pantalla o con el mosaico. Lo de antes ya no se enseña.
        vm.alVolver()
        // Caducada: si fue la pantalla, se cierra sin abrir la cámara; si fue el mosaico (su intent todavía no llega en
        // Android 10 o más), abre. La cámara espera hasta saberlo.
        val caducada = vm.msParaCerrar(bloqueado()) <= 0L && !abriendoLaApp.value
        caducadaAlVolver.value = caducada
        compuerta.detenida = caducada
    }

    override fun onStart() {
        super.onStart()
        if (detenida && !isFinishing && !caducadaAlVolver.value) alVolverAVerse()
    }

    /** Otra apertura en caliente (docs/escaner.md, «Apertura»): la misma Activity vuelve a verse. */
    private fun alVolverAVerse() {
        detenida = false
        Arranque.alVolver(window.decorView)
    }

    override fun onResume() {
        super.onResume()
        // Volvió caducada y ningún intent la trajo: se encendió la pantalla. Se cierra sin haber abierto la cámara.
        if (caducadaAlVolver.value) finish()
    }

    override fun onStop() {
        super.onStop()
        detenida = true
        vm.alDetenerse()
    }

    private fun bloqueado(): Boolean = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /**
     * Mientras se ve, se cierra sola al cumplirse el plazo sin escanear. Se vuelve a revisar al menos cada
     * [REVISAR_BLOQUEO_MS] (si bloquean o desbloquean el celular, el plazo cambia) y cuando deja de estar retenida.
     */
    private fun cerrarAlCaducar() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    retenida.first { !it }
                    val restante = vm.msParaCerrar(bloqueado())
                    if (restante <= 0L) {
                        finish()
                        break
                    }
                    withTimeoutOrNull(restante.coerceAtMost(REVISAR_BLOQUEO_MS)) { retenida.first { it } }
                }
            }
        }
    }

    /**
     * La cámara se apagó por inactividad (en pausa y sin superficie): no queda nada que hacer aquí. Si se apaga mientras
     * está retenida, se queda: al cancelar el PIN se ve la cámara en pausa y el plazo cierra lo demás.
     */
    private fun cerrarAlApagarseLaCamara() {
        val c = vm.controlador
        lifecycleScope.launch {
            esperarCamaraApagada(c.superficie, { c.estado.value.fase }, ::retenidaAhora)
            if (!isFinishing) finish()
        }
    }

    /**
     * Pide desbloquear (si hace falta) y abre la app en Precio con lo que se ve; esta pantalla se cierra. Con el PIN
     * cancelado sigue aquí, sobre el bloqueo, con el plazo desde cero. API 24–25 no puede pedirlo: la app aparece al
     * desbloquear (MainActivity no se muestra sobre el bloqueo). Lo llama el botón (y las pruebas).
     */
    internal fun abrirApp() {
        if (abriendoLaApp.value) return
        abriendoLaApp.value = true
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguard != null && keyguard.isKeyguardLocked) {
            // Sin bloqueo seguro (o con Smart Lock) se quita al instante; con PIN o huella, los pide.
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = abrir()

                    // También cuando el PIN se oculta por otra razón.
                    override fun onDismissCancelled() = seguirAqui()

                    // P. ej. la pantalla ya no estaba al frente. Si de todos modos ya se desbloqueó, abre.
                    override fun onDismissError() = if (keyguard.isKeyguardLocked) seguirAqui() else abrir()
                },
            )
        } else {
            // El de la tienda, sin bloqueo, abre directo.
            abrir()
        }
    }

    private fun seguirAqui() {
        vm.huboActividad()
        abriendoLaApp.value = false
    }

    private fun abrir() {
        // Casi siempre los productos ya están (se leen con la primera lectura): entonces abre en este mismo instante.
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            startActivity(intentAbrirApp(this@EscaneoRapidoActivity, vm.productoParaAbrir()))
            finish()
        }
    }

    private companion object {
        const val REVISAR_BLOQUEO_MS = 10_000L
    }
}

/**
 * «Abrir la app»: la principal en Precio con [producto] (su clave o, si no estaba en el catálogo, el código leído;
 * [MainActivity.EXTRA_PRODUCTO]). Con NEW_TASK | CLEAR_TOP | SINGLE_TOP, si la app ya está abierta la misma instancia lo
 * recibe en `onNewIntent` y la tarea se queda con su intent de raíz; con las banderas de antes (NEW_TASK |
 * RESET_TASK_IF_NEEDED) el sistema solo traía la tarea y el producto no llegaba. La acción y la categoría del ícono
 * (`makeMainActivity`): si crea la tarea, el ícono la vuelve a traer en lugar de apilar otra MainActivity.
 */
internal fun intentAbrirApp(context: Context, producto: String?): Intent =
    Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_MODO, MainActivity.MODO_PRECIO)
        .apply { if (producto != null) putExtra(MainActivity.EXTRA_PRODUCTO, producto) }

/**
 * El ciclo de vida de [actividad], salvo mientras está [detenida]: entonces no pasa de CREATED aunque la Activity ya esté
 * al frente. La cámara se liga a este: al volver con el plazo vencido, no abre antes de saber si la trajo el mosaico.
 */
internal class CompuertaDeCiclo(private val actividad: LifecycleOwner) : LifecycleOwner {
    private val registro = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registro

    var detenida = false
        set(valor) {
            field = valor
            seguir()
        }

    init {
        actividad.lifecycle.addObserver(LifecycleEventObserver { _, _ -> seguir() })
    }

    private fun seguir() {
        val estado = actividad.lifecycle.currentState
        if (registro.currentState == Lifecycle.State.DESTROYED || estado == Lifecycle.State.INITIALIZED) return
        registro.currentState = if (detenida && estado.isAtLeast(Lifecycle.State.STARTED)) Lifecycle.State.CREATED else estado
    }
}
