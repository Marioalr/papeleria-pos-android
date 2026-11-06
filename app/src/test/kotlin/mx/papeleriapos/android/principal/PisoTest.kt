package mx.papeleriapos.android.principal

import kotlinx.coroutines.flow.MutableStateFlow
import mx.papeleriapos.android.botonrapido.Lugar
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Escala
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El estado de la pantalla principal: los modos, Precio, el ticket, las hojas, los avisos y las pistas. */
class PisoTest {

    private val cuaderno = Producto("7509990100019", "Cuaderno profesional cuadro chico 100 hojas", "Escolar", listOf(Escala(1, 4600), Escala(12, 4100)), 8.0)
    private val lapiz = Producto("7509990100026", "Lápiz HB No. 2", "Escolar", listOf(Escala(1, 550), Escala(12, 450)), 140.0)
    private val catalogo = CatalogoDeProductos(listOf(cuaderno, lapiz))

    /** Lo que guardaría el celular, en memoria. */
    private class AlmacenEnMemoria : AlmacenPiso {
        var modo: ModoApp? = null
        var zurdo = false
        var pistas = emptySet<ModoApp>()
        var deslizando = 0
        var contadores = ContadoresRapido()
        override fun leerModo() = modo
        override fun guardarModo(modo: ModoApp) { this.modo = modo }
        override fun leerZurdo() = zurdo
        override fun guardarZurdo(zurdo: Boolean) { this.zurdo = zurdo }
        override fun leerPistasVistas() = pistas
        override fun guardarPistasVistas(modos: Set<ModoApp>) { pistas = modos }
        override fun leerElegidasDeslizando() = deslizando
        override fun guardarElegidasDeslizando(veces: Int) { deslizando = veces }
        override fun leerContadores() = contadores
        override fun guardarContadores(contadores: ContadoresRapido) { this.contadores = contadores }
    }

    private var ahora = 10_000L
    private val venta = MutableStateFlow(ListaLecturas())
    private val conteo = MutableStateFlow(ListaLecturas())
    private val almacen = AlmacenEnMemoria()

    private fun piso(modoAnterior: ModoApp = ModoApp.TICKET) =
        Piso(venta, conteo, almacen, modoAnterior) { ahora }.also { it.cargarCatalogo(catalogo) }

    // ---- Los modos ----

    @Test
    fun `abre en el ultimo modo usado y si nunca se eligio en el de antes`() {
        assertEquals(ModoApp.CONTEO, piso(ModoApp.CONTEO).modo.value)
        val p = piso()
        p.cambiarModo(ModoApp.PRECIO)
        assertEquals(ModoApp.PRECIO, almacen.modo)
        assertEquals(ModoApp.PRECIO, piso(ModoApp.TICKET).modo.value)
    }

    @Test
    fun `en precio escanear solo ensena y no agrega nada`() {
        val p = piso()
        p.cambiarModo(ModoApp.PRECIO)
        assertTrue(p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1))
        assertEquals(ConsultaPrecio.Encontrado(cuaderno), p.precio.value)
        assertTrue(venta.value.renglones.isEmpty())
        p.alLeer("7509990100194", Simbologia.EAN_13, 2)
        assertEquals(ConsultaPrecio.NoEsta("7509990100194"), p.precio.value)
    }

    @Test
    fun `en ticket cada lectura suma con la clave del catalogo`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 2)
        p.alLeer("7509990100194", Simbologia.EAN_13, 3)
        assertEquals(3, venta.value.total)
        assertEquals(lapiz.clave, venta.value.renglones.first { it.codigo == lapiz.clave }.clave)
        assertNull("Un código sin producto entra sin clave", venta.value.actual!!.clave)
    }

    @Test
    fun `en conteo cada lectura va a la lista de conteo`() {
        val p = piso()
        p.cambiarModo(ModoApp.CONTEO)
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        assertEquals(1, conteo.value.total)
        assertTrue(venta.value.renglones.isEmpty())
    }

    @Test
    fun `con una hoja encima las lecturas no cuentan`() {
        val p = piso()
        p.abrir(Hoja.Buscar)
        assertFalse(p.alLeer(lapiz.clave, Simbologia.EAN_13, 1))
        assertTrue(venta.value.renglones.isEmpty())
        p.cerrarHoja()
        assertTrue(p.alLeer(lapiz.clave, Simbologia.EAN_13, 2))
    }

    // ---- Precio y Buscar ----

    @Test
    fun `al ticket desde precio avisa arriba con el producto, cuantos van y el total`() {
        val p = piso()
        p.cambiarModo(ModoApp.PRECIO)
        p.alTicket(cuaderno)
        p.alTicket(cuaderno)
        val a = p.aviso.value!!
        assertTrue(a.arriba)
        assertEquals("Deshacer", a.accion)
        assertEquals("Cuaderno profesional cuad… al ticket (van 2) · 2 art. · \$92.00", a.texto)
        p.accionDelAviso(a.id)
        assertEquals(1, venta.value.total)
        assertNull(p.aviso.value)
    }

    @Test
    fun `lo elegido en buscar agrega en ticket, ensena en precio y cuenta en conteo`() {
        val p = piso()
        p.abrir(Hoja.Buscar)
        p.elegirDeBusqueda(lapiz)
        assertNull(p.hoja.value)
        assertEquals(lapiz.clave, venta.value.actual!!.clave)
        p.cambiarModo(ModoApp.PRECIO)
        p.elegirDeBusqueda(cuaderno)
        assertEquals(ConsultaPrecio.Encontrado(cuaderno), p.precio.value)
        p.cambiarModo(ModoApp.CONTEO)
        p.elegirDeBusqueda(cuaderno)
        assertEquals(1, conteo.value.total)
    }

    @Test
    fun `ensenar abre el precio grande con el producto que estaba en la tarjeta`() {
        val p = piso()
        p.ensenar(cuaderno)
        assertEquals(Hoja.PrecioGrande(cuaderno), p.hoja.value)
    }

    // ---- El ticket ----

    @Test
    fun `menos uno en uno quita con aviso y deshacer lo regresa`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        val id = venta.value.actual!!.id
        p.menosUno(id)
        assertTrue(venta.value.renglones.isEmpty())
        val a = p.aviso.value!!
        assertEquals("Quitado del ticket", a.texto)
        p.accionDelAviso(a.id)
        assertEquals(1, venta.value.total)
    }

    @Test
    fun `la cantidad tecleada cierra el teclado y empezar otro ticket se deshace`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        val id = venta.value.actual!!.id
        p.abrir(Hoja.Cantidad(id))
        p.conCantidad(id, 20)
        assertNull(p.hoja.value)
        assertEquals(20, venta.value.total)
        p.empezarOtroTicket()
        assertTrue(venta.value.renglones.isEmpty())
        p.accionDelAviso(p.aviso.value!!.id)
        assertEquals(20, venta.value.total)
    }

    // ---- El botón rápido ----

    @Test
    fun `los avisos esperan a que se cierre el menu`() {
        val p = piso()
        p.alAbrirMenu(conToque = false)
        p.rechazar("El ticket está vacío")
        assertNull("Con el menú abierto el aviso no se ve", p.aviso.value)
        p.alCerrarMenu()
        assertEquals("El ticket está vacío", p.aviso.value!!.texto)
        p.avisoVencido(p.aviso.value!!.id)
        assertNull(p.aviso.value)
    }

    @Test
    fun `la pista de la primera vez se va al abrir el menu en ese modo`() {
        val p = piso()
        assertFalse(ModoApp.TICKET in p.pistasVistas.value)
        p.alAbrirMenu(conToque = true)
        assertTrue(ModoApp.TICKET in p.pistasVistas.value)
        assertFalse(ModoApp.PRECIO in p.pistasVistas.value)
        assertEquals(1, p.contadores.value.abiertoConToque)
        p.volverAVerPistas()
        assertTrue(p.pistasVistas.value.isEmpty())
    }

    @Test
    fun `pulsa y desliza sin soltar se deja de decir despues de tres veces`() {
        val p = piso()
        repeat(5) { p.alElegir(Lugar.LADO, conGesto = true) }
        assertEquals(3, p.elegidasDeslizando.value)
        assertEquals(5, p.contadores.value.conGesto[Lugar.LADO])
        p.alElegir(Lugar.LADO, conGesto = false)
        assertEquals(1, p.contadores.value.conToque[Lugar.LADO])
    }

    @Test
    fun `lo que se cierra en menos de dos segundos sin hacer nada se cuenta`() {
        val p = piso()
        p.alElegir(Lugar.LADO, conGesto = true)
        p.abrir(Hoja.Buscar)
        ahora += 1_500
        p.cerrarHoja()
        assertEquals(1, p.contadores.value.cerradasPronto[Lugar.LADO])
        // Si se eligió algo adentro, no cuenta; ni si tardó más.
        p.alElegir(Lugar.LADO, conGesto = true)
        p.abrir(Hoja.Buscar)
        p.elegirDeBusqueda(lapiz)
        p.alElegir(Lugar.ARRIBA, conGesto = true)
        p.abrir(Hoja.Buscar)
        ahora += 3_000
        p.cerrarHoja()
        assertEquals(1, p.contadores.value.cerradasPronto.values.sum())
    }

    @Test
    fun `zurdo se guarda`() {
        val p = piso()
        p.cambiarZurdo(true)
        assertTrue(almacen.zurdo)
        assertTrue(piso().zurdo.value)
    }
}
