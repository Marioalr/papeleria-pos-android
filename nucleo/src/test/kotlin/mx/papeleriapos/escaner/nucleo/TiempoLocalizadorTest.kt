package mx.papeleriapos.escaner.nucleo

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cuánto tarda el localizador a 1280x720 en la JVM de la PC. No aserta tiempos: la cifra que importa es la del celular
 * más barato (meta p95 < 3 ms); esta sirve para notar si un cambio lo vuelve mucho más caro.
 */
class TiempoLocalizadorTest {
    @Test
    fun `mediana y p95 a 1280x720`() {
        val escenas = listOf(
            "EAN-13 3 px/módulo a 10°" to Planos.codigo(Simbologia.EAN_13, "7509993456786", 3f, base = Escena(giroGrados = 10f, ruido = 3f)),
            "sin código (texto)" to Planos.texto(7),
            "plano con ruido σ=6" to Lienzo.dibujar(BooleanArray(1), 0, 1280, 720, Escena(anchoRelativo = 1e-4f, ruido = 6f)),
        )
        val loc = Localizador()
        for ((nombre, luma) in escenas) {
            val (buf, paso) = Planos.buffer(luma)
            repeat(3000) { loc.procesar(buf, paso, 1280, 720) }
            val t = LongArray(3000)
            for (i in t.indices) t[i] = loc.procesar(buf, paso, 1280, 720).nanos
            t.sort()
            println(
                "localizador 1280x720, %s: mediana %.3f ms, p95 %.3f ms, máx %.3f ms (JVM %s, %s)".format(
                    nombre, t[t.size / 2] / 1e6, t[t.size * 95 / 100] / 1e6, t.last() / 1e6,
                    System.getProperty("java.vm.name"), System.getProperty("os.arch"),
                ),
            )
            assertTrue(t[0] > 0)
        }
    }
}
