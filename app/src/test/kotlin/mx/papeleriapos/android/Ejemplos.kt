package mx.papeleriapos.android

import mx.papeleriapos.escaner.EventoLectura
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.nucleo.Aceptada
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.CodigoLeido
import mx.papeleriapos.escaner.nucleo.Indicacion
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.nucleo.Via

/** Lecturas de ejemplo para las pruebas, como las entregaría el controlador. */
object Ejemplos {
    fun evento(
        original: String,
        simbologia: Simbologia = Simbologia.EAN_13,
        normalizado: String = original,
        claves: List<String> = listOf(normalizado),
        motor: Motor = Motor.ML_KIT,
        ms: Long = 180,
        indicaciones: List<Indicacion> = listOf(Indicacion.ACERCATE, Indicacion.QUIETO),
        moduloPx: Float = 2.5f,
        zoom: Float = 1.5f,
        linterna: Boolean = false,
        via: Via = Via.DIRECTA,
        distanciaCm: Float = 12.5f,
        msCuadro: Float = 8.4f,
    ) = EventoLectura(
        aceptada = Aceptada(CodigoLeido(original, normalizado, simbologia, claves), Caja(0.2f, 0.4f, 0.8f, 0.6f), 0L, false),
        msDesdeCandidato = ms,
        indicaciones = indicaciones,
        motor = motor,
        via = via,
        moduloPx = moduloPx,
        zoom = zoom,
        linterna = linterna,
        distanciaCm = distanciaCm,
        msCuadro = msCuadro,
    )

    /** Un UPC-A como lo da ML Kit (12 dígitos), ya normalizado a 13. */
    val upcA = evento(
        "099999291452", Simbologia.UPC_A, normalizado = "0099999291452",
        claves = listOf("099999291452", "0099999291452"),
    )
}
