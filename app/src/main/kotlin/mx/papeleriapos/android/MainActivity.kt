package mx.papeleriapos.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mx.papeleriapos.android.caja.Conexion
import mx.papeleriapos.android.entrada.Entrada
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.android.rapido.Accesos
import mx.papeleriapos.android.rapido.EsteCelular
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.android.ui.AppEscaner
import mx.papeleriapos.android.ui.Destino
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.camara.ControladorCamara
import mx.papeleriapos.escaner.ui.LocalAlVisorListo
import mx.papeleriapos.escaner.ui.LocalSoloVisor

/**
 * La app. Para abrir al instante (docs/escaner.md, «Apertura»): marca cada paso, enlaza la cámara en `onCreate` (abre en
 * `onStart`, mientras Compose dibuja), el primer cuadro trae solo el visor sobre negro (como la ventana, sin destello) y lo
 * demás llega cuando el visor ya entregó su superficie, y el TTFD es el primer cuadro analizado.
 *
 * Los accesos del ícono (el fijo «Revisar precio», en res/xml/shortcuts.xml, y los dinámicos «Armar ticket» y «Contar»,
 * [mx.papeleriapos.android.rapido.Accesos]) traen [EXTRA_MODO]; el sistema recrea la tarea con ellos, pero lo leído está
 * guardado ([mx.papeleriapos.android.lista.ListasCompartidas]) y no se pierde. Abren con la acción y la categoría del
 * ícono (MAIN y LAUNCHER), para que después el ícono traiga esta misma tarea y no apile otra MainActivity; el modo se lee
 * del extra, sin mirar la acción. Los dinámicos se revisan en cada apertura, después del primer cuadro analizado.
 *
 * «Abrir la app» del escaneo rápido trae además [EXTRA_PRODUCTO], con NEW_TASK | CLEAR_TOP | SINGLE_TOP: si la app ya
 * está abierta, la misma instancia lo recibe en [onNewIntent] ([aplicarIntent]).
 *
 * Al restaurar con estado guardado (Android mató el proceso en segundo plano y el empleado toca el ícono: el frío más
 * común al sacar el celular del bolsillo) también enlaza en `onCreate` si la pantalla a la vista era la principal.
 */
class MainActivity : ComponentActivity() {
    private val vm: EscanerViewModel by viewModels()
    private var detenida = false

    /** El PIN del celular con la pantalla de bloqueo (Android 8 y antes, o sin huella): si lo pasó, se entra. */
    private val credencial = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) Entrada.desbloquear()
    }
    private val pedirCredencial: (Intent) -> Unit = { credencial.launch(it) }
    /**
     * Si la pantalla a la vista es la principal (la del escáner), para guardarlo en el estado de la Activity. La interfaz
     * completa llega en el segundo cuadro: hasta entonces vale lo restaurado.
     */
    private var escanerVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        Arranque.alCrearActividad()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val controlador = vm.controlador
        Arranque.reportarAlPrimerAnalisis(this, controlador)
        if (savedInstanceState == null) aplicarIntent(intent, vm)
        val guardado = savedInstanceState?.takeIf { it.containsKey(CLAVE_ESCANER_VISIBLE) }?.getBoolean(CLAVE_ESCANER_VISIBLE)
        escanerVisible = guardado ?: (savedInstanceState == null)
        if (enlazarAlCrear(restaurando = savedInstanceState != null, escanerVisible = guardado)) {
            vm.enlazarTemprano(this, ControladorCamara.PANTALLA_PRINCIPAL, descuentoAbajoDp = ALTO_PESTANAS_DP)
        }
        Arranque.marcarPrimerCuadroUi(window.decorView)
        setContent {
            ContenidoEnDosCuadros(controlador) {
                TemaEscaner {
                    AppEscaner(
                        vm = vm,
                        alCambiarDestino = { escanerVisible = it == Destino.PRINCIPAL },
                        alPedirEntrar = { Entrada.pedir(this, pedirCredencial) },
                    )
                }
            }
        }
        revisarAccesos(controlador)
    }

    /**
     * La conexión con la caja principal late solo con la app al frente, y la entrada con la huella se pide al abrir con
     * el celular dado de alta. Las dos leen el alta del disco en su hilo: aquí solo se espera a saber si hay alta, fuera
     * de la apertura de la cámara (ya enlazada en onCreate).
     */
    private fun conexionAlFrente() {
        val conexion = Conexion.del(applicationContext)
        conexion.alFrente()
        lifecycleScope.launch {
            val estado = conexion.estado.first { it.leida }
            Entrada.alFrente(this@MainActivity, conAlta = estado.alta != null, pedirCredencial = pedirCredencial)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(CLAVE_ESCANER_VISIBLE, escanerVisible)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        aplicarIntent(intent, vm)
    }

    override fun onStart() {
        super.onStart()
        // La cámara reabre con el ON_START del ciclo de vida, que llega después de este método.
        if (detenida) {
            detenida = false
            Arranque.alVolver(window.decorView)
        }
        conexionAlFrente()
    }

    override fun onStop() {
        super.onStop()
        detenida = true
        vm.alDetenerse()
        Conexion.del(applicationContext).alFondo()
        Entrada.alFondo()
    }

    /**
     * Los accesos dinámicos del ícono ([Accesos.sincronizar]), cada vez que se crea la Activity (la guía pide revisarlos
     * en cada arranque: no pasan a un celular nuevo): después del primer cuadro analizado, para no estorbarle a la cámara
     * (o en cuanto se sabe que no va a abrir: sin permiso o con error), y fuera del hilo principal, porque son llamadas al
     * sistema. Solo publica si el menú cambió. No desde la Application: el proceso también arranca para el mosaico y para
     * el escaneo rápido.
     */
    private fun revisarAccesos(controlador: ControladorEscaner) {
        val app = applicationContext
        lifecycleScope.launch {
            controlador.estado.first(::listoParaLosAccesos)
            withContext(Dispatchers.IO) {
                try {
                    Accesos.sincronizar(app, EsteCelular.ARMA_TICKETS, EsteCelular.SE_PUEDE_CONTAR)
                } catch (e: RuntimeException) {
                    // P. ej. con el usuario bloqueado; se revisa otra vez en la próxima apertura.
                    Log.w("Escaner", "No se pudieron revisar los accesos del ícono", e)
                }
            }
        }
    }

    companion object {
        /**
         * El modo con que abre (de los accesos del ícono y de «Abrir la app»): [MODO_PRECIO], [MODO_TICKET] o
         * [MODO_CONTEO]. [MODO_VENTA] es el de los accesos que se anclaron con la 0.1 (ya apagados en shortcuts.xml); se
         * sigue entendiendo como el ticket.
         */
        const val EXTRA_MODO = "modo"
        const val MODO_PRECIO = "precio"
        const val MODO_TICKET = "ticket"
        const val MODO_VENTA = "venta"
        const val MODO_CONTEO = "conteo"

        /**
         * De «Abrir la app» del escaneo rápido: la clave del producto que se veía o, si no estaba en el catálogo, el código
         * que se leyó. La app lo enseña en Precio ([EscanerViewModel.ensenarDeFuera]).
         */
        const val EXTRA_PRODUCTO = "producto"

        /** El hueco de abajo, donde van los modos (el alto de la barra de pestañas de antes): para estimar el visor la primera vez. */
        private const val ALTO_PESTANAS_DP = 80

        private const val CLAVE_ESCANER_VISIBLE = "escaner_visible"
    }
}

/**
 * Lo que trae el intent con que se abre o se trae la app: el modo ([MainActivity.EXTRA_MODO]) y, de «Abrir la app» del
 * escaneo rápido, el producto ([MainActivity.EXTRA_PRODUCTO]), que lleva a la principal en Precio con él. Nada si
 * Recientes relanzó una tarea vacía (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, p. ej. después de reiniciar): su intent es
 * el de entonces y traería un producto de hace días. La Activity lo aplica en `onCreate` sin estado guardado y en
 * `onNewIntent`.
 */
internal fun aplicarIntent(intent: Intent?, vm: EscanerViewModel) {
    if (intent == null || (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
    ModoApp.deExtra(intent.getStringExtra(MainActivity.EXTRA_MODO))?.let(vm::cambiarModoApp)
    intent.getStringExtra(MainActivity.EXTRA_PRODUCTO)?.takeIf { it.isNotBlank() }?.let(vm::ensenarDeFuera)
}

/**
 * Ya se pueden revisar los accesos del ícono: hubo un primer cuadro analizado (la cámara ya abrió y no se le estorba) o
 * la cámara no va a abrir (sin permiso, con error).
 */
internal fun listoParaLosAccesos(e: EstadoEscaner): Boolean =
    e.apertura.marcas.containsKey(PasoApertura.PRIMER_ANALISIS) || e.fase == Fase.SIN_PERMISO || e.fase == Fase.ERROR

/**
 * Si se enlaza la cámara en `onCreate`: en un arranque nuevo siempre (abre en la principal); al restaurar, solo si la
 * pantalla guardada era la principal ([escanerVisible]; null = no se guardó, p. ej. de una versión anterior). Si nadie
 * confirma lo enlazado, el controlador lo suelta solo.
 */
internal fun enlazarAlCrear(restaurando: Boolean, escanerVisible: Boolean?): Boolean = !restaurando || escanerVisible == true

/** El testTag que aparece con el primer cuadro analizado (lo esperan el Baseline Profile y Macrobenchmark). */
const val ETIQUETA_ESCANEANDO = "escaneando"

/**
 * Primero el visor, luego lo demás. El primer cuadro trae [contenido] con [LocalSoloVisor]: sobre el fondo negro (lo
 * mismo que la ventana) solo el visor de la cámara, ya en su lugar y su tamaño, y la barra de pestañas vacía. Así el
 * SurfaceView del visor tiene su superficie enseguida y la sesión de la cámara (enlazada en `onCreate`) no espera a que
 * se mida y se dibuje toda la interfaz: en el Pixel 8 Pro esa espera era de 160 ms en frío (docs/plan.md, experimento
 * 1). Lo demás se compone cuando el visor avisa ([LocalAlVisorListo]) o, si algo falla, a los [PLAZO_VISOR_MS].
 *
 * La caja de fondo lleva la marca [ETIQUETA_ESCANEANDO] y expone los testTags como ids para UiAutomator. La marca va en
 * la caja y no en un punto aparte: Compose no le pasa a UiAutomator un nodo que solo tiene testTag y no dibuja nada.
 */
@Composable
internal fun ContenidoEnDosCuadros(controlador: ControladorEscaner, contenido: @Composable () -> Unit) {
    var completo by remember { mutableStateOf(false) }
    val visorListo = remember { CompletableDeferred<Unit>() }
    val alVisorListo = remember<() -> Unit> { { visorListo.complete(Unit) } }
    val escaneando = escaneando(controlador)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { testTagsAsResourceId = true }
            .then(if (escaneando) Modifier.testTag(ETIQUETA_ESCANEANDO) else Modifier),
    ) {
        CompositionLocalProvider(LocalSoloVisor provides !completo, LocalAlVisorListo provides alVisorListo) {
            contenido()
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withTimeoutOrNull(PLAZO_VISOR_MS) { visorListo.await() }
        completo = true
    }
}

/** Lo más que se espera al visor antes de mostrar lo demás (sin cámara, con un error o si el enlace tarda de más). */
private const val PLAZO_VISOR_MS = 400L

/** Si se escanea y ya hubo un cuadro analizado en esta apertura (la marca [ETIQUETA_ESCANEANDO]). */
@Composable
private fun escaneando(controlador: ControladorEscaner): Boolean {
    val estado = controlador.estado.collectAsStateWithLifecycle()
    val escaneando by remember(estado) {
        derivedStateOf {
            val e = estado.value
            e.apertura.marcas.containsKey(PasoApertura.PRIMER_ANALISIS) &&
                (e.fase == Fase.ESCANEANDO || e.fase == Fase.VIENDO || e.fase == Fase.LEIDO || e.fase == Fase.PAUSADO)
        }
    }
    return escaneando
}
