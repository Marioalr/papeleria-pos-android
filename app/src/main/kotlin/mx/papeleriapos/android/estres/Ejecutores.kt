package mx.papeleriapos.android.estres

import mx.papeleriapos.escaner.Motor
import kotlin.math.max
import kotlin.math.min

/**
 * Lo que la prueba de ciclos le pide a la cámara. En el celular lo implementa [CamaraEstres] (un controlador propio con
 * su propio ciclo de vida); en las pruebas de la JVM, uno falso.
 */
interface CamaraBajoPrueba {
    /** Si se sabe cuándo la cámara está abierta y cerrada (el `CameraState` de CameraX); si no, solo cuenta el cuadro. */
    val observaEstado: Boolean

    suspend fun abrir()
    suspend fun esperarAbierta(limiteMs: Long): Boolean
    suspend fun esperarPrimerCuadro(limiteMs: Long): Boolean
    suspend fun cerrar()
    suspend fun esperarCerrada(limiteMs: Long): Boolean

    /** El error que reporta la cámara o el escáner ahora; null si no hay. */
    fun error(): String?

    /** Memoria, hilos y descriptores tras un GC. */
    suspend fun memoria(en: Int): PuntoMemoria
}

/**
 * (a) N ciclos abrir → cámara abierta → primer cuadro analizado → cerrar → cámara cerrada, con la memoria tras GC cada
 * [cadaMemoria] ciclos (y en el primero y el último). Un ciclo que falla se cierra y se sigue; si el siguiente sale bien
 * con el mismo controlador, el error se recuperó ([CriteriosCiclos.erroresSinRecuperar]).
 */
class CiclosCamara(
    private val camara: CamaraBajoPrueba,
    private val ciclos: Int = 200,
    private val cadaMemoria: Int = 25,
    private val limiteMs: Long = 5_000,
    private val relojNs: () -> Long = System::nanoTime,
) {
    suspend fun correr(
        alCiclo: (Ciclo, PuntoMemoria?) -> Unit = { _, _ -> },
        cancelado: () -> Boolean = { false },
    ): Pair<List<Ciclo>, List<PuntoMemoria>> {
        val hechos = ArrayList<Ciclo>(ciclos)
        val memoria = ArrayList<PuntoMemoria>()
        for (n in 1..ciclos) {
            if (cancelado()) break
            val t0 = relojNs()
            camara.abrir()
            val abierta = camara.esperarAbierta(limiteMs)
            val tAbierta = relojNs()
            val cuadro = camara.esperarPrimerCuadro(limiteMs)
            val tCuadro = relojNs()
            val errorAbrir = if (cuadro) null else camara.error() ?: if (camara.observaEstado && !abierta) "no abrió" else "sin cuadros"
            camara.cerrar()
            val tCerrar = relojNs()
            val cerrada = camara.esperarCerrada(limiteMs)
            val tCerrada = relojNs()
            val error = errorAbrir ?: if (camara.observaEstado && !cerrada) "no cerró" else null
            val ciclo = Ciclo(
                n = n,
                abrirMs = if (camara.observaEstado && abierta) ms(t0, tAbierta) else Double.NaN,
                primerCuadroMs = if (cuadro) ms(t0, tCuadro) else Double.NaN,
                cerrarMs = if (camara.observaEstado && cerrada) ms(tCerrar, tCerrada) else Double.NaN,
                error = error,
            )
            hechos += ciclo
            val punto = if (n == 1 || n % cadaMemoria == 0 || n == ciclos) camara.memoria(n).also { memoria += it } else null
            alCiclo(ciclo, punto)
        }
        return hechos to memoria
    }

    private fun ms(desde: Long, hasta: Long) = (hasta - desde) / 1e6
}

/** Lo que la ráfaga de zoom y linterna le pide a la cámara (directo a su `CameraControl`, sin pasar por el escáner). */
interface ControlesBajoPrueba {
    /** El zoom alto (2× o el máximo, lo que sea menor) o el mínimo de la cámara. */
    fun zoom(alto: Boolean)

    /** La linterna explícita: alternarla según el estado leído fallaría con un estado atrasado. */
    fun linterna(encender: Boolean)

    /** Un contador que sube mientras se analizan cuadros (para ver que no se detuvo). */
    fun cuadros(): Long

    fun fase(): String

    /** Espera a que se asiente y compara la interfaz con la cámara. */
    suspend fun final(): FinalRafaga
}

/**
 * (b) Zoom cada [cadaZoomMs] durante [duracionMs], alternando el mínimo y el alto, y linterna cada [cadaLinternaMs]
 * [vecesLinterna] veces, encendida en las pares y apagada en las nones (con un número par termina apagada),
 * registrando cada segundo cuántos cuadros se analizaron.
 */
class RafagaControles(
    private val controles: ControlesBajoPrueba,
    private val relojMs: () -> Long,
    private val esperar: suspend (Long) -> Unit,
    private val duracionMs: Long = 60_000,
    private val cadaZoomMs: Long = 30,
    private val vecesLinterna: Int = 100,
    private val cadaLinternaMs: Long = 200,
) {
    suspend fun correr(
        alSegundo: (SegundoRafaga) -> Unit = {},
        cancelado: () -> Boolean = { false },
    ): Pair<List<SegundoRafaga>, FinalRafaga?> {
        val segundos = ArrayList<SegundoRafaga>()
        val t0 = relojMs()
        var proxZoom = t0
        var proxLinterna = t0
        var proxSegundo = t0 + 1_000
        var zooms = 0
        var linternas = 0
        while (!cancelado()) {
            val ahora = relojMs()
            val zoomActivo = ahora - t0 < duracionMs
            if (!zoomActivo && linternas >= vecesLinterna) break
            if (zoomActivo && ahora >= proxZoom) {
                controles.zoom(zooms % 2 == 0)
                zooms++
                // Si el hilo se atrasó no se mandan órdenes de golpe para alcanzar.
                proxZoom = max(proxZoom + cadaZoomMs, ahora + 1)
            }
            if (linternas < vecesLinterna && ahora >= proxLinterna) {
                controles.linterna(linternas % 2 == 0)
                linternas++
                proxLinterna = max(proxLinterna + cadaLinternaMs, ahora + 1)
            }
            if (ahora >= proxSegundo) {
                val s = SegundoRafaga(segundos.size + 1, zooms, linternas, controles.cuadros(), controles.fase())
                segundos += s
                alSegundo(s)
                proxSegundo += 1_000
            }
            var siguiente = proxSegundo
            if (relojMs() - t0 < duracionMs) siguiente = min(siguiente, proxZoom)
            if (linternas < vecesLinterna) siguiente = min(siguiente, proxLinterna)
            esperar((siguiente - relojMs()).coerceAtLeast(1))
        }
        // El último segundo (para ver que siguieron llegando cuadros al terminar).
        segundos += SegundoRafaga(segundos.size + 1, zooms, linternas, controles.cuadros(), controles.fase()).also(alSegundo)
        return segundos to if (cancelado()) null else controles.final()
    }
}

/** Lo que la prueba de cambio de motor le pide al escáner. */
interface MotorBajoPrueba {
    fun cambiar(motor: Motor)

    /** Espera a que se analice un cuadro con [motor]; false si no pasó en [limiteMs]. */
    suspend fun esperarMotor(motor: Motor, limiteMs: Long): Boolean

    fun error(): String?

    suspend fun memoria(en: Int): PuntoMemoria
}

/**
 * (c) Cambia de motor en caliente [veces] veces, en rueda por [motores] (empieza en el primero, que ya está puesto), y
 * mide cuánto tarda en leer con el nuevo. La base de memoria se toma cuando ya se creó cada motor una vez.
 */
class CambiosMotor(
    private val escaner: MotorBajoPrueba,
    private val veces: Int = 50,
    private val motores: List<Motor> = Motor.entries,
    private val cadaMemoria: Int = 10,
    private val limiteMs: Long = 5_000,
    private val relojNs: () -> Long = System::nanoTime,
) {
    suspend fun correr(
        alCambio: (CambioMotor, PuntoMemoria?) -> Unit = { _, _ -> },
        cancelado: () -> Boolean = { false },
    ): Pair<List<CambioMotor>, List<PuntoMemoria>> {
        require(motores.size >= 2) { "Hacen falta al menos dos motores." }
        val cambios = ArrayList<CambioMotor>(veces)
        val memoria = ArrayList<PuntoMemoria>()
        for (n in 1..veces) {
            if (cancelado()) break
            val motor = motores[n % motores.size]
            val t0 = relojNs()
            escaner.cambiar(motor)
            val ok = escaner.esperarMotor(motor, limiteMs)
            val ms = (relojNs() - t0) / 1e6
            val c = CambioMotor(n, motor.name, if (ok) ms else Double.NaN, if (ok) null else escaner.error() ?: "no leyó con ${motor.nombre}")
            cambios += c
            val base = motores.size
            val punto = if (n == base || (n > base && n % cadaMemoria == 0) || n == veces) escaner.memoria(n).also { memoria += it } else null
            alCambio(c, punto)
        }
        return cambios to memoria
    }
}
