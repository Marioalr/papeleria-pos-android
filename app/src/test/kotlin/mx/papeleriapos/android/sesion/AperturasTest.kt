package mx.papeleriapos.android.sesion

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.escaner.Apertura
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.TipoArranque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class AperturasTest {
    private val ms = 1_000_000L

    private fun apertura(tipo: TipoArranque, actividad: Long, vararg pasos: Pair<PasoApertura, Int>) =
        Apertura(tipo, mapOf(PasoApertura.ACTIVIDAD to actividad * ms) + pasos.map { it.first to it.second * ms })

    @Test
    fun `las columnas de la apertura van solo en el primer intento de cada apertura`() {
        val filtro = AperturaPorIntento()
        val a = apertura(TipoArranque.FRIO, 100, PasoApertura.PRIMER_ANALISIS to 700, PasoApertura.PRIMERA_LECTURA to 900)
        val primera = filtro.medidas(a)
        assertEquals("FRIO", primera.tipo)
        assertEquals(800L, primera.msPrimeraLectura)
        // Los demás intentos de la misma apertura, vacíos (aunque llegue con más marcas).
        assertEquals(MedidasApertura(), filtro.medidas(a))
        assertEquals(MedidasApertura(), filtro.medidas(a.copy(marcas = a.marcas + (PasoApertura.CAMARA_ABIERTA to 400 * ms))))
        // Una apertura nueva (volvió de segundo plano) sí.
        val caliente = apertura(TipoArranque.CALIENTE, 50_000, PasoApertura.PRIMER_ANALISIS to 50_200)
        assertEquals("CALIENTE", filtro.medidas(caliente).tipo)
        assertEquals(MedidasApertura(), filtro.medidas(caliente))
        // Si se deshace el intento que la llevaba, el siguiente la vuelve a llevar.
        filtro.olvidar()
        assertEquals("CALIENTE", filtro.medidas(caliente).tipo)
        // Sin marcas no hay nada que poner.
        assertEquals(MedidasApertura(), AperturaPorIntento().medidas(Apertura()))
    }

    @Test
    fun `del escaner se toman las marcas mas recientes de la misma apertura, nunca las de otra`() {
        val delEscaner = apertura(TipoArranque.TIBIO, 100, PasoApertura.PRIMER_ANALISIS to 600)
        // La primera lectura se marcó justo después de avisar la lectura: las compartidas ya la traen.
        val compartida = delEscaner.copy(marcas = delEscaner.marcas + (PasoApertura.PRIMERA_LECTURA to 900 * ms))
        assertSame(compartida, aperturaMasReciente(delEscaner, compartida))
        // Las compartidas ya son de otra apertura (el escaneo rápido se abrió encima): se queda la del escáner.
        val otra = apertura(TipoArranque.TIBIO, 5_000)
        assertSame(delEscaner, aperturaMasReciente(delEscaner, otra))
        assertEquals(100 * ms, claveApertura(delEscaner))
        assertEquals(0L, claveApertura(Apertura()))
    }

    @Test
    fun `aperturas csv trae el origen, el tipo y los ms de cada paso`() {
        val a = apertura(
            TipoArranque.FRIO, 120,
            PasoApertura.PROCESO to 1, PasoApertura.ENLACE_FIN to 420, PasoApertura.PRIMER_ANALISIS to 640,
        )
        val equipo = DatosEquipo("motorola moto g24", 34, "0.1.0", "abc1234")
        val texto = CsvAperturas.encabezado() +
            CsvAperturas.renglon(1_760_000_000_000, 1_760_000_000_123, CsvAperturas.ORIGEN_RAPIDO, a, equipo, TimeZone.getTimeZone("UTC"))
        assertTrue(texto.startsWith(Csv.BOM))
        val f = Csv.leer(texto)
        assertEquals(CsvAperturas.COLUMNAS, f[0])
        assertEquals(2, f.size)
        assertTrue(f.all { it.size == CsvAperturas.COLUMNAS.size })
        val col = { n: String -> f[1][CsvAperturas.COLUMNAS.indexOf(n)] }
        assertEquals("1760000000000", col("sesion"))
        assertEquals("2025-10-09T08:53:20.123+0000", col("hora"))
        assertEquals("rapido", col("origen"))
        assertEquals("FRIO", col("tipo_arranque"))
        assertEquals("0", col("ms_proceso"))
        assertEquals("119", col("ms_actividad"))
        assertEquals("419", col("ms_enlace_fin"))
        assertEquals("639", col("ms_primer_analisis"))
        assertEquals("", col("ms_primera_lectura"))
        assertFalse(texto.contains("null"))
    }
}
