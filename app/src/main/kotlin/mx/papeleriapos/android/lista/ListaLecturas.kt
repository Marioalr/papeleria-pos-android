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
    /**
     * El orden en que llegó a donde está: solo [ListaLecturas.sumar] lo da (y sube el renglón), así que es mayor arriba.
     * Con él, el «Deshacer» de «Quitado del ticket» sabe qué se escaneó después aunque los renglones sean los mismos: el
     * + del ticket completo no mueve nada y no lo cambia. No va a disco; al leerlo, sale del orden de la lista.
     */
    val llegada: Long = 0,
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
    /** La [Renglon.llegada] de la próxima suma: solo crece, también al deshacer (como [siguienteId]). */
    private val siguienteLlegada: Long,
) {
    constructor() : this(emptyList(), emptyList(), 1, 1)

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
            val nuevo = Renglon(siguienteId, codigo, simbologia, cantidad, hora, clave, llegada = siguienteLlegada)
            return cambiar(listOf(nuevo) + renglones, siguienteId + 1, siguienteLlegada + 1)
        }
        val r = renglones[i]
        val sumado = r.copy(cantidad = r.cantidad + cantidad, hora = hora, clave = r.clave ?: clave, llegada = siguienteLlegada)
        return cambiar(listOf(sumado) + renglones.filterIndexed { k, _ -> k != i }, siguienteId, siguienteLlegada + 1)
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
        if (anteriores.isEmpty()) this else ListaLecturas(anteriores.last(), anteriores.dropLast(1), siguienteId, siguienteLlegada)

    // ---- Lo que deshace cada aviso (docs/propuesta-interfaz.md, «Ticket»: quitar se puede deshacer) ----
    // El «Deshacer» de un aviso deshace lo que nombra el aviso aunque después se haya leído otra cosa: el último paso de
    // la lista sería esa lectura. Cada uno es un paso más, que el «Deshacer» del ticket completo también regresa. Si ese
    // «Deshacer» ya lo regresó, el del aviso no hace nada: lo haría dos veces.

    /**
     * «Quitado del ticket»: [quitado] regresa a su lugar como si no se hubiera quitado: entre los renglones que tenía
     * arriba y abajo en [tras] (la lista justo después de quitarlo, donde le tocaba el lugar [indice]) y abajo de lo que se
     * escaneó después, que [sumar] subió arriba («lo escaneado después queda arriba»), aunque sea un renglón que ya estaba
     * arriba y no se movió. Por el lugar de antes en la lista de ahora no basta: con una lectura en medio quedaría arriba
     * de un renglón que tenía arriba. Si mientras tanto se volvió a sumar su producto, lo quitado se le suma a ese renglón,
     * que se queda donde está (no quedan dos renglones del mismo).
     */
    fun regresar(quitado: Renglon, indice: Int, tras: List<Renglon>): ListaLecturas {
        // Ya está: el «Deshacer» del ticket completo lo regresó.
        if (renglones.any { it.id == quitado.id && it.codigo == quitado.codigo }) return this
        val lista = renglones.toMutableList()
        val i = lista.indexOfFirst { it.mismoQue(quitado) }
        if (i >= 0) {
            val r = lista[i]
            lista[i] = r.copy(cantidad = r.cantidad + quitado.cantidad, clave = r.clave ?: quitado.clave)
            return cambiar(lista, siguienteId)
        }
        // Lo escaneado después quedó arriba, con una llegada más nueva que todo lo que había al quitarlo. Por los ids no se
        // sabe: releer el que quedó arriba no lo mueve, y deja la misma lista que tocar su +.
        val alQuitar = maxOf(quitado.llegada, tras.maxOfOrNull { it.llegada } ?: 0L)
        val despues = lista.indexOfFirst { it.llegada <= alQuitar }.let { if (it < 0) lista.size else it }
        // Abajo de eso, lo que no se movió sigue en el orden de [tras]. De abajo hacia arriba, mientras el orden sea el de
        // [tras], es lo de antes.
        val lugarEnTras = tras.withIndex().associate { (k, r) -> r.id to k }
        var desde = lista.size
        var debajo = Int.MAX_VALUE
        for (k in (despues until lista.size).reversed()) {
            val t = lugarEnTras[lista[k].id] ?: break
            if (t >= debajo) break
            debajo = t
            desde = k
        }
        // Entre lo de antes, arriba del primero que tenía abajo.
        val lugar = (desde until lista.size).firstOrNull { lugarEnTras.getValue(lista[it].id) >= indice } ?: lista.size
        // Su id no se vuelve a dar (siguienteId solo crece); por si acaso, uno nuevo si ya lo trae otro.
        val r = if (lista.any { it.id == quitado.id }) quitado.copy(id = siguienteId) else quitado
        lista.add(lugar, r)
        return cambiar(lista, maxOf(siguienteId, r.id + 1))
    }

    /** «… al ticket»: quita esa pieza del producto [clave] (en 1, el renglón). La misma lista si ya no está. */
    fun quitarUna(clave: String): ListaLecturas {
        val r = renglones.firstOrNull { it.clave == clave || it.codigo == clave } ?: return this
        return menosUno(r.id)
    }

    /**
     * «Ticket vacío: empieza otro»: regresa [antes], lo que había al vaciarlo, con lo que se sumó después arriba, como si
     * se hubiera escaneado al último (lo mismo que [unir] con lo guardado): no se pierde lo leído después.
     */
    fun conLoDeAntes(antes: List<Renglon>): ListaLecturas {
        if (antes.isEmpty()) return this
        // Ya está: el «Deshacer» del ticket completo lo regresó (los renglones de antes traen su id).
        if (antes.any { a -> renglones.any { it.id == a.id && it.codigo == a.codigo } }) return this
        val lista = antes.toMutableList()
        for (n in renglones.asReversed()) {
            val i = lista.indexOfFirst { it.mismoQue(n) }
            if (i < 0) {
                lista.add(0, n)
            } else {
                val r = lista.removeAt(i)
                lista.add(0, r.copy(cantidad = r.cantidad + n.cantidad, hora = n.hora, clave = r.clave ?: n.clave, llegada = n.llegada))
            }
        }
        return cambiar(lista, maxOf(siguienteId, (lista.maxOfOrNull { it.id } ?: 0L) + 1))
    }

    /**
     * Los renglones sin la clave del catálogo (lo que sumó el escaneo rápido antes de la 0.3, lo guardado por la 0.1) con
     * la que diga [clave], sin un paso para deshacer: así lo mismo con otra variante del código, o elegido en Buscar, cae
     * en su renglón. La misma lista si no cambia nada.
     */
    fun completarClaves(clave: (Renglon) -> String?): ListaLecturas {
        if (renglones.none { it.clave == null }) return this
        var cambio = false
        val nuevos = renglones.map { r ->
            if (r.clave != null) r else clave(r)?.let { cambio = true; r.copy(clave = it) } ?: r
        }
        return if (cambio) ListaLecturas(nuevos, anteriores, siguienteId, siguienteLlegada) else this
    }

    /** El mismo código o el mismo producto del catálogo: como lo junta [sumar]. */
    private fun Renglon.mismoQue(otro: Renglon) = codigo == otro.codigo || (otro.clave != null && clave == otro.clave)

    private fun cambiar(nuevos: List<Renglon>, id: Long, llegada: Long = siguienteLlegada) =
        ListaLecturas(nuevos, (anteriores + listOf(renglones)).takeLast(PASOS_PARA_DESHACER), id, llegada)

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

        /**
         * Lo contrario de [aTexto]; un renglón que no se entiende se salta. Sin pasos para deshacer, y la
         * [Renglon.llegada] de cada uno sale de su lugar (la de arriba es la mayor).
         */
        fun deTexto(texto: String): ListaLecturas {
            val filas = Csv.leer(texto)
            if (filas.isEmpty() || (filas[0] != COLUMNAS && filas[0] != COLUMNAS_SIN_CLAVE)) return ListaLecturas()
            val leidos = filas.drop(1).mapNotNull { f ->
                if (f.size < COLUMNAS_SIN_CLAVE.size) return@mapNotNull null
                val id = f[0].toLongOrNull() ?: return@mapNotNull null
                val simbologia = Simbologia.entries.firstOrNull { it.name == f[2] } ?: return@mapNotNull null
                val cantidad = f[3].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val clave = f.getOrNull(5)?.takeIf { it.isNotEmpty() }
                Renglon(id, f[1], simbologia, cantidad, f[4].toLongOrNull() ?: 0L, clave)
            }.distinctBy { it.codigo }
            val renglones = leidos.mapIndexed { k, r -> r.copy(llegada = (leidos.size - k).toLong()) }
            return ListaLecturas(renglones, emptyList(), (renglones.maxOfOrNull { it.id } ?: 0L) + 1, renglones.size + 1L)
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
