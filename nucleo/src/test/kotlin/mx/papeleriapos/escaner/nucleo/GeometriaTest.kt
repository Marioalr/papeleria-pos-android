package mx.papeleriapos.escaner.nucleo

import org.junit.Assert.assertEquals
import org.junit.Test

class GeometriaTest {
    private val p = FloatArray(2)

    @Test
    fun `sin rotar solo resta el recorte y normaliza`() {
        val t = Transformacion(Rectangulo(100, 0, 1180, 720), 0)
        t.aVisible(100f, 0f, p); assertEquals(0f, p[0], 1e-4f); assertEquals(0f, p[1], 1e-4f)
        t.aVisible(1180f, 720f, p); assertEquals(1f, p[0], 1e-4f); assertEquals(1f, p[1], 1e-4f)
    }

    @Test
    fun `con el celular vertical el buffer gira 90 grados`() {
        // Buffer 1280x720 (apaisado); en pantalla vertical se ve de 720 de ancho por 1280 de alto.
        val t = Transformacion(Rectangulo(0, 0, 1280, 720), 90)
        assertEquals(720, t.anchoDerecho); assertEquals(1280, t.altoDerecho)
        // La esquina superior izquierda del buffer queda arriba a la derecha en pantalla.
        t.aVisible(0f, 0f, p); assertEquals(1f, p[0], 1e-4f); assertEquals(0f, p[1], 1e-4f)
        // La parte de abajo del buffer (y grande) queda a la izquierda.
        t.aVisible(0f, 720f, p); assertEquals(0f, p[0], 1e-4f); assertEquals(0f, p[1], 1e-4f)
        t.aVisible(1280f, 360f, p); assertEquals(0.5f, p[0], 1e-4f); assertEquals(1f, p[1], 1e-4f)
    }

    @Test
    fun `ida y vuelta en las cuatro rotaciones`() {
        for (rot in listOf(0, 90, 180, 270)) {
            val t = Transformacion(Rectangulo(40, 10, 1240, 710), rot)
            for ((x, y) in listOf(0.1f to 0.2f, 0.5f to 0.5f, 0.9f to 0.7f)) {
                t.aBuffer(x, y, p)
                t.aVisible(p[0], p[1], p)
                assertEquals("rot $rot x", x, p[0], 1e-4f)
                assertEquals("rot $rot y", y, p[1], 1e-4f)
            }
        }
    }

    @Test
    fun `la franja horizontal de pantalla es vertical en el buffer girado`() {
        val t = Transformacion(Rectangulo(0, 0, 1280, 720), 90)
        val franja = t.aBuffer(Caja(0f, 0.3f, 1f, 0.7f))
        assertEquals(Rectangulo(384, 0, 896, 720), franja)
    }

    @Test
    fun `Rotacion ida y vuelta`() {
        for (rot in listOf(0, 90, 180, 270)) {
            Rotacion.aDerecho(30f, 50f, 200, 100, rot, p)
            Rotacion.aBuffer(p[0], p[1], 200, 100, rot, p)
            assertEquals(30f, p[0], 1e-4f); assertEquals(50f, p[1], 1e-4f)
        }
    }

    @Test
    fun `traslape de cajas`() {
        val a = Caja(0f, 0f, 0.5f, 0.5f)
        assertEquals(1f, a.traslape(Caja(0.1f, 0.1f, 0.2f, 0.2f)), 1e-4f)
        assertEquals(0f, a.traslape(Caja(0.6f, 0.6f, 0.7f, 0.7f)), 1e-4f)
    }
}
