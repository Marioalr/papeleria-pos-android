package mx.papeleriapos.android.lista

import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ListaLecturasTest {
    private val ean = "7501234567893"
    private val interno = "2000000042010"
    private val code128 = "PAP0042x"

    private fun ListaLecturas.codigos() = renglones.map { it.codigo to it.cantidad }

    @Test
    fun `un codigo nuevo agrega renglon arriba y el mismo suma uno y sube`() {
        val l = ListaLecturas()
            .sumar(ean, Simbologia.EAN_13, 1)
            .sumar(code128, Simbologia.CODE_128, 2)
            .sumar(ean, Simbologia.EAN_13, 3)
        assertEquals(listOf(ean to 2, code128 to 1), l.codigos())
        assertEquals(ean, l.actual!!.codigo)
        assertEquals(3L, l.actual!!.hora)
        assertEquals(3, l.total)
    }

    @Test
    fun `mas uno y menos uno van al actual y en cero se quita`() {
        var l = ListaLecturas().sumar(ean, Simbologia.EAN_13, 1).sumar(interno, Simbologia.EAN_13, 2)
        l = l.masUno(5)
        assertEquals(listOf(interno to 2, ean to 1), l.codigos())
        l = l.menosUno().menosUno()
        assertEquals(listOf(ean to 1), l.codigos())
        l = l.menosUno()
        assertTrue(l.renglones.isEmpty())
        assertNull(l.actual)
        // Sin renglones no pasa nada.
        assertSame(l, l.menosUno())
        assertSame(l, l.masUno(9))
    }

    @Test
    fun `deshacer regresa paso por paso, incluido vaciar`() {
        val l1 = ListaLecturas().sumar(ean, Simbologia.EAN_13, 1)
        val l2 = l1.sumar(ean, Simbologia.EAN_13, 2)
        val l3 = l2.sumar(interno, Simbologia.EAN_13, 3)
        val vacia = l3.vaciar()
        assertTrue(vacia.renglones.isEmpty())
        val d1 = vacia.deshacer()
        assertEquals(l3.codigos(), d1.codigos())
        val d2 = d1.deshacer()
        assertEquals(listOf(ean to 2), d2.codigos())
        val d3 = d2.deshacer().deshacer()
        assertTrue(d3.renglones.isEmpty())
        assertFalse(d3.puedeDeshacer)
        assertSame(d3, d3.deshacer())
    }

    @Test
    fun `un codigo que se quito y vuelve a entrar es renglon nuevo con otro id`() {
        val l = ListaLecturas().sumar(ean, Simbologia.EAN_13, 1)
        val id = l.actual!!.id
        val otra = l.menosUno().sumar(ean, Simbologia.EAN_13, 2)
        assertTrue(otra.actual!!.id != id)
    }

    @Test
    fun `en conteo cada lectura suma y el total cuenta piezas`() {
        var l = ListaLecturas()
        repeat(5) { l = l.sumar(ean, Simbologia.EAN_13, it.toLong()) }
        repeat(2) { l = l.sumar(code128, Simbologia.CODE_128, 10L + it) }
        assertEquals(listOf(code128 to 2, ean to 5), l.codigos())
        assertEquals(7, l.total)
        l = l.deshacer()
        assertEquals(listOf(code128 to 1, ean to 5), l.codigos())
    }

    @Test
    fun `deshacer guarda un limite de pasos`() {
        var l = ListaLecturas()
        repeat(ListaLecturas.PASOS_PARA_DESHACER + 20) { l = l.sumar(ean, Simbologia.EAN_13, it.toLong()) }
        var pasos = 0
        while (l.puedeDeshacer) {
            l = l.deshacer(); pasos++
        }
        assertEquals(ListaLecturas.PASOS_PARA_DESHACER, pasos)
        assertEquals(20, l.total)
    }

    @Test
    fun `se guarda y se recupera igual, sin los pasos de deshacer`() {
        val l = ListaLecturas()
            .sumar(ean, Simbologia.EAN_13, 1_000)
            .sumar("a,b \"c\"", Simbologia.CODE_128, 2_000)
            .sumar(ean, Simbologia.EAN_13, 3_000)
        val r = ListaLecturas.deTexto(l.aTexto())
        assertEquals(l.renglones, r.renglones)
        assertEquals(l.total, r.total)
        assertFalse(r.puedeDeshacer)
        // Un código nuevo después de recuperar no repite id.
        val nuevo = r.sumar(interno, Simbologia.EAN_13, 4_000)
        assertEquals(nuevo.renglones.map { it.id }.toSet().size, nuevo.renglones.size)
        // Lo que no se entiende se salta; un archivo ajeno da la lista vacía.
        assertTrue(ListaLecturas.deTexto("hola\r\n1,2").renglones.isEmpty())
        val roto = l.aTexto() + "x,7501,EAN_13,1,0\r\n9,7502,NO_EXISTE,1,0\r\n10,7503,EAN_13,0,0\r\n"
        assertEquals(l.renglones, ListaLecturas.deTexto(roto).renglones)
    }

    @Test
    fun `lo escaneado mientras se leia el disco queda arriba de lo guardado`() {
        val guardada = ListaLecturas().sumar(ean, Simbologia.EAN_13, 1).sumar(code128, Simbologia.CODE_128, 2)
        val vacia = ListaLecturas()
        assertSame(guardada, ListaLecturas.unir(guardada, vacia))
        val mientras = ListaLecturas().sumar(interno, Simbologia.EAN_13, 5).sumar(ean, Simbologia.EAN_13, 6)
        val u = ListaLecturas.unir(guardada, mientras)
        assertEquals(listOf(ean to 2, interno to 1, code128 to 1), u.codigos())
        assertEquals(4, u.total)
    }

    @Test
    fun `el almacen guarda cada modo en su archivo y sobrevive a un proceso nuevo`() {
        val carpeta = kotlin.io.path.createTempDirectory("listas").toFile()
        try {
            val a = AlmacenListas { carpeta }
            assertTrue(a.leer(mx.papeleriapos.escaner.nucleo.Modo.VENTA).renglones.isEmpty())
            val venta = ListaLecturas().sumar(ean, Simbologia.EAN_13, 1).sumar(ean, Simbologia.EAN_13, 2)
            val conteo = ListaLecturas().sumar(code128, Simbologia.CODE_128, 3)
            a.guardar(mx.papeleriapos.escaner.nucleo.Modo.VENTA, venta)
            a.guardar(mx.papeleriapos.escaner.nucleo.Modo.CONTEO, conteo)
            // Otro almacén (como tras matar el proceso) lee lo mismo.
            val b = AlmacenListas { carpeta }
            assertEquals(venta.renglones, b.leer(mx.papeleriapos.escaner.nucleo.Modo.VENTA).renglones)
            assertEquals(conteo.renglones, b.leer(mx.papeleriapos.escaner.nucleo.Modo.CONTEO).renglones)
            // «Nueva venta» también se guarda.
            b.guardar(mx.papeleriapos.escaner.nucleo.Modo.VENTA, venta.vaciar())
            assertTrue(AlmacenListas { carpeta }.leer(mx.papeleriapos.escaner.nucleo.Modo.VENTA).renglones.isEmpty())
        } finally {
            carpeta.deleteRecursively()
        }
    }
}
