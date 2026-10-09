package mx.papeleriapos.android.sesion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AnotacionesTest {
    private val sesion = SesionPrueba("Moto G24", 1_760_000_000_000)
    private val vacias = Anotaciones().de(sesion)

    @Test
    fun `sin elegir nada todo es normal`() {
        assertEquals(Condicion.NORMAL, vacias.condicionActual)
        assertEquals(Condicion.NORMAL, vacias.condicionDe(1))
        assertEquals("", vacias.notaDe(1))
        assertEquals(sesion.inicio, vacias.inicioSesion)
    }

    @Test
    fun `la condicion rige desde el intento que sigue hasta que se elige otra`() {
        val a = vacias
            .conCondicion(Condicion.POCA_LUZ, siguiente = 1)
            .conCondicion(Condicion.REFLEJO, siguiente = 11)
            .conCondicion(Condicion.NORMAL, siguiente = 21)
        assertEquals((1..10).map { Condicion.POCA_LUZ }, (1..10).map { a.condicionDe(it) })
        assertEquals(Condicion.REFLEJO, a.condicionDe(11))
        assertEquals(Condicion.REFLEJO, a.condicionDe(20))
        assertEquals(Condicion.NORMAL, a.condicionDe(21))
        assertEquals(Condicion.NORMAL, a.condicionActual)
    }

    @Test
    fun `cambiar de idea antes de escanear no abre otro bloque`() {
        val a = vacias.conCondicion(Condicion.POCA_LUZ, 5).conCondicion(Condicion.MOVIDO, 5)
        assertEquals(listOf(Bloque(5, Condicion.MOVIDO)), a.bloques)
        // Y si regresa a la misma del bloque anterior, el bloque desaparece.
        val b = vacias.conCondicion(Condicion.REFLEJO, 1).conCondicion(Condicion.CURVO, 4).conCondicion(Condicion.REFLEJO, 4)
        assertEquals(listOf(Bloque(1, Condicion.REFLEJO)), b.bloques)
        // Elegir la que ya está no cambia nada.
        assertSame(b, b.conCondicion(Condicion.REFLEJO, 9))
        // Volver a normal antes del primer intento deja todo como al inicio.
        assertEquals(emptyList<Bloque>(), vacias.conCondicion(Condicion.CHICO_LEJOS, 1).conCondicion(Condicion.NORMAL, 1).bloques)
    }

    @Test
    fun `las notas se ponen, se cambian y se borran`() {
        val a = vacias.conNota(3, "  etiqueta arrugada ").conNota(7, "con funda")
        assertEquals("etiqueta arrugada", a.notaDe(3))
        assertEquals("con funda", a.notaDe(7))
        val b = a.conNota(3, "   ")
        assertEquals("", b.notaDe(3))
        assertEquals(mapOf(7 to "con funda"), b.notas)
    }

    @Test
    fun `las de otra sesion no se mezclan`() {
        val a = vacias.conCondicion(Condicion.MOVIDO, 1).conNota(1, "x")
        val otra = SesionPrueba("Otro", sesion.inicio + 1)
        assertEquals(Anotaciones(inicioSesion = otra.inicio), a.de(otra))
        assertSame(a, a.de(sesion))
        assertEquals(Anotaciones(), a.de(null))
    }

    @Test
    fun `se guardan y se recuperan como texto`() {
        val a = vacias
            .conCondicion(Condicion.VARIOS_CODIGOS, 2)
            .conCondicion(Condicion.OTRA, 9)
            .conNota(4, "con comas, \"comillas\"\ny salto")
            .conNota(10, "=no es fórmula")
        assertEquals(a, Anotaciones.deTexto(a.aTexto()))
        assertEquals(Anotaciones(), Anotaciones.deTexto(""))
        // Una condición que ya no existe se salta.
        val texto = a.aTexto().replace("OTRA", "INVENTADA")
        assertEquals(listOf(Bloque(2, Condicion.VARIOS_CODIGOS)), Anotaciones.deTexto(texto).bloques)
    }
}
