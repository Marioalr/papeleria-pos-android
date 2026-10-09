package mx.papeleriapos.escaner.motores

import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Simbologia
import java.nio.ByteBuffer

/**
 * Crea el motor pedido, configurado para [simbologias] (docs/escaner.md, «Motores»). Se llama en el hilo de análisis:
 * zxing-cpp carga su biblioteca nativa al crearse y ML Kit su modelo en la primera lectura ([MotorDeLectura.calentar]).
 *
 * @throws IllegalArgumentException si ninguna de las simbologías la sabe leer el motor.
 */
fun crearMotor(motor: Motor, simbologias: Set<Simbologia>): MotorDeLectura = when (motor) {
    Motor.AUTOMATICO -> MotorAutomatico(simbologias)
    Motor.ML_KIT -> MotorMlKit(simbologias)
    Motor.ZXING_CPP -> MotorZxingCpp(simbologias)
}

/**
 * Copia el plano Y de [izquierda], [arriba], [ancho]×[alto] a [destino] (renglón tras renglón, sin relleno). Mueve la
 * posición de [y] para leer en bloque (`get(int, byte[], …)` absoluto pide API 35) y la deja como estaba.
 */
internal fun copiarPlanoY(
    y: ByteBuffer, pasoFila: Int, izquierda: Int, arriba: Int, ancho: Int, alto: Int, destino: ByteArray, pasoDestino: Int = ancho,
) {
    val posicion = y.position()
    try {
        for (fila in 0 until alto) {
            y.position((arriba + fila) * pasoFila + izquierda)
            y.get(destino, fila * pasoDestino, ancho)
        }
    } finally {
        y.position(posicion)
    }
}

/** Resultado sin nada leído; solo lleva lo que tardó el motor. */
internal fun resultadoVacio(nanos: Long): ResultadoMotor =
    if (nanos == 0L) ResultadoMotor.VACIO else ResultadoMotor(emptyList(), emptyList(), nanos)

/** Arma el resultado sin asignar listas cuando no hay nada (el caso de casi todos los cuadros). */
internal fun resultado(lecturas: ArrayList<Lectura>?, potenciales: ArrayList<FloatArray>?, nanos: Long): ResultadoMotor =
    if (lecturas == null && potenciales == null) resultadoVacio(nanos)
    else ResultadoMotor(lecturas ?: emptyList(), potenciales ?: emptyList(), nanos)
