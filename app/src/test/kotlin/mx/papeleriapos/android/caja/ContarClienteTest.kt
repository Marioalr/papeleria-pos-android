package mx.papeleriapos.android.caja

import kotlinx.coroutines.runBlocking
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

/**
 * Mandar un conteo contra la API del POS (docs/celular-como-caja.md del POS, «Mandar un conteo a una caja»): la ruta, la
 * llave, el cuerpo tal cual lo espera el servicio y lo que contesta, con los ejemplos de ese documento.
 */
class ContarClienteTest {
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
    fun `para-contar lee si recibe, quien atiende y por que no`() = runBlocking {
        servidor.enqueue(
            MockResponse().setBody(
                """[{"id": "c2", "nombre": "Caja 2", "serie": "B", "recibe": true, "atiende": "Ana López", "codigo": null, "porQue": null, "pendientes": 0},
                   {"id": "c1", "nombre": "Caja principal", "serie": "A", "recibe": false, "atiende": "Pedro",
                    "codigo": "sin-permiso", "porQue": "Pedro tiene el turno de la Caja principal y no puede aceptar conteos.", "pendientes": 1}]""",
            ),
        )
        val cajas = cliente.paraContar(direccion, "llave", "cel1")
        val pedido = servidor.takeRequest()
        assertEquals("/api/cajas/cel1/para-contar", pedido.path)
        assertEquals("GET", pedido.method)
        assertEquals("llave", pedido.getHeader(ClienteCaja.ENCABEZADO_LLAVE))
        assertTrue(cajas[0].recibe)
        assertEquals("Ana López", cajas[0].atiende)
        assertFalse(cajas[1].recibe)
        assertEquals("sin-permiso", cajas[1].codigo)
        assertEquals(1, cajas[1].pendientes)
    }

    @Test
    fun `mandar-conteo manda la lista como la espera el POS y lee como quedo`() = runBlocking {
        servidor.enqueue(MockResponse().setBody("""{"id": "l1", "destino": "c2", "caja": "Caja 2", "estado": "recibida", "contadoUtc": "2026-11-22T17:42:00Z", "productos": 2, "aplicados": 0, "renglones": []}"""))
        val r = cliente.mandarConteo(
            direccion, "llave", "cel1",
            SolicitudMandarConteo(
                id = "l1", destino = "c2", contadoUtc = "2026-11-22T17:42:00Z", enviadoUtc = "2026-11-22T17:50:00Z",
                renglones = listOf(
                    RenglonContado("v1", "p-caja", null, 2000, "2026-11-22T17:40:00Z"),
                    RenglonContado(codigo = "7501234567890", cantidadMilesimas = 5000),
                ),
            ),
        )
        val pedido = servidor.takeRequest()
        assertEquals("/api/cajas/cel1/mandar-conteo", pedido.path)
        assertEquals("POST", pedido.method)
        val cuerpo = pedido.body.readUtf8()
        assertTrue(cuerpo, cuerpo.contains(""""id":"l1""""))
        assertTrue(cuerpo, cuerpo.contains(""""destino":"c2""""))
        assertTrue(cuerpo, cuerpo.contains(""""cantidadMilesimas":2000"""))
        assertTrue(cuerpo, cuerpo.contains(""""enviadoUtc":"2026-11-22T17:50:00Z""""))
        assertTrue(cuerpo, cuerpo.contains(""""contadoUtc":"2026-11-22T17:40:00Z""""))
        assertFalse("Sin usuario, no va el campo", cuerpo.contains("usuarioId"))
        assertEquals("recibida", r.estado)
        assertEquals("Caja 2", r.caja)
    }

    @Test
    fun `conteos lee cada producto con lo contado, lo aceptado y la nota`() = runBlocking {
        servidor.enqueue(
            MockResponse().setBody(
                """[{"id": "l1", "destino": "c2", "caja": "Caja 2", "estado": "aceptada-con-cambios",
                     "contadoUtc": "2026-11-22T17:42:00Z", "recibidoUtc": "2026-11-22T17:43:10Z", "resueltoUtc": "2026-11-22T18:05:00Z",
                     "resolvio": "Ana López", "motivo": null, "productos": 3, "aplicados": 1,
                     "renglones": [{"varianteId": "v1", "descripcion": "Lápiz HB", "contado": 29, "aceptado": 27, "aplicado": true, "nota": "En la caja lo cambiaron a 27."},
                                   {"varianteId": "v2", "descripcion": "Goma blanca", "contado": 3, "aceptado": null, "aplicado": false, "nota": "Lo quitaron en la caja: no cambió.", "eleventa": "por-capturar"}]}]""",
            ),
        )
        val c = cliente.conteos(direccion, "llave", "cel1").single()
        assertEquals("/api/cajas/cel1/conteos", servidor.takeRequest().path)
        assertEquals("aceptada-con-cambios", c.estado)
        assertEquals("Ana López", c.resolvio)
        assertEquals(27.0, c.renglones[0].aceptado!!, 0.0)
        assertNull(c.renglones[1].aceptado)
        assertEquals("por-capturar", c.renglones[1].eleventa)
    }

    @Test
    fun `un 409 del piloto llega con su mensaje y su codigo`() = runBlocking {
        servidor.enqueue(MockResponse().setResponseCode(409).setBody("""{"mensaje": "Mientras dure el piloto con eleventa, el inventario se lleva en eleventa.", "codigo": "piloto-eleventa"}"""))
        try {
            cliente.mandarConteo(direccion, "llave", "cel1", SolicitudMandarConteo("l1", "c2", null, renglones = listOf(RenglonContado(codigo = "x", cantidadMilesimas = 1000))))
            fail("Debió fallar")
        } catch (e: ErrorDelPos) {
            assertEquals("piloto-eleventa", e.codigo)
            assertTrue(e.message!!.contains("piloto"))
        }
    }
}
