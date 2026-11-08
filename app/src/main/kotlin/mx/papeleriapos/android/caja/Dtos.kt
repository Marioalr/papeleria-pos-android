package mx.papeleriapos.android.caja

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Lo que viaja entre el celular y el servicio de la caja principal (el documento del POS, docs/celular-como-caja.md, «La
 * secuencia de la app»): JSON en camelCase, los enums como número, las fechas en ISO 8601, los montos en centavos y las
 * cantidades en milésimas. Solo los campos que la app usa: lo demás del paquete se ignora al leer y nunca se guarda; en
 * particular los usuarios con el hash de su PIN, sus permisos y reglas, y los clientes con su crédito («Privacidad»).
 */

/** El JSON de la API: lo que no se conoce se salta y un `null` donde se espera un valor toma el de omisión. */
val JsonDelPos: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class ErrorDto(val mensaje: String = "", val codigo: String? = null)

/** `GET /api/estado`: sin llave dice qué es; con la llave y `caja=` es el latido. */
@Serializable
data class EstadoServicioDto(
    val servicio: String = "",
    val version: String = "",
    val sucursales: List<String> = emptyList(),
    val versionDatos: Long = 0,
    val prueba: Boolean = false,
    val versionExistencias: Long? = null,
    val base: String? = null,
    val formatoCopia: Int? = null,
    val tiendaNueva: Boolean = false,
    val unaSolaCaja: Boolean = false,
)

@Serializable
data class SolicitudCredenciales(val usuario: String, val pin: String)

@Serializable
data class CajaAltaDto(val id: String, val nombre: String, val serie: String, val equipo: String? = null)

@Serializable
data class SucursalAltaDto(val id: String, val nombre: String, val cajas: List<CajaAltaDto> = emptyList())

/** `cajaId` siempre vacío: así se crea una caja nueva (con el id de una que existe, le quitaría su llave a esa PC). */
@Serializable
data class SolicitudAltaCaja(
    val usuario: String,
    val pin: String,
    val sucursalId: String,
    val cajaId: String? = null,
    val nombre: String,
    val serie: String,
    val equipo: String,
)

@Serializable
data class CajaDto(
    val id: String,
    val nombre: String,
    val serie: String,
    val sucursalId: String,
    val sucursal: String = "",
    val equipo: String? = null,
)

@Serializable
data class AltaCajaDto(val caja: CajaDto, val llave: String, val llaveDelServicio: String? = null)

/** `GET /api/identidad?caja=…&reto=…`: la prueba de que ese servicio dio de alta a esta caja ([Identidad]). */
@Serializable
data class IdentidadServicioDto(val prueba: String = "", val llaveDelServicio: String? = null, val firma: String? = null)

// ── La copia ──

@Serializable
data class PresentacionDto(val id: String, val nombre: String = "", val factorMilesimas: Long = 1000, val esBase: Boolean = false)

@Serializable
data class PrecioDto(
    val id: String = "",
    val presentacionId: String? = null,
    val sucursalId: String? = null,
    val cantidadMinimaMilesimas: Long = 1000,
    val precioCentavos: Long = 0,
)

/**
 * Un producto (una variante) con sus presentaciones y precios, como va en la copia. [unidad]: 0 pieza, 1 metro, 2
 * kilogramo, 3 litro, 4 pieza a granel (`UnidadBase` del POS). [existencia] en unidades base, ya en unidades.
 */
@Serializable
data class ProductoVentaDto(
    val varianteId: String,
    val descripcion: String = "",
    val codigo: String? = null,
    val impuesto: Int = 0,
    val unidad: Int = 0,
    val llevaInventario: Boolean = true,
    val existencia: Double? = null,
    val presentacion: PresentacionDto,
    val presentaciones: List<PresentacionDto> = emptyList(),
    val precios: List<PrecioDto> = emptyList(),
)

@Serializable
data class CodigoLocalDto(val codigo: String, val varianteId: String, val presentacionId: String)

@Serializable
data class SinonimoDto(val id: String = "", val palabra: String, val equivale: String)

@Serializable
data class VecesVendidoDto(val varianteId: String, val veces: Int = 0)

@Serializable
data class ElegidoDto(val texto: String, val varianteId: String)

@Serializable
data class ExistenciaLocalDto(val varianteId: String, val cantidad: Double = 0.0)

@Serializable
data class VarianteEnPromocionDto(val varianteId: String, val precioCentavos: Long? = null)

/** [tipo]: 0 lleva N y paga M, 1 segundo a mitad, 2 porcentaje, 3 precio especial (`TipoPromocion` del POS). [dias]: banderas, lunes 1 … domingo 64. */
@Serializable
data class PromocionDto(
    val id: String,
    val nombre: String = "",
    val tipo: Int = 0,
    val lleva: Int = 0,
    val paga: Int = 0,
    val centesimas: Int = 0,
    val desde: String = "0001-01-01",
    val hasta: String? = null,
    val dias: Int = 127,
    val variantes: List<VarianteEnPromocionDto> = emptyList(),
)

/** Lo de Configuración › Tienda que sirve aquí: el nombre de la tienda. */
@Serializable
data class ConfiguracionSucursalDto(val nombreTienda: String? = null)

/**
 * `GET /api/cajas/{id}/paquete`: la copia completa. Los usuarios, sus permisos y reglas, los clientes y lo del ticket no
 * están aquí: se descartan al leer.
 */
@Serializable
data class PaqueteDto(
    val version: Long = 0,
    val caja: CajaDto,
    val productos: List<ProductoVentaDto> = emptyList(),
    val codigos: List<CodigoLocalDto> = emptyList(),
    val marcas: List<String>? = null,
    val sinonimos: List<SinonimoDto>? = null,
    val configuracion: ConfiguracionSucursalDto? = null,
    val base: String? = null,
    val formato: Int? = null,
    val versionExistencias: Long? = null,
    val vendidos: List<VecesVendidoDto>? = null,
    val elegidos: List<ElegidoDto>? = null,
    val versionVendidos: Long? = null,
    val versionElegidos: Long? = null,
    val promociones: List<PromocionDto>? = null,
    val huellaPromociones: String? = null,
)

/** `GET /api/cajas/{id}/cambios`: lo que cambió desde la versión de la copia, o [paqueteCompleto] con por qué hay que bajarla entera. */
@Serializable
data class CambiosDto(
    val paqueteCompleto: String? = null,
    val version: Long = 0,
    val versionExistencias: Long = 0,
    val productos: List<ProductoVentaDto> = emptyList(),
    val quitados: List<String> = emptyList(),
    val codigos: List<CodigoLocalDto> = emptyList(),
    val existencias: List<ExistenciaLocalDto> = emptyList(),
    val caja: CajaDto? = null,
    val configuracion: ConfiguracionSucursalDto? = null,
    val marcas: List<String>? = null,
    val sinonimos: List<SinonimoDto>? = null,
    val vendidos: List<VecesVendidoDto>? = null,
    val elegidos: List<ElegidoDto>? = null,
    val versionVendidos: Long? = null,
    val versionElegidos: Long? = null,
    val promociones: List<PromocionDto>? = null,
    val huellaPromociones: String? = null,
)
