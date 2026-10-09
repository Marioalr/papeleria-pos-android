package mx.papeleriapos.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.android.ui.AppEscaner
import mx.papeleriapos.android.ui.Pestana
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.camara.ControladorCamara
import mx.papeleriapos.escaner.nucleo.Modo

/**
 * La app de prueba. Para abrir al instante (docs/escaner.md, «Apertura»): marca cada paso, enlaza la cámara en
 * `onCreate` (abre en `onStart`, mientras Compose dibuja), el primer cuadro es solo negro (como la ventana, sin
 * destello) y la interfaz completa llega en el siguiente, y el TTFD es el primer cuadro analizado.
 *
 * Los atajos «Escanear venta» y «Escanear conteo» (res/xml/shortcuts.xml) traen [EXTRA_MODO]; el sistema recrea la tarea
 * con ellos, pero lo leído está guardado ([mx.papeleriapos.android.lista.ListasCompartidas]) y no se pierde.
 *
 * Al restaurar con estado guardado (Android mató el proceso en segundo plano y el empleado toca el ícono: el frío más
 * común al sacar el celular del bolsillo) también enlaza en `onCreate` si la pestaña a la vista era la del escáner.
 */
class MainActivity : ComponentActivity() {
    private val vm: EscanerViewModel by viewModels()
    private var detenida = false
    /**
     * Si la pestaña a la vista es la del escáner, para guardarlo en el estado de la Activity. La interfaz completa llega
     * en el segundo cuadro: hasta entonces vale lo restaurado.
     */
    private var escanerVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        Arranque.alCrearActividad()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val controlador = vm.controlador
        Arranque.reportarAlPrimerAnalisis(this, controlador)
        if (savedInstanceState == null) aplicarModo(intent)
        val guardado = savedInstanceState?.takeIf { it.containsKey(CLAVE_ESCANER_VISIBLE) }?.getBoolean(CLAVE_ESCANER_VISIBLE)
        escanerVisible = guardado ?: (savedInstanceState == null)
        if (enlazarAlCrear(restaurando = savedInstanceState != null, escanerVisible = guardado)) {
            vm.enlazarTemprano(this, ControladorCamara.PANTALLA_PRINCIPAL, descuentoAbajoDp = ALTO_PESTANAS_DP)
        }
        Arranque.marcarPrimerCuadroUi(window.decorView)
        setContent {
            ContenidoEnDosCuadros(controlador) {
                TemaEscaner { AppEscaner(vm = vm, alCambiarPestana = { escanerVisible = it == Pestana.ESCANEAR }) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(CLAVE_ESCANER_VISIBLE, escanerVisible)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        aplicarModo(intent)
    }

    override fun onStart() {
        super.onStart()
        // La cámara reabre con el ON_START del ciclo de vida, que llega después de este método.
        if (detenida) {
            detenida = false
            Arranque.alVolver(window.decorView)
        }
    }

    override fun onStop() {
        super.onStop()
        detenida = true
        vm.alDetenerse()
    }

    private fun aplicarModo(intent: Intent?) {
        val modo = when (intent?.getStringExtra(EXTRA_MODO)) {
            MODO_VENTA -> Modo.VENTA
            MODO_CONTEO -> Modo.CONTEO
            else -> return
        }
        vm.cambiarModo(modo)
    }

    companion object {
        /** El modo con que abre (de los atajos): [MODO_VENTA] o [MODO_CONTEO]. */
        const val EXTRA_MODO = "modo"
        const val MODO_VENTA = "venta"
        const val MODO_CONTEO = "conteo"

        /** La barra de pestañas de abajo (`NavigationBar` de Material 3): para estimar el visor la primera vez. */
        private const val ALTO_PESTANAS_DP = 80

        private const val CLAVE_ESCANER_VISIBLE = "escaner_visible"
    }
}

/**
 * Si se enlaza la cámara en `onCreate`: en un arranque nuevo siempre (abre en Escanear); al restaurar, solo si la pestaña
 * guardada era la del escáner ([escanerVisible]; null = no se guardó, p. ej. de una versión anterior). Si nadie confirma
 * lo enlazado, el controlador lo suelta solo.
 */
internal fun enlazarAlCrear(restaurando: Boolean, escanerVisible: Boolean?): Boolean = !restaurando || escanerVisible == true

/** El testTag que aparece con el primer cuadro analizado (lo esperan el Baseline Profile y Macrobenchmark). */
const val ETIQUETA_ESCANEANDO = "escaneando"

/**
 * El primer cuadro es solo el fondo negro (lo mismo que la ventana): el sistema quita la pantalla de inicio cuanto
 * antes y [contenido] se compone en el cuadro siguiente, mientras la cámara (enlazada en `onCreate`) sigue abriendo.
 * Encima va la marca [ETIQUETA_ESCANEANDO] y los testTags se exponen como ids para UiAutomator.
 */
@Composable
internal fun ContenidoEnDosCuadros(controlador: ControladorEscaner, contenido: @Composable () -> Unit) {
    var completo by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { testTagsAsResourceId = true },
    ) {
        if (completo) contenido()
        MarcaEscaneando(controlador)
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        completo = true
    }
}

/** Un punto invisible con testTag [ETIQUETA_ESCANEANDO] mientras se escanea y ya hubo un cuadro analizado en esta apertura. */
@Composable
private fun MarcaEscaneando(controlador: ControladorEscaner) {
    val estado = controlador.estado.collectAsStateWithLifecycle()
    val escaneando by remember(estado) {
        derivedStateOf {
            val e = estado.value
            e.apertura.marcas.containsKey(PasoApertura.PRIMER_ANALISIS) &&
                (e.fase == Fase.ESCANEANDO || e.fase == Fase.VIENDO || e.fase == Fase.LEIDO || e.fase == Fase.PAUSADO)
        }
    }
    if (escaneando) Spacer(Modifier.size(1.dp).testTag(ETIQUETA_ESCANEANDO))
}
