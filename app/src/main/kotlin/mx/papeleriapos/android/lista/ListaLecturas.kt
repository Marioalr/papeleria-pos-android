package mx.papeleriapos.android.lista

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.escaner.nucleo.Simbologia

/** Un código de la lista con cuántas veces se ha sumado. */
data class Renglon(
    val id: Long,
    /** La forma normalizada: así un UPC-A de 12 dígitos y su EAN-13 caen en el mismo renglón. */
    val codigo: String,
    /** [Simbologia.OTRA] si no se escaneó: se agregó buscando en el catálogo. */
    val simbologia: Simbologia,
    val cantidad: Int,
    /** Hora de la última suma (ms del reloj de pared). */
    val hora: Long,
    /**
     * El código con que el producto está en el catálogo (la clave que encontró la regla de UPC y EAN), o null si no está
     * o no se sabía al sumar. Lo escaneado y lo buscado caen en el mismo renglón por ella, y es lo que va al QR del ticket
     * (docs/propuesta-interfaz.md, «Mandar a una caja»): así la caja lo encuentra aunque no pruebe las variantes.
     */
    val clave: String? = null,
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

    /**
     * Suma una lectura del escáner ([cantidad] > 1: al juntar con lo guardado). Cae en el renglón del mismo código o, con
     * [clave], en el del mismo producto del catálogo (uno que se agregó buscando, o el mismo producto con otra variante
     * de UPC o EAN), que sube arriba.
     */
    fun sumar(codigo: String, simbologia: Simbologia, hora: Long, cantidad: Int = 1, clave: String? = null): ListaLecturas {
        val i = renglones.indexOfFirst { it.codigo == codigo || (clave != null && it.clave == clave) }
        if (i < 0) {
            val nuevo = Renglon(siguienteId, codigo, simbologia, cantidad, hora, clave)
            return cambiar(listOf(nuevo) + renglones, siguienteId + 1)
        }
        val r = renglones[i]
        val sumado = r.copy(cantidad = r.cantidad + cantidad, hora = hora, clave = r.clave ?: clave)
        return cambiar(listOf(sumado) + renglones.filterIndexed { k, _ -> k != i }, siguienteId)
    }

    /** Agrega lo que se eligió al buscar en el catálogo, por su [clave]: suma como una lectura de ese producto. */
    fun agregarDelCatalogo(clave: String, hora: Long, cantidad: Int = 1): ListaLecturas =
        sumar(clave, Simbologia.OTRA, hora, cantidad, clave)

    /** +1 al renglón [id], sin moverlo (en el ticket completo, cada renglón tiene su − y su +). */
    fun masUno(id: Long, hora: Long): ListaLecturas = cambiarRenglon(id) { it.copy(cantidad = it.cantidad + 1, hora = hora) }

    /** −1 al renglón [id], sin moverlo; en 1 lo quita (el − es el bote, y se deshace). */
    fun menosUno(id: Long): ListaLecturas = cambiarRenglon(id) { if (it.cantidad > 1) it.copy(cantidad = it.cantidad - 1) else null }

    /** Quita el renglón [id] entero. */
    fun quitar(id: Long): ListaLecturas = cambiarRenglon(id) { null }

    /** La [cantidad] tecleada para el renglón [id] («20» copias); 0 lo quita. */
    fun conCantidad(id: Long, cantidad: Int, hora: Long): ListaLecturas =
        cambiarRenglon(id) { if (cantidad > 0) it.copy(cantidad = cantidad, hora = hora) else null }

    /** El renglón [id] cambiado por [cambio] en su lugar (null lo quita). Si no está o no cambia, la misma lista. */
    private fun cambiarRenglon(id: Long, cambio: (Renglon) -> Renglon?): ListaLecturas {
        val i = renglones.indexOfFirst { it.id == id }
        if (i < 0) return this
        val nuevo = cambio(renglones[i])
        if (nuevo == renglones[i]) return this
        val lista = renglones.toMutableList()
        if (nuevo == null) lista.removeAt(i) else lista[i] = nuevo
        return cambiar(lista, siguienteId)
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

    /** Para guardarla en disco: un CSV con un renglón por código (sin lo de deshacer, que no sobrevive al proceso). */
    fun aTexto(): String {
        val sb = StringBuilder()
        Csv.renglon(COLUMNAS, sb)
        for (r in renglones) {
            Csv.renglon(
                listOf(r.id.toString(), r.codigo, r.simbologia.name, r.cantidad.toString(), r.hora.toString(), r.clave.orEmpty()),
                sb,
            )
        }
        return sb.toString()
    }

    companion object {
        /** Suficiente para corregir una racha de lecturas de más sin guardar la historia entera. */
        const val PASOS_PARA_DESHACER = 50

        private val COLUMNAS = listOf("id", "codigo", "simbologia", "cantidad", "hora", "clave")

        /** Las de la versión 0.1, sin la clave: lo que quedó guardado se sigue leyendo al actualizar la app. */
        private val COLUMNAS_SIN_CLAVE = COLUMNAS.dropLast(1)

        /** Lo contrario de [aTexto]; un renglón que no se entiende se salta. Sin pasos para deshacer. */
        fun deTexto(texto: String): ListaLecturas {
            val filas = Csv.leer(texto)
            if (filas.isEmpty() || (filas[0] != COLUMNAS && filas[0] != COLUMNAS_SIN_CLAVE)) return ListaLecturas()
            val renglones = filas.drop(1).mapNotNull { f ->
                if (f.size < COLUMNAS_SIN_CLAVE.size) return@mapNotNull null
                val id = f[0].toLongOrNull() ?: return@mapNotNull null
                val simbologia = Simbologia.entries.firstOrNull { it.name == f[2] } ?: return@mapNotNull null
                val cantidad = f[3].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val clave = f.getOrNull(5)?.takeIf { it.isNotEmpty() }
                Renglon(id, f[1], simbologia, cantidad, f[4].toLongOrNull() ?: 0L, clave)
            }.distinctBy { it.codigo }
            return ListaLecturas(renglones, emptyList(), (renglones.maxOfOrNull { it.id } ?: 0L) + 1)
        }

        /**
         * [guardada] (la que se leyó del disco) con lo que se sumó en [nueva] mientras se leía: lo nuevo queda arriba, como
         * si se hubiera escaneado después. Si no se sumó nada, [guardada] tal cual.
         */
        fun unir(guardada: ListaLecturas, nueva: ListaLecturas): ListaLecturas {
            if (nueva.renglones.isEmpty() && !nueva.puedeDeshacer) return guardada
            var r = guardada
            for (n in nueva.renglones.asReversed()) r = r.sumar(n.codigo, n.simbologia, n.hora, n.cantidad, n.clave)
            return r
        }
    }
}
