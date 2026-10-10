package mx.papeleriapos.escaner.nucleo

import kotlin.math.sqrt

/** Cómo se usa el escáner: en venta (cada código distinto entra de inmediato) o en conteo (cada lectura suma uno). */
enum class Modo { VENTA, CONTEO }

/** Una lectura que pasó la validación y el consenso; [repetida] = el mismo código que la anterior aceptada. */
data class Aceptada(val codigo: CodigoLeido, val caja: Caja?, val nanos: Long, val repetida: Boolean)

/** Reglas de validación, consenso y repetidos (docs/escaner.md, «Validación y consenso»). */
data class ReglasConfirmacion(
    /** Lecturas iguales que hacen falta en la ventana para aceptar, por simbología. */
    val consenso: Map<Simbologia, Int> = mapOf(
        Simbologia.EAN_13 to 2, Simbologia.UPC_A to 2, Simbologia.UPC_E to 2, Simbologia.EAN_8 to 2,
        Simbologia.ITF to 2, Simbologia.CODE_128 to 2, Simbologia.QR to 1,
    ),
    /** Cuadros que se miran hacia atrás para el consenso. */
    val ventanaCuadros: Int = 3,
    /** El mismo código vuelve a contar solo si estuvo ausente al menos esto. */
    val msAusenciaParaRepetir: Long = 500,
)

/**
 * Valida (dígito verificador, largo del ITF), normaliza, exige consenso entre cuadros, elige el código más cercano al
 * centro de la franja y filtra repetidos.
 *
 * - Consenso: el código (ya normalizado) debe aparecer en [ReglasConfirmacion.consenso] de los últimos
 *   [ReglasConfirmacion.ventanaCuadros] cuadros, contando el actual. Un UPC-A de 12 y su EAN-13 con 0 cuentan juntos.
 * - Varios a la vista: solo se considera el que contiene el centro de la franja o, si ninguno, el más cercano a él;
 *   los demás esperan aunque tengan consenso (el empleado apunta al que quiere).
 * - Repetidos: un código ya aceptado vuelve a aceptarse solo después de [ReglasConfirmacion.msAusenciaParaRepetir] de
 *   ausencia (ni leído ni con un potencial o una lectura inválida que traslape más de 0.3 con su última caja). Un
 *   código que no se ha aceptado hace poco entra de inmediato. Se recuerdan varios aceptados, no solo el último: con
 *   dos códigos juntos a la vista, el centro de la franja puede caer alternadamente en uno y otro, y sin esta memoria
 *   se sumarían los dos una y otra vez.
 *
 * Se llama en cada cuadro analizado, aunque el motor no haya leído nada: así corren la ventana y la ausencia. En un
 * cuadro sin lecturas, o con las mismas de los cuadros anteriores, no asigna memoria.
 */
class Confirmador(val reglas: ReglasConfirmacion = ReglasConfirmacion()) {
    private val ventana = reglas.ventanaCuadros

    init {
        require(ventana >= 1) { "La ventana debe ser de al menos un cuadro." }
        require(reglas.consenso.values.all { it in 1..ventana }) { "El consenso debe caber en la ventana ($ventana)." }
        require(reglas.msAusenciaParaRepetir >= 0) { "La ausencia no puede ser negativa." }
    }

    private val necesarias = IntArray(Simbologia.entries.size) { reglas.consenso[Simbologia.entries[it]] ?: minOf(2, ventana) }
    private val msAusencia = reglas.msAusenciaParaRepetir * 1_000_000L

    // Los últimos cuadros, en anillo: hasta POR_CUADRO códigos válidos en cada uno.
    private val historia = arrayOfNulls<CodigoLeido>(ventana * POR_CUADRO)
    private val cuentaHistoria = IntArray(ventana)
    private var cuadro = -1

    // El cuadro actual.
    private val cajas = arrayOfNulls<Caja>(POR_CUADRO)
    private val cajasInvalidas = arrayOfNulls<Caja>(POR_CUADRO)
    private var invalidas = 0

    // Los aceptados hace poco y su ausencia.
    private val recCodigo = arrayOfNulls<CodigoLeido>(RECIENTES)
    private val recCaja = arrayOfNulls<Caja>(RECIENTES)
    /** Desde cuándo no se ve; −1 = presente. */
    private val recAusenteDesde = LongArray(RECIENTES)
    private val recVisto = LongArray(RECIENTES)
    /** Ya estuvo ausente lo suficiente: puede volver a contar. */
    private val recRearmado = BooleanArray(RECIENTES)
    private var ultima: CodigoLeido? = null

    /**
     * En el último cuadro procesado, el código a la vista es uno ya aceptado que todavía no se retira: la guía dice «ya
     * leído» en vez de «leyendo» (no va a contar otra vez hasta que desaparezca).
     */
    var yaLeidoEnCuadro = false
        private set

    // Caché de normalización: el mismo texto llega cuadro tras cuadro y no hay que volver a crear cadenas.
    private val cacheTexto = arrayOfNulls<String>(CACHE)
    private val cacheSimbologia = arrayOfNulls<Simbologia>(CACHE)
    private val cacheCodigo = arrayOfNulls<CodigoLeido>(CACHE)
    private var cacheSiguiente = 0

    /**
     * Procesa lo leído en un cuadro. [lecturas] ya trae cada caja en la región visible normalizada; [potenciales] son las
     * cajas de códigos vistos sin leer (cuentan como «sigue presente» para el filtro de repetidos). [franja] es la
     * región de lectura (el centro preferido). Devuelve la lectura aceptada en este cuadro, o null.
     */
    fun procesar(lecturas: List<Pair<Lectura, Caja?>>, potenciales: List<Caja>, franja: Caja, nanos: Long): Aceptada? {
        cuadro = (cuadro + 1) % ventana
        val base = cuadro * POR_CUADRO
        var n = 0
        invalidas = 0
        for (i in 0 until lecturas.size) {
            val (lectura, caja) = lecturas[i]
            val codigo = normalizarConCache(lectura)
            if (codigo == null) {
                // Una lectura mal hecha también dice que ahí sigue un código.
                if (caja != null && invalidas < POR_CUADRO) cajasInvalidas[invalidas++] = caja
            } else if (n < POR_CUADRO) {
                historia[base + n] = codigo; cajas[n] = caja; n++
            }
        }
        for (k in n until POR_CUADRO) {
            historia[base + k] = null; cajas[k] = null
        }
        for (k in invalidas until POR_CUADRO) cajasInvalidas[k] = null
        cuentaHistoria[cuadro] = n

        actualizarPresencia(base, n, potenciales, nanos)
        yaLeidoEnCuadro = false

        val elegido = elegir(n, franja)
        if (elegido < 0) {
            // Sin lectura en este cuadro: si sigue a la vista (por un potencial) uno ya contado, se avisa igual.
            yaLeidoEnCuadro = hayPresenteSinRearmar(nanos)
            return null
        }
        val codigo = historia[base + elegido]!!
        val r = reciente(codigo)
        if (r >= 0 && !recRearmado[r]) {
            yaLeidoEnCuadro = true
            return null
        }
        if (cuadrosCon(codigo) < necesarias[codigo.simbologia.ordinal]) return null
        val repetida = ultima?.let { mismo(it, codigo) } ?: false
        val caja = cajas[elegido]
        recordar(r, codigo, caja, nanos)
        ultima = codigo
        return Aceptada(codigo, caja, nanos, repetida)
    }

    fun reiniciar() {
        yaLeidoEnCuadro = false
        historia.fill(null); cuentaHistoria.fill(0); cuadro = -1
        cajas.fill(null); cajasInvalidas.fill(null); invalidas = 0
        recCodigo.fill(null); recCaja.fill(null); recAusenteDesde.fill(-1L); recVisto.fill(0L); recRearmado.fill(false)
        ultima = null
        cacheTexto.fill(null); cacheSimbologia.fill(null); cacheCodigo.fill(null); cacheSiguiente = 0
    }

    private fun hayPresenteSinRearmar(nanos: Long): Boolean {
        for (r in 0 until RECIENTES) {
            if (recCodigo[r] != null && !recRearmado[r] && recAusenteDesde[r] < 0 && recVisto[r] == nanos) return true
        }
        return false
    }

    private fun normalizarConCache(lectura: Lectura): CodigoLeido? {
        val texto = lectura.texto
        val s = lectura.simbologia
        for (i in 0 until CACHE) {
            if (cacheSimbologia[i] === s && cacheTexto[i] == texto) return cacheCodigo[i]
        }
        val codigo = normalizar(lectura)
        val i = cacheSiguiente
        cacheSiguiente = (cacheSiguiente + 1) % CACHE
        cacheTexto[i] = texto; cacheSimbologia[i] = s; cacheCodigo[i] = codigo
        return codigo
    }

    private fun actualizarPresencia(base: Int, n: Int, potenciales: List<Caja>, nanos: Long) {
        for (r in 0 until RECIENTES) {
            val codigo = recCodigo[r] ?: continue
            if (presente(r, codigo, base, n, potenciales)) {
                if (recAusenteDesde[r] >= 0 && nanos - recAusenteDesde[r] >= msAusencia) recRearmado[r] = true
                recAusenteDesde[r] = -1
                recVisto[r] = nanos
            } else if (recAusenteDesde[r] < 0) {
                recAusenteDesde[r] = nanos
            }
        }
    }

    private fun presente(r: Int, codigo: CodigoLeido, base: Int, n: Int, potenciales: List<Caja>): Boolean {
        for (k in 0 until n) {
            if (mismo(historia[base + k]!!, codigo)) {
                cajas[k]?.let { recCaja[r] = it }
                return true
            }
        }
        val ultimaCaja = recCaja[r] ?: return false
        for (i in 0 until potenciales.size) if (ultimaCaja.traslape(potenciales[i]) > TRASLAPE_PRESENTE) return true
        for (i in 0 until invalidas) if (ultimaCaja.traslape(cajasInvalidas[i]!!) > TRASLAPE_PRESENTE) return true
        return false
    }

    /** El índice (en el cuadro actual) del código que contiene el centro de la franja o el más cercano; −1 si no hay. */
    private fun elegir(n: Int, franja: Caja): Int {
        val px = franja.centroX; val py = franja.centroY
        var mejor = -1
        var mejorBorde = Float.MAX_VALUE
        var mejorCentro = Float.MAX_VALUE
        for (k in 0 until n) {
            val caja = cajas[k]
            var aBorde = SIN_CAJA
            var aCentro = SIN_CAJA
            if (caja != null) {
                // Distancia del centro de la franja a la caja: 0 si la contiene. Desempata la distancia entre centros.
                val dx = maxOf(caja.izquierda - px, 0f, px - caja.derecha)
                val dy = maxOf(caja.arriba - py, 0f, py - caja.abajo)
                aBorde = sqrt(dx * dx + dy * dy)
                val cx = caja.centroX - px; val cy = caja.centroY - py
                aCentro = sqrt(cx * cx + cy * cy)
            }
            if (aBorde < mejorBorde || (aBorde == mejorBorde && aCentro < mejorCentro)) {
                mejor = k; mejorBorde = aBorde; mejorCentro = aCentro
            }
        }
        return mejor
    }

    /** En cuántos cuadros de la ventana aparece [codigo]. */
    private fun cuadrosCon(codigo: CodigoLeido): Int {
        var cuenta = 0
        for (f in 0 until ventana) {
            val base = f * POR_CUADRO
            for (k in 0 until cuentaHistoria[f]) {
                if (mismo(historia[base + k]!!, codigo)) {
                    cuenta++; break
                }
            }
        }
        return cuenta
    }

    private fun reciente(codigo: CodigoLeido): Int {
        for (r in 0 until RECIENTES) {
            val c = recCodigo[r]
            if (c != null && mismo(c, codigo)) return r
        }
        return -1
    }

    private fun recordar(r0: Int, codigo: CodigoLeido, caja: Caja?, nanos: Long) {
        var r = r0
        if (r < 0) {
            // Un lugar libre o el que lleva más tiempo sin verse.
            r = 0
            for (i in 0 until RECIENTES) {
                if (recCodigo[i] == null) {
                    r = i; break
                }
                if (recVisto[i] < recVisto[r]) r = i
            }
        }
        recCodigo[r] = codigo; recCaja[r] = caja; recAusenteDesde[r] = -1; recVisto[r] = nanos; recRearmado[r] = false
    }

    companion object {
        private const val POR_CUADRO = 8
        private const val RECIENTES = 8
        private const val CACHE = 16
        private const val TRASLAPE_PRESENTE = 0.3f
        private const val SIN_CAJA = 1e9f

        /** Valida y normaliza una lectura; null si no es válida (p. ej. ITF que no es de 14 dígitos con su verificador). */
        fun normalizar(lectura: Lectura): CodigoLeido? = Normalizacion.normalizar(lectura)

        /** EAN-13, UPC-A y UPC-E son el mismo GTIN-13 una vez normalizados. */
        private fun familia(s: Simbologia): Int = when (s) {
            Simbologia.EAN_13, Simbologia.UPC_A, Simbologia.UPC_E -> -1
            else -> s.ordinal
        }

        private fun mismo(a: CodigoLeido, b: CodigoLeido) =
            a === b || (familia(a.simbologia) == familia(b.simbologia) && a.normalizado == b.normalizado)
    }
}
