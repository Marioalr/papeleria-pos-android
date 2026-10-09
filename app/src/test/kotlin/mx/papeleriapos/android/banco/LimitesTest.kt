package mx.papeleriapos.android.banco

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.android.sesion.DatosEquipo
import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitesTest {
    /**
     * Un motor que sabe qué escena está cargada y lee hasta el grado [hasta] de cada condición (índice en la escalera).
     * [fallaVariantes] = variantes que no lee aunque esté dentro de su límite (para probar el 80 %).
     */
    private class MotorFalso(
        override val motor: Motor,
        private val banco: () -> BancoLimites,
        private val hasta: Map<CondicionLimite, Int>,
        private val fallaVariantes: Map<Pair<CondicionLimite, Int>, Set<Int>> = emptyMap(),
        private val leeMal: Set<Pair<CondicionLimite, Int>> = emptySet(),
        val lecturas: MutableList<EscenaLimite> = ArrayList(),
        val cerrados: MutableList<Motor> = ArrayList(),
    ) : MotorDeLectura {
        override fun leer(cuadro: CuadroY): ResultadoMotor {
            val e = banco().escenaActual!!
            lecturas += e
            assertTrue(cuadro.y.isDirect)
            val g = e.condicion to e.indiceGrado
            val texto = when {
                g in leeMal -> "7501234567899"
                e.indiceGrado > (hasta[e.condicion] ?: Int.MAX_VALUE) -> null
                e.variante in (fallaVariantes[g] ?: emptySet()) -> null
                else -> EscenaLimite.CODIGO
            }
            return ResultadoMotor(listOfNotNull(texto).map { Lectura(it, Simbologia.EAN_13, null) }, emptyList(), 0)
        }

        override fun close() {
            cerrados += motor
        }
    }

    private fun banco(
        condiciones: List<CondicionLimite>,
        motores: Map<Motor, (() -> BancoLimites) -> MotorDeLectura>,
        variantes: Int = 5,
    ): BancoLimites {
        lateinit var b: BancoLimites
        b = BancoLimites(
            crearMotor = { m, _ -> motores.getValue(m)({ b }) },
            motores = motores.keys.toList(),
            simbologias = setOf(Simbologia.EAN_13),
            recortarFranja = true,
            anchoBuffer = 160,
            altoBuffer = 90,
            condiciones = condiciones,
            variantes = variantes,
        )
        return b
    }

    @Test
    fun `encuentra el ultimo grado que lee cada motor y deja de subir tras dos fallos seguidos`() {
        val ml = ArrayList<EscenaLimite>()
        val zx = ArrayList<EscenaLimite>()
        val b = banco(
            listOf(CondicionLimite.DESENFOQUE),
            mapOf(
                Motor.ML_KIT to { f -> MotorFalso(Motor.ML_KIT, f, mapOf(CondicionLimite.DESENFOQUE to 4), lecturas = ml) },
                Motor.ZXING_CPP to { f -> MotorFalso(Motor.ZXING_CPP, f, mapOf(CondicionLimite.DESENFOQUE to 1), lecturas = zx) },
            ),
        )
        val r = b.correr()
        val porMotor = r.associateBy { it.motor }
        val grados = CondicionLimite.DESENFOQUE.grados
        assertEquals(grados[4], porMotor.getValue(Motor.ML_KIT).ultimoGrado)
        assertEquals(grados[1], porMotor.getValue(Motor.ZXING_CPP).ultimoGrado)
        assertFalse(porMotor.getValue(Motor.ML_KIT).alcanzoElMaximo)
        // ML Kit probó los grados 0 a 6 (dos fallos: 5 y 6) y zxing-cpp del 0 al 3.
        assertEquals((0..6).toSet(), ml.map { it.indiceGrado }.toSet())
        assertEquals((0..3).toSet(), zx.map { it.indiceGrado }.toSet())
        assertEquals(7 * 5, ml.size)
        assertEquals(4 * 5, zx.size)
        val tasas = porMotor.getValue(Motor.ZXING_CPP).grados
        assertEquals(listOf(1.0, 1.0, 0.0, 0.0), tasas.map { it.tasa })
    }

    @Test
    fun `un grado que falla fija el limite aunque los siguientes pasen por suerte`() {
        val c = CondicionLimite.MOVIDO
        val b = banco(
            listOf(c),
            mapOf(
                Motor.ML_KIT to { f ->
                    MotorFalso(
                        Motor.ML_KIT, f, mapOf(c to 5),
                        // En el grado 2 solo lee 3 de 5 (60 %): no pasa. El 3 al 5 sí, pero ya no suben el límite.
                        fallaVariantes = mapOf((c to 2) to setOf(0, 1), (c to 4) to setOf(3)),
                    )
                },
            ),
        )
        val l = b.correr().single()
        assertEquals(c.grados[1], l.ultimoGrado)
        assertFalse(l.alcanzoElMaximo)
        // La escalera sigue (hasta dos fallos seguidos) y el CSV muestra todo lo que se probó; el 80 % es 4 de 5.
        assertEquals(listOf(1.0, 1.0, 0.6, 1.0, 0.8, 1.0, 0.0, 0.0), l.grados.map { it.tasa })
    }

    @Test
    fun `el limite es el ultimo grado que paso antes del primero que fallo`() {
        fun t(vararg tasas: Double) = tasas.mapIndexed { i, x -> TasaGrado(i.toFloat(), 5, (x * 5).toInt(), 0, 1.0) }
        assertEquals(2f, limiteAntesDelPrimerFallo(t(1.0, 1.0, 0.8, 0.6, 1.0, 1.0)))
        assertEquals(5f, limiteAntesDelPrimerFallo(t(1.0, 1.0, 0.8, 1.0, 1.0, 1.0)))
        assertNull(limiteAntesDelPrimerFallo(t(0.6, 1.0, 1.0)))
        assertNull(limiteAntesDelPrimerFallo(emptyList()))
    }

    @Test
    fun `si lee hasta el mas dificil lo dice, y si no lee ni el mas facil queda sin limite`() {
        val c = CondicionLimite.GIRO
        val b = banco(
            listOf(c),
            mapOf(
                Motor.AUTOMATICO to { f -> MotorFalso(Motor.AUTOMATICO, f, emptyMap()) },
                Motor.ML_KIT to { f -> MotorFalso(Motor.ML_KIT, f, mapOf(c to -1)) },
            ),
        )
        val r = b.correr().associateBy { it.motor }
        val auto = r.getValue(Motor.AUTOMATICO)
        assertEquals(c.grados.last(), auto.ultimoGrado)
        assertTrue(auto.alcanzoElMaximo)
        assertEquals("≥ 90", CsvLimites.textoLimite(auto))
        val ml = r.getValue(Motor.ML_KIT)
        assertNull(ml.ultimoGrado)
        assertEquals("—", CsvLimites.textoLimite(ml))
        assertEquals(2, ml.grados.size)
    }

    @Test
    fun `una lectura incorrecta no cuenta como leida y se registra`() {
        val c = CondicionLimite.REFLEJO
        val b = banco(
            listOf(c),
            mapOf(Motor.ZXING_CPP to { f -> MotorFalso(Motor.ZXING_CPP, f, mapOf(c to 8), leeMal = setOf(c to 3)) }),
        )
        val l = b.correr().single()
        val g3 = l.grados[3]
        assertEquals(0, g3.leidas)
        assertEquals(5, g3.incorrectas)
        // Falló el 3: el límite es el 2, aunque leyó el 4 y siguió probando.
        assertEquals(c.grados[2], l.ultimoGrado)
        assertTrue(l.grados.size > 4)
    }

    @Test
    fun `un motor que truena sale con su error y los demas siguen`() {
        val cerrados = ArrayList<Motor>()
        val c = CondicionLimite.TAMANO
        val b = banco(
            listOf(c, CondicionLimite.PERSPECTIVA),
            mapOf(
                Motor.ML_KIT to { f -> MotorFalso(Motor.ML_KIT, f, mapOf(c to 3), cerrados = cerrados) },
                Motor.ZXING_CPP to { f ->
                    object : MotorDeLectura by MotorFalso(Motor.ZXING_CPP, f, emptyMap(), cerrados = cerrados) {
                        override fun leer(cuadro: CuadroY): ResultadoMotor {
                            if (f().escenaActual!!.indiceGrado == 2) throw IllegalStateException("se cerró")
                            return ResultadoMotor(listOf(Lectura(EscenaLimite.CODIGO, Simbologia.EAN_13, null)), emptyList(), 0)
                        }
                    }
                },
            ),
        )
        val r = b.correr()
        val zx = r.filter { it.motor == Motor.ZXING_CPP }
        assertTrue(zx.all { it.error!!.contains("se cerró") })
        assertEquals(c.grados[1], zx.first { it.condicion == c }.ultimoGrado)
        assertEquals(c.grados[3], r.first { it.motor == Motor.ML_KIT && it.condicion == c }.ultimoGrado)
        assertTrue(r.any { it.motor == Motor.ML_KIT && it.condicion == CondicionLimite.PERSPECTIVA && it.ultimoGrado != null })
        // Cada motor se cerró una sola vez.
        assertEquals(listOf(Motor.ML_KIT, Motor.ZXING_CPP).sorted(), cerrados.sorted())
    }

    @Test
    fun `se puede cancelar a la mitad`() {
        var leidas = 0
        val b = banco(
            CondicionLimite.entries,
            mapOf(Motor.ML_KIT to { f -> MotorFalso(Motor.ML_KIT, f, emptyMap()) }),
            variantes = 2,
        )
        val r = b.correr(progreso = { _, _ -> leidas++ }, cancelado = { leidas > 5 })
        assertTrue(r.size < CondicionLimite.entries.size)
    }

    @Test
    fun `las escenas son deterministas, distintas por variante y llevan la condicion`() {
        val a = EscenaLimite(CondicionLimite.DESENFOQUE, 3, 0)
        assertEquals(a.escena(720), EscenaLimite(CondicionLimite.DESENFOQUE, 3, 0).escena(720))
        assertNotEquals(a.escena(720), EscenaLimite(CondicionLimite.DESENFOQUE, 3, 1).escena(720))
        assertEquals(3, a.escena(720).desenfoque)
        assertEquals(6, EscenaLimite(CondicionLimite.MOVIDO, 3, 0).escena(720).movido)
        assertEquals(0.3f, EscenaLimite(CondicionLimite.PERSPECTIVA, 3, 0).escena(720).perspectiva, 1e-6f)
        val g = EscenaLimite(CondicionLimite.GIRO, 8, 0).escena(720).giroGrados
        assertTrue("giro $g", g in 87f..93f)
        // El tamaño: 113 módulos (95 + 2 zonas de 9) a 1.5 px en 720 px.
        assertEquals(113 * 1.5f / 720f, EscenaLimite(CondicionLimite.TAMANO, 4, 0).escena(720).anchoRelativo, 1e-6f)
        assertEquals(113 * 3f / 720f, a.escena(720).anchoRelativo, 1e-6f)
        val oscura = EscenaLimite(CondicionLimite.POCA_LUZ, 4, 0)
        assertEquals(0.2f, oscura.luz, 1e-6f)
        assertTrue(oscura.ruido > EscenaLimite.RUIDO_BASE)
        // La de poca luz se ve más oscura.
        val clara = EscenaLimite(CondicionLimite.POCA_LUZ, 0, 0).dibujar(180, 320)
        val oscuraLuma = oscura.dibujar(180, 320)
        assertTrue(oscuraLuma.pixeles.sumOf { it.toInt() and 0xFF } < clara.pixeles.sumOf { it.toInt() and 0xFF } / 2)
    }

    @Test
    fun `el CSV de limites trae un renglon por grado y marca el limite`() {
        val l = LimiteMotor(
            Motor.ML_KIT, CondicionLimite.DESENFOQUE, 2f, false,
            listOf(TasaGrado(0f, 5, 5, 0, 12.5), TasaGrado(1f, 5, 5, 0, 13.0), TasaGrado(2f, 5, 4, 1, 14.0), TasaGrado(3f, 5, 1, 0, Double.NaN)),
        )
        val sinLimite = LimiteMotor(Motor.ZXING_CPP, CondicionLimite.DESENFOQUE, null, false, emptyList(), "UnsatisfiedLinkError")
        val filas = Csv.leer(CsvLimites.escribir("20261009", listOf(l, sinLimite), DatosEquipo("Pixel", 36, "0.1.0", "abc")))
        assertEquals(CsvLimites.COLUMNAS, filas[0])
        assertEquals(6, filas.size)
        val col = { f: Int, n: String -> filas[f][CsvLimites.COLUMNAS.indexOf(n)] }
        assertEquals(listOf("si", "si", "si", "no"), (1..4).map { col(it, "pasa") })
        assertEquals(listOf("no", "no", "si", "no"), (1..4).map { col(it, "es_limite") })
        assertEquals("2", col(1, "limite"))
        assertEquals("0.80", col(3, "tasa"))
        assertEquals("1", col(3, "incorrectas"))
        assertEquals("", col(4, "ms_mediana"))
        assertEquals("—", col(5, "limite"))
        assertEquals("UnsatisfiedLinkError", col(5, "error"))
    }
}
