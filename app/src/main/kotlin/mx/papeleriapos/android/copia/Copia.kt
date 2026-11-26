package mx.papeleriapos.android.copia

import kotlinx.serialization.Serializable
import mx.papeleriapos.android.caja.CajaDto
import mx.papeleriapos.android.caja.CambiosDto
import mx.papeleriapos.android.caja.CodigoLocalDto
import mx.papeleriapos.android.caja.ElegidoDto
import mx.papeleriapos.android.caja.PaqueteDto
import mx.papeleriapos.android.caja.ProductoVentaDto
import mx.papeleriapos.android.caja.PromocionDto
import mx.papeleriapos.android.caja.SinonimoDto
import mx.papeleriapos.android.caja.VecesVendidoDto

/**
 * La copia del catálogo en el celular (docs/propuesta-interfaz.md, «La copia del catálogo en cada uno»): lo que baja de
 * `GET /api/cajas/{id}/paquete` menos lo que un celular no debe cargar, y lo que después llega con
 * `GET /api/cajas/{id}/cambios`. Es la copia de una caja, así que trae lo mismo que la de la caja de Windows: los
 * productos con sus presentaciones, precios y existencia, todos los códigos, las marcas, los sinónimos, lo más vendido,
 * lo elegido en la búsqueda y las promociones. Nunca los usuarios (ni el hash de su PIN), sus permisos ni reglas, los
 * clientes ni lo del ticket: eso ni siquiera se lee del JSON ([mx.papeleriapos.android.caja.PaqueteDto]).
 *
 * Inmutable: cada cambio da otra copia. Se guarda como JSON propio ([AlmacenCopia]).
 *
 * @param guardadaEn Cuándo se bajó o se puso al día (ms del reloj de pared): «la copia es de las 9:12».
 */
@Serializable
data class Copia(
    val version: Long,
    val versionExistencias: Long,
    val base: String?,
    val formato: Int?,
    val versionVendidos: Long,
    val versionElegidos: Long,
    val huellaPromociones: String?,
    val caja: CajaDto,
    val nombreTienda: String? = null,
    val productos: List<ProductoVentaDto>,
    val codigos: List<CodigoLocalDto>,
    val marcas: List<String> = emptyList(),
    val sinonimos: List<SinonimoDto> = emptyList(),
    val vendidos: List<VecesVendidoDto> = emptyList(),
    val elegidos: List<ElegidoDto> = emptyList(),
    val promociones: List<PromocionDto> = emptyList(),
    /** Qué productos tienen foto: su variante (en minúsculas) y la huella de su foto (paso 12 del documento del POS). */
    val fotos: Map<String, String> = emptyMap(),
    val guardadaEn: Long,
) {
    /** La huella de la foto de esa variante, o null si no tiene. */
    fun huellaDe(varianteId: String): String? = fotos[varianteId.lowercase()]

    /** Con la foto que se acaba de poner (o cambiar), sin esperar a los cambios. */
    fun conFoto(varianteId: String, huella: String): Copia = copy(fotos = fotos + (varianteId.lowercase() to huella))

    /** Sin la foto que se acaba de quitar. */
    fun sinFoto(varianteId: String): Copia = if (varianteId.lowercase() in fotos) copy(fotos = fotos - varianteId.lowercase()) else this

    /** Si el latido trae otras versiones que las de aquí: hay que pedir los cambios. */
    fun desactualizada(versionDatos: Long, versionExistencias: Long?, base: String?, formato: Int?): Boolean =
        versionDatos != version ||
            (versionExistencias != null && versionExistencias != this.versionExistencias) ||
            (base != null && !base.equals(this.base, ignoreCase = true)) ||
            (formato != null && formato != this.formato)

    /**
     * Los cambios aplicados como lo hace la caja de Windows (`CopiaAlDia.Aplicar`): los productos que llegaron reemplazan
     * a los de su mismo id y los nuevos se agregan; los quitados se van con sus códigos; los códigos que llegaron son
     * todos los de esos productos (un código que llegó con otro producto deja de ser del de antes); las existencias que
     * cambiaron; y lo demás, si viene, reemplaza lo de aquí. Con [CambiosDto.paqueteCompleto] no se llama: se baja la
     * copia entera.
     */
    fun aplicar(c: CambiosDto, ahora: Long): Copia {
        require(c.paqueteCompleto == null) { "Con paqueteCompleto se baja la copia entera" }
        val llegaron = c.productos.associateBy { it.varianteId.lowercase() }
        val quitados = c.quitados.map { it.lowercase() }.toHashSet()
        val existencias = c.existencias.associate { it.varianteId.lowercase() to it.cantidad }
        val productosNuevos = ArrayList<ProductoVentaDto>(productos.size + c.productos.size)
        val vistos = HashSet<String>()
        for (p in productos) {
            val id = p.varianteId.lowercase()
            if (id in quitados) continue
            val nuevo = llegaron[id] ?: p
            vistos.add(id)
            productosNuevos.add(if (nuevo === p) conExistencia(p, existencias[id]) else nuevo)
        }
        for (p in c.productos) if (vistos.add(p.varianteId.lowercase())) productosNuevos.add(p)
        val codigosQueLlegaron = c.codigos.map { it.codigo }.toHashSet()
        val codigosNuevos = codigos.filter { it.varianteId.lowercase() !in quitados && it.varianteId.lowercase() !in llegaron && it.codigo !in codigosQueLlegaron } + c.codigos
        return copy(
            version = c.version,
            versionExistencias = c.versionExistencias,
            versionVendidos = c.versionVendidos ?: versionVendidos,
            versionElegidos = c.versionElegidos ?: versionElegidos,
            huellaPromociones = c.huellaPromociones ?: huellaPromociones,
            caja = c.caja ?: caja,
            nombreTienda = c.configuracion?.nombreTienda ?: nombreTienda,
            productos = productosNuevos,
            codigos = codigosNuevos,
            marcas = c.marcas ?: marcas,
            sinonimos = c.sinonimos ?: sinonimos,
            vendidos = c.vendidos ?: vendidos,
            elegidos = c.elegidos?.let { nuevos -> (elegidos.filter { e -> nuevos.none { it.texto == e.texto } } + nuevos) } ?: elegidos,
            promociones = c.promociones ?: promociones,
            // Las fotos no se reemplazan todas: las que llegan van encima, y se van las quitadas y las de productos quitados.
            fotos = (fotos + c.fotos.orEmpty().associate { it.varianteId.lowercase() to it.huella })
                .filterKeys { it !in quitados && c.fotosQuitadas.orEmpty().none { q -> q.equals(it, ignoreCase = true) } },
            guardadaEn = ahora,
        )
    }

    private fun conExistencia(p: ProductoVentaDto, existencia: Double?): ProductoVentaDto =
        if (existencia == null || !p.llevaInventario) p else p.copy(existencia = existencia)

    companion object {
        /** La copia desde el paquete completo. Lo que el paquete trae de más (usuarios, clientes…) ya no está en [p]. */
        fun dePaquete(p: PaqueteDto, ahora: Long): Copia = Copia(
            version = p.version,
            versionExistencias = p.versionExistencias ?: 0,
            base = p.base,
            formato = p.formato,
            versionVendidos = p.versionVendidos ?: 0,
            versionElegidos = p.versionElegidos ?: 0,
            huellaPromociones = p.huellaPromociones,
            caja = p.caja,
            nombreTienda = p.configuracion?.nombreTienda,
            productos = p.productos,
            codigos = p.codigos,
            marcas = p.marcas.orEmpty(),
            sinonimos = p.sinonimos.orEmpty(),
            vendidos = p.vendidos.orEmpty(),
            elegidos = p.elegidos.orEmpty(),
            promociones = p.promociones.orEmpty(),
            fotos = p.fotos.orEmpty().associate { it.varianteId.lowercase() to it.huella },
            guardadaEn = ahora,
        )
    }
}
