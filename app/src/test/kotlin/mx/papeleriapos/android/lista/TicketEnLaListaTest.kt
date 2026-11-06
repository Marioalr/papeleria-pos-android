package mx.papeleriapos.android.lista

import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que el ticket le pide a la lista (docs/propuesta-interfaz.md, «Ticket: la preventa»): la clave del catálogo, para
 * que lo escaneado y lo buscado caigan juntos, y los cambios por renglón del ticket completo.
 */
class TicketEnLaListaTest {
    private val cuaderno = "7509990100019"
    private val lapiz = "7509990100026"

    /** Un UPC-A guardado de 12 dígitos en el catálogo: la cámara lo da como el EAN-13 con un 0 adelante. */
    private val upcGuardado = "099999291452"
    private val upcLeido = "0099999291452"

    private fun ListaLecturas.codigos() = renglones.map { it.codigo to it.cantidad }

    // ---- La clave del catálogo ----

    @Test
    fun `lo buscado y lo escaneado del mismo producto caen en el mismo renglon`() {
        val l = ListaLecturas()
            .agregarDelCatalogo(upcGuardado, 1)
            .sumar(upcLeido, Simbologia.EAN_13, 2, clave = upcGuardado)
        assertEquals(1, l.renglones.size)
        assertEquals(2, l.actual!!.cantidad)
        assertEquals(upcGuardado, l.actual!!.clave)
        // El renglón se queda con el código con que entró primero.
        assertEquals(upcGuardado, l.actual!!.codigo)
        assertEquals(Simbologia.OTRA, l.actual!!.simbologia)
    }

    @Test
    fun `dos variantes del mismo codigo caen juntas por la clave`() {
        val l = ListaLecturas()
            .sumar(upcLeido, Simbologia.EAN_13, 1, clave = upcGuardado)
            .sumar("99999291452", Simbologia.UPC_A, 2, clave = upcGuardado)
        assertEquals(listOf(upcLeido to 2), l.codigos())
    }

    @Test
    fun `un renglon sin clave la toma cuando el mismo codigo llega con ella`() {
        val l = ListaLecturas()
            .sumar(cuaderno, Simbologia.EAN_13, 1)
            .sumar(cuaderno, Simbologia.EAN_13, 2, clave = cuaderno)
        assertEquals(cuaderno, l.actual!!.clave)
        assertEquals(2, l.total)
    }

    @Test
    fun `sin clave sigue juntando por el codigo como antes`() {
        val l = ListaLecturas().sumar(lapiz, Simbologia.EAN_13, 1).sumar(lapiz, Simbologia.EAN_13, 2)
        assertEquals(listOf(lapiz to 2), l.codigos())
        assertEquals(null, l.actual!!.clave)
    }

    // ---- Cada renglón del ticket completo ----

    private fun tres() = ListaLecturas()
        .sumar(cuaderno, Simbologia.EAN_13, 1, clave = cuaderno)
        .sumar(lapiz, Simbologia.EAN_13, 2, clave = lapiz)
        .agregarDelCatalogo("RAPIDO-COPIA", 3)

    @Test
    fun `mas uno y menos uno por renglon no lo mueven`() {
        val l = tres()
        val idCuaderno = l.renglones.first { it.codigo == cuaderno }.id
        val mas = l.masUno(idCuaderno, 9)
        assertEquals(listOf("RAPIDO-COPIA" to 1, lapiz to 1, cuaderno to 2), mas.codigos())
        assertEquals(9L, mas.renglones.last().hora)
        val menos = mas.menosUno(idCuaderno)
        assertEquals(l.codigos(), menos.codigos())
    }

    @Test
    fun `menos uno en uno quita el renglon y se deshace`() {
        val l = tres()
        val idLapiz = l.renglones.first { it.codigo == lapiz }.id
        val sin = l.menosUno(idLapiz)
        assertEquals(listOf("RAPIDO-COPIA" to 1, cuaderno to 1), sin.codigos())
        assertEquals(l.codigos(), sin.deshacer().codigos())
    }

    @Test
    fun `la cantidad tecleada reemplaza y en cero quita`() {
        val l = tres()
        val idCopia = l.renglones.first { it.codigo == "RAPIDO-COPIA" }.id
        val veinte = l.conCantidad(idCopia, 20, 5)
        assertEquals(20, veinte.renglones.first { it.id == idCopia }.cantidad)
        assertEquals(22, veinte.total)
        assertFalse(veinte.conCantidad(idCopia, 0, 6).renglones.any { it.id == idCopia })
        // Teclear lo mismo no es un paso más para deshacer.
        assertSame(veinte, veinte.conCantidad(idCopia, 20, 5))
    }

    @Test
    fun `quitar quita el renglon entero y un id que no esta no cambia nada`() {
        val l = tres()
        val idCuaderno = l.renglones.first { it.codigo == cuaderno }.id
        assertEquals(listOf("RAPIDO-COPIA" to 1, lapiz to 1), l.quitar(idCuaderno).codigos())
        assertSame(l, l.quitar(999))
        assertSame(l, l.masUno(999, 1))
        assertSame(l, l.menosUno(999))
    }

    // ---- En disco ----

    @Test
    fun `la clave se guarda y se recupera`() {
        val l = tres().sumar("ZZP0042x", Simbologia.CODE_128, 4)
        val r = ListaLecturas.deTexto(l.aTexto())
        assertEquals(l.renglones, r.renglones)
        assertTrue(l.aTexto().startsWith("id,codigo,simbologia,cantidad,hora,clave"))
    }

    @Test
    fun `lo guardado por la version 0_1 sin clave se sigue leyendo`() {
        val viejo = "id,codigo,simbologia,cantidad,hora\r\n2,$cuaderno,EAN_13,3,1000\r\n1,$lapiz,EAN_13,1,900\r\n"
        val l = ListaLecturas.deTexto(viejo)
        assertEquals(listOf(cuaderno to 3, lapiz to 1), l.codigos())
        assertTrue(l.renglones.all { it.clave == null })
        // Y lo que entra después con clave cae en su renglón.
        assertEquals(4, l.sumar(cuaderno, Simbologia.EAN_13, 2_000, clave = cuaderno).renglones.first().cantidad)
    }

    @Test
    fun `unir conserva la clave de lo escaneado mientras se leia el disco`() {
        val guardada = ListaLecturas().sumar(cuaderno, Simbologia.EAN_13, 1, clave = cuaderno)
        val mientras = ListaLecturas().agregarDelCatalogo(upcGuardado, 2)
        val u = ListaLecturas.unir(guardada, mientras)
        assertEquals(upcGuardado, u.actual!!.clave)
        assertEquals(2, u.total)
    }
}
