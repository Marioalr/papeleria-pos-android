package mx.papeleriapos.android.botonrapido

import mx.papeleriapos.android.principal.ModoApp

/*
 * Qué ofrece el botón rápido en cada modo (docs/propuesta-interfaz.md, «Qué lleva cada modo»). Cada dirección quiere
 * decir siempre lo mismo: ← busca; ↑ entrega lo que se tiene; ↖ es lo propio del modo. Lo que un modo no tiene deja su
 * lugar vacío (se porta como cancelar) y lo apagado se queda en su lugar con su razón.
 */

/** Lo que hace cada opción. */
enum class Accion { MANDAR, IMPRESIONES, BUSCAR, ENSENAR }

/** Una opción: en su [lugar], con su [nombre] y, si está apagada, [razonApagada] (lo que se dice al soltar en ella). */
data class OpcionRapida(val lugar: Lugar, val accion: Accion, val nombre: String, val razonApagada: String? = null) {
    val encendida: Boolean get() = razonApagada == null
}

/** Lo que se sabe del modo para decidir qué está encendido. */
data class EstadoOpciones(
    /** En Precio: hay un producto en la tarjeta, que es lo que enseña ↑. */
    val hayProducto: Boolean = false,
    /** En Ticket: no trae nada. */
    val ticketVacio: Boolean = true,
)

/**
 * Lo que tiene el botón en [modo]. Vacío: no hay botón (en Conteo, mientras no se cuente contra el POS, el lugar del botón
 * queda vacío; los modos no se recorren). En esta entrega la app todavía no se conecta al POS: «Mandar» e «Impresiones»
 * salen apagadas, en su lugar, para que el gesto se aprenda igual.
 */
fun opcionesDe(modo: ModoApp, estado: EstadoOpciones): List<OpcionRapida> = when (modo) {
    ModoApp.TICKET -> listOf(
        OpcionRapida(
            Lugar.ARRIBA, Accion.MANDAR, "Mandar a una caja",
            if (estado.ticketVacio) "El ticket está vacío: escanea lo que lleva el cliente." else SIN_CONEXION_MANDAR,
        ),
        OpcionRapida(Lugar.DIAGONAL, Accion.IMPRESIONES, "Impresiones", SIN_CONEXION_IMPRESIONES),
        OpcionRapida(Lugar.LADO, Accion.BUSCAR, "Buscar"),
    )
    ModoApp.PRECIO -> listOf(
        OpcionRapida(
            Lugar.ARRIBA, Accion.ENSENAR, "Enseñar al cliente",
            if (estado.hayProducto) null else "Escanea un producto para enseñarlo.",
        ),
        OpcionRapida(Lugar.LADO, Accion.BUSCAR, "Buscar"),
    )
    ModoApp.CONTEO -> emptyList()
}

/** Mandar el ticket a una caja necesita la conexión al POS (entrega 4). */
const val SIN_CONEXION_MANDAR = "Mandarlo a una caja llega con la conexión al POS. Mientras, la caja lo captura."

/** Las impresiones por cobrar viven en la caja principal (entrega 5). */
const val SIN_CONEXION_IMPRESIONES = "Las impresiones llegan con la conexión al POS. Mientras, la caja las agrega con F2."

/** La flecha de cada lugar; en zurdo, en espejo (↗ y →). */
fun flecha(lugar: Lugar, zurdo: Boolean): String = when (lugar) {
    Lugar.ARRIBA -> "↑"
    Lugar.DIAGONAL -> if (zurdo) "↗" else "↖"
    Lugar.LADO -> if (zurdo) "→" else "←"
}

/** La etiqueta de una opción, con su flecha: la animación y la flecha enseñan la dirección del gesto. */
fun etiqueta(opcion: OpcionRapida, zurdo: Boolean): String = "${flecha(opcion.lugar, zurdo)} ${opcion.nombre}"

/** Lo que dice TalkBack del botón: «Opciones del ticket». */
fun nombreDelBoton(modo: ModoApp): String = when (modo) {
    ModoApp.PRECIO -> "Opciones del precio"
    ModoApp.TICKET -> "Opciones del ticket"
    ModoApp.CONTEO -> "Opciones del conteo"
}

/**
 * La burbuja de la primera vez en cada modo, sobre el botón: sus direcciones con lo que hace cada una. Se va al abrir el
 * menú en ese modo. null: el modo no tiene botón.
 */
fun pistaDe(modo: ModoApp, zurdo: Boolean): String? {
    val partes = opcionesDe(modo, EstadoOpciones()).map { "${flecha(it.lugar, zurdo)} ${corto(it.accion)}" }
    return if (partes.isEmpty()) null else "Pulsa y desliza: " + partes.joinToString(" · ")
}

private fun corto(accion: Accion): String = when (accion) {
    Accion.MANDAR -> "mandar"
    Accion.IMPRESIONES -> "impresiones"
    Accion.BUSCAR -> "buscar"
    Accion.ENSENAR -> "enseñar"
}

/** Mientras se abra con toques, arriba del menú, hasta que la persona elija [VECES_PARA_APRENDER] veces deslizando. */
const val PISTA_DESLIZAR = "Pulsa y desliza sin soltar"

/** Las veces que hay que elegir deslizando para que deje de salir [PISTA_DESLIZAR]. */
const val VECES_PARA_APRENDER = 3
