package mx.papeleriapos.escaner.nucleo

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/**
 * La hoja de etiquetas de prueba (docs/pruebas.md, «Hoja de etiquetas»): EAN-13, Code 128 y EAN-8 ficticios con módulos
 * de 0.25 a 0.5 mm y varias alturas de barra, para imprimirla a tamaño real y medir desde qué tamaño lee bien el Pixel.
 * `./gradlew :nucleo:hojaEtiquetas` la escribe en nucleo/build/hoja-etiquetas: el PDF para imprimir, un SVG por página
 * para verla en pantalla y el CSV con un renglón por etiqueta, que sirve de catálogo de prueba.
 *
 * Lo que importa son las medidas. Todo se calcula en mm desde la esquina superior izquierda de la página y el PDF lo
 * pasa a puntos (1 mm = 72/25.4 pt) con 4 decimales, menos de 0.0001 mm de error. Cada barra mide k × módulo, sin
 * reducirla por la tinta, y los módulos negros seguidos van en un solo rectángulo para que no haya costuras.
 */
object HojaEtiquetas {
    /** Carta: 215.9 × 279.4 mm. */
    const val ANCHO_PAGINA = 215.9
    const val ALTO_PAGINA = 279.4
    const val MARGEN = 10.0

    val MODULOS = listOf(0.25, 0.33, 0.40, 0.50)
    val ALTOS = listOf(15.0, 10.0, 6.0)

    /** El EAN-8 de los productos chicos: un solo renglón, a la altura de en medio. */
    const val ALTO_EAN8 = 10.0

    const val LEYENDA = "Imprimir al 100 % (tamaño real). Esta regla debe medir 100 mm"

    /** La regla de calibración: 100 mm de punta a punta, con marcas cada 10. */
    const val LARGO_REGLA = 100.0

    private const val PT_POR_MM = 72.0 / 25.4

    // El acomodo, en mm.
    private const val INICIO = 32.0
    private const val LIMITE = ALTO_PAGINA - MARGEN
    private const val ANCHO_UTIL = ANCHO_PAGINA - 2 * MARGEN
    private const val TITULO_SECCION = 6.0
    private const val ENTRE_ETIQUETAS = 8.0
    private const val ENTRE_LINEAS = 7.0
    private const val ENTRE_SECCIONES = 10.0

    // Debajo de las barras, el código y la descripción; el pie llega hasta el fondo de la descripción.
    private const val PUNTOS_CODIGO = 8.0
    private const val PUNTOS_DESCRIPCION = 6.5
    private const val BASE_CODIGO = 3.4
    private const val BASE_DESCRIPCION = 6.3
    private const val PIE = 7.0

    /**
     * Las zonas en blanco de la norma, en módulos a la izquierda y a la derecha: EAN-13, 11 y 7 (GS1); EAN-8, 7 y 7;
     * Code 128, 10 y 10. Los patrones de [Barras] no las traen (empiezan y terminan con barra), y [Barras.zonaEnBlanco]
     * le da 9 de cada lado al EAN-13: lo mismo en total pero centrado, que sirve para las escenas y no para imprimir.
     */
    fun zonas(simbologia: Simbologia): Pair<Int, Int> = when (simbologia) {
        Simbologia.EAN_13 -> 11 to 7
        Simbologia.EAN_8 -> 7 to 7
        Simbologia.CODE_128 -> 10 to 10
        else -> throw IllegalArgumentException("La hoja no lleva ${simbologia.nombre}.")
    }

    /** Un rectángulo negro, en mm desde la esquina superior izquierda de la página. */
    data class Rectangulo(val x: Double, val y: Double, val ancho: Double, val alto: Double)

    /**
     * Un renglón en Helvetica de [puntos]: [x] es su borde izquierdo o, si va [centrado], su centro; [base] es su línea
     * base.
     */
    data class Texto(
        val x: Double,
        val base: Double,
        val puntos: Double,
        val texto: String,
        val centrado: Boolean = false,
    ) {
        val ancho: Double get() = anchoTexto(texto, puntos)
        val izquierda: Double get() = if (centrado) x - ancho / 2 else x
    }

    /** Lo que se pide imprimir; [ranura] es lo que ocupa a lo ancho: la etiqueta o sus textos, lo más ancho. */
    private class Pedido(val codigo: String, val simbologia: Simbologia, val modulo: Double, val alto: Double) {
        val ancho = zonas(simbologia).let { it.first + Barras.para(simbologia, codigo).size + it.second } * modulo
        val ranura = maxOf(
            ancho,
            anchoTexto(codigo, PUNTOS_CODIGO),
            anchoTexto(descripcion(modulo, alto), PUNTOS_DESCRIPCION),
        )
    }

    /** Una etiqueta ya acomodada: [x] y [y] son la esquina superior izquierda de su zona en blanco izquierda, en mm. */
    class Etiqueta(
        val codigo: String,
        val simbologia: Simbologia,
        val modulo: Double,
        val alto: Double,
        val pagina: Int,
        val x: Double,
        val y: Double,
    ) {
        val modulos: BooleanArray = Barras.para(simbologia, codigo)
        val zonaIzquierda = zonas(simbologia).first
        val zonaDerecha = zonas(simbologia).second
        val xBarras: Double get() = x + zonaIzquierda * modulo
        val ancho: Double get() = (zonaIzquierda + modulos.size + zonaDerecha) * modulo
    }

    class Pagina(val numero: Int) {
        val rectangulos = ArrayList<Rectangulo>()
        val textos = ArrayList<Texto>()
    }

    class Hoja(val paginas: List<Pagina>, val etiquetas: List<Etiqueta>)

    private class Seccion(val titulo: String, val renglones: List<List<Pedido>>)

    /**
     * Qué lleva la hoja, en orden. Cada código dice qué etiqueta es: el EAN-13, 750999 + módulo en centésimas + alto en
     * mm + consecutivo (2 dígitos) + verificador; el Code 128 corto, ZZ + módulo + alto; el largo, ZZ-M033-A10-001 con
     * el consecutivo en 3 dígitos; el EAN-8, 750999 + la primera cifra del módulo + verificador. El consecutivo es el
     * número de la etiqueta en la hoja (su renglón en el CSV).
     */
    private fun secciones(): List<Seccion> {
        var n = 0
        val secciones = ArrayList<Seccion>()
        for (modulo in MODULOS) {
            val m = centesimas(modulo)
            val renglones = ALTOS.map { alto ->
                val a = alto.toInt()
                listOf(
                    Pedido(Gtin.completar(formato("750999%02d%02d%02d", m, a, ++n)), Simbologia.EAN_13, modulo, alto),
                    Pedido(formato("ZZ%02d%02d", m, a).also { n++ }, Simbologia.CODE_128, modulo, alto),
                    Pedido(formato("ZZ-M%03d-A%02d-%03d", m, a, ++n), Simbologia.CODE_128, modulo, alto),
                )
            }
            secciones += Seccion("Módulo ${milimetros(modulo)} mm · EAN-13, Code 128 corto y Code 128 largo", renglones)
        }
        val ean8 = MODULOS.map { Pedido(Gtin.completar("750999${centesimas(it) / 10}"), Simbologia.EAN_8, it, ALTO_EAN8) }
        secciones += Seccion("EAN-8 chicos · alto ${milimetros(ALTO_EAN8)} mm", listOf(ean8))
        return secciones
    }

    /** Acomoda las secciones de arriba abajo; la que no cabe en lo que queda de la página empieza en otra. */
    fun generar(): Hoja {
        val paginas = ArrayList<Pagina>()
        val etiquetas = ArrayList<Etiqueta>()
        var pagina = Pagina(1).also { paginas += it }
        var y = INICIO
        fun otraPagina() {
            pagina = Pagina(paginas.size + 1).also { paginas += it }
            y = INICIO
        }
        fun titulo(texto: String) {
            pagina.textos += Texto(MARGEN, y + 4.0, 9.0, texto)
            y += TITULO_SECCION
        }
        for (seccion in secciones()) {
            val lineas = seccion.renglones.flatMap { lineas(it) }
            val altoSeccion = TITULO_SECCION + lineas.sumOf { altoLinea(it) } + ENTRE_LINEAS * (lineas.size - 1)
            // Si cabe en una página, entera; si no, al menos el título con su primera línea.
            val necesita = if (altoSeccion <= LIMITE - INICIO) altoSeccion else TITULO_SECCION + altoLinea(lineas[0])
            if (y > INICIO && y + necesita > LIMITE) otraPagina()
            titulo(seccion.titulo)
            for ((i, linea) in lineas.withIndex()) {
                if (i > 0) y += ENTRE_LINEAS
                if (y + altoLinea(linea) > LIMITE) {
                    otraPagina()
                    titulo("${seccion.titulo} (sigue)")
                }
                var x = MARGEN
                for (p in linea) {
                    val izquierda = x + (p.ranura - p.ancho) / 2
                    val e = Etiqueta(p.codigo, p.simbologia, p.modulo, p.alto, pagina.numero, izquierda, y)
                    dibujar(e, pagina)
                    etiquetas += e
                    x += p.ranura + ENTRE_ETIQUETAS
                }
                y += altoLinea(linea)
            }
            y += ENTRE_SECCIONES
        }
        for (p in paginas) encabezado(p, paginas.size)
        return Hoja(paginas, etiquetas)
    }

    /** Parte un renglón en líneas que quepan a lo ancho, sin cambiar el orden. */
    private fun lineas(renglon: List<Pedido>): List<List<Pedido>> {
        val lineas = ArrayList<MutableList<Pedido>>()
        var ancho = 0.0
        for (p in renglon) {
            if (lineas.isEmpty() || ancho + ENTRE_ETIQUETAS + p.ranura > ANCHO_UTIL) {
                lineas += ArrayList<Pedido>()
                ancho = -ENTRE_ETIQUETAS
            }
            lineas.last() += p
            ancho += ENTRE_ETIQUETAS + p.ranura
        }
        return lineas
    }

    private fun altoLinea(linea: List<Pedido>) = linea.maxOf { it.alto } + PIE

    /** Las barras (un rectángulo por tramo de módulos negros) y, debajo, el código y la descripción, centrados. */
    private fun dibujar(e: Etiqueta, pagina: Pagina) {
        var i = 0
        while (i < e.modulos.size) {
            if (!e.modulos[i]) {
                i++
                continue
            }
            val inicio = i
            while (i < e.modulos.size && e.modulos[i]) i++
            pagina.rectangulos += Rectangulo(e.xBarras + inicio * e.modulo, e.y, (i - inicio) * e.modulo, e.alto)
        }
        val centro = e.x + e.ancho / 2
        val pie = e.y + e.alto
        val medidas = descripcion(e.modulo, e.alto)
        pagina.textos += Texto(centro, pie + BASE_CODIGO, PUNTOS_CODIGO, e.codigo, centrado = true)
        pagina.textos += Texto(centro, pie + BASE_DESCRIPCION, PUNTOS_DESCRIPCION, medidas, centrado = true)
    }

    /** Arriba de cada página: el título, la leyenda y la regla de 100 mm con marcas cada 10 (más largas cada 50). */
    private fun encabezado(p: Pagina, total: Int) {
        p.textos += Texto(MARGEN, 13.5, 9.0, "Hoja de etiquetas de prueba (códigos ficticios) — página ${p.numero} de $total")
        p.textos += Texto(MARGEN, 18.0, 8.0, LEYENDA)
        // Un milímetro adentro, para que el «0» de abajo no se salga del margen.
        val x0 = MARGEN + 1.0
        val fondo = 24.5
        val grueso = 0.3
        val marca = 0.25
        p.rectangulos += Rectangulo(x0, fondo - grueso, LARGO_REGLA, grueso)
        for (k in 0..10) {
            // Las de las puntas van por dentro: de punta a punta mide justo 100 mm.
            val izquierda = when (k) {
                0 -> x0
                10 -> x0 + LARGO_REGLA - marca
                else -> x0 + 10.0 * k - marca / 2
            }
            val largo = if (k % 5 == 0) 4.0 else 2.5
            p.rectangulos += Rectangulo(izquierda, fondo - largo, marca, largo)
            p.textos += Texto(izquierda + marca / 2, 27.0, 6.0, "${10 * k}", centrado = true)
        }
        p.textos += Texto(x0 + LARGO_REGLA + 3.0, 27.0, 6.0, "mm")
    }

    fun descripcion(modulo: Double, alto: Double) = "módulo ${milimetros(modulo)} mm · alto ${milimetros(alto)} mm"

    /** 0.4 → «0.40»; los enteros, sin decimales. */
    fun milimetros(mm: Double): String =
        if (mm == Math.rint(mm)) formato("%d", mm.toLong()) else formato("%.2f", mm)

    private fun centesimas(modulo: Double) = Math.round(modulo * 100).toInt()

    private fun formato(patron: String, vararg valores: Any) = String.format(Locale.ROOT, patron, *valores)

    // Helvetica es una de las 14 fuentes base del PDF y no se incrusta; sus anchos, en milésimas de em, de la AFM de
    // Adobe (con WinAnsi: 0x27 es la comilla recta y 0x60 el acento grave).
    private val ANCHOS_ASCII = intArrayOf(
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, // espacio a /
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556, // 0 a ?
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, // @ a O
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556, // P a _
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556, // ` a o
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584, // p a ~
    )
    private val ANCHOS_OTROS = mapOf(
        'á' to 556, 'é' to 556, 'í' to 278, 'ó' to 556, 'ú' to 556, 'ñ' to 556, 'ü' to 556,
        'Á' to 667, 'É' to 667, 'Í' to 278, 'Ó' to 778, 'Ú' to 722, 'Ñ' to 722,
        '·' to 278, '×' to 584, '°' to 400, '«' to 556, '»' to 556, '–' to 556, '—' to 1000,
    )

    /** Lo que mide [texto] a lo ancho en Helvetica de [puntos], en mm. */
    fun anchoTexto(texto: String, puntos: Double): Double {
        var milesimas = 0
        for (c in texto) {
            milesimas += if (c in ' '..'~') {
                ANCHOS_ASCII[c - ' ']
            } else {
                ANCHOS_OTROS[c] ?: throw IllegalArgumentException("«$c» no tiene ancho en la hoja.")
            }
        }
        return milesimas * puntos / 1000 / PT_POR_MM
    }

    /** El byte de [c] en WinAnsiEncoding: el de Latin-1, salvo los guiones largos. */
    private fun winAnsi(c: Char): Char = when {
        c in ' '..'~' || c.code in 0xA0..0xFF -> c
        c == '–' -> Char(0x96)
        c == '—' -> Char(0x97)
        else -> throw IllegalArgumentException("«$c» no está en WinAnsi.")
    }

    /** Un número para el PDF o el SVG: 4 decimales, sin ceros de sobra. */
    fun numero(v: Double): String {
        val s = formato("%.4f", v).trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    private fun pt(mm: Double) = numero(mm * PT_POR_MM)

    /**
     * El PDF 1.4, escrito a mano: catálogo, páginas, la fuente, los datos y, por página, su objeto y su contenido sin
     * comprimir (se lee con un editor de texto). Las cadenas van en WinAnsi y el archivo, en Latin-1 byte por byte.
     */
    fun pdf(hoja: Hoja): ByteArray {
        val salida = ByteArrayOutputStream()
        val total = 4 + 2 * hoja.paginas.size
        val desplazamientos = IntArray(total)
        fun escribe(texto: String) = salida.write(texto.toByteArray(Charsets.ISO_8859_1))
        fun objeto(numero: Int, cuerpo: String) {
            desplazamientos[numero - 1] = salida.size()
            escribe("$numero 0 obj\n$cuerpo\nendobj\n")
        }
        // El comentario con bytes altos avisa que el archivo es binario.
        escribe("%PDF-1.4\n%âãÏÓ\n")
        val hijas = hoja.paginas.indices.joinToString(" ") { "${5 + 2 * it} 0 R" }
        // PrintScaling es de PDF 1.6 y los demás lo ignoran; el lector que lo conoce propone «Tamaño real» al imprimir.
        objeto(1, "<< /Type /Catalog /Pages 2 0 R /ViewerPreferences << /PrintScaling /None >> >>")
        objeto(2, "<< /Type /Pages /Kids [$hijas] /Count ${hoja.paginas.size} >>")
        objeto(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        objeto(4, "<< /Title (Hoja de etiquetas de prueba) /Producer (papeleria-pos-android :nucleo:hojaEtiquetas) >>")
        val caja = "[0 0 ${pt(ANCHO_PAGINA)} ${pt(ALTO_PAGINA)}]"
        val recursos = "<< /Font << /F1 3 0 R >> >>"
        for ((i, pagina) in hoja.paginas.withIndex()) {
            val contenido = contenido(pagina)
            objeto(5 + 2 * i, "<< /Type /Page /Parent 2 0 R /MediaBox $caja /Resources $recursos /Contents ${6 + 2 * i} 0 R >>")
            objeto(6 + 2 * i, "<< /Length ${contenido.length} >>\nstream\n$contenido\nendstream")
        }
        val xref = salida.size()
        // Cada entrada mide 20 bytes justos, con su fin de línea de dos.
        val tabla = StringBuilder("xref\n0 ${total + 1}\n0000000000 65535 f\r\n")
        for (d in desplazamientos) tabla.append(formato("%010d 00000 n\r\n", d))
        escribe(tabla.toString())
        escribe("trailer\n<< /Size ${total + 1} /Root 1 0 R /Info 4 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return salida.toByteArray()
    }

    /** Las barras como «x y ancho alto re f» (el PDF cuenta desde abajo) y cada texto en su lugar exacto. */
    private fun contenido(p: Pagina): String {
        val s = StringBuilder("0 g\n")
        for (r in p.rectangulos) {
            s.append(pt(r.x)).append(' ').append(pt(ALTO_PAGINA - r.y - r.alto)).append(' ')
                .append(pt(r.ancho)).append(' ').append(pt(r.alto)).append(" re f\n")
        }
        for (t in p.textos) {
            s.append("BT /F1 ").append(numero(t.puntos)).append(" Tf ").append(pt(t.izquierda)).append(' ')
                .append(pt(ALTO_PAGINA - t.base)).append(" Td (")
            for (c in t.texto) {
                val b = winAnsi(c)
                if (b == '(' || b == ')' || b == '\\') s.append('\\')
                s.append(b)
            }
            s.append(") Tj ET\n")
        }
        return s.toString()
    }

    /** Una página en SVG, en mm (viewBox de 215.9 × 279.4), para verla en pantalla. */
    fun svg(p: Pagina): String {
        val ancho = numero(ANCHO_PAGINA)
        val alto = numero(ALTO_PAGINA)
        val s = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        s.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${ancho}mm\" height=\"${alto}mm\" ")
            .append("viewBox=\"0 0 $ancho $alto\">\n")
        s.append("<rect width=\"$ancho\" height=\"$alto\" fill=\"#fff\"/>\n<g fill=\"#000\">\n")
        for (r in p.rectangulos) {
            s.append("<rect x=\"").append(numero(r.x)).append("\" y=\"").append(numero(r.y))
                .append("\" width=\"").append(numero(r.ancho)).append("\" height=\"").append(numero(r.alto)).append("\"/>\n")
        }
        s.append("</g>\n<g fill=\"#000\" font-family=\"Helvetica, Arial, sans-serif\">\n")
        for (t in p.textos) {
            s.append("<text x=\"${numero(t.x)}\" y=\"${numero(t.base)}\" font-size=\"${numero(t.puntos / PT_POR_MM)}\"")
            if (t.centrado) s.append(" text-anchor=\"middle\"")
            s.append('>')
            for (c in t.texto) {
                when (c) {
                    '&' -> s.append("&amp;")
                    '<' -> s.append("&lt;")
                    '>' -> s.append("&gt;")
                    else -> s.append(c)
                }
            }
            s.append("</text>\n")
        }
        s.append("</g>\n</svg>\n")
        return s.toString()
    }

    /** Un renglón por etiqueta; la primera columna es el código, para cargarlo como catálogo de prueba. */
    fun csv(hoja: Hoja): String {
        val s = StringBuilder("codigo,simbologia,modulo_mm,alto_mm,pagina\n")
        for (e in hoja.etiquetas) {
            s.append(e.codigo).append(',').append(e.simbologia.name).append(',').append(milimetros(e.modulo))
                .append(',').append(milimetros(e.alto)).append(',').append(e.pagina).append('\n')
        }
        return s.toString()
    }

    fun escribir(carpeta: File): Hoja {
        val hoja = generar()
        carpeta.mkdirs()
        // Los SVG de una corrida anterior con más páginas.
        carpeta.listFiles { f -> f.name.startsWith("hoja-etiquetas") }?.forEach { it.delete() }
        File(carpeta, "hoja-etiquetas.pdf").writeBytes(pdf(hoja))
        for (p in hoja.paginas) File(carpeta, "hoja-etiquetas-${p.numero}.svg").writeText(svg(p))
        File(carpeta, "hoja-etiquetas.csv").writeText(csv(hoja))
        return hoja
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val carpeta = File(args.firstOrNull() ?: "build/hoja-etiquetas")
        val hoja = escribir(carpeta)
        println("Hoja de etiquetas: ${hoja.paginas.size} páginas y ${hoja.etiquetas.size} etiquetas en ${carpeta.path}")
    }
}
