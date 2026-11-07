package mx.papeleriapos.pos.precio

// Las promociones con fechas (fase 3 del POS, docs/fase-3-precios-clientes.md): se aplican solas, no se acumulan (se queda
// la que más le conviene al cliente, también contra el mayoreo). Viajan a las cajas con la copia del catálogo, con sus
// fechas: cada caja decide con su fecha, también sin conexión y a medianoche. Aquí, solo lo que cambia el precio de una
// línea; darlas de alta, revisarlas y guardarlas es del POS.

/** Cómo baja el precio una promoción. En el orden del POS: su número es el mismo. */
enum class TipoPromocion {
    /** Lleva N y paga M (3×2): de cada N iguales, N − M van gratis. Cuenta cada producto aparte, como el mayoreo. */
    LLEVA_N_PAGA_M,

    /** El segundo a mitad de precio: de cada dos iguales, uno va a la mitad. */
    SEGUNDO_A_MITAD,

    /** Un porcentaje menos sobre el menudeo. */
    PORCENTAJE,

    /** Un precio por unos días, el suyo de cada producto: dejar al costo lo estratégico en el regreso a clases. */
    PRECIO_ESPECIAL,
}

/**
 * Los días de la semana en que corre una promoción, entre sus fechas. Son banderas con los valores del POS (lunes 1,
 * martes 2 … domingo 64), así que el número que trae la copia del catálogo se usa tal cual.
 */
@JvmInline
value class DiasDeLaSemana(val valor: Int) {
    infix fun or(otros: DiasDeLaSemana): DiasDeLaSemana = DiasDeLaSemana(valor or otros.valor)

    /** Si el día de la semana de esa fecha es uno de estos. */
    fun incluye(dia: Fecha): Boolean = (valor and de(dia).valor) != 0

    companion object {
        val NINGUNO = DiasDeLaSemana(0)
        val LUNES = DiasDeLaSemana(1)
        val MARTES = DiasDeLaSemana(2)
        val MIERCOLES = DiasDeLaSemana(4)
        val JUEVES = DiasDeLaSemana(8)
        val VIERNES = DiasDeLaSemana(16)
        val SABADO = DiasDeLaSemana(32)
        val DOMINGO = DiasDeLaSemana(64)
        val TODOS = DiasDeLaSemana(127)

        /** El de esa fecha: lunes, martes… */
        fun de(dia: Fecha): DiasDeLaSemana = DiasDeLaSemana(1 shl (dia.diaDeLaSemana - 1))
    }
}

/**
 * Una promoción como la ve el cálculo del precio para un producto: lo de su tipo, sus fechas y sus días. Así la aplican
 * igual la pantalla de la caja, su cobro sin conexión, el servicio y el celular ([CalculoPrecio.calcular]).
 *
 * @param promocionId Su Id (el Guid, como texto): entre dos que dejan lo mismo se queda la de menor Id.
 * @param nombre Lo que dice el ticket, junto a lo que se ahorró.
 * @param lleva De «lleva N y paga M»: cuántas lleva (N). En las demás, cero.
 * @param paga De «lleva N y paga M»: cuántas paga (M). En las demás, cero.
 * @param centesimas Del porcentaje: en centésimas (1000 = 10 %). En las demás, cero.
 * @param precioCentavos De un precio especial: el de este producto, por unidad base y con IVA.
 * @param desde El primer día en que corre (hora de México).
 * @param hasta El último día en que corre; null: hasta que se termine.
 */
data class PromocionDeProducto(
    val promocionId: String,
    val nombre: String,
    val tipo: TipoPromocion,
    val lleva: Int = 0,
    val paga: Int = 0,
    val centesimas: Int = 0,
    val precioCentavos: Long? = null,
    val desde: Fecha,
    val hasta: Fecha? = null,
    val dias: DiasDeLaSemana = DiasDeLaSemana.TODOS,
) {
    /** Si corre ese día (la fecha de la caja o la del cobro, en hora de México). */
    fun aplicaEl(dia: Fecha): Boolean = dia >= desde && (hasta == null || dia <= hasta) && dias.incluye(dia)
}
