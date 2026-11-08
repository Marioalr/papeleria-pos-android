package mx.papeleriapos.android.caja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/** La prueba de que un servicio encontrado en la red es la caja principal de este celular (`Descubrimiento.cs` del POS). */
class IdentidadTest {
    private val servicio = ServicioDePrueba()
    private val cajaId = "0190C5A2-0000-7000-8000-00000000C0A1"
    private val direccion = Direccion("192.168.0.10", 5080)

    @Test
    fun `la huella y la prueba se calculan como en el POS`() {
        // SHA-256 de «abc», en mayúsculas: lo que guarda el servicio de la llave.
        assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", Identidad.huellaDeLaLlave("abc"))
        val prueba = Identidad.pruebaEsperada("abc", cajaId, "reto-de-prueba-1234", direccion)
        assertEquals(64, prueba.length)
        assertTrue(prueba.all { it.isDigit() || it in 'A'..'F' })
        // El id de la caja va en minúsculas (Guid «D»), así que da lo mismo cómo lo escriba la app.
        assertEquals(prueba, Identidad.pruebaEsperada("abc", cajaId.lowercase(), "reto-de-prueba-1234", direccion))
    }

    @Test
    fun `acepta la respuesta del servicio de verdad y rechaza las demas`() {
        val reto = Identidad.nuevoReto()
        assertTrue(reto.length in 16..128 && reto.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        val buena = servicio.identidad(cajaId, reto, direccion)
        assertTrue(Identidad.esLaCajaPrincipal(buena, servicio.llaveDeLaCaja, servicio.llavePublica, cajaId, reto, direccion))
        // Otro reto (una respuesta repetida), otra dirección (alguien en medio), otra llave de caja: no.
        assertFalse(Identidad.esLaCajaPrincipal(buena, servicio.llaveDeLaCaja, servicio.llavePublica, cajaId, Identidad.nuevoReto(), direccion))
        assertFalse(Identidad.esLaCajaPrincipal(buena, servicio.llaveDeLaCaja, servicio.llavePublica, cajaId, reto, Direccion("192.168.0.11", 5080)))
        assertFalse(Identidad.esLaCajaPrincipal(buena, "otra-llave", servicio.llavePublica, cajaId, reto, direccion))
        // La prueba bien pero firmada por otro servicio (una copia de la base en otra PC): no.
        val otra = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val firmadaPorOtro = servicio.identidad(cajaId, reto, direccion, otraLlave = otra)
        assertFalse(Identidad.esLaCajaPrincipal(firmadaPorOtro, servicio.llaveDeLaCaja, servicio.llavePublica, cajaId, reto, direccion))
        // Sin firma, o con basura, tampoco.
        assertFalse(Identidad.esLaCajaPrincipal(buena.copy(firma = null), servicio.llaveDeLaCaja, servicio.llavePublica, cajaId, reto, direccion))
        assertFalse(Identidad.esLaCajaPrincipal(buena.copy(firma = "no-es-base64!"), servicio.llaveDeLaCaja, servicio.llavePublica, cajaId, reto, direccion))
        assertFalse(Identidad.esLaCajaPrincipal(servicio.identidad(cajaId, reto, direccion, pruebaMal = true), servicio.llaveDeLaCaja, servicio.llavePublica, cajaId, reto, direccion))
    }

    @Test
    fun `con un servicio de antes, sin llave publica guardada, basta la prueba`() {
        val reto = Identidad.nuevoReto()
        val respuesta = servicio.identidad(cajaId, reto, direccion).copy(firma = null, llaveDelServicio = null)
        assertTrue(Identidad.esLaCajaPrincipal(respuesta, servicio.llaveDeLaCaja, null, cajaId, reto, direccion))
        assertFalse(Identidad.esLaCajaPrincipal(respuesta.copy(prueba = "00"), servicio.llaveDeLaCaja, null, cajaId, reto, direccion))
    }
}
