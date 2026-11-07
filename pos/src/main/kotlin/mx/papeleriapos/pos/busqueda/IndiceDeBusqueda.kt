package mx.papeleriapos.pos.busqueda

import java.util.BitSet
import java.util.Collections

/**
 * Un producto como lo ve la búsqueda: su variante (el Id que manda la API, como texto), su nombre, su código y todos sus
 * códigos.
 *
 * @param codigo El que se enseña en la lista (el de su presentación base).
 * @param codigos Todos los suyos: los de sus presentaciones y los que comparte con otro producto.
 */
data class ProductoParaBuscar(val varianteId: String, val descripcion: String, val codigo: String?, val codigos: List<String>)

/** Un producto ya listo para buscarlo. */
class ArticuloBuscable internal constructor(
    /** En qué lugar llegó (el de la lista de [IndiceDeBusqueda.articulos]). */
    internal val posicion: Int,
    val varianteId: String,
    val descripcion: String,
    val codigo: String?,
    /** El nombre y todos los códigos, sin acentos y en mayúsculas. */
    val normalizado: String,
    /** El nombre en pedazos (color, medida, marca…): con eso se juntan las familias de la lista ([Familias]). */
    val partes: List<ParteDeNombre>,
    /** El nombre para ordenar en español de México sin importar mayúsculas, ya calculado (comparar así es rápido). */
    internal val ordenDelNombre: IntArray,
)

/**
 * Lo que encontró la búsqueda, en su orden.
 *
 * @param parecido Nada coincidía exacto: salió por un error de dedo («sobre manial»).
 * @param porSinonimo La palabra de la tienda por la que salió, en minúsculas («boligrafo», si su nombre dice «Lapicero»).
 */
data class Encontrado(val articulo: ArticuloBuscable, val parecido: Boolean, val porSinonimo: String?)

/**
 * La búsqueda por nombre del POS (`IndiceDeBusqueda` de su núcleo, la del servicio y la de la copia de cada caja), para
 * buscar en la copia del celular, también sin red: con los mismos productos, códigos, marcas y sinónimos da lo mismo, en
 * el mismo orden. Se arma una vez (al cargar la copia), fuera de la búsqueda, y después no cambia: se puede buscar desde
 * varios hilos a la vez.
 *
 * Cada pedazo escrito tiene que ser el principio de una palabra del producto, en cualquier orden y sin importar acentos
 * («lus az» = Papel lustre azul), o de lo que vale por él en los sinónimos de la tienda. Las palabras son las del nombre
 * y las de los códigos, tal cual («3/4», «LIB-01») y partidas en lo que no es letra ni número («(azul)» → «azul»), y
 * también lo que lee la caja del nombre ([LectorDeNombres]): «negro» encuentra «Negra», «surtido» los «V/Colores» y
 * «1mm» el «1.0 Mm». Si nada coincide exacto, un error de dedo ([ErrorDeDedo]).
 *
 * El orden: el código exacto; entre los parecidos, los que coinciden exacto en más palabras; lo que se eligió antes con
 * el mismo texto; lo que se llama como se escribió antes de lo que salió por un sinónimo; lo que empieza con lo escrito;
 * lo más vendido; el nombre (en español de México, [OrdenEspanol]) y, al final, entre los que se llaman igual, el más
 * antiguo: los Id del POS son UUIDv7, que van por fecha, y en texto se comparan sin importar mayúsculas, como el Guid.
 *
 * Por dentro, cada palabra apunta a los productos que la tienen, y las palabras van ordenadas: las que empiezan con un
 * pedazo quedan juntas y se encuentran sin recorrer todo el catálogo.
 */
class IndiceDeBusqueda private constructor(
    private val todos: Array<ArticuloBuscable>,
    /** Todas las palabras, sin repetir, en orden ordinal: las que empiezan igual quedan juntas. */
    private val palabras: Array<String>,
    /** Los productos (su lugar) que tienen cada palabra de [palabras], de menor a mayor. */
    private val conPalabra: Array<IntArray>,
    private val sinonimos: SinonimosDeBusqueda,
) {
    private val porVariante = HashMap<String, ArticuloBuscable>(todos.size * 2)

    init {
        // Si una variante viene dos veces, se queda la primera (como el TryAdd del POS).
        for (a in todos) if (a.varianteId !in porVariante) porVariante[a.varianteId] = a
    }

    /** Cuántos productos tiene. */
    val productos: Int get() = todos.size

    /** Los productos, en el orden en que llegaron. */
    val articulos: List<ArticuloBuscable> = Collections.unmodifiableList(todos.asList())

    fun porVariante(varianteId: String): ArticuloBuscable? = porVariante[varianteId]

    /**
     * Lo que coincide con lo escrito, hasta [maximo], en su orden (ver la clase).
     *
     * @param elegido Lo que se eligió antes con el mismo texto (se busca con [clave]): sale primero.
     * @param vendidos Cuántas veces se vendió cada producto (en 90 días): ordena cuando el texto no distingue.
     */
    fun buscar(texto: String, maximo: Int = 30, elegido: String? = null, vendidos: ((String) -> Int)? = null): List<Encontrado> {
        val consulta = clave(texto)
        val pedazos = ComoNet.pedazos(consulta)
        if (pedazos.isEmpty() || maximo <= 0 || todos.isEmpty()) return emptyList()

        // Cada pedazo vale por sí mismo o por los sinónimos de la tienda («bolig» → lapicero).
        val deLaTienda = pedazos.map { sinonimos.de(it) }
        val directos = pedazos.map { conPrincipio(it) }
        val porSinonimo = deLaTienda.map { lista -> lista.map { conPrincipio(it.equivale) } }

        var candidatos = coinciden(directos, porSinonimo)
        // Si nada coincide exacto, un error de dedo: lo parecido, en lugar de «no existe».
        var parecido = false
        if (candidatos.isEmpty) {
            candidatos = coinciden(pedazos.map { parecidos(it) }, porSinonimo)
            parecido = !candidatos.isEmpty
        }

        val codigo = ComoNet.recortar(texto)
        val lugares = ArrayList<Lugar>()
        var i = candidatos.nextSetBit(0)
        while (i >= 0) {
            val a = todos[i]
            // La palabra de la tienda por la que salió, si algún pedazo no estaba en el nombre.
            var sinonimo: String? = null
            var k = 0
            while (k < pedazos.size && sinonimo == null) {
                if (!directos[k][i]) {
                    for (j in deLaTienda[k].indices) {
                        if (porSinonimo[k][j][i]) {
                            sinonimo = ComoNet.minusculas(deLaTienda[k][j].palabra)
                            break
                        }
                    }
                }
                k++
            }
            var coincidenExacto = 0
            if (parecido) {
                for (d in directos) if (d[i]) coincidenExacto++
            }
            lugares.add(
                Lugar(
                    articulo = a,
                    codigoExacto = a.codigo != null && ComoNet.igualesSinMayusculas(a.codigo, codigo),
                    coinciden = coincidenExacto,
                    elegido = a.varianteId == elegido,
                    porSinonimo = sinonimo,
                    empieza = a.normalizado.startsWith(consulta),
                    vendidos = vendidos?.invoke(a.varianteId) ?: 0,
                ),
            )
            i = candidatos.nextSetBit(i + 1)
        }
        lugares.sortWith(ORDEN)
        val encontrados = ArrayList<Encontrado>(minOf(maximo, lugares.size))
        for (lugar in lugares) {
            if (encontrados.size == maximo) break
            encontrados.add(Encontrado(lugar.articulo, parecido, lugar.porSinonimo))
        }
        return encontrados
    }

    /** Un producto encontrado, con lo que decide su lugar en la lista. */
    private class Lugar(
        val articulo: ArticuloBuscable,
        val codigoExacto: Boolean,
        val coinciden: Int,
        val elegido: Boolean,
        val porSinonimo: String?,
        val empieza: Boolean,
        val vendidos: Int,
    )

    /** Los que valen por cada pedazo: por sí mismo o por alguno de sus sinónimos; tienen que valer todos. */
    private fun coinciden(porPedazo: List<BitSet>, porSinonimo: List<List<BitSet>>): BitSet {
        val todosValen = BitSet(todos.size)
        todosValen.set(0, todos.size)
        for (k in porPedazo.indices) {
            val vale = porPedazo[k].clone() as BitSet
            for (sinonimo in porSinonimo[k]) vale.or(sinonimo)
            todosValen.and(vale)
        }
        return todosValen
    }

    /** Los productos con alguna palabra que empieza con [pedazo]. */
    private fun conPrincipio(pedazo: String): BitSet {
        val conjunto = BitSet(todos.size)
        var i = primeraDesde(pedazo)
        while (i < palabras.size && palabras[i].startsWith(pedazo)) {
            marcar(conjunto, conPalabra[i])
            i++
        }
        return conjunto
    }

    /** Los productos con alguna palabra que coincide con el pedazo por un error de dedo ([ErrorDeDedo.coincide]). */
    private fun parecidos(pedazo: String): BitSet {
        val conjunto = BitSet(todos.size)
        for (i in palabras.indices) {
            if (ErrorDeDedo.coincide(pedazo, palabras[i])) marcar(conjunto, conPalabra[i])
        }
        return conjunto
    }

    /** El lugar de la primera palabra que no va antes de [pedazo] (orden ordinal). */
    private fun primeraDesde(pedazo: String): Int {
        var desde = 0
        var hasta = palabras.size
        while (desde < hasta) {
            val medio = (desde + hasta) ushr 1
            if (palabras[medio] < pedazo) desde = medio + 1 else hasta = medio
        }
        return desde
    }

    companion object {
        /** Sin productos: no encuentra nada. */
        val VACIO = IndiceDeBusqueda(emptyArray(), emptyArray(), emptyArray(), SinonimosDeBusqueda.NINGUNO)

        /** Lo más largo que se guarda de lo que se buscó (la clave de «lo elegido antes»). */
        const val LARGO_GUARDADO = 100

        /** La clave de lo escrito: sin acentos, mayúsculas ni espacios de más. Con ella se recuerda «lo elegido antes». */
        fun clave(texto: String): String = Texto.normalizar(texto)

        /** La clave con que se guarda lo que se buscó: la de [clave], hasta [LARGO_GUARDADO] letras. */
        fun claveGuardada(texto: String): String {
            val clave = clave(texto)
            return if (clave.length > LARGO_GUARDADO) ComoNet.recortarFinal(clave.substring(0, LARGO_GUARDADO)) else clave
        }

        /**
         * Arma el índice. Los productos, los códigos, las marcas y los sinónimos pueden venir en cualquier orden y los
         * códigos, las marcas y los sinónimos, repetidos: dan lo mismo. Así la copia del celular puesta al día con lo que
         * cambió (los productos nuevos al final) busca igual que una recién bajada y que el POS. Un producto que viene dos
         * veces sale dos veces, como allá.
         *
         * @param marcas Las marcas para leer los nombres ([LectorDeNombres]); también se aprenden de lo que se repite al
         * final de los nombres.
         * @param sinonimos Los de la tienda, como los manda el POS (ya normalizados).
         */
        fun armar(
            productos: Iterable<ProductoParaBuscar>,
            marcas: Iterable<String>?,
            sinonimos: Iterable<Sinonimo>?,
        ): IndiceDeBusqueda {
            val lista = productos.toList()
            val lector = LectorDeNombres(lista.map { it.descripcion }, marcas)
            // Cada palabra una sola vez en memoria, con los productos que la tienen.
            val conPalabra = HashMap<String, Lugares>()
            val articulos = Array(lista.size) { i ->
                val p = lista[i]
                val partes = lector.leer(p.descripcion)
                val codigos = p.codigos.distinct().sorted().joinToString(" ")
                val normalizado = Texto.normalizar("${p.descripcion} $codigos")
                for (palabra in palabras(normalizado, partes)) conPalabra.getOrPut(palabra) { Lugares() }.agregar(i)
                ArticuloBuscable(i, p.varianteId, p.descripcion, p.codigo, normalizado, partes, OrdenEspanol.llave(p.descripcion))
            }
            val palabras = conPalabra.keys.sorted().toTypedArray()
            val apuntan = Array(palabras.size) { conPalabra.getValue(palabras[it]).enArreglo() }
            val ordenados = (sinonimos ?: emptyList()).distinct().sortedWith(compareBy<Sinonimo> { it.palabra }.thenBy { it.equivale })
            return IndiceDeBusqueda(articulos, palabras, apuntan, SinonimosDeBusqueda(ordenados))
        }

        /**
         * Las palabras de un producto: las del nombre y los códigos tal cual («3/4», «LIB-01») y partidas en lo que no es
         * letra ni número («(azul)» → «azul»), y las de lo que lee la caja del nombre. Cada pedazo escrito tiene que ser el
         * principio de una.
         */
        private fun palabras(normalizado: String, partes: List<ParteDeNombre>): Set<String> {
            val salida = HashSet<String>()
            salida.addAll(ComoNet.pedazos(normalizado))
            for (w in NO_LETRA_NI_NUMERO.split(normalizado)) if (w.isNotEmpty()) salida.add(w)
            for (p in partes) for (w in p.clave.split(' ')) if (w.isNotEmpty()) salida.add(w)
            return salida
        }

        private val NO_LETRA_NI_NUMERO = Regex("[^\\p{L}\\p{N}]+")

        /**
         * Primero el código exacto; entre los parecidos, los que coinciden exacto en más palabras («pluma pumto» antes que
         * el plumón); lo elegido antes; lo que se llama como se escribió antes de lo que salió por un sinónimo; lo que
         * empieza con lo escrito; lo más vendido; el nombre, y entre dos que se llaman igual, el más antiguo (su Id va por
         * fecha): no depende del orden en que llegaron.
         */
        private val ORDEN = Comparator<Lugar> { x, y ->
            var c = y.codigoExacto.compareTo(x.codigoExacto)
            if (c == 0) c = y.coinciden.compareTo(x.coinciden)
            if (c == 0) c = y.elegido.compareTo(x.elegido)
            if (c == 0) c = (x.porSinonimo != null).compareTo(y.porSinonimo != null)
            if (c == 0) c = y.empieza.compareTo(x.empieza)
            if (c == 0) c = y.vendidos.compareTo(x.vendidos)
            if (c == 0) c = OrdenEspanol.comparar(x.articulo.ordenDelNombre, y.articulo.ordenDelNombre)
            if (c != 0) c else x.articulo.varianteId.compareTo(y.articulo.varianteId, ignoreCase = true)
        }

        private fun marcar(conjunto: BitSet, lugares: IntArray) {
            for (lugar in lugares) conjunto.set(lugar)
        }
    }

    /** Los lugares de los productos con una palabra, al armar el índice (llegan de menor a mayor). */
    private class Lugares {
        private var valores = IntArray(2)
        private var cuantos = 0

        fun agregar(lugar: Int) {
            if (cuantos == valores.size) valores = valores.copyOf(valores.size * 2)
            valores[cuantos++] = lugar
        }

        fun enArreglo(): IntArray = valores.copyOf(cuantos)
    }
}
