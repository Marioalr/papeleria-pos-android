package mx.papeleriapos.pos

import java.math.BigDecimal
import kotlin.math.abs

/**
 * Cantidad en milésimas de la unidad base: 1 pieza = 1000, 1.5 m = 1500, 0.250 kg = 250. Se guarda como entero para que
 * las sumas sean exactas, como `Cantidad` del POS. Sumar, restar y multiplicar no se desbordan en silencio: si el
 * resultado no cabe, [ArithmeticException] (el `checked` del POS).
 */
@JvmInline
value class Cantidad(val milesimas: Long) : Comparable<Cantidad> {
    /**
     * En unidades, con los decimales que hacen falta y ninguno de más (1.5, 3, 0.25), como el `decimal` del POS: allá
     * la división entre mil quita los ceros del final, y así se enseñan y se suman igual («5» artículos, no «5.000»).
     * Para comparar, `compareTo`: el `equals` de BigDecimal cuenta los decimales (3.0 no es 3), el `==` del POS no.
     */
    val enUnidades: BigDecimal
        get() {
            // Los ceros se quitan del Long: stripTrailingZeros() deja 30 como 3E+1, y en Android el cero como 0.000.
            var cifras = milesimas
            var decimales = 3
            while (decimales > 0 && cifras % 10 == 0L) {
                cifras /= 10
                decimales--
            }
            return BigDecimal.valueOf(cifras, decimales)
        }

    val esEntera: Boolean get() = milesimas % ESCALA == 0L

    val esPositiva: Boolean get() = milesimas > 0

    fun abs(): Cantidad = Cantidad(if (milesimas < 0) Math.negateExact(milesimas) else milesimas)

    /** Cantidad de presentaciones × su factor = cantidad en unidades base (1.5 paquetes × 12 = 18 piezas). */
    fun por(factor: Cantidad): Cantidad = Cantidad(Redondeo.dividirProducto(milesimas, factor.milesimas, ESCALA))

    /** Importe de esta cantidad a un precio por unidad, redondeado al centavo. */
    fun importe(centavosPorUnidad: Long): Long = Redondeo.dividirProducto(centavosPorUnidad, milesimas, ESCALA)

    override fun compareTo(other: Cantidad): Int = milesimas.compareTo(other.milesimas)

    operator fun plus(otra: Cantidad): Cantidad = Cantidad(Math.addExact(milesimas, otra.milesimas))

    operator fun minus(otra: Cantidad): Cantidad = Cantidad(Math.subtractExact(milesimas, otra.milesimas))

    operator fun unaryMinus(): Cantidad = Cantidad(Math.negateExact(milesimas))

    /** Sin ceros de más, con punto: «1.5», «12», «0.25», «-2» (el «0.###» del POS). */
    override fun toString(): String {
        val enteros = milesimas / ESCALA
        val fraccion = abs(milesimas % ESCALA)
        if (fraccion == 0L) return enteros.toString()
        // −0.5: los enteros son 0 y el signo no saldría.
        val signo = if (milesimas < 0 && enteros == 0L) "-" else ""
        return signo + enteros + "." + fraccion.toString().padStart(3, '0').trimEnd('0')
    }

    companion object {
        const val ESCALA = 1000L

        val CERO = Cantidad(0)

        /** Las unidades con hasta tres decimales (1.5 m, 0.250 kg); con más, [IllegalArgumentException]. */
        fun unidades(unidades: BigDecimal): Cantidad {
            val milesimas = unidades.multiply(BigDecimal.valueOf(ESCALA))
            require(milesimas.remainder(BigDecimal.ONE).signum() == 0) {
                "La cantidad ${unidades.toPlainString()} tiene más de tres decimales."
            }
            return Cantidad(milesimas.longValueExact())
        }

        fun piezas(piezas: Long): Cantidad = Cantidad(Math.multiplyExact(piezas, ESCALA))
    }
}
