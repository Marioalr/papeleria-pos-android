package mx.papeleriapos.android.sesion

import mx.papeleriapos.android.Ejemplos
import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.nucleo.Via
import mx.papeleriapos.escaner.nucleo.Indicacion
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class CsvSesionTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val equipo = DatosEquipo("motorola moto g24", 34, "0.1.0", "abc1234")
    private val contexto = Contexto(1_760_000_000_123, Modo.CONTEO, Motor.ML_KIT, "15-30", Medicion(55, 9876, 2), 2f, true)
    private val apertura = MedidasApertura("CALIENTE", 0, 40, 95, 140, 180, 610)
    private val conApertura = contexto.copy(medicion = Medicion(54, 120, 2, paredMs = 480, apertura = apertura))

    private fun sesion() = SesionPrueba("Moto, \"Lupita\"", 1_760_000_000_000)
        .registrarLectura(
            Ejemplos.evento("7501234567893", indicaciones = listOf(Indicacion.ACERCATE, Indicacion.IZQUIERDA, Indicacion.QUIETO)),
            contexto,
        )
        .registrarLectura(Ejemplos.upcA.copy(via = Via.RESPALDO), conApertura)
        .registrarLectura(Ejemplos.evento("TICKET-SECRETO-1234", Simbologia.QR, ms = -1, moduloPx = 0f), contexto)
        .registrarLectura(Ejemplos.evento("=CMD|x", Simbologia.CODE_128), contexto)
        .marcarUltimo(ResultadoIntento.FALSO_POSITIVO)
        .conEsperado("7501234567893")
        .registrarFallo(ResultadoIntento.TIMEOUT, contexto)

    @Test
    fun `el encabezado trae las columnas en el orden del protocolo`() {
        val renglones = Csv.leer(CsvSesion.escribir(sesion(), equipo, utc))
        assertEquals(
            "sesion,n,hora,motor,via,modo,rango_fps,condicion,ms_desde_candidato,indicaciones,simbologia,valor," +
                "normalizado,esperado,coincide,modulo_px,zoom,linterna,resultado,nota,bateria_pct,cpu_ms,pared_ms," +
                "cpu_pct_nucleo,estado_termico,tipo_arranque,ms_actividad,ms_enlace,ms_camara,ms_primer_cuadro," +
                "ms_primer_analisis,ms_primera_lectura,dispositivo,sdk,version_app,commit",
            renglones[0].joinToString(","),
        )
        assertEquals(6, renglones.size)
        assertTrue(renglones.all { it.size == CsvSesion.COLUMNAS.size })
    }

    @Test
    fun `cada intento llena sus columnas`() {
        val texto = CsvSesion.escribir(sesion(), equipo, utc)
        assertTrue("Empieza con BOM para Excel", texto.startsWith(Csv.BOM))
        assertTrue("Fin de línea CRLF", texto.contains("\r\n"))
        val r = Csv.leer(texto)
        val col = { fila: Int, nombre: String -> r[fila][CsvSesion.COLUMNAS.indexOf(nombre)] }
        assertEquals("Moto, \"Lupita\"@20251009-085320", col(1, "sesion"))
        assertEquals("1", col(1, "n"))
        assertEquals("2025-10-09T08:53:20.123+0000", col(1, "hora"))
        assertEquals("ML_KIT", col(1, "motor"))
        assertEquals("DIRECTA", col(1, "via"))
        assertEquals("NORMAL", col(1, "condicion"))
        assertEquals("", col(1, "nota"))
        assertEquals("CONTEO", col(1, "modo"))
        assertEquals("15-30", col(1, "rango_fps"))
        assertEquals("180", col(1, "ms_desde_candidato"))
        assertEquals("ACERCATE|IZQUIERDA|QUIETO", col(1, "indicaciones"))
        assertEquals("EAN_13", col(1, "simbologia"))
        assertEquals("7501234567893", col(1, "valor"))
        assertEquals("", col(1, "esperado"))
        assertEquals("", col(1, "coincide"))
        assertEquals("2.50", col(1, "modulo_px"))
        assertEquals("1.50", col(1, "zoom"))
        assertEquals("no", col(1, "linterna"))
        assertEquals("OK", col(1, "resultado"))
        assertEquals("55", col(1, "bateria_pct"))
        assertEquals("9876", col(1, "cpu_ms"))
        // Sin ventana no hay porcentaje, ni apertura marcada.
        assertEquals("", col(1, "pared_ms"))
        assertEquals("", col(1, "cpu_pct_nucleo"))
        assertEquals("", col(1, "tipo_arranque"))
        assertEquals("", col(1, "ms_primera_lectura"))
        assertEquals("2", col(1, "estado_termico"))
        assertEquals("motorola moto g24", col(1, "dispositivo"))
        assertEquals("34", col(1, "sdk"))
        assertEquals("0.1.0", col(1, "version_app"))
        assertEquals("abc1234", col(1, "commit"))
        // UPC-A: el valor como lo dio el motor y el normalizado en 13.
        assertEquals("036000291452", col(2, "valor"))
        assertEquals("0036000291452", col(2, "normalizado"))
        // El que leyó ML Kit de respaldo, con la CPU del intento y la apertura en caliente.
        assertEquals("RESPALDO", col(2, "via"))
        assertEquals("120", col(2, "cpu_ms"))
        assertEquals("480", col(2, "pared_ms"))
        assertEquals("25.0", col(2, "cpu_pct_nucleo"))
        assertEquals("CALIENTE", col(2, "tipo_arranque"))
        assertEquals("0", col(2, "ms_actividad"))
        assertEquals("40", col(2, "ms_enlace"))
        assertEquals("95", col(2, "ms_camara"))
        assertEquals("140", col(2, "ms_primer_cuadro"))
        assertEquals("180", col(2, "ms_primer_analisis"))
        assertEquals("610", col(2, "ms_primera_lectura"))
        // Lo que no se sabe va vacío, no en −1 ni en 0.
        assertEquals("", col(3, "ms_desde_candidato"))
        assertEquals("", col(3, "modulo_px"))
        // El Code 128 que parece fórmula va neutralizado.
        assertEquals("'=CMD|x", col(4, "valor"))
        assertEquals("FALSO_POSITIVO", col(4, "resultado"))
        // El intento fallido.
        assertEquals("TIMEOUT", col(5, "resultado"))
        assertEquals("", col(5, "via"))
        assertEquals("", col(5, "simbologia"))
        assertEquals("7501234567893", col(5, "esperado"))
        assertEquals("2.00", col(5, "zoom"))
        assertEquals("si", col(5, "linterna"))
    }

    @Test
    fun `el contenido del QR no sale en el archivo`() {
        val texto = CsvSesion.escribir(sesion(), equipo, utc)
        assertFalse(texto.contains("SECRETO"))
        val r = Csv.leer(texto)
        val valor = r[3][CsvSesion.COLUMNAS.indexOf("valor")]
        assertEquals(Csv.ocultar("TICKET-SECRETO-1234"), valor)
        assertTrue(valor.endsWith("...1234"))
    }

    @Test
    fun `con el celular en otro idioma los numeros siguen con punto`() {
        val antes = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val r = Csv.leer(CsvSesion.escribir(sesion(), equipo, utc))
            assertEquals("2.50", r[1][CsvSesion.COLUMNAS.indexOf("modulo_px")])
            assertEquals(CsvSesion.COLUMNAS.size, r[1].size)
        } finally {
            Locale.setDefault(antes)
        }
    }

    @Test
    fun `los intentos se recuperan del mismo CSV`() {
        val s = sesion()
        val recuperados = CsvSesion.leerIntentos(CsvSesion.escribir(s, equipo, utc), utc)
        assertEquals(s.intentos, recuperados)
    }

    @Test
    fun `la condicion de cada bloque y las notas salen de las anotaciones`() {
        val s = sesion()
        val notas = Anotaciones()
            .de(s)
            .conCondicion(Condicion.POCA_LUZ, siguiente = 3)
            .conCondicion(Condicion.REFLEJO, siguiente = 5)
            .conNota(4, "=etiqueta arrugada, \"doblada\"")
        val r = Csv.leer(CsvSesion.escribir(s, equipo, utc, notas))
        val col = { fila: Int, nombre: String -> r[fila][CsvSesion.COLUMNAS.indexOf(nombre)] }
        assertEquals(listOf("NORMAL", "NORMAL", "POCA_LUZ", "POCA_LUZ", "REFLEJO"), (1..5).map { col(it, "condicion") })
        assertEquals("'=etiqueta arrugada, \"doblada\"", col(4, "nota"))
        assertEquals("", col(3, "nota"))
        // Las de otra sesión no se mezclan.
        val ajenas = Anotaciones(inicioSesion = 1).conCondicion(Condicion.MOVIDO, 1)
        val r2 = Csv.leer(CsvSesion.escribir(s, equipo, utc, ajenas))
        assertEquals("NORMAL", r2[1][CsvSesion.COLUMNAS.indexOf("condicion")])
    }

    @Test
    fun `un CSV de la version anterior se recupera sin la CPU acumulada`() {
        val viejo = Csv.BOM +
            "sesion,n,hora,motor,modo,rango_fps,ms_desde_candidato,indicaciones,simbologia,valor,normalizado,esperado," +
            "coincide,modulo_px,zoom,linterna,resultado,bateria_pct,cpu_ms_proceso,estado_termico,dispositivo,sdk," +
            "version_app,commit\r\n" +
            "x@1,1,2025-10-09T08:53:20.123+0000,ML_KIT,VENTA,30-30,180,QUIETO,EAN_13,7501234567893,7501234567893,,," +
            "2.50,1.50,no,OK,80,123456,1,moto,34,0.1.0,abc\r\n"
        val i = CsvSesion.leerIntentos(viejo, utc).single()
        assertEquals("7501234567893", i.valor)
        assertEquals("", i.via)
        assertEquals(80, i.medicion.bateriaPct)
        assertEquals(-1L, i.medicion.cpuMs)
        assertEquals(1, i.medicion.estadoTermico)
        assertEquals(MedidasApertura(), i.medicion.apertura)
    }

    @Test
    fun `un CSV ajeno o vacio no recupera nada`() {
        assertEquals(emptyList<Intento>(), CsvSesion.leerIntentos(""))
        assertEquals(emptyList<Intento>(), CsvSesion.leerIntentos("a,b\r\n1,2\r\n"))
    }

    @Test
    fun `el nombre del archivo no lleva espacios ni acentos`() {
        assertEquals("sesion-Moto_G24_Lupita_20251009-085320.csv", CsvSesion.nombreArchivo("sesion", "Moto G24 Lupita@20251009-085320"))
        assertEquals("sesion-Jos_P_rez.csv", CsvSesion.nombreArchivo("sesion", "José Pérez"))
    }
}
