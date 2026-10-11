package mx.papeleriapos.escaner.motores

import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Confirmador
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.nucleo.Via

/**
 * zxing-cpp primero y ML Kit solo de respaldo (docs/escaner.md, «Motores»). En el emulador zxing-cpp lee la franja en
 * ~0.4 ms y ML Kit en 55–100 ms: correr ML Kit en cada cuadro limita el análisis a 10–15 cuadros por segundo y gasta
 * batería en los cuadros donde no hay nada. Por cuadro, hasta tres pasadas, de la más barata a la más cara:
 *
 * 1. zxing-cpp sobre la franja, tal cual (casi todos los códigos se leen aquí).
 * 2. Si no leyó y el localizador midió el nivel entre barras y espacios: zxing-cpp con ese umbral (~1 ms). Rescata la
 *    etiqueta blanca sobre fondo gris, que su binarizador local no lee.
 * 3. Si tampoco y hay un código probable ([CuadroY.codigoProbable]): ML Kit sobre la región visible completa, a lo más
 *    uno de cada [cadaCuantos] cuadros para que la guía siga fluida; si lee algo en su turno, también en el siguiente,
 *    para la segunda lectura seguida ([TurnoRespaldo]). Con el celular vertical le cuesta menos lo visible que la
 *    franja (medido) y le da contexto a un código difícil (borroso, con reflejo).
 *
 * El ITF lo leen solo las pasadas de zxing-cpp: ML Kit no lo lee (da ceros o basura de 40 dígitos).
 */
class MotorAutomatico(simbologias: Set<Simbologia>, cadaCuantos: Int = 2, probarGiros: Boolean = false) : MotorDeLectura {
    override val motor = Motor.AUTOMATICO

    private val rapido = MotorZxingCpp(simbologias, probarGiros)
    private val respaldo: MotorMlKit? = (simbologias - Simbologia.ITF)
        .takeIf { s -> s.any { aFormatoMlKit(it) != null } }
        ?.let { MotorMlKit(it) }
    private val turno = TurnoRespaldo(cadaCuantos)

    override fun leer(cuadro: CuadroY): ResultadoMotor {
        val directo = rapido.leer(cuadro)
        turno.cuadro()
        if (algunaValida(directo.lecturas) || !cuadro.codigoProbable) return directo

        val conUmbral = rapido.leerConUmbral(cuadro, cuadro.umbralSugerido)
        var nanos = directo.nanos + conUmbral.nanos
        if (algunaValida(conUmbral.lecturas)) return ResultadoMotor(conUmbral.lecturas, emptyList(), nanos, Via.UMBRAL)

        val ml = respaldo
        if (ml == null || !turno.toca()) return conNanos(directo, nanos)
        val visible = CuadroY(
            cuadro.y, cuadro.pasoFila, cuadro.ancho, cuadro.alto, cuadro.rotacion, cuadro.regionVisible,
            proxy = null, regionVisible = cuadro.regionVisible,
        )
        val m = ml.leer(visible)
        nanos += m.nanos
        val leyo = algunaValida(m.lecturas)
        turno.corrio(leyo)
        val lecturas = if (directo.lecturas.isEmpty()) m.lecturas else directo.lecturas + m.lecturas
        return ResultadoMotor(lecturas, m.potencialesBuffer, nanos, if (leyo) Via.RESPALDO else Via.DIRECTA)
    }

    override fun calentar() {
        rapido.calentar()
        respaldo?.calentar()
    }

    override fun close() {
        rapido.close()
        respaldo?.close()
    }

    private fun conNanos(r: ResultadoMotor, nanos: Long) =
        if (r.nanos == nanos) r else ResultadoMotor(r.lecturas, r.potencialesBuffer, nanos, r.via)

    private fun algunaValida(lecturas: List<Lectura>): Boolean {
        for (i in lecturas.indices) if (Confirmador.normalizar(lecturas[i]) != null) return true
        return false
    }
}

/**
 * Cuándo le toca al respaldo: a lo más uno de cada [cadaCuantos] cuadros. Si en su turno lee algo válido, corre también
 * en el cuadro siguiente: en 1D el confirmador pide dos lecturas iguales en cuadros seguidos (docs/escaner.md,
 * «Validación y consenso»), y con un turno sí y otro no un código que solo lee ML Kit nunca entraría. Esa segunda
 * corrida toma el turno que seguía, así que en promedio sigue siendo uno de cada [cadaCuantos].
 */
internal class TurnoRespaldo(private val cadaCuantos: Int) {
    init {
        require(cadaCuantos >= 1) { "El respaldo corre a lo más en cada cuadro." }
    }

    private var desde = 0
    /** El respaldo leyó en su turno, en el cuadro anterior. */
    private var leyoEnTurno = false
    /** Este cuadro es el siguiente a una lectura del respaldo en su turno. */
    private var segunda = false

    /** Empieza un cuadro (se llama en todos, haga falta o no el respaldo). */
    fun cuadro() {
        if (desde < cadaCuantos) desde++
        segunda = leyoEnTurno
        leyoEnTurno = false
    }

    /** En este cuadro puede correr el respaldo. */
    fun toca() = desde >= cadaCuantos || segunda

    /** El respaldo corrió en este cuadro y [leyo] algo válido o no. */
    fun corrio(leyo: Boolean) {
        // La segunda corrida, adelantada, toma el turno que seguía.
        desde = if (desde < cadaCuantos) 1 - cadaCuantos else 0
        leyoEnTurno = leyo && !segunda
    }
}
