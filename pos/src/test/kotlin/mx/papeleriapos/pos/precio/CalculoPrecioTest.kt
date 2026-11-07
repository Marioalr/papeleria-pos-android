package mx.papeleriapos.pos.precio

import mx.papeleriapos.pos.Cantidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CalculoPrecioTests.cs del POS, con los mismos casos y números. Después, las pruebas de ClienteRegistradoTests.cs y
 * VentaTests.cs que llaman al cálculo, y lo que esas mismas pruebas le piden a través de la venta (su precio por
 * presentación, el mayoreo y su ahorro), sin la venta, que no se trae.
 */
class CalculoPrecioTest {
    private val pluma = nuevoId()
    private val pieza = Presentacion(nuevoId(), pluma, Cantidad.piezas(1))
    private val docena = Presentacion(nuevoId(), pluma, Cantidad.piezas(12))

    private fun porPieza(centavos: Long, desde: Long = 1, sucursal: String? = null) =
        Precio(pluma, centavos, Cantidad.piezas(desde), sucursalId = sucursal)

    private fun calcular(
        precios: List<Precio>,
        sucursal: String,
        presentacion: Presentacion,
        cantidad: Cantidad,
        mayoreoSiempre: Boolean = false,
    ): PrecioAplicado? = CalculoPrecio.calcular(precios, sucursal, presentacion, cantidad, mayoreoSiempre)

    @Test
    fun `menudeo cobra precio por pieza`() {
        val aplicado = calcular(listOf(porPieza(800)), MATRIZ, pieza, Cantidad.piezas(3))

        assertEquals(2400L, aplicado!!.importeCentavos)
    }

    @Test
    fun `el paquete sin precio propio cuesta sus piezas y cuenta para mayoreo`() {
        val precios = listOf(porPieza(800), porPieza(650, desde = 12))

        val aplicado = calcular(precios, MATRIZ, docena, Cantidad.piezas(1))

        assertEquals(650L, aplicado!!.precio.precioCentavos)
        assertEquals(12 * 650L, aplicado.importeCentavos)
    }

    @Test
    fun `se usa la escala mas alta que alcanza`() {
        val precios = listOf(porPieza(800), porPieza(700, desde = 10), porPieza(600, desde = 50))

        assertEquals(800L, calcular(precios, MATRIZ, pieza, Cantidad.piezas(9))!!.precio.precioCentavos)
        assertEquals(700L, calcular(precios, MATRIZ, pieza, Cantidad.piezas(10))!!.precio.precioCentavos)
        assertEquals(600L, calcular(precios, MATRIZ, pieza, Cantidad.piezas(80))!!.precio.precioCentavos)
    }

    @Test
    fun `el precio propio de la presentacion gana`() {
        val propio = Precio(pluma, 7000, Cantidad.piezas(1), presentacionId = docena.id)

        val aplicado = calcular(listOf(porPieza(800), propio), MATRIZ, docena, Cantidad.piezas(2))

        assertSame(propio, aplicado!!.precio)
        assertEquals(14000L, aplicado.importeCentavos)
    }

    @Test
    fun `el precio de la sucursal gana sobre el base`() {
        val precios = listOf(porPieza(800), porPieza(900, sucursal = SUCURSAL_2))

        assertEquals(900L, calcular(precios, SUCURSAL_2, pieza, Cantidad.piezas(1))!!.importeCentavos)
        assertEquals(800L, calcular(precios, MATRIZ, pieza, Cantidad.piezas(1))!!.importeCentavos)
    }

    @Test
    fun `las escalas base no se mezclan con las de la sucursal`() {
        val precios = listOf(porPieza(800), porPieza(600, desde = 12), porPieza(900, sucursal = SUCURSAL_2))

        val aplicado = calcular(precios, SUCURSAL_2, pieza, Cantidad.piezas(12))

        assertEquals(900L, aplicado!!.precio.precioCentavos)
    }

    @Test
    fun `por metro con fracciones por debajo de la escala minima`() {
        val liston = nuevoId()
        val metro = Presentacion(nuevoId(), liston, unidades("1"))
        val precio = Precio(liston, 750, unidades("1"))

        assertEquals(1125L, calcular(listOf(precio), MATRIZ, metro, unidades("1.5"))!!.importeCentavos)
        assertEquals(375L, calcular(listOf(precio), MATRIZ, metro, unidades("0.5"))!!.importeCentavos)
    }

    @Test
    fun `sin precio devuelve null`() {
        assertNull(calcular(emptyList(), MATRIZ, pieza, Cantidad.piezas(1)))
    }

    // ── Nunca se cobra más que a menudeo, aunque las escalas hayan quedado capturadas fuera de orden ──

    @Test
    fun `una escala mas cara que el menudeo no se cobra`() {
        val precios = listOf(porPieza(1090), porPieza(1100, desde = 12))

        val doce = calcular(precios, MATRIZ, pieza, Cantidad.piezas(12))!!

        assertEquals(Triple(1090L, 12 * 1090L, false), Triple(doce.precio.precioCentavos, doce.importeCentavos, doce.esMayoreo))
    }

    @Test
    fun `una escala fuera de orden no cobra mas por llevar mas`() {
        // Desde 24 quedó más cara que desde 12 (y que el menudeo): 24 piezas se cobran a la de 12.
        val precios = listOf(porPieza(1500), porPieza(1450, desde = 12), porPieza(1550, desde = 24))

        val veinticuatro = calcular(precios, MATRIZ, pieza, Cantidad.piezas(24))!!

        assertEquals(
            Triple(1450L, 24 * 1450L, true),
            Triple(veinticuatro.precio.precioCentavos, veinticuatro.importeCentavos, veinticuatro.esMayoreo),
        )
        assertEquals(1450L, calcular(precios, MATRIZ, pieza, Cantidad.piezas(12))!!.precio.precioCentavos)
    }

    @Test
    fun `el mayoreo siempre tampoco cobra una escala mas cara`() {
        val precios = listOf(porPieza(1000), porPieza(1100, desde = 12))

        val una = calcular(precios, MATRIZ, pieza, Cantidad.piezas(1), mayoreoSiempre = true)!!

        assertEquals(1000L to false, una.precio.precioCentavos to una.esMayoreo)
    }

    @Test
    fun `un paquete mas caro que sus piezas a menudeo se cobra por sus piezas`() {
        val par = Presentacion(nuevoId(), pluma, Cantidad.piezas(2))
        val propio = Precio(pluma, 2050, Cantidad.piezas(1), presentacionId = par.id)
        val conDescuento = Precio(pluma, 1990, Cantidad.piezas(1), presentacionId = par.id)

        val caro = calcular(listOf(porPieza(1000), propio), MATRIZ, par, Cantidad.piezas(1))!!
        val barato = calcular(listOf(porPieza(1000), conDescuento), MATRIZ, par, Cantidad.piezas(1))!!

        assertEquals(2000L to null, caro.importeCentavos to caro.precio.presentacionId)
        assertSame(conDescuento, barato.precio)
        assertEquals(1990L, barato.importeCentavos)
    }

    // ── De ClienteRegistradoTests.cs: el mayoreo siempre del cliente registrado ──

    private val goma = nuevoId()
    private val gomaPieza = Presentacion(nuevoId(), goma)

    /** Los precios de ClienteRegistradoTests: la pluma con escalas desde 12 y desde 24, la goma sin escalas. */
    private val conEscalas = listOf(porPieza(1000), porPieza(800, desde = 12), porPieza(750, desde = 24), Precio(goma, 500))

    @Test
    fun `mayoreo siempre es al menos la primera escala de mayoreo`() {
        val casos = listOf(
            1L to 800L, // desde la primera pieza, la primera escala de mayoreo
            12L to 800L,
            24L to 750L, // la escala más alta sigue al llegar a ella
        )
        for ((piezas, precio) in casos) {
            val aplicado = calcular(conEscalas, MATRIZ, pieza, Cantidad.piezas(piezas), mayoreoSiempre = true)!!

            assertEquals("$piezas piezas", precio, aplicado.precio.precioCentavos)
            assertTrue("$piezas piezas", aplicado.esMayoreo)
        }
    }

    @Test
    fun `sin escalas de mayoreo el mayoreo siempre no cambia nada`() {
        val aplicado = calcular(conEscalas, MATRIZ, gomaPieza, Cantidad.piezas(3), mayoreoSiempre = true)!!

        assertEquals(500L to false, aplicado.precio.precioCentavos to aplicado.esMayoreo)
    }

    /**
     * Elegir_al_cliente_con_mayoreo_vuelve_a_calcular_los_renglones_y_quitarlo_los_regresa, sin la venta: lo que el cálculo
     * le da a cada renglón con el mayoreo del cliente y sin él.
     */
    @Test
    fun `con el mayoreo del cliente cada renglon va a su escala y sin el regresa`() {
        val plumas = calcular(conEscalas, MATRIZ, pieza, Cantidad.piezas(3), mayoreoSiempre = true)!!
        val gomas = calcular(conEscalas, MATRIZ, gomaPieza, Cantidad.piezas(2), mayoreoSiempre = true)!!
        val otra = calcular(conEscalas, MATRIZ, docena, Cantidad.piezas(2), mayoreoSiempre = true)!! // 24 piezas: ya llega a la de 24
        val sinElCliente = calcular(conEscalas, MATRIZ, pieza, Cantidad.piezas(3))!!

        assertEquals(Triple(800L, true, 600L), Triple(plumas.precioPorPresentacion(pieza), plumas.esMayoreo, plumas.ahorroCentavos))
        // Es el del cliente: el mayoreo aplicó antes de llegar a su cantidad.
        assertTrue(Cantidad.piezas(3) < plumas.precio.cantidadMinima)
        assertEquals(500L, gomas.precioPorPresentacion(gomaPieza))
        assertEquals(3400L, plumas.importeCentavos + gomas.importeCentavos)
        assertEquals(Cantidad.piezas(24), otra.precio.cantidadMinima)
        assertFalse(Cantidad.piezas(2).por(docena.factor) < otra.precio.cantidadMinima)
        assertEquals(Triple(1000L, false, 0L), Triple(sinElCliente.precioPorPresentacion(pieza), sinElCliente.esMayoreo, sinElCliente.ahorroCentavos))
    }

    // ── De VentaTests.cs: las escalas, y lo que la venta toma del cálculo ──

    private val liston = nuevoId()
    private val metro = Presentacion(nuevoId(), liston, unidades("1"))

    /** Los precios de VentaTests: la pluma con una escala desde 12 y el listón por metro. */
    private val deLaVenta = listOf(porPieza(1000), porPieza(800, desde = 12), Precio(liston, 750, unidades("1")))

    @Test
    fun `las escalas de un producto salen en orden y de la sucursal si tiene`() {
        val escalas = CalculoPrecio.escalas(deLaVenta, MATRIZ, pieza)

        assertFalse(escalas.dePresentacion)
        assertEquals(listOf(1000L, 800L), escalas.escalas.map { it.precioCentavos })
    }

    /** Agregar_el_mismo_producto_suma_cantidad_y_alcanza_el_mayoreo: la venta junta 10 y 2 en una línea de 12. */
    @Test
    fun `doce piezas alcanzan el mayoreo`() {
        val doce = calcular(deLaVenta, MATRIZ, pieza, Cantidad.piezas(10) + Cantidad.piezas(2))!!

        assertEquals(800L to 9600L, doce.precioPorPresentacion(pieza) to doce.importeCentavos)
    }

    /** La_linea_guarda_desde_cuanto_fue_mayoreo_y_lo_que_se_ahorro: desde cuánto (el de la escala) y cuánto se ahorró. */
    @Test
    fun `el mayoreo dice desde cuanto fue y lo que se ahorro`() {
        val linea = calcular(deLaVenta, MATRIZ, pieza, Cantidad.piezas(12))!!
        val caja = calcular(deLaVenta, MATRIZ, docena, Cantidad.piezas(1))!!
        val poquitas = calcular(deLaVenta, MATRIZ, pieza, Cantidad.piezas(11))!!

        // 12 a $8 en vez de $10: ahorra $24. La caja de 12 cuenta como 12 piezas.
        assertEquals(Triple(true, Cantidad.piezas(12), 2400L), Triple(linea.esMayoreo, linea.precio.cantidadMinima, linea.ahorroCentavos))
        assertEquals(Triple(true, Cantidad.piezas(12), 2400L), Triple(caja.esMayoreo, caja.precio.cantidadMinima, caja.ahorroCentavos))
        assertEquals(false to 0L, poquitas.esMayoreo to poquitas.ahorroCentavos)
    }

    /** Una_caja_de_12_sale_a_precio_de_mayoreo_y_descuenta_12_piezas. */
    @Test
    fun `una caja de 12 sale a precio de mayoreo`() {
        val caja = calcular(deLaVenta, MATRIZ, docena, Cantidad.piezas(1))!!

        assertEquals(Cantidad.piezas(12), Cantidad.piezas(1).por(docena.factor))
        assertEquals(9600L, caja.precioPorPresentacion(docena))
        assertEquals(9600L, caja.importeCentavos)
    }

    /** Por_metro_admite_fracciones_y_por_pieza_no: el metro y medio (que la pieza no admita fracciones lo revisa la venta). */
    @Test
    fun `por metro el metro y medio`() {
        assertEquals(1125L, calcular(deLaVenta, MATRIZ, metro, unidades("1.5"))!!.importeCentavos)
    }

    /** Sin_precio_no_se_agrega: los precios de otros productos no cuentan. */
    @Test
    fun `sin precio de ese producto no hay precio`() {
        val sinPrecio = nuevoId()

        assertNull(calcular(deLaVenta, MATRIZ, Presentacion(nuevoId(), sinPrecio), Cantidad.piezas(1)))
    }

    // ── Lo de Kotlin: las escalas de la sucursal y las propias, y los Id sin importar mayúsculas ──

    @Test
    fun `las escalas son las de la sucursal si tiene y las propias de la presentacion si las tiene`() {
        val propias = listOf(
            Precio(pluma, 9000, Cantidad.piezas(5), presentacionId = docena.id),
            Precio(pluma, 9500, Cantidad.piezas(1), presentacionId = docena.id),
        )
        val precios = deLaVenta + porPieza(1100, sucursal = SUCURSAL_2) + porPieza(900, desde = 10, sucursal = SUCURSAL_2) + propias

        val deLaSucursal = CalculoPrecio.escalas(precios, SUCURSAL_2, pieza)
        val deLaDocena = CalculoPrecio.escalas(precios, MATRIZ, docena)

        assertEquals(false to listOf(1100L, 900L), deLaSucursal.dePresentacion to deLaSucursal.escalas.map { it.precioCentavos })
        assertEquals(true to listOf(9500L, 9000L), deLaDocena.dePresentacion to deLaDocena.escalas.map { it.precioCentavos })
    }

    /** La orilla de «nunca más caro que sus piezas»: lo mismo que sus piezas no es más caro, va con su precio propio. */
    @Test
    fun `un paquete que cuesta lo mismo que sus piezas va con su precio propio`() {
        val par = Presentacion(nuevoId(), pluma, Cantidad.piezas(2))
        val igual = Precio(pluma, 2000, Cantidad.piezas(1), presentacionId = par.id)

        val aplicado = calcular(listOf(porPieza(1000), igual), MATRIZ, par, Cantidad.piezas(1))!!

        assertSame(igual, aplicado.precio)
        assertEquals(2000L, aplicado.importeCentavos)
    }

    /** La regla de `ElegirEscala` («entre dos al mismo precio, la de más piezas»): así el ticket dice «desde 24». */
    @Test
    fun `entre dos escalas al mismo precio se queda la de mas piezas`() {
        val enOrden = listOf(porPieza(1000), porPieza(800, desde = 12), porPieza(800, desde = 24))
        for (precios in listOf(enOrden, listOf(enOrden[0], enOrden[2], enOrden[1]))) {
            val treinta = calcular(precios, MATRIZ, pieza, Cantidad.piezas(30))!!

            assertSame(enOrden[2], treinta.precio)
            assertEquals(Triple(24000L, true, 6000L), Triple(treinta.importeCentavos, treinta.esMayoreo, treinta.ahorroCentavos))
        }
    }

    @Test
    fun `los Id son los mismos con mayusculas o minusculas, como un Guid`() {
        val precios = listOf(
            Precio(pluma.uppercase(), 800),
            Precio(pluma, 950, sucursalId = SUCURSAL_2.uppercase()),
            Precio(pluma.uppercase(), 7000, presentacionId = docena.id.uppercase()),
        )

        assertEquals(950L, calcular(precios, SUCURSAL_2, pieza, Cantidad.piezas(1))!!.importeCentavos)
        assertEquals(800L, calcular(precios, MATRIZ, pieza, Cantidad.piezas(1))!!.importeCentavos)
        assertEquals(7000L, calcular(precios, MATRIZ, docena, Cantidad.piezas(1))!!.importeCentavos)
    }

    private companion object {
        val MATRIZ = nuevoId()
        val SUCURSAL_2 = nuevoId()
    }
}
