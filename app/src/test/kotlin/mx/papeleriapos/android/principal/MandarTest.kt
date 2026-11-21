package mx.papeleriapos.android.principal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mx.papeleriapos.android.botonrapido.SIN_CONEXION_MANDAR
import mx.papeleriapos.android.caja.CajaParaMandarDto
import mx.papeleriapos.android.caja.CanalDeMandar
import mx.papeleriapos.android.caja.JsonDelPos
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.caja.PaqueteDto
import mx.papeleriapos.android.caja.SolicitudMandarTicket
import mx.papeleriapos.android.caja.TicketMandadoDto
import mx.papeleriapos.android.copia.Copia
import mx.papeleriapos.android.copia.CopiaTest
import mx.papeleriapos.android.copia.ProductosDeLaCopia
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.pos.precio.Fecha
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** «Mandar a una caja»: cuándo se puede, qué viaja (sin precios), la caja elegida, el mismo Id al reintentar y los errores. */
@OptIn(ExperimentalCoroutinesApi::class)
class MandarTest {
    private val copia = Copia.dePaquete(JsonDelPos.decodeFromString(PaqueteDto.serializer(), CopiaTest.textoDelPaquete()), 0L)
    private val catalogo = ProductosDeLaCopia.armar(copia, Fecha.de(2026, 11, 8))
    private val lapiz = "0190c5a2-0000-7000-8000-000000000002"
    private val cajaDeLapices = "0190c5a2-0000-7000-8000-0000000000c2"

    /** Dos lápices sueltos (por su código) y una caja de 12 (por su clave, como desde Buscar). */
    private fun ticket(): ListaLecturas {
        val suelto = catalogo.porClave(lapiz)!!
        return ListaLecturas()
            .sumar(suelto.codigo!!, Simbologia.EAN_13, 1L, cantidad = 2, clave = suelto.clave)
            .agregarDelCatalogo("$lapiz/$cajaDeLapices", 2L)
    }

    private class Canal : CanalDeMandar {
        var cajas = listOf(
            CajaParaMandarDto("c1", "Caja 1", "A", recibe = false),
            CajaParaMandarDto("c2", "Caja 2", "B", recibe = true, atiende = "Ana", esperan = 0),
        )
        val mandadas = ArrayList<SolicitudMandarTicket>()
        var respuesta: suspend (SolicitudMandarTicket) -> TicketMandadoDto = { TicketMandadoDto(it.id, it.destino, "Caja 2", 2, 3.0, 6500, recibido = true) }

        override suspend fun cajasParaMandar() = cajas
        override suspend fun mandar(solicitud: SolicitudMandarTicket): TicketMandadoDto {
            mandadas.add(solicitud)
            return respuesta(solicitud)
        }
    }

    private val hilo = StandardTestDispatcher()

    @Before
    fun preparar() = Dispatchers.setMain(hilo)

    @After
    fun limpiar() = Dispatchers.resetMain()

    @Test
    fun `cuando se puede mandar`() {
        val t = ticket()
        assertEquals(SIN_CONEXION_MANDAR, razonParaMandar(t, catalogo, conAlta = false, dePrueba = false))
        assertEquals(CON_PRODUCTOS_DE_PRUEBA, razonParaMandar(t, catalogo, conAlta = true, dePrueba = true))
        assertNull(razonParaMandar(t, catalogo, conAlta = true, dePrueba = false))
        val conDesconocido = t.sumar("7500000000000", Simbologia.EAN_13, 3L)
        assertEquals(
            "El código 7500000000000 no está en el catálogo: quítalo del ticket para mandarlo (en la caja se da de alta).",
            razonParaMandar(conDesconocido, catalogo, conAlta = true, dePrueba = false),
        )
    }

    @Test
    fun `viaja que y cuanto, en el orden en que se escaneo, sin precios`() {
        val r = renglonesParaMandar(ticket(), catalogo)
        assertEquals(listOf(lapiz, lapiz), r.map { it.varianteId })
        assertEquals(cajaDeLapices, r[1].presentacionId)
        assertEquals(listOf(2000L, 1000L), r.map { it.cantidadMilesimas })
        assertEquals("7509990100200", r[1].codigo)
        // Lo que se manda no tiene dónde llevar un precio.
        assertFalse(JsonDelPos.encodeToString(SolicitudMandarTicket.serializer(), SolicitudMandarTicket("x", "c2", r)).contains("precio", ignoreCase = true))
    }

    private fun TestScope.mandador(canal: Canal, ultima: String? = null, guardadas: MutableList<String> = ArrayList()): Mandador {
        var n = 0
        return Mandador(canal, this, { ultima }, { guardadas.add(it) }, nuevoId = { "id-${++n}" })
    }

    @Test
    fun `elige la caja que tiene la venta abierta y manda, y lo enviado vacia el ticket`() = runTest(hilo) {
        val canal = Canal()
        val guardadas = ArrayList<String>()
        val m = mandador(canal, ultima = "c1", guardadas = guardadas)
        m.cargar()
        advanceUntilIdle()
        assertEquals("La última (c1) no tiene la venta abierta: la única que sí", "c2", m.estado.value.elegida)
        assertTrue(m.estado.value.puedeMandar)

        var aviso: String? = null
        m.mandar(ticket(), catalogo) { aviso = it }
        assertTrue(m.estado.value.mandando)
        assertFalse("Mientras manda, no otra vez", m.estado.value.puedeMandar)
        advanceUntilIdle()
        assertEquals("Enviado a Caja 2 · 3 art. · \$65.00", aviso)
        assertEquals(listOf("c2"), guardadas)
        assertEquals("c2", canal.mandadas.single().destino)
    }

    @Test
    fun `si no llego el mismo ticket va con el mismo Id y si cambia con otro`() = runTest(hilo) {
        val canal = Canal()
        canal.respuesta = { throw NoSeMando("La Caja 2 no lo recogió: revisa que tenga la venta abierta y vuelve a mandarlo.", "caja-no-recibe") }
        val m = mandador(canal)
        m.cargar()
        advanceUntilIdle()
        var aviso: String? = null
        m.mandar(ticket(), catalogo) { aviso = it }
        advanceUntilIdle()
        assertNull("El ticket se queda en el celular", aviso)
        assertEquals("La Caja 2 no lo recogió: revisa que tenga la venta abierta y vuelve a mandarlo.", m.estado.value.error)

        m.mandar(ticket(), catalogo) { aviso = it }
        advanceUntilIdle()
        assertEquals(canal.mandadas[0].id, canal.mandadas[1].id)

        m.mandar(ticket().masUno(ticket().renglones[0].id, 9L), catalogo) { aviso = it }
        advanceUntilIdle()
        assertNotEquals(canal.mandadas[1].id, canal.mandadas[2].id)
    }

    @Test
    fun `si la caja lo recogio sin confirmar lo dice y el reintento no lo duplica`() = runTest(hilo) {
        val canal = Canal()
        val primera = CompletableDeferred<Unit>()
        canal.respuesta = {
            if (!primera.isCompleted) {
                primera.complete(Unit)
                TicketMandadoDto(it.id, it.destino, "Caja 2", 2, 3.0, 6500, recibido = false)
            } else {
                TicketMandadoDto(it.id, it.destino, "Caja 2", 2, 3.0, 6500, recibido = true)
            }
        }
        val m = mandador(canal)
        m.cargar()
        advanceUntilIdle()
        var aviso: String? = null
        m.mandar(ticket(), catalogo) { aviso = it }
        advanceUntilIdle()
        assertTrue(m.estado.value.error!!.startsWith("La Caja 2 lo recogió pero no alcanzó a confirmar"))
        assertNull(aviso)
        m.mandar(ticket(), catalogo) { aviso = it }
        advanceUntilIdle()
        assertEquals(canal.mandadas[0].id, canal.mandadas[1].id)
        assertEquals("Enviado a Caja 2 · 3 art. · \$65.00", aviso)
    }

    @Test
    fun `sin red al pedir las cajas lo dice`() = runTest(hilo) {
        val canal = object : CanalDeMandar {
            override suspend fun cajasParaMandar(): List<CajaParaMandarDto> = throw NoSeMando("Hay una VPN encendida en el celular", sinRed = true)
            override suspend fun mandar(solicitud: SolicitudMandarTicket) = error("no")
        }
        val m = Mandador(canal, this, { null }, {})
        m.cargar()
        advanceUntilIdle()
        assertEquals(emptyList<CajaParaMandarDto>(), m.estado.value.cajas)
        assertEquals("Hay una VPN encendida en el celular", m.estado.value.error)
        assertFalse(m.estado.value.puedeMandar)
    }
}
