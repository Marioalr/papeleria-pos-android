package mx.papeleriapos.android.banco

import mx.papeleriapos.escaner.nucleo.Barras
import mx.papeleriapos.escaner.nucleo.Gtin
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoEscenasTest {
    private val escenas = CatalogoEscenas.escenas()

    @Test
    fun `los ids no se repiten`() {
        assertEquals(escenas.size, escenas.map { it.id }.toSet().size)
    }

    @Test
    fun `estan todos los codigos de la tienda y son validos`() {
        val simbologias = CatalogoEscenas.CODIGOS.map { it.first }.toSet()
        assertEquals(setOf(Simbologia.EAN_13, Simbologia.UPC_A, Simbologia.EAN_8, Simbologia.ITF, Simbologia.CODE_128), simbologias)
        for ((s, texto) in CatalogoEscenas.CODIGOS) {
            if (s != Simbologia.CODE_128) assertTrue("$texto no es un GTIN válido", Gtin.esValido(texto))
            // Que se pueda dibujar.
            assertTrue(Barras.para(s, texto).isNotEmpty())
        }
        assertTrue("Falta un EAN-13 750…", CatalogoEscenas.CODIGOS.any { it.second.startsWith("750") })
        assertTrue("Falta un interno 2…", CatalogoEscenas.CODIGOS.any { Gtin.esInterno(it.second) })
        assertEquals(14, CatalogoEscenas.CODIGOS.first { it.first == Simbologia.ITF }.second.length)
        assertTrue("El Code 128 debe llevar letras", CatalogoEscenas.CODIGOS.first { it.first == Simbologia.CODE_128 }.second.any { it.isLetter() })
    }

    @Test
    fun `cubre tamanos, giros, desenfoques y condiciones`() {
        val conCodigo = escenas.filter { it.conCodigo }
        assertEquals(setOf(1.5f, 2f, 3f, 5f), conCodigo.map { it.pxPorModulo }.toSet())
        assertEquals(setOf(0f, 20f, 45f), conCodigo.map { it.giro }.toSet())
        assertEquals(setOf(0, 1, 2), conCodigo.map { it.desenfoque }.toSet())
        assertTrue(conCodigo.any { it.ruido > 0f && it.luz == 1f })
        assertTrue(conCodigo.any { it.luz < 0.5f })
        assertTrue(conCodigo.any { it.reflejo > 0f })
        val sinCodigo = escenas.filter { !it.conCodigo }
        assertEquals(Negativo.entries.size * 3, sinCodigo.size)
        assertEquals(Negativo.entries.toSet(), sinCodigo.map { it.negativo }.toSet())
    }

    @Test
    fun `lo que no cabe completo se queda fuera y lo demas si esta`() {
        val ids = escenas.map { it.id }.toSet()
        // Un ITF-14 a 5 px/módulo mide 775 px: no cabe en 720.
        assertFalse(ids.any { it.startsWith("itf-5px") })
        assertFalse(ids.any { it.startsWith("code128-5px") })
        // Un EAN-13 a 5 px/módulo mide 565 px y cabe aun girado 45°.
        assertTrue("ean13-5px-45g" in ids)
        assertTrue("itf-3px-20g" in ids)
        for (e in escenas) assertTrue(e.id, CatalogoEscenas.cabe(e, 720, 1280))
        assertEquals(6 * (12 + 4 + 3) - 6 + 12, escenas.size)
    }

    @Test
    fun `una escena se dibuja del tamano pedido con el codigo al centro y fondo en las orillas`() {
        val e = escenas.first { it.id == "ean13-3px-0g" }
        val luma = CatalogoEscenas.dibujar(e, 720, 1280)
        assertEquals(720, luma.ancho)
        assertEquals(1280, luma.alto)
        // A lo largo del centro hay barras (oscuro) y espacios (claro).
        val fila = (0 until 720).map { luma[it, 640] }
        assertTrue(fila.any { it < 60 })
        assertTrue(fila.any { it > 200 })
        // Las esquinas son fondo.
        assertEquals(170, luma[0, 0])
        assertEquals(170, luma[719, 1279])
    }

    @Test
    fun `los codigos mas grandes no tocan la orilla aunque esten girados`() {
        for ((s, _) in CatalogoEscenas.CODIGOS) {
            val mayor = escenas.filter { it.simbologia == s && it.giro == 45f }.maxBy { it.pxPorModulo }
            val luma = CatalogoEscenas.dibujar(mayor, 720, 1280)
            for (x in 0 until 720) {
                assertEquals(mayor.id, 170, luma[x, 0]); assertEquals(mayor.id, 170, luma[x, 1279])
            }
            for (y in 0 until 1280) {
                assertEquals(mayor.id, 170, luma[0, y]); assertEquals(mayor.id, 170, luma[719, y])
            }
        }
    }

    @Test
    fun `poca luz oscurece y las escenas sin codigo se dibujan`() {
        val oscuro = CatalogoEscenas.dibujar(escenas.first { it.id == "ean13-3px-oscuro" }, 720, 1280)
        val normal = CatalogoEscenas.dibujar(escenas.first { it.id == "ean13-3px-0g" }, 720, 1280)
        val media = { l: mx.papeleriapos.escaner.nucleo.Luma -> l.pixeles.sumOf { it.toInt() and 0xFF } / l.pixeles.size }
        assertTrue(media(oscuro) < media(normal) / 2)
        for (n in Negativo.entries) {
            val luma = CatalogoEscenas.dibujar(escenas.first { it.negativo == n }, 720, 1280)
            assertEquals(720 * 1280, luma.pixeles.size)
        }
    }
}
