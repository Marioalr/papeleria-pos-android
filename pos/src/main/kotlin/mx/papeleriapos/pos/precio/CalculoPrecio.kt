package mx.papeleriapos.pos.precio

import mx.papeleriapos.pos.Cantidad
import mx.papeleriapos.pos.Redondeo
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.min

/**
 * El precio que le tocó a una línea.
 *
 * @param menudeo El menudeo del mismo grupo de escalas (la de cantidad más baja): contra él se mide el mayoreo.
 * @param importeMenudeoCentavos Lo que costaría la misma cantidad a precio de menudeo.
 * @param promocion La promoción que se aplicó (fase 3): le convenía más al cliente que el mayoreo y que las demás. Entonces
 * [precio] es el menudeo y el importe, el de la promoción.
 * @param ahorroPromocionCentavos Lo que se ahorró por la promoción contra el precio de la línea ([precioPorPresentacion]) por
 * la cantidad: así el importe de la línea es siempre ese precio por la cantidad menos este ahorro, y se vuelve a armar igual
 * al subir una venta sin conexión. Cero sin promoción.
 */
data class PrecioAplicado(
    val precio: Precio,
    val importeCentavos: Long,
    val menudeo: Precio? = null,
    val importeMenudeoCentavos: Long = 0,
    val promocion: PromocionDeProducto? = null,
    val ahorroPromocionCentavos: Long = 0,
) {
    /** Si se aplicó una escala de mayoreo que de verdad baja el precio. */
    val esMayoreo: Boolean
        get() = menudeo != null && precio.cantidadMinima > menudeo.cantidadMinima && importeMenudeoCentavos > importeCentavos

    val ahorroCentavos: Long get() = if (esMayoreo) importeMenudeoCentavos - importeCentavos else 0

    /** El precio de una presentación como va en la línea de la venta: el propio de la presentación, o el de la unidad base por las que trae. */
    fun precioPorPresentacion(presentacion: Presentacion): Long =
        if (precio.presentacionId == null) presentacion.factor.importe(precio.precioCentavos) else precio.precioCentavos
}

/**
 * Las escalas que tocan a una presentación: las suyas propias (raro) o las de la unidad base, de menor a mayor.
 *
 * @param dePresentacion Si son precios propios de la presentación: las cantidades son de presentaciones, no de piezas.
 */
data class EscalasDePrecio(val dePresentacion: Boolean, val escalas: List<Precio>)

/**
 * El único lugar donde se decide el precio, como `CalculoPrecio` del POS: la pantalla de la caja, su cobro sin conexión y el
 * servicio calculan igual, y el celular enseña lo mismo que cobrará la caja.
 */
object CalculoPrecio {
    /**
     * Precio que aplica al vender [cantidad] de una presentación en una sucursal:
     * 1. Si la presentación tiene precio propio (raro), se usa ese, por cada presentación.
     * 2. Si no, el precio por unidad base: el paquete de 12 cuesta 12 piezas y cuenta como 12 para el mayoreo.
     *
     * En ambos casos se prefieren los precios de la sucursal sobre los base, y se toma la escala más alta que alcance la
     * cantidad. Con [mayoreoSiempre] (el privilegio de un cliente registrado), al menos la primera escala de mayoreo aunque
     * no se llegue a su cantidad. Devuelve null si la variante no tiene precio.
     *
     * Nunca se cobra más que a menudeo: si las escalas quedaron capturadas fuera de orden (una más cara que el menudeo o que
     * una de menos piezas), se cobra la más barata de las que alcanza; y un paquete cuyo precio propio cuesta más que sus
     * piezas a menudeo se cobra por sus piezas.
     *
     * Con las [promociones] del producto que corren ese [dia] (fase 3): no se acumulan. Cada una se calcula sobre el
     * menudeo y se queda la que deja el importe más bajo, si es más bajo que el del mayoreo (o el menudeo); entre dos
     * iguales, la de menor Id, para que la caja y el servicio elijan la misma. Con el [descuentoDelCliente] (el fijo del
     * cliente registrado, que tampoco se acumula con ellas), la promoción tiene que dejar menos que el mayoreo (o el
     * menudeo) con ese descuento; si no, se queda el descuento.
     *
     * @param precios Los de la variante (los de otras se ignoran).
     * @param promociones Las del producto, corran o no ese día: el cálculo decide con [dia].
     * @param dia El día en hora de México; sin él, sin promociones.
     * @param descuentoDelCliente En centésimas (500 = 5 %): el del cliente registrado que lleva el renglón.
     */
    fun calcular(
        precios: Iterable<Precio>,
        sucursalId: String,
        presentacion: Presentacion,
        cantidad: Cantidad,
        mayoreoSiempre: Boolean = false,
        promociones: List<PromocionDeProducto>? = null,
        dia: Fecha? = null,
        descuentoDelCliente: Int? = null,
    ): PrecioAplicado? {
        val regular = sinPromociones(precios, sucursalId, presentacion, cantidad, mayoreoSiempre)
        return if (regular != null && !promociones.isNullOrEmpty() && dia != null && cantidad.esPositiva) {
            conLaQueMasConviene(regular, presentacion, cantidad, promociones, dia, descuentoDelCliente)
        } else {
            regular
        }
    }

    private fun sinPromociones(
        precios: Iterable<Precio>,
        sucursalId: String,
        presentacion: Presentacion,
        cantidad: Cantidad,
        mayoreoSiempre: Boolean,
    ): PrecioAplicado? {
        val deLaVariante = precios.filter { Ids.iguales(it.varianteId, presentacion.varianteId) }

        val propias = deSucursalOBase(deLaVariante.filter { Ids.iguales(it.presentacionId, presentacion.id) }, sucursalId)
        val porUnidad = deSucursalOBase(deLaVariante.filter { it.presentacionId == null }, sucursalId)
        // El paquete nunca cuesta más que sus piezas a menudeo: si así quedó su precio propio, se cobra por sus piezas.
        val propio = elegirEscala(propias, cantidad, mayoreoSiempre)
        if (propio != null) {
            val menudeoPorPieza = porUnidad.minByOrNull { it.cantidadMinima }
            if (menudeoPorPieza == null || propio.precioCentavos <= presentacion.factor.importe(menudeoPorPieza.precioCentavos)) {
                val menudeo = propias.minBy { it.cantidadMinima }
                return PrecioAplicado(
                    propio,
                    cantidad.importe(propio.precioCentavos),
                    menudeo = menudeo,
                    importeMenudeoCentavos = cantidad.importe(menudeo.precioCentavos),
                )
            }
        }

        val unidadesBase = cantidad.por(presentacion.factor)
        val escala = elegirEscala(porUnidad, unidadesBase, mayoreoSiempre) ?: return null
        val deMenudeo = porUnidad.minBy { it.cantidadMinima }
        return PrecioAplicado(
            escala,
            unidadesBase.importe(escala.precioCentavos),
            menudeo = deMenudeo,
            importeMenudeoCentavos = unidadesBase.importe(deMenudeo.precioCentavos),
        )
    }

    /**
     * La que más le conviene al cliente: el mayoreo (o el menudeo) que ya se calculó, o la promoción que deja el importe más
     * bajo. Las promociones van sobre el menudeo del mismo grupo de precios (el de la unidad base, o el propio de la
     * presentación) y cuentan en sus unidades: piezas (un paquete de 12 son 12 para el 3×2, como para el mayoreo) o
     * presentaciones, si tienen precio propio. El precio especial es por unidad base.
     */
    private fun conLaQueMasConviene(
        regular: PrecioAplicado,
        presentacion: Presentacion,
        cantidad: Cantidad,
        promociones: List<PromocionDeProducto>,
        dia: Fecha,
        descuentoDelCliente: Int?,
    ): PrecioAplicado {
        val menudeo = regular.menudeo ?: regular.precio
        val porPresentacion = menudeo.presentacionId != null
        val unidades = if (porPresentacion) cantidad else cantidad.por(presentacion.factor)
        val importeMenudeo = unidades.importe(menudeo.precioCentavos)
        val conMenudeo = PrecioAplicado(menudeo, importeMenudeo, menudeo = menudeo, importeMenudeoCentavos = importeMenudeo)
        // Lo que vale la línea a ese precio: la promoción tiene que quedar debajo de eso y del mayoreo; con el descuento del
        // cliente, del mayoreo con su descuento (como lo descuenta la venta).
        val importeDeLaLinea = cantidad.importe(conMenudeo.precioPorPresentacion(presentacion))
        var mejor: PromocionDeProducto? = null
        var importeMejor = min(regular.importeCentavos, importeDeLaLinea)
        if (descuentoDelCliente != null && descuentoDelCliente > 0) {
            importeMejor = min(
                importeMejor,
                regular.importeCentavos - Redondeo.dividirProducto(regular.importeCentavos, descuentoDelCliente.toLong(), 10_000),
            )
        }
        val corren = promociones.filter { it.aplicaEl(dia) }.sortedWith { a, b -> Ids.comparar(a.promocionId, b.promocionId) }
        for (promocion in corren) {
            val importe = importeCon(promocion, menudeo.precioCentavos, unidades, importeMenudeo, cantidad.por(presentacion.factor))
            if (importe != null && importe > 0 && importe < importeMejor) {
                mejor = promocion
                importeMejor = importe
            }
        }
        return if (mejor == null) {
            regular
        } else {
            conMenudeo.copy(importeCentavos = importeMejor, promocion = mejor, ahorroPromocionCentavos = importeDeLaLinea - importeMejor)
        }
    }

    /**
     * El precio por unidad base más bajo al que la promoción deja un producto, con el mismo cálculo que la venta: con su grupo
     * completo (3 en un 3×2, 2 en el segundo a mitad de precio) sobre el menudeo de cada grupo de precios con que lo vende (el
     * de la unidad base y el propio de cada presentación que lo tiene y que la venta usa: el que no cuesta más que sus
     * piezas), el más bajo. Una caja de 12 a $120 de un producto de $12 la pieza baja con un 3×2 a $6.67 la pieza, no a $8.
     * Null si no lo baja en ninguno (o no tiene precio): entonces no lo deja debajo de nada. Con esto el POS revisa el costo
     * al darla de alta y al llevarla a las cajas.
     *
     * @return En centavos por unidad base, con los decimales de un `decimal` de .NET (666.66666666666666666666666667).
     */
    fun precioMasBajoCon(
        promocion: PromocionDeProducto,
        precios: Iterable<Precio>,
        presentaciones: Iterable<Presentacion>,
        sucursalId: String,
    ): BigDecimal? {
        val lista = precios.toList()
        val menudeoPorUnidad = deSucursalOBase(lista.filter { it.presentacionId == null }, sucursalId)
            .minByOrNull { it.cantidadMinima }?.precioCentavos
        var masBajo = if (menudeoPorUnidad != null) porUnidadBase(promocion, menudeoPorUnidad, Cantidad.piezas(1)) else null
        for (presentacion in presentaciones.filter { it.factor.esPositiva }) {
            val propias = deSucursalOBase(lista.filter { Ids.iguales(it.presentacionId, presentacion.id) }, sucursalId)
            // La venta usa su precio propio solo si alguna de sus escalas no cuesta más que sus piezas a menudeo.
            if (propias.isEmpty() ||
                menudeoPorUnidad != null && propias.minOf { it.precioCentavos } > presentacion.factor.importe(menudeoPorUnidad)
            ) {
                continue
            }
            val propio = porUnidadBase(promocion, propias.minBy { it.cantidadMinima }.precioCentavos, presentacion.factor)
            if (propio != null && (masBajo == null || propio < masBajo)) masBajo = propio
        }
        return masBajo
    }

    /**
     * Un grupo completo de la promoción sobre el menudeo de un grupo de precios, por unidad base; null si no lo baja.
     *
     * @param factor Las unidades base de cada unidad del grupo: una en el de la unidad base; las de la presentación en uno propio.
     */
    private fun porUnidadBase(promocion: PromocionDeProducto, menudeo: Long, factor: Cantidad): BigDecimal? {
        val grupo = when (promocion.tipo) {
            TipoPromocion.LLEVA_N_PAGA_M -> max(1, promocion.lleva)
            TipoPromocion.SEGUNDO_A_MITAD -> 2
            else -> 1
        }
        val unidades = Cantidad.piezas(grupo.toLong())
        val importe = unidades.importe(menudeo)
        val unidadesBase = unidades.por(factor)
        // Como la venta: la promoción solo se aplica si deja menos que el menudeo.
        val conPromocion = importeCon(promocion, menudeo, unidades, importe, unidadesBase) ?: return null
        return if (conPromocion > 0 && conPromocion < importe) comoDecimal(conPromocion, unidadesBase) else null
    }

    /**
     * Lo que cuesta la cantidad con esa promoción; null si no se puede aplicar (le falta lo de su tipo).
     *
     * @param precioMenudeo El menudeo, por cada una de [unidades].
     * @param unidades En lo que cuenta el menudeo: unidades base, o presentaciones con precio propio.
     * @param unidadesBase Para el precio especial, que es por unidad base.
     */
    private fun importeCon(
        p: PromocionDeProducto,
        precioMenudeo: Long,
        unidades: Cantidad,
        importeMenudeo: Long,
        unidadesBase: Cantidad,
    ): Long? = when (p.tipo) {
        TipoPromocion.PORCENTAJE ->
            if (p.centesimas in 1..9_999) importeMenudeo - Redondeo.dividirProducto(importeMenudeo, p.centesimas.toLong(), 10_000) else null
        TipoPromocion.PRECIO_ESPECIAL -> {
            val especial = p.precioCentavos
            if (especial != null && especial > 0) unidadesBase.importe(especial) else null
        }
        TipoPromocion.LLEVA_N_PAGA_M ->
            if (p.paga >= 1 && p.lleva > p.paga) {
                // De cada N completas (se cuentan las enteras: 3.5 m son un grupo de 3), N − M van gratis.
                val grupos = unidades.milesimas / (p.lleva * Cantidad.ESCALA)
                importeMenudeo - Cantidad.piezas(grupos * (p.lleva - p.paga)).importe(precioMenudeo)
            } else {
                null
            }
        TipoPromocion.SEGUNDO_A_MITAD -> {
            val pares = unidades.milesimas / (2 * Cantidad.ESCALA)
            importeMenudeo - Redondeo.dividir(Cantidad.piezas(pares).importe(precioMenudeo), 2)
        }
    }

    /** Las escalas de una presentación en la sucursal, con la misma regla que [calcular] (el F11 de la caja las muestra). */
    fun escalas(precios: Iterable<Precio>, sucursalId: String, presentacion: Presentacion): EscalasDePrecio {
        val deLaVariante = precios.filter { Ids.iguales(it.varianteId, presentacion.varianteId) }
        val propias = deSucursalOBase(deLaVariante.filter { Ids.iguales(it.presentacionId, presentacion.id) }, sucursalId)
        return if (propias.isNotEmpty()) {
            EscalasDePrecio(true, propias.sortedBy { it.cantidadMinima })
        } else {
            EscalasDePrecio(false, deSucursalOBase(deLaVariante.filter { it.presentacionId == null }, sucursalId).sortedBy { it.cantidadMinima })
        }
    }

    private fun deSucursalOBase(precios: List<Precio>, sucursalId: String): List<Precio> {
        val deSucursal = precios.filter { Ids.iguales(it.sucursalId, sucursalId) }
        return if (deSucursal.isNotEmpty()) deSucursal else precios.filter { it.sucursalId == null }
    }

    /**
     * La escala más alta que alcanza la cantidad; si no alcanza ninguna (medio metro), la más baja. Con mayoreo siempre,
     * nunca menos que la primera de mayoreo (la segunda escala). Con las escalas en orden (cada una más barata que la
     * anterior) es la más barata de las que alcanza, y eso se toma: si una quedó fuera de orden (más cara que el menudeo o
     * que una de menos piezas), no se cobra más por llevar más.
     */
    private fun elegirEscala(escalas: List<Precio>, cantidad: Cantidad, mayoreoSiempre: Boolean): Precio? {
        val abs = cantidad.abs()
        val alcanzadas = escalas.filterTo(ArrayList()) { it.cantidadMinima <= abs }
        if (mayoreoSiempre) {
            val primeraDeMayoreo = escalas.sortedBy { it.cantidadMinima }.getOrNull(1)
            // La misma, no una igual: como el POS, que compara las referencias.
            if (primeraDeMayoreo != null && alcanzadas.none { it === primeraDeMayoreo }) alcanzadas.add(primeraDeMayoreo)
        }
        // Entre dos al mismo precio, la de más piezas, como antes. Entre dos iguales en todo, la primera.
        return alcanzadas.minWithOrNull(compareBy<Precio> { it.precioCentavos }.thenByDescending { it.cantidadMinima })
            ?: escalas.minByOrNull { it.cantidadMinima }
    }

    /**
     * [centavos] ÷ [unidades] como lo divide un `decimal` de .NET: con todas las cifras que quepan en sus 96 bits, hasta 28
     * decimales, redondeando la última al par, y sin ceros de más. Así el precio más bajo es el mismo número que en el POS,
     * que lo compara con el costo.
     */
    private fun comoDecimal(centavos: Long, unidades: Cantidad): BigDecimal {
        // centavos ÷ (milésimas / 1000): las milésimas entre mil son exactas en un decimal.
        val numerador = BigInteger.valueOf(centavos).multiply(BigInteger.valueOf(Cantidad.ESCALA))
        val denominador = BigInteger.valueOf(unidades.milesimas)
        var escala = MAXIMO_DE_DECIMALES
        while (escala > 0 && numerador.multiply(BigInteger.TEN.pow(escala)).divide(denominador).abs().bitLength() > 96) escala--
        var cociente = BigDecimal(numerador).divide(BigDecimal(denominador), escala, RoundingMode.HALF_EVEN)
        // Al redondear hacia arriba puede llegar a 2⁹⁶: entonces una cifra menos.
        if (cociente.unscaledValue().abs().bitLength() > 96) {
            cociente = BigDecimal(numerador).divide(BigDecimal(denominador), escala - 1, RoundingMode.HALF_EVEN)
        }
        var cifras = cociente.unscaledValue()
        var decimales = cociente.scale()
        while (decimales > 0) {
            val (sinUna, sobra) = cifras.divideAndRemainder(BigInteger.TEN)
            if (sobra.signum() != 0) break
            cifras = sinUna
            decimales--
        }
        return BigDecimal(cifras, decimales)
    }

    /** Los que admite un `decimal` de .NET. */
    private const val MAXIMO_DE_DECIMALES = 28
}
