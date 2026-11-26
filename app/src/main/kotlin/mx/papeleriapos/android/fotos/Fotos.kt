package mx.papeleriapos.android.fotos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.archivo.escribirAtomico
import mx.papeleriapos.android.caja.CanalDeFotos
import mx.papeleriapos.android.caja.FotoGuardadaDto
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import okio.ByteString.Companion.decodeBase64
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * La foto del producto desde el celular (0.5.3; docs/celular-como-caja.md del POS, paso 12). El dueño la toma con la cámara
 * o la elige de las del celular; la app la achica a un JPEG de 960 px y la manda solo a la caja principal, por la red de la
 * tienda. El servicio guarda una de 240 px, que es la que ven todas las cajas y la que este celular guarda en su memoria
 * propia. La original no se guarda en la app ni sale a ningún otro lado.
 */

/** Las fotos ya guardadas (las de 240 px que contesta la caja principal), en la memoria propia de la app, por variante y huella. */
class AlmacenFotos(private val carpeta: File) {
    private fun archivo(varianteId: String, huella: String) = File(carpeta, "${limpio(varianteId)}-${limpio(huella)}.jpg")

    fun leer(varianteId: String, huella: String): ByteArray? = try {
        archivo(varianteId, huella).takeIf { it.isFile }?.readBytes()
    } catch (e: IOException) {
        null
    }

    /** Guarda la de esa huella y borra las de antes de ese producto (ya no son su foto). */
    fun guardar(varianteId: String, huella: String, jpeg: ByteArray) {
        try {
            carpeta.mkdirs()
            borrar(varianteId)
            archivo(varianteId, huella).escribirAtomico(jpeg)
        } catch (e: IOException) {
            // Sin lugar en el disco: se baja otra vez cuando haga falta.
        }
    }

    fun borrar(varianteId: String) {
        val prefijo = limpio(varianteId) + "-"
        carpeta.listFiles { f -> f.name.startsWith(prefijo) }?.forEach { it.delete() }
    }

    /** Solo letras, cifras y guiones: lo que llega de la caja nunca arma una ruta. */
    private fun limpio(texto: String) = texto.lowercase().filter { it.isLetterOrDigit() || it == '-' }.take(64)

    companion object {
        const val CARPETA = "fotos"
    }
}

/** Poner, quitar y enseñar fotos: junta la caja principal ([CanalDeFotos]) con lo guardado en el celular ([AlmacenFotos]). */
class Fotos(private val canal: CanalDeFotos, private val almacen: AlmacenFotos) {
    /** El JPEG de la foto de esa variante con esa huella: del celular o, la primera vez, de la caja principal. null si no se pudo. */
    suspend fun imagen(varianteId: String, huella: String): ByteArray? = withContext(Dispatchers.IO) {
        almacen.leer(varianteId, huella) ?: try {
            canal.bajarFoto(varianteId)?.also { almacen.guardar(varianteId, huella, it) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /** Manda [jpeg] (ya achicado); la que contesta la caja principal queda guardada en el celular. */
    suspend fun poner(varianteId: String, jpeg: ByteArray): FotoGuardadaDto {
        val guardada = canal.ponerFoto(varianteId, jpeg)
        withContext(Dispatchers.IO) {
            // Con okio: java.util.Base64 no existe antes de Android 8.
            guardada.jpegBase64.decodeBase64()?.toByteArray()?.takeIf { it.isNotEmpty() }
                ?.let { almacen.guardar(varianteId, guardada.huella, it) }
        }
        return guardada
    }

    suspend fun quitar(varianteId: String) {
        canal.quitarFoto(varianteId)
        withContext(Dispatchers.IO) { almacen.borrar(varianteId) }
    }
}

/** Deja la foto como la pide el servicio: JPEG de 960 px por su lado más largo, calidad 85, ya enderezada. */
object PreparacionDeFoto {
    /** Lo que manda también la página del conteo: el servicio guarda 240 px y parte del doble o más para dejarla nítida. */
    const val LADO = 960
    const val CALIDAD = 85

    /** El tamaño con el lado más largo en [lado], sin deformarla; una más chica no se agranda. */
    fun tamano(ancho: Int, alto: Int, lado: Int = LADO): Pair<Int, Int> {
        val mayor = max(ancho, alto)
        if (mayor <= lado || mayor <= 0) return ancho to alto
        val escala = lado.toDouble() / mayor
        return max(1, (ancho * escala).roundToInt()) to max(1, (alto * escala).roundToInt())
    }

    /**
     * Lee la imagen de [uri] (la que tomó la cámara o la que se eligió: JPG, PNG, WebP, HEIC…), la endereza y la deja en JPEG.
     * El servicio no lee HEIC ni AVIF: por eso siempre sale JPEG.
     */
    suspend fun preparar(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        val mapa = try {
            if (Build.VERSION.SDK_INT >= 28) conDecodificador(context, uri) else conFabrica(context, uri)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("No se pudo leer la imagen", e)
        }
        ByteArrayOutputStream(256 * 1024).also {
            if (!mapa.compress(Bitmap.CompressFormat.JPEG, CALIDAD, it)) throw IOException("No se pudo preparar la imagen")
            mapa.recycle()
        }.toByteArray()
    }

    /** Android 9 o más: endereza sola con la orientación de la imagen y la lee ya achicada. */
    @androidx.annotation.RequiresApi(28)
    private fun conDecodificador(context: Context, uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decodificador, info, _ ->
            val (ancho, alto) = tamano(info.size.width, info.size.height)
            decodificador.setTargetSize(ancho, alto)
            decodificador.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun conFabrica(context: Context, uri: Uri): Bitmap {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) } ?: throw IOException("No se pudo abrir la imagen")
        var muestra = 1
        while (max(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= LADO) muestra *= 2
        val leida = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
        } ?: throw IOException("No se reconoce la imagen")
        val grados = context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val (ancho, alto) = tamano(leida.width, leida.height)
        val matriz = Matrix().apply {
            postScale(ancho.toFloat() / leida.width, alto.toFloat() / leida.height)
            if (grados != 0f) postRotate(grados)
        }
        return Bitmap.createBitmap(leida, 0, 0, leida.width, leida.height, matriz, true)
    }
}
