package mx.papeleriapos.android.caja

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Un servicio de mentira para las pruebas: su llave ECDSA P-256 y cómo contesta la identidad, como `Descubrimiento.cs`. */
class ServicioDePrueba(val llaveDeLaCaja: String = "llave-de-prueba-" + "x".repeat(40)) {
    val llaves: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** La llave pública en base64 de SubjectPublicKeyInfo, como la manda el alta. */
    val llavePublica: String = Base64.getEncoder().encodeToString(llaves.public.encoded)

    /** Lo que contestaría `GET /api/identidad` desde [direccion] a [reto] para [cajaId]; con [otraLlave], firmado por otro servicio. */
    fun identidad(cajaId: String, reto: String, direccion: Direccion, otraLlave: KeyPair? = null, pruebaMal: Boolean = false): IdentidadServicioDto {
        val prueba = Identidad.pruebaEsperada(llaveDeLaCaja, cajaId, reto, direccion).let { if (pruebaMal) it.reversed() else it }
        val mensaje = "papeleria-pos/identidad-del-servicio/1\n${cajaId.lowercase()}\n$reto\n${direccion.firmable}\n$prueba".toByteArray()
        val firmante = Signature.getInstance("SHA256withECDSA")
        firmante.initSign((otraLlave ?: llaves).private)
        firmante.update(mensaje)
        return IdentidadServicioDto(prueba.lowercase(), llavePublica, Base64.getEncoder().encodeToString(derAP1363(firmante.sign())))
    }

    companion object {
        /** La firma DER de Java como los 64 bytes r ‖ s (IEEE P1363) que manda .NET. */
        fun derAP1363(der: ByteArray): ByteArray {
            var i = 2
            fun entero(): ByteArray {
                require(der[i] == 0x02.toByte())
                val largo = der[i + 1].toInt()
                val bytes = der.copyOfRange(i + 2, i + 2 + largo)
                i += 2 + largo
                val sinCeros = bytes.dropWhile { it == 0.toByte() }.toByteArray()
                return ByteArray(32 - sinCeros.size) + sinCeros
            }
            val r = entero()
            val s = entero()
            return r + s
        }
    }
}
