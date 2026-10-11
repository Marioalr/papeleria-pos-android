package mx.papeleriapos.escaner.nucleo

/**
 * Un rectángulo normalizado (0 a 1) sobre la REGIÓN VISIBLE, derecha, como la ve el empleado en pantalla. Es el espacio
 * común de todo el escáner (docs/escaner.md, «Coordenadas»): la franja de lectura, las cajas de los motores y del
 * localizador, y lo que dibuja la interfaz. x crece a la derecha, y hacia abajo.
 */
data class Caja(val izquierda: Float, val arriba: Float, val derecha: Float, val abajo: Float) {
    val ancho get() = derecha - izquierda
    val alto get() = abajo - arriba
    val centroX get() = (izquierda + derecha) / 2f
    val centroY get() = (arriba + abajo) / 2f

    fun contiene(x: Float, y: Float) = x in izquierda..derecha && y in arriba..abajo

    /** Área de la intersección entre el área de la menor; 0 si no se tocan. */
    fun traslape(otra: Caja): Float {
        val w = minOf(derecha, otra.derecha) - maxOf(izquierda, otra.izquierda)
        val h = minOf(abajo, otra.abajo) - maxOf(arriba, otra.arriba)
        if (w <= 0f || h <= 0f) return 0f
        val menor = minOf(ancho * alto, otra.ancho * otra.alto)
        return if (menor <= 0f) 0f else (w * h) / menor
    }

    companion object {
        /** La caja que encierra los puntos [xy] (x0, y0, x1, y1, …). */
        fun deEsquinas(xy: FloatArray): Caja {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for (i in xy.indices step 2) {
                l = minOf(l, xy[i]); r = maxOf(r, xy[i])
                t = minOf(t, xy[i + 1]); b = maxOf(b, xy[i + 1])
            }
            return Caja(l, t, r, b)
        }
    }
}

/** Un rectángulo en píxeles enteros del buffer de la cámara (sin rotar). */
data class Rectangulo(val izquierda: Int, val arriba: Int, val derecha: Int, val abajo: Int) {
    val ancho get() = derecha - izquierda
    val alto get() = abajo - arriba
}

/**
 * Pasa puntos entre el buffer de la cámara (sin rotar, en píxeles) y la región visible normalizada y derecha.
 *
 * [visible] es la parte del buffer que se ve en pantalla (el `cropRect` del `ImageProxy` cuando la sesión tiene un
 * `ViewPort`); [rotacion] son los grados que hay que girar el buffer, en sentido horario, para dejarlo derecho
 * (`imageInfo.rotationDegrees`: 0, 90, 180 o 270).
 */
class Transformacion(val visible: Rectangulo, val rotacion: Int) {
    init {
        require(rotacion == 0 || rotacion == 90 || rotacion == 180 || rotacion == 270) { "Rotación inválida: $rotacion" }
    }

    /** Ancho y alto de la región visible ya derecha, en píxeles del buffer. */
    val anchoDerecho get() = if (rotacion % 180 == 0) visible.ancho else visible.alto
    val altoDerecho get() = if (rotacion % 180 == 0) visible.alto else visible.ancho

    /** Un punto del buffer a la región visible normalizada; escribe x en [salida][0] e y en [salida][1]. */
    fun aVisible(xBuffer: Float, yBuffer: Float, salida: FloatArray) {
        val x = xBuffer - visible.izquierda
        val y = yBuffer - visible.arriba
        val w = visible.ancho.toFloat()
        val h = visible.alto.toFloat()
        // Girar en sentido horario: 90° → (h − y, x); 180° → (w − x, h − y); 270° → (y, w − x).
        val (ux, uy) = when (rotacion) {
            90 -> (h - y) to x
            180 -> (w - x) to (h - y)
            270 -> y to (w - x)
            else -> x to y
        }
        salida[0] = ux / anchoDerecho
        salida[1] = uy / altoDerecho
    }

    /** Un punto de la región visible normalizada al buffer; escribe x en [salida][0] e y en [salida][1]. */
    fun aBuffer(xVisible: Float, yVisible: Float, salida: FloatArray) {
        val ux = xVisible * anchoDerecho
        val uy = yVisible * altoDerecho
        val w = visible.ancho.toFloat()
        val h = visible.alto.toFloat()
        val (x, y) = when (rotacion) {
            90 -> uy to (h - ux)
            180 -> (w - ux) to (h - uy)
            270 -> (w - uy) to ux
            else -> ux to uy
        }
        salida[0] = x + visible.izquierda
        salida[1] = y + visible.arriba
    }

    /** Una caja de la región visible al rectángulo del buffer que la contiene (para recortar la franja). */
    fun aBuffer(caja: Caja): Rectangulo {
        val p = FloatArray(2)
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for ((x, y) in listOf(caja.izquierda to caja.arriba, caja.derecha to caja.arriba, caja.derecha to caja.abajo, caja.izquierda to caja.abajo)) {
            aBuffer(x, y, p)
            l = minOf(l, p[0]); r = maxOf(r, p[0]); t = minOf(t, p[1]); b = maxOf(b, p[1])
        }
        return Rectangulo(
            l.toInt().coerceIn(visible.izquierda, visible.derecha), t.toInt().coerceIn(visible.arriba, visible.abajo),
            (r + 0.999f).toInt().coerceIn(visible.izquierda, visible.derecha), (b + 0.999f).toInt().coerceIn(visible.arriba, visible.abajo),
        )
    }

    /** Las esquinas [xyBuffer] (x0, y0, x1, y1, …) en el buffer a la caja normalizada que las encierra. */
    fun cajaVisible(xyBuffer: FloatArray): Caja {
        val p = FloatArray(2)
        val salida = FloatArray(xyBuffer.size)
        for (i in xyBuffer.indices step 2) {
            aVisible(xyBuffer[i], xyBuffer[i + 1], p)
            salida[i] = p[0]; salida[i + 1] = p[1]
        }
        return Caja.deEsquinas(salida)
    }
}

/**
 * Giros de un rectángulo de [ancho]×[alto] (el buffer o un recorte) a su versión derecha y de regreso. Lo usan los
 * motores para devolver sus esquinas en píxeles del buffer.
 */
object Rotacion {
    /** Del buffer (sin rotar) a derecho: el resultado vive en un rectángulo de alto×ancho si gira 90 o 270. */
    fun aDerecho(x: Float, y: Float, ancho: Int, alto: Int, grados: Int, salida: FloatArray) {
        val w = ancho.toFloat(); val h = alto.toFloat()
        when (grados) {
            90 -> { salida[0] = h - y; salida[1] = x }
            180 -> { salida[0] = w - x; salida[1] = h - y }
            270 -> { salida[0] = y; salida[1] = w - x }
            else -> { salida[0] = x; salida[1] = y }
        }
    }

    /** De derecho al buffer de [ancho]×[alto] (medidas del buffer, sin rotar). */
    fun aBuffer(x: Float, y: Float, ancho: Int, alto: Int, grados: Int, salida: FloatArray) {
        val w = ancho.toFloat(); val h = alto.toFloat()
        when (grados) {
            90 -> { salida[0] = y; salida[1] = h - x }
            180 -> { salida[0] = w - x; salida[1] = h - y }
            270 -> { salida[0] = w - y; salida[1] = x }
            else -> { salida[0] = x; salida[1] = y }
        }
    }
}
