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
    private val caja = Producto("7509990100200", "Lápiz HB No. 2 · caja con 12", "Escolar", listOf(Escala(1, 5400)), 140.0, piezas = 12, base = lapiz.clave)
    private val catalogo = CatalogoDeProductos(listOf(cuaderno, lapiz, caja))

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

    private fun piso(modoAnterior: ModoApp = ModoApp.VENTA) =
        Piso(venta, conteo, almacen, modoAnterior) { ahora }.also { it.cargarCatalogo(catalogo) }

    // ---- Los modos ----

    @Test
    fun `abre en el ultimo modo usado y si nunca se eligio en el de antes`() {
        assertEquals(ModoApp.CONTEO, piso(ModoApp.CONTEO).modo.value)
        val p = piso()
        p.cambiarModo(ModoApp.CONTEO)
        assertEquals(ModoApp.CONTEO, almacen.modo)
        assertEquals("Manda el guardado, no el de antes", ModoApp.CONTEO, piso(ModoApp.VENTA).modo.value)
    }

    @Test
    fun `cada lectura entra al ticket y lo que no esta en el catalogo se queda fuera`() {
        val p = piso()
        assertTrue(p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1))
        assertEquals(cuaderno.clave, venta.value.actual!!.clave)
        assertNull(p.noEsta.value)
        assertTrue("Vibra y la sesión la anota, pero no entra", p.alLeer("7509990100194", Simbologia.EAN_13, 2))
        assertEquals(NoEsta("7509990100194"), p.noEsta.value)
        assertEquals(1, venta.value.total)
        // La siguiente que sí está se lleva el «no está».
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 3)
        assertNull(p.noEsta.value)
        // Al contar, igual: no entra al conteo.
        p.cambiarModo(ModoApp.CONTEO)
        p.alLeer("7509990100194", Simbologia.EAN_13, 4)
        assertEquals(NoEsta("7509990100194"), p.noEsta.value)
        assertTrue(conteo.value.renglones.isEmpty())
        // Cambiar de modo también se lo lleva.
        p.cambiarModo(ModoApp.VENTA)
        assertNull(p.noEsta.value)
    }

    @Test
    fun `en ticket cada lectura suma con la clave del catalogo`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 2)
        p.alLeer("7509990100194", Simbologia.EAN_13, 3)
        assertEquals("Lo que no está no entra", 2, venta.value.total)
        assertEquals(1, venta.value.renglones.size)
        assertEquals(lapiz.clave, venta.value.renglones.first { it.codigo == lapiz.clave }.clave)
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
        p.cambiarModo(ModoApp.VENTA)
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
        p.cambiarModo(ModoApp.CONTEO)
        p.elegirDeBusqueda(cuaderno)
        assertEquals(1, conteo.value.total)
    }

    @Test
    fun `lo elegido antes con el mismo texto sale primero la proxima vez`() {
        val p = piso()
        val primero = p.buscar("hb").first()
        val otro = p.buscar("hb").last()
        assertTrue(primero != otro)
        p.abrir(Hoja.Buscar)
        p.elegirDeBusqueda(otro, "hb")
        assertEquals(otro, p.buscar("hb").first())
        // Con otro texto, no.
        assertEquals(p.buscar("lapiz hb").first(), p.buscar("lapiz hb").first())
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
        assertFalse(ModoApp.VENTA in p.pistasVistas.value)
        p.alAbrirMenu(conToque = true)
        assertTrue(ModoApp.VENTA in p.pistasVistas.value)
        assertFalse(ModoApp.CONTEO in p.pistasVistas.value)
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

    // ---- El «Deshacer» de cada aviso deshace lo que nombra, aunque en medio entre otra lectura ----

    private val goma = Producto("7509990100033", "Goma blanca chica", "Escolar", listOf(Escala(1, 500)), 63.0)
    private val conGoma = CatalogoDeProductos(listOf(cuaderno, lapiz, caja, goma))

    private fun claves() = venta.value.renglones.map { (it.clave ?: it.codigo) to it.cantidad }

    @Test
    fun `deshacer el quitado del ticket lo regresa a su lugar aunque despues se haya leido otro`() {
        val p = piso().also { it.cargarCatalogo(conGoma) }
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 2)
        p.menosUno(venta.value.actual!!.id)
        val a = p.aviso.value!!
        assertEquals("Quitado del ticket", a.texto)
        // Con el aviso a la vista entra otra lectura: el último paso del ticket ya es esa.
        p.alLeer(goma.clave, Simbologia.EAN_13, 3)
        p.accionDelAviso(a.id)
        // El lápiz vuelve como si no se hubiera quitado y la goma, que el escáner confirmó en verde, no se pierde: queda
        // arriba, con su − y su +, porque se leyó después.
        assertEquals(listOf(goma.clave to 1, lapiz.clave to 1, cuaderno.clave to 1), claves())
    }

    @Test
    fun `deshacer el quitado deja arriba lo que se volvio a leer aunque ya estuviera arriba`() {
        val p = piso().also { it.cargarCatalogo(conGoma) }
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        p.alLeer(goma.clave, Simbologia.EAN_13, 2) // por error
        p.menosUno(venta.value.actual!!.id) // el bote del renglón actual: «Quitado del ticket · Deshacer»
        val a = p.aviso.value!!
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 3) // el segundo lápiz, con el aviso a la vista
        p.accionDelAviso(a.id)
        // El renglón actual (el del − y el +) es lo último leído: el lápiz.
        assertEquals(listOf(lapiz.clave to 2, goma.clave to 1), claves())
    }

    @Test
    fun `deshacer el quitado despues del mas regresa lo quitado arriba, donde estaba`() {
        val p = piso().also { it.cargarCatalogo(conGoma) }
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        p.alLeer(goma.clave, Simbologia.EAN_13, 2)
        p.menosUno(venta.value.actual!!.id)
        val a = p.aviso.value!!
        // El + del lápiz (el que subió) no es una lectura: no mueve nada.
        p.masUno(venta.value.actual!!.id)
        p.accionDelAviso(a.id)
        assertEquals(listOf(goma.clave to 1, lapiz.clave to 2), claves())
    }

    @Test
    fun `deshacer el quitado con tres deja arriba lo leido despues en su orden`() {
        val p = piso().also { it.cargarCatalogo(conGoma) }
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 2)
        p.alLeer(goma.clave, Simbologia.EAN_13, 3)
        p.menosUno(venta.value.actual!!.id)
        val a = p.aviso.value!!
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 4)
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 5)
        p.accionDelAviso(a.id)
        assertEquals(listOf(cuaderno.clave to 2, lapiz.clave to 2, goma.clave to 1), claves())
    }

    @Test
    fun `el deshacer del ticket completo se lleva el aviso del paso que deshizo`() {
        // «Quitado del ticket» con el bote del ticket completo: el aviso se ve encima, junto al deshacer de la hoja.
        val p = piso()
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 2)
        p.abrir(Hoja.Ticket)
        p.menosUno(venta.value.actual!!.id)
        p.deshacerTicket()
        assertNull("Ya se deshizo: su «Deshacer» regresaría el lápiz otra vez", p.aviso.value)
        assertEquals(listOf(lapiz.clave to 1, cuaderno.clave to 1), claves())
        // Un paso de después no se lleva el aviso: el suyo sigue sin deshacer.
        p.menosUno(venta.value.actual!!.id)
        val quitado = p.aviso.value!!
        p.masUno(venta.value.actual!!.id)
        p.deshacerTicket()
        assertEquals(quitado, p.aviso.value)
        p.accionDelAviso(quitado.id)
        assertEquals(listOf(lapiz.clave to 1, cuaderno.clave to 1), claves())
        // «Ticket vacío»: el deshacer de la hoja lo regresa y su aviso se va (no lo suma dos veces).
        p.empezarOtroTicket()
        p.deshacerTicket()
        assertNull(p.aviso.value)
        assertEquals(listOf(lapiz.clave to 1, cuaderno.clave to 1), claves())
        // «… al ticket»: el deshacer de la hoja quita esa pieza y el aviso ya no quita otra.
        p.cerrarHoja()
        p.cambiarModo(ModoApp.VENTA)
        p.alTicket(lapiz)
        p.abrir(Hoja.Ticket)
        p.deshacerTicket()
        assertNull(p.aviso.value)
        assertEquals(listOf(lapiz.clave to 1, cuaderno.clave to 1), claves())
    }

    @Test
    fun `deshacer el al ticket quita esa pieza aunque despues se haya leido otro`() {
        val p = piso().also { it.cargarCatalogo(conGoma) }
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        p.abrir(Hoja.Buscar)
        p.elegirDeBusqueda(lapiz)
        val a = p.aviso.value!!
        assertTrue(a.texto.startsWith("Lápiz HB No. 2 al ticket (van 2)"))
        p.alLeer(goma.clave, Simbologia.EAN_13, 2)
        p.accionDelAviso(a.id)
        assertEquals(listOf(goma.clave to 1, lapiz.clave to 1), claves())
    }

    @Test
    fun `deshacer el ticket vacio regresa lo de antes con lo leido despues arriba`() {
        val p = piso().also { it.cargarCatalogo(conGoma) }
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 2)
        p.empezarOtroTicket()
        val a = p.aviso.value!!
        assertEquals("Ticket vacío: empieza otro", a.texto)
        p.alLeer(goma.clave, Simbologia.EAN_13, 3)
        p.accionDelAviso(a.id)
        assertEquals(listOf(goma.clave to 1, lapiz.clave to 1, cuaderno.clave to 1), claves())
    }

    @Test
    fun `sin nada en medio el deshacer del aviso regresa el ticket tal cual, con su orden`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 2)
        p.cambiarModo(ModoApp.VENTA)
        // «Al ticket» del lápiz, que ya estaba abajo: lo sube; deshacer lo regresa abajo, no solo le quita uno.
        p.alTicket(lapiz)
        p.accionDelAviso(p.aviso.value!!.id)
        assertEquals(listOf(cuaderno.clave to 1, lapiz.clave to 1), claves())
    }

    @Test
    fun `teclear cero quita el renglon con aviso y se deshace`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        val id = venta.value.actual!!.id
        p.abrir(Hoja.Cantidad(id))
        p.conCantidad(id, 0)
        assertTrue(venta.value.renglones.isEmpty())
        val a = p.aviso.value!!
        assertEquals("Quitado del ticket", a.texto)
        assertEquals("Deshacer", a.accion)
        p.accionDelAviso(a.id)
        assertEquals(listOf(lapiz.clave to 1), claves())
    }

    // ---- Las hojas se apilan ----

    @Test
    fun `lo que se abre desde el ticket completo regresa a el al cerrarse`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        val id = venta.value.actual!!.id
        p.abrir(Hoja.Ticket)
        // La cantidad: «Listo» regresa al ticket completo.
        p.abrir(Hoja.Cantidad(id))
        p.conCantidad(id, 3)
        assertEquals(Hoja.Ticket, p.hoja.value)
        // La × o atrás también.
        p.abrir(Hoja.Cantidad(id))
        p.cerrarHoja()
        assertEquals(Hoja.Ticket, p.hoja.value)
        // Buscar: elegir agrega y regresa al ticket completo.
        p.abrir(Hoja.Buscar)
        p.elegirDeBusqueda(cuaderno)
        assertEquals(Hoja.Ticket, p.hoja.value)
        assertEquals(4, venta.value.total)
        // Y de ahí, a la pantalla principal.
        p.cerrarHoja()
        assertNull(p.hoja.value)
        // Abiertas desde la pantalla principal, regresan a ella.
        p.abrir(Hoja.Buscar)
        p.cerrarHoja()
        assertNull(p.hoja.value)
    }

    @Test
    fun `cerrar pronto una hoja del boton solo cuenta al volver a la principal`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        p.abrir(Hoja.Ticket)
        p.alElegir(Lugar.LADO, conGesto = true)
        p.abrir(Hoja.Buscar)
        p.cerrarHoja()
        assertEquals("Regresó al ticket completo: no cuenta", 0, p.contadores.value.cerradasPronto.values.sum())
    }

    // ---- Los avisos y el menú ----

    @Test
    fun `un aviso que ya se veia se esconde al abrir el menu y sale completo al cerrarlo`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        p.menosUno(venta.value.actual!!.id)
        val a = p.aviso.value!!
        p.alAbrirMenu(conToque = false)
        assertNull("Bajo el velo no se ve ni corre su tiempo", p.aviso.value)
        assertTrue(p.menuAbierto)
        // El de la pantalla se vence (su tiempo corría): no lo quita, porque ya no es el que se ve.
        p.avisoVencido(a.id)
        p.alCerrarMenu()
        assertEquals(a, p.aviso.value)
        assertTrue(!p.menuAbierto)
        // Cerrar de más no hace nada.
        p.alCerrarMenu()
        assertEquals(a, p.aviso.value)
    }

    @Test
    fun `con el precio en grande el aviso del ticket espera y sale completo al regresar`() {
        val p = piso()
        p.cambiarModo(ModoApp.VENTA)
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        p.alTicket(cuaderno)
        val a = p.aviso.value!!
        // ↑ «Enseñar al cliente» con el gesto: el menú se abre, se cierra (el aviso vuelve) y sale el precio en grande.
        p.alAbrirMenu(conToque = false)
        p.alCerrarMenu()
        p.ensenar(cuaderno)
        assertNull("El cliente no ve el aviso del ticket", p.aviso.value)
        // La pantalla vuelve a decir que el menú está cerrado: el aviso sigue esperando.
        p.alCerrarMenu()
        assertNull(p.aviso.value)
        p.cerrarHoja()
        assertEquals(a, p.aviso.value)
    }

    // ---- Lo que anuncia cada lectura (TalkBack) ----

    @Test
    fun `cada lectura anuncia lo que hizo en su modo`() {
        val p = piso()
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        assertEquals("Cuaderno profesional cuadro chico 100 hojas, \$46.00, al ticket. Total \$46.00", p.anuncio.value!!.texto)
        val primero = p.anuncio.value!!.id
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 2)
        assertTrue("El mismo producto otra vez es otro anuncio", p.anuncio.value!!.id != primero)
        p.alLeer("7509990100194", Simbologia.EAN_13, 3)
        assertEquals("No está en el catálogo: no entró al ticket", p.anuncio.value!!.texto)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 4)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 5)
        assertEquals("Lápiz HB No. 2, \$5.50, al ticket, van 2. Total \$103.00", p.anuncio.value!!.texto)
        p.cambiarModo(ModoApp.CONTEO)
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 6)
        assertEquals("De uno, en singular", "Cuaderno profesional cuadro chico 100 hojas, va 1", p.anuncio.value!!.texto)
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 7)
        assertEquals("Cuaderno profesional cuadro chico 100 hojas, van 2", p.anuncio.value!!.texto)
        // Con una hoja encima no cuenta ni se anuncia.
        val antes = p.anuncio.value
        p.abrir(Hoja.Buscar)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 8)
        assertEquals(antes, p.anuncio.value)
    }

    // ---- Lo que llega de fuera: «Abrir la app» del escaneo rápido (docs/propuesta-interfaz.md, «Del bolsillo al precio») ----

    @Test
    fun `lo que llega de fuera cierra las hojas, pasa a la venta y entra al ticket como leido`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        val ticket = venta.value
        p.abrir(Hoja.Ticket)
        p.abrir(Hoja.Cantidad(ticket.actual!!.id))
        val antes = p.deFuera.value
        p.ensenarDeFuera(cuaderno.clave)
        assertNull("Ni la cantidad ni el ticket completo de abajo", p.hoja.value)
        assertEquals(ModoApp.VENTA, p.modo.value)
        assertNull(p.noEsta.value)
        assertEquals("Entra al ticket, arriba, para quitarlo deslizando si solo preguntaba", cuaderno.clave, venta.value.actual!!.clave)
        assertEquals(2, venta.value.total)
        assertEquals(
            "TalkBack lo dice, como una lectura",
            "Cuaderno profesional cuadro chico 100 hojas, \$46.00, al ticket. Total \$51.50", p.anuncio.value!!.texto,
        )
        assertTrue("La pantalla vuelve a la principal y cierra el menú una vez por cada uno", p.deFuera.value != antes)
        // El mismo producto otra vez: otra vez a la principal, y se anuncia otra vez.
        val primero = p.deFuera.value
        val anuncio = p.anuncio.value!!.id
        p.ensenarDeFuera(cuaderno.clave)
        assertTrue(p.deFuera.value != primero)
        assertTrue(p.anuncio.value!!.id != anuncio)
    }

    @Test
    fun `lo que llega de fuera y no esta en el catalogo sale como no esta con su codigo`() {
        val p = piso()
        p.ensenarDeFuera("7509990100194")
        assertEquals(NoEsta("7509990100194"), p.noEsta.value)
        assertEquals("No está en el catálogo: no entró al ticket", p.anuncio.value!!.texto)
        assertTrue(venta.value.renglones.isEmpty())
    }

    @Test
    fun `si los productos no han llegado entra al ticket al llegar`() {
        val p = Piso(venta, conteo, almacen, ModoApp.VENTA) { ahora }
        p.ensenarDeFuera(caja.clave)
        assertTrue("Espera a los productos", venta.value.renglones.isEmpty())
        assertNull(p.anuncio.value)
        p.cargarCatalogo(catalogo)
        assertEquals(caja.clave, venta.value.actual!!.clave)
        assertEquals("Lápiz HB No. 2 · caja con 12, \$54.00, al ticket. Total \$54.00", p.anuncio.value!!.texto)
    }

    @Test
    fun `lo que se anuncio sin la pantalla a la vista se repite cuando vuelve, una vez`() {
        val p = piso()
        // La principal a la vista y TalkBack ya conoce su región viva.
        p.alConocerseLosAnuncios()
        p.cambiarModo(ModoApp.VENTA)
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        val leido = p.anuncio.value
        p.alConocerseLosAnuncios()
        assertEquals("Ya se dijo: no se repite", leido, p.anuncio.value)
        // A Ajustes, y llega un producto de fuera: la región viva no está.
        p.alOcultarseLosAnuncios()
        p.ensenarDeFuera(lapiz.clave)
        val deFuera = p.anuncio.value!!
        p.alConocerseLosAnuncios()
        assertEquals(deFuera.texto, p.anuncio.value!!.texto)
        assertTrue("Otro anuncio con lo mismo: TalkBack lo dice ahora", p.anuncio.value!!.id != deFuera.id)
        val repetido = p.anuncio.value
        p.alConocerseLosAnuncios()
        assertEquals("Una sola vez", repetido, p.anuncio.value)
    }

    @Test
    fun `cerrar las hojas por lo que llego de fuera no cuenta como cerrada pronto`() {
        val p = piso()
        p.alElegir(Lugar.LADO, conGesto = true)
        p.abrir(Hoja.Buscar)
        ahora += 500
        p.ensenarDeFuera(cuaderno.clave)
        assertEquals("No la cerró quien atiende", ContadoresRapido().alElegir(Lugar.LADO, conElGesto = true), p.contadores.value)
    }

    // ---- Lo que no trae clave (el escaneo rápido, lo de la 0.1) ----

    @Test
    fun `lo que no trae clave se busca por su codigo y la toma al cargar los productos`() {
        venta.value = ListaLecturas().sumar(cuaderno.clave, Simbologia.EAN_13, 1)
        val p = Piso(venta, conteo, almacen, ModoApp.VENTA) { ahora }
        assertNull(venta.value.actual!!.clave)
        assertEquals("Sin productos no se sabe: no suma", 0L, totalDe(venta.value, null))
        p.cargarCatalogo(catalogo)
        assertEquals(cuaderno.clave, venta.value.actual!!.clave)
        assertEquals(4600L, totalDe(venta.value, catalogo))
        // Y elegido en Buscar cae en su renglón.
        p.abrir(Hoja.Buscar)
        p.elegirDeBusqueda(cuaderno)
        assertEquals(listOf(cuaderno.clave to 2), claves())
    }

    @Test
    fun `los contadores del boton rapido salen en su csv`() {
        val c = ContadoresRapido().alAbrirConToque().alElegir(Lugar.LADO, conElGesto = true).alCerrarPronto(Lugar.ARRIBA)
        val csv = c.csv()
        assertTrue(csv.startsWith("\uFEFFdato,veces\r\n"))
        assertTrue(csv.contains("Abierto con un toque,1\r\n"))
        assertTrue(csv.contains("←: con el gesto,1\r\n"))
        assertTrue(csv.contains("↑: cerrada en menos de 2 s,1\r\n"))
        assertEquals(1 + c.renglones().size, csv.trimEnd().lines().size)
    }

    // ---- 0.5: los gestos de la tarjeta, el artículo común y el conteo desde el menú ----

    @Test
    fun `deslizar hasta el fondo quita el renglon entero y deshacer lo regresa con todas sus piezas`() {
        val p = piso()
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 2)
        val id = venta.value.actual!!.id
        repeat(2) { p.masUno(id) }
        assertEquals(3, venta.value.actual!!.cantidad)
        p.quitar(id)
        assertEquals(listOf(cuaderno.clave), venta.value.renglones.map { it.clave })
        val a = p.aviso.value!!
        assertEquals("Quitados los 3 del ticket", a.texto)
        assertEquals("Deshacer", a.accion)
        p.accionDelAviso(a.id)
        assertEquals("Regresa arriba, donde estaba, con sus 3", lapiz.clave to 3, venta.value.actual!!.let { it.clave to it.cantidad })
        assertEquals(4, venta.value.total)
    }

    @Test
    fun `un tramo corto quita uno y con una sola pieza el renglon sale, con deshacer`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        val id = venta.value.actual!!.id
        p.masUno(id)
        p.menosUno(id)
        assertEquals(1, venta.value.total)
        assertNull("Quitar una de varias no avisa: se ve en la tarjeta", p.aviso.value)
        p.menosUno(id)
        assertTrue(venta.value.renglones.isEmpty())
        assertEquals("Quitado del ticket", p.aviso.value!!.texto)
        p.accionDelAviso(p.aviso.value!!.id)
        assertEquals(1, venta.value.total)
    }

    @Test
    fun `tocar un renglon lo sube a la tarjeta sin sumarle y se lleva el no esta`() {
        val p = piso()
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 2)
        p.alLeer("7509990100194", Simbologia.EAN_13, 3)
        assertEquals(NoEsta("7509990100194"), p.noEsta.value)
        val deAbajo = venta.value.renglones.last()
        p.subir(deAbajo.id)
        assertEquals(cuaderno.clave, venta.value.actual!!.clave)
        assertEquals("Sin sumarle", 2, venta.value.total)
        assertNull(p.noEsta.value)
    }

    @Test
    fun `el articulo comun entra al ticket con su precio a mano y suma al total`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        p.alLeer("7509990100194", Simbologia.EAN_13, 2)
        p.abrir(Hoja.Comun)
        p.agregarComun(1550)
        assertNull("Su hoja se cierra", p.hoja.value)
        assertNull("Y el «no está» se va", p.noEsta.value)
        val r = venta.value.actual!!
        assertEquals(1550L, r.comunCentavos)
        assertEquals("Artículo común", catalogo.de(r)!!.nombre)
        assertEquals(550L + 1550L, totalDe(venta.value, catalogo))
        // Otro artículo común es otro renglón: no se juntan, aunque cuesten lo mismo.
        p.agregarComun(1550)
        assertEquals(3, venta.value.renglones.size)
        // La cantidad se cambia en su tarjeta, como la de cualquiera.
        p.masUno(venta.value.actual!!.id)
        assertEquals(550L + 1550L * 3, totalDe(venta.value, catalogo))
        // Un precio de cero no entra.
        p.agregarComun(0)
        assertEquals(3, venta.value.renglones.size)
    }

    @Test
    fun `al contar los gestos van al conteo y su deshacer tambien`() {
        val p = piso()
        p.alLeer(lapiz.clave, Simbologia.EAN_13, 1)
        p.cambiarModo(ModoApp.CONTEO)
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 2)
        val id = conteo.value.actual!!.id
        p.masUno(id)
        p.conCantidad(id, 12)
        assertEquals(12, conteo.value.total)
        p.quitar(id)
        assertTrue(conteo.value.renglones.isEmpty())
        assertEquals("Quitados los 12 del conteo", p.aviso.value!!.texto)
        // El aviso sigue a la vista al volver a vender: su «Deshacer» regresa lo del conteo, no toca el ticket.
        p.cambiarModo(ModoApp.VENTA)
        p.accionDelAviso(p.aviso.value!!.id)
        assertEquals(12, conteo.value.total)
        assertEquals("El ticket, como estaba", 1, venta.value.total)
    }

    @Test
    fun `empezar otro conteo lo vacia y se puede deshacer`() {
        val p = piso()
        p.cambiarModo(ModoApp.CONTEO)
        // Vacío, no hay nada que vaciar ni que avisar.
        p.empezarOtroConteo()
        assertNull(p.aviso.value)
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 1)
        p.alLeer(cuaderno.clave, Simbologia.EAN_13, 2)
        p.empezarOtroConteo()
        assertTrue(conteo.value.renglones.isEmpty())
        assertEquals("Conteo vacío: empieza otro", p.aviso.value!!.texto)
        p.accionDelAviso(p.aviso.value!!.id)
        assertEquals(2, conteo.value.total)
    }
}
