package mx.papeleriapos.android.sesion

import mx.papeleriapos.android.Ejemplos
import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.escaner.ComparacionMotores
import mx.papeleriapos.escaner.LadoMotor
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class ResumenTandaTest {
    private val inicio = 1_760_000_000_000L

    private fun contexto(hora: Long, linterna: Boolean = false, lente: String = "PRINCIPAL") =
        Contexto(hora, Modo.CONTEO, Motor.AUTOMATICO, "30-30", zoom = 1.5f, linterna = linterna, lente = lente)

    /** Dos tandas: la normal (intentos 1 a 5) y la de poca luz (6 y 7), con linterna y sin ella. */
    private fun sesion(): SesionPrueba = SesionPrueba("Pixel", inicio)
        .registrarLectura(Ejemplos.evento("7509993456786", ms = 200, distanciaCm = 10f), contexto(inicio))
        .registrarLectura(Ejemplos.evento("75099916", Simbologia.EAN_8, ms = 400, distanciaCm = 14f), contexto(inicio + 10_000))
        .registrarLectura(Ejemplos.evento("7509990000013", ms = 300, distanciaCm = -1f), contexto(inicio + 30_000))
        .registrarLectura(Ejemplos.evento("7509990000020", ms = 50), contexto(inicio + 31_000))
        .marcarUltimo(ResultadoIntento.LECTURA_INCORRECTA)
        .registrarFallo(ResultadoIntento.TIMEOUT, contexto(inicio + 40_000))
        // En una lectura la linterna viene del evento (el contexto solo la pone en los fallos).
        .registrarLectura(
            Ejemplos.evento("7509993456786", ms = 900, distanciaCm = 8f, linterna = true),
            contexto(inicio + 60_000, linterna = true, lente = "GRAN_ANGULAR"),
        )
        .registrarFallo(ResultadoIntento.TIMEOUT, contexto(inicio + 70_000, linterna = false, lente = "GRAN_ANGULAR"))

    private val anotaciones = Anotaciones(inicio).conCondicion(Condicion.POCA_LUZ, siguiente = 6)

    @Test
    fun `una tanda por bloque de condicion con sus cuentas`() {
        val t = ResumenTandas.de(sesion(), anotaciones)
        assertEquals(2, t.size)
        val a = t[0]
        assertEquals(Condicion.NORMAL, a.condicion)
        assertEquals(1, a.desde)
        assertEquals(5, a.hasta)
        assertEquals(5, a.intentos)
        assertEquals(3, a.bien)
        assertEquals(1, a.falsas)
        assertEquals(1, a.noLeyo)
        assertEquals(0, a.duplicados)
        assertEquals(0, a.cancelados)
        // Mediana (rango más cercano) de 200, 300 y 400 = 300; p90 = 400. La lectura incorrecta no cuenta.
        assertEquals(300L, a.msMediana)
        assertEquals(400L, a.msP90)
        // La distancia, solo de las lecturas bien que la traen: 10 y 14 → la primera de dos.
        assertEquals(10f, a.distanciaCm)
        // Tres lecturas bien en 30 s: dos intervalos por medio minuto = 4 por minuto.
        assertEquals(4f, a.lecturasPorMinuto, 1e-4f)
        assertEquals(mapOf("EAN_13" to Conteo(3, 2), "EAN_8" to Conteo(1, 1)), a.porSimbologia)
        assertEquals(Conteo(0, 0), a.conLinterna)
        assertEquals(Conteo(5, 3), a.sinLinterna)
        assertEquals(mapOf("PRINCIPAL" to Conteo(5, 3)), a.porLente)

        val b = t[1]
        assertEquals(Condicion.POCA_LUZ, b.condicion)
        assertEquals(6, b.desde)
        assertEquals(7, b.hasta)
        assertEquals(2, b.intentos)
        assertEquals(1, b.bien)
        assertEquals(Conteo(1, 1), b.conLinterna)
        assertEquals(Conteo(1, 0), b.sinLinterna)
        assertEquals(900L, b.msMediana)
        assertEquals(900L, b.msP90)
        assertEquals(0f, b.lecturasPorMinuto)
        assertEquals(8f, b.distanciaCm)
        assertEquals(mapOf("GRAN_ANGULAR" to Conteo(2, 1)), b.porLente)
    }

    @Test
    fun `sin intentos no hay tandas y sin bloques todo es una`() {
        assertEquals(emptyList<ResumenTanda>(), ResumenTandas.de(SesionPrueba("Pixel", inicio), anotaciones))
        val una = ResumenTandas.de(sesion(), Anotaciones(inicio)).single()
        assertEquals(Condicion.NORMAL, una.condicion)
        assertEquals(1 to 7, una.desde to una.hasta)
        assertEquals(7, una.intentos)
        assertEquals(4, una.bien)
        // Las anotaciones de otra sesión no cuentan.
        assertEquals(1, ResumenTandas.de(sesion(), Anotaciones(1).conCondicion(Condicion.MOVIDO, 2)).size)
    }

    @Test
    fun `el texto es corto y dice lo que importa`() {
        val a = ResumenTanda(
            Condicion.NORMAL, 1, 25, 25, 23, 0, 0, 2, 0,
            msMediana = 280, msP90 = 650, distanciaCm = 12.4f, lecturasPorMinuto = 40.6f,
            porSimbologia = linkedMapOf("EAN_13" to Conteo(18, 18), "UPC_A" to Conteo(7, 5)),
            conLinterna = Conteo(3, 3), sinLinterna = Conteo(22, 20),
            porLente = linkedMapOf("PRINCIPAL" to Conteo(25, 23)),
        )
        assertEquals(
            "Normal (#1–25): 23 de 25 bien (92 %), 0 falsas, 2 no leyó.\n" +
                "Lectura típica 280 ms (p90 650), 41 lecturas/min, a 12 cm.\n" +
                "EAN_13 18/18 · UPC_A 5/7. Linterna: con 3/3, sin 20/22. Lente: PRINCIPAL 23/25.",
            a.texto(),
        )
        // Sin datos sobran renglones; en curso lo dice; una falsa o un cancelado van en singular.
        val b = ResumenTanda(Condicion.OTRA, 3, 3, 2, 0, 1, 0, 0, 1)
        assertEquals("Otra (#3), en curso: 0 de 2 bien (0 %), 1 falsa, 1 cancelado.", b.texto(enCurso = true))
        // Lo de antes de esta versión no trae lente.
        val c = ResumenTanda(Condicion.NORMAL, 1, 2, 2, 2, 0, 0, 0, 0, porLente = mapOf("" to Conteo(2, 2)))
        assertEquals("Normal (#1–2): 2 de 2 bien (100 %), 0 falsas.\nLente: sin dato 2/2.", c.texto())
    }

    @Test
    fun `con lado a lado la tanda dice como le fue a cada motor`() {
        val sinLado = ResumenTandas.de(sesion(), anotaciones)
        assertNull(sinLado[0].ladoALado)
        // Tres lecturas con «Lado a lado»: zxing-cpp lee en dos (a 120 y 200 ms) y en una lee otra cosa; ML Kit lee en las tres.
        val s = SesionPrueba("Pixel", inicio)
            .registrarLectura(
                Ejemplos.evento("7509993456786", comparacion = ComparacionMotores(10, LadoMotor(6, 0, 120, 0.4f), LadoMotor(4, 0, 250, 35f))),
                contexto(inicio),
            )
            .registrarLectura(
                Ejemplos.evento("7509993456786", comparacion = ComparacionMotores(8, LadoMotor(3, 0, 200, 0.5f), LadoMotor(5, 0, 180, 40f))),
                contexto(inicio + 5_000),
            )
            .registrarLectura(
                Ejemplos.evento("75099916", Simbologia.EAN_8, comparacion = ComparacionMotores(20, LadoMotor(0, 2, -1, 0.45f), LadoMotor(7, 0, 400, 38f))),
                contexto(inicio + 9_000),
            )
            .registrarFallo(ResultadoIntento.TIMEOUT, contexto(inicio + 12_000))
        val t = ResumenTandas.de(s, Anotaciones(inicio)).single()
        // Medianas por rango más cercano: zxing 120 y 200 → 120; 0.4, 0.45 y 0.5 → 0.45; ML Kit 180, 250 y 400 → 250; 35, 38 y 40 → 38.
        assertEquals(ResumenLado(3, LadoTanda(2, 1, 120, 0.45f), LadoTanda(3, 0, 250, 38f)), t.ladoALado)
        assertEquals(
            "Lado a lado (3): zxing-cpp leyó 2 (primera a 120 ms, 0.45 ms/cuadro), ML Kit 3 (primera a 250 ms, 38 ms/cuadro); " +
                "otras lecturas: zxing-cpp 1, ML Kit 0.",
            t.texto().lines().last(),
        )
        val r = Csv.leer(CsvResumen.escribir(s, listOf(t), DatosEquipo("Pixel 8 Pro", 36, "0.1.0", "abc"), TimeZone.getTimeZone("UTC")))
        val col = { nombre: String -> r[1][CsvResumen.COLUMNAS.indexOf(nombre)] }
        assertEquals("3", col("lado_intentos"))
        assertEquals("2", col("zxing_leyo"))
        assertEquals("1", col("zxing_otras"))
        assertEquals("120", col("zxing_ms_primera"))
        assertEquals("0.45", col("zxing_ms_cuadro"))
        assertEquals("3", col("mlkit_leyo"))
        assertEquals("250", col("mlkit_ms_primera"))
        assertEquals("38.00", col("mlkit_ms_cuadro"))
        // Sin lado a lado, las columnas van vacías.
        val r2 = Csv.leer(CsvResumen.escribir(sesion(), sinLado, DatosEquipo("x", 1, "1", "1"), TimeZone.getTimeZone("UTC")))
        assertEquals("", r2[1][CsvResumen.COLUMNAS.indexOf("lado_intentos")])
    }

    @Test
    fun `el percentil es por rango mas cercano`() {
        assertNull(ResumenTandas.percentil(emptyList<Long>(), 0.5))
        assertEquals(5L, ResumenTandas.percentil(listOf(5L), 0.9))
        assertEquals(2L, ResumenTandas.percentil(listOf(1L, 2L, 3L, 4L), 0.5))
        assertEquals(4L, ResumenTandas.percentil(listOf(1L, 2L, 3L, 4L), 0.9))
        assertEquals(1L, ResumenTandas.percentil(listOf(1L, 2L, 3L, 4L), 0.0))
    }

    @Test
    fun `el CSV del resumen lleva una fila por tanda`() {
        val equipo = DatosEquipo("Pixel 8 Pro", 36, "0.1.0", "abc1234")
        val s = sesion()
        val r = Csv.leer(CsvResumen.escribir(s, ResumenTandas.de(s, anotaciones), equipo, TimeZone.getTimeZone("UTC")))
        assertEquals(CsvResumen.COLUMNAS, r[0])
        assertEquals(3, r.size)
        val col = { fila: Int, nombre: String -> r[fila][CsvResumen.COLUMNAS.indexOf(nombre)] }
        assertEquals("Pixel@20251009-085320", col(1, "sesion"))
        assertEquals("NORMAL", col(1, "condicion"))
        assertEquals("1", col(1, "desde"))
        assertEquals("5", col(1, "hasta"))
        assertEquals("5", col(1, "intentos"))
        assertEquals("3", col(1, "bien"))
        assertEquals("1", col(1, "falsas"))
        assertEquals("1", col(1, "no_leyo"))
        assertEquals("300", col(1, "ms_mediana"))
        assertEquals("400", col(1, "ms_p90"))
        assertEquals("10.0", col(1, "distancia_cm"))
        assertEquals("4.0", col(1, "lecturas_por_minuto"))
        assertEquals("EAN_13 2/3|EAN_8 1/1", col(1, "por_simbologia"))
        assertEquals("0", col(1, "con_linterna_intentos"))
        assertEquals("5", col(1, "sin_linterna_intentos"))
        assertEquals("3", col(1, "sin_linterna_bien"))
        assertEquals("PRINCIPAL 3/5", col(1, "por_lente"))
        assertEquals("Pixel 8 Pro", col(1, "dispositivo"))
        assertEquals("abc1234", col(1, "commit"))
        // La de poca luz: sin lecturas por minuto (una sola bien) y con la linterna aparte.
        assertEquals("POCA_LUZ", col(2, "condicion"))
        assertEquals("", col(2, "lecturas_por_minuto"))
        assertEquals("1", col(2, "con_linterna_bien"))
        assertEquals("0", col(2, "sin_linterna_bien"))
        assertEquals("GRAN_ANGULAR 1/2", col(2, "por_lente"))
    }
}
