package mx.papeleriapos.android.sesion

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import mx.papeleriapos.android.BuildConfig
import mx.papeleriapos.escaner.Apertura
import mx.papeleriapos.escaner.MarcasApertura

/**
 * Mide batería, CPU y temperatura en cada intento (una vez por lectura, no por cuadro). La CPU va en delta desde la
 * medición anterior: el acumulado del proceso no dice nada de un intento.
 */
class MedidorAndroid(context: Context) {
    private val bateria = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
    private val energia = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
    private val delta = DeltaCpu().also { it.siguiente(Process.getElapsedCpuTime(), SystemClock.elapsedRealtime()) }

    /**
     * [apertura] es la del escáner (`EstadoEscaner.apertura`); por omisión, las marcas compartidas de donde sale.
     */
    fun medir(apertura: Apertura = MarcasApertura.apertura): Medicion {
        val (cpu, pared) = delta.siguiente(Process.getElapsedCpuTime(), SystemClock.elapsedRealtime())
        return Medicion(
            bateriaPct = bateria?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: -1,
            cpuMs = cpu,
            estadoTermico = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) energia?.currentThermalStatus ?: -1 else -1,
            paredMs = pared,
            apertura = MedidasApertura.de(apertura),
        )
    }

    companion object {
        fun equipo() = DatosEquipo(
            dispositivo = "${Build.MANUFACTURER} ${Build.MODEL}",
            sdk = Build.VERSION.SDK_INT,
            versionApp = BuildConfig.VERSION_NAME,
            commit = BuildConfig.COMMIT,
        )
    }
}

/** CPU y reloj desde la llamada anterior (el medidor la llama una vez al crearse). */
class DeltaCpu {
    private var cpuPrevio = -1L
    private var paredPrevia = -1L

    /** Devuelve (ms de CPU, ms de reloj) desde la llamada anterior; (−1, −1) la primera vez. */
    @Synchronized
    fun siguiente(cpuMs: Long, paredMs: Long): Pair<Long, Long> {
        val r = if (cpuPrevio < 0 || cpuMs < cpuPrevio || paredMs < paredPrevia) -1L to -1L
        else (cpuMs - cpuPrevio) to (paredMs - paredPrevia)
        cpuPrevio = cpuMs
        paredPrevia = paredMs
        return r
    }
}
