package mx.papeleriapos.pos.precio

import mx.papeleriapos.pos.Cantidad

// Del catálogo del POS, solo lo que lee el cálculo del precio. Los Id son los Guid del POS como texto (ver Ids).

/**
 * Precio con IVA incluido. Lo normal es por unidad base de la variante (por pieza, metro, kilo); con [presentacionId] es
 * el precio propio de una presentación, que es raro.
 *
 * @param cantidadMinima Desde qué cantidad aplica: la más baja es menudeo, las demás son escalas de mayoreo. En unidades
 * base; en un precio propio, en presentaciones.
 * @param presentacionId Null: precio por unidad base. Con valor: precio por cada presentación.
 * @param sucursalId Null: precio base, para todas las sucursales.
 */
data class Precio(
    val varianteId: String,
    val precioCentavos: Long,
    val cantidadMinima: Cantidad = Cantidad.piezas(1),
    val presentacionId: String? = null,
    val sucursalId: String? = null,
)

/**
 * Forma de venta: pieza, paquete de 12, rollo de 50 m.
 *
 * @param factor Unidades base que contiene. La presentación base tiene factor de una unidad.
 */
data class Presentacion(
    val id: String,
    val varianteId: String,
    val factor: Cantidad = Cantidad.piezas(1),
)

/**
 * Los Id del POS son Guid; aquí viajan como su texto («0190c5a2-7f3b-7c1e-9a4d-…»). Se comparan sin importar mayúsculas,
 * como dos Guid, y se ordenan como .NET (`Guid.CompareTo`): campo por campo y sin signo, que con el texto de 8-4-4-4-12
 * cifras es el orden del texto. No como `java.util.UUID`, que compara con signo y pondría «8…» antes que «7…»: entre dos
 * promociones que dejan lo mismo, la caja y el celular tienen que quedarse con la misma.
 */
internal object Ids {
    fun iguales(a: String?, b: String): Boolean = a != null && a.equals(b, ignoreCase = true)

    fun comparar(a: String, b: String): Int = a.compareTo(b, ignoreCase = true)
}
