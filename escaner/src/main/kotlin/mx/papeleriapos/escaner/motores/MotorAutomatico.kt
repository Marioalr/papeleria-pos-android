package mx.papeleriapos.escaner.motores

import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Confirmador
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Simbologia

/**
 * zxing-cpp en cada cuadro y ML Kit solo de respaldo (docs/escaner.md, «Motores»). En el emulador zxing-cpp lee la
 * franja en ~0.4 ms y ML Kit en 55–100 ms; correr ML Kit en cada cuadro limita el análisis a 10–15 cuadros por segundo y
 * gasta batería en los cuadros donde no hay nada.
 *
 * ML Kit entra cuando zxing-cpp no leyó nada válido pero hay un código probable ([CuadroY.codigoProbable]: el localizador
 * lo ve o ya había candidato), a lo más uno de cada [cadaCuantos] cuadros para que la guía siga fluida. Mira la región
 * visible completa: con el celular vertical le cuesta menos que la franja (medido) y le da contexto a un código difícil.
 * El ITF lo lee solo zxing-cpp: ML Kit no lo lee (da ceros o basura de 40 dígitos).
 */
class MotorAutomatico(simbologias: Set<Simbologia>, private val cadaCuantos: Int = 2) : MotorDeLectura {
    override val motor = Motor.AUTOMATICO

    private val rapido = MotorZxingCpp(simbologias)
    private val respaldo: MotorMlKit? = (simbologias - Simbologia.ITF)
        .takeIf { s -> s.any { aFormatoMlKit(it) != null } }
        ?.let { MotorMlKit(it) }
    private var desdeRespaldo = 0

    override fun leer(cuadro: CuadroY): ResultadoMotor {
        val r = rapido.leer(cuadro)
        desdeRespaldo++
        val ml = respaldo
        if (ml == null || algunaValida(r.lecturas) || !cuadro.codigoProbable || desdeRespaldo < cadaCuantos) return r
        desdeRespaldo = 0
        val visible = CuadroY(
            cuadro.y, cuadro.pasoFila, cuadro.ancho, cuadro.alto, cuadro.rotacion, cuadro.regionVisible,
            proxy = null, regionVisible = cuadro.regionVisible, codigoProbable = true,
        )
        val m = ml.leer(visible)
        val lecturas = when {
            m.lecturas.isEmpty() -> r.lecturas
            r.lecturas.isEmpty() -> m.lecturas
            else -> r.lecturas + m.lecturas
        }
        val potenciales = if (r.potencialesBuffer.isEmpty()) m.potencialesBuffer else r.potencialesBuffer + m.potencialesBuffer
        return ResultadoMotor(lecturas, potenciales, r.nanos + m.nanos, deRespaldo = algunaValida(m.lecturas))
    }

    override fun calentar() {
        rapido.calentar()
        respaldo?.calentar()
    }

    override fun close() {
        rapido.close()
        respaldo?.close()
    }

    private fun algunaValida(lecturas: List<Lectura>): Boolean {
        for (i in lecturas.indices) if (Confirmador.normalizar(lecturas[i]) != null) return true
        return false
    }
}
