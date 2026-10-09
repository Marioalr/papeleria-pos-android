package mx.papeleriapos.escaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AperturaTest {
    private val ms = 1_000_000L

    @Before
    fun limpiar() {
        MarcasApertura.nueva(TipoArranque.DESCONOCIDO)
    }

    @Test
    fun msSeMideDesdeLaPrimeraMarcaQueHaya() {
        val a = Apertura(
            TipoArranque.FRIO,
            mapOf(
                PasoApertura.PROCESO to 1_000 * ms,
                PasoApertura.ACTIVIDAD to 1_120 * ms,
                PasoApertura.CAMARA_ABIERTA to 1_510 * ms,
                PasoApertura.PRIMER_ANALISIS to 1_640 * ms,
            ),
        )
        assertEquals(1_000 * ms, a.inicio)
        assertEquals(0L, a.ms(PasoApertura.PROCESO))
        assertEquals(120L, a.ms(PasoApertura.ACTIVIDAD))
        assertEquals(640L, a.ms(PasoApertura.PRIMER_ANALISIS))
        assertEquals(-1L, a.ms(PasoApertura.PRIMERA_LECTURA))
    }

    @Test
    fun enCalienteElInicioEsLaActividadQueVolvio() {
        // Sin proceso ni Application: el inicio es la primera marca que haya.
        val a = Apertura(
            TipoArranque.CALIENTE,
            mapOf(PasoApertura.ACTIVIDAD to 50_000 * ms, PasoApertura.PRIMER_ANALISIS to 50_210 * ms),
        )
        assertEquals(210L, a.ms(PasoApertura.PRIMER_ANALISIS))
    }

    @Test
    fun sinMarcasNoHayTiempos() {
        val a = Apertura()
        assertEquals(0L, a.inicio)
        assertEquals(-1L, a.ms(PasoApertura.ACTIVIDAD))
        assertEquals("desconocido:", a.resumen())
    }

    @Test
    fun elResumenVaEnElOrdenDeLosPasosYSoloConLoQueOcurrio() {
        val a = Apertura(
            TipoArranque.TIBIO,
            // En otro orden a propósito: el resumen sigue el de PasoApertura.
            mapOf(
                PasoApertura.PRIMER_ANALISIS to 1_400 * ms,
                PasoApertura.ACTIVIDAD to 1_000 * ms,
                PasoApertura.ENLACE_FIN to 1_090 * ms,
                PasoApertura.ENLACE_INICIO to 1_010 * ms,
            ),
        )
        assertEquals("tibio: actividad=0 enlace_inicio=10 enlace_fin=90 primer_analisis=400", a.resumen())
    }

    @Test
    fun cadaMarcaSePoneUnaVezPorApertura() {
        assertTrue(MarcasApertura.marcar(PasoApertura.ACTIVIDAD, 10 * ms))
        assertFalse(MarcasApertura.marcar(PasoApertura.ACTIVIDAD, 20 * ms))
        assertEquals(10 * ms, MarcasApertura.apertura.marcas[PasoApertura.ACTIVIDAD])
        assertTrue(MarcasApertura.tiene(PasoApertura.ACTIVIDAD))
        assertFalse(MarcasApertura.tiene(PasoApertura.PRIMER_ANALISIS))
        // Una hora inválida no marca.
        assertFalse(MarcasApertura.marcar(PasoApertura.PRIMER_ANALISIS, 0L))
        assertFalse(MarcasApertura.tiene(PasoApertura.PRIMER_ANALISIS))
    }

    @Test
    fun unaAperturaNuevaEmpiezaDeCeroSalvoLoQueSeConserva() {
        MarcasApertura.marcar(PasoApertura.PROCESO, 1 * ms)
        MarcasApertura.marcar(PasoApertura.APLICACION, 2 * ms)
        MarcasApertura.marcar(PasoApertura.PRIMER_ANALISIS, 3 * ms)
        MarcasApertura.nueva(TipoArranque.CALIENTE, conservar = setOf(PasoApertura.PROCESO))
        val a = MarcasApertura.apertura
        assertEquals(TipoArranque.CALIENTE, a.tipo)
        assertEquals(setOf(PasoApertura.PROCESO), a.marcas.keys)
        // Y se vuelve a poder marcar lo que no se conservó.
        assertTrue(MarcasApertura.marcar(PasoApertura.PRIMER_ANALISIS, 9 * ms))
    }

    @Test
    fun cambiarElTipoConservaLasMarcas() {
        MarcasApertura.marcar(PasoApertura.PROCESO, 1 * ms)
        MarcasApertura.tipo(TipoArranque.FRIO)
        assertEquals(TipoArranque.FRIO, MarcasApertura.apertura.tipo)
        assertEquals(1 * ms, MarcasApertura.apertura.marcas[PasoApertura.PROCESO])
    }

    @Test
    fun frioSoloSiEsLaPrimeraActividadYElProcesoNacioParaElla() {
        val app = 5_000 * ms
        assertEquals(TipoArranque.FRIO, tipoDeArranque(true, app, app + 150 * ms))
        // Justo en el umbral todavía es frío; pasado, el proceso ya vivía (lo arrancó el mosaico al abrir el panel).
        assertEquals(TipoArranque.FRIO, tipoDeArranque(true, app, app + UMBRAL_PROCESO_PRECALENTADO_NS))
        assertEquals(TipoArranque.TIBIO, tipoDeArranque(true, app, app + UMBRAL_PROCESO_PRECALENTADO_NS + 1))
        assertEquals(TipoArranque.TIBIO, tipoDeArranque(false, app, app + 100 * ms))
        assertEquals(TipoArranque.DESCONOCIDO, tipoDeArranque(true, 0L, app))
    }
}
