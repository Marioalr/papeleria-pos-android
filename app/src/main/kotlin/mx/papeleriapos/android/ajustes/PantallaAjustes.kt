package mx.papeleriapos.android.ajustes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.BuildConfig
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.catalogo.EstadoCatalogo
import mx.papeleriapos.android.catalogo.OrigenCatalogo
import mx.papeleriapos.android.rapido.COMO_AGREGAR_MOSAICO_A_MANO
import mx.papeleriapos.android.rapido.mensajeMosaico
import mx.papeleriapos.android.rapido.pedirMosaico
import mx.papeleriapos.android.rapido.puedePedirMosaico
import mx.papeleriapos.android.ui.ALTO_TOCABLE
import mx.papeleriapos.android.ui.FilaInterruptor
import mx.papeleriapos.android.ui.Opciones
import mx.papeleriapos.android.ui.TituloSeccion
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.Lente
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.nucleo.Modo
import kotlin.math.roundToInt

@Composable
fun PantallaAjustes(vm: EscanerViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val catalogo by vm.catalogo.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var mensaje by remember { mutableStateOf<String?>(null) }
    // El catálogo se carga perezoso: aquí, para decir cuál está y cuántos códigos trae.
    LaunchedEffect(Unit) { vm.prepararCatalogo() }
    // El selector del sistema: sin permisos; el archivo se lee una vez y se copia a la memoria de la app.
    val elegirCatalogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.cargarCatalogo(uri)
    }
    ContenidoAjustes(
        config,
        alCambiar = vm::cambiarConfig,
        alRestablecer = vm::restablecerAjustes,
        // Android 13+: el diálogo del sistema; antes no se puede pedir y la pantalla explica cómo agregarlo a mano.
        alAgregarMosaico = if (!puedePedirMosaico()) null else {
            {
                mensaje = null
                val pedido = pedirMosaico(contexto) { resultado -> mensaje = mensajeMosaico(resultado) }
                if (!pedido) mensaje = "No se pudo pedir. $COMO_AGREGAR_MOSAICO_A_MANO"
            }
        },
        mensajeMosaico = mensaje,
        catalogo = catalogo,
        alCargarCatalogo = { elegirCatalogo.launch(TIPOS_CATALOGO) },
        alUsarCatalogoDeEjemplo = vm::usarCatalogoDeEjemplo,
    )
}

/** Texto o CSV (algunos proveedores dan el CSV como application/csv). */
private val TIPOS_CATALOGO = arrayOf("text/*", "application/csv")

/** Los ajustes del escáner; cada cambio se guarda y se aplica en caliente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoAjustes(
    config: ConfigEscaner,
    alCambiar: (ConfigEscaner) -> Unit,
    alRestablecer: () -> Unit,
    /** null = el celular no deja pedirlo desde la app (antes de Android 13): se explica cómo agregarlo a mano. */
    alAgregarMosaico: (() -> Unit)? = null,
    /** Lo que contestó el sistema la última vez que se pidió. */
    mensajeMosaico: String? = null,
    /** El catálogo de prueba cargado; null mientras carga. */
    catalogo: EstadoCatalogo? = null,
    alCargarCatalogo: () -> Unit = {},
    alUsarCatalogoDeEjemplo: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("pantalla-ajustes"),
    ) {
        TituloSeccion("Lectura")
        Etiqueta("Motor")
        Opciones(Motor.entries, config.motor, { it.etiqueta }, { alCambiar(config.copy(motor = it)) })
        Etiqueta("Modo")
        Opciones(Modo.entries, config.modo, { it.etiqueta }, { alCambiar(config.copy(modo = it)) })
        Etiqueta("Simbologías")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in PreferenciasEscaner.OFRECIDAS) {
                val elegida = s in config.simbologias
                FilterChip(
                    selected = elegida,
                    // Siempre queda al menos una.
                    onClick = {
                        val nuevas = if (elegida) config.simbologias - s else config.simbologias + s
                        if (nuevas.isNotEmpty()) alCambiar(config.copy(simbologias = nuevas))
                    },
                    label = { Text(s.nombre) },
                    modifier = Modifier.testTag("simbologia-${s.name}"),
                )
            }
        }
        Nota("En ML Kit, EAN-13 y UPC-A van juntas: pedir una trae la otra.")
        FilaInterruptor(
            titulo = "Leer solo la franja",
            descripcion = "Más rápido. Apágalo para comparar contra el cuadro completo.",
            valor = config.recortarFranja,
            alCambiar = { alCambiar(config.copy(recortarFranja = it)) },
            modifier = Modifier.testTag("ajuste-recortar"),
        )
        Deslizador(
            titulo = "Alto de la franja",
            valor = config.altoFranja,
            rango = PreferenciasEscaner.MIN_ALTO_FRANJA..PreferenciasEscaner.MAX_ALTO_FRANJA,
            pasos = 7,
            texto = { "${(it * 100).roundToInt()} % de la pantalla" },
            alTerminar = { alCambiar(config.copy(altoFranja = it)) },
        )
        FilaInterruptor(
            titulo = "Hasta 60 cuadros por segundo",
            descripcion = if (config.fps60) {
                "Si la cámara los da: cuadros más seguidos y menos movidos, pero gasta más batería."
            } else {
                "Hasta 30: gasta menos."
            },
            valor = config.fps60,
            alCambiar = { alCambiar(config.copy(fps60 = it)) },
        )
        FilaInterruptor(
            titulo = "Cuadros por segundo fijos",
            descripcion = if (config.fpsFijo) "Menos movido; con poca luz se ve más oscuro." else "Desde 15: más luz, pero más movido.",
            valor = config.fpsFijo,
            alCambiar = { alCambiar(config.copy(fpsFijo = it)) },
        )

        TituloSeccion("Confirmación")
        FilaInterruptor("Sonido", config.sonido, { alCambiar(config.copy(sonido = it)) }, Modifier.testTag("ajuste-sonido"))
        FilaInterruptor(
            "Vibración", config.vibracion, { alCambiar(config.copy(vibracion = it)) },
            descripcion = "Si el celular tiene apagada la respuesta táctil, no vibra.",
        )

        TituloSeccion("Cámara")
        Etiqueta("Lente")
        Opciones(Lente.entries, config.lente, { it.etiqueta }, { alCambiar(config.copy(lente = it)) })
        FilaInterruptor(
            "Linterna automática", config.linternaAutomatica, { alCambiar(config.copy(linternaAutomatica = it)) },
            descripcion = "La enciende sola si hay poca luz.",
        )
        Deslizador(
            titulo = "Pausar sin códigos a la vista",
            valor = config.segundosParaPausar.toFloat(),
            rango = 0f..PreferenciasEscaner.MAX_SEGUNDOS_PAUSA.toFloat(),
            pasos = PreferenciasEscaner.MAX_SEGUNDOS_PAUSA / 10 - 1,
            texto = { if (it < 1f) "Nunca" else "Después de ${it.roundToInt()} s" },
            alTerminar = { alCambiar(config.copy(segundosParaPausar = it.roundToInt())) },
        )

        TituloSeccion("Abrir rápido")
        Nota(
            "Un mosaico en Ajustes rápidos abre el escáner con un toque, aun con el celular bloqueado: desliza desde " +
                "arriba y tócalo.",
        )
        if (alAgregarMosaico != null) {
            OutlinedButton(
                onClick = alAgregarMosaico,
                modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("agregar-mosaico"),
            ) { Text("Agregar a Ajustes rápidos") }
        } else {
            Nota("Este celular no deja agregarlo desde la app. $COMO_AGREGAR_MOSAICO_A_MANO")
        }
        mensajeMosaico?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp).testTag("mensaje-mosaico")) }

        TituloSeccion("Catálogo de prueba")
        Text(textoCatalogo(catalogo), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("catalogo-cargado"))
        Nota(
            "Cada lectura se busca aquí, sin red, y la sesión de prueba anota si está. El archivo (texto o CSV, con el " +
                "código en la primera columna) se copia al celular y no sale de él.",
        )
        catalogo?.aviso?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("catalogo-aviso"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = alCargarCatalogo,
                enabled = catalogo?.cargando != true,
                modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE).testTag("catalogo-cargar"),
            ) { Text("Cargar archivo…") }
            OutlinedButton(
                onClick = alUsarCatalogoDeEjemplo,
                enabled = catalogo != null && catalogo.origen == OrigenCatalogo.ARCHIVO && !catalogo.cargando,
                modifier = Modifier.weight(1f).heightIn(min = ALTO_TOCABLE).testTag("catalogo-ejemplo"),
            ) { Text("Usar el de ejemplo") }
        }

        TituloSeccion("Pruebas")
        FilaInterruptor(
            "Mostrar diagnóstico", config.mostrarDiagnostico, { alCambiar(config.copy(mostrarDiagnostico = it)) },
            descripcion = "Cuadros por segundo, ms del motor, px por módulo, zoom…",
        )
        OutlinedButton(
            onClick = alRestablecer,
            modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).padding(top = 12.dp),
        ) { Text("Restablecer ajustes") }
        Nota("Versión ${BuildConfig.VERSION_NAME} · ${BuildConfig.COMMIT}")
    }
}

/** Cuál catálogo está cargado y cuántos códigos trae. */
internal fun textoCatalogo(catalogo: EstadoCatalogo?): String = when {
    catalogo == null -> "Cargando…"
    catalogo.cargando -> "Cargando el archivo…"
    catalogo.origen == OrigenCatalogo.EJEMPLO -> "El de ejemplo · ${codigos(catalogo.catalogo.tamano)}"
    else -> "${catalogo.nombre} · ${codigos(catalogo.catalogo.tamano)}"
}

private fun codigos(n: Int) = if (n == 1) "1 código" else "$n códigos"

@Composable
private fun Etiqueta(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

@Composable
private fun Nota(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** Un deslizador que guarda al soltarlo, no en cada paso del arrastre. */
@Composable
private fun Deslizador(
    titulo: String,
    valor: Float,
    rango: ClosedFloatingPointRange<Float>,
    pasos: Int,
    texto: (Float) -> String,
    alTerminar: (Float) -> Unit,
) {
    var local by remember(valor) { mutableFloatStateOf(valor) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge)
        Text(texto(local), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { alTerminar(local) },
            valueRange = rango,
            steps = pasos,
        )
    }
}
