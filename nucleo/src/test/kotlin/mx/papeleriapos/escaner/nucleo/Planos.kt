package mx.papeleriapos.escaner.nucleo

import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Planos Y de prueba para el localizador: códigos dibujados con [Lienzo] y escenas sin código. */
object Planos {
    /** Un código de [simbologia] a [pxModulo] píxeles por módulo; [base] fija el resto de la escena. */
    fun codigo(
        simbologia: Simbologia,
        texto: String,
        pxModulo: Float,
        ancho: Int = 1280,
        alto: Int = 720,
        base: Escena = Escena(),
    ): Luma {
        val modulos = Barras.para(simbologia, texto)
        val zona = Barras.zonaEnBlanco(simbologia)
        val anchoRelativo = (modulos.size + 2 * zona) * pxModulo / ancho
        return Lienzo.dibujar(modulos, zona, ancho, alto, base.copy(anchoRelativo = anchoRelativo))
    }

    /** Copia el plano a un ByteBuffer con renglones de [ancho] + [relleno] bytes, como los de la cámara. */
    fun buffer(luma: Luma, relleno: Int = 0, directo: Boolean = true): Pair<ByteBuffer, Int> {
        val paso = luma.ancho + relleno
        // El último renglón llega sin relleno, igual que en ImageProxy.
        val tam = paso * (luma.alto - 1) + luma.ancho
        val b = if (directo) ByteBuffer.allocateDirect(tam) else ByteBuffer.allocate(tam)
        for (y in 0 until luma.alto) {
            b.position(y * paso)
            b.put(luma.pixeles, y * luma.ancho, luma.ancho)
        }
        b.rewind()
        return b to paso
    }

    /** Fondo con degradado, como una mesa o un empaque. */
    fun fondo(ancho: Int = 1280, alto: Int = 720): Luma {
        val l = Luma(ancho, alto)
        for (y in 0 until alto) for (x in 0 until ancho) l.pixeles[y * ancho + x] = (90 + 80 * x / ancho + 20 * y / alto).toByte()
        return l
    }

    /** Trazos cortos en direcciones al azar: lo que hay alrededor del código en un empaque. */
    fun trazos(semilla: Int, cuantos: Int = 3000, ancho: Int = 1280, alto: Int = 720): Luma {
        val r = Random(semilla)
        val l = fondo(ancho, alto)
        repeat(cuantos) {
            val x0 = r.nextInt(ancho); val y0 = r.nextInt(alto)
            val largo = 4 + r.nextInt(14); val a = r.nextDouble() * PI; val grueso = 1 + r.nextInt(3)
            val v = if (r.nextBoolean()) 30 else 220
            for (t in 0 until largo) for (k in 0 until grueso) {
                val x = (x0 + t * cos(a) - k * sin(a)).toInt(); val y = (y0 + t * sin(a) + k * cos(a)).toInt()
                if (x in 0 until ancho && y in 0 until alto) l.pixeles[y * ancho + x] = v.toByte()
            }
        }
        return ruido(l, r)
    }

    /** Renglones de «letras» con astas verticales: el texto es lo que más se parece a unas barras. */
    fun texto(semilla: Int, ancho: Int = 1280, alto: Int = 720): Luma {
        val r = Random(semilla)
        val l = fondo(ancho, alto)
        fun pon(x: Int, y: Int) {
            if (x in 0 until ancho && y in 0 until alto) l.pixeles[y * ancho + x] = 30
        }
        var yy = 40
        while (yy < alto - 30) {
            var xx = 30 + r.nextInt(20)
            while (xx < ancho - 30) {
                if (r.nextInt(6) == 0) {
                    xx += 8; continue
                }
                val astas = 1 + r.nextInt(2)
                for (k in 0 until astas) for (dy in 0 until 13) for (dx in 0 until 2) pon(xx + k * 4 + dx, yy + dy)
                if (r.nextBoolean()) {
                    val arriba = if (r.nextBoolean()) 0 else 11
                    for (dx in 0 until 6) for (dy in 0 until 2) pon(xx + dx, yy + arriba + dy)
                }
                xx += 9
            }
            yy += 22
        }
        return ruido(l, r)
    }

    /** Hoja de cuaderno: rayas horizontales cada 14 px. */
    fun rayada(semilla: Int, ancho: Int = 1280, alto: Int = 720): Luma {
        val r = Random(semilla)
        val l = fondo(ancho, alto)
        for (y in 0 until alto) if (y % 14 == 0) for (x in 0 until ancho) {
            l.pixeles[y * ancho + x] = 60
            l.pixeles[minOf(y + 1, alto - 1) * ancho + x] = 60
        }
        return ruido(l, r)
    }

    private fun ruido(l: Luma, r: Random, amplitud: Int = 6): Luma {
        val p = l.pixeles
        for (i in p.indices) p[i] = ((p[i].toInt() and 0xFF) + r.nextInt(-amplitud, amplitud + 1)).coerceIn(0, 255).toByte()
        return l
    }

    /** Diferencia entre dos ángulos de barras (módulo 180°), en grados. */
    fun difAngulo(aRad: Float, bGrados: Float): Float {
        var d = (Math.toDegrees(aRad.toDouble()) - bGrados) % 180.0
        if (d < 0) d += 180.0
        return minOf(d, 180.0 - d).let { abs(it).toFloat() }
    }
}
