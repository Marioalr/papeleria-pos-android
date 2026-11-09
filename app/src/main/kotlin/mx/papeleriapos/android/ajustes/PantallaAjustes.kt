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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.BuildConfig
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.camara.CsvFicha
import mx.papeleriapos.android.camara.FichaLente
import mx.papeleriapos.android.catalogo.EstadoCatalogo
import mx.papeleriapos.android.catalogo.FuenteDeProductos
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
import mx.papeleriapos.escaner.ResolucionAnalisis
import kotlin.math.roundToInt

/**
 * Ajustes › Herramientas de medición › El escáner: el motor, la franja, la cámara, el catálogo de prueba (los códigos de
 * la sesión de prueba) y el diagnóstico. Lo personal (vibración, bip, linterna) está en [PantallaAjustes].
 */
@Composable
fun PantallaAjustesEscaner(vm: EscanerViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val catalogo by vm.catalogo.collectAsStateWithLifecycle()
    val ficha by vm.ficha.collectAsStateWithLifecycle()
    val productosDePrueba by FuenteDeProductos.dePrueba.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    // El catálogo y la ficha de la cámara se cargan perezosos: aquí, para mostrarlos.
    LaunchedEffect(Unit) {
        vm.prepararCatalogo()
        vm.prepararFicha()
    }
    // El selector del sistema: sin permisos; el archivo se lee una vez y se copia a la memoria de la app.
    val elegirCatalogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.cargarCatalogo(uri)
    }
    ContenidoAjustes(
        config,
        alCambiar = vm::cambiarConfig,
        alRestablecer = vm::restablecerAjustes,
        catalogo = catalogo,
        alCargarCatalogo = { elegirCatalogo.launch(TIPOS_CATALOGO) },
        alUsarCatalogoDeEjemplo = vm::usarCatalogoDeEjemplo,
        ficha = ficha,
        productosDePrueba = productosDePrueba,
        alCambiarProductosDePrueba = { FuenteDeProductos.usarDePrueba(contexto, it) },
    )
}

/** Ajustes rápidos › el mosaico: en Android 13+ el diálogo del sistema; antes se explica cómo agregarlo a mano. */
@Composable
internal fun rememberAgregarMosaico(alMensaje: (String?) -> Unit): (() -> Unit)? {
    val contexto = LocalContext.current
    if (!puedePedirMosaico()) return null
    return remember(contexto) {
        {
            alMensaje(null)
            val pedido = pedirMosaico(contexto) { resultado -> alMensaje(mensajeMosaico(resultado)) }
            if (!pedido) alMensaje("No se pudo pedir. $COMO_AGREGAR_MOSAICO_A_MANO")
        }
    }
}

/** Texto o CSV (algunos proveedores dan el CSV como application/csv). */
private val TIPOS_CATALOGO = arrayOf("text/*", "application/csv")

/** Los ajustes del escáner, para medir; cada cambio se guarda y se aplica en caliente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoAjustes(
    config: ConfigEscaner,
    alCambiar: (ConfigEscaner) -> Unit,
    alRestablecer: () -> Unit,
    /** El catálogo de prueba cargado; null mientras carga. */
    catalogo: EstadoCatalogo? = null,
    alCargarCatalogo: () -> Unit = {},
    alUsarCatalogoDeEjemplo: () -> Unit = {},
    /** Las lentes traseras según Camera2; null mientras se leen. */
    ficha: List<FichaLente>? = null,
    /** Precio, Buscar y el ticket con los productos ficticios en lugar de la copia del catálogo. */
    productosDePrueba: Boolean = false,
    alCambiarProductosDePrueba: (Boolean) -> Unit = {},
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
        if (config.motor == Motor.LADO_A_LADO) {
            Nota(
                "zxing-cpp y ML Kit en cada cuadro sobre la misma franja, para compararlos con productos reales: la sesión " +
                    "de prueba y el diagnóstico dicen qué leyó cada uno y cuánto tardó. Más lento; solo para medir.",
            )
        }
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
        FilaInterruptor(
            titulo = "Leer también girados 90°",
            descripcion = "zxing-cpp prueba las dos orientaciones de la franja (los códigos de pie); cuesta más por cuadro. " +
                "ML Kit los lee girados de por sí.",
            valor = config.leerGirados,
            alCambiar = { alCambiar(config.copy(leerGirados = it)) },
            modifier = Modifier.testTag("ajuste-girados"),
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

        TituloSeccion("Cámara")
        Etiqueta("Lente")
        Opciones(Lente.entries, config.lente, { it.etiqueta }, { alCambiar(config.copy(lente = it)) })
        Etiqueta("Resolución del análisis")
        Opciones(
            ResolucionAnalisis.entries, config.resolucion, { it.etiqueta }, { alCambiar(config.copy(resolucion = it)) },
            modifier = Modifier.testTag("ajuste-resolucion"),
        )
        Nota(
            "720p basta con 2 px por módulo. Más resolución da más píxeles a los códigos chicos o lejanos y cuesta más " +
                "por cuadro (se ve en el diagnóstico); el visor se queda en 720p. La sesión de prueba guarda la de cada intento.",
        )
        Etiqueta("Ficha de la cámara")
        Text(
            textoFicha(ficha),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag("ficha-camara"),
        )
        Nota(
            "Lo que dice Camera2 de cada lente trasera: desde dónde enfoca, cuánto abarca ahí y con qué zoom abre la app. " +
                "Va con la medición de la sesión (ficha-camara.csv).",
        )
        FilaInterruptor(
            titulo = "Medir la luz en la mira",
            descripcion = "La exposición se ajusta al código, no al fondo. Apágalo para comparar con todo el cuadro.",
            valor = config.medirLuzEnMira,
            alCambiar = { alCambiar(config.copy(medirLuzEnMira = it)) },
            modifier = Modifier.testTag("ajuste-luz-mira"),
        )
        Deslizador(
            titulo = "Pausar sin códigos a la vista",
            valor = config.segundosParaPausar.toFloat(),
            rango = 0f..PreferenciasEscaner.MAX_SEGUNDOS_PAUSA.toFloat(),
            pasos = PreferenciasEscaner.MAX_SEGUNDOS_PAUSA / 10 - 1,
            texto = { if (it < 1f) "Nunca" else "Después de ${it.roundToInt()} s" },
            alTerminar = { alCambiar(config.copy(segundosParaPausar = it.roundToInt())) },
        )

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
            "Productos de prueba", productosDePrueba, alCambiarProductosDePrueba, Modifier.testTag("ajuste-productos-prueba"),
            descripcion = "Precio, Buscar y el ticket con los productos ficticios del prototipo en lugar de la copia del catálogo " +
                "de la caja. Para medir y probar sin la caja principal; también en el escaneo rápido.",
        )
        FilaInterruptor(
            "Mostrar diagnóstico", config.mostrarDiagnostico, { alCambiar(config.copy(mostrarDiagnostico = it)) },
            descripcion = "Cuadros por segundo, ms del motor, px por módulo, zoom…",
        )
        OutlinedButton(
            onClick = alRestablecer,
            modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).padding(top = 12.dp),
        ) { Text("Restablecer los ajustes del escáner") }
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

/** La ficha para Ajustes: una línea por lente, o qué pasa mientras no hay. */
internal fun textoFicha(ficha: List<FichaLente>?): String = when {
    ficha == null -> "Leyendo la cámara…"
    ficha.isEmpty() -> "Sin cámara trasera."
    else -> CsvFicha.resumen(ficha)
}

@Composable
internal fun Etiqueta(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

@Composable
internal fun Nota(texto: String) {
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
