package mx.papeleriapos.android.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Locale

class CsvTest {
    @Test
    fun `un campo simple va tal cual`() {
        assertEquals("7509993456786", Csv.campo("7509993456786"))
        assertEquals("", Csv.campo(""))
    }

    @Test
    fun `comas, comillas y saltos de linea van entre comillas con las comillas dobladas`() {
        assertEquals("\"a,b\"", Csv.campo("a,b"))
        assertEquals("\"dijo \"\"hola\"\"\"", Csv.campo("dijo \"hola\""))
        assertEquals("\"uno\ndos\"", Csv.campo("uno\ndos"))
        assertEquals("\"uno\r\ndos\"", Csv.campo("uno\r\ndos"))
        // Los espacios en las orillas se pierden en algunas hojas de cálculo si no van entre comillas.
        assertEquals("\" Lupita \"", Csv.campo(" Lupita "))
    }

    @Test
    fun `un renglon termina en CRLF`() {
        val sb = StringBuilder()
        Csv.renglon(listOf("a", "b,c", ""), sb)
        assertEquals("a,\"b,c\",\r\n", sb.toString())
    }

    @Test
    fun `leer regresa lo mismo que se escribio`() {
        val renglones = listOf(
            listOf("sesion", "valor", "nota"),
            listOf("Moto G24, Lupita", "ZZP\"0042\"x", "uno\r\ndos"),
            listOf("", "", ""),
            listOf("ñandú", "=1+1", "último"),
        )
        val sb = StringBuilder(Csv.BOM)
        for (r in renglones) Csv.renglon(r, sb)
        assertEquals(renglones, Csv.leer(sb.toString()))
    }

    @Test
    fun `leer un texto vacio no da renglones`() {
        assertEquals(emptyList<List<String>>(), Csv.leer(""))
        assertEquals(emptyList<List<String>>(), Csv.leer(Csv.BOM))
    }

    @Test
    fun `lo que parece formula se neutraliza y se recupera`() {
        for (peligroso in listOf("=HYPERLINK(\"x\")", "+52", "-1", "@SUMA(A1)")) {
            val seguro = Csv.textoSeguro(peligroso)
            assertEquals("'$peligroso", seguro)
            assertEquals(peligroso, Csv.textoOriginal(seguro))
        }
        assertEquals("7509993456786", Csv.textoSeguro("7509993456786"))
        assertEquals("'hola", Csv.textoOriginal("'hola"))
    }

    @Test
    fun `los decimales llevan punto aunque el celular este en un idioma con coma`() {
        val antes = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"))
            assertEquals("2.50", Csv.decimal(2.5f))
            assertEquals("61.3", Csv.decimal(61.27, 1))
            assertFalse(Csv.decimal(1234.5f).contains(','))
        } finally {
            Locale.setDefault(antes)
        }
    }

    @Test
    fun `ocultar deja un hash corto y los ultimos 4`() {
        val qr = "A-000123"
        val oculto = Csv.ocultar(qr)
        val esperado = MessageDigest.getInstance("SHA-256").digest(qr.toByteArray()).take(6)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        assertEquals("sha256:$esperado...0123", oculto)
        assertFalse(oculto.contains("A-000123"))
        assertTrue(Csv.ocultar("abc").endsWith("...abc"))
        assertEquals("", Csv.ocultar(""))
    }
}
