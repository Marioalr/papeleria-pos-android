package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que la búsqueda de Kotlin hace distinto de la del POS, buscado leyendo los dos lados función por función y comparando
 * con catálogos al azar. Lo esperado es lo que da el C# del POS (src/PapeleriaPos.Nucleo): sale de leer sus fuentes y se
 * confirmó corriéndolas tal cual (.NET 10 con la ICU 74 de Ubuntu, como pos/herramientas/oraculo).
 *
 * Las que empiezan con «difiere» fallan mientras siga la diferencia; las que empiezan con «igual» pasan y quedan para que
 * no se rompa lo que ya da lo mismo (casos raros que no están en las demás pruebas).
 */
class DiferenciasConElPosTest {
    /** Un catálogo de solo nombres, con los Id en el orden de la lista (van por fecha, como en el POS). */
    private fun armar(vararg nombres: String): IndiceDeBusqueda =
        IndiceDeBusqueda.armar(nombres.mapIndexed { i, n -> ProductoParaBuscar(BaseDePrueba.id(i), n, null, emptyList()) }, null, null)

    /** Lo que encuentra, por nombre, sin lo elegido ni lo vendido: si nada más los distingue, el orden es el del nombre. */
    private fun encuentra(texto: String, vararg nombres: String): List<String> = armar(*nombres).buscar(texto).map { it.articulo.descripcion }

    /** La clave de la medida de un nombre, como la lee el POS sin marcas. */
    private fun medida(nombre: String): String = Familias.clave(LectorDeNombres(listOf(nombre)).leer(nombre), EjeFamilia.MEDIDA)

    // ---------------------------------------------------------------------------------------------------------------------
    // Diferencias que siguen.

    /**
     * El POS ordena por la llave de ICU (IndiceDeBusqueda.cs:146 y :260), y ICU no le da peso a los caracteres de formato
     * ni a los selectores de variante: el BOM (U+FEFF, el que deja un CSV de Excel o un texto pegado), U+180E y U+FE0F
     * no cuentan, y «\uFEFFAgenda» es «Agenda». En Kotlin no están en la tabla (TablaDeOrden.RANGOS) y van al final de
     * todo (OrdenEspanol.kt:81-84), como si fueran la letra más alta.
     */
    @Test
    fun `difiere - un BOM o un selector de variante en el nombre no lo mueve en el orden`() {
        assertEquals(
            listOf(
                listOf("\uFEFFAgenda escolar 2027", "Cuaderno escolar", "Libreta escolar"),
                listOf("Cuaderno \u180EA", "Cuaderno B"),
                listOf("Cuaderno \uFE0FA", "Cuaderno B"),
            ),
            listOf(
                encuentra("escolar", "Libreta escolar", "\uFEFFAgenda escolar 2027", "Cuaderno escolar"),
                encuentra("cuaderno", "Cuaderno B", "Cuaderno \u180EA"),
                encuentra("cuaderno", "Cuaderno B", "Cuaderno \uFE0FA"),
            ),
        )
    }

    /**
     * El POS lee el número con `decimal.Parse` (Familias.cs:268-270), que guarda a lo más 28 o 29 cifras y redondea las
     * demás antes del "0.###": 1.00049999999999999999999999999 (29 decimales) queda en 1.0005 y sale «1.001»; un entero de
     * 29 cifras con .5 se redondea al entero. Kotlin redondea el número exacto a tres decimales (LectorDeNombres.kt:286-290).
     */
    @Test
    fun `difiere - una medida con mas cifras de las que caben en un decimal se redondea como en el POS`() {
        assertEquals(
            listOf("1.001MM", "12345678901234567890123456790CM"),
            listOf(medida("Lapicero 1.00049999999999999999999999999mm Negro"), medida("Regla 12345678901234567890123456789.5cm")),
        )
    }

    /**
     * ICU lee la ñ escrita como n + tilde aunque entre las dos vaya una marca de abajo (la contracción separada de la
     * intercalación de Unicode): «An\u0323\u0303o» es una «Año» con punto abajo y va después de «Anzo». Kotlin solo junta
     * la n con el carácter que le sigue (OrdenEspanol.kt:74-80) y la ordena como «Ano».
     */
    @Test
    fun `difiere - la n con una marca de abajo antes de la tilde se ordena como la ene`() {
        assertEquals(listOf("Anzo", "An\u0323\u0303o"), encuentra("an", "An\u0323\u0303o", "Anzo"))
    }

    /**
     * Fuera de la tabla también quedan letras latinas (U+0250 a U+02AF, U+1D00 a U+1DBF, U+2C60 a U+2C7F, U+A720 a
     * U+A7FF: la g de imprenta, la alfa latina, la a versalita) y signos (U+2500 a U+259F, U+FFFC). ICU las pone junto a
     * su letra y los signos antes de los números; Kotlin, al final de todo por su número (OrdenEspanol.kt:81-84).
     */
    @Test
    fun `difiere - las letras latinas y los signos que la tabla no tiene van donde los pone ICU`() {
        assertEquals(
            listOf(
                listOf("Cuaderno \u2500", "Cuaderno 1", "Cuaderno \u0261", "Cuaderno h"),
                listOf("Cuaderno \u1D00", "Cuaderno \u0251", "Cuaderno b"),
            ),
            listOf(
                encuentra("cuaderno", "Cuaderno h", "Cuaderno \u0261", "Cuaderno 1", "Cuaderno \u2500"),
                encuentra("cuaderno", "Cuaderno b", "Cuaderno \u0251", "Cuaderno \u1D00"),
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Lo que ya da lo mismo (pruebas de regresión).

    /** Lo que ICU ignora y la tabla sí tiene (el espacio de ancho cero, la unión de palabras, el guion suave) no cuenta. */
    @Test
    fun `igual - lo que ICU ignora y esta en la tabla no mueve el nombre`() {
        assertEquals(
            listOf("Cuaderno \u200BA", "Cuaderno B", "Cuaderno \u2060C", "Cuaderno \u00ADD"),
            encuentra("cuaderno", "Cuaderno B", "Cuaderno \u200BA", "Cuaderno \u2060C", "Cuaderno \u00ADD"),
        )
        // La ñ escrita con la tilde junto a la n, aunque después venga otra marca o la n ya la traiga, va después de «Anzo».
        assertEquals(
            listOf("Ano", "Anzo", "An\u0303\u0323o", "A\u1E47\u0303o"),
            encuentra("an", "An\u0303\u0323o", "A\u1E47\u0303o", "Anzo", "Ano"),
        )
    }

    /** El nombre con BOM se lee y se busca igual que en el POS: el BOM se queda pegado a la primera palabra. */
    @Test
    fun `igual - el nombre con BOM se lee y se encuentra por sus palabras`() {
        val nombre = "\uFEFFCuaderno Raya Azul"
        assertEquals(
            listOf(
                ParteDeNombre("\uFEFFCuaderno", null, "\uFEFFCUADERNO"),
                ParteDeNombre("Raya", null, "RAYA"), // no es cuaderno: «Raya» no es el rayado
                ParteDeNombre("Azul", EjeFamilia.COLOR, "AZUL"),
            ),
            LectorDeNombres(listOf(nombre)).leer(nombre),
        )
        assertEquals(
            listOf("\uFEFFAgenda escolar 2027"),
            encuentra("agenda", "\uFEFFAgenda escolar 2027", "Cuaderno escolar", "Libreta escolar"),
        )
    }

    /** Con 28 decimales el número cabe en el decimal del POS, y lo que redondea él a 28 lugares da lo mismo a tres. */
    @Test
    fun `igual - los decimales que caben o no llegan a la mitad se redondean igual`() {
        assertEquals(
            listOf("0MM", "0.001MM", "1MM"),
            listOf(
                medida("Lapicero 0.0004999999999999999999999999mm"),
                medida("Lapicero 0.00050000000000000000000000000001"),
                medida("Lapicero 0.99999999999999999999999999999mm"),
            ),
        )
    }

    /**
     * «0/0» es NaN y LINQ (Comparer<double>) lo pone antes que todo; 1/0 es infinito, después de los números. Así las
     * medidas de la familia van 0/0, 1/4, 1/2, 1/0, y la «XL», que no es medida, va sola.
     */
    @Test
    fun `igual - la medida 0 entre 0 va antes que las demas como en LINQ`() {
        assertTrue(LectorDeNombres.ordenDeMedida("0/0").isNaN())
        assertEquals(Double.POSITIVE_INFINITY, LectorDeNombres.ordenDeMedida("1/0"), 0.0)
        val encontrados = armar("Etiqueta 1/2 Azul", "Etiqueta 0/0 Azul", "Etiqueta 1/0 Azul", "Etiqueta 1/4 Azul", "Etiqueta XL Azul")
            .buscar("etiqueta")
        assertEquals(
            listOf("Etiqueta 0/0 Azul", "Etiqueta 1/0 Azul", "Etiqueta 1/2 Azul", "Etiqueta 1/4 Azul", "Etiqueta XL Azul"),
            encontrados.map { it.articulo.descripcion },
        )
        val agrupadas = Familias.agrupar(encontrados, { it.articulo.partes }, { 0 })
        assertEquals(EjeFamilia.MEDIDA, agrupadas.eje)
        assertEquals(
            listOf("Etiqueta Azul [0/0, 1/4, 1/2, 1/0] 0", "null [Etiqueta XL Azul] 0"),
            agrupadas.renglones.map { r ->
                "${r.titulo} ${r.valores.map { if (r.esFamilia) it.texto else it.elemento.articulo.descripcion }} ${r.masVendido}"
            },
        )
    }

    /**
     * El código exacto se compara como `OrdinalIgnoreCase`: la ı y la ſ no son la I ni la S, la µ es la mu, la ǆ es la ǅ,
     * el kelvin no es la k, la ß no es SS ni ẞ y la İ no es la i. Lo escrito también se normaliza (el kelvin es K, la İ es I).
     */
    @Test
    fun `igual - el codigo exacto sin importar mayusculas es el de OrdinalIgnoreCase`() {
        val casos = listOf(
            Triple("S1", "\u017F1", listOf("Dos \u017F1", "Uno")),
            Triple("I1", "\u01311", listOf("Dos \u01311")),
            Triple("\u039C1", "\u00B51", listOf("Uno", "Dos \u00B51")),
            Triple("\u01C51", "\u01C61", listOf("Uno", "Dos \u01C61")),
            Triple("K1", "\u212A1", listOf("Dos \u212A1", "Uno")),
            Triple("\u212A1", "k1", listOf("Dos k1", "Uno")),
            Triple("SS1", "\u00DF1", listOf("Dos \u00DF1")),
            Triple("\u1E9E1", "\u00DF1", listOf("Dos \u00DF1")),
            Triple("\u01301", "i1", listOf("Dos i1", "Uno")),
            Triple("\u0178", "\u00FF", listOf("Uno", "Dos \u00FF")),
        )
        for ((codigo, escrito, esperado) in casos) {
            val indice = IndiceDeBusqueda.armar(
                listOf(
                    ProductoParaBuscar(BaseDePrueba.id(0), "Uno", codigo, listOf(codigo)),
                    ProductoParaBuscar(BaseDePrueba.id(1), "Dos $escrito", null, emptyList()),
                ),
                null,
                null,
            )
            assertEquals("código $codigo, escrito $escrito", esperado, indice.buscar(escrito).map { it.articulo.descripcion })
        }
    }

    /** NEL (U+0085) es espacio para .NET y separa lo escrito; el separador U+001C no, y queda dentro de la palabra. */
    @Test
    fun `igual - lo que es espacio para dotnet separa lo escrito`() {
        val nombres = arrayOf("Lapicero azul", "Lapicero\u001Cazul", "Lapicero\u0085rojo")
        assertEquals(listOf("Lapicero azul", "Lapicero\u001Cazul"), encuentra("lapicero\u0085azul", *nombres))
        assertEquals(listOf("Lapicero\u0085rojo"), encuentra("rojo", *nombres))
        assertEquals(listOf("Lapicero\u001Cazul"), encuentra("lapicero\u001Cazul", *nombres))
    }
}
