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
 * Cotizar y cobrar contra la API del POS (docs/celular-como-caja.md del POS, paso 10): la ruta, la llave, el cuerpo tal
 * cual lo espera el servicio y lo que contesta, con los ejemplos de ese documento.
 */
class CobrarClienteTest {
    private val servidor = MockWebServer()
    private val cliente = ClienteCaja(msRespuesta = 2_000)
    private lateinit var direccion: Direccion

    private val renglones = listOf(
        RenglonMandado(varianteId = "v1", presentacionId = "p1", codigo = "7509990100019", cantidadMilesimas = 3000),
        RenglonMandado(descripcion = "Engargolado", precioCentavos = 3550, cantidadMilesimas = 1000),
    )

    @Before
    fun arrancar() {
        servidor.start()
        direccion = Direccion("127.0.0.1", servidor.port)
    }

    @After
    fun apagar() = servidor.shutdown()

    @Test
    fun `cotizar manda los renglones sin cobros y lee el total, el efectivo y el redondeo`() = runBlocking {
        servidor.enqueue(
            MockResponse().setBody(
                """{"lineas": [{"varianteId": "v1", "presentacionId": "p1", "descripcion": "Lápiz del 2 Pelikan Triangular", "cantidadMilesimas": 3000,
                    "precioCentavos": 800, "importeCentavos": 2400, "ahorroMayoreoCentavos": 0, "ahorroPromocionCentavos": 0, "promocion": null, "articuloComun": false},
                   {"varianteId": null, "presentacionId": null, "descripcion": "Engargolado", "cantidadMilesimas": 1000,
                    "precioCentavos": 3550, "importeCentavos": 3550, "ahorroMayoreoCentavos": 0, "ahorroPromocionCentavos": 0, "promocion": null, "articuloComun": true}],
                  "renglones": 2, "articulos": 4, "totalCentavos": 5950, "efectivoCentavos": 6000, "redondeoCentavos": 50, "pago": null}""",
            ),
        )
        val c = cliente.cotizar(direccion, "LLAVE", "cel", SolicitudCotizar(renglones))
        assertEquals(5950L, c.totalCentavos)
        assertEquals(6000L, c.efectivoCentavos)
        assertEquals(50L, c.redondeoCentavos)
        assertEquals(4.0, c.articulos, 0.0)
        assertEquals(listOf(false, true), c.lineas.map { it.articuloComun })
        assertEquals(800L, c.lineas[0].precioCentavos)
        assertNull(c.pago)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/cajas/cel/cotizar", pedido.path)
        assertEquals("LLAVE", pedido.getHeader(ClienteCaja.ENCABEZADO_LLAVE))
        assertEquals(
            """{"renglones":[{"varianteId":"v1","presentacionId":"p1","codigo":"7509990100019","cantidadMilesimas":3000},""" +
                """{"cantidadMilesimas":1000,"descripcion":"Engargolado","precioCentavos":3550}]}""",
            pedido.body.readUtf8(),
        )
    }

    @Test
    fun `cobrar manda el Id, los cobros, la caja y el total visto, y lee la venta`() = runBlocking {
        servidor.enqueue(
            MockResponse().setBody(
                """{"id": "t1", "folio": "A-000001", "folioEleventa": null, "cajaId": "c1", "caja": "Caja 1", "cajero": "Encargado de prueba",
                  "renglones": 2, "articulos": 4, "totalCentavos": 5950, "redondeoCentavos": 50, "cobradoCentavos": 6000,
                  "recibidoCentavos": 10000, "cambioCentavos": 4000,
                  "cobros": [{"forma": 0, "montoCentavos": 6000, "recibidoCentavos": 10000, "cambioCentavos": 4000, "referencia": null}],
                  "yaCobrada": false, "ticket": {"encabezado": "Papelería de prueba", "lineas": [{"descripcion": "x"}]}}""",
            ),
        )
        val solicitud = SolicitudCobrar("t1", renglones, listOf(CobroPedido(0, 10_000)), caja = "c1", totalVisto = 5950)
        val v = cliente.cobrar(direccion, "LLAVE", "cel", solicitud)
        assertEquals("A-000001", v.folio)
        assertEquals("Caja 1", v.caja)
        assertEquals(6000L, v.cobradoCentavos)
        assertEquals(4000L, v.cambioCentavos)
        assertFalse(v.yaCobrada)
        assertEquals(0, v.cobros.single().forma)
        val pedido = servidor.takeRequest()
        assertEquals("/api/cajas/cel/cobrar", pedido.path)
        assertEquals("LLAVE", pedido.getHeader(ClienteCaja.ENCABEZADO_LLAVE))
        assertEquals(
            """{"id":"t1","renglones":[{"varianteId":"v1","presentacionId":"p1","codigo":"7509990100019","cantidadMilesimas":3000},""" +
                """{"cantidadMilesimas":1000,"descripcion":"Engargolado","precioCentavos":3550}],""" +
                """"cobros":[{"forma":0,"montoCentavos":10000}],"caja":"c1","totalVisto":5950}""",
            pedido.body.readUtf8(),
        )
    }

    @Test
    fun `la tarjeta lleva su referencia y lo ya cobrado se entiende`() = runBlocking {
        servidor.enqueue(MockResponse().setBody("""{"id":"t1","folio":"A-000001","caja":"Caja 1","totalCentavos":5950,"cobradoCentavos":5950,"cobros":[{"forma":1,"montoCentavos":5950,"referencia":"4321"}],"yaCobrada":true}"""))
        val v = cliente.cobrar(direccion, "LLAVE", "cel", SolicitudCobrar("t1", renglones, listOf(CobroPedido(1, 5950, "4321")), totalVisto = 5950))
        assertTrue(v.yaCobrada)
        assertEquals("4321", v.cobros.single().referencia)
        assertTrue(servidor.takeRequest().body.readUtf8().contains(""""cobros":[{"forma":1,"montoCentavos":5950,"referencia":"4321"}]"""))
    }

    @Test
    fun `los errores del cobro llegan con su codigo y su mensaje para ensenarlo`() = runBlocking {
        servidor.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"mensaje":"El total cambió: ahora es ${'$'}90.00 (el celular decía ${'$'}135.00). Revísalo con el cliente y vuelve a cobrar.","codigo":"total-distinto"}"""),
        )
        try {
            cliente.cobrar(direccion, "LLAVE", "cel", SolicitudCobrar("t1", renglones, listOf(CobroPedido(0, 10_000)), totalVisto = 13_500))
            fail("Debió decir que el total cambió")
        } catch (e: ErrorDelPos) {
            assertEquals(409, e.http)
            assertEquals("total-distinto", e.codigo)
            assertTrue(e.message!!.startsWith("El total cambió"))
        }
        // Un servicio anterior al paso 10 no tiene la ruta: 404 sin cuerpo.
        servidor.enqueue(MockResponse().setResponseCode(404))
        try {
            cliente.cotizar(direccion, "LLAVE", "cel", SolicitudCotizar(renglones))
            fail("Debió fallar")
        } catch (e: ErrorDelPos) {
            assertEquals(404, e.http)
            assertNull(e.codigo)
        }
    }

    @Test
    fun `las cajas dicen cual tiene turno abierto`() = runBlocking {
        servidor.enqueue(MockResponse().setBody("""[{"id":"c1","nombre":"Caja 1","serie":"A","recibe":false,"atiende":null,"esperan":0,"turnoAbierto":true},{"id":"c2","nombre":"Caja 2","serie":"B","recibe":true}]"""))
        val cajas = cliente.paraMandar(direccion, "LLAVE", "cel")
        assertEquals(listOf(true, false), cajas.map { it.turnoAbierto })
    }

    @Test
    fun `las cajas dicen si pueden imprimir, el cobro dice que paso con el ticket y se puede pedir despues`() = runBlocking {
        servidor.enqueue(MockResponse().setBody("""[{"id": "c1", "nombre": "Caja 1", "serie": "", "recibe": true, "atiende": "Ana López", "esperan": 0, "turnoAbierto": true,
            "impresion": {"puede": true, "por": "caja", "codigo": null, "mensaje": "Sale en la impresora de la Caja 1.", "automatico": true}}]"""))
        val caja = cliente.paraMandar(direccion, "LLAVE", "cel").single()
        assertEquals(true, caja.impresion!!.puede)
        assertEquals("caja", caja.impresion!!.por)
        assertEquals("Sale en la impresora de la Caja 1.", caja.impresion!!.mensaje)

        servidor.enqueue(MockResponse().setBody("""{"id": "t1", "folio": "000123", "caja": "Caja 1", "yaCobrada": false, "ticket": {"x": 1},
            "impresion": {"estado": "impreso", "codigo": null, "mensaje": "Salió en la impresora de la Caja 1.", "cajaId": "c1", "caja": "Caja 1", "por": "red", "salio": true}}"""))
        val venta = cliente.cobrar(direccion, "LLAVE", "cel", SolicitudCobrar("t1", renglones, listOf(CobroPedido(0, 10_000)), totalVisto = 5950, imprimir = true))
        assertTrue(venta.impresion!!.salio)
        assertEquals("impreso", venta.impresion!!.estado)
        assertTrue(servidor.takeRequest().let { servidor.takeRequest() }.body.readUtf8().endsWith(""""totalVisto":5950,"imprimir":true}"""))

        servidor.enqueue(MockResponse().setBody("""{"estado": "no-impreso", "codigo": "sin-papel", "mensaje": "Se acabó el papel de la impresora de la Caja 1: pon papel y pídelo otra vez.", "caja": "Caja 1", "por": "red", "salio": false}"""))
        val despues = cliente.imprimirVenta(direccion, "LLAVE", "cel", "t1", SolicitudImprimir("p1"))
        assertEquals("sin-papel", despues.codigo)
        assertFalse(despues.salio)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/cajas/cel/ventas/t1/imprimir", pedido.path)
        assertEquals("""{"id":"p1"}""", pedido.body.readUtf8())
    }
}
