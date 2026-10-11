package mx.papeleriapos.android.sesion

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import mx.papeleriapos.android.BuildConfig

/** Mide batería, CPU del proceso y temperatura en cada intento (una vez por lectura, no por cuadro). */
class MedidorAndroid(context: Context) {
    private val bateria = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
    private val energia = context.getSystemService(Context.POWER_SERVICE) as PowerManager?

    fun medir(): Medicion = Medicion(
        bateriaPct = bateria?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: -1,
        cpuMsProceso = Process.getElapsedCpuTime(),
        estadoTermico = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) energia?.currentThermalStatus ?: -1 else -1,
    )

    companion object {
        fun equipo() = DatosEquipo(
            dispositivo = "${Build.MANUFACTURER} ${Build.MODEL}",
            sdk = Build.VERSION.SDK_INT,
            versionApp = BuildConfig.VERSION_NAME,
            commit = BuildConfig.COMMIT,
        )
    }
}
