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
        val c = Confirmador.normalizar(lectura("7509993456786"))!!
        assertEquals("7509993456786", c.normalizado)
        assertEquals(listOf("7509993456786"), c.clavesDeBusqueda)
        assertNull(Confirmador.normalizar(lectura("7509993456787")))
        assertNull(Confirmador.normalizar(lectura("750999345678")))
    }

    @Test
    fun `UPC-A de 12 se busca con 12 y 13`() {
        val c = Confirmador.normalizar(lectura("099999291452", Simbologia.UPC_A))!!
        assertEquals("0099999291452", c.normalizado)
        assertEquals("099999291452", c.original)
        assertTrue(c.clavesDeBusqueda.containsAll(listOf("099999291452", "0099999291452")))
        // zxing-cpp lo da con 13.
        assertEquals("0099999291452", Confirmador.normalizar(lectura("0099999291452", Simbologia.UPC_A))!!.normalizado)
        assertNull(Confirmador.normalizar(lectura("099999291453", Simbologia.UPC_A)))
    }

    @Test
    fun `UPC-E de 8 se expande y se busca con 8, 12 y 13`() {
        val c = Confirmador.normalizar(lectura("09952618", Simbologia.UPC_E))!!
        assertEquals("0099100005268", c.normalizado)
        assertTrue(c.clavesDeBusqueda.containsAll(listOf("09952618", "099100005268", "0099100005268")))
        // Expandido por zxing-cpp: la clave corta se recupera.
        val z = Confirmador.normalizar(lectura("0099100005268", Simbologia.UPC_E))!!
        assertEquals("0099100005268", z.normalizado)
        assertTrue(z.clavesDeBusqueda.contains("09952618"))
        assertNull(Confirmador.normalizar(lectura("09952619", Simbologia.UPC_E)))
    }

    @Test
    fun `ITF solo de 14 digitos con verificador`() {
        assertEquals("17509990000019", Confirmador.normalizar(lectura("17509990000019", Simbologia.ITF))!!.normalizado)
        assertNull(Confirmador.normalizar(lectura("17509990000018", Simbologia.ITF)))
        // Como la lectura falsa de ML Kit que reprodujo la investigación: el principio del ITF y luego ceros.
        assertNull(Confirmador.normalizar(lectura("1750999000000000000000000000000000000000", Simbologia.ITF)))
        assertNull(Confirmador.normalizar(lectura("123456", Simbologia.ITF)))
        // Un GTIN-14 que empieza con 0 también se busca como GTIN-13.
        val cero = Gtin.completar("0750999345678")
        assertTrue(Confirmador.normalizar(lectura(cero, Simbologia.ITF))!!.clavesDeBusqueda.contains(cero.substring(1)))
    }

    @Test
    fun `Code 128 sin prefijo de simbologia ni caracteres de control`() {
        assertEquals("ZZP0042x", Confirmador.normalizar(lectura("ZZP0042x", Simbologia.CODE_128))!!.normalizado)
        assertEquals("0107509993456786", Confirmador.normalizar(lectura("]C10107509993456786", Simbologia.CODE_128))!!.normalizado)
        assertEquals("ZZ12", Confirmador.normalizar(lectura("ZZ\u001D12", Simbologia.CODE_128))!!.normalizado)
        assertNull(Confirmador.normalizar(lectura("\u001D", Simbologia.CODE_128)))
    }

    @Test
    fun `simbologias de verificacion debil no se aceptan`() {
        assertNull(Confirmador.normalizar(lectura("ABC123", Simbologia.CODE_39)))
        assertNull(Confirmador.normalizar(lectura("A123B", Simbologia.CODABAR)))
        assertNotNull(Confirmador.normalizar(lectura("A-000123", Simbologia.QR)))
    }

    // ---- Consenso ----

    @Test
    fun `hacen falta dos lecturas iguales seguidas y el QR entra a la primera`() {
        val c = Confirmador()
        assertNull(c.cuadro(lectura("7509993456786") to centro))
        val a = c.cuadro(lectura("7509993456786") to centro)
        assertNotNull(a)
        assertEquals("7509993456786", a!!.codigo.normalizado)
        assertFalse(a.repetida)
        assertEquals(centro, a.caja)

        val q = Confirmador()
        assertNotNull(q.cuadro(lectura("ZZ-000001", Simbologia.QR) to centro))
    }

    @Test
    fun `un cuadro sin la lectura rompe la racha`() {
        val c = Confirmador()
        val l = lectura("7509993456786") to centro
        // Lectura, cuadro vacío, lectura: no son seguidas.
        assertNull(c.cuadro(l))
        assertNull(c.cuadro())
        assertNull(c.cuadro(l))
        // Un cuadro con otro código en medio tampoco deja seguir la racha.
        assertNull(c.cuadro(lectura("7509990000012") to centro))
        assertNull(c.cuadro(l))
        // Una lectura inválida en el mismo lugar, tampoco.
        assertNull(c.cuadro(lectura("7509993456787") to centro))
        assertNull(c.cuadro(l))
        // Dos seguidas: entra.
        assertNotNull(c.cuadro(l))
    }

    @Test
    fun `en 1D hacen falta dos cuadros seguidos y el QR entra de uno`() {
        val unaD = listOf(
            lectura("7509993456786", Simbologia.EAN_13),
            lectura("099999291452", Simbologia.UPC_A),
            lectura("09952618", Simbologia.UPC_E),
            lectura("75099916", Simbologia.EAN_8),
            lectura("17509990000019", Simbologia.ITF),
            lectura("ZZ-PRUEBA-0001", Simbologia.CODE_128),
        )
        for (l in unaD) {
            val c = Confirmador()
            // Un solo cuadro nunca basta, aunque luego no haya nada.
            assertNull(l.simbologia.nombre, c.cuadro(l to centro))
            repeat(5) { assertNull(l.simbologia.nombre, c.cuadro()) }
            assertNull(l.simbologia.nombre, c.cuadro(l to centro))
            assertNotNull(l.simbologia.nombre, c.cuadro(l to centro))
        }
        val q = Confirmador()
        assertNotNull(q.cuadro(lectura("A-000123", Simbologia.QR) to centro))
    }

    @Test
    fun `un verificador mal nunca entra, ni en muchos cuadros seguidos`() {
        val malas = listOf(
            lectura("7509993456787", Simbologia.EAN_13),
            lectura("099999291453", Simbologia.UPC_A),
            lectura("0099999291453", Simbologia.UPC_A),
            lectura("09952619", Simbologia.UPC_E),
            lectura("75099917", Simbologia.EAN_8),
            lectura("17509990000018", Simbologia.ITF),
        )
        for (l in malas) {
            val c = Confirmador()
            repeat(30) { assertNull("${l.simbologia.nombre} ${l.texto}", c.cuadro(l to centro)) }
        }
        // Tampoco junto a la buena: la mala no suma a su racha.
        val c = Confirmador()
        repeat(10) {
            assertNull(c.cuadro(lectura("7509993456787") to centro))
            assertNull(c.cuadro(lectura("7509993456786") to centro))
        }
    }

    @Test
    fun `un UPC-A de 12 y su EAN-13 cuentan juntos`() {
        val c = Confirmador()
        assertNull(c.cuadro(lectura("099999291452", Simbologia.UPC_A) to centro))
        assertNotNull(c.cuadro(lectura("0099999291452", Simbologia.EAN_13) to centro))
    }

    @Test
    fun `dos lecturas distintas no hacen consenso y lo invalido nunca entra`() {
        val c = Confirmador()
        assertNull(c.cuadro(lectura("7509993456786") to centro))
        assertNull(c.cuadro(lectura("7509990000012") to centro))
        val malo = lectura("1750999000000000000000000000000000000000", Simbologia.ITF)
        repeat(10) { assertNull(c.cuadro(malo to centro)) }
    }

    // ---- Varios a la vista ----

    @Test
    fun `con dos codigos se toma el que tiene el centro de la franja`() {
        val c = Confirmador()
        val otro = lectura("7509990000012") to izquierda
        val elegido = lectura("7509993456786") to centro
        assertNull(c.cuadro(otro, elegido))
        val a = c.cuadro(otro, elegido)
        assertEquals("7509993456786", a!!.codigo.normalizado)
        // El de un lado tiene consenso, pero no se toma mientras el centro tenga otro código.
        repeat(5) { assertNull(c.cuadro(otro, elegido)) }
    }

    /**
     * Dos códigos juntos, los dos leídos en cada cuadro durante [cuadros]; las cajas se mueven un poco, así el centro
     * de la franja cae 3 cuadros en uno y 3 en el otro.
     */
    private fun Confirmador.alternar(cuadros: Int): List<Aceptada> {
        val a = lectura("7509993456786")
        val b = lectura("7509990000012")
        val cajaA = Caja(0.3f, 0.40f, 0.7f, 0.49f)
        val cajaB = Caja(0.3f, 0.51f, 0.7f, 0.60f)
        val aceptadas = ArrayList<Aceptada>()
        for (k in 0 until cuadros) {
            val sube = if (k % 6 < 3) 0.03f else -0.03f
            val r = cuadro(
                a to cajaA.copy(arriba = cajaA.arriba + sube, abajo = cajaA.abajo + sube),
                b to cajaB.copy(arriba = cajaB.arriba + sube, abajo = cajaB.abajo + sube),
            )
            if (r != null) aceptadas += r
        }
        return aceptadas
    }

    @Test
    fun `dos codigos que se alternan en el centro no se cuentan una y otra vez`() {
        // Sin el plazo a la vista: cada uno una vez en 3 s, por más que el centro vaya y venga.
        val sinPlazo = Confirmador(ReglasConfirmacion(msParaRepetirAVista = 0)).alternar(90)
        assertEquals(listOf("7509993456786", "7509990000012"), sinPlazo.map { it.codigo.normalizado })

        // Con el plazo de 1.5 s: cada uno a lo más una vez cada 1.5 s, no en cada vuelta del centro.
        val conPlazo = Confirmador().alternar(90)
        for ((codigo, veces) in conPlazo.groupBy { it.codigo.normalizado }) {
            assertTrue("$codigo: ${veces.size} veces", veces.size <= 2)
            for (i in 1 until veces.size) {
                val ms = (veces[i].nanos - veces[i - 1].nanos) / 1_000_000L
                assertTrue("$codigo volvió a contar a los $ms ms", ms >= 1500L)
            }
        }
        assertTrue("aceptadas ${conPlazo.size}", conPlazo.size <= 4)
    }

    // ---- Repetidos ----

    @Test
    fun `el mismo codigo a la vista no se repite antes del plazo`() {
        // Casi 1.5 s a la vista: una vez.
        val c = Confirmador()
        var aceptadas = 0
        repeat(45) { if (c.cuadro(lectura("7509993456786") to centro) != null) aceptadas++ }
        assertEquals(1, aceptadas)
        // Sin el plazo a la vista (0): una sola vez en 5 s.
        val sinPlazo = Confirmador(ReglasConfirmacion(msParaRepetirAVista = 0))
        aceptadas = 0
        repeat(150) { if (sinPlazo.cuadro(lectura("7509993456786") to centro) != null) aceptadas++ }
        assertEquals(1, aceptadas)
    }

    @Test
    fun `a la vista vuelve a contar a los 1500 ms y no antes`() {
        val c = Confirmador()
        val l = lectura("7509993456786") to centro
        val aceptadas = ArrayList<Aceptada>()
        // 5 s con el mismo código enfrente, sin retirarlo.
        repeat(150) {
            val r = c.cuadro(l)
            if (r != null) {
                aceptadas += r
            } else if (aceptadas.isNotEmpty()) {
                // Entre una y otra, la guía dice «ya leído».
                assertTrue(c.yaLeidoEnCuadro)
            }
        }
        assertEquals(4, aceptadas.size)
        assertFalse(aceptadas[0].repetida)
        for (i in 1 until aceptadas.size) {
            val ms = (aceptadas[i].nanos - aceptadas[i - 1].nanos) / 1_000_000L
            // No antes de 1.5 s, y en el primer cuadro después.
            assertTrue("a los $ms ms", ms in 1500L..1534L)
            assertTrue(aceptadas[i].repetida)
        }
    }

    @Test
    fun `vuelve a contar despues de medio segundo fuera de la mira`() {
        val c = Confirmador()
        val l = lectura("7509993456786") to centro
        c.cuadro(l); assertNotNull(c.cuadro(l))
        // 400 ms fuera: no basta (y no han pasado 1.5 s).
        c.vacios(400)
        assertNull(c.cuadro(l)); assertNull(c.cuadro(l)); assertNull(c.cuadro(l))
        // 600 ms fuera: cuenta otra vez, como repetida, antes de 1.5 s desde la primera.
        c.vacios(600)
        assertNull(c.cuadro(l))
        val otra = c.cuadro(l)
        assertNotNull(otra)
        assertTrue(otra!!.repetida)
        assertTrue(otra.nanos < 1_500_000_000L)
    }

    @Test
    fun `un parpadeo del motor no rearma`() {
        // El motor deja de leerlo menos de 500 ms y lo vuelve a leer: no cuenta otra vez.
        val c = Confirmador(ReglasConfirmacion(msParaRepetirAVista = 0))
        val l = lectura("7509993456786") to centro
        c.cuadro(l); assertNotNull(c.cuadro(l))
        repeat(10) {
            c.vacios(300)
            repeat(5) { assertNull(c.cuadro(l)) }
        }
    }

    @Test
    fun `un potencial en el mismo lugar cuenta como que sigue ahi`() {
        // Sin el plazo a la vista, para ver solo la presencia.
        val c = Confirmador(ReglasConfirmacion(msParaRepetirAVista = 0))
        val l = lectura("7509993456786") to centro
        c.cuadro(l); assertNotNull(c.cuadro(l))
        // El motor deja de leerlo pero lo sigue viendo (p. ej. se movió y está borroso).
        repeat(30) { c.cuadro(potenciales = listOf(centro.copy(izquierda = 0.32f))) }
        assertNull(c.cuadro(l)); assertNull(c.cuadro(l))
        // Una lectura inválida en el mismo lugar también.
        repeat(30) { c.cuadro(lectura("7509993456787") to centro) }
        assertNull(c.cuadro(l)); assertNull(c.cuadro(l))
    }

    @Test
    fun `un codigo distinto entra de inmediato`() {
        val c = Confirmador()
        val a = lectura("7509993456786") to centro
        val b = lectura("7509990000012") to centro
        c.cuadro(a); assertNotNull(c.cuadro(a))
        c.cuadro(b)
        val otra = c.cuadro(b)
        assertNotNull(otra)
        assertFalse(otra!!.repetida)
    }

    @Test
    fun `la ausencia configurable y reiniciar`() {
        val c = Confirmador(ReglasConfirmacion(msAusenciaParaRepetir = 100))
        val l = lectura("7509993456786") to centro
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
        val lecturas = listOf(lectura("7509993456786") to centro)
        val vacias = emptyList<Pair<Lectura, Caja?>>()
        val sin = emptyList<Caja>()
        // El código a la vista y, uno de cada siete cuadros, sin leer: la racha se rompe y se rehace.
        repeat(100) { i -> c.procesar(if (i % 7 == 6) vacias else lecturas, sin, franja, t); t += 33_333_333L }
        val hilo = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val antes = hilo.getThreadAllocatedBytes(id)
        repeat(1000) { i -> c.procesar(if (i % 7 == 6) vacias else lecturas, sin, franja, t); t += 33_333_333L }
        val porCuadro = (hilo.getThreadAllocatedBytes(id) - antes) / 1000.0
        println("confirmador: bytes asignados por cuadro: $porCuadro")
        assertTrue("asigna $porCuadro bytes por cuadro", porCuadro < 64)
    }

    @Test
    fun `un cuadro que no se pudo analizar rompe la racha`() {
        val c = Confirmador()
        val l = lectura("7509993456786") to centro
        assertNull(c.cuadro(l))
        c.cortarRacha()
        assertNull(c.cuadro(l))
        assertNotNull(c.cuadro(l))
    }

    @Test
    fun `avisa que el codigo a la vista ya se leyo hasta que se rearma`() {
        val c = Confirmador()
        val l = lectura("7509993456786") to centro
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
