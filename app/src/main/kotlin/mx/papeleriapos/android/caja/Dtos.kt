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
    /** Qué productos tienen foto y su huella (paso 12); nada de la imagen. */
    val fotos: List<FotoDto>? = null,
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
    /** Solo las fotos nuevas y las cambiadas: van encima de las de la copia. Sin el campo, no cambiaron. */
    val fotos: List<FotoDto>? = null,
    val fotosQuitadas: List<String>? = null,
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

// ---- Mandar el ticket a una caja (docs/celular-como-caja.md del POS, paso 9) ----

/** `GET /api/cajas/{id}/para-mandar`: una caja de la sucursal, si tiene la venta abierta, quién atiende y cuántos esperan. */
@Serializable
data class CajaParaMandarDto(
    val id: String,
    val nombre: String = "",
    val serie: String = "",
    val recibe: Boolean = false,
    val atiende: String? = null,
    val esperan: Int? = null,
    /** Tiene un turno abierto (paso 10): el celular puede cobrar en él, aunque no tenga la venta abierta. */
    val turnoAbierto: Boolean = false,
    /** Si el ticket de lo que se cobre en esta caja puede salir, y por dónde (paso 11). null: un POS que todavía no imprime. */
    val impresion: ImpresionPosibleDto? = null,
)

/** [puede]: la casilla «Imprimir ticket» se puede marcar; si no, sale apagada con [mensaje]. */
@Serializable
data class ImpresionPosibleDto(
    val puede: Boolean = false,
    val por: String? = null,
    val codigo: String? = null,
    val mensaje: String = "",
    val automatico: Boolean = false,
)

/**
 * Lo que pasó con el ticket (al cobrar o al pedirlo después): [estado] «impreso», «en-camino», «no-pedida» o «no-impreso»
 * (con su [codigo]); [mensaje], para enseñarse tal cual. [salio] solo con «impreso».
 */
@Serializable
data class ImpresionDto(
    val estado: String = "",
    val codigo: String? = null,
    val mensaje: String = "",
    val cajaId: String? = null,
    val caja: String? = null,
    val por: String? = null,
    val salio: Boolean = false,
)

/** `POST /api/cajas/{id}/ventas/{ventaId}/imprimir`: [id], el de esta petición (la misma otra vez no saca otro papel). */
@Serializable
data class SolicitudImprimir(val id: String, val cajeroId: String? = null, val motivo: String? = null)

/**
 * Un renglón, el mismo al mandar, cotizar y cobrar: el producto de la copia (su variante y presentación) y, por si acaso,
 * su código; nunca su precio. Un artículo común va sin variante, con su [descripcion] y su [precioCentavos] por pieza.
 */
@Serializable
data class RenglonMandado(
    val varianteId: String? = null,
    val presentacionId: String? = null,
    val codigo: String? = null,
    val cantidadMilesimas: Long,
    val descripcion: String? = null,
    val precioCentavos: Long? = null,
)

/** `POST /api/cajas/{id}/mandar`: [id], uno por ticket (mandarlo otra vez con el mismo no lo duplica). */
@Serializable
data class SolicitudMandarTicket(val id: String, val destino: String, val renglones: List<RenglonMandado>)

/** Lo que contesta: a qué caja llegó, cuántos artículos y el total con los precios del servicio. */
@Serializable
data class TicketMandadoDto(
    val id: String,
    val destino: String = "",
    val caja: String = "",
    val renglones: Int = 0,
    val articulos: Double = 0.0,
    val totalCentavos: Long = 0,
    val recibido: Boolean = false,
)

// ---- Cobrar en el celular (docs/celular-como-caja.md del POS, paso 10) ----

/** Cómo paga: [forma] 0 efectivo (el monto es lo que entregó el cliente), 1 tarjeta y 2 transferencia (exactas). */
@Serializable
data class CobroPedido(val forma: Int, val montoCentavos: Long, val referencia: String? = null)

/** `POST /api/cajas/{id}/cotizar`: lo que costaría, con la misma función que el cobro. No cobra nada ni pide turno. */
@Serializable
data class SolicitudCotizar(val renglones: List<RenglonMandado>, val cobros: List<CobroPedido>? = null)

/** Una línea como la cobraría el servicio, con sus precios y promociones de hoy. */
@Serializable
data class LineaCotizadaDto(
    val varianteId: String? = null,
    val presentacionId: String? = null,
    val descripcion: String = "",
    val cantidadMilesimas: Long = 0,
    val precioCentavos: Long = 0,
    val importeCentavos: Long = 0,
    val articuloComun: Boolean = false,
)

/** Con los cobros de la solicitud: cuánto falta o el cambio. */
@Serializable
data class PagoCotizadoDto(
    val sinEfectivoCentavos: Long = 0,
    val efectivoCentavos: Long = 0,
    val redondeoCentavos: Long = 0,
    val recibidoCentavos: Long = 0,
    val faltanCentavos: Long = 0,
    val cambioCentavos: Long = 0,
)

/** [totalCentavos] es el exacto (el de tarjeta y transferencia); [efectivoCentavos], lo que se cobra si todo es en efectivo. */
@Serializable
data class CotizacionDto(
    val lineas: List<LineaCotizadaDto> = emptyList(),
    val renglones: Int = 0,
    val articulos: Double = 0.0,
    val totalCentavos: Long = 0,
    val efectivoCentavos: Long = 0,
    val redondeoCentavos: Long = 0,
    val pago: PagoCotizadoDto? = null,
)

/**
 * `POST /api/cajas/{id}/cobrar`. [id], uno por ticket: es el Id de la venta, y mandarlo otra vez no cobra dos veces.
 * [caja], en el turno de qué caja entra (vacía: la única con turno abierto). [cajeroId] vacío: el cajero de ese turno.
 * [totalVisto]: el total exacto que el celular enseñó; si el del servicio es otro, no cobra.
 */
@Serializable
data class SolicitudCobrar(
    val id: String,
    val renglones: List<RenglonMandado>,
    val cobros: List<CobroPedido>,
    val caja: String? = null,
    val cajeroId: String? = null,
    val totalVisto: Long? = null,
    /**
     * Que salga el ticket de esta venta (la casilla del cobro; paso 11 del documento del POS): cierto, que salga; falso, que
     * no. Un POS anterior ignora el campo y la venta queda «sin imprimir».
     */
    val imprimir: Boolean? = null,
)

@Serializable
data class CobroHechoDto(
    val forma: Int = 0,
    val montoCentavos: Long = 0,
    val recibidoCentavos: Long = 0,
    val cambioCentavos: Long = 0,
    val referencia: String? = null,
)

/** La venta cobrada. El ticket para imprimir que también contesta el servicio no se lee: el celular no imprime. */
@Serializable
data class VentaCobradaDto(
    val id: String = "",
    val folio: String = "",
    val folioEleventa: String? = null,
    val cajaId: String = "",
    val caja: String = "",
    val cajero: String = "",
    val renglones: Int = 0,
    val articulos: Double = 0.0,
    val totalCentavos: Long = 0,
    val redondeoCentavos: Long = 0,
    val cobradoCentavos: Long = 0,
    val recibidoCentavos: Long = 0,
    val cambioCentavos: Long = 0,
    val cobros: List<CobroHechoDto> = emptyList(),
    /** Ese Id ya era una venta: no se cobró otra vez. */
    val yaCobrada: Boolean = false,
    /** Qué pasó con el ticket (paso 11); null con un POS que todavía no imprime lo del celular. */
    val impresion: ImpresionDto? = null,
)

// ---- La foto del producto (docs/celular-como-caja.md del POS, paso 12) ----

/** Un producto con foto: su variante y la huella del JPEG guardado (16 cifras hexadecimales). */
@Serializable
data class FotoDto(val varianteId: String, val huella: String = "")

/** `PUT /api/fotos/{varianteId}`: la imagen en base64, sin «data:…». Con la llave de un celular no hace falta el usuario. */
@Serializable
data class SolicitudFoto(val imagenBase64: String, val usuarioId: String? = null)

/** La foto ya guardada (240 px por su lado más largo), para enseñarla sin volver a pedirla. */
@Serializable
data class FotoGuardadaDto(
    val varianteId: String = "",
    val huella: String = "",
    val ancho: Int = 0,
    val alto: Int = 0,
    val bytes: Int = 0,
    val jpegBase64: String = "",
)
