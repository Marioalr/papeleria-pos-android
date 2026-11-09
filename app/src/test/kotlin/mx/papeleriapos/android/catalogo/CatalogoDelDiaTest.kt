package mx.papeleriapos.android.catalogo

import mx.papeleriapos.android.caja.JsonDelPos
import mx.papeleriapos.android.caja.PaqueteDto
import mx.papeleriapos.android.copia.Copia
import mx.papeleriapos.android.copia.CopiaTest
import mx.papeleriapos.pos.precio.Fecha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * docs/revision-entrega-2.md, hallazgo 5: con la misma copia, al cambiar el día los productos se vuelven a armar con las
 * promociones de ese día (antes se quedaban con las del día en que se armaron).
 */
class CatalogoDelDiaTest {
    private val copia = Copia.dePaquete(JsonDelPos.decodeFromString(PaqueteDto.serializer(), CopiaTest.textoDelPaquete()), 0L)
    private val cuaderno = "0190c5a2-0000-7000-8000-000000000001"

    @Test
    fun `con la misma copia, otro dia trae las promociones de ese dia`() {
        val noviembre = CatalogoDelDia.de(null, copia, Fecha.de(2026, 11, 8))
        assertNull(noviembre.catalogo.porClave(cuaderno)!!.promocion)
        assertSame("El mismo día no se vuelve a armar", noviembre, CatalogoDelDia.de(noviembre, copia, Fecha.de(2026, 11, 8)))
        // La app sigue viva hasta enero sin que cambie la copia: la promoción de enero ya corre.
        val enero = CatalogoDelDia.de(noviembre, copia, Fecha.de(2027, 1, 15))
        assertEquals("Solo en enero: 10 % menos", enero.catalogo.porClave(cuaderno)!!.promocion)
        assertEquals(4050L, enero.catalogo.porClave(cuaderno)!!.importe(1))
    }
}
