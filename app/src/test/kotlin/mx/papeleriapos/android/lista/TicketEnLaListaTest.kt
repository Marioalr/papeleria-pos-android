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
        // Todo menos la llegada, que no va a disco.
        assertEquals(l.renglones.map { it.copy(llegada = 0) }, r.renglones.map { it.copy(llegada = 0) })
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

    // ---- Lo que deshace cada aviso, con una lectura en medio ----

    private val goma = "7509990100033"

    private val sacapuntas = "7509990100040"
    private val pegamento = "7509990100057"

    /** [l] sin el renglón [i]: lo quitado, su lugar y la lista justo después (lo que guarda el aviso). */
    private fun quitar(l: ListaLecturas, i: Int): Triple<Renglon, Int, ListaLecturas> {
        val r = l.renglones[i]
        return Triple(r, i, l.quitar(r.id))
    }

    @Test
    fun `regresar pone lo quitado en su lugar aunque despues se haya leido otra cosa`() {
        var l = ListaLecturas()
            .sumar(cuaderno, Simbologia.EAN_13, 1, clave = cuaderno)
            .sumar(lapiz, Simbologia.EAN_13, 2, clave = lapiz)
        val (quitado, i, tras) = quitar(l, 0)
        l = tras.sumar(goma, Simbologia.EAN_13, 3, clave = goma)
        l = l.regresar(quitado, i, tras.renglones)
        // El lápiz vuelve, como si no se hubiera quitado: la goma, leída después, queda arriba (con su − y su +).
        assertEquals(listOf(goma to 1, lapiz to 1, cuaderno to 1), l.codigos())
        assertEquals(quitado.id, l.renglones[1].id)
        assertTrue(l.renglones.map { it.id }.toSet().size == 3)
        // Y el deshacer del ticket completo lo vuelve a quitar.
        assertEquals(listOf(goma to 1, cuaderno to 1), l.deshacer().codigos())
    }

    @Test
    fun `regresar lo de en medio lo deja entre los mismos renglones aunque en medio se lea otro`() {
        // Cuaderno, lápiz y goma (la goma arriba); se quita el lápiz, que estaba en medio, y se lee el sacapuntas.
        var l = ListaLecturas()
            .sumar(cuaderno, Simbologia.EAN_13, 1, clave = cuaderno)
            .sumar(lapiz, Simbologia.EAN_13, 2, clave = lapiz)
            .sumar(goma, Simbologia.EAN_13, 3, clave = goma)
        val (quitado, i, tras) = quitar(l, 1)
        l = tras.sumar(sacapuntas, Simbologia.EAN_13, 4, clave = sacapuntas)
        // Por su lugar de antes (el 1) quedaría arriba de la goma, que tenía arriba.
        assertEquals(listOf(sacapuntas to 1, goma to 1, lapiz to 1, cuaderno to 1), l.regresar(quitado, i, tras.renglones).codigos())
        // Si en medio se volvió a leer el de abajo (subió), regresa abajo de lo que sigue en su orden.
        val releido = tras.sumar(cuaderno, Simbologia.EAN_13, 5, clave = cuaderno)
        assertEquals(listOf(cuaderno to 2, goma to 1, lapiz to 1), releido.regresar(quitado, i, tras.renglones).codigos())
        // Y si se quitó también el de abajo, va al final.
        val sinCuaderno = tras.quitar(tras.renglones.last().id).sumar(pegamento, Simbologia.EAN_13, 6, clave = pegamento)
        assertEquals(listOf(pegamento to 1, goma to 1, lapiz to 1), sinCuaderno.regresar(quitado, i, tras.renglones).codigos())
    }

    @Test
    fun `regresar deja arriba lo que se volvio a leer aunque ya estuviera arriba`() {
        // El lápiz y, por error, la goma: se quita la goma (el renglón actual) y se lee el segundo lápiz, que ya quedó
        // arriba y no se mueve. Lo escaneado después queda arriba: el lápiz, con su − y su +.
        val l = ListaLecturas()
            .sumar(lapiz, Simbologia.EAN_13, 1, clave = lapiz)
            .sumar(goma, Simbologia.EAN_13, 2, clave = goma)
        val (quitado, i, tras) = quitar(l, 0)
        val releido = tras.sumar(lapiz, Simbologia.EAN_13, 3, clave = lapiz)
        assertEquals(listOf(lapiz to 2, goma to 1), releido.regresar(quitado, i, tras.renglones).codigos())
        // Con varios lápices pasa igual.
        val varios = ListaLecturas()
            .sumar(lapiz, Simbologia.EAN_13, 1, clave = lapiz)
            .sumar(lapiz, Simbologia.EAN_13, 2, clave = lapiz)
            .sumar(goma, Simbologia.EAN_13, 3, clave = goma)
        val (q, k, t) = quitar(varios, 0)
        assertEquals(listOf(lapiz to 3, goma to 1), t.sumar(lapiz, Simbologia.EAN_13, 4, clave = lapiz).regresar(q, k, t.renglones).codigos())
        // El + del ticket completo no es una lectura y no mueve nada: la goma regresa arriba, donde estaba. Deja la misma
        // lista de renglones que leer el lápiz; solo la llegada los distingue.
        val conMas = tras.masUno(tras.renglones[0].id, 3)
        assertEquals(releido.codigos(), conMas.codigos())
        assertEquals(listOf(goma to 1, lapiz to 2), conMas.regresar(quitado, i, tras.renglones).codigos())
    }

    @Test
    fun `regresar con tres deja lo leido despues arriba en el orden en que se leyo`() {
        // Cuaderno, lápiz y goma; se quita la goma y se leen el lápiz y luego el cuaderno.
        val l = ListaLecturas()
            .sumar(cuaderno, Simbologia.EAN_13, 1, clave = cuaderno)
            .sumar(lapiz, Simbologia.EAN_13, 2, clave = lapiz)
            .sumar(goma, Simbologia.EAN_13, 3, clave = goma)
        val (quitado, i, tras) = quitar(l, 0)
        val despues = tras.sumar(lapiz, Simbologia.EAN_13, 4, clave = lapiz).sumar(cuaderno, Simbologia.EAN_13, 5, clave = cuaderno)
        assertEquals(listOf(cuaderno to 2, lapiz to 2, goma to 1), despues.regresar(quitado, i, tras.renglones).codigos())
        // Si solo se leyó el cuaderno (el de abajo), la goma regresa arriba del lápiz, que no se movió.
        val soloCuaderno = tras.sumar(cuaderno, Simbologia.EAN_13, 4, clave = cuaderno)
        assertEquals(listOf(cuaderno to 2, goma to 1, lapiz to 1), soloCuaderno.regresar(quitado, i, tras.renglones).codigos())
    }

    @Test
    fun `regresar en lo leido del disco tambien deja arriba lo que se volvio a leer`() {
        // La llegada no va a disco: al leerlo sale del orden de la lista.
        val guardada = ListaLecturas.deTexto(
            ListaLecturas().sumar(lapiz, Simbologia.EAN_13, 1, clave = lapiz).sumar(goma, Simbologia.EAN_13, 2, clave = goma).aTexto(),
        )
        assertEquals(listOf(goma to 1, lapiz to 1), guardada.codigos())
        val (quitado, i, tras) = quitar(guardada, 0)
        val releido = tras.sumar(lapiz, Simbologia.EAN_13, 3, clave = lapiz)
        assertEquals(listOf(lapiz to 2, goma to 1), releido.regresar(quitado, i, tras.renglones).codigos())
        assertEquals(listOf(goma to 1, lapiz to 2), tras.masUno(tras.renglones[0].id, 3).regresar(quitado, i, tras.renglones).codigos())
    }

    @Test
    fun `regresar no repite lo que ya regreso el deshacer del ticket completo`() {
        val l = ListaLecturas()
            .sumar(cuaderno, Simbologia.EAN_13, 1, clave = cuaderno)
            .sumar(lapiz, Simbologia.EAN_13, 2, clave = lapiz)
        val (quitado, i, tras) = quitar(l, 0)
        val deshecha = tras.deshacer()
        assertSame(deshecha, deshecha.regresar(quitado, i, tras.renglones))
        // Lo de antes de vaciar tampoco se suma dos veces.
        val vaciada = l.vaciar().deshacer()
        assertSame(vaciada, vaciada.conLoDeAntes(l.renglones))
    }

    @Test
    fun `regresar suma a su renglon si el producto se volvio a leer`() {
        var l = ListaLecturas().sumar(lapiz, Simbologia.EAN_13, 1, clave = lapiz).conCantidad(1, 5, 2)
        val quitado = l.renglones[0]
        val tras = l.conCantidad(quitado.id, 0, 3)
        l = tras.sumar(lapiz, Simbologia.EAN_13, 4, clave = lapiz)
        l = l.regresar(quitado, 0, tras.renglones)
        assertEquals(listOf(lapiz to 6), l.codigos())
    }

    @Test
    fun `quitar una pieza deshace el al ticket aunque haya otra lectura arriba`() {
        var l = ListaLecturas().agregarDelCatalogo(lapiz, 1).agregarDelCatalogo(lapiz, 2)
        l = l.sumar(goma, Simbologia.EAN_13, 3, clave = goma)
        l = l.quitarUna(lapiz)
        assertEquals(listOf(goma to 1, lapiz to 1), l.codigos())
        l = l.quitarUna(lapiz)
        assertEquals(listOf(goma to 1), l.codigos())
        assertSame("Si ya no está, nada cambia", l, l.quitarUna(lapiz))
    }

    @Test
    fun `lo de antes de vaciar regresa con lo leido despues arriba`() {
        val antes = ListaLecturas()
            .sumar(cuaderno, Simbologia.EAN_13, 1, clave = cuaderno)
            .sumar(lapiz, Simbologia.EAN_13, 2, clave = lapiz)
        var l = antes.vaciar()
        l = l.sumar(goma, Simbologia.EAN_13, 3, clave = goma).sumar(cuaderno, Simbologia.EAN_13, 4, clave = cuaderno)
        l = l.conLoDeAntes(antes.renglones)
        // Como unir: lo de después va arriba, en su orden, y el cuaderno de antes y el de después son un renglón.
        assertEquals(listOf(cuaderno to 2, goma to 1, lapiz to 1), l.codigos())
        assertEquals(l.renglones.size, l.renglones.map { it.id }.toSet().size)
        // Lo que sigue ya no repite ids.
        val siguiente = l.sumar("7509990100040", Simbologia.EAN_13, 5)
        assertEquals(siguiente.renglones.size, siguiente.renglones.map { it.id }.toSet().size)
    }

    @Test
    fun `completar las claves no agrega un paso y junta lo buscado con lo del rapido`() {
        // Lo que sumó el escaneo rápido: sin clave, con el código como lo da la cámara.
        val rapido = ListaLecturas().sumar(upcLeido, Simbologia.UPC_A, 1)
        val completa = rapido.completarClaves { if (it.codigo == upcLeido) upcGuardado else null }
        assertEquals(upcGuardado, completa.actual!!.clave)
        // No es un paso para deshacer: deshacer regresa a lo de antes de la lectura, no a la lectura sin clave.
        assertTrue(completa.deshacer().renglones.isEmpty())
        assertEquals(listOf(upcLeido to 2), completa.agregarDelCatalogo(upcGuardado, 2).codigos())
        assertSame("Sin nada que completar, la misma lista", completa, completa.completarClaves { upcGuardado })
    }
}
