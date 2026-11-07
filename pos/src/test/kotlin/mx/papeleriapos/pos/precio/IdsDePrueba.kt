package mx.papeleriapos.pos.precio

import mx.papeleriapos.pos.Cantidad
import java.math.BigDecimal
import java.util.UUID

/** Un Id nuevo, como `Ids.Nuevo()` en las pruebas del POS: ninguna depende de cuál sale. */
internal fun nuevoId(): String = UUID.randomUUID().toString()

/** Como `Cantidad.Unidades(1.5m)` en las pruebas del POS. */
internal fun unidades(texto: String): Cantidad = Cantidad.unidades(BigDecimal(texto))
