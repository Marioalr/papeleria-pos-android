package mx.papeleriapos.escaner.motores

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Rotacion
import mx.papeleriapos.escaner.nucleo.Simbologia
import zxingcpp.BarcodeReader
import zxingcpp.BarcodeReader.Format
import java.nio.ByteBuffer

/**
 * zxing-cpp (docs/escaner.md, «Motores»). Con el `ImageProxy` lee el plano Y directo, sin copiar: basta con poner la
 * región como `cropRect`. Sin él (banco de pruebas) copia la región a un `Bitmap` ALPHA_8, que el lado nativo toma
 * como luminancia sin convertir.
 *
 * Las posiciones vienen en el recorte ya girado (el lado nativo hace `ImageView.cropped(…).rotated(rotación)`, en
 * sentido horario como `rotationDegrees`), en píxeles enteros.
 *
 * @param probarGiros `tryRotate`: buscar también códigos girados 90°. La franja es horizontal y el empleado alinea el
 * código con ella, así que por omisión no se prueba y se ahorra ese tiempo (ver MotoresTest); en ajustes, «Leer también
 * girados 90°» lo enciende para medir en la tienda si vale la pena con los códigos de pie.
 */
class MotorZxingCpp(simbologias: Set<Simbologia>, probarGiros: Boolean = false) : MotorDeLectura {
    override val motor = Motor.ZXING_CPP

    private val lector: BarcodeReader
    /** Para la pasada con umbral: la imagen ya viene en blanco y negro. */
    private val lectorBinario: BarcodeReader

    init {
        val formatos = simbologias.mapNotNullTo(LinkedHashSet(), ::aFormatoZxing)
        require(formatos.isNotEmpty()) { "zxing-cpp no lee ninguna de: ${simbologias.joinToString { it.nombre }}." }
        lector = BarcodeReader(
            BarcodeReader.Options(
                formats = formatos,
                // En vivo el siguiente cuadro es el reintento: más rápido que esforzarse en uno.
                tryHarder = false,
                tryRotate = probarGiros,
                tryInvert = false,
                tryDownscale = false,
                // Dos líneas de escaneo iguales antes de dar un 1D por leído: menos lecturas falsas.
                minLineCount = 2,
                maxNumberOfSymbols = 3,
                // Los errores de zxing-cpp no traen caja útil para guiar con códigos 1D.
                returnErrors = false,
            ),
        )
        lectorBinario = BarcodeReader(lector.options.copy(binarizer = BarcodeReader.Binarizer.FIXED_THRESHOLD))
    }

    private val recorte = Rect()
    private val completo = Rect()
    private val punto = FloatArray(2)

    /** Para el banco de pruebas: el mapa de bits de la región y su copia en bytes, reusados mientras no cambie el tamaño. */
    private var mapa: Bitmap? = null
    private var bytesMapa = ByteArray(0)
    private var envoltura: ByteBuffer = ByteBuffer.wrap(bytesMapa)

    /**
     * Una segunda pasada sobre la región binarizada con [umbral] (el nivel entre barras y espacios que midió el
     * localizador). El binarizador local de zxing-cpp falla con una etiqueta blanca sobre un fondo gris medio (cartón,
     * empaques de color): toma el fondo por oscuro y se queda sin zona en blanco. Medido con un cuadro real del emulador:
     * sin esto no lee; con un umbral fijo, sí. Cuesta una copia de la región y una lectura (~1 ms).
     */
    fun leerConUmbral(cuadro: CuadroY, umbral: Int): ResultadoMotor {
        val r = cuadro.region
        if (r.ancho < MINIMO || r.alto < MINIMO || umbral !in 1..254) return ResultadoMotor.VACIO
        val bitmap = prepararMapa(r.ancho, r.alto)
        copiarPlanoY(cuadro.y, cuadro.pasoFila, r.izquierda, r.arriba, r.ancho, r.alto, bytesMapa, bitmap.rowBytes)
        val b = bytesMapa
        for (i in b.indices) b[i] = if ((b[i].toInt() and 0xFF) < umbral) 0 else -1
        envoltura.rewind()
        bitmap.copyPixelsFromBuffer(envoltura)
        val inicio = SystemClock.elapsedRealtimeNanos()
        val resultados = lectorBinario.read(bitmap, completo, cuadro.rotacion)
        return armar(resultados, cuadro, SystemClock.elapsedRealtimeNanos() - inicio)
    }

    override fun leer(cuadro: CuadroY): ResultadoMotor {
        val r = cuadro.region
        if (r.ancho < MINIMO || r.alto < MINIMO) return ResultadoMotor.VACIO
        val proxy = cuadro.proxy
        val inicio: Long
        val resultados: List<BarcodeReader.Result>
        if (proxy != null) {
            val original = proxy.cropRect
            recorte.set(r.izquierda, r.arriba, r.derecha, r.abajo)
            proxy.setCropRect(recorte)
            inicio = SystemClock.elapsedRealtimeNanos()
            try {
                resultados = lector.read(proxy)
            } finally {
                proxy.setCropRect(original)
            }
        } else {
            val bitmap = prepararMapa(r.ancho, r.alto)
            copiarPlanoY(cuadro.y, cuadro.pasoFila, r.izquierda, r.arriba, r.ancho, r.alto, bytesMapa, bitmap.rowBytes)
            envoltura.rewind()
            bitmap.copyPixelsFromBuffer(envoltura)
            inicio = SystemClock.elapsedRealtimeNanos()
            resultados = lector.read(bitmap, completo, cuadro.rotacion)
        }
        return armar(resultados, cuadro, SystemClock.elapsedRealtimeNanos() - inicio)
    }

    private fun armar(resultados: List<BarcodeReader.Result>, cuadro: CuadroY, nanos: Long): ResultadoMotor {
        val r = cuadro.region
        if (resultados.isEmpty()) return resultadoVacio(nanos)

        var lecturas: ArrayList<Lectura>? = null
        for (resultado in resultados) {
            val texto = resultado.text
            if (resultado.error != null || texto.isNullOrEmpty()) continue
            val p = resultado.position
            val esquinas = FloatArray(8)
            ponerEsquina(esquinas, 0, p.topLeft.x, p.topLeft.y, r.ancho, r.alto, cuadro.rotacion, r.izquierda, r.arriba)
            ponerEsquina(esquinas, 1, p.topRight.x, p.topRight.y, r.ancho, r.alto, cuadro.rotacion, r.izquierda, r.arriba)
            ponerEsquina(esquinas, 2, p.bottomRight.x, p.bottomRight.y, r.ancho, r.alto, cuadro.rotacion, r.izquierda, r.arriba)
            ponerEsquina(esquinas, 3, p.bottomLeft.x, p.bottomLeft.y, r.ancho, r.alto, cuadro.rotacion, r.izquierda, r.arriba)
            (lecturas ?: ArrayList<Lectura>(2).also { lecturas = it })
                .add(Lectura(texto, deFormatoZxing(resultado.format), esquinas))
        }
        return resultado(lecturas, null, nanos)
    }

    /** El lado nativo no guarda estado entre lecturas; solo se suelta el mapa de bits del banco. */
    override fun close() {
        mapa?.recycle()
        mapa = null
    }

    private fun prepararMapa(ancho: Int, alto: Int): Bitmap {
        mapa?.let { if (it.width == ancho && it.height == alto) return it }
        mapa?.recycle()
        val nuevo = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ALPHA_8)
        // rowBytes puede venir alineado: la copia respeta ese paso.
        bytesMapa = ByteArray(nuevo.rowBytes * alto)
        envoltura = ByteBuffer.wrap(bytesMapa)
        completo.set(0, 0, ancho, alto)
        mapa = nuevo
        return nuevo
    }

    /** Un punto del recorte derecho (píxel entero; se toma su centro) a píxeles del buffer sin rotar. */
    private fun ponerEsquina(destino: FloatArray, i: Int, x: Int, y: Int, ancho: Int, alto: Int, rotacion: Int, dx: Int, dy: Int) {
        Rotacion.aBuffer(x + 0.5f, y + 0.5f, ancho, alto, rotacion, punto)
        destino[2 * i] = punto[0] + dx
        destino[2 * i + 1] = punto[1] + dy
    }

    private companion object {
        const val MINIMO = 16
    }
}

/** La simbología como formato de zxing-cpp; null si no la lee. El ITF de las cajas es ITF-14. */
fun aFormatoZxing(simbologia: Simbologia): Format? = when (simbologia) {
    Simbologia.EAN_13 -> Format.EAN_13
    Simbologia.EAN_8 -> Format.EAN_8
    Simbologia.UPC_A -> Format.UPC_A
    Simbologia.UPC_E -> Format.UPC_E
    Simbologia.ITF -> Format.ITF_14
    Simbologia.CODE_128 -> Format.CODE_128
    Simbologia.CODE_39 -> Format.CODE_39
    Simbologia.CODE_93 -> Format.CODE_93
    Simbologia.CODABAR -> Format.CODABAR
    Simbologia.QR -> Format.QR_CODE
    Simbologia.DATA_MATRIX -> Format.DATA_MATRIX
    Simbologia.PDF_417 -> Format.PDF_417
    Simbologia.AZTEC -> Format.AZTEC
    Simbologia.OTRA -> null
}

/** Lo que zxing-cpp dice que leyó (a veces la variante, p. ej. QR modelo 2), como simbología del escáner. */
fun deFormatoZxing(formato: Format): Simbologia = when (formato) {
    Format.EAN_13, Format.ISBN -> Simbologia.EAN_13
    Format.EAN_8 -> Simbologia.EAN_8
    Format.UPC_A -> Simbologia.UPC_A
    Format.UPC_E -> Simbologia.UPC_E
    Format.ITF, Format.ITF_14 -> Simbologia.ITF
    Format.CODE_128 -> Simbologia.CODE_128
    Format.CODE_39, Format.CODE_39_STD, Format.CODE_39_EXT -> Simbologia.CODE_39
    Format.CODE_93 -> Simbologia.CODE_93
    Format.CODABAR -> Simbologia.CODABAR
    Format.QR_CODE, Format.QR_CODE_MODEL_1, Format.QR_CODE_MODEL_2 -> Simbologia.QR
    Format.DATA_MATRIX -> Simbologia.DATA_MATRIX
    Format.PDF_417, Format.COMPACT_PDF_417 -> Simbologia.PDF_417
    Format.AZTEC, Format.AZTEC_CODE -> Simbologia.AZTEC
    else -> Simbologia.OTRA
}
