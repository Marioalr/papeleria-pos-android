package mx.papeleriapos.android.principal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.papeleriapos.android.caja.CajaParaContarDto
import mx.papeleriapos.android.caja.CanalDeContar
import mx.papeleriapos.android.caja.ConteoMandadoDto
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.caja.SolicitudMandarConteo
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Cobro
import mx.papeleriapos.android.catalogo.Escala
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.pos.Cantidad
import mx.papeleriapos.pos.precio.Presentacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mandar el conteo a una caja (docs/celular-como-caja.md del POS, «Mandar un conteo a una caja»): lo que viaja, el id que
 * no duplica la lista, a qué caja va y lo que pasa cuando no se puede.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MandarConteoTest {
    // Como en la copia: la clave es la variante (o variante/presentación) y el código de barras va aparte.
    private val lapiz = Producto(
        "var-lapiz", "Lápiz HB No. 2", "Escolar", listOf(Escala(1, 550)), 140.0, codigo = "7509990100026",
        cobro = Cobro(emptyList(), Presentacion(id = "pres-pieza", varianteId = "var-lapiz", factor = Cantidad.piezas(1)), "s1"),
    )
    private val cajaDeLapices = Producto(
        "var-lapiz/pres-caja", "Lápiz HB No. 2 · caja con 12", "Escolar", listOf(Escala(1, 5400)), 140.0, piezas = 12, base = "var-lapiz",
        codigo = "7509990100200",
        cobro = Cobro(emptyList(), Presentacion(id = "pres-caja", varianteId = "var-lapiz", factor = Cantidad.piezas(12)), "s1"),
    )
    private val catalogo = CatalogoDeProductos.deCopia(
        listOf(lapiz, cajaDeLapices),
        mapOf("7509990100026" to listOf(lapiz.clave), "7509990100200" to listOf(cajaDeLapices.clave)),
        emptyList(), emptyList(), emptyList(), emptyMap(), 1,
    )

    /** 2 cajas y 5 sueltos, escaneados a las 17:40 y 17:42 UTC del 22 de noviembre. */
    private fun conteo() = ListaLecturas()
        .sumar("7509990100200", Simbologia.EAN_13, HORA, cantidad = 2, clave = cajaDeLapices.clave)
        .sumar("7509990100026", Simbologia.EAN_13, HORA + 120_000, cantidad = 5, clave = lapiz.clave)

    @Test
    fun `cada renglon va con su variante y presentacion, en la presentacion escaneada y con la hora de su ultimo escaneo`() {
        val r = renglonesDelConteo(conteo(), catalogo)
        assertEquals(2, r.size)
        assertEquals("var-lapiz", r[0].varianteId)
        assertEquals("pres-caja", r[0].presentacionId)
        assertEquals("2 cajas son 2000, no 24000", 2000L, r[0].cantidadMilesimas)
        assertEquals("2026-11-22T17:40:00Z", r[0].contadoUtc)
        assertEquals("pres-pieza", r[1].presentacionId)
        assertEquals(5000L, r[1].cantidadMilesimas)
        assertEquals("2026-11-22T17:42:00Z", r[1].contadoUtc)
    }

    @Test
    fun `lo que no esta en la copia va por su codigo`() {
        val r = renglonesDelConteo(ListaLecturas().sumar("7501234567890", Simbologia.EAN_13, HORA), catalogo).single()
        assertNull(r.varianteId)
        assertEquals("7501234567890", r.codigo)
    }

    @Test
    fun `la misma lista lleva el mismo id, otra lista otro, y al llegar se olvida`() {
        var n = 0
        val ids = IdDeConteo.enMemoria { "id-${++n}" }
        val r = renglonesDelConteo(conteo(), catalogo)
        assertEquals("id-1", ids.de(r))
        assertEquals("Reintentar no duplica", "id-1", ids.de(r))
        assertEquals("Se contó uno más: es otra lista", "id-2", ids.de(r.dropLast(1)))
        ids.olvidar()
        assertEquals("id-3", ids.de(r))
    }

    private class Canal(var cajas: List<CajaParaContarDto>) : CanalDeContar {
        var falla: NoSeMando? = null
        var fallaAlMandar: NoSeMando? = null
        val mandadas = ArrayList<SolicitudMandarConteo>()
        override suspend fun cajasParaContar(): List<CajaParaContarDto> = falla?.let { throw it } ?: cajas
        override suspend fun mandarConteo(solicitud: SolicitudMandarConteo): ConteoMandadoDto {
            mandadas.add(solicitud)
            fallaAlMandar?.let { throw it }
            return ConteoMandadoDto(solicitud.id, solicitud.destino, caja = "Caja 2", estado = "recibida", productos = 1)
        }
        override suspend fun conteosMandados(): List<ConteoMandadoDto> = emptyList()
    }

    private val caja2 = CajaParaContarDto("c2", "Caja 2", "B", recibe = true, atiende = "Ana López")
    private val principal = CajaParaContarDto(
        "c1", "Caja principal", "A", recibe = false, atiende = "Pedro", codigo = "sin-permiso",
        porQue = "Pedro tiene el turno de la Caja principal y no puede aceptar conteos.",
    )

    @Test
    fun `elige la ultima caja si recibe, si no la unica que recibe`() = runTest(StandardTestDispatcher()) {
        val c = Contador(Canal(listOf(principal, caja2)), this, { "c1" }, {}, IdDeConteo.enMemoria())
        c.cargar()
        advanceUntilIdle()
        assertEquals("La última no recibe: la única que sí", "c2", c.estado.value.elegida)
        assertTrue(c.estado.value.puedeMandar)
        assertNull(c.estado.value.error)
    }

    @Test
    fun `si ninguna recibe no se puede mandar y cada caja dice por que`() = runTest(StandardTestDispatcher()) {
        val c = Contador(Canal(listOf(principal)), this, { null }, {}, IdDeConteo.enMemoria())
        c.cargar()
        advanceUntilIdle()
        assertNull(c.estado.value.elegida)
        assertFalse(c.estado.value.puedeMandar)
        assertEquals("sin-permiso", c.estado.value.cajas!!.single().codigo)
    }

    @Test
    fun `manda la lista con su hora y la del envio, guarda la caja y olvida el id`() = runTest(StandardTestDispatcher()) {
        val canal = Canal(listOf(caja2))
        val guardadas = ArrayList<String>()
        var n = 0
        val ids = IdDeConteo.enMemoria { "id-${++n}" }
        val c = Contador(canal, this, { null }, { guardadas.add(it) }, ids, reloj = { HORA + 600_000 })
        c.cargar()
        advanceUntilIdle()
        var llego: ConteoMandadoDto? = null
        c.mandar(conteo(), catalogo) { llego = it }
        advanceUntilIdle()
        val s = canal.mandadas.single()
        assertEquals("id-1", s.id)
        assertEquals("c2", s.destino)
        assertEquals("La del último escaneo", "2026-11-22T17:42:00Z", s.contadoUtc)
        assertEquals("2026-11-22T17:50:00Z", s.enviadoUtc)
        assertEquals(2, s.renglones.size)
        assertEquals("Caja 2", llego!!.caja)
        assertEquals(listOf("c2"), guardadas)
        assertEquals("La siguiente lista es otra", "id-2", ids.de(s.renglones))
    }

    @Test
    fun `si no contesta, la lista se queda y al reintentar va con el mismo id`() = runTest(StandardTestDispatcher()) {
        val canal = Canal(listOf(caja2)).apply { fallaAlMandar = NoSeMando("No contestó la caja principal.", sinRed = true) }
        val c = Contador(canal, this, { null }, {}, IdDeConteo.enMemoria())
        c.cargar()
        advanceUntilIdle()
        var llego = false
        c.mandar(conteo(), catalogo) { llego = true }
        advanceUntilIdle()
        assertFalse(llego)
        assertEquals("No contestó la caja principal.", c.estado.value.error)
        canal.fallaAlMandar = null
        c.mandar(conteo(), catalogo) { llego = true }
        advanceUntilIdle()
        assertTrue(llego)
        assertEquals(canal.mandadas[0].id, canal.mandadas[1].id)
    }

    @Test
    fun `sin red al pedir las cajas se ofrece cambiar de sucursal`() = runTest(StandardTestDispatcher()) {
        val canal = Canal(listOf(caja2)).apply { falla = NoSeMando("No se encontró la caja principal en esta red.", sinRed = true) }
        val c = Contador(canal, this, { null }, {}, IdDeConteo.enMemoria())
        c.cargar()
        advanceUntilIdle()
        assertTrue(c.estado.value.otraSucursal)
        canal.falla = NoSeMando("Mientras dure el piloto con eleventa, el inventario se lleva en eleventa.", codigo = "piloto-eleventa")
        c.cargar()
        advanceUntilIdle()
        assertFalse("Contestó: es la de aquí", c.estado.value.otraSucursal)
        assertEquals("Mientras dure el piloto con eleventa, el inventario se lleva en eleventa.", c.estado.value.error)
    }

    @Test
    fun `el aviso dice a que caja llego y cuantos productos`() {
        assertEquals("Conteo mandado a Caja 2 · 14 productos", Contador.textoMandado(ConteoMandadoDto("x", caja = "Caja 2", productos = 14), 3))
        assertEquals("Conteo mandado a Caja 2 · 1 producto", Contador.textoMandado(ConteoMandadoDto("x", caja = "Caja 2"), 1))
    }

    @Test
    fun `las horas del POS se leen con o sin fraccion y con desfase`() {
        val esperado = HORA
        assertEquals(esperado, ConteosFechas.ms("2026-11-22T17:40:00Z"))
        assertEquals(esperado, ConteosFechas.ms("2026-11-22T17:40:00.1234567Z"))
        assertEquals(esperado, ConteosFechas.ms("2026-11-22T11:40:00-06:00"))
        assertNull(ConteosFechas.ms(null))
        assertNull(ConteosFechas.ms("ayer"))
        assertNotEquals("Por revisar en la caja", textoEstado("aceptada"))
        assertEquals("Rechazado", textoEstado("rechazada"))
        assertEquals("otro-estado", textoEstado("otro-estado"))
    }

    companion object {
        /** 2026-11-22 17:40:00 UTC. */
        const val HORA = 1_795_369_200_000L
    }
}
