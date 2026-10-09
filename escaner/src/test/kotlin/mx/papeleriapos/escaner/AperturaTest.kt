package mx.papeleriapos.escaner

import android.app.ApplicationStartInfo
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
    fun cadaControladorMarcaSoloEnSuApertura() {
        // El controlador de la app tomó el número de su apertura (en onCreate u ON_RESUME).
        val deLaApp = MarcasApertura.numero
        assertTrue(MarcasApertura.esActual(deLaApp))
        assertTrue(MarcasApertura.marcarSi(deLaApp, PasoApertura.PRIMER_ANALISIS, 10 * ms))
        // Se abre el escaneo rápido: otra apertura. Un cuadro en vuelo del controlador de la app ya no marca en ella.
        MarcasApertura.nueva(TipoArranque.TIBIO)
        val delRapido = MarcasApertura.numero
        assertFalse(MarcasApertura.esActual(deLaApp))
        assertFalse(MarcasApertura.marcarSi(deLaApp, PasoApertura.PRIMER_CUADRO_SENSOR, 20 * ms))
        assertFalse(MarcasApertura.marcarSi(deLaApp, PasoApertura.PRIMER_ANALISIS, 20 * ms))
        assertFalse(MarcasApertura.tiene(PasoApertura.PRIMER_ANALISIS))
        // El del escaneo rápido sí.
        assertTrue(MarcasApertura.marcarSi(delRapido, PasoApertura.PRIMER_ANALISIS, 30 * ms))
        assertEquals(30 * ms, MarcasApertura.apertura.marcas[PasoApertura.PRIMER_ANALISIS])
        // Sin número (el ciclo no está al frente) nunca marca.
        assertFalse(MarcasApertura.marcarSi(0, PasoApertura.PRIMERA_LECTURA, 40 * ms))
    }

    @Test
    fun laAppEsperaElPrimerAnalisisDeSuPropiaApertura() {
        val propia = MarcasApertura.numero
        assertFalse(MarcasApertura.tieneEn(propia, PasoApertura.PRIMER_ANALISIS))
        MarcasApertura.marcarSi(propia, PasoApertura.PRIMER_ANALISIS, 10 * ms)
        assertTrue(MarcasApertura.tieneEn(propia, PasoApertura.PRIMER_ANALISIS))
        // Otra apertura que ya tiene su primer análisis no cuenta para la anterior.
        MarcasApertura.nueva(TipoArranque.CALIENTE)
        MarcasApertura.marcarSi(MarcasApertura.numero, PasoApertura.PRIMER_ANALISIS, 20 * ms)
        assertFalse(MarcasApertura.tieneEn(propia, PasoApertura.PRIMER_ANALISIS))
        assertTrue(MarcasApertura.tieneEn(MarcasApertura.numero, PasoApertura.PRIMER_ANALISIS))
    }

    @Test
    fun cadaAperturaNuevaTieneOtroNumeroYCambiarElTipoNo() {
        val antes = MarcasApertura.numero
        MarcasApertura.tipo(TipoArranque.FRIO)
        assertEquals(antes, MarcasApertura.numero)
        MarcasApertura.nueva(TipoArranque.CALIENTE)
        assertEquals(antes + 1, MarcasApertura.numero)
    }

    @Test
    fun conElRegistroDelSistemaMandaElOrigenDelProceso() {
        val app = 5_000 * ms
        // Nació para una Activity: frío aunque haya tardado (gama baja sin el perfil compilado).
        assertEquals(TipoArranque.FRIO, tipoDeArranque(true, OrigenProceso.ACTIVIDAD, app, app + 8_000 * ms))
        // Nació por el mosaico (al abrir el panel) y el toque llegó rápido: tibio de todos modos.
        assertEquals(TipoArranque.TIBIO, tipoDeArranque(true, OrigenProceso.OTRO, app, app + 300 * ms))
        // No es la primera Activity del proceso: tibio, diga lo que diga el registro.
        assertEquals(TipoArranque.TIBIO, tipoDeArranque(false, OrigenProceso.ACTIVIDAD, app, app + 100 * ms))
    }

    @Test
    fun sinElRegistroDecideElTiempoDesdeLaApplication() {
        val app = 5_000 * ms
        assertEquals(TipoArranque.FRIO, tipoDeArranque(true, OrigenProceso.NO_SE, app, app + 150 * ms))
        // Un frío lento (más de 1 s, el umbral anterior) sigue siendo frío: antes se reportaba como tibio y más corto.
        assertEquals(TipoArranque.FRIO, tipoDeArranque(true, OrigenProceso.NO_SE, app, app + 2_500 * ms))
        assertEquals(TipoArranque.FRIO, tipoDeArranque(true, OrigenProceso.NO_SE, app, app + UMBRAL_PROCESO_PRECALENTADO_NS))
        // Pasado el umbral el proceso ya vivía (lo arrancó el mosaico al abrir el panel, un servicio o un aviso).
        assertEquals(TipoArranque.TIBIO, tipoDeArranque(true, OrigenProceso.NO_SE, app, app + UMBRAL_PROCESO_PRECALENTADO_NS + 1))
        assertEquals(TipoArranque.TIBIO, tipoDeArranque(false, OrigenProceso.NO_SE, app, app + 100 * ms))
        assertEquals(TipoArranque.DESCONOCIDO, tipoDeArranque(true, OrigenProceso.NO_SE, 0L, app))
        assertEquals(5_000_000_000L, UMBRAL_PROCESO_PRECALENTADO_NS)
    }

    @Test
    fun laRazonDelSistemaDiceSiElProcesoNacioParaUnaActivity() {
        for (r in listOf(
            ApplicationStartInfo.START_REASON_LAUNCHER,
            ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS,
            ApplicationStartInfo.START_REASON_START_ACTIVITY,
        )) {
            assertEquals(OrigenProceso.ACTIVIDAD, origenDeRazon(r))
        }
        // El mosaico de Ajustes rápidos es un servicio; los avisos, trabajos y demás también arrancan el proceso antes.
        for (r in listOf(
            ApplicationStartInfo.START_REASON_SERVICE,
            ApplicationStartInfo.START_REASON_BROADCAST,
            ApplicationStartInfo.START_REASON_CONTENT_PROVIDER,
            ApplicationStartInfo.START_REASON_JOB,
            ApplicationStartInfo.START_REASON_PUSH,
            ApplicationStartInfo.START_REASON_ALARM,
            ApplicationStartInfo.START_REASON_BOOT_COMPLETE,
            ApplicationStartInfo.START_REASON_OTHER,
        )) {
            assertEquals(OrigenProceso.OTRO, origenDeRazon(r))
        }
    }
}
