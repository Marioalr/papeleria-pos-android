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
 *    uno de cada [cadaCuantos] cuadros para que la guía siga fluida. Con el celular vertical le cuesta menos lo visible
 *    que la franja (medido) y le da contexto a un código difícil (borroso, con reflejo).
 *
 * El ITF lo leen solo las pasadas de zxing-cpp: ML Kit no lo lee (da ceros o basura de 40 dígitos).
 */
class MotorAutomatico(simbologias: Set<Simbologia>, private val cadaCuantos: Int = 2) : MotorDeLectura {
    override val motor = Motor.AUTOMATICO

    private val rapido = MotorZxingCpp(simbologias)
    private val respaldo: MotorMlKit? = (simbologias - Simbologia.ITF)
        .takeIf { s -> s.any { aFormatoMlKit(it) != null } }
        ?.let { MotorMlKit(it) }
    private var desdeRespaldo = 0

    override fun leer(cuadro: CuadroY): ResultadoMotor {
        val directo = rapido.leer(cuadro)
        desdeRespaldo++
        if (algunaValida(directo.lecturas) || !cuadro.codigoProbable) return directo

        val conUmbral = rapido.leerConUmbral(cuadro, cuadro.umbralSugerido)
        var nanos = directo.nanos + conUmbral.nanos
        if (algunaValida(conUmbral.lecturas)) return ResultadoMotor(conUmbral.lecturas, emptyList(), nanos, Via.UMBRAL)

        val ml = respaldo
        if (ml == null || desdeRespaldo < cadaCuantos) return conNanos(directo, nanos)
        desdeRespaldo = 0
        val visible = CuadroY(
            cuadro.y, cuadro.pasoFila, cuadro.ancho, cuadro.alto, cuadro.rotacion, cuadro.regionVisible,
            proxy = null, regionVisible = cuadro.regionVisible,
        )
        val m = ml.leer(visible)
        nanos += m.nanos
        val lecturas = if (directo.lecturas.isEmpty()) m.lecturas else directo.lecturas + m.lecturas
        return ResultadoMotor(lecturas, m.potencialesBuffer, nanos, if (algunaValida(m.lecturas)) Via.RESPALDO else Via.DIRECTA)
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
