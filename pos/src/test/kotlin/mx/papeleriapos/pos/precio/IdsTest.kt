package mx.papeleriapos.pos.precio

import mx.papeleriapos.pos.Cantidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los Id como texto: iguales sin importar mayúsculas y en el orden de `Guid.CompareTo` de .NET, que decide el empate entre
 * dos promociones. El orden esperado es el que da .NET con estos mismos Guid.
 */
class IdsTest {
    @Test
    fun `se ordenan como los Guid de dotnet, sin signo`() {
        val enOrden = listOf(
            "00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002",
            "00000000-0000-0000-7fff-ffffffffffff",
            "00000000-0000-0000-8000-000000000000",
            "00000000-0000-7fff-0000-000000000000",
            "00000000-0000-8000-0000-000000000000",
            "00000000-7fff-0000-0000-000000000000",
            "00000000-8000-0000-0000-000000000000",
            "0190c5a2-7f3b-7c1e-9a4d-000000000000",
            "7fffffff-ffff-ffff-ffff-ffffffffffff",
            "80000000-0000-0000-0000-000000000000",
            "FFFFFFFF-0000-0000-0000-000000000000",
            "ffffffff-ffff-ffff-ffff-ffffffffffff",
        )

        assertEquals(enOrden, enOrden.shuffled(java.util.Random(7)).sortedWith(Ids::comparar))
    }

    @Test
    fun `son iguales con mayusculas o minusculas`() {
        assertTrue(Ids.iguales("0190C5A2-7F3B-7C1E-9A4D-00000000ABCD", "0190c5a2-7f3b-7c1e-9a4d-00000000abcd"))
        assertEquals(0, Ids.comparar("ABCDEF00-0000-0000-0000-000000000000", "abcdef00-0000-0000-0000-000000000000"))
        assertFalse(Ids.iguales(null, "0190c5a2-7f3b-7c1e-9a4d-00000000abcd"))
        assertFalse(Ids.iguales("0190c5a2-7f3b-7c1e-9a4d-00000000abce", "0190c5a2-7f3b-7c1e-9a4d-00000000abcd"))
    }

    /** Con `java.util.UUID`, que compara con signo, «80000000-…» quedaría antes y ganaría el empate: la caja elige la otra. */
    @Test
    fun `en un empate entre promociones gana la de menor Id como la ordena dotnet`() {
        val cuaderno = nuevoId()
        val pieza = Presentacion(nuevoId(), cuaderno)
        val hoy = Fecha.de(2027, 8, 4)
        fun diezPorCiento(id: String) =
            PromocionDeProducto(id, id.substring(0, 1), TipoPromocion.PORCENTAJE, centesimas = 1000, desde = hoy, hasta = hoy)
        val arriba = diezPorCiento("80000000-0000-0000-0000-000000000000")
        val abajo = diezPorCiento("7fffffff-ffff-ffff-ffff-ffffffffffff")

        for (promociones in listOf(listOf(arriba, abajo), listOf(abajo, arriba))) {
            val aplicado = CalculoPrecio.calcular(
                listOf(Precio(cuaderno, 1000)), nuevoId(), pieza, Cantidad.piezas(1), promociones = promociones, dia = hoy,
            )!!
            assertEquals("7", aplicado.promocion!!.nombre)
            assertEquals(900L, aplicado.importeCentavos)
        }
    }
}
