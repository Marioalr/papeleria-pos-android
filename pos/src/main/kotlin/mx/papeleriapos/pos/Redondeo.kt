package mx.papeleriapos.pos

import java.math.BigInteger
import kotlin.math.abs

/**
 * Los redondeos del POS (`Redondeo` de su núcleo), con los mismos resultados. Allá el numerador es un `Int128` para que el
 * producto de dos importes o cantidades no se desborde; aquí va en `Long` mientras quepa y en [BigInteger] cuando no. El
 * resultado sí tiene que caber en un `Long`: si no, [ArithmeticException], como el `checked` del POS.
 */
object Redondeo {
    /** Divide redondeando al entero más cercano; los medios se alejan de cero (1.5 → 2, −1.5 → −2). */
    fun dividir(numerador: Long, denominador: Long): Long {
        if (denominador == 0L) throw ArithmeticException("División entre cero.")
        // −2⁶³ no tiene positivo en un Long: esa va por BigInteger, como en el Int128 del POS.
        if (numerador == Long.MIN_VALUE || denominador == Long.MIN_VALUE) {
            return dividir(BigInteger.valueOf(numerador), BigInteger.valueOf(denominador))
        }
        val negativo = (numerador < 0) != (denominador < 0)
        val n = abs(numerador)
        val d = abs(denominador)
        // Es (n + d / 2) / d, sin la suma que podría desbordarse: uno más si lo que sobra llega a la mitad.
        val resultado = n / d + if (n % d >= d - d / 2) 1 else 0
        return if (negativo) -resultado else resultado
    }

    /** [dividir] con un numerador de cualquier tamaño (el `Int128` del POS); el resultado tiene que caber en un `Long`. */
    fun dividir(numerador: BigInteger, denominador: BigInteger): Long {
        if (denominador.signum() == 0) throw ArithmeticException("División entre cero.")
        val negativo = (numerador.signum() < 0) != (denominador.signum() < 0)
        val d = denominador.abs()
        val resultado = numerador.abs().add(d.shiftRight(1)).divide(d)
        return aLong(if (negativo) resultado.negate() else resultado)
    }

    /**
     * a × b ÷ [denominador], redondeado como [dividir]: lo que el POS escribe `Redondeo.Dividir((Int128)a * b, d)`. El
     * producto de dos `Long` puede no caber en uno; entonces se hace con [BigInteger].
     */
    fun dividirProducto(a: Long, b: Long, denominador: Long): Long =
        if (a in -RAIZ_DEL_MAXIMO..RAIZ_DEL_MAXIMO && b in -RAIZ_DEL_MAXIMO..RAIZ_DEL_MAXIMO) {
            dividir(a * b, denominador)
        } else {
            dividir(BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)), BigInteger.valueOf(denominador))
        }

    /** Al peso más cercano: $37.49 → $37, $37.50 → $38 (fase 3, el cobro en efectivo). */
    fun alPeso(centavos: Long): Long = dividir(centavos, 100) * 100

    /** ⌊√(2⁶³ − 1)⌋: dos números que no pasan de esto, en valor absoluto, se multiplican sin salirse de un `Long`. */
    private const val RAIZ_DEL_MAXIMO = 3_037_000_499L

    // Sin longValueExact(), que en Android es más nueva que la API 24.
    private fun aLong(valor: BigInteger): Long {
        if (valor.bitLength() > 63) throw ArithmeticException("El resultado no cabe en un Long.")
        return valor.toLong()
    }
}
