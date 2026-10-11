package mx.papeleriapos.android.sesion

import mx.papeleriapos.android.Ejemplos
import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.escaner.Apertura
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.TipoArranque
import mx.papeleriapos.escaner.nucleo.Via
import mx.papeleriapos.escaner.nucleo.Catalogo
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
        lente = "GRAN_ANGULAR", camaraFisica = "3", resolucion = "1280x720", msCuadro = 7f,
    )
    private val vacia = SesionPrueba("Moto G24 Lupita", 1_760_000_000_000)

    @Test
    fun `cada lectura es un intento numerado con lo que trae el evento`() {
        val s = vacia
            .registrarLectura(Ejemplos.evento("7509993456786"), contexto)
            .registrarLectura(Ejemplos.evento("75099916", Simbologia.EAN_8, motor = Motor.ZXING_CPP), contexto)
        assertEquals(listOf(1, 2), s.intentos.map { it.n })
        val i = s.intentos[0]
        assertEquals("ML_KIT", i.motor)
        assertEquals("DIRECTA", i.via)
        assertEquals("VENTA", i.modo)
        assertEquals("30-30", i.rangoFps)
        assertEquals(180L, i.msDesdeCandidato)
        assertEquals(listOf("ACERCATE", "QUIETO"), i.indicaciones)
        assertEquals("EAN_13", i.simbologia)
        assertEquals("7509993456786", i.valor)
        assertEquals(2.5f, i.moduloPx)
        assertEquals(1.5f, i.zoom)
        assertEquals(Medicion(87, 1234, 1), i.medicion)
        // El lente, la cámara física y la resolución vienen del contexto; los ms por cuadro y la distancia, del evento.
        assertEquals("GRAN_ANGULAR", i.lente)
        assertEquals("3", i.camaraFisica)
        assertEquals("1280x720", i.resolucion)
        assertEquals(8.4f, i.msCuadro)
        assertEquals(12.5f, i.distanciaCm)
        // Sin código esperado no se compara y de entrada está bien.
        assertNull(i.coincide)
        assertEquals(ResultadoIntento.OK, i.resultado)
        assertEquals("EAN_8", s.intentos[1].simbologia)
    }

    @Test
    fun `con codigo esperado compara y si no coincide queda como lectura incorrecta`() {
        val s = vacia.conEsperado(" 7509993456786 ")
            .registrarLectura(Ejemplos.evento("7509993456786"), contexto)
            .registrarLectura(Ejemplos.evento("7509990000013"), contexto)
        assertEquals("7509993456786", s.esperado)
        assertEquals(true, s.intentos[0].coincide)
        assertEquals(ResultadoIntento.OK, s.intentos[0].resultado)
        assertEquals(false, s.intentos[1].coincide)
        assertEquals(ResultadoIntento.LECTURA_INCORRECTA, s.intentos[1].resultado)
        assertEquals("7509993456786", s.intentos[1].esperado)
    }

    @Test
    fun `el esperado se guarda sin espacios y coincide aunque se teclee con ellos en medio`() {
        val s = vacia.conEsperado("7509 9934 56786").registrarLectura(Ejemplos.evento("7509993456786"), contexto)
        assertEquals("7509993456786", s.esperado)
        assertEquals(true, s.intentos[0].coincide)
        assertEquals(ResultadoIntento.OK, s.intentos[0].resultado)
        // Una sesión recuperada de un CSV viejo puede traer el espacio: la comparación también lo ignora.
        assertTrue(SesionPrueba.coincide("7509 9934 56786", CodigoLeido("7509993456786", "7509993456786", Simbologia.EAN_13, emptyList())))
    }

    @Test
    fun `un UPC-A coincide con su EAN-13 y un UPC-E con su expansion`() {
        assertTrue(SesionPrueba.coincide("0099999291452", CodigoLeido("099999291452", "099999291452", Simbologia.UPC_A, emptyList())))
        assertTrue(SesionPrueba.coincide("099999291452", CodigoLeido("0099999291452", "0099999291452", Simbologia.EAN_13, emptyList())))
        // UPC-E 09999961 se expande a 099999000061.
        assertTrue(SesionPrueba.coincide("09999961", CodigoLeido("0099999000061", "0099999000061", Simbologia.UPC_E, emptyList())))
        assertTrue(SesionPrueba.coincide("099999000061", CodigoLeido("09999961", "0099999000061", Simbologia.UPC_E, emptyList())))
        assertFalse(SesionPrueba.coincide("7509993456786", CodigoLeido("7509993456782", "7509993456782", Simbologia.EAN_13, emptyList())))
        // Code 128 se compara tal cual.
        assertTrue(SesionPrueba.coincide("ZZP0042x", CodigoLeido("ZZP0042x", "ZZP0042x", Simbologia.CODE_128, emptyList())))
        assertFalse(SesionPrueba.coincide("ZZP0042X", CodigoLeido("ZZP0042x", "ZZP0042x", Simbologia.CODE_128, emptyList())))
    }

    @Test
    fun `en un QR solo queda el hash y los ultimos 4, pero la comparacion usa el valor real`() {
        val qr = "A-000123"
        val s = vacia.conEsperado(qr).registrarLectura(Ejemplos.evento(qr, Simbologia.QR), contexto)
        val i = s.intentos.single()
        assertTrue(i.valor.startsWith("sha256:"))
        assertTrue(i.valor.endsWith("...0123"))
        assertFalse(i.valor.contains("A-000123"))
        assertFalse(i.normalizado.contains("A-000123"))
        assertFalse(i.esperado.contains("A-000123"))
        assertEquals(true, i.coincide)
    }

    @Test
    fun `cada lectura se busca en el catalogo de prueba, igual venga de ML Kit o de zxing-cpp`() {
        val catalogo = Catalogo.deTexto("codigo,descripcion\n7509993456786,x\n99999291452,UPC-A guardado de 11\nA-000123,x\n")
        val conCatalogo = contexto.copy(catalogo = catalogo)
        val mlKit = Ejemplos.upcA
        val zxing = Ejemplos.evento(
            "0099999291452", Simbologia.UPC_A, claves = listOf("0099999291452", "099999291452"), motor = Motor.ZXING_CPP,
        )
        val s = vacia
            .registrarLectura(Ejemplos.evento("7509993456786"), conCatalogo)
            .registrarLectura(mlKit, conCatalogo)
            .registrarLectura(zxing, conCatalogo)
            .registrarLectura(Ejemplos.evento("75099916", Simbologia.EAN_8), conCatalogo)
            .registrarLectura(Ejemplos.evento("A-000123", Simbologia.QR), conCatalogo)
            .registrarLectura(Ejemplos.evento("7509993456786"), contexto)
            .registrarFallo(ResultadoIntento.TIMEOUT, conCatalogo)
        val busqueda = s.intentos.map { Triple(it.enCatalogo, it.variante, it.claveCatalogo) }
        assertEquals(Triple(true, "TAL_CUAL", "7509993456786"), busqueda[0])
        assertEquals(Triple(true, "UPC_A_11", "99999291452"), busqueda[1])
        assertEquals(busqueda[1], busqueda[2])
        assertEquals(Triple(false, "", ""), busqueda[3])
        // El QR: la clave tampoco sale.
        assertEquals(Triple(true, "TAL_CUAL", Csv.ocultar("A-000123")), busqueda[4])
        // Sin catálogo no se buscó; un fallo no tiene qué buscar.
        assertEquals(Triple(null, "", ""), busqueda[5])
        assertEquals(Triple(null, "", ""), busqueda[6])
    }

    @Test
    fun `el operador corrige el ultimo intento leido`() {
        val s = vacia.registrarLectura(Ejemplos.evento("7509993456786"), contexto)
        assertEquals(ResultadoIntento.DUPLICADO, s.marcarUltimo(ResultadoIntento.DUPLICADO).ultimo!!.resultado)
        assertEquals(ResultadoIntento.FALSO_POSITIVO, s.marcarUltimo(ResultadoIntento.FALSO_POSITIVO).ultimo!!.resultado)
        // A un intento leído no se le pone TIMEOUT: eso es un intento nuevo.
        assertSame(s, s.marcarUltimo(ResultadoIntento.TIMEOUT))
        // Sin intentos no hay nada que marcar.
        assertSame(vacia, vacia.marcarUltimo(ResultadoIntento.OK))
    }

    @Test
    fun `los intentos fallidos se registran aparte con el contexto del momento`() {
        val s = vacia.conEsperado("7509993456786").registrarFallo(ResultadoIntento.TIMEOUT, contexto)
        val i = s.intentos.single()
        assertEquals(ResultadoIntento.TIMEOUT, i.resultado)
        assertEquals("", i.simbologia)
        assertEquals("", i.valor)
        assertEquals("7509993456786", i.esperado)
        assertNull(i.coincide)
        assertEquals("ZXING_CPP", i.motor)
        assertEquals(1.5f, i.zoom)
        assertTrue(i.linterna)
        assertEquals(-1L, i.msDesdeCandidato)
        assertEquals("GRAN_ANGULAR", i.lente)
        assertEquals("1280x720", i.resolucion)
        // Los ms por cuadro, del diagnóstico del momento; la distancia no se sabe (no enfocó nada).
        assertEquals(7f, i.msCuadro)
        assertEquals(-1f, i.distanciaCm)
        // Un fallido puede cambiar entre TIMEOUT y CANCELADO, pero no a OK.
        assertEquals(ResultadoIntento.CANCELADO, s.marcarUltimo(ResultadoIntento.CANCELADO).ultimo!!.resultado)
        assertSame(s, s.marcarUltimo(ResultadoIntento.OK))
    }

    @Test
    fun `cada lectura guarda por donde leyo y un fallo no tiene via`() {
        val s = vacia
            .registrarLectura(Ejemplos.evento("7509993456786", motor = Motor.AUTOMATICO, via = Via.RESPALDO), contexto)
            .registrarLectura(Ejemplos.evento("7509993456786", motor = Motor.AUTOMATICO, via = Via.UMBRAL), contexto)
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
    fun `la ventana del primer intento empieza al iniciar la sesion`() {
        val d = DeltaCpu()
        d.siguiente(1_000, 10_000) // Al crearse la pantalla.
        d.siguiente(4_000, 50_000) // El último intento de la sesión anterior.
        // Se inicia la sesión: lo de antes no cuenta.
        d.reiniciar(4_500, 60_000)
        assertEquals(200L to 1_000L, d.siguiente(4_700, 61_000))
    }

    @Test
    fun `un doble toque en No leyo se ignora y el ultimo fallo se puede deshacer`() {
        val a = Antirrebote(msMinimo = 1_000)
        assertTrue(a.permite(10_000))
        assertFalse("Doble toque", a.permite(10_150))
        assertFalse(a.permite(10_999))
        assertTrue(a.permite(11_000))
        a.olvidar()
        assertTrue("Después de deshacer cuenta de inmediato", a.permite(11_100))

        val s = vacia
            .registrarLectura(Ejemplos.evento("7509993456786"), contexto)
            .registrarFallo(ResultadoIntento.TIMEOUT, contexto)
        assertTrue(s.puedeDeshacerFallo)
        val sin = s.quitarUltimoFallo()
        assertEquals(listOf(1), sin.intentos.map { it.n })
        // Una lectura no se quita (se corrige con su resultado).
        assertFalse(sin.puedeDeshacerFallo)
        assertSame(sin, sin.quitarUltimoFallo())
        assertSame(vacia, vacia.quitarUltimoFallo())
        // El siguiente fallo vuelve a tomar el número 2.
        assertEquals(2, sin.registrarFallo(ResultadoIntento.CANCELADO, contexto).ultimo!!.n)
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
            .registrarLectura(Ejemplos.evento("7509993456786"), contexto)
            .registrarLectura(Ejemplos.evento("7509993456786"), contexto).marcarUltimo(ResultadoIntento.DUPLICADO)
            .registrarLectura(Ejemplos.evento("75099916", Simbologia.EAN_8), contexto)
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
