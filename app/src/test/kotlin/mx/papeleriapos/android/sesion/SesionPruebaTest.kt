package mx.papeleriapos.android.sesion

import mx.papeleriapos.android.Ejemplos
import mx.papeleriapos.escaner.Apertura
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.TipoArranque
import mx.papeleriapos.escaner.nucleo.Via
import mx.papeleriapos.escaner.nucleo.CodigoLeido
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class SesionPruebaTest {
    private val contexto = Contexto(
        hora = 1_760_000_000_000, modo = Modo.VENTA, motor = Motor.ZXING_CPP, rangoFps = "30-30",
        medicion = Medicion(87, 1234, 1), zoom = 1.5f, linterna = true,
    )
    private val vacia = SesionPrueba("Moto G24 Lupita", 1_760_000_000_000)

    @Test
    fun `cada lectura es un intento numerado con lo que trae el evento`() {
        val s = vacia
            .registrarLectura(Ejemplos.evento("7501234567893"), contexto)
            .registrarLectura(Ejemplos.evento("96385074", Simbologia.EAN_8, motor = Motor.ZXING_CPP), contexto)
        assertEquals(listOf(1, 2), s.intentos.map { it.n })
        val i = s.intentos[0]
        assertEquals("ML_KIT", i.motor)
        assertEquals("DIRECTA", i.via)
        assertEquals("VENTA", i.modo)
        assertEquals("30-30", i.rangoFps)
        assertEquals(180L, i.msDesdeCandidato)
        assertEquals(listOf("ACERCATE", "QUIETO"), i.indicaciones)
        assertEquals("EAN_13", i.simbologia)
        assertEquals("7501234567893", i.valor)
        assertEquals(2.5f, i.moduloPx)
        assertEquals(1.5f, i.zoom)
        assertEquals(Medicion(87, 1234, 1), i.medicion)
        // Sin código esperado no se compara y de entrada está bien.
        assertNull(i.coincide)
        assertEquals(ResultadoIntento.OK, i.resultado)
        assertEquals("EAN_8", s.intentos[1].simbologia)
    }

    @Test
    fun `con codigo esperado compara y si no coincide queda como lectura incorrecta`() {
        val s = vacia.conEsperado(" 7501234567893 ")
            .registrarLectura(Ejemplos.evento("7501234567893"), contexto)
            .registrarLectura(Ejemplos.evento("7501000000013"), contexto)
        assertEquals("7501234567893", s.esperado)
        assertEquals(true, s.intentos[0].coincide)
        assertEquals(ResultadoIntento.OK, s.intentos[0].resultado)
        assertEquals(false, s.intentos[1].coincide)
        assertEquals(ResultadoIntento.LECTURA_INCORRECTA, s.intentos[1].resultado)
        assertEquals("7501234567893", s.intentos[1].esperado)
    }

    @Test
    fun `un UPC-A coincide con su EAN-13 y un UPC-E con su expansion`() {
        assertTrue(SesionPrueba.coincide("0036000291452", CodigoLeido("036000291452", "036000291452", Simbologia.UPC_A, emptyList())))
        assertTrue(SesionPrueba.coincide("036000291452", CodigoLeido("0036000291452", "0036000291452", Simbologia.EAN_13, emptyList())))
        // UPC-E 01234565 se expande a 012345000065.
        assertTrue(SesionPrueba.coincide("01234565", CodigoLeido("0012345000065", "0012345000065", Simbologia.UPC_E, emptyList())))
        assertTrue(SesionPrueba.coincide("012345000065", CodigoLeido("01234565", "0012345000065", Simbologia.UPC_E, emptyList())))
        assertFalse(SesionPrueba.coincide("7501234567893", CodigoLeido("7501234567899", "7501234567899", Simbologia.EAN_13, emptyList())))
        // Code 128 se compara tal cual.
        assertTrue(SesionPrueba.coincide("PAP0042x", CodigoLeido("PAP0042x", "PAP0042x", Simbologia.CODE_128, emptyList())))
        assertFalse(SesionPrueba.coincide("PAP0042X", CodigoLeido("PAP0042x", "PAP0042x", Simbologia.CODE_128, emptyList())))
    }

    @Test
    fun `en un QR solo queda el hash y los ultimos 4, pero la comparacion usa el valor real`() {
        val qr = "TICKET|folio=000987|total=120.00|7F3A"
        val s = vacia.conEsperado(qr).registrarLectura(Ejemplos.evento(qr, Simbologia.QR), contexto)
        val i = s.intentos.single()
        assertTrue(i.valor.startsWith("sha256:"))
        assertTrue(i.valor.endsWith("...7F3A"))
        assertFalse(i.valor.contains("folio"))
        assertFalse(i.normalizado.contains("folio"))
        assertFalse(i.esperado.contains("folio"))
        assertEquals(true, i.coincide)
    }

    @Test
    fun `el operador corrige el ultimo intento leido`() {
        val s = vacia.registrarLectura(Ejemplos.evento("7501234567893"), contexto)
        assertEquals(ResultadoIntento.DUPLICADO, s.marcarUltimo(ResultadoIntento.DUPLICADO).ultimo!!.resultado)
        assertEquals(ResultadoIntento.FALSO_POSITIVO, s.marcarUltimo(ResultadoIntento.FALSO_POSITIVO).ultimo!!.resultado)
        // A un intento leído no se le pone TIMEOUT: eso es un intento nuevo.
        assertSame(s, s.marcarUltimo(ResultadoIntento.TIMEOUT))
        // Sin intentos no hay nada que marcar.
        assertSame(vacia, vacia.marcarUltimo(ResultadoIntento.OK))
    }

    @Test
    fun `los intentos fallidos se registran aparte con el contexto del momento`() {
        val s = vacia.conEsperado("7501234567893").registrarFallo(ResultadoIntento.TIMEOUT, contexto)
        val i = s.intentos.single()
        assertEquals(ResultadoIntento.TIMEOUT, i.resultado)
        assertEquals("", i.simbologia)
        assertEquals("", i.valor)
        assertEquals("7501234567893", i.esperado)
        assertNull(i.coincide)
        assertEquals("ZXING_CPP", i.motor)
        assertEquals(1.5f, i.zoom)
        assertTrue(i.linterna)
        assertEquals(-1L, i.msDesdeCandidato)
        // Un fallido puede cambiar entre TIMEOUT y CANCELADO, pero no a OK.
        assertEquals(ResultadoIntento.CANCELADO, s.marcarUltimo(ResultadoIntento.CANCELADO).ultimo!!.resultado)
        assertSame(s, s.marcarUltimo(ResultadoIntento.OK))
    }

    @Test
    fun `cada lectura guarda por donde leyo y un fallo no tiene via`() {
        val s = vacia
            .registrarLectura(Ejemplos.evento("7501234567893", motor = Motor.AUTOMATICO, via = Via.RESPALDO), contexto)
            .registrarLectura(Ejemplos.evento("7501234567893", motor = Motor.AUTOMATICO, via = Via.UMBRAL), contexto)
            .registrarFallo(ResultadoIntento.TIMEOUT, contexto)
        assertEquals(listOf("RESPALDO", "UMBRAL", ""), s.intentos.map { it.via })
    }

    @Test
    fun `la CPU va en delta con su ventana y da el porcentaje de un nucleo`() {
        assertEquals(25.0, Medicion(cpuMs = 250, paredMs = 1_000).cpuPctNucleo, 1e-9)
        assertTrue(Medicion(cpuMs = -1, paredMs = 1_000).cpuPctNucleo.isNaN())
        assertTrue(Medicion(cpuMs = 10, paredMs = 0).cpuPctNucleo.isNaN())
        val d = DeltaCpu()
        assertEquals(-1L to -1L, d.siguiente(5_000, 100_000))
        assertEquals(300L to 2_000L, d.siguiente(5_300, 102_000))
        assertEquals(0L to 500L, d.siguiente(5_300, 102_500))
        // Si el reloj o la CPU van para atrás (otro proceso), no hay delta.
        assertEquals(-1L to -1L, d.siguiente(100, 103_000))
    }

    @Test
    fun `las medidas de la apertura salen de sus marcas`() {
        val ms = 1_000_000L
        val a = Apertura(
            TipoArranque.FRIO,
            mapOf(
                PasoApertura.PROCESO to 1_000 * ms,
                PasoApertura.ACTIVIDAD to 1_120 * ms,
                PasoApertura.ENLACE_INICIO to 1_180 * ms,
                PasoApertura.ENLACE_FIN to 1_420 * ms,
                PasoApertura.CAMARA_ABIERTA to 1_510 * ms,
                PasoApertura.PRIMER_CUADRO_SENSOR to 1_590 * ms,
                PasoApertura.PRIMER_ANALISIS to 1_640 * ms,
            ),
        )
        assertEquals(
            MedidasApertura("FRIO", 120, 420, 510, 590, 640, -1),
            MedidasApertura.de(a),
        )
        assertEquals(MedidasApertura(), MedidasApertura.de(Apertura()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un fallo no puede ser OK`() {
        vacia.registrarFallo(ResultadoIntento.OK, contexto)
    }

    @Test
    fun `los contadores suman por resultado`() {
        val s = vacia
            .registrarLectura(Ejemplos.evento("7501234567893"), contexto)
            .registrarLectura(Ejemplos.evento("7501234567893"), contexto).marcarUltimo(ResultadoIntento.DUPLICADO)
            .registrarLectura(Ejemplos.evento("96385074", Simbologia.EAN_8), contexto)
            .registrarFallo(ResultadoIntento.TIMEOUT, contexto)
            .registrarFallo(ResultadoIntento.TIMEOUT, contexto)
            .registrarFallo(ResultadoIntento.CANCELADO, contexto)
        val c = s.contadores()
        assertEquals(2, c[ResultadoIntento.OK])
        assertEquals(1, c[ResultadoIntento.DUPLICADO])
        assertEquals(2, c[ResultadoIntento.TIMEOUT])
        assertEquals(1, c[ResultadoIntento.CANCELADO])
        assertEquals(0, c[ResultadoIntento.LECTURA_INCORRECTA])
        assertEquals(0, c[ResultadoIntento.FALSO_POSITIVO])
        assertEquals(6, c.values.sum())
        assertEquals(ResultadoIntento.entries, c.keys.toList())
    }

    @Test
    fun `la clave junta el nombre y la hora de inicio`() {
        assertEquals("Moto G24 Lupita@20251009-085320", vacia.clave(TimeZone.getTimeZone("UTC")))
    }
}
