package mx.papeleriapos.android.caja

import kotlinx.coroutines.runBlocking
import mx.papeleriapos.android.copia.Copia
import mx.papeleriapos.android.copia.CopiaTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Las llamadas a la API de la caja principal: la llave en el encabezado, lo que contesta y cómo dice que no. */
class ClienteCajaTest {
    private val servidor = MockWebServer()
    private val cliente = ClienteCaja(msRespuesta = 2_000)
    private lateinit var direccion: Direccion

    @Before
    fun arrancar() {
        servidor.start()
        direccion = Direccion("127.0.0.1", servidor.port)
    }

    @After
    fun apagar() = servidor.shutdown()

    @Test
    fun `el estado sin llave y el latido con ella`() = runBlocking {
        servidor.enqueue(MockResponse().setBody("""{"servicio":"Papelería POS","version":"1.2.3","sucursales":["Matriz"],"versionDatos":41,"prueba":false,"versionExistencias":7,"base":"b","formatoCopia":5,"red":{"internet":true},"tiendaNueva":false,"unaSolaCaja":false,"avisoRaro":"ignorado"}"""))
        val e = cliente.estado(direccion)
        assertEquals("Papelería POS", e.servicio)
        assertEquals(41L, e.versionDatos)
        assertEquals(7L, e.versionExistencias)
        assertEquals(5, e.formatoCopia)
        val pedido = servidor.takeRequest()
        assertEquals("/api/estado", pedido.path)
        assertNull(pedido.getHeader(ClienteCaja.ENCABEZADO_LLAVE))

        servidor.enqueue(MockResponse().setBody("""{"servicio":"Papelería POS","version":"1.2.3","sucursales":[]}"""))
        cliente.estado(direccion, llave = "LLAVE", cajaId = "c1")
        val latido = servidor.takeRequest()
        assertEquals("/api/estado?caja=c1", latido.path)
        assertEquals("LLAVE", latido.getHeader(ClienteCaja.ENCABEZADO_LLAVE))
    }

    @Test
    fun `el alta manda lo que el servicio espera y guarda lo que contesta`() = runBlocking {
        servidor.enqueue(MockResponse().setBody("""[{"id":"s1","nombre":"Matriz","cajas":[{"id":"c1","nombre":"Caja 1","serie":"A","equipo":"PC-CAJA"}]}]"""))
        val sucursales = cliente.opcionesDeAlta(direccion, "luis", "1234")
        assertEquals("Matriz", sucursales[0].nombre)
        assertEquals("PC-CAJA", sucursales[0].cajas[0].equipo)
        val opciones = servidor.takeRequest()
        assertEquals("/api/alta/opciones", opciones.path)
        assertEquals("""{"usuario":"luis","pin":"1234"}""", opciones.body.readUtf8())

        servidor.enqueue(MockResponse().setBody("""{"caja":{"id":"c9","nombre":"Celular de Luis","serie":"CEL","sucursalId":"s1","sucursal":"Matriz","equipo":"Pixel 8 Pro"},"llave":"LLAVE-NUEVA","llaveDelServicio":"PUB"}"""))
        val alta = cliente.darDeAlta(direccion, SolicitudAltaCaja("luis", "1234", "s1", null, "Celular de Luis", "CEL", "Pixel 8 Pro"))
        assertEquals("LLAVE-NUEVA", alta.llave)
        assertEquals("PUB", alta.llaveDelServicio)
        assertEquals("CEL", alta.caja.serie)
        val cuerpo = servidor.takeRequest().body.readUtf8()
        assertFalse("Sin cajaId: una caja nueva", """"cajaId":"""" in cuerpo)
        assertTrue(cuerpo, """"equipo":"Pixel 8 Pro"""" in cuerpo)
    }

    @Test
    fun `los cambios se piden con todas las versiones de la copia`() = runBlocking {
        val copia = Copia.dePaquete(JsonDelPos.decodeFromString(PaqueteDto.serializer(), CopiaTest.textoDelPaquete()), 0L)
        servidor.enqueue(MockResponse().setBody("""{"paqueteCompleto":null,"version":42,"versionExistencias":7,"ultimoFolio":0,"productos":[],"quitados":[],"codigos":[],"existencias":[]}"""))
        val c = cliente.cambios(direccion, "LLAVE", copia)
        assertEquals(42L, c.version)
        assertNull(c.paqueteCompleto)
        assertEquals(
            "/api/cajas/${copia.caja.id}/cambios?desde=41&existencias=7&base=0190c5a2-0000-7000-8000-00000000ba5e&formato=5&vendidos=3&elegidos=2&promociones=ABCDEF0123456789",
            servidor.takeRequest().path,
        )
    }

    @Test
    fun `cuando el servicio dice que no, su mensaje llega tal cual y el 401 sin alta se distingue`() = runBlocking {
        servidor.enqueue(MockResponse().setResponseCode(400).setBody("""{"mensaje":"La serie CEL ya es de otra caja.","codigo":""}"""))
        try {
            cliente.darDeAlta(direccion, SolicitudAltaCaja("luis", "1234", "s1", null, "Celular", "CEL", "Pixel"))
            fail()
        } catch (e: ErrorDelPos) {
            assertEquals(400, e.http)
            assertEquals("La serie CEL ya es de otra caja.", e.message)
            assertNull(e.codigo)
            assertFalse(e.sinAlta)
        }
        servidor.enqueue(MockResponse().setResponseCode(401).setBody("""{"mensaje":"Esta PC no está dada de alta como caja: el dueño la da de alta con su PIN.","codigo":"caja-sin-alta"}"""))
        try {
            cliente.estado(direccion, "LLAVE-VIEJA", "c1")
            fail()
        } catch (e: ErrorDelPos) {
            assertTrue(e.sinAlta)
        }
        // Sin JSON, un mensaje genérico.
        servidor.enqueue(MockResponse().setResponseCode(503).setBody("mantenimiento"))
        try {
            cliente.estado(direccion)
            fail()
        } catch (e: ErrorDelPos) {
            assertEquals("El servicio contestó 503.", e.message)
        }
    }

    @Test
    fun `si no contesta o contesta basura es un error de red, no del servicio`() = runBlocking {
        servidor.enqueue(MockResponse().setBody("<html>no soy el pos</html>"))
        try {
            cliente.estado(direccion)
            fail()
        } catch (e: IOException) {
            assertTrue(e.message!!.startsWith("No se entendió"))
        }
        servidor.shutdown()
        try {
            cliente.estado(direccion)
            fail()
        } catch (e: IOException) {
            // Conexión rechazada.
        }
    }
}
