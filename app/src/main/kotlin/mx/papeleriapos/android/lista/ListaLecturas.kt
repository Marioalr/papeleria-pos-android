package mx.papeleriapos.android.lista

import mx.papeleriapos.escaner.nucleo.Simbologia

/** Un código de la lista con cuántas veces se ha sumado. */
data class Renglon(
    val id: Long,
    /** La forma normalizada: así un UPC-A de 12 dígitos y su EAN-13 caen en el mismo renglón. */
    val codigo: String,
    val simbologia: Simbologia,
    val cantidad: Int,
    /** Hora de la última suma (ms del reloj de pared). */
    val hora: Long,
)

/**
 * Lo leído en modo venta o conteo: un código nuevo agrega un renglón arriba y uno que ya está suma uno y sube; el de
 * arriba es el actual (al que se aplican +1 y −1). Es inmutable para que Compose note cada cambio, y guarda las listas
 * anteriores para deshacer cualquier paso, incluido vaciar.
 */
class ListaLecturas private constructor(
    /** El más reciente primero. */
    val renglones: List<Renglon>,
    private val anteriores: List<List<Renglon>>,
    private val siguienteId: Long,
) {
    constructor() : this(emptyList(), emptyList(), 1)

    val actual: Renglon? get() = renglones.firstOrNull()
    val total: Int get() = renglones.sumOf { it.cantidad }
    val puedeDeshacer: Boolean get() = anteriores.isNotEmpty()

    /** Suma una lectura del escáner. */
    fun sumar(codigo: String, simbologia: Simbologia, hora: Long): ListaLecturas {
        val i = renglones.indexOfFirst { it.codigo == codigo }
        if (i < 0) {
            val nuevo = Renglon(siguienteId, codigo, simbologia, 1, hora)
            return cambiar(listOf(nuevo) + renglones, siguienteId + 1)
        }
        val sumado = renglones[i].copy(cantidad = renglones[i].cantidad + 1, hora = hora)
        return cambiar(listOf(sumado) + renglones.filterIndexed { k, _ -> k != i }, siguienteId)
    }

    /** +1 al actual (el botón junto al último producto). */
    fun masUno(hora: Long): ListaLecturas {
        val a = actual ?: return this
        return cambiar(listOf(a.copy(cantidad = a.cantidad + 1, hora = hora)) + renglones.drop(1), siguienteId)
    }

    /** −1 al actual; si llega a cero se quita. */
    fun menosUno(): ListaLecturas {
        val a = actual ?: return this
        val resto = renglones.drop(1)
        return cambiar(if (a.cantidad > 1) listOf(a.copy(cantidad = a.cantidad - 1)) + resto else resto, siguienteId)
    }

    fun vaciar(): ListaLecturas = if (renglones.isEmpty()) this else cambiar(emptyList(), siguienteId)

    /** Regresa al estado de antes del último paso. */
    fun deshacer(): ListaLecturas =
        if (anteriores.isEmpty()) this else ListaLecturas(anteriores.last(), anteriores.dropLast(1), siguienteId)

    private fun cambiar(nuevos: List<Renglon>, id: Long) =
        ListaLecturas(nuevos, (anteriores + listOf(renglones)).takeLast(PASOS_PARA_DESHACER), id)

    companion object {
        /** Suficiente para corregir una racha de lecturas de más sin guardar la historia entera. */
        const val PASOS_PARA_DESHACER = 50
    }
}
