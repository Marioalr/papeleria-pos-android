package mx.papeleriapos.android.catalogo

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.lista.Renglon
import mx.papeleriapos.escaner.nucleo.Catalogo
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.pos.Cantidad
import mx.papeleriapos.pos.busqueda.IndiceDeBusqueda
import mx.papeleriapos.pos.busqueda.ProductoParaBuscar
import mx.papeleriapos.pos.busqueda.Sinonimo
import mx.papeleriapos.pos.precio.CalculoPrecio
import mx.papeleriapos.pos.precio.Fecha
import mx.papeleriapos.pos.precio.Precio
import mx.papeleriapos.pos.precio.PrecioAplicado
import mx.papeleriapos.pos.precio.Presentacion
import mx.papeleriapos.pos.precio.PromocionDeProducto

/*
 * Los productos con nombre y precio que enseñan Precio, Buscar y el ticket. De verdad vienen de la copia del catálogo de
 * la caja (entrega 2, [mx.papeleriapos.android.copia.ProductosDeLaCopia]); los de prueba (los del prototipo, ficticios, en
 * assets/productos-prueba.csv) quedan para las herramientas de medición y las pruebas, y traen lo mismo: el código, el
 * nombre, los precios con sus escalas, la existencia y las presentaciones.
 */

/** Cómo se mide lo que se vende (`UnidadBase` del POS, en su orden). */
enum class Unidad(val corta: String, val unaPalabra: String, val variasPalabra: String) {
    PIEZA("pza", "pieza", "piezas"),
    METRO("m", "metro", "metros"),
    KILOGRAMO("kg", "kilo", "kilos"),
    LITRO("l", "litro", "litros"),

    /** Por pieza, pero a granel: admite fracciones y se escribe como la pieza. */
    GRANEL("pza", "pieza", "piezas"),
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
     * que sus piezas a menudeo, y para eso necesita las del suelto. Las pone [CatalogoDeProductos] (en los de prueba).
     */
    val escalasDelSuelto: List<Escala> = emptyList(),
    /** El código que enseña la tarjeta (el principal de esta presentación); null si no tiene ninguno. En los de prueba es la clave. */
    val codigo: String? = clave,
    /**
     * Cómo lo cobra el POS, de la copia del catálogo: sus precios tal cual, su presentación, su sucursal y sus promociones.
     * null en los de prueba: se arma de las escalas.
     */
    val cobro: Cobro? = null,
) {
    val precio: Long get() = escalas.first().centavos

    /** La primera escala de mayoreo, la que enseña la tarjeta («Desde 12: $41.00 c/u»). */
    val mayoreo: Escala? get() = escalas.getOrNull(1)

    /** Con lo que se cobra: lo de la copia o, en los de prueba, las escalas como las guardaría el POS. */
    private val comoSeCobra: Cobro = cobro ?: Cobro.deEscalas(clave, base, piezas, escalas, escalasDelSuelto)

    /**
     * El precio de llevar [cantidad], calculado como la caja ([CalculoPrecio], el del núcleo del POS): la escala más alta
     * que alcanza, el precio propio de la presentación, la promoción que corre hoy si conviene más, y nunca más caro que a
     * menudeo. null si no tiene precio.
     */
    private fun aplicado(cantidad: Int): PrecioAplicado? = comoSeCobra.aplicado(cantidad)

    /** El precio de cada una al llevar [cantidad] (como va en el renglón de la venta). */
    fun precioUnitario(cantidad: Int): Long = aplicado(cantidad)?.precioPorPresentacion(comoSeCobra.presentacion) ?: precio

    fun importe(cantidad: Int): Long = aplicado(cantidad)?.importeCentavos ?: (precio * cantidad)

    /** Si con [cantidad] ya entra el mayoreo (el renglón dice «Mayoreo» y el total ya va con ese precio). */
    fun esMayoreo(cantidad: Int): Boolean = aplicado(cantidad)?.esMayoreo == true
}

/**
 * Lo que necesita [CalculoPrecio] para cobrar un producto como la caja: los precios de su variante (por unidad base y
 * los propios de sus presentaciones, de la sucursal y generales), la presentación que se vende, la sucursal de la caja
 * y las promociones del producto, que se deciden con [dia] (sin él, sin promociones).
 */
data class Cobro(
    val precios: List<Precio>,
    val presentacion: Presentacion,
    val sucursalId: String,
    val promociones: List<PromocionDeProducto> = emptyList(),
    val dia: Fecha? = null,
) {
    fun aplicado(cantidad: Int): PrecioAplicado? =
        CalculoPrecio.calcular(precios, sucursalId, presentacion, Cantidad.piezas(cantidad.toLong()), promociones = promociones, dia = dia)

    /** El precio de una presentación al llevar una, como lo dice la tarjeta; null si la variante no tiene precio. */
    fun precioDeUna(): Long? = aplicado(1)?.precioPorPresentacion(presentacion)

    companion object {
        /** Los productos de prueba no tienen sucursal: todos sus precios son los base. */
        const val SIN_SUCURSAL = "00000000-0000-0000-0000-000000000000"

        /** Como los guardaría el POS: por pieza del suelto (con sus escalas) y, en una presentación, el suyo propio. */
        fun deEscalas(clave: String, base: String?, piezas: Int, escalas: List<Escala>, escalasDelSuelto: List<Escala>): Cobro {
            val presentacion = Presentacion(id = clave, varianteId = base ?: clave, factor = Cantidad.piezas(piezas.toLong()))
            val precios = if (base == null) {
                escalas.map { Precio(varianteId = clave, precioCentavos = it.centavos, cantidadMinima = Cantidad.piezas(it.desde.toLong())) }
            } else {
                escalas.map { Precio(varianteId = base, precioCentavos = it.centavos, cantidadMinima = Cantidad.piezas(it.desde.toLong()), presentacionId = clave) } +
                    escalasDelSuelto.map { Precio(varianteId = base, precioCentavos = it.centavos, cantidadMinima = Cantidad.piezas(it.desde.toLong())) }
            }
            return Cobro(precios, presentacion, SIN_SUCURSAL)
        }
    }
}

/** Lo que hay en la tienda de [p], para la tarjeta y el ticket: «Queda 1» o «Quedan 3» cuando queda poco. */
fun quedanPocos(p: Producto): Boolean = p.existencia != null && p.existencia <= POCA_EXISTENCIA

/**
 * Con esto o menos, la tarjeta y el renglón lo dicen en ámbar: la regla de «Poca existencia» de la caja
 * (`PocaExistencia.Hasta` = 3), que no tiene un mínimo propio por producto.
 */
const val POCA_EXISTENCIA = 3.0

/** «Queda 1» con una sola y «Quedan 3» con más (docs/propuesta-interfaz.md, «Precio» y «Ticket»: «Queda 1» en ámbar). */
fun quedan(existencia: Double): String = if (existencia == 1.0) "Queda 1" else "Quedan ${cantidadCorta(existencia)}"

/**
 * El precio por pieza de una presentación, como lo dice la tarjeta de Precio: «$4.50 c/u (12)» en la caja de 12 lápices;
 * null en el suelto. También lo enseña el escaneo rápido, sobre el bloqueo.
 */
fun porPieza(p: Producto): String? = if (p.piezas > 1) "${pesos(p.precio / p.piezas)} c/u (${p.piezas})" else null

/** La primera escala de mayoreo, como la dicen la tarjeta y el precio en grande: «Desde 12: $41.00 c/u»; null si no tiene. */
fun desdeMayoreo(p: Producto): String? = p.mayoreo?.let { "Desde ${it.desde}: ${pesos(it.centavos)} c/u" }

/** Lo contado o lo leído, abreviado: «1 pza» con una sola y «3 pzs» con más (también «0 pzs»). */
fun pzs(n: Int): String = if (n == 1) "1 pza" else "$n pzs"

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
 * ([IndiceDeBusqueda], portada de su núcleo) para lo que no se escanea. Los de la copia del catálogo se arman en
 * [mx.papeleriapos.android.copia.ProductosDeLaCopia]; los de prueba, con el constructor de la lista.
 *
 * @param productos Todos: los sueltos y sus presentaciones.
 * @param porCodigo Cada código con las claves de los productos que lo tienen: casi siempre una; con dos, el código es
 * de más de un producto y no se adivina ([deVarios]), como en el POS.
 * @param buscables Lo que sale al buscar por nombre: en la copia, las variantes (como la caja); en los de prueba, todos.
 * @param elegidos Lo que la caja eligió antes con cada texto (su clave guardada), de la copia: sale primero.
 * @param variantes Cuántos productos distintos (sin contar presentaciones): lo que dice Ajustes.
 */
class CatalogoDeProductos private constructor(
    val productos: List<Producto>,
    private val porCodigo: Map<String, List<String>>,
    buscables: List<ProductoParaBuscar>,
    marcas: Iterable<String>?,
    sinonimos: Iterable<Sinonimo>?,
    private val elegidos: Map<String, String>,
    val variantes: Int,
) {
    /** Los de prueba (y los de las pruebas): cada presentación con las escalas de su suelto, para que el precio se calcule como en la caja. */
    constructor(productosSinSuelto: List<Producto>) : this(
        productos = productosSinSuelto.let { lista ->
            val escalas = lista.associate { it.clave to it.escalas }
            lista.map { p -> if (p.base == null) p else p.copy(escalasDelSuelto = escalas[p.base].orEmpty()) }
        },
        porCodigo = productosSinSuelto.associate { it.clave to listOf(it.clave) },
        buscables = productosSinSuelto.map { ProductoParaBuscar(varianteId = it.clave, descripcion = it.nombre, codigo = it.clave, codigos = listOf(it.clave)) },
        marcas = null,
        sinonimos = null,
        elegidos = emptyMap(),
        variantes = productosSinSuelto.count { it.base == null },
    )

    private val porClave = productos.associateBy { it.clave }
    private val claves: Catalogo = Catalogo.deLineas(sequenceOf("codigo") + porCodigo.keys.asSequence())
    private val indice = IndiceDeBusqueda.armar(buscables, marcas, sinonimos)
    private val buscablesPorClave = buscables.map { it.varianteId }.toHashSet()

    /** Los que salen sin escribir nada: los rápidos y luego lo más vendido. */
    private val sinTexto: List<Producto> by lazy {
        productos.filter { it.clave in buscablesPorClave }
            .sortedWith(compareByDescending<Producto> { it.rapido }.thenByDescending { it.vendidos })
    }

    val tamano: Int get() = productos.size

    fun porClave(clave: String): Producto? = porClave[clave]

    /**
     * Lo escaneado ([normalizado] y su simbología), con las variantes de UPC y EAN; null si no está o si el código es de
     * más de un producto ([deVarios]).
     */
    fun porLectura(normalizado: String, simbologia: Simbologia): Producto? =
        clavesDe(normalizado, simbologia).singleOrNull()?.let { porClave[it] }

    /** Si lo escaneado da con más de un producto (dos productos con el mismo código): no se adivina, se busca por nombre. */
    fun deVarios(normalizado: String, simbologia: Simbologia): Boolean = clavesDe(normalizado, simbologia).size > 1

    private fun clavesDe(normalizado: String, simbologia: Simbologia): List<String> =
        claves.buscarNormalizado(normalizado, simbologia)?.let { porCodigo[it.clave] } ?: porCodigo[normalizado].orEmpty()

    /**
     * El producto de un renglón del ticket o del conteo: por su clave y, si no la trae (lo que sumó el escaneo rápido
     * antes de la 0.3 o lo que guardó la 0.1) o ya no la encuentra (la clave era de otro catálogo), por su código con la
     * regla de UPC y EAN, como al escanearlo. Así un producto que sí está no sale en rojo ni deja de sumar al total.
     */
    fun de(r: Renglon): Producto? = r.clave?.let { porClave[it] } ?: porLectura(r.codigo, r.simbologia)

    /**
     * Lo que se escribe en Buscar, con las reglas de la caja: pedazos de palabra en cualquier orden («lus az» da el papel
     * lustre azul), un error de dedo, lo que se eligió antes con ese texto ([elegido], su clave; si no, lo que la caja
     * eligió según la copia) primero y luego lo más vendido; hasta [maximo]. Sin escribir nada, los rápidos y lo más vendido.
     */
    fun buscar(texto: String, maximo: Int = MAXIMO_RESULTADOS, elegido: String? = null): List<Producto> {
        if (texto.isBlank()) return sinTexto.take(maximo)
        val primero = elegido ?: elegidos[IndiceDeBusqueda.claveGuardada(texto)]
        return indice.buscar(texto, maximo, primero) { clave -> porClave[clave]?.vendidos ?: 0 }
            .mapNotNull { porClave[it.articulo.varianteId] }
    }

    companion object {
        /** Como la búsqueda de la caja: hasta 30. */
        const val MAXIMO_RESULTADOS = 30

        /** Los productos de prueba (los del prototipo, ficticios), en assets: los leen la app y el escaneo rápido. */
        const val ARCHIVO_DE_PRUEBA = "productos-prueba.csv"

        val VACIO = CatalogoDeProductos(emptyList())

        /** El de la copia del catálogo ([mx.papeleriapos.android.copia.ProductosDeLaCopia] lo arma). */
        internal fun deCopia(
            productos: List<Producto>,
            porCodigo: Map<String, List<String>>,
            buscables: List<ProductoParaBuscar>,
            marcas: Iterable<String>,
            sinonimos: Iterable<Sinonimo>,
            elegidos: Map<String, String>,
            variantes: Int,
        ) = CatalogoDeProductos(productos, porCodigo, buscables, marcas, sinonimos, elegidos, variantes)

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
