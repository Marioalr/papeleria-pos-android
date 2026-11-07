package mx.papeleriapos.android.ajustes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.BuildConfig
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.principal.ContadoresRapido
import mx.papeleriapos.android.rapido.COMO_AGREGAR_MOSAICO_A_MANO
import mx.papeleriapos.android.ui.ALTO_TOCABLE
import mx.papeleriapos.android.ui.Destino
import mx.papeleriapos.android.ui.FilaDato
import mx.papeleriapos.android.ui.FilaInterruptor
import mx.papeleriapos.android.ui.FilaNavegacion
import mx.papeleriapos.android.ui.TituloSeccion
import mx.papeleriapos.escaner.ConfigEscaner

/**
 * Ajustes (docs/propuesta-interfaz.md, «Ajustes y herramientas de medición»): arriba lo de quien usa el celular (vibración
 * y bip, linterna automática, el botón rápido a la izquierda y las pistas), el mosaico para abrir rápido, los productos de
 * prueba y, al final, las herramientas de medición, que se quedan porque se sigue midiendo en la tienda.
 */
@Composable
fun PantallaAjustes(vm: EscanerViewModel, alAbrir: (Destino) -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val zurdo by vm.piso.zurdo.collectAsStateWithLifecycle()
    val contadores by vm.piso.contadores.collectAsStateWithLifecycle()
    val productos by vm.piso.catalogo.collectAsStateWithLifecycle()
    var mensajeMosaico by remember { mutableStateOf<String?>(null) }
    var pistasDeNuevo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.prepararProductos() }
    ContenidoAjustesPersonales(
        config = config,
        alCambiar = vm::cambiarConfig,
        zurdo = zurdo,
        alCambiarZurdo = vm.piso::cambiarZurdo,
        alVolverAVerPistas = {
            vm.piso.volverAVerPistas()
            pistasDeNuevo = true
        },
        pistasDeNuevo = pistasDeNuevo,
        alAgregarMosaico = rememberAgregarMosaico { mensajeMosaico = it },
        mensajeMosaico = mensajeMosaico,
        productos = productos,
        contadores = contadores,
        alAbrir = alAbrir,
    )
}

@Composable
fun ContenidoAjustesPersonales(
    config: ConfigEscaner,
    alCambiar: (ConfigEscaner) -> Unit,
    zurdo: Boolean,
    alCambiarZurdo: (Boolean) -> Unit,
    alVolverAVerPistas: () -> Unit,
    /** Ya se pidieron otra vez: el botón lo dice. */
    pistasDeNuevo: Boolean,
    /** null = el celular no deja pedirlo desde la app (antes de Android 13): se explica cómo agregarlo a mano. */
    alAgregarMosaico: (() -> Unit)?,
    /** Lo que contestó el sistema la última vez que se pidió. */
    mensajeMosaico: String?,
    /** Los productos de prueba; null mientras se cargan. */
    productos: CatalogoDeProductos?,
    contadores: ContadoresRapido,
    alAbrir: (Destino) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("pantalla-ajustes"),
    ) {
        TituloSeccion("Al escanear")
        FilaInterruptor(
            "Vibración", config.vibracion, { alCambiar(config.copy(vibracion = it)) },
            descripcion = "Vibra al leer. Si el celular tiene apagada la respuesta táctil, no vibra.",
        )
        FilaInterruptor(
            "Bip", config.sonido, { alCambiar(config.copy(sonido = it)) }, Modifier.testTag("ajuste-sonido"),
            descripcion = "Suena al leer.",
        )
        FilaInterruptor(
            "Linterna automática", config.linternaAutomatica, { alCambiar(config.copy(linternaAutomatica = it)) },
            descripcion = "La enciende sola si hay poca luz.",
        )

        TituloSeccion("El botón rápido")
        FilaInterruptor(
            "Botón rápido a la izquierda", zurdo, alCambiarZurdo, Modifier.testTag("ajuste-zurdo"),
            descripcion = "Para usarlo con la mano izquierda: va al principio de la barra y las opciones salen en espejo.",
        )
        OutlinedButton(
            onClick = alVolverAVerPistas,
            enabled = !pistasDeNuevo,
            modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("volver-a-ver-pistas"),
        ) { Text(if (pistasDeNuevo) "Las pistas saldrán otra vez" else "Volver a ver las pistas") }

        TituloSeccion("Abrir rápido")
        Nota(
            "El mosaico «Precio» de Ajustes rápidos enseña un precio con un toque, aun con el celular bloqueado: desliza " +
                "desde arriba y tócalo.",
        )
        if (alAgregarMosaico != null) {
            OutlinedButton(
                onClick = alAgregarMosaico,
                modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("agregar-mosaico"),
            ) { Text("Agregar a Ajustes rápidos") }
        } else {
            Nota("Este celular no deja agregarlo desde la app. $COMO_AGREGAR_MOSAICO_A_MANO")
        }
        mensajeMosaico?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp).testTag("mensaje-mosaico"))
        }

        TituloSeccion("Productos")
        Text(
            when {
                productos == null -> "Cargando…"
                else -> "${productos.tamano} productos de prueba"
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("productos-prueba"),
        )
        Nota(
            "Ficticios, para probar Precio, el ticket y la búsqueda sin red. Los de la tienda, con sus precios, llegan " +
                "cuando el celular se conecte al POS.",
        )

        TituloSeccion("Herramientas de medición")
        Nota("Para medir el escáner en la tienda: la sesión de prueba, el banco y las pruebas de estrés.")
        FilaNavegacion(
            "El escáner", { alAbrir(Destino.ESCANER) },
            descripcion = "Motor, franja, cámara, catálogo de prueba y diagnóstico",
            modifier = Modifier.testTag("abrir-escaner"),
        )
        FilaNavegacion("Sesión de prueba", { alAbrir(Destino.SESION) }, Modifier.testTag("abrir-sesion"))
        FilaNavegacion("Banco de pruebas", { alAbrir(Destino.BANCO) }, Modifier.testTag("abrir-banco"))
        FilaNavegacion("Pruebas de estrés", { alAbrir(Destino.ESTRES) }, Modifier.testTag("abrir-estres"))
        Etiqueta("El botón rápido, en este celular")
        for ((que, cuantas) in contadores.renglones()) FilaDato(que, cuantas.toString())
        Nota(
            "Sin el nombre de nadie: sirven para mover las fronteras del gesto si ↖ junta los errores. Salen solo con la " +
                "medición de la sesión de prueba (contadores-rapido.csv).",
        )
        Nota("Versión ${BuildConfig.VERSION_NAME} · ${BuildConfig.COMMIT}")
    }
}
