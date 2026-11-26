package mx.papeleriapos.android.principal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mx.papeleriapos.android.caja.CajaParaMandarDto
import mx.papeleriapos.android.caja.CanalDeCobrar
import mx.papeleriapos.android.caja.CobroHechoDto
import mx.papeleriapos.android.caja.CotizacionDto
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.caja.SolicitudCobrar
import mx.papeleriapos.android.caja.SolicitudCotizar
import mx.papeleriapos.android.caja.VentaCobradaDto
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Escala
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Cobrar en el celular (docs/celular-como-caja.md del POS, paso 10): el redondeo, lo que viaja y lo que pasa cuando no se puede. */
@OptIn(ExperimentalCoroutinesApi::class)
class CobroTest {
    private val cuaderno = Producto("7509990100019", "Cuaderno profesional cuadro chico 100 hojas", "Escolar", listOf(Escala(1, 4600)), 8.0)
    private val lapiz = Producto("7509990100026", "Lápiz HB No. 2", "Escolar", listOf(Escala(1, 550)), 140.0)
    private val catalogo = CatalogoDeProductos(listOf(cuaderno, lapiz))

    /** Un cuaderno y un lápiz: $51.50, que en efectivo son $52. */
    private fun ticket() = ListaLecturas()
        .sumar(cuaderno.clave, Simbologia.EAN_13, 1L, clave = cuaderno.clave)
        .sumar(lapiz.clave, Simbologia.EAN_13, 2L, clave = lapiz.clave)

    private val caja1 = CajaParaMandarDto("c1", "Caja 1", "A", recibe = true, turnoAbierto = true)
    private val caja2 = CajaParaMandarDto("c2", "Caja 2", "B", recibe = false, turnoAbierto = true)
    private val sinTurno = CajaParaMandarDto("c3", "Caja 3", "C", recibe = false, turnoAbierto = false)

    private class Canal(var cajas: List<CajaParaMandarDto>) : CanalDeCobrar {
        var total = 5150L
        var falla: NoSeMando? = null
        var fallaAlCobrar: NoSeMando? = null
        val cotizadas = ArrayList<SolicitudCotizar>()
        val cobradas = ArrayList<SolicitudCobrar>()

        override suspend fun cajasParaMandar(): List<CajaParaMandarDto> = cajas

        override suspend fun cotizar(solicitud: SolicitudCotizar): CotizacionDto {
            falla?.let { throw it }
            cotizadas.add(solicitud)
            val efectivo = Efectivo.aCobrar(total)
            return CotizacionDto(renglones = solicitud.renglones.size, articulos = 2.0, totalCentavos = total, efectivoCentavos = efectivo, redondeoCentavos = efectivo - total)
        }

        override suspend fun cobrar(solicitud: SolicitudCobrar): VentaCobradaDto {
            cobradas.add(solicitud)
            fallaAlCobrar?.let { throw it }
            val c = solicitud.cobros.single()
            val efectivo = Efectivo.aCobrar(total)
            return VentaCobradaDto(
                id = solicitud.id, folio = "A-000001", cajaId = solicitud.caja.orEmpty(), caja = "Caja 1", cajero = "Encargado de prueba",
                renglones = solicitud.renglones.size, articulos = 2.0, totalCentavos = total,
                cobradoCentavos = if (c.forma == 0) efectivo else total, recibidoCentavos = c.montoCentavos,
                cambioCentavos = if (c.forma == 0) c.montoCentavos - efectivo else 0,
                cobros = listOf(CobroHechoDto(c.forma, if (c.forma == 0) efectivo else total, c.montoCentavos, if (c.forma == 0) c.montoCentavos - efectivo else 0, c.referencia)),
            )
        }
    }

    private val hilo = StandardTestDispatcher()

    @Before
    fun preparar() = Dispatchers.setMain(hilo)

    @After
    fun limpiar() = Dispatchers.resetMain()

    private fun TestScope.cobrador(canal: Canal, ultima: String? = null, guardadas: MutableList<String> = ArrayList()): Cobrador {
        var n = 0
        return Cobrador(canal, this, { ultima }, { guardadas.add(it) }, IdsDeTicket { "id-${++n}" })
    }

    // ---- El redondeo al peso, como el POS ----

    @Test
    fun `el efectivo se redondea al peso mas cercano, los 50 centavos hacia arriba, y nunca menos de un peso`() {
        assertEquals(7000L, Efectivo.aCobrar(7049))
        assertEquals(7100L, Efectivo.aCobrar(7050))
        assertEquals(7000L, Efectivo.aCobrar(7000))
        assertEquals(6000L, Efectivo.aCobrar(5950))
        assertEquals("Algo de menos de 50 centavos se cobra a un peso", 100L, Efectivo.aCobrar(30))
        assertEquals(100L, Efectivo.aCobrar(50))
        assertEquals(0L, Efectivo.aCobrar(0))
    }

    @Test
    fun `los billetes sugeridos son los tres que siguen a lo que se cobra`() {
        assertEquals(listOf(20_000L, 50_000L, 100_000L), Efectivo.sugeridos(15_900))
        assertEquals(listOf(2_000L, 5_000L, 10_000L), Efectivo.sugeridos(1_500))
        assertEquals("El billete exacto no se sugiere: para eso está «Exacto»", listOf(50_000L, 100_000L), Efectivo.sugeridos(20_000))
        assertTrue(Efectivo.sugeridos(150_000).isEmpty())
    }

    // ---- Al abrir ----

    @Test
    fun `al abrir ensena el total de la copia y luego el del servicio, con la unica caja con turno`() = runTest(hilo) {
        val canal = Canal(listOf(caja1, sinTurno))
        val c = cobrador(canal)
        c.abrir(ticket(), catalogo)
        var e = c.estado.value
        assertEquals("Mientras contesta, el de la copia", 5150L, e.total)
        assertEquals(5200L, e.aCobrar)
        assertTrue(e.cargando)
        assertFalse("Sin la cotización no se cobra", e.puedeCobrar)
        advanceUntilIdle()
        e = c.estado.value
        assertEquals(5150L, e.cotizacion!!.totalCentavos)
        assertEquals("c1", e.cajaDelCobro!!.id)
        assertEquals(50L, e.redondeo)
        assertEquals("Efectivo y exacto ya elegidos: casi siempre es tocar «Cobrar»", 0L, e.cambio)
        assertTrue(e.puedeCobrar)
        // Lo que se cotiza: los mismos renglones que se cobran, sin precios y sin cobros.
        val cotizada = canal.cotizadas.single()
        assertEquals(listOf(1000L, 1000L), cotizada.renglones.map { it.cantidadMilesimas })
        assertNull(cotizada.cobros)
    }

    @Test
    fun `sin ninguna caja con turno lo dice y no cobra`() = runTest(hilo) {
        val c = cobrador(Canal(listOf(sinTurno)))
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        assertEquals(SIN_TURNO_PARA_COBRAR, c.estado.value.error)
        assertFalse(c.estado.value.puedeCobrar)
    }

    @Test
    fun `con dos cajas con turno hay que elegir, y la del ultimo cobro ya viene elegida`() = runTest(hilo) {
        val sinUltima = cobrador(Canal(listOf(caja1, caja2)))
        sinUltima.abrir(ticket(), catalogo)
        advanceUntilIdle()
        assertNull(sinUltima.estado.value.cajaDelCobro)
        assertFalse(sinUltima.estado.value.puedeCobrar)
        sinUltima.elegirCaja("c2")
        assertEquals("c2", sinUltima.estado.value.cajaDelCobro!!.id)
        assertTrue(sinUltima.estado.value.puedeCobrar)

        val conUltima = cobrador(Canal(listOf(caja1, caja2)), ultima = "c2")
        conUltima.abrir(ticket(), catalogo)
        advanceUntilIdle()
        assertEquals("c2", conUltima.estado.value.cajaDelCobro!!.id)
    }

    @Test
    fun `sin red al abrir lo dice y con volver a intentar se recupera`() = runTest(hilo) {
        val canal = Canal(listOf(caja1)).apply { falla = NoSeMando("No contestó la caja principal.", sinRed = true) }
        val c = cobrador(canal)
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        assertEquals("No contestó la caja principal.", c.estado.value.error)
        assertFalse(c.estado.value.puedeCobrar)
        canal.falla = null
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        assertNull(c.estado.value.error)
        assertTrue(c.estado.value.puedeCobrar)
    }

    @Test
    fun `un pos anterior al cobro dice que hay que actualizarlo`() = runTest(hilo) {
        val canal = Canal(listOf(caja1)).apply { falla = NoSeMando("No está.", http = 404) }
        val c = cobrador(canal)
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        assertEquals(POS_SIN_COBRO, c.estado.value.error)
    }

    // ---- Cobrar ----

    @Test
    fun `en efectivo viaja lo que entrego el cliente, la caja, el total visto y un Id nuevo`() = runTest(hilo) {
        val canal = Canal(listOf(caja1))
        val guardadas = ArrayList<String>()
        val c = cobrador(canal, guardadas = guardadas)
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        c.recibir(10_000)
        assertEquals(4800L, c.estado.value.cambio)
        var cobrada: VentaCobradaDto? = null
        c.cobrar { cobrada = it }
        assertTrue(c.estado.value.cobrando)
        advanceUntilIdle()
        val s = canal.cobradas.single()
        assertEquals("id-1", s.id)
        assertEquals("c1", s.caja)
        assertEquals("El exacto que se enseñó: si el del servicio es otro, no cobra", 5150L, s.totalVisto)
        assertNull("Sin sesión en el celular, cobra el cajero del turno", s.cajeroId)
        assertEquals(listOf(0 to 10_000L), s.cobros.map { it.forma to it.montoCentavos })
        assertEquals(4800L, cobrada!!.cambioCentavos)
        assertEquals("A-000001", c.estado.value.cobrada!!.folio)
        assertEquals(listOf("c1"), guardadas)
        assertEquals("efectivo", Cobrador.comoPago(cobrada!!))
    }

    @Test
    fun `si faltan pesos no cobra`() = runTest(hilo) {
        val canal = Canal(listOf(caja1))
        val c = cobrador(canal)
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        c.recibir(5_000)
        assertEquals(200L, c.estado.value.falta)
        assertFalse(c.estado.value.puedeCobrar)
        c.cobrar { }
        advanceUntilIdle()
        assertTrue(canal.cobradas.isEmpty())
        // De vuelta a exacto.
        c.recibir(null)
        assertTrue(c.estado.value.puedeCobrar)
    }

    @Test
    fun `tarjeta y transferencia van exactas, sin redondeo, y la tarjeta con su referencia`() = runTest(hilo) {
        val canal = Canal(listOf(caja1))
        val c = cobrador(canal)
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        c.elegirForma(FormaDePago.TARJETA)
        assertEquals("Exacto, sin redondear", 5150L, c.estado.value.aCobrar)
        assertEquals(0L, c.estado.value.redondeo)
        c.referencia("  4321 ")
        c.cobrar { }
        advanceUntilIdle()
        val tarjeta = canal.cobradas.single().cobros.single()
        assertEquals(Triple(1, 5150L, "4321"), Triple(tarjeta.forma, tarjeta.montoCentavos, tarjeta.referencia))

        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        c.elegirForma(FormaDePago.TRANSFERENCIA)
        var cobrada: VentaCobradaDto? = null
        c.cobrar { cobrada = it }
        advanceUntilIdle()
        val transferencia = canal.cobradas.last().cobros.single()
        assertEquals(Triple(2, 5150L, null), Triple(transferencia.forma, transferencia.montoCentavos, transferencia.referencia))
        assertEquals("transferencia, por verificar", Cobrador.comoPago(cobrada!!))
        assertEquals("Otro ticket, otro Id", "id-2", canal.cobradas.last().id)
    }

    @Test
    fun `si se perdio la respuesta, cobrar otra vez lleva el mismo Id y no cobra dos veces`() = runTest(hilo) {
        val canal = Canal(listOf(caja1)).apply { fallaAlCobrar = NoSeMando("No contestó la caja principal.", sinRed = true) }
        val c = cobrador(canal)
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        var veces = 0
        c.cobrar { veces++ }
        advanceUntilIdle()
        assertTrue(c.estado.value.error!!, c.estado.value.error!!.contains("no se cobra dos veces"))
        assertEquals(0, veces)
        assertFalse(c.estado.value.cobrando)
        canal.fallaAlCobrar = null
        c.cobrar { veces++ }
        advanceUntilIdle()
        assertEquals(listOf("id-1", "id-1"), canal.cobradas.map { it.id })
        assertEquals(1, veces)
    }

    @Test
    fun `si el total del servicio es otro no cobra, lo dice y ensena el nuevo`() = runTest(hilo) {
        val canal = Canal(listOf(caja1))
        val c = cobrador(canal)
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        // Entre la cotización y el cobro cambió un precio en la caja principal.
        canal.total = 9000
        canal.fallaAlCobrar = NoSeMando("El total cambió: ahora es \$90.00 (el celular decía \$51.50). Revísalo con el cliente y vuelve a cobrar.", codigo = "total-distinto", http = 409)
        var veces = 0
        c.cobrar { veces++ }
        advanceUntilIdle()
        assertEquals(0, veces)
        assertTrue(c.estado.value.error!!.startsWith("El total cambió"))
        assertEquals("Se cotizó otra vez", 9000L, c.estado.value.total)
        assertEquals(2, canal.cotizadas.size)
        // Con el total nuevo a la vista, ahora sí.
        canal.fallaAlCobrar = null
        c.cobrar { veces++ }
        advanceUntilIdle()
        assertEquals(9000L, canal.cobradas.last().totalVisto)
        assertEquals(1, veces)
    }

    @Test
    fun `el mismo ticket mandado y luego cobrado lleva el mismo Id`() = runTest(hilo) {
        var n = 0
        val ids = IdsDeTicket { "id-${++n}" }
        val renglones = renglonesParaMandar(ticket(), catalogo)
        val alMandar = ids.de(renglones)
        val canal = Canal(listOf(caja1))
        val c = Cobrador(canal, this, { null }, {}, ids)
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        c.cobrar { }
        advanceUntilIdle()
        assertEquals(alMandar, canal.cobradas.single().id)
        // Ya cobrado, el siguiente ticket (aunque traiga lo mismo) es otro.
        assertEquals("id-2", ids.de(renglones))
    }

    // ---- Imprimir el ticket ----

    @Test
    fun `la casilla de imprimir sale como diga ajustes, se cambia por venta y viaja con el cobro`() = runTest(hilo) {
        val canal = Canal(listOf(caja1))
        var deFabrica = true
        val c = Cobrador(canal, this, { null }, {}, IdsDeTicket(), imprimirDeFabrica = { deFabrica })
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        assertTrue("De fábrica, marcada", c.estado.value.imprimir)
        c.cobrar { }
        advanceUntilIdle()
        assertEquals(true, canal.cobradas.last().imprimir)
        // En esta venta no quiere ticket: solo esta.
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        c.imprimir(false)
        c.cobrar { }
        advanceUntilIdle()
        assertEquals(false, canal.cobradas.last().imprimir)
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        assertTrue("La siguiente venta vuelve a lo de Ajustes", c.estado.value.imprimir)
        // Con el ajuste apagado, sale sin marcar.
        deFabrica = false
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        assertFalse(c.estado.value.imprimir)
    }

    @Test
    fun `al volver a intentar tras un error la casilla se queda como se dejo`() = runTest(hilo) {
        val canal = Canal(listOf(caja1)).apply { falla = NoSeMando("No contestó la caja principal.", sinRed = true) }
        val c = cobrador(canal)
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        c.imprimir(false)
        canal.falla = null
        c.abrir(ticket(), catalogo)
        advanceUntilIdle()
        assertFalse(c.estado.value.imprimir)
    }
}
