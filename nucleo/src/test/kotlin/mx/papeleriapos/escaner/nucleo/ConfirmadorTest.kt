package mx.papeleriapos.escaner.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmadorTest {
    private val franja = Caja(0.04f, 0.32f, 0.96f, 0.68f)
    private val centro = Caja(0.3f, 0.45f, 0.7f, 0.55f)
    private val izquierda = Caja(0.05f, 0.45f, 0.25f, 0.55f)
    private var t = 0L

    private fun lectura(texto: String, s: Simbologia = Simbologia.EAN_13) = Lectura(texto, s, null)

    /** Un cuadro a 30 fps con [lecturas] (cada una con su caja) y [potenciales]. */
    private fun Confirmador.cuadro(vararg lecturas: Pair<Lectura, Caja?>, potenciales: List<Caja> = emptyList()): Aceptada? {
        val r = procesar(lecturas.toList(), potenciales, franja, t)
        t += 33_333_333L
        return r
    }

    private fun Confirmador.vacios(ms: Long) {
        val n = (ms * 30 / 1000).toInt()
        repeat(n) { cuadro() }
    }

    // ---- Normalización ----

    @Test
    fun `EAN-13 valido e invalido`() {
        val c = Confirmador.normalizar(lectura("7501234567893"))!!
        assertEquals("7501234567893", c.normalizado)
        assertEquals(listOf("7501234567893"), c.clavesDeBusqueda)
        assertNull(Confirmador.normalizar(lectura("7501234567894")))
        assertNull(Confirmador.normalizar(lectura("750123456789")))
    }

    @Test
    fun `UPC-A de 12 se busca con 12 y 13`() {
        val c = Confirmador.normalizar(lectura("036000291452", Simbologia.UPC_A))!!
        assertEquals("0036000291452", c.normalizado)
        assertEquals("036000291452", c.original)
        assertTrue(c.clavesDeBusqueda.containsAll(listOf("036000291452", "0036000291452")))
        // zxing-cpp lo da con 13.
        assertEquals("0036000291452", Confirmador.normalizar(lectura("0036000291452", Simbologia.UPC_A))!!.normalizado)
        assertNull(Confirmador.normalizar(lectura("036000291453", Simbologia.UPC_A)))
    }

    @Test
    fun `UPC-E de 8 se expande y se busca con 8, 12 y 13`() {
        val c = Confirmador.normalizar(lectura("04252614", Simbologia.UPC_E))!!
        assertEquals("0042100005264", c.normalizado)
        assertTrue(c.clavesDeBusqueda.containsAll(listOf("04252614", "042100005264", "0042100005264")))
        // Expandido por zxing-cpp: la clave corta se recupera.
        val z = Confirmador.normalizar(lectura("0042100005264", Simbologia.UPC_E))!!
        assertEquals("0042100005264", z.normalizado)
        assertTrue(z.clavesDeBusqueda.contains("04252614"))
        assertNull(Confirmador.normalizar(lectura("04252615", Simbologia.UPC_E)))
    }

    @Test
    fun `ITF solo de 14 digitos con verificador`() {
        assertEquals("17501000000019", Confirmador.normalizar(lectura("17501000000019", Simbologia.ITF))!!.normalizado)
        assertNull(Confirmador.normalizar(lectura("17501000000018", Simbologia.ITF)))
        // La lectura falsa de ML Kit que reprodujo la investigación.
        assertNull(Confirmador.normalizar(lectura("1750100000000000000000000000000000000000", Simbologia.ITF)))
        assertNull(Confirmador.normalizar(lectura("123456", Simbologia.ITF)))
        // Un GTIN-14 que empieza con 0 también se busca como GTIN-13.
        val cero = Gtin.completar("0750123456789")
        assertTrue(Confirmador.normalizar(lectura(cero, Simbologia.ITF))!!.clavesDeBusqueda.contains(cero.substring(1)))
    }

    @Test
    fun `Code 128 sin prefijo de simbologia ni caracteres de control`() {
        assertEquals("PAP0042x", Confirmador.normalizar(lectura("PAP0042x", Simbologia.CODE_128))!!.normalizado)
        assertEquals("0107501234567893", Confirmador.normalizar(lectura("]C10107501234567893", Simbologia.CODE_128))!!.normalizado)
        assertEquals("AB12", Confirmador.normalizar(lectura("AB\u001D12", Simbologia.CODE_128))!!.normalizado)
        assertNull(Confirmador.normalizar(lectura("\u001D", Simbologia.CODE_128)))
    }

    @Test
    fun `simbologias de verificacion debil no se aceptan`() {
        assertNull(Confirmador.normalizar(lectura("ABC123", Simbologia.CODE_39)))
        assertNull(Confirmador.normalizar(lectura("A123B", Simbologia.CODABAR)))
        assertNotNull(Confirmador.normalizar(lectura("ticket:A-000123", Simbologia.QR)))
    }

    // ---- Consenso ----

    @Test
    fun `hacen falta dos lecturas iguales y el QR entra a la primera`() {
        val c = Confirmador()
        assertNull(c.cuadro(lectura("7501234567893") to centro))
        val a = c.cuadro(lectura("7501234567893") to centro)
        assertNotNull(a)
        assertEquals("7501234567893", a!!.codigo.normalizado)
        assertFalse(a.repetida)
        assertEquals(centro, a.caja)

        val q = Confirmador()
        assertNotNull(q.cuadro(lectura("ticket:A-1", Simbologia.QR) to centro))
    }

    @Test
    fun `las dos lecturas deben caber en la ventana de tres cuadros`() {
        val c = Confirmador()
        assertNull(c.cuadro(lectura("7501234567893") to centro))
        assertNull(c.cuadro())
        assertNull(c.cuadro())
        // La primera ya salió de la ventana.
        assertNull(c.cuadro(lectura("7501234567893") to centro))
        assertNotNull(c.cuadro(lectura("7501234567893") to centro))
    }

    @Test
    fun `un UPC-A de 12 y su EAN-13 cuentan juntos`() {
        val c = Confirmador()
        assertNull(c.cuadro(lectura("036000291452", Simbologia.UPC_A) to centro))
        assertNotNull(c.cuadro(lectura("0036000291452", Simbologia.EAN_13) to centro))
    }

    @Test
    fun `dos lecturas distintas no hacen consenso y lo invalido nunca entra`() {
        val c = Confirmador()
        assertNull(c.cuadro(lectura("7501234567893") to centro))
        assertNull(c.cuadro(lectura("7501000000012") to centro))
        val malo = lectura("1750100000000000000000000000000000000000", Simbologia.ITF)
        repeat(10) { assertNull(c.cuadro(malo to centro)) }
    }

    // ---- Varios a la vista ----

    @Test
    fun `con dos codigos se toma el que tiene el centro de la franja`() {
        val c = Confirmador()
        val otro = lectura("7501000000012") to izquierda
        val elegido = lectura("7501234567893") to centro
        assertNull(c.cuadro(otro, elegido))
        val a = c.cuadro(otro, elegido)
        assertEquals("7501234567893", a!!.codigo.normalizado)
        // El de un lado tiene consenso, pero no se toma mientras el centro tenga otro código.
        repeat(5) { assertNull(c.cuadro(otro, elegido)) }
    }

    @Test
    fun `dos codigos que se alternan en el centro no se cuentan una y otra vez`() {
        val c = Confirmador()
        val a = lectura("7501234567893")
        val b = lectura("7501000000012")
        val cajaA = Caja(0.3f, 0.40f, 0.7f, 0.49f)
        val cajaB = Caja(0.3f, 0.51f, 0.7f, 0.60f)
        var aceptadas = 0
        // Se leen los dos en cada cuadro; las cajas se mueven un poco, así el centro cae en uno y luego en el otro.
        for (k in 0 until 90) {
            val sube = if (k % 6 < 3) 0.03f else -0.03f
            val r = c.cuadro(
                a to cajaA.copy(arriba = cajaA.arriba + sube, abajo = cajaA.abajo + sube),
                b to cajaB.copy(arriba = cajaB.arriba + sube, abajo = cajaB.abajo + sube),
            )
            if (r != null) aceptadas++
        }
        assertTrue("aceptadas $aceptadas", aceptadas <= 2)
    }

    // ---- Repetidos ----

    @Test
    fun `el mismo codigo a la vista no se repite`() {
        val c = Confirmador()
        var aceptadas = 0
        repeat(150) { if (c.cuadro(lectura("7501234567893") to centro) != null) aceptadas++ }
        assertEquals(1, aceptadas)
    }

    @Test
    fun `vuelve a contar solo despues de medio segundo de ausencia`() {
        val c = Confirmador()
        val l = lectura("7501234567893") to centro
        c.cuadro(l); assertNotNull(c.cuadro(l))
        // 400 ms fuera: no basta.
        c.vacios(400)
        assertNull(c.cuadro(l)); assertNull(c.cuadro(l)); assertNull(c.cuadro(l))
        // 600 ms fuera: cuenta otra vez, como repetida.
        c.vacios(600)
        c.cuadro(l)
        val otra = c.cuadro(l)
        assertNotNull(otra)
        assertTrue(otra!!.repetida)
    }

    @Test
    fun `un potencial en el mismo lugar cuenta como que sigue ahi`() {
        val c = Confirmador()
        val l = lectura("7501234567893") to centro
        c.cuadro(l); assertNotNull(c.cuadro(l))
        // El motor deja de leerlo pero lo sigue viendo (p. ej. se movió y está borroso).
        repeat(30) { c.cuadro(potenciales = listOf(centro.copy(izquierda = 0.32f))) }
        assertNull(c.cuadro(l)); assertNull(c.cuadro(l))
        // Una lectura inválida en el mismo lugar también.
        repeat(30) { c.cuadro(lectura("7501234567894") to centro) }
        assertNull(c.cuadro(l)); assertNull(c.cuadro(l))
    }

    @Test
    fun `un codigo distinto entra de inmediato`() {
        val c = Confirmador()
        val a = lectura("7501234567893") to centro
        val b = lectura("7501000000012") to centro
        c.cuadro(a); assertNotNull(c.cuadro(a))
        c.cuadro(b)
        val otra = c.cuadro(b)
        assertNotNull(otra)
        assertFalse(otra!!.repetida)
    }

    @Test
    fun `la ausencia configurable y reiniciar`() {
        val c = Confirmador(ReglasConfirmacion(msAusenciaParaRepetir = 100))
        val l = lectura("7501234567893") to centro
        c.cuadro(l); assertNotNull(c.cuadro(l))
        c.vacios(150)
        c.cuadro(l); assertNotNull(c.cuadro(l))

        c.reiniciar()
        // Tras reiniciar, empieza de cero: hace falta el consenso otra vez y no es repetida.
        assertNull(c.cuadro(l))
        assertFalse(c.cuadro(l)!!.repetida)
    }

    @Test
    fun `un cuadro repetido no asigna memoria en el camino normal`() {
        val c = Confirmador()
        val lecturas = listOf(lectura("7501234567893") to centro)
        val sin = emptyList<Caja>()
        repeat(100) { c.procesar(lecturas, sin, franja, t); t += 33_333_333L }
        val hilo = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val antes = hilo.getThreadAllocatedBytes(id)
        repeat(1000) { c.procesar(lecturas, sin, franja, t); t += 33_333_333L }
        val porCuadro = (hilo.getThreadAllocatedBytes(id) - antes) / 1000.0
        println("confirmador: bytes asignados por cuadro: $porCuadro")
        assertTrue("asigna $porCuadro bytes por cuadro", porCuadro < 64)
    }

    @Test
    fun `avisa que el codigo a la vista ya se leyo hasta que se retira`() {
        val c = Confirmador()
        val l = lectura("7501234567893") to centro
        c.cuadro(l); assertFalse(c.yaLeidoEnCuadro)
        assertNotNull(c.cuadro(l)); assertFalse(c.yaLeidoEnCuadro)
        // Sigue a la vista: ya leído (también si el motor solo lo ve sin leerlo).
        c.cuadro(l); assertTrue(c.yaLeidoEnCuadro)
        c.cuadro(potenciales = listOf(centro)); assertTrue(c.yaLeidoEnCuadro)
        // Se retira: ya no.
        c.cuadro(); assertFalse(c.yaLeidoEnCuadro)
        c.vacios(600)
        // Vuelve tras la ausencia: cuenta otra vez, no es «ya leído».
        c.cuadro(l); assertFalse(c.yaLeidoEnCuadro)
        assertNotNull(c.cuadro(l))
    }
}
