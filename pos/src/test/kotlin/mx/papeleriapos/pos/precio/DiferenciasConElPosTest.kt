package mx.papeleriapos.pos.precio

import mx.papeleriapos.pos.Cantidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Las sospechas de diferencias del port con el núcleo del POS, una prueba por cada una: lo que al portarse a mano podría
 * quedar distinto (los redondeos y su signo, el orden y los empates de las escalas, el precio propio contra sus piezas, la
 * promoción que gana y sus empates, los desbordes y la división del `decimal`). Lo esperado es lo que da el POS: se
 * razonó con su código (cada prueba cita el archivo y la línea, en src/PapeleriaPos.Nucleo) y se comprobó corriéndolo con
 * estos mismos números. Las que pasan quedan de regresión.
 */
class DiferenciasConElPosTest {
    private val cuaderno = nuevoId()
    private val pieza = Presentacion(nuevoId(), cuaderno)
    private val docena = Presentacion(nuevoId(), cuaderno, Cantidad.piezas(12))

    /** El rollo de metro y medio de un producto que se vende por metro. */
    private val rollo = Presentacion(nuevoId(), cuaderno, unidades("1.5"))

    private fun porPieza(centavos: Long, desde: Long = 1, sucursal: String? = null) =
        Precio(cuaderno, centavos, Cantidad.piezas(desde), sucursalId = sucursal)

    private fun propio(presentacion: Presentacion, centavos: Long, desde: Long = 1) =
        Precio(cuaderno, centavos, Cantidad.piezas(desde), presentacionId = presentacion.id)

    private fun regla(
        tipo: TipoPromocion,
        lleva: Int = 0,
        paga: Int = 0,
        centesimas: Int = 0,
        precio: Long? = null,
        dias: DiasDeLaSemana = DiasDeLaSemana.TODOS,
        id: String = nuevoId(),
        nombre: String = "x",
    ) = PromocionDeProducto(id, nombre, tipo, lleva, paga, centesimas, precio, HOY.masDias(-10), HOY.masDias(10), dias)

    private fun calcular(
        precios: List<Precio>,
        presentacion: Presentacion,
        cantidad: Cantidad,
        vararg promociones: PromocionDeProducto,
        mayoreoSiempre: Boolean = false,
        descuento: Int? = null,
        sucursal: String = MATRIZ,
        dia: Fecha? = HOY,
    ): PrecioAplicado? =
        CalculoPrecio.calcular(precios, sucursal, presentacion, cantidad, mayoreoSiempre, promociones.toList(), dia, descuento)

    /** Lo que se compara de una línea, como lo imprime el POS: la escala y su importe, el menudeo, el mayoreo y la promoción. */
    private data class Linea(
        val precio: Long,
        val importe: Long,
        val menudeo: Long?,
        val importeMenudeo: Long,
        val esMayoreo: Boolean = false,
        val ahorro: Long = 0,
        val promocion: String? = null,
        val ahorroPromocion: Long = 0,
    )

    private fun linea(a: PrecioAplicado?): Linea? = a?.let {
        Linea(
            it.precio.precioCentavos, it.importeCentavos, it.menudeo?.precioCentavos, it.importeMenudeoCentavos, it.esMayoreo,
            it.ahorroCentavos, it.promocion?.nombre, it.ahorroPromocionCentavos,
        )
    }

    // ── Cantidad y los redondeos ──

    /**
     * Cantidad.cs:25: `EnUnidades => (decimal)Milesimas / Escala`. La división del `decimal` de .NET deja el número sin ceros
     * de más (1.5, 3, 0.25), y así lo suma la venta en sus artículos (Venta.cs:100: 3 + 2 piezas son 5). Dos `decimal` con
     * otros ceros son iguales (`1.5m == 1.500m`); dos BigDecimal no, porque su `equals` cuenta la escala.
     */
    @Test
    fun `en unidades sale sin ceros de mas como el decimal del POS`() {
        val cantidades = listOf(unidades("1.5"), Cantidad.piezas(3), unidades("0.25"), Cantidad(1), unidades("-0.5"), Cantidad.piezas(12))

        assertEquals(
            listOf("1.5", "3", "0.25", "0.001", "-0.5", "12", "5", "true"),
            cantidades.map { it.enUnidades.toString() } +
                (Cantidad.piezas(3).enUnidades + Cantidad.piezas(2).enUnidades).toString() +
                (unidades("1.5").enUnidades == BigDecimal("1.5")).toString(),
        )
    }

    /**
     * Cantidad.cs:37 divide con `Redondeo.Dividir` (Redondeo.cs:11-15): el signo aparte y el medio hacia arriba en valor
     * absoluto, así que en una devolución el medio centavo se aleja de cero (−0.5 → −1) en vez de subir a cero.
     */
    @Test
    fun `el medio centavo de una devolucion se aleja de cero`() {
        assertEquals(
            listOf(-1L, -1L, 0L, 1L),
            listOf(Cantidad(-125).importe(4), Cantidad(-1).importe(500), Cantidad(-1).importe(499), Cantidad(125).importe(4)),
        )
    }

    /** Cantidad.cs:34: por el factor también se redondea el medio milésimo alejándose de cero (0.333 × 1.5 = 0.4995 → 0.5). */
    @Test
    fun `por un factor con fracciones el medio milesimo se aleja de cero`() {
        assertEquals(unidades("0.5"), unidades("0.333").por(unidades("1.5")))
        assertEquals(unidades("-0.5"), unidades("-0.333").por(unidades("1.5")))
    }

    // ── La escala que aplica ──

    /**
     * CalculoPrecio.cs:232-233 elige la escala con el valor absoluto: devolver 12 piezas va a la de 12 ($6.50). Pero
     * `EsMayoreo` (CalculoPrecio.cs:12) pide `ImporteMenudeoCentavos > ImporteCentavos`, y con importes negativos (−$96
     * contra −$78) es falso: la devolución no dice mayoreo ni ahorro.
     */
    @Test
    fun `una devolucion va a la escala de su cantidad pero no dice mayoreo`() {
        val mayoreo = porPieza(650, desde = 12)

        val aplicado = calcular(listOf(porPieza(800), mayoreo), pieza, Cantidad.piezas(-12))

        assertSame(mayoreo, aplicado!!.precio)
        assertEquals(Linea(650, -7800, 800, -9600), linea(aplicado))
    }

    /**
     * CalculoPrecio.cs:234: la primera de mayoreo sale de ordenar por la cantidad mínima (`OrderBy`, estable) y saltarse la
     * primera, no es la segunda de la lista. Con dos escalas desde la misma cantidad, la que llegó antes, aunque la otra sea
     * más barata; si la cantidad llega a ellas, la más barata (CalculoPrecio.cs:238).
     */
    @Test
    fun `con el mayoreo siempre la primera de mayoreo es la de menos piezas, en cualquier orden`() {
        val menudeo = porPieza(800)
        val desordenadas = listOf(menudeo, porPieza(600, desde = 24), porPieza(700, desde = 12))
        assertEquals(
            Linea(700, 700, 800, 800, esMayoreo = true, ahorro = 100),
            linea(calcular(desordenadas, pieza, Cantidad.piezas(1), mayoreoSiempre = true)),
        )

        val a = porPieza(700, desde = 12)
        val b = porPieza(650, desde = 12)
        assertSame(a, calcular(listOf(menudeo, a, b), pieza, Cantidad.piezas(1), mayoreoSiempre = true)!!.precio)
        assertSame(b, calcular(listOf(menudeo, b, a), pieza, Cantidad.piezas(1), mayoreoSiempre = true)!!.precio)
        assertSame(b, calcular(listOf(menudeo, a, b), pieza, Cantidad.piezas(12))!!.precio)
    }

    /**
     * CalculoPrecio.cs:238: `OrderBy(precio).ThenByDescending(cantidad).FirstOrDefault()` es estable: entre dos escalas
     * iguales en todo (dos renglones, otra referencia) se queda la primera de la lista. El menudeo (`MinBy`,
     * CalculoPrecio.cs:97) también se queda con la primera, y las escalas (CalculoPrecio.cs:214) guardan el orden.
     */
    @Test
    fun `entre escalas iguales en todo se queda la primera de la lista`() {
        val x = porPieza(800)
        val y = porPieza(800)
        val p = porPieza(700, desde = 12)
        val q = porPieza(700, desde = 12)

        assertSame(x, calcular(listOf(x, y, p, q), pieza, Cantidad.piezas(1))!!.precio)
        val doce = calcular(listOf(x, y, p, q), pieza, Cantidad.piezas(12))!!
        assertEquals(Linea(700, 8400, 800, 9600, esMayoreo = true, ahorro = 1200), linea(doce))
        assertSame(p, doce.precio)
        assertSame(x, doce.menudeo)
        val alReves = calcular(listOf(y, x, q, p), pieza, Cantidad.piezas(12))!!
        assertSame(q, alReves.precio)
        assertSame(y, alReves.menudeo)
        val escalas = CalculoPrecio.escalas(listOf(x, y, p, q), MATRIZ, pieza).escalas
        assertTrue(escalas.size == 4 && escalas[0] === x && escalas[1] === y && escalas[2] === p && escalas[3] === q)
    }

    /**
     * Lo del POS que el port copia: «nunca más que a menudeo» no se cumple con el mayoreo siempre por debajo de la cantidad
     * del menudeo. Medio metro no alcanza el menudeo (desde 1 m, CalculoPrecio.cs:233), el mayoreo siempre agrega la primera
     * de mayoreo (CalculoPrecio.cs:234-236), que quedó más cara ($9), y es la única que alcanza: $4.50 en vez de $4.00.
     */
    @Test
    fun `con el mayoreo siempre debajo del menudeo se cobra la primera de mayoreo aunque sea mas cara, como el POS`() {
        val metro = Presentacion(nuevoId(), cuaderno)
        val precios = listOf(porPieza(800), porPieza(900, desde = 12))

        assertEquals(Linea(900, 450, 800, 400), linea(calcular(precios, metro, unidades("0.5"), mayoreoSiempre = true)))
        assertEquals(Linea(800, 400, 800, 400), linea(calcular(precios, metro, unidades("0.5"))))
        assertEquals(Linea(800, 800, 800, 800), linea(calcular(precios, metro, Cantidad.piezas(1), mayoreoSiempre = true)))
    }

    // ── El precio propio de la presentación ──

    /**
     * CalculoPrecio.cs:83-84: el precio propio se compara con sus piezas a menudeo (el `MinBy` por cantidad de las de la
     * unidad base), no a mayoreo: la docena con precio propio de $70 se cobra a $70 aunque 12 piezas a la escala de 12 ($5)
     * salgan en $60.
     */
    @Test
    fun `el precio propio se compara con el menudeo de sus piezas, no con su mayoreo`() {
        val deLaDocena = propio(docena, 7000)

        val aplicado = calcular(listOf(deLaDocena, porPieza(800), porPieza(500, desde = 12)), docena, Cantidad.piezas(1))

        assertSame(deLaDocena, aplicado!!.precio)
        assertEquals(Linea(7000, 7000, 7000, 7000), linea(aplicado))
    }

    /**
     * CalculoPrecio.cs:84 compara con `presentacion.Factor.Importe(menudeo)`, redondeado como todo importe: metro y medio a
     * $3.33 son $4.995 → $5.00. El rollo con precio propio de $5.00 no cuesta más que sus metros y va a su precio propio; el
     * de $5.01 se cobra por sus metros, también $5.00.
     */
    @Test
    fun `el precio propio se compara con sus piezas redondeadas al centavo`() {
        val porMetro = porPieza(333)
        val deCinco = propio(rollo, 500)
        val deCincoUno = propio(rollo, 501)

        assertSame(deCinco, calcular(listOf(deCinco, porMetro), rollo, Cantidad.piezas(1))!!.precio)
        val porSusMetros = calcular(listOf(deCincoUno, porMetro), rollo, Cantidad.piezas(1))!!
        assertSame(porMetro, porSusMetros.precio)
        assertEquals(500L, porSusMetros.importeCentavos)
    }

    /**
     * CalculoPrecio.cs:212-214: las escalas que enseña el F11 son las propias de la presentación si tiene, aunque el cálculo
     * (CalculoPrecio.cs:82-84) la cobre por sus piezas porque su precio propio cuesta más.
     */
    @Test
    fun `las escalas de la presentacion son las propias aunque se cobre por sus piezas`() {
        val deLaDocena = propio(docena, 15000)
        val precios = listOf(deLaDocena, porPieza(1200))

        val escalas = CalculoPrecio.escalas(precios, MATRIZ, docena)

        assertTrue(escalas.dePresentacion)
        assertSame(deLaDocena, escalas.escalas.single())
        assertEquals(Linea(1200, 14400, 1200, 14400), linea(calcular(precios, docena, Cantidad.piezas(1))))
    }

    /**
     * CalculoPrecio.cs:79-80: lo de la sucursal se escoge aparte para los precios propios y para los de la unidad base. La
     * docena con precio propio base de $70 y la pieza a $5 en la sucursal 2 (base $8): allá la docena cuesta más que sus
     * piezas ($60) y se cobra por ellas; en la matriz, a su precio propio.
     */
    @Test
    fun `la sucursal se escoge aparte para el precio propio y para las piezas`() {
        val precios = listOf(propio(docena, 7000), porPieza(800), porPieza(500, sucursal = SUCURSAL_2))

        assertEquals(Linea(500, 6000, 500, 6000), linea(calcular(precios, docena, Cantidad.piezas(1), sucursal = SUCURSAL_2)))
        assertEquals(Linea(7000, 7000, 7000, 7000), linea(calcular(precios, docena, Cantidad.piezas(1))))
    }

    // ── Las promociones: cuál gana y sus empates ──

    /**
     * CalculoPrecio.cs:125-126 y 130: con el descuento del cliente la promoción tiene que dejar menos (estricto) que el
     * importe con su descuento, redondeado como la venta. 3 piezas de $10 con 33.33 % (y con 33.34 %) son $30 − $10.00 =
     * $20, lo mismo que el 3×2: se queda el descuento, que no descuenta el cálculo sino la venta. Con 33.30 % ($20.01) gana
     * el 3×2.
     */
    @Test
    fun `en un empate con el descuento del cliente se queda el descuento`() {
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2, nombre = "3×2")
        val precios = listOf(porPieza(1000))

        for (descuento in listOf(3333, 3334)) {
            assertEquals("$descuento", Linea(1000, 3000, 1000, 3000), linea(calcular(precios, pieza, Cantidad.piezas(3), tresPorDos, descuento = descuento)))
        }
        assertEquals(
            Linea(1000, 2000, 1000, 3000, promocion = "3×2", ahorroPromocion = 1000),
            linea(calcular(precios, pieza, Cantidad.piezas(3), tresPorDos, descuento = 3330)),
        )
    }

    /**
     * CalculoPrecio.cs:122 y 124: la promoción tiene que dejar menos que el regular (4.5 m × $3.33 = $14.985 → $14.99) y que
     * la línea a su precio por presentación redondeado ($5.00 el rollo de 1.5 m, por 3 = $15.00). Un 0.01 % deja $14.99: no
     * baja nada. Un 0.04 % deja $14.98, y su ahorro es contra la línea (CalculoPrecio.cs:135): $15.00 − $14.98. Con rollos
     * de 1.25 m la línea queda debajo del regular (3 × $4.16 = $12.48 contra 3.75 m × $3.33 = $12.4875 → $12.49): un 0.05 %
     * o un 0.12 % dejan $12.48, que no es menos que la línea, y se queda el regular de $12.49; un 0.13 % deja $12.47.
     */
    @Test
    fun `la promocion tiene que quedar debajo del regular y de la linea redondeada`() {
        val precios = listOf(porPieza(333))
        fun porcentaje(centesimas: Int) = regla(TipoPromocion.PORCENTAJE, centesimas = centesimas, nombre = "$centesimas")

        assertEquals(Linea(333, 1499, 333, 1499), linea(calcular(precios, rollo, Cantidad.piezas(3), porcentaje(1))))
        val baja = calcular(precios, rollo, Cantidad.piezas(3), porcentaje(4))!!
        assertEquals(Linea(333, 1498, 333, 1499, promocion = "4", ahorroPromocion = 2), linea(baja))
        assertEquals(500L, baja.precioPorPresentacion(rollo))

        val rolloCorto = Presentacion(nuevoId(), cuaderno, unidades("1.25"))
        for (centesimas in listOf(5, 12)) {
            assertEquals("$centesimas", Linea(333, 1249, 333, 1249), linea(calcular(precios, rolloCorto, Cantidad.piezas(3), porcentaje(centesimas))))
        }
        val bajaDeLaLinea = calcular(precios, rolloCorto, Cantidad.piezas(3), porcentaje(13))!!
        assertEquals(Linea(333, 1247, 333, 1249, promocion = "13", ahorroPromocion = 1), linea(bajaDeLaLinea))
        assertEquals(416L, bajaDeLaLinea.precioPorPresentacion(rolloCorto))
    }

    /**
     * CalculoPrecio.cs:130 (`importe > 0`) y 191-198: no se aplica la promoción que deja la línea en cero (99.99 % de un
     * centavo) ni a la que le falta lo de su tipo (precio especial de cero o sin precio, 100 % o −0.05 %, «lleva 2 y paga 2»,
     * «lleva 3 y paga 0»). El segundo a mitad no pide nada: de dos piezas de un centavo deja una.
     */
    @Test
    fun `una promocion que deja cero o le falta lo de su tipo no se aplica`() {
        val deUnCentavo = listOf(porPieza(1))
        assertNull(calcular(deUnCentavo, pieza, Cantidad.piezas(1), regla(TipoPromocion.PORCENTAJE, centesimas = 9999))!!.promocion)
        assertNull(calcular(deUnCentavo, pieza, Cantidad.piezas(1), regla(TipoPromocion.PRECIO_ESPECIAL, precio = 0))!!.promocion)
        assertEquals(
            Linea(1, 1, 1, 2, promocion = "mitad", ahorroPromocion = 1),
            linea(calcular(deUnCentavo, pieza, Cantidad.piezas(2), regla(TipoPromocion.SEGUNDO_A_MITAD, nombre = "mitad"))),
        )

        val precios = listOf(porPieza(1000))
        val sinLoDeSuTipo = listOf(
            regla(TipoPromocion.PRECIO_ESPECIAL),
            regla(TipoPromocion.PORCENTAJE, centesimas = 10000),
            regla(TipoPromocion.PORCENTAJE, centesimas = -5),
            regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 2, paga = 2),
            regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 0),
        )
        for (promocion in sinLoDeSuTipo) {
            assertEquals("$promocion", Linea(1000, 3000, 1000, 3000), linea(calcular(precios, pieza, Cantidad.piezas(3), promocion)))
        }
    }

    /**
     * CalculoPrecio.cs:127: `OrderBy(p => p.PromocionId)` es estable y compara Guid. El mismo Id en mayúsculas y en
     * minúsculas es el mismo Guid: empatan, y con el mismo importe se queda la que llegó primero. Los dos grupos de en medio
     * (dos `short` en el Guid de .NET) se comparan sin signo, como su texto: «…-7fff-…» antes que «…-8000-…».
     */
    @Test
    fun `el mismo Id con otras mayusculas empata como un Guid y los grupos de en medio van sin signo`() {
        val precios = listOf(porPieza(1000))
        val primera = regla(TipoPromocion.PORCENTAJE, centesimas = 1000, id = "0190c5a2-7f3b-7c1e-9a4d-00000000abcd", nombre = "primera")
        val segunda = regla(TipoPromocion.PRECIO_ESPECIAL, precio = 900, id = "0190C5A2-7F3B-7C1E-9A4D-00000000ABCD", nombre = "segunda")

        assertEquals("primera", calcular(precios, pieza, Cantidad.piezas(1), primera, segunda)!!.promocion!!.nombre)
        assertEquals("segunda", calcular(precios, pieza, Cantidad.piezas(1), segunda, primera)!!.promocion!!.nombre)
        val enMedio = listOf(
            "00000000-7fff-0000-0000-000000000000" to "00000000-8000-0000-0000-000000000000",
            "00000000-0000-7fff-0000-000000000000" to "00000000-0000-8000-0000-000000000000",
        )
        for ((menor, mayor) in enMedio) {
            val gana = regla(TipoPromocion.PORCENTAJE, centesimas = 1000, id = menor, nombre = menor)
            val pierde = regla(TipoPromocion.PRECIO_ESPECIAL, precio = 900, id = mayor, nombre = mayor)
            assertEquals(menor, calcular(precios, pieza, Cantidad.piezas(1), pierde, gana)!!.promocion!!.nombre)
        }
    }

    /**
     * CalculoPrecio.cs:116-117 y 129: con precio propio las promociones cuentan presentaciones (3 docenas son un grupo del
     * 3×2, y 3.5 también: solo las enteras), el segundo a mitad va por pares de docenas, y el precio especial es por pieza
     * (`unidadesBase`): una docena a $5 la pieza son $60 contra sus $70.
     */
    @Test
    fun `con precio propio las promociones cuentan presentaciones y el precio especial piezas`() {
        val precios = listOf(propio(docena, 7000), porPieza(800))
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2, nombre = "3×2")

        assertEquals(
            Linea(7000, 14000, 7000, 21000, promocion = "3×2", ahorroPromocion = 7000),
            linea(calcular(precios, docena, Cantidad.piezas(3), tresPorDos)),
        )
        assertEquals(
            Linea(7000, 17500, 7000, 24500, promocion = "3×2", ahorroPromocion = 7000),
            linea(calcular(precios, docena, unidades("3.5"), tresPorDos)),
        )
        assertEquals(
            Linea(7000, 10500, 7000, 14000, promocion = "mitad", ahorroPromocion = 3500),
            linea(calcular(precios, docena, Cantidad.piezas(2), regla(TipoPromocion.SEGUNDO_A_MITAD, nombre = "mitad"))),
        )
        assertEquals(
            Linea(7000, 6000, 7000, 7000, promocion = "especial", ahorroPromocion = 1000),
            linea(calcular(precios, docena, Cantidad.piezas(1), regla(TipoPromocion.PRECIO_ESPECIAL, precio = 500, nombre = "especial"))),
        )
    }

    /**
     * Promocion.cs:62 y 298: los días son banderas y solo se mira la del día (`(dias & De(dia)) != 0`): 128 (ningún día)
     * no corre; 128 + miércoles sí, el miércoles 4/08/2027; 128 + martes no; −1 (todas las banderas) sí.
     */
    @Test
    fun `los dias de una promocion son banderas aunque traigan bits de mas`() {
        val precios = listOf(porPieza(1000))
        fun conDias(valor: Int) =
            calcular(precios, pieza, Cantidad.piezas(1), regla(TipoPromocion.PORCENTAJE, centesimas = 1000, dias = DiasDeLaSemana(valor)))!!.importeCentavos

        assertEquals(listOf(1000L, 900L, 1000L, 900L), listOf(128, 128 or 4, 128 or 2, -1).map(::conDias))
    }

    /** CalculoPrecio.cs:69: sin día, en una devolución o con cantidad cero no hay promoción, sino el regular. */
    @Test
    fun `sin dia o sin cantidad positiva no hay promocion`() {
        val precios = listOf(porPieza(1000))
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2)

        assertEquals(Linea(1000, 3000, 1000, 3000), linea(calcular(precios, pieza, Cantidad.piezas(3), tresPorDos, dia = null)))
        assertEquals(Linea(1000, -3000, 1000, -3000), linea(calcular(precios, pieza, Cantidad.piezas(-3), tresPorDos)))
        assertEquals(Linea(1000, 0, 1000, 0), linea(calcular(precios, pieza, Cantidad.CERO, tresPorDos)))
    }

    // ── Desbordes ──

    /**
     * CalculoPrecio.cs:197-198: `p.Lleva * Cantidad.Escala` es `int` por `long`, en `long`: un «lleva 2,147,483,647 y paga 1»
     * no se desborda. 2,147,483,647 piezas de un centavo son un grupo y queda un centavo; con una pieza menos, ningún grupo.
     */
    @Test
    fun `un lleva y paga enorme no se desborda`() {
        val precios = listOf(porPieza(1))
        val enorme = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = Int.MAX_VALUE, paga = 1, nombre = "enorme")

        assertEquals(
            Linea(1, 1, 1, 2_147_483_647, promocion = "enorme", ahorroPromocion = 2_147_483_646),
            linea(calcular(precios, pieza, Cantidad.piezas(Int.MAX_VALUE.toLong()), enorme)),
        )
        assertNull(calcular(precios, pieza, Cantidad.piezas(Int.MAX_VALUE - 1L), enorme)!!.promocion)
    }

    /**
     * CalculoPrecio.cs:124-126 y 133-134: el descuento del cliente solo decide si gana la promoción; si no gana, el cálculo
     * devuelve el regular sin descontarlo (lo descuenta la venta). Uno de más del 100 % (int.MaxValue centésimas, en
     * `Int128`: $30 − $6,442,450.94) no deja ganar a ninguna promoción y tampoco se desborda.
     */
    @Test
    fun `el calculo no descuenta el descuento del cliente y uno enorme no se desborda`() {
        val precios = listOf(porPieza(1000))
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2)

        assertEquals(Linea(1000, 2000, 1000, 2000), linea(calcular(precios, pieza, Cantidad.piezas(2), tresPorDos, descuento = 500)))
        assertEquals(Linea(1000, 3000, 1000, 3000), linea(calcular(precios, pieza, Cantidad.piezas(3), tresPorDos, descuento = Int.MAX_VALUE)))
    }

    /**
     * Cantidad.cs:37 y CalculoPrecio.cs:126 y 192 multiplican en `Int128`: a $50 billones la pieza, 10 piezas son 5 × 10¹⁹
     * (centavos por milésimas, más que un Long) y el importe, 5 × 10¹⁶, sí cabe. Igual el 10 % de 3 piezas y el 3×2 contra
     * el 5 % del cliente.
     */
    @Test
    fun `con precios grandes el producto que no cabe en un Long da el importe del Int128`() {
        val caro = 5_000_000_000_000_000L
        val precios = listOf(porPieza(caro))
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2, nombre = "3×2")

        assertEquals(50_000_000_000_000_000L, calcular(precios, pieza, Cantidad.piezas(10))!!.importeCentavos)
        assertEquals(
            Linea(caro, 10_000_000_000_000_000L, caro, 15_000_000_000_000_000L, promocion = "3×2", ahorroPromocion = caro),
            linea(calcular(precios, pieza, Cantidad.piezas(3), tresPorDos)),
        )
        assertEquals(
            Linea(caro, 13_500_000_000_000_000L, caro, 15_000_000_000_000_000L, promocion = "10 %", ahorroPromocion = 1_500_000_000_000_000L),
            linea(calcular(precios, pieza, Cantidad.piezas(3), regla(TipoPromocion.PORCENTAJE, centesimas = 1000, nombre = "10 %"))),
        )
        assertEquals(10_000_000_000_000_000L, calcular(precios, pieza, Cantidad.piezas(3), tresPorDos, descuento = 500)!!.importeCentavos)
    }

    /** Redondeo.cs:15 convierte con `checked`: el importe que no cabe en un Long lanza (allá `OverflowException`). */
    @Test
    fun `un importe que no cabe en un Long lanza como el checked del POS`() {
        val precios = listOf(porPieza(Long.MAX_VALUE / 1000))

        assertThrows(ArithmeticException::class.java) { calcular(precios, pieza, Cantidad.piezas(2000)) }
        assertThrows(ArithmeticException::class.java) { calcular(precios, docena, Cantidad.piezas(100)) }
    }

    // ── El precio más bajo con una promoción ──

    /**
     * CalculoPrecio.cs:179 divide en `decimal` de .NET: todas las cifras que quepan en 96 bits, hasta 28 decimales,
     * redondeando al par y sin ceros al final. Un rollo de 7 m a $100 con un 3×2: $200 ÷ 21 m = 952.38…, con 28 cifras (29 ya
     * no caben en 96 bits). Y con factores de más de 32 bits, que en .NET van por la división entre un divisor de 64 bits.
     */
    @Test
    fun `el precio mas bajo divide como el decimal del POS tambien con divisores grandes`() {
        val rolloDe7 = Presentacion(nuevoId(), cuaderno, Cantidad.piezas(7))
        val de33Bits = Presentacion(nuevoId(), cuaderno, Cantidad(4_294_967_297))
        val de54Bits = Presentacion(nuevoId(), cuaderno, Cantidad(9_007_199_254_740_993))
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2)
        val especial = regla(TipoPromocion.PRECIO_ESPECIAL, precio = 7)

        assertEquals(
            listOf("952.3809523809523809523809524", "6.9999999816063791556268978967", "0.0000740148688264478393026807"),
            listOf(
                CalculoPrecio.precioMasBajoCon(tresPorDos, listOf(porPieza(2000), propio(rolloDe7, 10_000)), listOf(pieza, rolloDe7), MATRIZ),
                CalculoPrecio.precioMasBajoCon(especial, listOf(propio(de33Bits, 100_000_000)), listOf(de33Bits), MATRIZ),
                CalculoPrecio.precioMasBajoCon(tresPorDos, listOf(propio(de54Bits, 1_000_000_007)), listOf(de54Bits), MATRIZ),
            ).map { it?.toPlainString() },
        )
    }

    /**
     * CalculoPrecio.cs:155 y 157: la presentación cuenta si alguna de sus escalas propias no pasa de sus piezas a menudeo
     * (`Min` del precio), pero la promoción va sobre su menudeo propio (`MinBy` de la cantidad). La docena a $125 (más que
     * 12 × $10) con mayoreo propio a $110 cuenta, y un precio especial de $10.20, que no baja la pieza de $10, sí baja la
     * docena: $10.20 la pieza. Sin ese mayoreo propio no cuenta: nada. Con factor cero no cuenta (CalculoPrecio.cs:151).
     */
    @Test
    fun `el precio mas bajo usa la presentacion si alguna de sus escalas no pasa de sus piezas`() {
        val especial = regla(TipoPromocion.PRECIO_ESPECIAL, precio = 1020)
        val conMayoreoPropio = listOf(porPieza(1000), propio(docena, 12500), propio(docena, 11000, desde = 5))
        val deCero = Presentacion(nuevoId(), cuaderno, Cantidad.CERO)

        assertEquals("1020", CalculoPrecio.precioMasBajoCon(especial, conMayoreoPropio, listOf(pieza, docena), MATRIZ)?.toPlainString())
        assertNull(CalculoPrecio.precioMasBajoCon(especial, listOf(porPieza(1000), propio(docena, 12500)), listOf(pieza, docena), MATRIZ))
        assertNull(CalculoPrecio.precioMasBajoCon(regla(TipoPromocion.PRECIO_ESPECIAL, precio = 500), listOf(propio(deCero, 12500)), listOf(deCero), MATRIZ))
    }

    private companion object {
        val MATRIZ = nuevoId()
        val SUCURSAL_2 = nuevoId()

        /** Un miércoles. */
        val HOY = Fecha.de(2027, 8, 4)
    }
}
