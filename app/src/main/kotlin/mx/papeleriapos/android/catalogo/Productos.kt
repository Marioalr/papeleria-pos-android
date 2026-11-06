package mx.papeleriapos.android.catalogo

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.escaner.nucleo.Catalogo
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.pos.Cantidad
import mx.papeleriapos.pos.busqueda.IndiceDeBusqueda
import mx.papeleriapos.pos.busqueda.ProductoParaBuscar
import mx.papeleriapos.pos.precio.CalculoPrecio
import mx.papeleriapos.pos.precio.Precio
import mx.papeleriapos.pos.precio.PrecioAplicado
import mx.papeleriapos.pos.precio.Presentacion

/*
 * Los productos con nombre y precio para la entrega 1 (docs/propuesta-interfaz.md, «Entregas»: «la búsqueda y el ticket
 * con el catálogo de prueba, sin red todavía»). Son los del prototipo, ficticios, en assets/productos-prueba.csv. La
 * copia del catálogo de verdad llega del POS con la entrega 2 y trae lo mismo: la clave, el nombre, el departamento, los
 * precios con sus escalas, la existencia y las presentaciones.
 */

/** Cómo se mide lo que se vende. */
enum class Unidad(val corta: String, val unaPalabra: String, val variasPalabra: String) {
    PIEZA("pza", "pieza", "piezas"),
    METRO("m", "metro", "metros"),
    ;

    /** «1 pieza», «3 piezas», «10 metros». */
    fun de(n: Number): String = "$n ${if (n.toDouble() == 1.0) unaPalabra else variasPalabra}"
}

/** Desde [desde] unidades, cada una cuesta [centavos]. La primera escala es el menudeo; las demás, mayoreo. */
data class Escala(val desde: Int, val centavos: Long)

/**
 * Lo que se vende con un código: un producto, o una de sus presentaciones con su propio código y su precio (la caja de
 * 12 lápices). Los precios llevan el IVA, como en la caja.
 */
data class Producto(
    /** El código con que está en el catálogo: a él llega la regla de UPC y EAN del núcleo. */
    val clave: String,
    val nombre: String,
    /** «Escolar»; «Servicios» para lo que no se cuenta. */
    val departamento: String,
    /** De menor a mayor [Escala.desde]; la primera es el menudeo. */
    val escalas: List<Escala>,
    /** En unidades del suelto (una caja de 12 dice las piezas sueltas que hay), o null si no lleva inventario. */
    val existencia: Double?,
    val unidad: Unidad = Unidad.PIEZA,
    /** Las unidades del suelto que trae una presentación (la caja: 12); 1 el suelto. */
    val piezas: Int = 1,
    /** La clave del suelto, si esto es una presentación. */
    val base: String? = null,
    /** La de hoy, como la dice la caja. */
    val promocion: String? = null,
    /** Para ordenar la búsqueda: lo más vendido primero. */
    val vendidos: Int = 0,
    /** Los rápidos (Copia B/N, CURP): salen en Buscar sin escribir nada. */
    val rapido: Boolean = false,
    /**
     * Las escalas del suelto, si esto es una presentación: el POS cobra la caja con su precio propio, pero nunca más caro
     * que sus piezas a menudeo, y para eso necesita las del suelto. Las pone [CatalogoDeProductos].
     */
    val escalasDelSuelto: List<Escala> = emptyList(),
) {
    val precio: Long get() = escalas.first().centavos

    /** La primera escala de mayoreo, la que enseña la tarjeta («Desde 12: $41.00 c/u»). */
    val mayoreo: Escala? get() = escalas.getOrNull(1)

    /** Como la ve el POS: la variante (el suelto) y esta presentación, con las piezas que trae. */
    private val presentacion = Presentacion(id = clave, varianteId = base ?: clave, factor = Cantidad.piezas(piezas.toLong()))

    /** Los precios como los guarda el POS: por pieza del suelto (con sus escalas) y, en una presentación, el suyo propio. */
    private val precios: List<Precio> = if (base == null) {
        escalas.map { Precio(varianteId = clave, precioCentavos = it.centavos, cantidadMinima = Cantidad.piezas(it.desde.toLong())) }
    } else {
        escalas.map { Precio(varianteId = base, precioCentavos = it.centavos, cantidadMinima = Cantidad.piezas(it.desde.toLong()), presentacionId = clave) } +
            escalasDelSuelto.map { Precio(varianteId = base, precioCentavos = it.centavos, cantidadMinima = Cantidad.piezas(it.desde.toLong())) }
    }

    /**
     * El precio de llevar [cantidad], calculado como la caja ([CalculoPrecio], el del núcleo del POS): la escala más alta
     * que alcanza, el precio propio de la presentación y nunca más caro que a menudeo. null si no tiene precio.
     */
    private fun aplicado(cantidad: Int): PrecioAplicado? =
        CalculoPrecio.calcular(precios, SUCURSAL, presentacion, Cantidad.piezas(cantidad.toLong()))

    /** El precio de cada una al llevar [cantidad] (como va en el renglón de la venta). */
    fun precioUnitario(cantidad: Int): Long = aplicado(cantidad)?.precioPorPresentacion(presentacion) ?: precio

    fun importe(cantidad: Int): Long = aplicado(cantidad)?.importeCentavos ?: (precio * cantidad)

    /** Si con [cantidad] ya entra el mayoreo (el renglón dice «Mayoreo» y el total ya va con ese precio). */
    fun esMayoreo(cantidad: Int): Boolean = aplicado(cantidad)?.esMayoreo == true

    private companion object {
        /** Los productos de prueba no tienen sucursal: todos sus precios son los base. */
        const val SUCURSAL = "00000000-0000-0000-0000-000000000000"
    }
}

/** Lo que hay en la tienda de [p], para la tarjeta y el ticket: «Quedan 1» cuando queda poco. */
fun quedanPocos(p: Producto): Boolean = p.existencia != null && p.existencia <= POCA_EXISTENCIA

/** Con esto o menos, la tarjeta y el renglón dicen «Quedan N» en ámbar (la regla de «Poca existencia» de la caja). */
const val POCA_EXISTENCIA = 2.0

/** «$1,234.50», como los importes de la caja: con comas en los miles y siempre dos decimales. */
fun pesos(centavos: Long): String {
    val negativo = centavos < 0
    val c = kotlin.math.abs(centavos)
    val enteros = (c / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (negativo) "−$" else "$") + enteros + "." + (c % 100).toString().padStart(2, '0')
}

/** Una cantidad sin decimales de más: 85.5 → «85.5», 140.0 → «140». */
fun cantidadCorta(n: Double): String = if (n == Math.floor(n)) n.toLong().toString() else n.toString()

/**
 * Los productos, con la regla de UPC y EAN del núcleo para encontrar lo escaneado ([porLectura]) y la búsqueda del POS
 * ([IndiceDeBusqueda], portada de su núcleo) para lo que no se escanea.
 */
class CatalogoDeProductos(productosSinSuelto: List<Producto>) {
    /** Cada presentación con las escalas de su suelto, para que el precio se calcule como en la caja. */
    val productos: List<Producto> = productosSinSuelto.let { lista ->
        val escalas = lista.associate { it.clave to it.escalas }
        lista.map { p -> if (p.base == null) p else p.copy(escalasDelSuelto = escalas[p.base].orEmpty()) }
    }
    private val porClave = productos.associateBy { it.clave }
    private val claves: Catalogo = Catalogo.deLineas(sequenceOf("codigo") + productos.asSequence().map { it.clave })
    private val indice = IndiceDeBusqueda.armar(
        productos.map { ProductoParaBuscar(varianteId = it.clave, descripcion = it.nombre, codigo = it.clave, codigos = listOf(it.clave)) },
        marcas = null,
        sinonimos = null,
    )

    val tamano: Int get() = productos.size

    fun porClave(clave: String): Producto? = porClave[clave]

    /** Lo escaneado ([normalizado] y su simbología), con las variantes de UPC y EAN; null si no está. */
    fun porLectura(normalizado: String, simbologia: Simbologia): Producto? =
        claves.buscarNormalizado(normalizado, simbologia)?.let { porClave[it.clave] } ?: porClave[normalizado]

    /**
     * Lo que se escribe en Buscar, con las reglas de la caja: pedazos de palabra en cualquier orden («lus az» da el papel
     * lustre azul), un error de dedo, lo que se eligió antes con ese texto ([elegido], su clave) primero y luego lo más
     * vendido; hasta [maximo]. Sin escribir nada, los rápidos y lo más vendido.
     */
    fun buscar(texto: String, maximo: Int = MAXIMO_RESULTADOS, elegido: String? = null): List<Producto> {
        if (texto.isBlank()) {
            return productos.sortedWith(compareByDescending<Producto> { it.rapido }.thenByDescending { it.vendidos }).take(maximo)
        }
        return indice.buscar(texto, maximo, elegido) { clave -> porClave[clave]?.vendidos ?: 0 }
            .mapNotNull { porClave[it.articulo.varianteId] }
    }

    companion object {
        /** Como la búsqueda de la caja: hasta 30. */
        const val MAXIMO_RESULTADOS = 30

        val VACIO = CatalogoDeProductos(emptyList())

        private val COLUMNAS = listOf(
            "clave", "nombre", "departamento", "precio", "mayoreo_desde", "mayoreo_precio", "existencia", "unidad",
            "piezas", "base", "promocion", "vendidos", "rapido",
        )

        /** El CSV de assets/productos-prueba.csv; un renglón que no se entiende se salta. */
        fun deCsv(texto: String): CatalogoDeProductos {
            val filas = Csv.leer(texto.removePrefix("\uFEFF"))
            if (filas.isEmpty() || filas[0] != COLUMNAS) return VACIO
            val productos = filas.drop(1).mapNotNull { f ->
                if (f.size < COLUMNAS.size) return@mapNotNull null
                val d = COLUMNAS.zip(f).toMap()
                val clave = d.getValue("clave").trim().ifEmpty { return@mapNotNull null }
                val precio = centavos(d.getValue("precio")) ?: return@mapNotNull null
                val desde = d.getValue("mayoreo_desde").toIntOrNull()
                val precioMayoreo = centavos(d.getValue("mayoreo_precio"))
                val escalas = listOf(Escala(1, precio)) +
                    (if (desde != null && desde > 1 && precioMayoreo != null) listOf(Escala(desde, precioMayoreo)) else emptyList())
                Producto(
                    clave = clave,
                    nombre = d.getValue("nombre").trim(),
                    departamento = d.getValue("departamento").trim(),
                    escalas = escalas,
                    existencia = d.getValue("existencia").toDoubleOrNull(),
                    unidad = if (d.getValue("unidad").trim() == "metro") Unidad.METRO else Unidad.PIEZA,
                    piezas = d.getValue("piezas").toIntOrNull()?.takeIf { it > 0 } ?: 1,
                    base = d.getValue("base").trim().ifEmpty { null },
                    promocion = d.getValue("promocion").trim().ifEmpty { null },
                    vendidos = d.getValue("vendidos").toIntOrNull() ?: 0,
                    rapido = d.getValue("rapido").trim() == "si",
                )
            }.distinctBy { it.clave }
            return CatalogoDeProductos(productos)
        }

        /** «46.00» → 4600; null si no es un precio. */
        private fun centavos(texto: String): Long? {
            val t = texto.trim()
            if (t.isEmpty()) return null
            val partes = t.split('.')
            if (partes.size > 2 || partes.any { p -> p.isEmpty() || p.any { !it.isDigit() } }) return null
            val pesos = partes[0].toLongOrNull() ?: return null
            val decimales = partes.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLong() ?: 0L
            return pesos * 100 + decimales
        }
    }
}
