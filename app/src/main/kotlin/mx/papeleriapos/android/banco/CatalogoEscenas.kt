package mx.papeleriapos.android.banco

import mx.papeleriapos.escaner.nucleo.Barras
import mx.papeleriapos.escaner.nucleo.Escena
import mx.papeleriapos.escaner.nucleo.Gtin
import mx.papeleriapos.escaner.nucleo.Lienzo
import mx.papeleriapos.escaner.nucleo.Luma
import mx.papeleriapos.escaner.nucleo.Simbologia
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Las escenas sin código: lo que suele haber alrededor en la tienda y no debe leerse como código. */
enum class Negativo(val etiqueta: String) {
    /** Una etiqueta en blanco con ruido. */
    LISO("etiqueta en blanco"),
    /** Hoja de cuaderno rayada. */
    RAYADO("hoja rayada"),
    /** Renglones de letras (bloques oscuros). */
    TEXTO("texto"),
    /** Barras al azar: parece código pero no codifica nada. */
    BARRAS_AL_AZAR("barras al azar"),
}

/** Una escena del banco de pruebas, como se ve en pantalla (derecha). */
data class EscenaBanco(
    val id: String,
    /** null = escena sin código. */
    val simbologia: Simbologia?,
    /** Lo que debe leerse; null en las escenas sin código. */
    val esperado: String?,
    val pxPorModulo: Float,
    val giro: Float = 0f,
    val desenfoque: Int = 0,
    val ruido: Float = 0f,
    /** 1 = luz normal; menos es más oscuro. */
    val luz: Float = 1f,
    val reflejo: Float = 0f,
    val negativo: Negativo? = null,
    val semilla: Int = 1,
) {
    val conCodigo: Boolean get() = simbologia != null
}

/**
 * Las escenas sintéticas del banco de pruebas (docs/pruebas.md): los códigos de la papelería en varios tamaños, giros y
 * desenfoques, más ruido, poca luz, reflejo y escenas sin código. Se dibujan con el [Lienzo] del núcleo.
 */
object CatalogoEscenas {
    /** Los códigos que importan en la tienda: de fábrica, internos (2…), de cajas y las claves de eleventa. */
    val CODIGOS: List<Pair<Simbologia, String>> = listOf(
        Simbologia.EAN_13 to "7509993456786",
        Simbologia.EAN_13 to Gtin.completar("299999004201"),
        Simbologia.UPC_A to "099999291452",
        Simbologia.EAN_8 to "75099916",
        Simbologia.ITF to "17509990000019",
        Simbologia.CODE_128 to "ZZP0042x",
    )
    val MODULOS = listOf(1.5f, 2f, 3f, 5f)
    val GIROS = listOf(0f, 20f, 45f)
    val DESENFOQUES = listOf(1, 2)

    /** Alto de las barras respecto al ancho del código, como en una etiqueta común. */
    private const val PROPORCION_ALTO = 0.35f

    /** Lo que se deja libre en la orilla para que ningún código quede cortado. */
    private const val MARGEN = 0.96f

    /**
     * Todas las escenas que caben completas en una imagen derecha de [ancho]×[alto] (la de un celular en vertical). Las
     * combinaciones que no caben (un ITF-14 a 5 px por módulo en 720 px) se dejan fuera.
     */
    fun escenas(ancho: Int = 720, alto: Int = 1280): List<EscenaBanco> {
        val lista = ArrayList<EscenaBanco>()
        for ((s, texto) in CODIGOS) {
            val base = nombre(s, texto)
            for (m in MODULOS) for (g in GIROS) {
                lista += EscenaBanco("$base-${px(m)}-${g.toInt()}g", s, texto, m, giro = g)
            }
            for (m in listOf(2f, 3f)) for (d in DESENFOQUES) {
                lista += EscenaBanco("$base-${px(m)}-borroso$d", s, texto, m, desenfoque = d)
            }
            lista += EscenaBanco("$base-3px-ruido", s, texto, 3f, ruido = 12f)
            // Con poca luz el sensor sube la ganancia: más ruido.
            lista += EscenaBanco("$base-3px-oscuro", s, texto, 3f, luz = 0.3f, ruido = 4f)
            lista += EscenaBanco("$base-3px-reflejo", s, texto, 3f, reflejo = 0.35f)
        }
        for (n in Negativo.entries) for (semilla in 1..3) {
            lista += EscenaBanco("sin-${n.name.lowercase()}-$semilla", null, null, 0f, ruido = 6f, negativo = n, semilla = semilla)
        }
        return lista.filter { cabe(it, ancho, alto) }
    }

    /** Si el código (con sus zonas en blanco y ya girado) cabe completo en la imagen. */
    fun cabe(e: EscenaBanco, ancho: Int, alto: Int): Boolean {
        val s = e.simbologia ?: return true
        val w = anchoCodigoPx(s, e.esperado!!, e.pxPorModulo)
        val h = w * PROPORCION_ALTO
        val rad = e.giro * PI.toFloat() / 180f
        val wGirado = abs(w * cos(rad)) + abs(h * sin(rad))
        val hGirado = abs(w * sin(rad)) + abs(h * cos(rad))
        return wGirado <= ancho * MARGEN && hGirado <= alto * MARGEN
    }

    fun anchoCodigoPx(simbologia: Simbologia, texto: String, pxPorModulo: Float): Float =
        (Barras.para(simbologia, texto).size + 2 * Barras.zonaEnBlanco(simbologia)) * pxPorModulo

    /** Dibuja la escena derecha, de [ancho]×[alto]. */
    fun dibujar(e: EscenaBanco, ancho: Int = 720, alto: Int = 1280): Luma {
        val luma = when {
            e.simbologia != null -> {
                val modulos = Barras.para(e.simbologia, e.esperado!!)
                val escena = Escena(
                    anchoRelativo = anchoCodigoPx(e.simbologia, e.esperado, e.pxPorModulo) / ancho,
                    proporcionAlto = PROPORCION_ALTO,
                    giroGrados = e.giro,
                    desenfoque = e.desenfoque,
                    ruido = e.ruido,
                    reflejo = e.reflejo,
                    semilla = e.semilla,
                )
                Lienzo.dibujar(modulos, Barras.zonaEnBlanco(e.simbologia), ancho, alto, escena)
            }
            else -> negativo(e, ancho, alto)
        }
        if (e.luz != 1f) Lienzo.ganancia(luma, e.luz)
        return luma
    }

    private fun negativo(e: EscenaBanco, ancho: Int, alto: Int): Luma {
        val azar = Random(e.semilla * 7919 + (e.negativo?.ordinal ?: 0))
        return when (e.negativo ?: Negativo.LISO) {
            Negativo.LISO -> Lienzo.dibujar(BooleanArray(0), 20, ancho, alto, Escena(anchoRelativo = 0.6f, ruido = e.ruido, semilla = e.semilla))
            Negativo.BARRAS_AL_AZAR -> {
                val modulos = BooleanArray(95) { azar.nextBoolean() }
                Lienzo.dibujar(modulos, 9, ancho, alto, Escena(anchoRelativo = 0.6f, ruido = e.ruido, semilla = e.semilla))
            }
            Negativo.RAYADO -> {
                val p = ByteArray(ancho * alto) { 228.toByte() }
                val separacion = (alto / 40).coerceAtLeast(6)
                for (y in separacion until alto step separacion) {
                    for (x in 0 until ancho) p[y * ancho + x] = 150.toByte()
                }
                // El margen rojo de la hoja, en gris.
                val margen = ancho / 8
                for (y in 0 until alto) p[y * ancho + margen] = 120.toByte()
                ruido(Luma(ancho, alto, p), e.ruido, azar)
            }
            Negativo.TEXTO -> {
                val p = ByteArray(ancho * alto) { 235.toByte() }
                val renglon = (alto / 32).coerceAtLeast(8)
                val letra = (renglon * 0.45f).roundToInt().coerceAtLeast(3)
                for (y0 in renglon until alto - renglon step renglon) {
                    var x = ancho / 12
                    while (x < ancho - ancho / 12) {
                        // Una palabra de 2 a 8 letras de ancho variable, y un espacio.
                        repeat(2 + azar.nextInt(7)) {
                            val w = (letra * (0.5f + azar.nextFloat())).roundToInt().coerceAtLeast(1)
                            val h = (letra * (0.7f + 0.6f * azar.nextFloat())).roundToInt()
                            for (yy in y0 - h until y0) for (xx in x until minOf(x + w, ancho)) {
                                if (yy >= 0) p[yy * ancho + xx] = 40.toByte()
                            }
                            x += w + 2
                        }
                        x += letra + 2
                    }
                }
                ruido(Luma(ancho, alto, p), e.ruido, azar)
            }
        }
    }

    private fun ruido(luma: Luma, sigma: Float, azar: Random): Luma {
        if (sigma <= 0f) return luma
        val p = luma.pixeles
        for (i in 0 until luma.ancho * luma.alto) {
            val n = (azar.nextFloat() + azar.nextFloat() + azar.nextFloat() - 1.5f) * 2f * sigma
            p[i] = ((p[i].toInt() and 0xFF) + n).roundToInt().coerceIn(0, 255).toByte()
        }
        return luma
    }

    private fun nombre(s: Simbologia, texto: String) = when {
        s == Simbologia.EAN_13 && texto.startsWith("2") -> "interno"
        else -> s.name.lowercase().replace("_", "")
    }

    private fun px(m: Float) = if (m % 1f == 0f) "${m.toInt()}px" else "${m}px"
}
