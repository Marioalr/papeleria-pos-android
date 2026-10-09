package mx.papeleriapos.escaner.ui

import mx.papeleriapos.escaner.Fase
import org.junit.Assert.assertEquals
import org.junit.Test

class PantallaEncendidaTest {
    @Test
    fun laPantallaSeQuedaPrendidaSoloMientrasAbreEscaneaOConfirma() {
        val prendida = Fase.entries.filter { mantenerPantallaEncendida(it) }.toSet()
        assertEquals(setOf(Fase.ABRIENDO, Fase.ESCANEANDO, Fase.VIENDO, Fase.LEIDO), prendida)
        // En pausa (sobre el bloqueo, sin escanear), con error o sin permiso se apaga sola: no gasta batería.
        val apagable = Fase.entries.toSet() - prendida
        assertEquals(Fase.entries.toSet() - setOf(Fase.ABRIENDO, Fase.ESCANEANDO, Fase.VIENDO, Fase.LEIDO), apagable)
        for (f in listOf(Fase.PAUSADO, Fase.ERROR, Fase.SIN_PERMISO)) assertEquals(f.name, false, mantenerPantallaEncendida(f))
    }
}
