package mx.papeleriapos.android.copia

import mx.papeleriapos.android.caja.PresentacionDto
import mx.papeleriapos.android.caja.ProductoVentaDto
import mx.papeleriapos.android.caja.PromocionDto
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Cobro
import mx.papeleriapos.android.catalogo.Escala
import mx.papeleriapos.android.catalogo.Producto
import mx.papeleriapos.android.catalogo.Unidad
import mx.papeleriapos.android.catalogo.pesos
import mx.papeleriapos.pos.Cantidad
import mx.papeleriapos.pos.busqueda.IndiceDeBusqueda
import mx.papeleriapos.pos.busqueda.ProductoParaBuscar
import mx.papeleriapos.pos.busqueda.Sinonimo
import mx.papeleriapos.pos.precio.DiasDeLaSemana
import mx.papeleriapos.pos.precio.Fecha
import mx.papeleriapos.pos.precio.Precio
import mx.papeleriapos.pos.precio.Presentacion
import mx.papeleriapos.pos.precio.PromocionDeProducto
import mx.papeleriapos.pos.precio.TipoPromocion
import java.util.TimeZone

/**
 * Los productos de la copia como los enseñan Precio, Buscar y el ticket ([Producto]). Cada variante es un producto (el
 * suelto) y cada una de sus otras presentaciones, otro (la caja de 12), con su propio código y su precio; el precio se
 * calcula con [mx.papeleriapos.pos.precio.CalculoPrecio] sobre los precios tal cual de la copia, así que da lo mismo que
 * cobrará la caja. Se arma una vez por copia, fuera del hilo principal: con decenas de miles de productos tarda lo que
 * tarda el índice de la búsqueda.
 */
object ProductosDeLaCopia {
    fun armar(copia: Copia, hoy: Fecha = hoy()): CatalogoDeProductos {
        val sucursal = copia.caja.sucursalId
        val vendidos = HashMap<String, Int>(copia.vendidos.size * 2)
        for (v in copia.vendidos) vendidos[v.varianteId.lowercase()] = v.veces
        val promociones = promocionesPorVariante(copia.promociones)
        // Los códigos de cada presentación (variante + presentación), en su orden: el primero es el que se enseña.
        val codigosDe = HashMap<String, MutableList<String>>()
        for (c in copia.codigos) codigosDe.getOrPut(lugar(c.varianteId, c.presentacionId)) { ArrayList(1) }.add(c.codigo)

        val productos = ArrayList<Producto>(copia.productos.size * 5 / 4)
        val buscables = ArrayList<ProductoParaBuscar>(copia.productos.size)
        val porCodigo = HashMap<String, MutableList<String>>(copia.codigos.size * 2)
        // La clave de cada presentación, para los códigos: la del suelto o «variante/presentación».
        val claveDe = HashMap<String, String>(copia.codigos.size * 2)
        for (v in copia.productos) {
            val id = v.varianteId.lowercase()
            val base = v.presentacion
            val precios = v.precios.map {
                Precio(
                    varianteId = v.varianteId,
                    precioCentavos = it.precioCentavos,
                    cantidadMinima = Cantidad(it.cantidadMinimaMilesimas),
                    presentacionId = it.presentacionId,
                    sucursalId = it.sucursalId,
                )
            }
            val promos = promociones[id].orEmpty()
            val existencia = if (v.llevaInventario) v.existencia else null
            val unidad = Unidad.entries.getOrElse(v.unidad) { Unidad.PIEZA }
            val textoPromo = textoDePromociones(promos, hoy)
            val codigosDelSuelto = codigosDe[lugar(v.varianteId, base.id)].orEmpty()
            val suelto = Producto(
                clave = id,
                nombre = v.descripcion,
                departamento = "",
                escalas = escalasDe(precios, sucursal),
                existencia = existencia,
                unidad = unidad,
                promocion = textoPromo,
                vendidos = vendidos[id] ?: 0,
                codigo = v.codigo ?: codigosDelSuelto.firstOrNull(),
                cobro = Cobro(precios, presentacion(v.varianteId, base), sucursal, promos, hoy),
            )
            productos.add(suelto)
            claveDe[lugar(v.varianteId, base.id)] = id
            val todosLosCodigos = ArrayList(codigosDelSuelto)
            for (pr in v.presentaciones) {
                if (pr.esBase || pr.id.equals(base.id, ignoreCase = true)) continue
                val clave = "$id/${pr.id.lowercase()}"
                val codigos = codigosDe[lugar(v.varianteId, pr.id)].orEmpty()
                todosLosCodigos.addAll(codigos)
                val cobro = Cobro(precios, presentacion(v.varianteId, pr), sucursal, promos, hoy)
                productos.add(
                    Producto(
                        clave = clave,
                        nombre = "${v.descripcion} · ${pr.nombre}",
                        departamento = "",
                        escalas = listOf(Escala(1, cobro.precioDeUna() ?: 0)),
                        existencia = existencia,
                        unidad = unidad,
                        piezas = piezasDe(pr),
                        base = id,
                        promocion = textoPromo,
                        vendidos = vendidos[id] ?: 0,
                        codigo = codigos.firstOrNull(),
                        cobro = cobro,
                    ),
                )
                claveDe[lugar(v.varianteId, pr.id)] = clave
            }
            buscables.add(ProductoParaBuscar(varianteId = id, descripcion = v.descripcion, codigo = suelto.codigo, codigos = todosLosCodigos))
        }
        for (c in copia.codigos) {
            // Un código de una presentación que ya no está en el producto cae en su suelto.
            val clave = claveDe[lugar(c.varianteId, c.presentacionId)] ?: claveDe.entries.firstOrNull { it.key.startsWith(c.varianteId.lowercase() + "/") }?.value ?: continue
            val lista = porCodigo.getOrPut(c.codigo) { ArrayList(1) }
            if (clave !in lista) lista.add(clave)
        }
        val elegidos = HashMap<String, String>(copia.elegidos.size * 2)
        for (e in copia.elegidos) elegidos[IndiceDeBusqueda.claveGuardada(e.texto)] = e.varianteId.lowercase()
        return CatalogoDeProductos.deCopia(
            productos = productos,
            porCodigo = porCodigo,
            buscables = buscables,
            marcas = copia.marcas,
            sinonimos = copia.sinonimos.map { Sinonimo(it.palabra, it.equivale) },
            elegidos = elegidos,
            variantes = copia.productos.size,
        )
    }

    private fun lugar(varianteId: String, presentacionId: String) = "${varianteId.lowercase()}/${presentacionId.lowercase()}"

    private fun presentacion(varianteId: String, p: PresentacionDto) =
        Presentacion(id = p.id, varianteId = varianteId, factor = Cantidad(p.factorMilesimas))

    /** Las piezas que trae una presentación, para «$4.50 c/u (12)»: redondeadas (una de medio metro cuenta como 1). */
    private fun piezasDe(p: PresentacionDto): Int = ((p.factorMilesimas + 500) / 1000).toInt().coerceAtLeast(1)

    /**
     * Las escalas que enseña la tarjeta del suelto: los precios por unidad de la sucursal de la caja si hay, si no los
     * generales (`PrecioDeMenudeo.De` del POS), de la cantidad más chica a la más grande; la primera es el menudeo.
     */
    internal fun escalasDe(precios: List<Precio>, sucursalId: String): List<Escala> {
        val porUnidad = precios.filter { it.presentacionId == null }
        val deSucursal = porUnidad.filter { it.sucursalId != null && it.sucursalId.equals(sucursalId, ignoreCase = true) }
        val grupo = deSucursal.ifEmpty { porUnidad.filter { it.sucursalId == null } }
        val escalas = grupo.sortedBy { it.cantidadMinima }
            .map { Escala(desde = ((it.cantidadMinima.milesimas + 999) / 1000).toInt().coerceAtLeast(1), centavos = it.precioCentavos) }
        return escalas.ifEmpty { listOf(Escala(1, 0)) }
    }

    /** Las promociones de la copia por variante, como las lee el cálculo del precio. */
    internal fun promocionesPorVariante(promociones: List<PromocionDto>): Map<String, List<PromocionDeProducto>> {
        val porVariante = HashMap<String, MutableList<PromocionDeProducto>>()
        for (p in promociones) {
            val desde = fecha(p.desde) ?: continue
            val hasta = p.hasta?.let { fecha(it) }
            val tipo = TipoPromocion.entries.getOrNull(p.tipo) ?: continue
            for (v in p.variantes) {
                porVariante.getOrPut(v.varianteId.lowercase()) { ArrayList(1) }.add(
                    PromocionDeProducto(
                        promocionId = p.id,
                        nombre = p.nombre,
                        tipo = tipo,
                        lleva = p.lleva,
                        paga = p.paga,
                        centesimas = p.centesimas,
                        precioCentavos = v.precioCentavos,
                        desde = desde,
                        hasta = hasta,
                        dias = DiasDeLaSemana(p.dias),
                    ),
                )
            }
        }
        return porVariante
    }

    /** Lo que dice la tarjeta de las promociones que corren hoy: «Regreso a clases: lleva 3 y paga 2»; null si ninguna. */
    internal fun textoDePromociones(promociones: List<PromocionDeProducto>, hoy: Fecha): String? {
        val deHoy = promociones.filter { it.aplicaEl(hoy) }
        if (deHoy.isEmpty()) return null
        return deHoy.joinToString(" · ") { p ->
            val que = when (p.tipo) {
                TipoPromocion.LLEVA_N_PAGA_M -> "lleva ${p.lleva} y paga ${p.paga}"
                TipoPromocion.SEGUNDO_A_MITAD -> "el segundo a mitad de precio"
                TipoPromocion.PORCENTAJE -> "${porcentaje(p.centesimas)} % menos"
                TipoPromocion.PRECIO_ESPECIAL -> p.precioCentavos?.let { "a ${pesos(it)}" } ?: "precio especial"
            }
            if (p.nombre.isBlank()) que.replaceFirstChar { it.uppercase() } else "${p.nombre}: $que"
        }
    }

    private fun porcentaje(centesimas: Int): String = if (centesimas % 100 == 0) (centesimas / 100).toString() else (centesimas / 100.0).toString()

    /** «2026-11-08» → la fecha; null si no se entiende. */
    internal fun fecha(iso: String): Fecha? {
        val partes = iso.take(10).split('-')
        if (partes.size != 3) return null
        val (a, m, d) = partes.map { it.toIntOrNull() ?: return null }
        return try {
            Fecha.de(a, m, d)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** El día de hoy donde está el celular (la tienda), como lo cuenta el POS: para decidir qué promociones corren. */
fun hoy(ms: Long = System.currentTimeMillis(), zona: TimeZone = TimeZone.getDefault()): Fecha =
    Fecha(Math.floorDiv(ms + zona.getOffset(ms), 86_400_000L))
