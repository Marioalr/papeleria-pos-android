package mx.papeleriapos.escaner.nucleo

import java.nio.ByteBuffer
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Resultado reutilizable del [Localizador] (cero asignaciones por cuadro). Coordenadas en píxeles del plano Y SIN
 * rotar (el buffer de ImageAnalysis).
 */
class Localizacion {
    var encontrado = false
    /** 1 = código 1D (barras), 2 = textura densa (QR o texto; no se usa para guiar). */
    var tipo = 0
    var centroX = 0f
    var centroY = 0f
    /** Largo del código a lo largo del gradiente (a través de las barras), en px. */
    var largo = 0f
    /** Alto de las barras, en px. */
    var grosor = 0f
    /** Dirección del gradiente; 0 = barras verticales en el buffer. */
    var anguloRad = 0f
    var izquierda = 0
    var arriba = 0
    var derecha = 0
    var abajo = 0
    var tocaBorde = false
    /** Las barras llegan al borde del cuadro: el código está cortado. */
    var cortado = false
    var celdas = 0
    var coherencia = 0f
    /** Luminancia media en la región (0 a 255). */
    var brillo = 0f
    /** Fracción de muestras saturadas (≥ 250) en la región: reflejo. */
    var saturados = 0f
    var oscuros = 0f
    /** Ancho estimado del módulo (la barra más delgada), en px; 0 = desconocido. */
    var moduloPx = 0f
    /** Pendiente de los bordes normalizada de 0 a 1 (independiente del contraste y la escala). */
    var nitidez = 0f
    var transiciones = 0
    /** Pasa las pruebas de transiciones, proporción oscuro/claro y continuidad de las barras. */
    var verificado = false
    var continuidad = 0f
    var oscuroClaro = 0f
    /** Diferencia media de luminancia por celda contra el cuadro anterior (movimiento); −1 = sin cuadro previo. */
    var diferenciaCuadro = -1f
    /**
     * Luminancia media del 50 % central del cuadro (0 a 255), haya o no código; −1 = cuadro demasiado chico. Es la
     * medida para pedir luz: a oscuras el localizador no encuentra el código y [brillo] no se llena.
     */
    var brilloCentral = -1f
    var nanos = 0L

    fun reiniciar() {
        encontrado = false; tipo = 0; centroX = 0f; centroY = 0f; largo = 0f; grosor = 0f; anguloRad = 0f
        izquierda = 0; arriba = 0; derecha = 0; abajo = 0; tocaBorde = false; cortado = false; celdas = 0
        coherencia = 0f; brillo = 0f; saturados = 0f; oscuros = 0f; moduloPx = 0f; nitidez = 0f; transiciones = 0
        verificado = false; continuidad = 0f; oscuroClaro = 0f; diferenciaCuadro = -1f; brilloCentral = -1f
    }

    /**
     * Las cuatro esquinas del código girado (x0, y0, …, x3, y3) en píxeles del buffer: [largo] a través de las barras y
     * [grosor] a lo largo de ellas. Para pasarlas a la región visible con [Transformacion.cajaVisible].
     */
    fun esquinas(salida: FloatArray): FloatArray {
        val gx = cos(anguloRad); val gy = sin(anguloRad)
        val l = largo / 2f; val g = grosor / 2f
        // (gx, gy) cruza las barras; (−gy, gx) corre a lo largo de ellas.
        salida[0] = centroX - l * gx + g * gy; salida[1] = centroY - l * gy - g * gx
        salida[2] = centroX + l * gx + g * gy; salida[3] = centroY + l * gy - g * gx
        salida[4] = centroX + l * gx - g * gy; salida[5] = centroY + l * gy + g * gx
        salida[6] = centroX - l * gx - g * gy; salida[7] = centroY - l * gy + g * gx
        return salida
    }
}

/**
 * Localizador ligero de códigos 1D sobre el plano Y (docs/escaner.md, «Localizador»): dice dónde hay un código aunque
 * todavía no se pueda leer, qué tan grande se ve (px por módulo), qué tan nítido, y la luz y el reflejo. Sirve para
 * guiar al empleado; nunca decide una lectura.
 *
 * 1. Muestreo disperso y estratificado (paso ≈ lado largo / [muestrasLadoLargo]) con un gradiente en cruz de 2x2 a
 *    resolución completa: un promedio previo borraría las barras de 2 px.
 * 2. Tensor de estructura por celda ([muestrasPorCelda]² muestras), solo con muestras de borde, como el
 *    `BarcodeDetector` de OpenCV.
 * 3. Coherencia de orientación (Bazen y Gerez 2002); una celda es 1D si pasa [coherencia1D].
 * 4. Crecimiento de región sobre celdas coherentes con orientación parecida (vector de doble ángulo).
 * 5. Tres líneas de escaneo a resolución completa a través de las barras: módulo, nitidez, transiciones, continuidad
 *    entre líneas (correlación de sus derivadas) y extensión real (zona en blanco y si está cortado).
 *
 * Cambios contra el prototipo de la investigación, medidos con las pruebas: el gradiente en cruz (las diferencias
 * hacia adelante desde un mismo píxel hacían del ruido σ ≥ 4 una región enorme a 45° que tapaba el código) y la
 * continuidad sobre la derivada (un degradado de luz daba continuidad ~1 en texto).
 *
 * Sin asignaciones por cuadro: todo va en arreglos preasignados. [anchoMaximo] y [altoMaximo] fijan la capacidad
 * inicial; un cuadro más grande la amplía una sola vez.
 */
class Localizador(
    anchoMaximo: Int = 1920,
    altoMaximo: Int = 1920,
    private val muestrasPorCelda: Int = 8,
    /** ~320 da paso 4 a 1280x720. Bajar a 256 si en el celular más barato no cabe en ~3 ms. */
    private val muestrasLadoLargo: Int = 320,
) {
    // Parámetros: puntos de partida que se calibran con los celulares de la tienda.
    /**
     * |gx| + |gy| mínimo (por renglón: el gradiente en cruz suma dos) para contar una muestra como borde; el ruido del
     * sensor en Y queda por debajo de ~10.
     */
    var umbralBorde = 20
    var fraccionMinimaBordes = 0.06f
    /** OpenCV usa 0.9 con Scharr; aquí 0.7 y la verificación quita los falsos positivos. */
    var coherencia1D = 0.70f
    var difAnguloMaxGrados = 20f
    var celdasMinimas1D = 3
    var nivelSaturado = 250
    var nivelOscuro = 40
    /** EAN-8 da ~43, UPC-E ~33, EAN-13 ~59 y un Code 128 corto ≥ ~40. */
    var transicionesMinimas = 20
    /** Correlación entre dos líneas paralelas: las barras la dan cercana a 1; el texto, baja. */
    var continuidadMinima = 0.55f

    val resultado = Localizacion()

    private var capacidad = 0
    private var fila0 = ByteArray(0)
    private var fila1 = ByteArray(0)
    private var linea = IntArray(0)
    private var perfil0 = IntArray(0)
    private var perfil2 = IntArray(0)

    // Paso entero ⇒ menos de 2·muestrasLadoLargo/muestrasPorCelda celdas por lado, sea cual sea el cuadro.
    private val maxCeldas = (2 * muestrasLadoLargo / muestrasPorCelda + 2).let { it * it }
    private val sxx = IntArray(maxCeldas)
    private val syy = IntArray(maxCeldas)
    private val sxy = IntArray(maxCeldas)
    private val sumaY = IntArray(maxCeldas)
    private val saturadas = IntArray(maxCeldas)
    private val oscuras = IntArray(maxCeldas)
    private val bordes = IntArray(maxCeldas)
    private val minimo = IntArray(maxCeldas)
    private val maximo = IntArray(maxCeldas)
    /** 0 = plana, 1 = 1D, 2 = textura 2D. */
    private val clase = ByteArray(maxCeldas)
    /** Marca por época para no limpiar el arreglo en cada búsqueda. */
    private val visitada = IntArray(maxCeldas)
    private val ux = FloatArray(maxCeldas)
    private val uy = FloatArray(maxCeldas)
    private val cola = IntArray(maxCeldas)
    private val sumaAnterior = IntArray(maxCeldas)
    private var anchoRejillaAnterior = -1
    private var altoRejillaAnterior = -1
    private val histCorridas = IntArray(256)
    private val faseX = IntArray(muestrasPorCelda)
    private val faseY = IntArray(muestrasPorCelda)
    private var pasoFases = -1
    private val modulos3 = FloatArray(3)
    private val nitideces3 = FloatArray(3)
    private var epoca = 0
    private val posTransiciones = IntArray(4096)
    private var cuentaTransiciones = 0
    private var ultimoModulo = 0f
    private var ultimaNitidez = 0f
    private var ultimoOscuroClaro = 0f

    // Geometría del cuadro actual.
    private var anchoRejilla = 0
    private var altoRejilla = 0
    private var celdaPx = 0

    init {
        asegurarCapacidad(max(anchoMaximo, altoMaximo))
    }

    /**
     * @param y plano Y (`ImageProxy.planes[0].buffer`); se restaura `position()` al terminar. Se llama antes de
     *   entregar el cuadro al motor: ambos leen el mismo buffer.
     * @param pasoFila `planes[0].rowStride` (el `pixelStride` del plano Y siempre es 1).
     * @param preferidoX, preferidoY punto del buffer cerca del cual se prefiere el código (el centro de la franja).
     */
    fun procesar(
        y: ByteBuffer,
        pasoFila: Int,
        ancho: Int,
        alto: Int,
        preferidoX: Float = ancho / 2f,
        preferidoY: Float = alto / 2f,
    ): Localizacion {
        val t0 = System.nanoTime()
        val r = resultado
        r.reiniciar()
        require(ancho > 0 && alto > 0 && pasoFila >= ancho) { "Cuadro inválido: ${ancho}x$alto, paso $pasoFila" }
        asegurarCapacidad(max(ancho, alto))
        val posicion = y.position()
        try {
            acumular(y, pasoFila, ancho, alto)
            diferenciaDeCuadro()
            brilloDelCentro()
            clasificarCeldas()
            val semilla = mejorRegion(1, preferidoX, preferidoY)
            if (semilla >= 0) {
                describirRegion(semilla, 1, ancho, alto)
                lineasDeEscaneo(y, pasoFila, ancho, alto)
            } else {
                val semilla2 = mejorRegion(2, preferidoX, preferidoY)
                if (semilla2 >= 0) describirRegion(semilla2, 2, ancho, alto)
            }
        } finally {
            y.position(posicion)
        }
        r.nanos = System.nanoTime() - t0
        return r
    }

    /** Solo crece cuando cambia la resolución: nunca en el camino de cada cuadro. */
    private fun asegurarCapacidad(lado: Int) {
        if (lado <= capacidad) return
        capacidad = lado
        fila0 = ByteArray(lado + 2)
        fila1 = ByteArray(lado + 2)
        linea = IntArray(lado + 2)
        perfil0 = IntArray(lado + 2)
        perfil2 = IntArray(lado + 2)
    }

    private fun acumular(y: ByteBuffer, pasoFila: Int, ancho: Int, alto: Int) {
        val s = max(1, max(ancho, alto) / muestrasLadoLargo)
        if (pasoFases != s) {
            // Muestreo estratificado: cada renglón de muestras usa otra fase en x e y, para que un borde nítido nunca
            // «caiga entre muestras» (aliasing con módulos múltiplos del paso).
            for (k in 0 until muestrasPorCelda) {
                faseY[k] = k % s
                val g = k * 0.6180339887 + 0.3
                faseX[k] = ((g - floor(g)) * s).toInt()
            }
            pasoFases = s
        }
        // Diferencias a 2 px con paso 4: ve el doble de bordes que una diferencia de 1 px.
        val d = max(1, s / 2)
        val celda = muestrasPorCelda * s
        celdaPx = celda
        anchoRejilla = max(0, (ancho - d) / celda)
        altoRejilla = max(0, (alto - d) / celda)
        val n = anchoRejilla * altoRejilla
        Arrays.fill(sxx, 0, n, 0); Arrays.fill(syy, 0, n, 0); Arrays.fill(sxy, 0, n, 0)
        Arrays.fill(sumaY, 0, n, 0); Arrays.fill(saturadas, 0, n, 0); Arrays.fill(oscuras, 0, n, 0)
        Arrays.fill(bordes, 0, n, 0); Arrays.fill(minimo, 0, n, 255); Arrays.fill(maximo, 0, n, 0)
        val copia = anchoRejilla * celda + d
        // Las diferencias de 2x2 suman dos renglones: el doble de señal, y el umbral también se duplica.
        val umbral = 2 * umbralBorde; val sat = nivelSaturado; val osc = nivelOscuro
        val r0 = fila0; val r1 = fila1
        for (cy in 0 until altoRejilla) {
            val renglonCeldas = cy * anchoRejilla
            for (sy in 0 until muestrasPorCelda) {
                val yy = cy * celda + sy * s + faseY[sy]
                // position() + get relativo: el get absoluto por bloques pide API 35.
                y.position(yy * pasoFila); y.get(r0, 0, copia)
                y.position((yy + d) * pasoFila); y.get(r1, 0, copia)
                var p0 = faseX[sy]
                for (cx in 0 until anchoRejilla) {
                    var axx = 0; var ayy = 0; var axy = 0; var suma = 0; var st = 0; var dk = 0; var ed = 0
                    var mn = 255; var mx = 0
                    var p = p0
                    for (sx in 0 until muestrasPorCelda) {
                        val a = r0[p].toInt() and 0xFF
                        val b = r0[p + d].toInt() and 0xFF
                        val c = r1[p].toInt() and 0xFF
                        val e = r1[p + d].toInt() and 0xFF
                        // Cruz de 2x2: gx y gy no comparten píxel. Con diferencias hacia adelante desde el mismo píxel,
                        // el ruido del sensor (σ ≥ 4) las correlaciona y el fondo entero parecía barras a 45°.
                        val gx = b + e - a - c
                        val gy = c + e - a - b
                        suma += a
                        if (a < mn) mn = a
                        if (a > mx) mx = a
                        if (a >= sat) st++
                        if (a <= osc) dk++
                        val m = (if (gx < 0) -gx else gx) + (if (gy < 0) -gy else gy)
                        if (m >= umbral) {
                            ed++; axx += gx * gx; ayy += gy * gy; axy += gx * gy
                        }
                        p += s
                    }
                    val ci = renglonCeldas + cx
                    sxx[ci] += axx; syy[ci] += ayy; sxy[ci] += axy; sumaY[ci] += suma
                    saturadas[ci] += st; oscuras[ci] += dk; bordes[ci] += ed
                    if (mn < minimo[ci]) minimo[ci] = mn
                    if (mx > maximo[ci]) maximo[ci] = mx
                    p0 += celda
                }
            }
        }
    }

    /** Movimiento global barato: diferencia media de luminancia por celda contra el cuadro anterior. */
    private fun diferenciaDeCuadro() {
        val n = anchoRejilla * altoRejilla
        if (n > 0 && anchoRejilla == anchoRejillaAnterior && altoRejilla == altoRejillaAnterior) {
            var acc = 0L
            for (i in 0 until n) {
                val d = sumaY[i] - sumaAnterior[i]
                acc += if (d < 0) -d else d
            }
            resultado.diferenciaCuadro = acc.toFloat() / (n * muestrasPorCelda * muestrasPorCelda)
        }
        System.arraycopy(sumaY, 0, sumaAnterior, 0, n)
        anchoRejillaAnterior = anchoRejilla; altoRejillaAnterior = altoRejilla
    }

    private fun brilloDelCentro() {
        val x0 = anchoRejilla / 4; val x1 = anchoRejilla - anchoRejilla / 4
        val y0 = altoRejilla / 4; val y1 = altoRejilla - altoRejilla / 4
        var suma = 0L; var n = 0
        for (cy in y0 until y1) for (cx in x0 until x1) {
            suma += sumaY[cy * anchoRejilla + cx]; n++
        }
        resultado.brilloCentral = if (n == 0) -1f else suma.toFloat() / (n * muestrasPorCelda * muestrasPorCelda)
    }

    private fun clasificarCeldas() {
        val n = muestrasPorCelda * muestrasPorCelda
        val minBordes = (fraccionMinimaBordes * n).toInt().coerceAtLeast(2)
        val c2min = coherencia1D * coherencia1D
        for (i in 0 until anchoRejilla * altoRejilla) {
            clase[i] = 0
            if (bordes[i] < minBordes) continue
            val fxx = sxx[i].toFloat(); val fyy = syy[i].toFloat(); val fxy = sxy[i].toFloat()
            val d = fxx - fyy; val t = fxx + fyy
            if (t <= 0f) continue
            val num = d * d + 4f * fxy * fxy
            // Coherencia al cuadrado: ahorra la raíz en las celdas que no pasan.
            val c2 = num / (t * t)
            if (c2 >= c2min) {
                val inv = 1f / sqrt(num)
                // Vector unitario de doble ángulo: θ y θ + 180° son la misma orientación de barras.
                ux[i] = d * inv; uy[i] = 2f * fxy * inv
                clase[i] = 1
            } else if (c2 <= 0.30f && bordes[i] >= n / 3 && maximo[i] - minimo[i] >= 60) {
                clase[i] = 2
            }
        }
    }

    /** La celda semilla de la mejor región del [tipo] dado, o −1. */
    private fun mejorRegion(tipo: Int, preferidoX: Float, preferidoY: Float): Int {
        nuevaEpoca()
        val cosT = cos(Math.toRadians(2.0 * difAnguloMaxGrados)).toFloat()
        val diagonal = sqrt((anchoRejilla * anchoRejilla + altoRejilla * altoRejilla).toFloat())
        val pcx = preferidoX / celdaPx - 0.5f; val pcy = preferidoY / celdaPx - 0.5f
        var mejor = -1; var mejorPuntaje = 0f
        val k = tipo.toByte()
        val minCeldas = if (tipo == 1) celdasMinimas1D else 4
        for (semilla in 0 until anchoRejilla * altoRejilla) {
            if (clase[semilla] != k || visitada[semilla] == epoca) continue
            val cuenta = crecer(semilla, tipo, cosT, epoca)
            if (cuenta < minCeldas) continue
            var sx = 0f; var sy = 0f
            for (q in 0 until cuenta) {
                val c = cola[q]; sx += c % anchoRejilla; sy += c / anchoRejilla
            }
            val dx = sx / cuenta - pcx; val dy = sy / cuenta - pcy
            val dist = sqrt(dx * dx + dy * dy) / diagonal
            // Prefiere regiones grandes y cercanas al centro de la franja.
            val puntaje = cuenta / (1f + 3f * dist)
            if (puntaje > mejorPuntaje) {
                mejorPuntaje = puntaje; mejor = semilla
            }
        }
        return mejor
    }

    private fun nuevaEpoca() {
        epoca++
        if (epoca == Int.MAX_VALUE) {
            Arrays.fill(visitada, 0); epoca = 1
        }
    }

    /**
     * BFS con 8 vecinos y «puente» a distancia 2 solo si la celda intermedia es plana (un espacio ancho sin bordes,
     * típico dentro de un código con módulos grandes). Deja las celdas en cola[0 until cuenta].
     */
    private fun crecer(semilla: Int, tipo: Int, cosT: Float, marca: Int): Int {
        var cabeza = 0; var fin = 0
        cola[fin++] = semilla; visitada[semilla] = marca
        val sux = ux[semilla]; val suy = uy[semilla]
        val k = tipo.toByte()
        val minBordes = (fraccionMinimaBordes * muestrasPorCelda * muestrasPorCelda).toInt().coerceAtLeast(2)
        val th = 0.5f * atan2(suy, sux)
        val gdx = cos(th); val gdy = sin(th)
        while (cabeza < fin) {
            val c = cola[cabeza++]
            val cx = c % anchoRejilla; val cy = c / anchoRejilla
            for (dy in -2..2) {
                val ny = cy + dy
                if (ny < 0 || ny >= altoRejilla) continue
                for (dx in -2..2) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx
                    if (nx < 0 || nx >= anchoRejilla) continue
                    val lejos = dx == 2 || dx == -2 || dy == 2 || dy == -2
                    if (lejos) {
                        // Solo las 8 direcciones a distancia 2 y, en 1D, solo a través de las barras.
                        if ((dx and 1) != 0 || (dy and 1) != 0) continue
                        if (tipo == 1 && abs(dx * gdx + dy * gdy) < 0.7f * sqrt((dx * dx + dy * dy).toFloat())) continue
                        val medio = (cy + dy / 2) * anchoRejilla + (cx + dx / 2)
                        if (bordes[medio] >= minBordes) continue
                    }
                    val ni = ny * anchoRejilla + nx
                    if (visitada[ni] == marca || clase[ni] != k) continue
                    if (tipo == 1 && ux[ni] * sux + uy[ni] * suy < cosT) continue
                    visitada[ni] = marca
                    cola[fin++] = ni
                }
            }
        }
        return fin
    }

    private fun describirRegion(semilla: Int, tipo: Int, ancho: Int, alto: Int) {
        nuevaEpoca()
        val cosT = cos(Math.toRadians(2.0 * difAnguloMaxGrados)).toFloat()
        val cuenta = crecer(semilla, tipo, cosT, epoca)
        var txx = 0f; var tyy = 0f; var txy = 0f
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        var suma = 0L; var st = 0; var dk = 0
        for (q in 0 until cuenta) {
            val c = cola[q]
            txx += sxx[c]; tyy += syy[c]; txy += sxy[c]
            val x = c % anchoRejilla; val yy = c / anchoRejilla
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (yy < minY) minY = yy
            if (yy > maxY) maxY = yy
            suma += sumaY[c]; st += saturadas[c]; dk += oscuras[c]
        }
        val theta = 0.5f * atan2(2f * txy, txx - tyy)
        val gxu = cos(theta); val gyu = sin(theta)
        var pMin = Float.MAX_VALUE; var pMax = -Float.MAX_VALUE; var qMin = Float.MAX_VALUE; var qMax = -Float.MAX_VALUE
        var cxs = 0f; var cys = 0f
        for (q in 0 until cuenta) {
            val c = cola[q]
            val px = (c % anchoRejilla + 0.5f) * celdaPx; val py = (c / anchoRejilla + 0.5f) * celdaPx
            cxs += px; cys += py
            val a = px * gxu + py * gyu; val b = -px * gyu + py * gxu
            if (a < pMin) pMin = a
            if (a > pMax) pMax = a
            if (b < qMin) qMin = b
            if (b > qMax) qMax = b
        }
        val r = resultado
        val d = txx - tyy; val t = txx + tyy
        r.encontrado = true; r.tipo = tipo; r.celdas = cuenta
        r.coherencia = if (t > 0f) sqrt(d * d + 4f * txy * txy) / t else 0f
        r.anguloRad = theta
        r.centroX = cxs / cuenta; r.centroY = cys / cuenta
        r.largo = pMax - pMin + celdaPx; r.grosor = qMax - qMin + celdaPx
        r.izquierda = minX * celdaPx; r.arriba = minY * celdaPx
        r.derecha = min(ancho, (maxX + 1) * celdaPx); r.abajo = min(alto, (maxY + 1) * celdaPx)
        r.tocaBorde = minX == 0 || minY == 0 || maxX >= anchoRejilla - 1 || maxY >= altoRejilla - 1
        val n = (cuenta * muestrasPorCelda * muestrasPorCelda).toFloat()
        r.brillo = suma / n; r.saturados = st / n; r.oscuros = dk / n
    }

    /**
     * Tres líneas de escaneo a resolución completa A TRAVÉS de las barras (en la dirección del gradiente), con muestreo
     * bilineal a paso de 1 px: ancho del módulo, nitidez normalizada y número de transiciones.
     */
    private fun lineasDeEscaneo(y: ByteBuffer, pasoFila: Int, ancho: Int, alto: Int) {
        val r = resultado
        val gx = cos(r.anguloRad); val gy = sin(r.anguloRad)
        // Geometría congelada: las tres líneas comparten origen a lo largo del gradiente, así los perfiles se alinean.
        val cx0 = r.centroX; val cy0 = r.centroY; val largo0 = r.largo
        // Una celda de más por lado: la rejilla no cubre el último pedazo del cuadro y la zona en blanco puede caer fuera
        // de la región; sin esto, un código cortado parecía terminar antes del borde.
        val mitad = largo0 * 0.5f + celdaPx
        val n = min(linea.size - 2, (2f * mitad).toInt())
        val maxX = ancho - 1.001f; val maxY = alto - 1.001f
        var validas = 0
        var origenX = 0f; var origenY = 0f; var centralOk = false
        // La central va al final: deja sus transiciones para refinarExtension.
        for (j in 0 until 3) {
            val k = if (j == 0) 0 else if (j == 1) 2 else 1
            // Separación total 0.16·largo, menor que el alto de las barras (≥ ~0.25·largo).
            val desp = (k - 1) * 0.08f * largo0
            val ox = cx0 - desp * gy - mitad * gx
            val oy = cy0 + desp * gx - mitad * gy
            for (i in 0 until n) {
                val fx = (ox + i * gx).coerceIn(0f, maxX); val fy = (oy + i * gy).coerceIn(0f, maxY)
                val x0 = fx.toInt(); val y0 = fy.toInt()
                val ax = fx - x0; val ay = fy - y0
                val base = y0 * pasoFila + x0
                val p00 = y.get(base).toInt() and 0xFF
                val p10 = y.get(base + 1).toInt() and 0xFF
                val p01 = y.get(base + pasoFila).toInt() and 0xFF
                val p11 = y.get(base + pasoFila + 1).toInt() and 0xFF
                val arr = p00 + (p10 - p00) * ax
                val aba = p01 + (p11 - p01) * ax
                linea[i] = (arr + (aba - arr) * ay + 0.5f).toInt()
            }
            if (k == 0) System.arraycopy(linea, 0, perfil0, 0, n)
            if (k == 2) System.arraycopy(linea, 0, perfil2, 0, n)
            if (analizarLinea(n)) {
                modulos3[validas] = ultimoModulo; nitideces3[validas] = ultimaNitidez; validas++
                if (k == 1) {
                    r.oscuroClaro = ultimoOscuroClaro; origenX = ox; origenY = oy; centralOk = true
                }
            }
        }
        // Tolera ±12° de error en la orientación.
        r.continuidad = correlacion(n, (0.16f * largo0 * 0.21f).toInt() + 2)
        if (validas > 0) {
            r.moduloPx = mediana(modulos3, validas)
            r.nitidez = mediana(nitideces3, validas)
        }
        if (centralOk) refinarExtension(origenX, origenY, gx, gy, n, ancho, alto)
        r.verificado = r.transiciones >= transicionesMinimas && r.continuidad >= continuidadMinima &&
            r.oscuroClaro in 0.35f..2.8f
    }

    /**
     * El tramo denso de transiciones alrededor del centro, cortado donde aparece un hueco mayor que la zona en blanco
     * (~10 módulos). Da el largo y el centro reales (no cuantizados a celdas) y si el código está cortado por el borde.
     */
    private fun refinarExtension(ox: Float, oy: Float, gx: Float, gy: Float, n: Int, ancho: Int, alto: Int) {
        val r = resultado
        if (cuentaTransiciones < 8) return
        val hueco = max(12f, 10f * ultimoModulo)
        val medio = n / 2
        val tp = posTransiciones
        var c = 0
        while (c < cuentaTransiciones - 1 && tp[c + 1] < medio) c++
        var a = c; var b = c
        while (a > 0 && tp[a] - tp[a - 1] <= hueco) a--
        while (b < cuentaTransiciones - 1 && tp[b + 1] - tp[b] <= hueco) b++
        if (b - a < 8) return
        val p0 = tp[a].toFloat(); val p1 = tp[b].toFloat()
        r.largo = p1 - p0
        val pc = (p0 + p1) * 0.5f
        r.centroX = ox + pc * gx; r.centroY = oy + pc * gy
        // Cortado: el tramo llega al inicio o al fin del perfil y ese extremo está en el borde del cuadro.
        val e0x = ox + p0 * gx; val e0y = oy + p0 * gy; val e1x = ox + p1 * gx; val e1y = oy + p1 * gy
        // La última transición visible queda, a lo más, a una barra ancha (4 módulos) del borde.
        val margen = max(2f * celdaPx / 8f + 2f, 4f * ultimoModulo + 2f)
        r.cortado = (a == 0 && (e0x < margen || e0y < margen || e0x > ancho - margen || e0y > alto - margen)) ||
            (b == cuentaTransiciones - 1 && (e1x < margen || e1y < margen || e1x > ancho - margen || e1y > alto - margen))
    }

    /**
     * Correlación normalizada máxima entre las derivadas de perfil0 y perfil2 con desplazamiento ±[maxDesp]. Sobre la
     * derivada y no sobre el perfil: un degradado de luz común a las dos líneas daba correlación ~1 aunque no hubiera
     * barras (texto sobre un empaque iluminado de lado).
     */
    private fun correlacion(n: Int, maxDesp: Int): Float {
        if (n < 32) return 0f
        val p0 = perfil0; val p2 = perfil2
        var mejor = -1f
        for (sh in -maxDesp..maxDesp) {
            val a0 = max(1, 1 - sh); val a1 = min(n - 1, n - 1 - sh)
            if (a1 - a0 < 24) continue
            var sa = 0L; var sb = 0L; var saa = 0L; var sbb = 0L; var sab = 0L
            var i = a0; var k = 0
            // Cada 2 muestras con diferencia central de 2 px: cubre todos los píxeles a la mitad del costo.
            while (i < a1) {
                val a = p0[i + 1] - p0[i - 1]; val b = p2[i + sh + 1] - p2[i + sh - 1]
                sa += a; sb += b; saa += a * a; sbb += b * b; sab += a * b
                i += 2; k++
            }
            val va = saa - sa * sa / k.toDouble(); val vb = sbb - sb * sb / k.toDouble()
            if (va <= 0 || vb <= 0) continue
            val c = ((sab - sa * sb / k.toDouble()) / sqrt(va * vb)).toFloat()
            if (c > mejor) mejor = c
        }
        return mejor
    }

    private fun analizarLinea(n: Int): Boolean {
        if (n < 16) return false
        val s = linea
        var mn = 255; var mx = 0
        for (i in 0 until n) {
            val v = s[i]
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        val rango = mx - mn
        if (rango < 24) return false
        val umbral = (mn + mx) ushr 1
        Arrays.fill(histCorridas, 0)
        var oscuroAntes = s[0] < umbral
        var inicioCorrida = 0
        var transiciones = 0
        var sumaSaltos = 0
        var corridas = 0
        var primera = true
        var largoOscuro = 0; var largoClaro = 0
        for (i in 1 until n) {
            val oscuro = s[i] < umbral
            if (oscuro != oscuroAntes) {
                var salto = abs(s[i] - s[i - 1])
                if (i + 1 < n) salto = max(salto, abs(s[i + 1] - s[i]))
                if (i >= 2) salto = max(salto, abs(s[i - 1] - s[i - 2]))
                sumaSaltos += salto
                if (transiciones < posTransiciones.size) posTransiciones[transiciones] = i
                transiciones++
                // La primera corrida es fondo: no cuenta.
                if (!primera) {
                    histCorridas[min(255, i - inicioCorrida)]++; corridas++
                    if (oscuroAntes) largoOscuro += i - inicioCorrida else largoClaro += i - inicioCorrida
                }
                primera = false
                inicioCorrida = i
                oscuroAntes = oscuro
            }
        }
        cuentaTransiciones = min(transiciones, posTransiciones.size)
        resultado.transiciones = max(resultado.transiciones, transiciones)
        if (transiciones < 8 || corridas < 6) return false
        // El percentil 25 de las corridas internas ≈ un módulo.
        val objetivo = corridas / 4
        var acc = 0; var p25 = 1
        for (largo in 1 until 256) {
            acc += histCorridas[largo]
            if (acc > objetivo) {
                p25 = largo; break
            }
        }
        ultimoModulo = p25.toFloat()
        ultimaNitidez = (sumaSaltos.toFloat() / transiciones) / rango
        ultimoOscuroClaro = if (largoClaro > 0) largoOscuro.toFloat() / largoClaro else 99f
        return true
    }

    private fun mediana(a: FloatArray, n: Int): Float = when (n) {
        1 -> a[0]
        2 -> (a[0] + a[1]) * 0.5f
        else -> max(min(a[0], a[1]), min(max(a[0], a[1]), a[2]))
    }
}
