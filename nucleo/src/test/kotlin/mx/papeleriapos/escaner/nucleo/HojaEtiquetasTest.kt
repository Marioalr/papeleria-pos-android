package mx.papeleriapos.escaner.nucleo

import mx.papeleriapos.escaner.nucleo.HojaEtiquetas.ALTO_PAGINA
import mx.papeleriapos.escaner.nucleo.HojaEtiquetas.ANCHO_PAGINA
import mx.papeleriapos.escaner.nucleo.HojaEtiquetas.Etiqueta
import mx.papeleriapos.escaner.nucleo.HojaEtiquetas.MARGEN
import mx.papeleriapos.escaner.nucleo.HojaEtiquetas.Rectangulo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * La hoja de etiquetas se revisa en lo que escribe, no en el modelo: el PDF (leído por su tabla xref) y el SVG se
 * vuelven a convertir en módulos y se comparan con el codificador, con 0.001 mm de tolerancia.
 */
class HojaEtiquetasTest {
    private val hoja = HojaEtiquetas.generar()
    private val pdf = Pdf(HojaEtiquetas.pdf(hoja))
    private val svg = hoja.paginas.map { rectangulosSvg(HojaEtiquetas.svg(it)) }

    @Test
    fun `cada etiqueta del PDF y del SVG reproduce los modulos del codificador`() {
        for ((formato, paginas) in listOf("PDF" to pdf.paginas.map { it.rectangulos }, "SVG" to svg)) {
            for (e in hoja.etiquetas) {
                val barras = barrasDe(e, paginas[e.pagina - 1])
                val esperado = unosYCeros(Barras.para(e.simbologia, e.codigo))
                assertEquals("$formato, ${e.codigo}", esperado, modulosLeidos(e, barras))
                for (b in barras) {
                    assertEquals("$formato, ${e.codigo}: arriba", e.y, b.y, TOLERANCIA)
                    assertEquals("$formato, ${e.codigo}: alto", e.alto, b.alto, TOLERANCIA)
                }
            }
        }
    }

    @Test
    fun `fuera de las etiquetas solo esta la regla`() {
        for ((i, pagina) in pdf.paginas.withIndex()) {
            val tramos = hoja.etiquetas.filter { it.pagina == i + 1 }.sumOf { tramosNegros(it.modulos) }
            // La regla: la línea y 11 marcas.
            assertEquals(tramos + 12, pagina.rectangulos.size)
            assertEquals(pagina.rectangulos.size, svg[i].size)
        }
    }

    @Test
    fun `las zonas en blanco miden lo de la norma y nada las invade`() {
        // Los patrones del codificador no las traen: empiezan y terminan con barra.
        for (e in hoja.etiquetas) assertTrue(e.codigo, e.modulos.first() && e.modulos.last())
        for (e in hoja.etiquetas) {
            val norma = when (e.simbologia) {
                Simbologia.EAN_13 -> 11 to 7
                Simbologia.EAN_8 -> 7 to 7
                else -> 10 to 10
            }
            assertEquals(e.codigo, norma, e.zonaIzquierda to e.zonaDerecha)
            val pagina = pdf.paginas[e.pagina - 1]
            val propias = barrasDe(e, pagina.rectangulos)
            val inicio = propias.minOf { it.x }
            val fin = propias.maxOf { it.x + it.ancho }
            assertEquals(e.codigo, e.xBarras, inicio, TOLERANCIA)
            assertEquals(e.codigo, e.xBarras + e.modulos.size * e.modulo, fin, TOLERANCIA)
            // Lo más cercano a cada lado a la altura de las barras (otras barras, la regla o letras) queda fuera de la zona.
            val otros = (pagina.rectangulos - propias.toSet()) + pagina.textos.map { it.caja }
            for (r in otros) {
                if (r.y >= e.y + e.alto || r.y + r.alto <= e.y) continue
                when {
                    r.x + r.ancho <= inicio -> assertTrue(
                        "${e.codigo}: algo a ${inicio - r.x - r.ancho} mm por la izquierda",
                        inicio - (r.x + r.ancho) >= e.zonaIzquierda * e.modulo - TOLERANCIA,
                    )
                    r.x >= fin -> assertTrue(
                        "${e.codigo}: algo a ${r.x - fin} mm por la derecha",
                        r.x - fin >= e.zonaDerecha * e.modulo - TOLERANCIA,
                    )
                    else -> fail("${e.codigo}: algo encima de las barras: $r")
                }
            }
        }
    }

    @Test
    fun `los codigos son validos, distintos y dicen que etiqueta son`() {
        val codigos = hoja.etiquetas.map { it.codigo }
        assertEquals(codigos.size, codigos.toSet().size)
        assertEquals(4 * 3 * 3 + 4, codigos.size)
        for ((i, e) in hoja.etiquetas.withIndex()) {
            val m = (e.modulo * 100).roundToInt()
            val a = e.alto.roundToInt()
            val n = i + 1
            when (e.simbologia) {
                Simbologia.EAN_13 -> {
                    assertTrue(e.codigo, Gtin.esValido(e.codigo))
                    assertEquals("750999%02d%02d%02d".format(m, a, n), e.codigo.dropLast(1))
                }
                Simbologia.EAN_8 -> {
                    assertTrue(e.codigo, Gtin.esValido(e.codigo))
                    assertEquals("750999${m / 10}", e.codigo.dropLast(1))
                }
                else -> assertTrue(
                    e.codigo,
                    e.codigo == "ZZ%02d%02d".format(m, a) || e.codigo == "ZZ-M%03d-A%02d-%03d".format(m, a, n),
                )
            }
        }
        // Por cada módulo y alto: un EAN-13, una clave corta (hasta 6) y una larga; y un EAN-8 por módulo.
        for (m in HojaEtiquetas.MODULOS) {
            for (a in HojaEtiquetas.ALTOS) {
                val grupo = hoja.etiquetas.filter { it.modulo == m && it.alto == a && it.simbologia != Simbologia.EAN_8 }
                assertEquals(listOf(13, 6, 15), grupo.map { it.codigo.length })
            }
            assertEquals(1, hoja.etiquetas.count { it.modulo == m && it.simbologia == Simbologia.EAN_8 })
        }
    }

    @Test
    fun `debajo de cada codigo van el codigo, el modulo y el alto`() {
        assertEquals("módulo 0.40 mm · alto 6 mm", HojaEtiquetas.descripcion(0.40, 6.0))
        for (e in hoja.etiquetas) {
            val medidas = "módulo ${HojaEtiquetas.milimetros(e.modulo)} mm · alto ${e.alto.roundToInt()} mm"
            for (esperado in listOf(e.codigo, medidas)) {
                val debajo = pdf.paginas[e.pagina - 1].textos.count {
                    it.texto == esperado && it.base > e.y + e.alto && it.base < e.y + e.alto + 8 &&
                        abs(it.centro - (e.x + e.ancho / 2)) < 0.01
                }
                assertEquals("${e.codigo}: «$esperado»", 1, debajo)
            }
        }
    }

    @Test
    fun `cada pagina trae la regla de 100 mm y la leyenda`() {
        for (pagina in pdf.paginas) {
            assertTrue(pagina.textos.any { it.texto == "Imprimir al 100 % (tamaño real). Esta regla debe medir 100 mm" })
            val regla = pagina.rectangulos.filter { r -> hoja.etiquetas.none { barrasDe(it, listOf(r)).isNotEmpty() } }
            val x0 = regla.minOf { it.x }
            assertEquals(100.0, regla.maxOf { it.x + it.ancho } - x0, TOLERANCIA)
            val marcas = regla.filter { it.alto > 1.0 }.sortedBy { it.x }
            assertEquals(11, marcas.size)
            for ((k, marca) in marcas.withIndex()) {
                // Las de las puntas, por dentro; las demás, centradas.
                val donde = when (k) {
                    0 -> marca.x
                    10 -> marca.x + marca.ancho
                    else -> marca.x + marca.ancho / 2
                }
                assertEquals(10.0 * k, donde - x0, TOLERANCIA)
            }
        }
    }

    @Test
    fun `nada sale de los margenes de 10 mm`() {
        for (pagina in pdf.paginas) {
            assertEquals("[0 0 612 792]", pagina.caja)
            for (r in pagina.rectangulos + pagina.textos.map { it.caja }) {
                assertTrue("$r", r.x >= MARGEN - 1e-6 && r.y >= MARGEN - 1e-6)
                assertTrue("$r", r.x + r.ancho <= ANCHO_PAGINA - MARGEN + 1e-6)
                assertTrue("$r", r.y + r.alto <= ALTO_PAGINA - MARGEN + 1e-6)
            }
        }
        // Tampoco las zonas en blanco.
        for (e in hoja.etiquetas) assertTrue(e.codigo, e.x >= MARGEN && e.x + e.ancho <= ANCHO_PAGINA - MARGEN)
    }

    @Test
    fun `el PDF es valido`() {
        // La tabla xref, startxref, los objetos y el /Length de cada contenido ya se revisaron al leerlo.
        assertTrue(pdf.texto.startsWith("%PDF-1.4\n"))
        assertTrue(pdf.texto.endsWith("%%EOF\n"))
        assertEquals(4 + 2 * hoja.paginas.size, pdf.objetos.size)
        assertTrue(pdf.objeto(1).contains("/Type /Catalog"))
        assertTrue(pdf.objeto(2).contains("/Count ${hoja.paginas.size}"))
        assertTrue(pdf.objeto(3).contains("/BaseFont /Helvetica /Encoding /WinAnsiEncoding"))
        assertEquals(hoja.paginas.size, pdf.paginas.size)
    }

    @Test
    fun `el CSV trae una fila por etiqueta, con el codigo primero`() {
        val lineas = HojaEtiquetas.csv(hoja).trimEnd('\n').split('\n')
        assertEquals("codigo,simbologia,modulo_mm,alto_mm,pagina", lineas[0])
        assertEquals(hoja.etiquetas.size, lineas.size - 1)
        for ((e, linea) in hoja.etiquetas.zip(lineas.drop(1))) {
            val c = linea.split(',')
            assertEquals(listOf(e.codigo, e.simbologia.name), c.take(2))
            assertEquals(e.modulo, c[2].toDouble(), 0.0)
            assertEquals(e.alto, c[3].toDouble(), 0.0)
            // La página es donde está su código en el PDF.
            assertTrue(linea, pdf.paginas[c[4].toInt() - 1].textos.any { it.texto == e.codigo })
        }
    }

    /** Los rectángulos que caen en la franja de las barras de [e], de izquierda a derecha. */
    private fun barrasDe(e: Etiqueta, rectangulos: List<Rectangulo>): List<Rectangulo> = rectangulos.filter {
        abs(it.y - e.y) < TOLERANCIA && abs(it.alto - e.alto) < TOLERANCIA &&
            it.x >= e.x - TOLERANCIA && it.x + it.ancho <= e.x + e.ancho + TOLERANCIA
    }.sortedBy { it.x }

    /**
     * Los módulos que dibujan [barras], desde el inicio de las barras de [e]: cada borde cae en un número entero de
     * módulos (± 0.001 mm) y entre dos rectángulos hay al menos un módulo blanco (los negros seguidos van en uno solo).
     */
    private fun modulosLeidos(e: Etiqueta, barras: List<Rectangulo>): String {
        val s = StringBuilder()
        var posicion = 0
        for ((i, b) in barras.withIndex()) {
            val blancos = enModulos(b.x - e.xBarras, e.modulo) - posicion
            assertTrue("${e.codigo}: barras pegadas", blancos >= if (i == 0) 0 else 1)
            val negros = enModulos(b.ancho, e.modulo)
            assertTrue(negros >= 1)
            assertEquals("${e.codigo}: borde derecho", posicion + blancos + negros, enModulos(b.x + b.ancho - e.xBarras, e.modulo))
            repeat(blancos) { s.append('0') }
            repeat(negros) { s.append('1') }
            posicion += blancos + negros
        }
        return s.toString()
    }

    private fun enModulos(mm: Double, modulo: Double): Int {
        val k = (mm / modulo).roundToInt()
        assertTrue("$mm mm no son módulos enteros de $modulo mm", abs(mm - k * modulo) < TOLERANCIA)
        return k
    }

    private fun tramosNegros(m: BooleanArray) = m.indices.count { m[it] && (it == 0 || !m[it - 1]) }

    private fun unosYCeros(m: BooleanArray) = m.joinToString("") { if (it) "1" else "0" }

    /** Un texto leído del PDF; su caja va de lo más alto de Helvetica (0.931 em) a lo más bajo (0.225 em). */
    private class TextoPdf(val izquierda: Double, val base: Double, val puntos: Double, val texto: String) {
        val ancho = HojaEtiquetas.anchoTexto(texto, puntos)
        val centro = izquierda + ancho / 2
        val caja: Rectangulo
            get() {
                val em = puntos * 25.4 / 72
                return Rectangulo(izquierda, base - 0.931 * em, ancho, 1.156 * em)
            }
    }

    private class PaginaPdf(val caja: String, val rectangulos: List<Rectangulo>, val textos: List<TextoPdf>)

    /** Lo justo de un lector de PDF para esta hoja: la tabla xref, los objetos y el contenido de cada página. */
    private class Pdf(bytes: ByteArray) {
        val texto = String(bytes, Charsets.ISO_8859_1)
        val objetos: Map<Int, Int>
        val paginas: List<PaginaPdf>

        init {
            val final = Regex("startxref\n(\\d+)\n%%EOF\n$").find(texto) ?: throw AssertionError("Sin startxref ni %%EOF.")
            val xref = final.groupValues[1].toInt()
            val cabeza = Regex("xref\n0 (\\d+)\n").find(texto, xref)
            assertTrue("startxref no apunta a la tabla", cabeza != null && cabeza.range.first == xref)
            val n = cabeza!!.groupValues[1].toInt()
            var p = cabeza.range.last + 1
            val desplazamientos = HashMap<Int, Int>()
            for (i in 0 until n) {
                // Cada entrada mide 20 bytes justos.
                val entrada = texto.substring(p, p + 20)
                p += 20
                if (i == 0) {
                    assertEquals("0000000000 65535 f\r\n", entrada)
                } else {
                    assertTrue(entrada, Regex("\\d{10} 00000 n\r\n").matches(entrada))
                    val d = entrada.substring(0, 10).toInt()
                    assertTrue("el objeto $i no está en $d", texto.startsWith("$i 0 obj\n", d))
                    desplazamientos[i] = d
                }
            }
            assertTrue(texto.startsWith("trailer\n<< /Size $n /Root 1 0 R", p))
            objetos = desplazamientos
            val raiz = Regex("/Pages (\\d+) 0 R").find(objeto(1))!!.groupValues[1].toInt()
            val hijas = Regex("/Kids \\[([^]]*)]").find(objeto(raiz))!!.groupValues[1]
            paginas = Regex("(\\d+) 0 R").findAll(hijas).map { pagina(it.groupValues[1].toInt()) }.toList()
        }

        fun objeto(n: Int): String {
            val d = objetos.getValue(n)
            return texto.substring(d, texto.indexOf("endobj", d))
        }

        private fun pagina(n: Int): PaginaPdf {
            val o = objeto(n)
            val caja = Regex("/MediaBox (\\[[^]]*])").find(o)!!.groupValues[1]
            val contenido = flujo(Regex("/Contents (\\d+) 0 R").find(o)!!.groupValues[1].toInt())
            val k = 72.0 / 25.4
            val rectangulos = ArrayList<Rectangulo>()
            val textos = ArrayList<TextoPdf>()
            for (linea in contenido.lines()) {
                val r = Regex("(\\S+) (\\S+) (\\S+) (\\S+) re f").matchEntire(linea)
                if (r != null) {
                    val (x, y, w, h) = r.destructured.toList().map { it.toDouble() / k }
                    rectangulos += Rectangulo(x, ALTO_PAGINA - y - h, w, h)
                    continue
                }
                val t = Regex("BT /F1 (\\S+) Tf (\\S+) (\\S+) Td \\((.*)\\) Tj ET").matchEntire(linea)
                if (t != null) {
                    val (puntos, x, y, cadena) = t.destructured
                    // WinAnsi es Latin-1 salvo de 0x80 a 0x9F: aquí solo se usan los guiones largos.
                    val leida = cadena.replace(Regex("\\\\(.)"), "$1").replace('\u0096', '–').replace('\u0097', '—')
                    textos += TextoPdf(x.toDouble() / k, ALTO_PAGINA - y.toDouble() / k, puntos.toDouble(), leida)
                    continue
                }
                assertTrue("operación inesperada: $linea", linea == "0 g" || linea.isEmpty())
            }
            return PaginaPdf(caja, rectangulos, textos)
        }

        /** El contenido de un flujo, con su /Length comprobado. */
        private fun flujo(n: Int): String {
            val o = objeto(n)
            val largo = Regex("/Length (\\d+)").find(o)!!.groupValues[1].toInt()
            val inicio = objetos.getValue(n) + o.indexOf("stream\n") + "stream\n".length
            assertTrue("/Length de $n", texto.startsWith("\nendstream\nendobj", inicio + largo))
            return texto.substring(inicio, inicio + largo)
        }
    }

    private fun rectangulosSvg(svg: String): List<Rectangulo> =
        Regex("<rect x=\"(\\S+)\" y=\"(\\S+)\" width=\"(\\S+)\" height=\"(\\S+)\"/>").findAll(svg).map {
            val (x, y, w, h) = it.destructured.toList().map(String::toDouble)
            Rectangulo(x, y, w, h)
        }.toList()

    private companion object {
        const val TOLERANCIA = 0.001
    }
}
