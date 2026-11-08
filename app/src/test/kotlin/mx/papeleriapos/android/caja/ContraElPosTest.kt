package mx.papeleriapos.android.caja

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

        // La identidad, como si hubiera cambiado de IP.
        val reto = Identidad.nuevoReto()
        val identidad = cliente.identidad(direccion, alta.caja.id, reto)
        assertTrue("El servicio prueba quién es", Identidad.esLaCajaPrincipal(identidad, alta.llave, alta.llaveDelServicio, alta.caja.id, reto, direccion))
    }
}
