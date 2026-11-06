package mx.papeleriapos.pos.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Buscar por nombre contra la copia del celular (BusquedaEnLaCopiaTests del POS, entrega 5, parte 3): con los datos de la base
 * de prueba del POS, la búsqueda de Kotlin da lo mismo que la del servicio, en el mismo orden, sin la red. Lo del servicio es
 * lo que encontró la búsqueda del POS con esos mismos productos, marcas y sinónimos ([BaseDePrueba]). La pantalla, la red y lo
 * que se guarda en la base no son de la búsqueda: no se traen. Lo que tarda solo se anota.
 */
class BusquedaEnLaCopiaTest {
    private val copia = BaseDePrueba.juego("base")

    /** Todo lo que dice un resultado, para comparar (el nombre en pedazos, por su huella). */
    private fun renglon(lugar: Int, descripcion: String, codigo: String?, parecido: Boolean, porSinonimo: String?, huellaDePartes: String) =
        "$lugar · $descripcion · $codigo · ${BaseDePrueba.vendidos(BaseDePrueba.id(lugar))} · $parecido · $porSinonimo · $huellaDePartes"

    private fun deLaCopia(indice: IndiceDeBusqueda, texto: String, maximo: Int, elegido: String? = null): List<String> =
        indice.buscar(texto, maximo, elegido, BaseDePrueba::vendidos).map {
            val a = it.articulo
            renglon(BaseDePrueba.lugar(a.varianteId), a.descripcion, a.codigo, it.parecido, it.porSinonimo, BaseDePrueba.huella(BaseDePrueba.partes(a.partes)))
        }

    /** Lo que contestó el servicio del POS con hasta [maximo]: los primeros de lo que encontró con 200, que van en el mismo orden. */
    private fun delServicio(juego: String, texto: String, maximo: Int, elegido: Int = -1): List<String> {
        val esperado = BaseDePrueba.esperado(juego, texto, elegido)
        return esperado.encontrados.take(maximo).map { (lugar, porSinonimo) ->
            val p = BaseDePrueba.productos[lugar]
            renglon(lugar, p.descripcion, p.codigo, esperado.parecido, porSinonimo, p.huellaDePartes)
        }
    }

    /**
     * Lo que escribiría la cajera, sacado del catálogo de la copia: palabras enteras, principios de palabra, dos pedazos en
     * cualquier orden, lo que lee la caja del nombre (colores, medidas), errores de dedo y principios de código.
     */
    private fun textos(productos: List<BaseDePrueba.Producto>): List<String> {
        val vistas = HashSet<String>()
        val palabras = productos.flatMap { ComoNet.pedazos(it.descripcion) }
            .map { it.trim('(', ')', '.', ',') }
            .filter { w -> w.length >= 3 && w.any { it.isLetter() } }
            .filter { vistas.add(ComoNet.claveSinMayusculas(it)) }
            .sorted()
        val textos = ArrayList<String>()
        for (i in palabras.indices step maxOf(1, palabras.size / 40)) {
            val w = palabras[i]
            textos.add(ComoNet.minusculas(w))
            textos.add(ComoNet.minusculas(w.substring(0, 3)))
            // Un error de dedo: dos letras volteadas.
            if (w.length >= 5) textos.add(ComoNet.minusculas("${w.substring(0, 2)}${w[3]}${w[2]}${w.substring(4)}"))
        }
        for (k in productos.indices step maxOf(1, productos.size / 30)) {
            val p = productos[k]
            val ws = ComoNet.pedazos(p.descripcion).filter { w -> w.length >= 3 && w.any { it.isLetter() } }
            if (ws.size >= 2) {
                textos.add("${ws[0].substring(0, 3)} ${ws[1].substring(0, 3)}")
                textos.add("${ws[1].substring(0, 3)} ${ws[0].substring(0, 3)}")
                textos.add("${ws[0]} ${ws[ws.size - 1]}")
            }
            val codigo = p.codigo
            if (codigo != null && codigo.length >= 4) textos.add(codigo.substring(0, 4))
        }
        textos.addAll(
            listOf(
                "pluma gel", "pl gel", "cua pro", "folder", "lustre plata", "negro", "surtido", "1mm", "sobre manial", "cuadreno",
                "pluma pumto fino", "Pluma  PUNTO fino", "lápiz", "z",
            ),
        )
        return textos.distinct()
    }

    private fun comparar(indice: IndiceDeBusqueda, juego: String, textos: List<String>, maximo: Int, elegido: String? = null): Int {
        var comparados = 0
        for (texto in textos) {
            val delServicio = delServicio(juego, texto, maximo, elegido?.let { BaseDePrueba.lugar(it) } ?: -1)
            val deLaCopia = deLaCopia(indice, texto, maximo, elegido)
            assertEquals("«$texto» no da lo mismo", delServicio, deLaCopia)
            if (delServicio.isNotEmpty()) comparados++
        }
        return comparados
    }

    @Test
    fun `la copia busca lo mismo que el servicio en el mismo orden`() {
        val textos = textos(BaseDePrueba.productos)
        // Los mismos textos que sacó la prueba del POS de su copia.
        assertEquals(BaseDePrueba.renglones(BaseDePruebaDatos.TEXTOS).map { BaseDePrueba.desescapar(it) }, textos)
        // Lo vendido en 90 días ordena cuando el texto no distingue: la base de prueba trae ventas, y la copia las lleva.
        assertTrue(BaseDePrueba.productos.any { it.vendidos > 0 })

        val conResultados = comparar(copia, "base", textos, maximo = 200)
        comparar(copia, "base", textos.take(60), maximo = 30)

        println("${textos.size} textos, $conResultados con resultados, contra ${BaseDePrueba.productos.size} productos.")
        assertTrue("Solo $conResultados de ${textos.size} encontraron algo.", conResultados > textos.size / 2)
        // También los parecidos (un error de dedo) y los que se encuentran por lo que lee la caja del nombre.
        val parecidos = copia.buscar("sobre manial", 200, null, BaseDePrueba::vendidos)
        assertTrue(parecidos.all { it.parecido })
        assertTrue(parecidos.isNotEmpty())
        assertTrue(copia.buscar("lustre plata", 200, null, BaseDePrueba::vendidos).all { "Plateado" in it.articulo.descripcion })
    }

    /**
     * Lo que se eligió con un texto sale primero, igual en el servicio que en la copia: el que se eligió antes viene en la
     * copia (con la clave con que lo guarda el servicio), y el que se elige ahora se recuerda sin esperar a que baje otra.
     */
    @Test
    fun `lo elegido antes sale primero tambien en la copia`() {
        val antes = copia.buscar("pluma punto fino", 200, null, BaseDePrueba::vendidos)
        assertEquals(delServicio("base", "pluma punto fino", 200), deLaCopia(copia, "pluma punto fino", 200))
        val elegido = antes[antes.size - 1].articulo.varianteId
        assertNotEquals(antes[0].articulo.varianteId, elegido)

        // Se elige en el celular: se guarda con la clave de lo escrito y la búsqueda lo busca con la de lo que se escribe.
        val elegidos = HashMap<String, String>()
        elegidos[IndiceDeBusqueda.claveGuardada(" Pluma  PUNTO fino")] = elegido
        val delServicioConElegido = BaseDePrueba.esperado("base", "pluma punto fino", BaseDePrueba.lugar(elegido))
        assertEquals(BaseDePrueba.lugar(elegido), delServicioConElegido.encontrados[0].first)
        val enLaCopia = copia.buscar("pluma punto fino", 200, elegidos[IndiceDeBusqueda.clave("pluma punto fino")], BaseDePrueba::vendidos)
        assertEquals(elegido, enLaCopia[0].articulo.varianteId)
        for (texto in listOf("pluma punto fino", "PLUMA PUNTO FINO")) {
            assertEquals(elegido, elegidos[IndiceDeBusqueda.clave(texto)])
            comparar(copia, "base", listOf(texto), 200, elegidos[IndiceDeBusqueda.clave(texto)])
        }

        // Otro celular con la misma copia lo trae con esa clave.
        assertEquals(mapOf("PLUMA PUNTO FINO" to elegido), elegidos)
        // Sin decir quién eligió (compras, etiquetas…) el servicio no lo pone primero, y la copia tampoco.
        comparar(copia, "base", listOf("pluma punto fino"), 200)
    }

    /**
     * Los sinónimos de la tienda viajan con la copia y la búsqueda del celular los usa igual: lo que salió por un sinónimo va
     * después de lo que se llama así y lo dice.
     */
    @Test
    fun `los sinonimos de la tienda dan lo mismo en la copia`() {
        // El encargado da de alta «rotulador» = «plumon», también al revés.
        val rotulador = Sinonimo.normalizar("rotulador")!!
        val plumon = Sinonimo.normalizar("plumon")!!
        val conSinonimos = BaseDePrueba.armar(sinonimos = listOf(Sinonimo(rotulador, plumon), Sinonimo(plumon, rotulador)))

        val plumones = conSinonimos.buscar("rotulador", 200, null, BaseDePrueba::vendidos)
        assertTrue(plumones.isNotEmpty())
        assertTrue(plumones.all { it.porSinonimo == "rotulador" })
        comparar(conSinonimos, "rotulador", listOf("rotulador", "rotul", "plumon", "plumon rotulador", "rotulador negro"), maximo = 200)
    }

    /**
     * Escribir un nombre en la venta busca en la copia; Enter sin elegir agrega el primer renglón (en una familia, su más
     * vendido), el mismo que en el POS. Lo de la pantalla y la red es de la app.
     */
    @Test
    fun `escribir en la venta busca en la copia sin la red`() {
        val resultados = copia.buscar("pluma gel", 200, null, BaseDePrueba::vendidos)
        val primero = Familias.agrupar(resultados, { it.articulo.partes }, { BaseDePrueba.vendidos(it.articulo.varianteId) }).renglones[0].elegido

        assertEquals(BaseDePruebaDatos.PRIMERO_PLUMA_GEL, BaseDePrueba.lugar(primero.articulo.varianteId))
    }

    /**
     * Lo que tarda buscar en la copia, del texto a la lista (sin la red ni el dibujo), con la base de prueba y con la base de
     * prueba cuatro veces (casi 6,000 productos, como la tienda): se anota, no se revisa (depende de la máquina).
     */
    @Test
    fun `lo que tarda buscar en la copia se anota`() {
        medir(BaseDePrueba.productos)
        medir(cuatro(BaseDePrueba.productos))
    }

    private fun medir(productos: List<BaseDePrueba.Producto>) {
        val inicio = System.nanoTime()
        val indice = BaseDePrueba.armar(productos)
        val armar = (System.nanoTime() - inicio) / 1_000_000
        val textos = listOf("pl", "plu", "pluma", "pluma g", "pluma gel", "cua pro", "folder", "sobre manial", "cuadreno", "lap az")
        for (texto in textos) indice.buscar(texto, 200, null, BaseDePrueba::vendidos)
        println("Copia con ${indice.productos} productos: armar la búsqueda, $armar ms.")
        for (texto in textos) {
            val reloj = System.nanoTime()
            var encontrados = 0
            repeat(20) { encontrados = indice.buscar(texto, 200, null, BaseDePrueba::vendidos).size }
            println("  «$texto»: ${(System.nanoTime() - reloj) / 20 / 1000} µs, $encontrados resultados.")
        }
        assertTrue(indice.productos > 0)
    }

    /** La copia cuatro veces, cada vez con otros Id y otro número al final del nombre (y de cada código). */
    private fun cuatro(productos: List<BaseDePrueba.Producto>): List<BaseDePrueba.Producto> =
        (0 until 4).flatMap { vez ->
            productos.map {
                BaseDePrueba.Producto(
                    lugar = 100_000 * (vez + 1) + it.lugar,
                    descripcion = if (vez == 0) it.descripcion else "${it.descripcion} $vez",
                    codigo = if (vez == 0 || it.codigo == null) it.codigo else "${it.codigo}-$vez",
                    codigos = it.codigos.map { c -> if (vez == 0) c else "$c-$vez" },
                    vendidos = 0,
                    huellaDePartes = "",
                )
            }
        }

    /** Además de lo que revisa la prueba del POS: cada texto que anotó el oráculo, con cada juego, da lo mismo que allá. */
    @Test
    fun `con cada juego y cada texto da lo mismo que el POS`() {
        for (e in BaseDePrueba.esperados) {
            val elegido = if (e.elegido >= 0) BaseDePrueba.id(e.elegido) else null
            val encontrados = BaseDePrueba.juego(e.juego).buscar(e.texto, 200, elegido, BaseDePrueba.vendidosDe(e.juego))
            val donde = "${e.juego} «${e.texto}» (elegido ${e.elegido})"
            assertEquals(donde, e.encontrados, BaseDePrueba.lugares(encontrados))
            assertTrue(donde, encontrados.all { it.parecido == e.parecido })
        }
    }

    /** Además: las familias en que se juntan los resultados de cada texto, como las junta el POS. */
    @Test
    fun `las familias de cada texto son las del POS`() {
        for (renglon in BaseDePrueba.renglones(BaseDePruebaDatos.FAMILIAS)) {
            val (escrito, huella) = renglon.split('|').map { BaseDePrueba.desescapar(it) }
            val texto = escrito.removePrefix("200:")
            val maximo = if (escrito.startsWith("200:")) 200 else 30
            val a = Familias.agrupar(copia.buscar(texto, maximo, null, BaseDePrueba::vendidos), { it.articulo.partes }) {
                BaseDePrueba.vendidos(it.articulo.varianteId)
            }
            val enTexto = "${BaseDePrueba.ejeDelPos(a.eje)}|${a.ejes.joinToString(",") { BaseDePrueba.ejeDelPos(it) }}|" +
                a.renglones.joinToString(" ; ") { r ->
                    "${BaseDePrueba.ejeDelPos(r.eje)}~${r.titulo ?: ""}~${r.masVendido}~" +
                        r.valores.joinToString(",") { "${BaseDePrueba.lugar(it.elemento.articulo.varianteId)}=${it.texto}=${it.clave}" }
                }
            assertEquals("«$escrito»: $enTexto", huella, BaseDePrueba.huella(enTexto))
        }
    }
}
