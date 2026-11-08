package mx.papeleriapos.android.caja

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * La prueba de que un servicio encontrado en la red es la caja principal que dio de alta a este celular
 * (docs/celular-como-caja.md, «Si la caja principal cambia de IP»; `Descubrimiento.cs` del POS). Antes de mandarle la
 * llave a una dirección nueva se le manda un reto y se revisan dos cosas de lo que contesta:
 *
 * 1. La prueba: HMAC-SHA256 sobre «papeleria-pos/identidad/1», el id de la caja, el reto y la dirección, con la huella de
 *    la llave de la caja (SHA-256 de la llave, en hexadecimal y mayúsculas) como llave del HMAC. Solo la tiene el servicio
 *    que dio de alta a esta caja; la llave nunca viaja. La dirección evita que otro equipo pase la pregunta y se quede en
 *    medio; el reto nuevo evita repetir una respuesta vieja.
 * 2. La firma: ECDSA P-256 con SHA-256 (64 bytes, formato IEEE P1363) de lo mismo más la prueba, con la llave pública del
 *    servicio que se guardó en el alta. Una copia de la base en otra PC sabe la huella, pero no firma como el servicio.
 */
object Identidad {
    private const val VERSION = "papeleria-pos/identidad/1"
    private const val VERSION_DE_LA_FIRMA = "papeleria-pos/identidad-del-servicio/1"

    /** Un reto nuevo, al azar: 32 bytes en base64 para URL, sin «=». */
    fun nuevoReto(aleatorio: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(32).also { aleatorio.nextBytes(it) }
        // Okio (viene con OkHttp): java.util.Base64 es de la API 26 y la app corre desde la 24.
        return bytes.toByteString().base64Url().trimEnd('=')
    }

    /** La huella de la llave de la caja, lo único que el servicio guarda de ella. */
    fun huellaDeLaLlave(llave: String): String =
        MessageDigest.getInstance("SHA-256").digest(llave.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }

    /** La prueba que debe contestar el servicio de verdad. */
    fun pruebaEsperada(llave: String, cajaId: String, reto: String, direccion: Direccion): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(huellaDeLaLlave(llave).toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val mensaje = "$VERSION\n${cajaId.lowercase()}\n$reto\n${direccion.firmable}"
        return mac.doFinal(mensaje.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    /**
     * Si lo que contestó el servicio en [direccion] prueba que es la caja principal de esta caja: la prueba coincide (sin
     * importar mayúsculas, comparada en tiempo constante) y la firma es de [llaveDelServicio] (la del alta, en base64 de
     * SubjectPublicKeyInfo). Sin llave del servicio guardada (un alta de antes) basta la prueba.
     */
    fun esLaCajaPrincipal(
        respuesta: IdentidadServicioDto,
        llave: String,
        llaveDelServicio: String?,
        cajaId: String,
        reto: String,
        direccion: Direccion,
    ): Boolean {
        val esperada = pruebaEsperada(llave, cajaId, reto, direccion)
        val contesto = respuesta.prueba.uppercase()
        if (!MessageDigest.isEqual(contesto.toByteArray(Charsets.US_ASCII), esperada.toByteArray(Charsets.US_ASCII))) return false
        if (llaveDelServicio.isNullOrEmpty()) return true
        val mensaje = "$VERSION_DE_LA_FIRMA\n${cajaId.lowercase()}\n$reto\n${direccion.firmable}\n$esperada"
        return firmaValida(llaveDelServicio, mensaje.toByteArray(Charsets.UTF_8), respuesta.firma)
    }

    /** Si [firma] (base64, P1363 de 64 bytes) es de esa llave pública sobre ese mensaje; cualquier cosa rara, no. */
    fun firmaValida(llavePublica: String, mensaje: ByteArray, firma: String?): Boolean {
        if (firma.isNullOrEmpty() || firma.length > 1024 || llavePublica.length > 1024) return false
        return try {
            val publica = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(llavePublica.decodeBase64()?.toByteArray() ?: return false))
            val bytes = firma.decodeBase64()?.toByteArray() ?: return false
            if (bytes.size != 64) return false
            val verificador = Signature.getInstance("SHA256withECDSA")
            verificador.initVerify(publica)
            verificador.update(mensaje)
            verificador.verify(p1363ADer(bytes))
        } catch (e: Exception) {
            // IllegalArgumentException del base64, InvalidKeySpecException, SignatureException…
            false
        }
    }

    /** La firma de 64 bytes (r ‖ s) como la secuencia DER que entiende `Signature` de Java. */
    internal fun p1363ADer(rs: ByteArray): ByteArray {
        fun entero(bytes: ByteArray): ByteArray {
            var inicio = 0
            while (inicio < bytes.size - 1 && bytes[inicio] == 0.toByte()) inicio++
            val sinCeros = bytes.copyOfRange(inicio, bytes.size)
            val conSigno = if (sinCeros[0] < 0) byteArrayOf(0) + sinCeros else sinCeros
            return byteArrayOf(0x02, conSigno.size.toByte()) + conSigno
        }
        val r = entero(rs.copyOfRange(0, 32))
        val s = entero(rs.copyOfRange(32, 64))
        return byteArrayOf(0x30, (r.size + s.size).toByte()) + r + s
    }
}
