package mx.papeleriapos.android.copia

import mx.papeleriapos.android.caja.JsonDelPos
import mx.papeleriapos.android.caja.PaqueteDto
import mx.papeleriapos.android.catalogo.Escala
import mx.papeleriapos.android.catalogo.Unidad
import mx.papeleriapos.android.catalogo.desdeMayoreo
import mx.papeleriapos.android.catalogo.porPieza
import mx.papeleriapos.android.principal.detalleDe
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.pos.precio.Fecha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Los productos de la copia como los enseñan Precio, Buscar y el ticket. */
class ProductosDeLaCopiaTest {
    private val copia = Copia.dePaquete(JsonDelPos.decodeFromString(PaqueteDto.serializer(), CopiaTest.textoDelPaquete()), 0L)
    private val hoy = Fecha.de(2026, 11, 8)
    private val catalogo = ProductosDeLaCopia.armar(copia, hoy)

    private val cuaderno = "0190c5a2-0000-7000-8000-000000000001"
    private val lapiz = "0190c5a2-0000-7000-8000-000000000002"
    private val caja = "$lapiz/0190c5a2-0000-7000-8000-0000000000c2"

    @Test
    fun `cada variante es un producto y cada otra presentacion, otro`() {
        assertEquals(6, catalogo.variantes)
        assertEquals(7, catalogo.tamano)
        val c = catalogo.porClave(cuaderno)!!
        assertEquals("Cuaderno profesional cuadro chico 100 hojas", c.nombre)
        assertEquals("7509990100019", c.codigo)
        assertEquals("La tarjeta sin departamento: solo el código", "7509990100019", detalleDe(c))
        assertEquals("El precio de la sucursal manda sobre el general", 4500L, c.precio)
        assertNull("Con precio de sucursal, las escalas generales no cuentan", c.mayoreo)
        assertEquals(8.0, c.existencia!!, 0.0)
        assertNull("La promoción de enero todavía no corre", c.promocion)
        val l = catalogo.porClave(lapiz)!!
        assertEquals(550L, l.precio)
        assertEquals(Escala(12, 450), l.mayoreo)
        assertEquals("Desde 12: \$4.50 c/u", desdeMayoreo(l))
        assertEquals(140.0, l.existencia!!, 0.0)
        val k = catalogo.porClave(caja)!!
        assertEquals("Lápiz HB No. 2 · Caja con 12", k.nombre)
        assertEquals(12, k.piezas)
        assertEquals(lapiz, k.base)
        assertEquals("7509990100200", k.codigo)
        assertEquals("Su precio propio", 5400L, k.precio)
        assertEquals("\$4.50 c/u (12)", porPieza(k))
        assertEquals("La caja se cobra con su precio propio", 10800L, k.importe(2))
        assertEquals("Las piezas con su mayoreo", 5400L, l.importe(12))
        assertEquals(550L, l.precioUnitario(11))
        assertTrue(l.esMayoreo(12))
    }

    @Test
    fun `lo que no lleva inventario, las unidades y lo que no tiene codigo`() {
        val copias = catalogo.porClave("0190c5a2-0000-7000-8000-000000000003")!!
        assertNull(copias.existencia)
        assertNull(copias.codigo)
        assertEquals("", detalleDe(copias))
        assertEquals(100L, copias.precio)
        val liston = catalogo.porClave("0190c5a2-0000-7000-8000-000000000004")!!
        assertEquals(Unidad.METRO, liston.unidad)
        assertEquals(85.5, liston.existencia!!, 0.0)
    }

    @Test
    fun `lo escaneado se encuentra con la regla de los codigos y un codigo de dos productos no se adivina`() {
        assertEquals(caja, catalogo.porLectura("7509990100200", Simbologia.EAN_13)!!.clave)
        assertEquals(lapiz, catalogo.porLectura("7509990100026", Simbologia.EAN_13)!!.clave)
        // El UPC-A de 12 guardado llega de la cámara como el EAN-13 con un 0 adelante.
        assertEquals("0190c5a2-0000-7000-8000-000000000005", catalogo.porLectura("0099999291452", Simbologia.EAN_13)!!.clave)
        assertNull(catalogo.porLectura("7509990100194", Simbologia.EAN_13))
        assertFalse(catalogo.deVarios("7509990100194", Simbologia.EAN_13))
        // 7509990100040 es de los dos plumones: como el POS, no se encuentra ninguno y se dice por qué.
        assertNull(catalogo.porLectura("7509990100040", Simbologia.EAN_13))
        assertTrue(catalogo.deVarios("7509990100040", Simbologia.EAN_13))
    }

    @Test
    fun `la promocion que corre hoy se dice y se cobra`() {
        val otro = catalogo.porClave("0190c5a2-0000-7000-8000-000000000006")!!
        assertEquals("Regreso a clases: lleva 3 y paga 2", otro.promocion)
        assertEquals("3 al precio de 2", 4200L, otro.importe(3))
        assertEquals(2100L, otro.importe(1))
        // Sin la promoción (otro día) se cobra normal; y en enero el cuaderno sí la trae.
        val enero = ProductosDeLaCopia.armar(copia, Fecha.de(2027, 1, 15))
        assertEquals(6300L, enero.porClave("0190c5a2-0000-7000-8000-000000000006")!!.importe(3))
        assertEquals("Solo en enero: 10 % menos", enero.porClave(cuaderno)!!.promocion)
        assertEquals("10 % menos sobre el menudeo", 4050L, enero.porClave(cuaderno)!!.importe(1))
    }

    @Test
    fun `buscar es la busqueda de la caja, con lo elegido y lo mas vendido`() {
        // Sin texto: las variantes por lo más vendido; la caja de 12 no sale aparte.
        val sinTexto = catalogo.buscar("")
        assertEquals(6, sinTexto.size)
        assertEquals(lapiz, sinTexto[0].clave)
        assertEquals(cuaderno, sinTexto[1].clave)
        assertFalse(sinTexto.any { it.clave == caja })
        // Por nombre, pedazos en cualquier orden y sin acentos.
        assertEquals(listOf(lapiz), catalogo.buscar("lapiz hb").map { it.clave })
        assertEquals(listOf(cuaderno), catalogo.buscar("cuad chico").map { it.clave })
        // Lo que la caja eligió antes con «plumon» (el otro proveedor) sale primero, aunque se venda menos.
        assertEquals("0190c5a2-0000-7000-8000-000000000006", catalogo.buscar("plumon")[0].clave)
        // Y por código, también el de una presentación (encuentra la variante).
        assertEquals(listOf(lapiz), catalogo.buscar("7509990100200").map { it.clave })
    }

    @Test
    fun `hoy se cuenta donde esta el celular`() {
        val mexico = TimeZone.getTimeZone("America/Mexico_City")
        // 2026-11-08 23:30 en México es el 9 en UTC: el día es el 8.
        val ms = 1_794_202_200_000L // 2026-11-09T05:30:00Z
        assertEquals(Fecha.de(2026, 11, 8), hoy(ms, mexico))
        assertEquals(Fecha.de(2026, 11, 9), hoy(ms, TimeZone.getTimeZone("UTC")))
    }
}
