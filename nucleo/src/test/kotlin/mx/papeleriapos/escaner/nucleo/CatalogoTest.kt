package mx.papeleriapos.escaner.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La búsqueda en el catálogo (docs/escaner.md, «Búsqueda en el catálogo»), con códigos ficticios. */
class CatalogoTest {
    private fun catalogo(vararg claves: String) = Catalogo.deLineas(claves.asSequence())

    private fun leido(texto: String, s: Simbologia) = Confirmador.normalizar(Lectura(texto, s, null))!!

    // ---- Construir desde líneas ----

    @Test
    fun `los codigos de ejemplo son validos`() {
        for (c in listOf(EAN13, UPC_A, EAN13_00, UPC_A_DE_E, EAN8, GTIN14, INTERNO, UPC_A_1, EAN13_01, EAN8_0)) {
            assertTrue(c, Gtin.esValido(c))
        }
        assertEquals(UPC_A_DE_E, Gtin.upcEaUpcA(UPC_E))
        assertFalse(Gtin.esValido(VERIFICADOR_MAL))
    }

    @Test
    fun `la primera columna con coma, punto y coma o tabulador, y el encabezado se salta`() {
        val c = Catalogo.deTexto(
            "codigo,descripcion\n" +
                "$EAN13,Cuaderno ficticio\n" +
                "$UPC_A;Plumón ficticio;12.50\n" +
                "$EAN8\tGoma ficticia\n" +
                "ZZ0001\n" +
                "ZZ-PRUEBA-0001,Clave larga, con coma en la descripción\n",
        )
        assertEquals(5, c.tamano)
        for (k in listOf(EAN13, UPC_A, EAN8, "ZZ0001", "ZZ-PRUEBA-0001")) assertTrue(k, k in c)
        assertFalse("codigo" in c)
    }

    @Test
    fun `sin comillas ni espacios alrededor, sin lineas vacias y con BOM y CRLF`() {
        val c = Catalogo.deTexto(
            "﻿Código;Descripción\r\n  \"$EAN13\"  ;x\r\n\r\n   \r\n\t\r\n \"ZZ0001\"\r\n,sin código\r\n",
        )
        assertTrue(EAN13 in c)
        assertTrue("ZZ0001" in c)
        assertEquals(2, c.tamano)
    }

    @Test
    fun `el encabezado es opcional y solo puede ser el primer renglon`() {
        for (encabezado in listOf("codigo", "Código de barras", "CLAVE\tdescripcion", "code;desc", "Barcode,x")) {
            assertEquals(encabezado, 1, Catalogo.deTexto("$encabezado\n$EAN13\n").tamano)
        }
        // Sin encabezado, el primer renglón es un código.
        val sin = Catalogo.deTexto("$EAN13,Cuaderno\n$UPC_A,Plumón\n")
        assertEquals(2, sin.tamano)
        assertTrue(EAN13 in sin)
        // Más abajo ya no es encabezado: se guarda como viene (no coincide con ninguna lectura).
        assertTrue("codigo" in Catalogo.deTexto("$EAN13\ncodigo\n"))
        // Con BOM también se reconoce.
        assertEquals(1, Catalogo.deTexto("﻿codigo,descripcion\n$EAN13\n").tamano)
    }

    @Test
    fun `un codigo repetido cuenta una vez y se guarda tal cual, aun con el verificador mal`() {
        val c = Catalogo.deTexto("$EAN13,a\n$EAN13;b\n $EAN13 \n$VERIFICADOR_MAL,verificador mal\n")
        assertEquals(2, c.tamano)
        assertTrue(VERIFICADOR_MAL in c)
        assertEquals(0, Catalogo.deTexto("").tamano)
        assertEquals(0, Catalogo.deTexto("codigo,descripcion\n\n").tamano)
        assertEquals(0, Catalogo.VACIO.tamano)
    }

    // ---- Cada paso de la regla ----

    @Test
    fun `tal cual en cada simbologia`() {
        val c = catalogo(EAN13, UPC_A, UPC_E, EAN8, GTIN14, INTERNO, "ZZ0001", "ZZ-PRUEBA-0001", "A-000123")
        assertEquals(Coincidencia(EAN13, Variante.TAL_CUAL), c.buscar(EAN13, Simbologia.EAN_13))
        assertEquals(Coincidencia(INTERNO, Variante.TAL_CUAL), c.buscar(INTERNO, Simbologia.EAN_13))
        assertEquals(Coincidencia(UPC_A, Variante.TAL_CUAL), c.buscar(UPC_A, Simbologia.UPC_A))
        assertEquals(Coincidencia(UPC_E, Variante.TAL_CUAL), c.buscar(UPC_E, Simbologia.UPC_E))
        assertEquals(Coincidencia(EAN8, Variante.TAL_CUAL), c.buscar(EAN8, Simbologia.EAN_8))
        assertEquals(Coincidencia(GTIN14, Variante.TAL_CUAL), c.buscar(GTIN14, Simbologia.ITF))
        assertEquals(Coincidencia("ZZ0001", Variante.TAL_CUAL), c.buscar("ZZ0001", Simbologia.CODE_128))
        assertEquals(Coincidencia("ZZ-PRUEBA-0001", Variante.TAL_CUAL), c.buscar("ZZ-PRUEBA-0001", Simbologia.CODE_128))
        assertEquals(Coincidencia("A-000123", Variante.TAL_CUAL), c.buscar("A-000123", Simbologia.QR))
        assertNull(c.buscar("ZZ-000001", Simbologia.QR))
    }

    @Test
    fun `UPC-A de 12 que empieza con 0, sin ese 0`() {
        val once = UPC_A.substring(1)
        assertEquals(Coincidencia(once, Variante.UPC_A_11), catalogo(once).buscar(UPC_A, Simbologia.UPC_A))
    }

    @Test
    fun `EAN-13 que empieza con 0, como UPC-A de 12 y luego de 11`() {
        val doce = EAN13_00.substring(1)
        val once = EAN13_00.substring(2)
        assertEquals(Coincidencia(doce, Variante.EAN13_A_UPC_A), catalogo(doce).buscar(EAN13_00, Simbologia.EAN_13))
        assertEquals(Coincidencia(once, Variante.EAN13_A_UPC_A_11), catalogo(once).buscar(EAN13_00, Simbologia.EAN_13))
        // «01…»: el UPC-A de 12 empieza con 1, así que no hay de 11.
        val doce01 = EAN13_01.substring(1)
        assertEquals(Coincidencia(doce01, Variante.EAN13_A_UPC_A), catalogo(doce01).buscar(EAN13_01, Simbologia.EAN_13))
    }

    @Test
    fun `UPC-E expandido a UPC-A de 12`() {
        assertEquals(Coincidencia(UPC_A_DE_E, Variante.UPC_E_A_UPC_A), catalogo(UPC_A_DE_E).buscar(UPC_E, Simbologia.UPC_E))
    }

    // ---- El orden: si dos variantes están en el catálogo, gana la primera ----

    @Test
    fun `gana la primera variante que esta en el catalogo`() {
        val u = catalogo(UPC_A, UPC_A.substring(1))
        assertEquals(Variante.TAL_CUAL, u.buscar(UPC_A, Simbologia.UPC_A)!!.variante)

        val todas = catalogo(EAN13_00, EAN13_00.substring(1), EAN13_00.substring(2))
        assertEquals(Coincidencia(EAN13_00, Variante.TAL_CUAL), todas.buscar(EAN13_00, Simbologia.EAN_13))
        val doceYOnce = catalogo(EAN13_00.substring(1), EAN13_00.substring(2))
        assertEquals(Coincidencia(EAN13_00.substring(1), Variante.EAN13_A_UPC_A), doceYOnce.buscar(EAN13_00, Simbologia.EAN_13))

        val e = catalogo(UPC_E, UPC_A_DE_E)
        assertEquals(Coincidencia(UPC_E, Variante.TAL_CUAL), e.buscar(UPC_E, Simbologia.UPC_E))
    }

    // ---- Ninguna otra corrección ----

    @Test
    fun `el UPC-A no se completa a 13 ni se le quita otro digito`() {
        assertNull(catalogo("0$UPC_A").buscar(UPC_A, Simbologia.UPC_A))
        // Un UPC-A que empieza con 1 no tiene forma de 11.
        assertNull(catalogo(UPC_A_1.substring(1), "0$UPC_A_1").buscar(UPC_A_1, Simbologia.UPC_A))
        // Ni se quitan dos ceros.
        assertNull(catalogo(UPC_A.substring(2)).buscar(UPC_A, Simbologia.UPC_A))
    }

    @Test
    fun `el EAN-13 que no empieza con 0 se busca solo tal cual`() {
        val c = catalogo(EAN13.substring(1), EAN13.substring(2), "0$EAN13", EAN13.dropLast(1))
        assertNull(c.buscar(EAN13, Simbologia.EAN_13))
        assertNull(catalogo(INTERNO.substring(1)).buscar(INTERNO, Simbologia.EAN_13))
        // «01…» no llega a 11.
        assertNull(catalogo(EAN13_01.substring(2)).buscar(EAN13_01, Simbologia.EAN_13))
    }

    @Test
    fun `el UPC-E no se busca con 13 ni con 11`() {
        assertNull(catalogo("0$UPC_A_DE_E", UPC_A_DE_E.substring(1), UPC_E.substring(1)).buscar(UPC_E, Simbologia.UPC_E))
    }

    @Test
    fun `no se quitan ni se agregan ceros a EAN-8, ITF, Code 128 ni QR`() {
        assertNull(catalogo("00000$EAN8", "000000$EAN8").buscar(EAN8, Simbologia.EAN_8))
        assertNull(catalogo(EAN8_0.substring(1)).buscar(EAN8_0, Simbologia.EAN_8))
        // El GTIN-14 que empieza con 0 trae dentro su EAN-13, pero no se busca con él.
        val gtin14Cero = "0$EAN13"
        assertTrue(Gtin.esValido(gtin14Cero))
        assertNull(catalogo(EAN13).buscar(gtin14Cero, Simbologia.ITF))
        assertNull(catalogo(GTIN14.substring(1)).buscar(GTIN14, Simbologia.ITF))
        assertNull(catalogo("123").buscar("0123", Simbologia.CODE_128))
        assertNull(catalogo("ZZ0001").buscar("zz0001", Simbologia.CODE_128))
        assertNull(catalogo("ZZ0001").buscar("ZZ-0001", Simbologia.CODE_128))
        assertNull(catalogo("A-000123").buscar("A-123", Simbologia.QR))
    }

    @Test
    fun `no se adivinan ni completan digitos ni se busca una lectura sin verificar`() {
        val c = catalogo(UPC_A, EAN13, VERIFICADOR_MAL, "ZZ0001")
        // Una lectura corta no se completa.
        assertNull(c.buscar(UPC_A.substring(1), Simbologia.UPC_A))
        assertNull(c.buscar(EAN13.dropLast(1), Simbologia.EAN_13))
        // Con el largo de otra simbología, no.
        assertNull(c.buscar(UPC_A, Simbologia.EAN_13))
        assertNull(c.buscar("0$UPC_A", Simbologia.UPC_A))
        // Con el verificador mal no se busca, aunque esté igual en el catálogo.
        assertNull(c.buscar(VERIFICADOR_MAL, Simbologia.EAN_13))
        val upcMal = UPC_A.dropLast(1) + ((UPC_A.last() - '0' + 1) % 10)
        assertNull(catalogo(upcMal).buscar(upcMal, Simbologia.UPC_A))
        assertNull(catalogo("09999458").buscar("09999458", Simbologia.UPC_E))
        // Las simbologías que la app no acepta no se buscan.
        assertNull(c.buscar("ZZ0001", Simbologia.CODE_39))
        assertNull(c.buscar("ZZ0001", Simbologia.CODABAR))
    }

    // ---- El catálogo con un verificador mal ----

    @Test
    fun `un codigo del catalogo con el verificador mal nunca coincide con una lectura valida`() {
        val correcto = VERIFICADOR_MAL.dropLast(1) + Gtin.digitoVerificador(VERIFICADOR_MAL.dropLast(1))
        assertNull(catalogo(VERIFICADOR_MAL).buscar(correcto, Simbologia.EAN_13))
        // Lo mismo con las formas de 12 y de 11 de un UPC-A y con la expansión de un UPC-E.
        for (texto in listOf(UPC_A, EAN13_00, UPC_E)) {
            val s = when (texto.length) { 12 -> Simbologia.UPC_A; 13 -> Simbologia.EAN_13; else -> Simbologia.UPC_E }
            val malas = formas(texto, s).flatMap { conVerificadorMal(it) }
            assertNull(texto, catalogo(*malas.toTypedArray()).buscar(texto, s))
        }
    }

    @Test
    fun `con cualquier UPC-A ficticio, las claves con el verificador mal no coinciden y las buenas si`() {
        for (n in 0 until 300) {
            val ean13 = Gtin.completar("00999995" + n.toString().padStart(4, '0'))
            val upcA = ean13.substring(1)
            val buenas = listOf(ean13, upcA, upcA.substring(1))
            val malas = catalogo(*buenas.flatMap { conVerificadorMal(it) }.toTypedArray())
            assertNull(malas.buscar(ean13, Simbologia.EAN_13))
            assertNull(malas.buscar(upcA, Simbologia.UPC_A))
            assertEquals(Variante.UPC_A_11, catalogo(buenas[2]).buscar(upcA, Simbologia.UPC_A)!!.variante)
            assertEquals(Variante.EAN13_A_UPC_A_11, catalogo(buenas[2]).buscar(ean13, Simbologia.EAN_13)!!.variante)
        }
    }

    // ---- Lo leído: igual venga de ML Kit o de zxing-cpp ----

    @Test
    fun `el UPC-A se busca como UPC-A de 12 venga como venga`() {
        val formas = listOf(
            leido(UPC_A, Simbologia.UPC_A), // ML Kit
            leido("0$UPC_A", Simbologia.UPC_A), // zxing-cpp
            leido("0$UPC_A", Simbologia.EAN_13), // un EAN-13 que empieza con 0: las mismas barras
        )
        val catalogos = listOf(catalogo(UPC_A), catalogo(UPC_A.substring(1)), catalogo("0$UPC_A"), catalogo(UPC_A, UPC_A.substring(1)))
        for (c in catalogos) assertEquals(1, formas.map { c.buscar(it) }.toSet().size)
        assertEquals(Coincidencia(UPC_A, Variante.TAL_CUAL), catalogo(UPC_A).buscar(formas[1]))
        assertEquals(Coincidencia(UPC_A.substring(1), Variante.UPC_A_11), catalogo(UPC_A.substring(1)).buscar(formas[2]))
        // El de 13 dígitos no se prueba: el UPC-A tiene 12.
        assertNull(catalogo("0$UPC_A").buscar(formas[0]))
    }

    @Test
    fun `el EAN-13 que empieza con 00 se busca como UPC-A y llega igual a la clave de 11`() {
        val c = catalogo(EAN13_00.substring(2))
        val esperado = Coincidencia(EAN13_00.substring(2), Variante.UPC_A_11)
        assertEquals(esperado, c.buscar(leido(EAN13_00, Simbologia.EAN_13)))
        assertEquals(esperado, c.buscar(leido(EAN13_00.substring(1), Simbologia.UPC_A)))
        // La regla con el texto de 13, como lo mandaría un lector configurado así: la misma clave, por el paso 3.
        assertEquals(Coincidencia(EAN13_00.substring(2), Variante.EAN13_A_UPC_A_11), c.buscar(EAN13_00, Simbologia.EAN_13))
    }

    @Test
    fun `el UPC-E se busca con sus 8 digitos venga como venga`() {
        val mlKit = leido(UPC_E, Simbologia.UPC_E)
        val zxing = leido("0$UPC_A_DE_E", Simbologia.UPC_E)
        for (c in listOf(catalogo(UPC_E), catalogo(UPC_A_DE_E), catalogo(UPC_E, UPC_A_DE_E), catalogo("0$UPC_A_DE_E"))) {
            assertEquals(c.buscar(mlKit), c.buscar(zxing))
        }
        assertEquals(Coincidencia(UPC_E, Variante.TAL_CUAL), catalogo(UPC_E).buscar(zxing))
        assertEquals(Coincidencia(UPC_A_DE_E, Variante.UPC_E_A_UPC_A), catalogo(UPC_A_DE_E).buscar(zxing))
        assertNull(catalogo("0$UPC_A_DE_E").buscar(mlKit))
    }

    @Test
    fun `lo demas se busca con su forma normalizada`() {
        val c = catalogo(EAN13, EAN8, GTIN14, "ZZ0001", "0107509994000117")
        assertEquals(Coincidencia(EAN13, Variante.TAL_CUAL), c.buscar(leido(EAN13, Simbologia.EAN_13)))
        assertEquals(Coincidencia(EAN8, Variante.TAL_CUAL), c.buscar(leido(EAN8, Simbologia.EAN_8)))
        assertEquals(Coincidencia(GTIN14, Variante.TAL_CUAL), c.buscar(leido(GTIN14, Simbologia.ITF)))
        // Code 128 sin el prefijo de simbología ni los caracteres de control.
        assertEquals(Coincidencia("ZZ0001", Variante.TAL_CUAL), c.buscar(leido("ZZ\u001D0001", Simbologia.CODE_128)))
        assertEquals("0107509994000117", c.buscar(leido("]C10107509994000117", Simbologia.CODE_128))!!.clave)
    }

    @Test
    fun `la forma de busqueda sale del normalizado y su simbologia`() {
        assertEquals(UPC_A to Simbologia.UPC_A, Catalogo.formaDeBusqueda("0$UPC_A", Simbologia.UPC_A))
        assertEquals(UPC_A to Simbologia.UPC_A, Catalogo.formaDeBusqueda("0$UPC_A", Simbologia.EAN_13))
        assertEquals(EAN13 to Simbologia.EAN_13, Catalogo.formaDeBusqueda(EAN13, Simbologia.EAN_13))
        assertEquals(UPC_E to Simbologia.UPC_E, Catalogo.formaDeBusqueda("0$UPC_A_DE_E", Simbologia.UPC_E))
        assertEquals(EAN8 to Simbologia.EAN_8, Catalogo.formaDeBusqueda(EAN8, Simbologia.EAN_8))
        assertEquals("A-000123" to Simbologia.QR, Catalogo.formaDeBusqueda("A-000123", Simbologia.QR))
        assertNull(Catalogo.formaDeBusqueda(UPC_A, Simbologia.UPC_A))
        // Lo que guardan las listas (normalizado y simbología) da lo mismo que la lectura.
        val c = catalogo(UPC_A.substring(1))
        val l = leido(UPC_A, Simbologia.UPC_A)
        assertEquals(c.buscar(l), c.buscarNormalizado(l.normalizado, l.simbologia))
    }

    /** Las formas que prueba la regla para [texto] (sin la de tal cual repetida). */
    private fun formas(texto: String, s: Simbologia): List<String> = when (s) {
        Simbologia.UPC_A -> listOf(texto, texto.substring(1))
        Simbologia.EAN_13 -> listOf(texto, texto.substring(1), texto.substring(2))
        else -> listOf(texto, Gtin.upcEaUpcA(texto)!!)
    }

    /** [codigo] con cada uno de los otros nueve dígitos verificadores. */
    private fun conVerificadorMal(codigo: String): List<String> =
        ('0'..'9').filter { it != codigo.last() }.map { codigo.dropLast(1) + it }

    private companion object {
        // Ficticios, con el verificador calculado con el algoritmo GS1 (lo revisa la primera prueba).
        const val EAN13 = "7509994000117"
        const val INTERNO = "2999990000016"
        const val UPC_A = "099999400014"
        const val UPC_A_1 = "199999400042"
        const val EAN13_00 = "0099999400038"
        const val EAN13_01 = "0199999400059"
        const val UPC_E = "09999459"
        const val UPC_A_DE_E = "099994000059"
        const val EAN8 = "75099947"
        const val EAN8_0 = "07509995"
        const val GTIN14 = "17509994000114"
        const val VERIFICADOR_MAL = "7509994000996"
    }
}
