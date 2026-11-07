package mx.papeleriapos.pos.precio

import mx.papeleriapos.pos.Cantidad
import mx.papeleriapos.pos.Redondeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * PromocionesTests.cs del POS, lo que prueba el cálculo, con los mismos casos y números: cada tipo, sus fechas, sus días y
 * la medianoche, que no se acumulan (se queda la que más le conviene al cliente, también contra el mayoreo) y su precio más
 * bajo. Las que allá pasan por la venta (la medianoche, la línea y el descuento del cliente) aquí piden lo mismo al cálculo,
 * que es lo que la venta le pregunta, sin la venta.
 */
class PromocionesTest {
    private val cuaderno = nuevoId()
    private val pieza = Presentacion(nuevoId(), cuaderno, Cantidad.piezas(1))
    private val docena = Presentacion(nuevoId(), cuaderno, Cantidad.piezas(12))

    private fun porPieza(centavos: Long, desde: Long = 1) = Precio(cuaderno, centavos, Cantidad.piezas(desde))

    private fun regla(
        tipo: TipoPromocion,
        lleva: Int = 0,
        paga: Int = 0,
        centesimas: Int = 0,
        precio: Long? = null,
        desde: Fecha? = null,
        hasta: Fecha? = null,
        dias: DiasDeLaSemana = DiasDeLaSemana.TODOS,
        id: String? = null,
        nombre: String = "Regreso a clases",
    ) = PromocionDeProducto(
        id ?: nuevoId(), nombre, tipo, lleva, paga, centesimas, precio, desde ?: HOY.masDias(-10), hasta ?: HOY.masDias(10), dias,
    )

    private fun calcular(precios: List<Precio>, presentacion: Presentacion, cantidad: Long, vararg promociones: PromocionDeProducto) =
        CalculoPrecio.calcular(precios, MATRIZ, presentacion, Cantidad.piezas(cantidad), promociones = promociones.toList(), dia = HOY)!!

    // ── Cada tipo ──

    @Test
    fun `lleva tres y paga dos cuenta los grupos completos`() {
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2)
        val precios = listOf(porPieza(1000))

        assertNull(calcular(precios, pieza, 2, tresPorDos).promocion)
        assertEquals(
            2000L to 1000L,
            calcular(precios, pieza, 3, tresPorDos).importeCentavos to calcular(precios, pieza, 3, tresPorDos).ahorroPromocionCentavos,
        )
        assertEquals(4000L, calcular(precios, pieza, 5, tresPorDos).importeCentavos)
        assertEquals(4000L, calcular(precios, pieza, 6, tresPorDos).importeCentavos)
        // Con la promoción, la línea va a su menudeo: no es mayoreo.
        val aplicado = calcular(precios, pieza, 6, tresPorDos)
        assertEquals(1000L, aplicado.precio.precioCentavos)
        assertFalse(aplicado.esMayoreo)
        assertSame(tresPorDos, aplicado.promocion)
    }

    @Test
    fun `el segundo a mitad de precio va por pares`() {
        val segundo = regla(TipoPromocion.SEGUNDO_A_MITAD)
        val precios = listOf(porPieza(1000))

        assertNull(calcular(precios, pieza, 1, segundo).promocion)
        assertEquals(1500L, calcular(precios, pieza, 2, segundo).importeCentavos)
        assertEquals(2500L, calcular(precios, pieza, 3, segundo).importeCentavos)
        assertEquals(3000L, calcular(precios, pieza, 4, segundo).importeCentavos)
    }

    @Test
    fun `el porcentaje va sobre el menudeo de toda la cantidad`() {
        val aplicado = calcular(listOf(porPieza(1250)), pieza, 2, regla(TipoPromocion.PORCENTAJE, centesimas = 1500))

        assertEquals(2125L, aplicado.importeCentavos)
        assertEquals(375L, aplicado.ahorroPromocionCentavos)
    }

    @Test
    fun `el precio especial es por pieza tambien en el paquete`() {
        val alCosto = regla(TipoPromocion.PRECIO_ESPECIAL, precio = 730)
        val precios = listOf(porPieza(1250))

        assertEquals(2190L, calcular(precios, pieza, 3, alCosto).importeCentavos)
        val laDocena = calcular(precios, docena, 1, alCosto)
        assertEquals(12 * 730L, laDocena.importeCentavos)
        // El precio de la línea es el de la docena a menudeo; lo que se ahorró es contra él.
        assertEquals(12 * 1250L, laDocena.precioPorPresentacion(docena))
        assertEquals(12 * (1250L - 730L), laDocena.ahorroPromocionCentavos)
    }

    @Test
    fun `un paquete cuenta sus piezas para el tres por dos como para el mayoreo`() {
        // Una docena son 12 piezas: 4 grupos de 3, 4 van gratis.
        val aplicado = calcular(listOf(porPieza(800)), docena, 1, regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2))

        assertEquals(8 * 800L, aplicado.importeCentavos)
    }

    @Test
    fun `a granel solo cuentan las unidades enteras`() {
        val liston = nuevoId()
        val metro = Presentacion(nuevoId(), liston, unidades("1"))
        val precios = listOf(Precio(liston, 600, unidades("0.001")))

        val aplicado = CalculoPrecio.calcular(
            precios, MATRIZ, metro, unidades("3.5"),
            promociones = listOf(regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2)), dia = HOY,
        )!!

        // 3.5 m son un grupo de 3: un metro gratis, se pagan 2.5.
        assertEquals(1500L, aplicado.importeCentavos)
    }

    // ── Fechas, días y medianoche ──

    /** Allá también prueba cómo se leen los días y las fechas (`Semana.Texto`, `Promocion.FechasTexto`): eso es de la lista de promociones del POS. */
    @Test
    fun `corre entre sus fechas y en sus dias`() {
        val finDeSemana = regla(
            TipoPromocion.PORCENTAJE, centesimas = 1000, desde = Fecha.de(2027, 8, 1), hasta = Fecha.de(2027, 8, 31),
            dias = DiasDeLaSemana.SABADO or DiasDeLaSemana.DOMINGO,
        )

        assertFalse(finDeSemana.aplicaEl(Fecha.de(2027, 7, 31)))
        assertTrue(finDeSemana.aplicaEl(Fecha.de(2027, 8, 1)))
        assertFalse(finDeSemana.aplicaEl(Fecha.de(2027, 8, 4)))
        assertTrue(finDeSemana.aplicaEl(Fecha.de(2027, 8, 7)))
        assertFalse(finDeSemana.aplicaEl(Fecha.de(2027, 9, 4)))
        assertNull(calcular(listOf(porPieza(1000)), pieza, 1, finDeSemana).promocion)
    }

    /**
     * A_medianoche_empieza_una_y_termina_otra_sin_bajar_nada: la venta lee el día cada vez que calcula; una promoción que
     * empieza a medianoche se aplica a lo que se escanea después, sin bajar nada, y la que termina deja de aplicarse. Aquí,
     * los dos días con que la venta le pide el precio al cálculo.
     */
    @Test
    fun `a medianoche empieza una y termina otra sin bajar nada`() {
        var dia = Fecha.de(2027, 8, 14)
        val empieza = regla(TipoPromocion.PORCENTAJE, centesimas = 2000, desde = dia.masDias(1), hasta = dia.masDias(30), nombre = "Empieza mañana")
        val otro = nuevoId()
        val piezaOtro = Presentacion(nuevoId(), otro, Cantidad.piezas(1))
        val termina = PromocionDeProducto(nuevoId(), "Termina hoy", TipoPromocion.PORCENTAJE, 0, 0, 1000, null, dia.masDias(-30), dia, DiasDeLaSemana.TODOS)
        val delCuaderno = listOf(porPieza(1000))
        val delOtro = listOf(Precio(otro, 2000, Cantidad.piezas(1)))
        fun precioDelCuaderno() = CalculoPrecio.calcular(delCuaderno, MATRIZ, pieza, Cantidad.piezas(1), promociones = listOf(empieza), dia = dia)!!
        fun precioDelOtro() = CalculoPrecio.calcular(delOtro, MATRIZ, piezaOtro, Cantidad.piezas(1), promociones = listOf(termina), dia = dia)!!

        val cuaderno = precioDelCuaderno()
        val conOtro = precioDelOtro()
        assertEquals(1000L to 1800L, cuaderno.importeCentavos to conOtro.importeCentavos)
        assertEquals("Termina hoy", conOtro.promocion?.nombre)

        dia = dia.masDias(1)
        val cuadernoManana = precioDelCuaderno()
        val otroManana = precioDelOtro()

        assertEquals(
            Triple(800L, "Empieza mañana", 200L),
            Triple(cuadernoManana.importeCentavos, cuadernoManana.promocion?.nombre, cuadernoManana.ahorroPromocionCentavos),
        )
        assertEquals(Triple(2000L, null, 0L), Triple(otroManana.importeCentavos, otroManana.promocion?.nombre, otroManana.ahorroPromocionCentavos))
    }

    // ── No se acumulan ──

    @Test
    fun `contra el mayoreo se queda lo que mas le conviene al cliente`() {
        val precios = listOf(porPieza(800), porPieza(650, desde = 12))
        val diezPorCiento = regla(TipoPromocion.PORCENTAJE, centesimas = 1000)

        // 5: la promoción ($36) le conviene más que el menudeo ($40).
        val cinco = calcular(precios, pieza, 5, diezPorCiento)
        assertEquals(3600L to 400L, cinco.importeCentavos to cinco.ahorroPromocionCentavos)
        // 12: el mayoreo ($78) le conviene más que la promoción ($86.40): no se juntan.
        val doce = calcular(precios, pieza, 12, diezPorCiento)
        assertNull(doce.promocion)
        assertTrue(doce.esMayoreo)
        assertEquals(7800L, doce.importeCentavos)
        // Con el mayoreo siempre del cliente, igual: lo que más le conviene.
        val deCliente = CalculoPrecio.calcular(
            precios, MATRIZ, pieza, Cantidad.piezas(2), mayoreoSiempre = true, promociones = listOf(diezPorCiento), dia = HOY,
        )!!
        assertEquals(1300L, deCliente.importeCentavos)
        assertNull(deCliente.promocion)
    }

    @Test
    fun `entre dos promociones se queda la que deja menos y en un empate siempre la misma`() {
        val precios = listOf(porPieza(1000))
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2, nombre = "3×2")
        val veinte = regla(TipoPromocion.PORCENTAJE, centesimas = 2000, nombre = "20 %")

        assertEquals("3×2", calcular(precios, pieza, 3, tresPorDos, veinte).promocion!!.nombre)
        assertEquals("20 %", calcular(precios, pieza, 2, tresPorDos, veinte).promocion!!.nombre)

        // Dos que dejan lo mismo: la de menor Id, en el orden en que lleguen (la caja y el servicio eligen la misma).
        val a = regla(TipoPromocion.PORCENTAJE, centesimas = 1000, id = "00000000-0000-0000-0000-000000000001", nombre = "A")
        val b = regla(TipoPromocion.PRECIO_ESPECIAL, precio = 900, id = "00000000-0000-0000-0000-000000000002", nombre = "B")
        assertEquals("A", calcular(precios, pieza, 1, a, b).promocion!!.nombre)
        assertEquals("A", calcular(precios, pieza, 1, b, a).promocion!!.nombre)
    }

    // ── La línea de la venta ──

    /**
     * La_linea_dice_la_promocion_y_el_descuento_va_encima_de_ella, lo que es del cálculo: el precio de la línea, su
     * importe, lo que se ahorró y cuál promoción. El descuento a mano y el precio cambiado son de la venta.
     */
    @Test
    fun `la linea dice la promocion`() {
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2)
        val tres = Cantidad.piezas(3)

        val linea = CalculoPrecio.calcular(listOf(porPieza(1000)), MATRIZ, pieza, tres, promociones = listOf(tresPorDos), dia = HOY)!!
        val precioDeLaLinea = linea.precioPorPresentacion(pieza)

        assertEquals(Triple(1000L, 2000L, 1000L), Triple(precioDeLaLinea, linea.importeCentavos, linea.ahorroPromocionCentavos))
        assertEquals(tresPorDos.promocionId to "Regreso a clases", linea.promocion!!.promocionId to linea.promocion!!.nombre)
        assertEquals(tres.importe(precioDeLaLinea) - linea.ahorroPromocionCentavos, linea.importeCentavos)
    }

    /**
     * El_descuento_del_cliente_no_se_acumula_con_la_promocion_se_queda_lo_que_mas_le_conviene (revisión del PR #132 del POS):
     * en cada renglón se queda lo que más le conviene al cliente, la promoción o su precio con su descuento, y se vuelve a
     * decidir con cada cantidad y al quitarle el descuento. La venta le pasa al cálculo su descuento y, si no ganó la
     * promoción, se lo descuenta al importe.
     */
    @Test
    fun `el descuento del cliente no se acumula con la promocion se queda lo que mas le conviene`() {
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2)
        val precios = listOf(porPieza(1000))
        fun conDescuento(piezas: Long, descuento: Int?) = CalculoPrecio.calcular(
            precios, MATRIZ, pieza, Cantidad.piezas(piezas), promociones = listOf(tresPorDos), dia = HOY, descuentoDelCliente = descuento,
        )!!
        /** Lo que la venta descuenta encima, si no ganó la promoción. */
        fun descuentoDeLaVenta(aplicado: PrecioAplicado, centesimas: Int) =
            if (aplicado.promocion != null) 0L else Redondeo.dividirProducto(aplicado.importeCentavos, centesimas.toLong(), 10_000)

        // 3 con el 5 % del cliente: el 3×2 ($20) le conviene más que $30 con 5 % ($28.50). El descuento queda en pausa.
        val tres = conDescuento(3, 500)
        assertEquals(Triple(2000L, 0L, "Regreso a clases"), Triple(tres.importeCentavos, descuentoDeLaVenta(tres, 500), tres.promocion?.nombre))

        // Con 2 no hay 3×2: vuelve su 5 %.
        val dos = conDescuento(2, 500)
        assertNull(dos.promocion)
        assertEquals(1900L to 100L, dos.importeCentavos - descuentoDeLaVenta(dos, 500) to descuentoDeLaVenta(dos, 500))
        assertEquals(2000L, conDescuento(3, 500).importeCentavos)

        // Con 40 % le conviene más su descuento ($18) que el 3×2 ($20): sin la promoción.
        val conCuarenta = conDescuento(3, 4000)
        assertNull(conCuarenta.promocion)
        assertEquals(1800L to 1200L, conCuarenta.importeCentavos - descuentoDeLaVenta(conCuarenta, 4000) to descuentoDeLaVenta(conCuarenta, 4000))
        // Sin el descuento del cliente (sale del ticket), vuelve la promoción.
        val sinDescuento = conDescuento(3, null)
        assertEquals(2000L to "Regreso a clases", sinDescuento.importeCentavos to sinDescuento.promocion?.nombre)
        // Un descuento a mano sí va encima de la promoción: la venta no se lo pasa al cálculo y lo descuenta del importe.
        assertEquals(1800L, sinDescuento.importeCentavos - Redondeo.dividirProducto(sinDescuento.importeCentavos, 1000, 10_000))
    }

    // ── Su precio más bajo, para revisar el costo ──

    @Test
    fun `su precio mas bajo es con el grupo completo`() {
        fun masBajo(regla: PromocionDeProducto, centavosPorPieza: Long) =
            CalculoPrecio.precioMasBajoCon(regla, listOf(porPieza(centavosPorPieza)), listOf(pieza, docena), MATRIZ)

        assertMismoNumero("600", masBajo(regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2), 900))
        assertMismoNumero("750", masBajo(regla(TipoPromocion.SEGUNDO_A_MITAD), 1000))
        assertMismoNumero("850", masBajo(regla(TipoPromocion.PORCENTAJE, centesimas = 1500), 1000))
        assertMismoNumero("730", masBajo(regla(TipoPromocion.PRECIO_ESPECIAL, precio = 730), 1250))
        // Un precio especial más alto que el menudeo no lo baja (la venta no lo aplica): no lo deja debajo de nada.
        assertNull(masBajo(regla(TipoPromocion.PRECIO_ESPECIAL, precio = 1300), 1250))
    }

    /**
     * Revisión del PR #132 del POS: la docena con precio propio ($120, $10 la pieza) de un cuaderno de $12 baja con un 3×2 a
     * $6.67 la pieza (tres docenas por $240), no a los $8 de la pieza. Contra eso se revisa el costo.
     */
    @Test
    fun `su precio mas bajo cuenta la presentacion con precio propio como la venta`() {
        fun deLaDocena(centavos: Long) = Precio(cuaderno, centavos, Cantidad.piezas(1), presentacionId = docena.id)
        val tresPorDos = regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2)
        val precios = listOf(porPieza(1200), deLaDocena(12000))

        val masBajo = CalculoPrecio.precioMasBajoCon(tresPorDos, precios, listOf(pieza, docena), MATRIZ)!!
        assertMismoNumero("666.6667", masBajo.setScale(4, RoundingMode.HALF_EVEN))
        assertEquals(24000L, calcular(precios, docena, 3, tresPorDos).importeCentavos)
        // Una docena con precio propio más caro que sus piezas no la usa la venta (la cobra por sus piezas): cuenta la pieza.
        assertMismoNumero("800", CalculoPrecio.precioMasBajoCon(tresPorDos, listOf(porPieza(1200), deLaDocena(15000)), listOf(pieza, docena), MATRIZ))
        // El precio especial es por pieza en cualquier presentación.
        assertMismoNumero(
            "700",
            CalculoPrecio.precioMasBajoCon(regla(TipoPromocion.PRECIO_ESPECIAL, precio = 700), precios, listOf(pieza, docena), MATRIZ),
        )
        // Con todas las cifras del decimal del POS.
        assertEquals("666.66666666666666666666666667", masBajo.toPlainString())
    }

    /** Una caja de 100 clips a $10 (su precio propio) con un 3×2: 6.67 centavos la pieza, con los 28 decimales del decimal del POS. */
    @Test
    fun `su precio mas bajo lleva los decimales del decimal del POS`() {
        val caja100 = Presentacion(nuevoId(), cuaderno, Cantidad.piezas(100))
        val precios = listOf(porPieza(15), Precio(cuaderno, 1000, presentacionId = caja100.id))

        val masBajo = CalculoPrecio.precioMasBajoCon(regla(TipoPromocion.LLEVA_N_PAGA_M, lleva = 3, paga = 2), precios, listOf(pieza, caja100), MATRIZ)

        assertEquals("6.6666666666666666666666666667", masBajo!!.toPlainString())
    }

    /** Como el `Assert.Equal` de dos `decimal`: el mismo número aunque tenga otros ceros al final. */
    private fun assertMismoNumero(esperado: String, real: BigDecimal?) {
        assertTrue("$real en vez de $esperado", real != null && BigDecimal(esperado).compareTo(real) == 0)
    }

    private companion object {
        val MATRIZ = nuevoId()

        /** Un miércoles. */
        val HOY = Fecha.de(2027, 8, 4)
    }
}
