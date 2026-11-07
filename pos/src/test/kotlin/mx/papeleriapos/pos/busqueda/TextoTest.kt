package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Test

/** El texto como lo compara el POS (TextoTests del POS, con los mismos casos) y lo que hace .NET con algunos caracteres. */
class TextoTest {
    @Test
    fun `normalizar ignora mayusculas acentos y espacios`() {
        for ((texto, esperado) in listOf(
            "Bic" to "BIC",
            "  pelikán  " to "PELIKAN",
            "Baco   Ñandú" to "BACO ÑANDU",
            // Los acentos sueltos que quedan en un nombre (2026-10-24): sin ellos, y sin dejar dos espacios.
            "Lapiz¨ Azul" to "LAPIZ AZUL",
            "Mu´ñeca  ´ grande" to "MUÑECA GRANDE",
            "Cinta`  Canela^" to "CINTA CANELA",
        )) {
            assertEquals(texto, esperado, Texto.normalizar(texto))
        }
    }

    /**
     * En el POS la marca guarda su nombre recortado y normalizado («La_marca_guarda_su_nombre_normalizado»). Aquí no hay
     * marcas guardadas: llegan con la copia como texto y la búsqueda las normaliza al leer los nombres.
     */
    @Test
    fun `la marca guarda su nombre normalizado`() {
        assertEquals("PELIKAN", Texto.normalizar(" Pelikán "))
        val partes = LectorDeNombres(emptyList(), listOf(" Pelikán ")).leer("Lápiz adhesivo Pelikan")
        assertEquals(ParteDeNombre("Pelikan", EjeFamilia.MARCA, "PELIKAN"), partes.last())
    }

    /**
     * Lo que hace .NET (el de la tienda) con algunos caracteres, tal como lo dio el oráculo (pos/herramientas/oraculo, que lo
     * revisa con todos los del plano básico): qué es un espacio, la ı sin punto que se queda, la µ y la ſ que cambian, la
     * tilde suelta y lo que no se ve.
     */
    @Test
    fun `normaliza cada caracter como dotnet`() {
        for ((texto, esperado) in listOf(
            "Pelota\u0085Roja" to "PELOTA ROJA", // NEL es espacio para .NET y no para Kotlin
            "Pelota\u00a0Roja\u3000Grande" to "PELOTA ROJA GRANDE",
            "A\u001cB" to "A\u001cB", // los separadores de información no son espacio para .NET (para Kotlin sí)
            "Ra\u200bya" to "RA\u200bYA", // el espacio de ancho cero no es espacio
            "\u0131lg\u0131n" to "\u0131LG\u0131N", // la ı sin punto se queda, como en Windows
            "\u0130zmir" to "IZMIR",
            "5\u00b5m" to "5\u039cM", // la µ es la mu griega mayúscula
            "\u017fello" to "SELLO",
            "Nan\u0303du\u0301" to "NANDU", // la tilde suelta no hace ñ: es una marca y se quita
            "\u00ff \u2126 \u212a" to "Y \u03a9 K", // la ÿ sin diéresis; el ohm y el kelvin son la omega y la K
            "Stra\u00dfe" to "STRA\u00dfE",
            "\u0009 Lápiz \u000d\u000a" to "LAPIZ",
        )) {
            assertEquals(texto, esperado, Texto.normalizar(texto))
        }
    }
}
