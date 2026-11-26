package mx.papeleriapos.android.fotos

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.caja.ProductoVentaDto
import mx.papeleriapos.android.copia.Copia
import mx.papeleriapos.android.ui.ALTO_TOCABLE
import mx.papeleriapos.android.ajustes.Nota
import java.io.File
import java.io.IOException
import java.text.Normalizer

/*
 * «Fotos de productos» (0.5.3), en el menú «Este celular»: una tarea aparte, como contar. La lista de los productos de la
 * copia (de fábrica, los que no tienen foto), se busca por nombre o por código, y en cada uno se toma la foto con la cámara
 * del celular o se elige de las que ya tiene. Lo último que se escaneó en la venta sale arriba: se escanea el producto, se
 * abre esta pantalla y ahí está.
 */

/** Cómo se deja lista una imagen para mandarla; las pruebas ponen la suya. */
fun interface PreparadorDeFoto {
    suspend fun preparar(uri: Uri): ByteArray
}

/** Sin acentos ni mayúsculas, para buscar. */
internal fun sinAcentos(texto: String): String =
    Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

/**
 * Los productos de [copia] que se enseñan: con [texto], los que traen todos sus pedazos en el nombre o tienen ese código;
 * con [soloSinFoto], solo los que no tienen foto. [ultimo] (la variante de lo último escaneado) va primero si está.
 */
internal fun productosParaFotos(copia: Copia, texto: String, soloSinFoto: Boolean, ultimo: String?): List<ProductoVentaDto> {
    val pedazos = sinAcentos(texto.trim()).split(' ').filter { it.isNotEmpty() }
    val deEseCodigo = if (pedazos.size == 1) copia.codigos.filter { it.codigo.equals(texto.trim(), ignoreCase = true) }.map { it.varianteId.lowercase() }.toSet() else emptySet()
    val lista = copia.productos.filter { p ->
        val id = p.varianteId.lowercase()
        val coincide = pedazos.isEmpty() || id in deEseCodigo || sinAcentos(p.descripcion).let { d -> pedazos.all { it in d } }
        coincide && (!soloSinFoto || copia.huellaDe(id) == null || id.equals(ultimo, ignoreCase = true))
    }.sortedBy { sinAcentos(it.descripcion) }
    val primero = lista.firstOrNull { it.varianteId.equals(ultimo, ignoreCase = true) } ?: return lista
    return listOf(primero) + lista.filter { it !== primero }
}

/**
 * @param copia La copia del catálogo; null: el celular no está dado de alta (o todavía se lee).
 * @param ultimo La variante de lo último que se escaneó en la venta, si hay: sale arriba.
 */
@Composable
fun PantallaFotos(
    copia: Copia?,
    ultimo: String?,
    fotos: Fotos,
    modifier: Modifier = Modifier,
    preparador: PreparadorDeFoto? = null,
) {
    if (copia == null) {
        Column(modifier.fillMaxSize().padding(16.dp).testTag("pantalla-fotos")) {
            Text("Sin la copia del catálogo no hay productos a los que ponerles foto.", style = MaterialTheme.typography.bodyLarge)
            Nota("Da de alta este celular en Este celular › La conexión.")
        }
        return
    }
    var texto by rememberSaveable { mutableStateOf("") }
    var soloSinFoto by rememberSaveable { mutableStateOf(true) }
    var elegido by rememberSaveable { mutableStateOf<String?>(null) }
    val producto = elegido?.let { id -> copia.productos.firstOrNull { it.varianteId.equals(id, ignoreCase = true) } }
    if (producto != null) {
        BackHandler { elegido = null }
        FichaDeFoto(producto, copia.huellaDe(producto.varianteId), fotos, preparador, modifier) { elegido = null }
        return
    }
    val lista = remember(copia, texto, soloSinFoto, ultimo) { productosParaFotos(copia, texto, soloSinFoto, ultimo) }
    Column(modifier.fillMaxSize().imePadding().testTag("pantalla-fotos")) {
        Text(
            "${copia.fotos.size} de ${copia.productos.size} productos con foto",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag("fotos-cuantas"),
        )
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            singleLine = true,
            placeholder = { Text("Nombre o código") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("fotos-buscar"),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(soloSinFoto, role = Role.Checkbox) { soloSinFoto = it }
                .padding(horizontal = 16.dp)
                .testTag("fotos-solo-sin"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = soloSinFoto, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text("Solo los que no tienen foto", style = MaterialTheme.typography.bodyLarge)
        }
        if (lista.isEmpty()) {
            Text(
                if (texto.isBlank()) "Todos los productos tienen foto." else "Nada con «$texto».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).testTag("fotos-vacia"),
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("fotos-lista")) {
            items(lista, key = { it.varianteId }) { p ->
                val huella = copia.huellaDe(p.varianteId)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(role = Role.Button) { elegido = p.varianteId }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("foto-de-${p.varianteId}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Miniatura(p, huella, fotos, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.descripcion, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                "Lo último que escaneaste".takeIf { p.varianteId.equals(ultimo, ignoreCase = true) },
                                if (huella == null) "Sin foto" else "Con foto",
                                p.codigo,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** La foto guardada (o las iniciales del producto, como en la caja), cuadrada. */
@Composable
private fun Miniatura(p: ProductoVentaDto, huella: String?, fotos: Fotos, lado: Dp) {
    val imagen by produceState<ImageBitmap?>(null, p.varianteId, huella) {
        value = if (huella == null) null else withContext(Dispatchers.IO) {
            fotos.imagen(p.varianteId, huella)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    }
    val forma = RoundedCornerShape(if (lado > 100.dp) 20.dp else 12.dp)
    val foto = imagen
    if (foto != null) {
        Image(foto, contentDescription = "Foto de ${p.descripcion}", contentScale = ContentScale.Fit, modifier = Modifier.size(lado).clip(forma).background(MaterialTheme.colorScheme.surfaceVariant).testTag("miniatura"))
    } else {
        val letras = p.descripcion.split(' ').filter { it.isNotEmpty() && it[0].isLetter() }.take(2).joinToString("") { it.take(1).uppercase() }
        Box(Modifier.size(lado).clip(forma).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(letras, style = if (lado > 100.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** La ficha de un producto: su foto en grande y tomarla, elegirla o quitarla. */
@Composable
private fun FichaDeFoto(p: ProductoVentaDto, huella: String?, fotos: Fotos, preparador: PreparadorDeFoto?, modifier: Modifier, alCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val preparar = preparador ?: remember(contexto) { PreparadorDeFoto { PreparacionDeFoto.preparar(contexto.applicationContext, it) } }
    var ocupada by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var hecho by remember { mutableStateOf<String?>(null) }
    // La imagen ya preparada que no se pudo mandar: «Volver a intentar» no pide tomarla otra vez.
    var pendiente by remember { mutableStateOf<ByteArray?>(null) }

    fun mandar(jpeg: ByteArray) {
        alcance.launch {
            ocupada = "Guardando la foto en la caja principal…"
            error = null
            hecho = null
            try {
                fotos.poner(p.varianteId, jpeg)
                pendiente = null
                hecho = "Foto guardada: ya la ven todas las cajas."
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSeMando) {
                pendiente = jpeg
                error = e.message ?: "La caja principal no la aceptó."
            } finally {
                ocupada = null
            }
        }
    }

    fun usar(uri: Uri, borrar: File? = null) {
        alcance.launch {
            ocupada = "Preparando la foto…"
            error = null
            hecho = null
            val jpeg = try {
                preparar.preparar(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ocupada = null
                error = "No se pudo leer esa imagen: toma la foto otra vez o elige otra."
                return@launch
            } finally {
                // La original que dejó la cámara no se queda en el celular.
                borrar?.delete()
            }
            mandar(jpeg)
        }
    }

    var captura by remember { mutableStateOf<File?>(null) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tomada ->
        val archivo = captura
        captura = null
        if (tomada && archivo != null) usar(uriDe(contexto, archivo), borrar = archivo) else archivo?.delete()
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) usar(uri) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("ficha-foto"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(p.descripcion, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        p.codigo?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth()) }
        Miniatura(p, huella, fotos, 240.dp)
        Text(
            if (huella == null) "Sin foto" else "La foto que ven las cajas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("ficha-estado"),
        )
        (ocupada ?: hecho)?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("ficha-aviso"))
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("ficha-error"))
        }
        val libre = ocupada == null
        pendiente?.let { jpeg ->
            Button(onClick = { mandar(jpeg) }, enabled = libre, modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("foto-reintentar")) {
                Text("Volver a intentar")
            }
        }
        Button(
            onClick = {
                try {
                    val archivo = File(File(contexto.cacheDir, CARPETA_CAPTURAS).apply { mkdirs() }, "captura-${System.currentTimeMillis()}.jpg")
                    captura = archivo
                    camara.launch(uriDe(contexto, archivo))
                } catch (e: Exception) {
                    // Sin app de cámara, o sin el permiso de la cámara para esta app.
                    captura = null
                    error = "No se pudo abrir la cámara del celular: elige la foto de las que ya tiene."
                }
            },
            enabled = libre,
            modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("foto-tomar"),
        ) { Text(if (huella == null) "Tomar foto" else "Tomar otra foto") }
        OutlinedButton(
            onClick = {
                try {
                    galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (e: Exception) {
                    error = "Este celular no deja elegir una foto desde aquí."
                }
            },
            enabled = libre,
            modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("foto-elegir"),
        ) { Text("Elegir del celular") }
        if (huella != null) {
            TextButton(
                onClick = {
                    alcance.launch {
                        ocupada = "Quitando la foto…"
                        error = null
                        hecho = null
                        try {
                            fotos.quitar(p.varianteId)
                            hecho = "Foto quitada: las cajas vuelven a enseñar sus iniciales."
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: NoSeMando) {
                            error = e.message ?: "La caja principal no la quitó."
                        } finally {
                            ocupada = null
                        }
                    }
                },
                enabled = libre,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("foto-quitar"),
            ) { Text("Quitar la foto") }
        }
        OutlinedButton(onClick = alCerrar, modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("ficha-regresar")) { Text("Regresar a la lista") }
        Nota("La foto se achica y se manda solo a la caja principal, por la red de la tienda. La original no se guarda en la app.")
    }
}

/** Donde la cámara del celular deja la foto recién tomada, mientras se prepara (se borra enseguida). */
internal const val CARPETA_CAPTURAS = "capturas"

private fun uriDe(contexto: Context, archivo: File): Uri =
    try {
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", archivo)
    } catch (e: IllegalArgumentException) {
        throw IOException("No se pudo preparar el archivo de la foto", e)
    }
