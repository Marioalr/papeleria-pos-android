package mx.papeleriapos.android.caja

import org.junit.Assert.fail
import mx.papeleriapos.android.principal.totalDe
import mx.papeleriapos.android.principal.renglonesParaMandar
import mx.papeleriapos.android.principal.Efectivo
import mx.papeleriapos.android.principal.Cobrador
import mx.papeleriapos.android.lista.ListaLecturas
import java.util.UUID
import kotlinx.coroutines.runBlocking
import mx.papeleriapos.android.copia.Copia
import mx.papeleriapos.android.copia.ProductosDeLaCopia
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Contra un servicio del POS de verdad (docs/pruebas.md, «La conexión con el POS»): solo corre con `POS_URL`
 * (`POS_URL=http://127.0.0.1:5080 POS_USUARIO=dueno POS_PIN=3333 ./gradlew :app:testDebugUnitTest --tests '*ContraElPos*'`),
 * contra la base ficticia, nunca contra la tienda. Da de alta una caja nueva cada vez (serie libre) y revisa que lo que
 * contesta el POS se entienda tal cual: el alta, el paquete, los productos y los cambios.
 */
class ContraElPosTest {
    private val url = System.getenv("POS_URL")
    private val usuario = System.getenv("POS_USUARIO") ?: "dueno"
    private val pin = System.getenv("POS_PIN") ?: "3333"

    @Test
    fun `el alta, el paquete y los cambios se entienden tal cual`() = runBlocking {
        assumeTrue("Sin POS_URL no corre", url != null)
        val direccion = RedLocal.direccionDe(url!!)!!
        val cliente = ClienteCaja()
        val estado = cliente.estado(direccion)
        assertEquals(Conexion.SERVICIO, estado.servicio)
        assertFalse(estado.tiendaNueva)
        val sucursales = cliente.opcionesDeAlta(direccion, usuario, pin)
        assertTrue(sucursales.isNotEmpty())
        val sucursal = sucursales[0]
        val serie = Conexion.seriePropuesta(sucursal.cajas)
        val alta = cliente.darDeAlta(direccion, SolicitudAltaCaja(usuario, pin, sucursal.id, null, "Celular de prueba", serie, "Prueba JVM"))
        assertTrue(alta.llave.isNotBlank())
        assertNotNull("El servicio manda su llave pública", alta.llaveDelServicio)
        assertEquals(serie, alta.caja.serie)

        // El latido con la llave (el alta sube la versión del catálogo: la copia trae la caja nueva).
        val latido = cliente.estado(direccion, alta.llave, alta.caja.id)
        assertTrue(latido.versionDatos >= estado.versionDatos)

        val paquete = cliente.paquete(direccion, alta.llave, alta.caja.id)
        val copia = Copia.dePaquete(paquete, System.currentTimeMillis())
        println("Paquete: ${copia.productos.size} productos, ${copia.codigos.size} códigos, ${copia.marcas.size} marcas, ${copia.sinonimos.size} sinónimos, ${copia.promociones.size} promociones, versión ${copia.version}, formato ${copia.formato}")
        assertTrue(copia.productos.isNotEmpty())
        assertTrue(copia.codigos.isNotEmpty())
        assertEquals(alta.caja.id.lowercase(), copia.caja.id.lowercase())
        assertFalse(copia.desactualizada(latido.versionDatos, latido.versionExistencias, latido.base, latido.formatoCopia))

        val desde = System.nanoTime()
        val productos = ProductosDeLaCopia.armar(copia)
        println("Productos armados en ${(System.nanoTime() - desde) / 1_000_000} ms: ${productos.tamano} (variantes ${productos.variantes})")
        val conCodigo = copia.codigos.first { it.codigo.length == 13 && it.codigo.all(Char::isDigit) }
        val p = productos.porLectura(conCodigo.codigo, Simbologia.EAN_13)
        assertTrue("Lo que el POS encuentra por código, la copia también (o es de varios)", p != null || productos.deVarios(conCodigo.codigo, Simbologia.EAN_13))
        // Lo que contesta el POS por código, con la misma regla, da el mismo producto.
        if (p != null) {
            val texto = productos.buscar(p.nombre.split(' ').take(2).joinToString(" "))
            assertTrue(texto.isNotEmpty())
        }

        val cambios = cliente.cambios(direccion, alta.llave, copia)
        println("Cambios: paqueteCompleto=${cambios.paqueteCompleto}, versión ${cambios.version}, ${cambios.productos.size} productos")
        assertEquals(null, cambios.paqueteCompleto)
        assertEquals(copia.version, cambios.version)
        val igual = copia.aplicar(cambios, System.currentTimeMillis())
        assertEquals(copia.productos.size, igual.productos.size)

        // Mandar a la caja (POS con la rama celular/mandar-a-la-caja): las cajas se entienden y, sin una caja con la venta
        // abierta, mandar dice por qué, en español, con su código.
        val cajas = cliente.paraMandar(direccion, alta.llave, alta.caja.id)
        println("Cajas para mandar: ${cajas.map { "${it.nombre} (recibe=${it.recibe})" }}")
        assertTrue(cajas.none { it.id.equals(alta.caja.id, ignoreCase = true) })
        cajas.firstOrNull { !it.recibe }?.let { apagada ->
            val p0 = copia.productos.first()
            val e = runCatching {
                cliente.mandar(direccion, alta.llave, alta.caja.id, SolicitudMandarTicket(java.util.UUID.randomUUID().toString(), apagada.id, listOf(RenglonMandado(p0.varianteId, null, null, 1000))))
            }.exceptionOrNull() as ErrorDelPos
            assertEquals("caja-no-recibe", e.codigo)
        }

        // La identidad, como si hubiera cambiado de IP.
        val reto = Identidad.nuevoReto()
        val identidad = cliente.identidad(direccion, alta.caja.id, reto)
        assertTrue("El servicio prueba quién es", Identidad.esLaCajaPrincipal(identidad, alta.llave, alta.llaveDelServicio, alta.caja.id, reto, direccion))
    }

    /**
     * Cobrar en el celular (docs/celular-como-caja.md del POS, paso 10), contra el servicio de verdad. Necesita una caja con
     * turno abierto en la base de prueba (ese documento dice cómo abrirlo con curl); sin él, no corre.
     */
    @Test
    fun `cotizar y cobrar se entienden tal cual y el total de la copia es el del servicio`() = runBlocking {
        assumeTrue("Sin POS_URL no corre", url != null)
        val direccion = RedLocal.direccionDe(url!!)!!
        val cliente = ClienteCaja()
        val sucursal = cliente.opcionesDeAlta(direccion, usuario, pin)[0]
        val alta = cliente.darDeAlta(direccion, SolicitudAltaCaja(usuario, pin, sucursal.id, null, "Celular de prueba", Conexion.seriePropuesta(sucursal.cajas), "Prueba JVM"))
        val celular = alta.caja.id
        val cajas = cliente.paraMandar(direccion, alta.llave, celular)
        val conTurno = cajas.filter { it.turnoAbierto }
        println("Cajas: " + cajas.joinToString { "${it.nombre} (turno ${it.turnoAbierto}, recibe ${it.recibe})" })
        assumeTrue("Sin una caja con turno abierto no se puede cobrar", conTurno.isNotEmpty())

        // El ticket, armado como en la app: tres de un producto de la copia y un artículo común de $35.50.
        val copia = Copia.dePaquete(cliente.paquete(direccion, alta.llave, celular), System.currentTimeMillis())
        val productos = ProductosDeLaCopia.armar(copia)
        val producto = productos.productos.first { it.cobro != null && it.base == null && it.precio in 100..50_000 && it.promocion == null }
        var ticket = ListaLecturas().agregarDelCatalogo(producto.clave, 1L, cantidad = 3).agregarComun(3550, 2L)
        val renglones = renglonesParaMandar(ticket, productos)
        val local = totalDe(ticket, productos)

        val cotizacion = cliente.cotizar(direccion, alta.llave, celular, SolicitudCotizar(renglones))
        println("Cotización: total ${cotizacion.totalCentavos}, efectivo ${cotizacion.efectivoCentavos}, redondeo ${cotizacion.redondeoCentavos}; la copia dice $local")
        assertEquals("El total que enseña la app con su copia es el del servicio", cotizacion.totalCentavos, local)
        assertEquals("El redondeo de la app es el del servicio", cotizacion.efectivoCentavos, Efectivo.aCobrar(local))
        assertEquals(listOf(false, true), cotizacion.lineas.map { it.articuloComun })
        assertEquals(4.0, cotizacion.articulos, 0.0)

        // Con el total que no es, no cobra: lo dice y no pasa nada.
        val id = UUID.randomUUID().toString()
        val recibido = cotizacion.efectivoCentavos + 5_000
        try {
            cliente.cobrar(direccion, alta.llave, celular, SolicitudCobrar(id, renglones, listOf(CobroPedido(0, recibido)), caja = conTurno[0].id, totalVisto = local + 100))
            fail("Con otro total no debió cobrar")
        } catch (e: ErrorDelPos) {
            assertEquals("total-distinto", e.codigo)
            println("Total distinto: ${e.message}")
        }

        // En efectivo, con $50 de más: el cambio.
        val solicitud = SolicitudCobrar(id, renglones, listOf(CobroPedido(0, recibido)), caja = conTurno[0].id, totalVisto = local)
        val venta = cliente.cobrar(direccion, alta.llave, celular, solicitud)
        println("Cobrado: folio ${venta.folio} en ${venta.caja}, cajero ${venta.cajero}, cobrado ${venta.cobradoCentavos}, cambio ${venta.cambioCentavos}")
        assertTrue(venta.folio.isNotBlank())
        assertEquals(conTurno[0].nombre, venta.caja)
        assertEquals(cotizacion.totalCentavos, venta.totalCentavos)
        assertEquals(cotizacion.efectivoCentavos, venta.cobradoCentavos)
        assertEquals(5_000L, venta.cambioCentavos)
        assertFalse(venta.yaCobrada)
        assertEquals("efectivo", Cobrador.comoPago(venta))

        // El mismo Id otra vez (se perdió la respuesta): la misma venta, sin cobrar dos veces.
        val otraVez = cliente.cobrar(direccion, alta.llave, celular, solicitud)
        assertTrue(otraVez.yaCobrada)
        assertEquals(venta.folio, otraVez.folio)

        // Con tarjeta, exacto y con su referencia; es otro ticket, con otro Id y otro folio.
        ticket = ListaLecturas().agregarDelCatalogo(producto.clave, 3L)
        val deTarjeta = renglonesParaMandar(ticket, productos)
        val exacto = totalDe(ticket, productos)
        val conTarjeta = cliente.cobrar(
            direccion, alta.llave, celular,
            SolicitudCobrar(UUID.randomUUID().toString(), deTarjeta, listOf(CobroPedido(1, exacto, "4321")), caja = conTurno[0].id, totalVisto = exacto),
        )
        println("Con tarjeta: folio ${conTarjeta.folio}, cobrado ${conTarjeta.cobradoCentavos}")
        assertEquals(exacto, conTarjeta.cobradoCentavos)
        assertEquals("4321", conTarjeta.cobros.single().referencia)
        assertTrue(conTarjeta.folio != venta.folio)

        // Y mandar a una caja un ticket con artículo común ya no lo rechaza por él.
        try {
            val mandado = cliente.mandar(direccion, alta.llave, celular, SolicitudMandarTicket(UUID.randomUUID().toString(), conTurno[0].id, renglones))
            println("Mandado con artículo común: recibido ${mandado.recibido}, total ${mandado.totalCentavos}")
        } catch (e: ErrorDelPos) {
            println("Mandar con artículo común: ${e.codigo}: ${e.message}")
            assertEquals("Sin la venta abierta en esa caja no lo recoge, pero el artículo común ya no es el problema", "caja-no-recibe", e.codigo)
        }
    }
}
