package mx.papeleriapos.android.caja

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra y descifra la llave de la caja antes de guardarla; las pruebas usan uno transparente. */
interface Cifrador {
    fun cifrar(texto: String): String

    /** null si no se puede descifrar (otra llave del Keystore, texto roto): el celular tiene que darse de alta otra vez. */
    fun descifrar(texto: String): String?

    /** Para las pruebas: guarda tal cual. */
    object Transparente : Cifrador {
        override fun cifrar(texto: String) = texto
        override fun descifrar(texto: String) = texto
    }
}

/**
 * La llave de la caja cifrada con una llave AES-256 (GCM) del Android Keystore (docs/propuesta-interfaz.md, «Dar de
 * alta el celular»: «guardar la llave que da el servicio, cifrada con el Android Keystore»). La llave AES no sale del
 * hardware (o del TEE) y es de esta app en este celular: un respaldo o una copia de los archivos de la app no sirve en
 * otro. Lo guardado es «iv:cifrado», en base64. No pide la huella para usarla: la app ya la pidió al entrar.
 */
class CifradorKeystore(private val alias: String = ALIAS) : Cifrador {
    private fun llave(): SecretKey {
        val almacen = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (almacen.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generador.generateKey()
    }

    override fun cifrar(texto: String): String {
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.ENCRYPT_MODE, llave())
        val cifrado = cifrador.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cifrador.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cifrado, Base64.NO_WRAP)
    }

    override fun descifrar(texto: String): String? {
        val partes = texto.split(':')
        if (partes.size != 2) return null
        return try {
            val cifrador = Cipher.getInstance(TRANSFORMACION)
            cifrador.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, Base64.decode(partes[0], Base64.NO_WRAP)))
            String(cifrador.doFinal(Base64.decode(partes[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            // La llave del Keystore cambió (se restauró la app en otro celular, se borró el bloqueo…): sin alta.
            null
        }
    }

    /** Olvida la llave AES: lo cifrado con ella ya no se puede leer. */
    fun borrar() {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
        } catch (e: Exception) {
            // Si no estaba, no hay nada que borrar.
        }
    }

    companion object {
        const val ALIAS = "caja-llave"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
    }
}
