package mx.papeleriapos.android.fabrica

import android.content.Context
import android.util.Log
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner

/** El controlador de la cámara real (módulo escaner). */
private const val CONTROLADOR_CAMARA = "mx.papeleriapos.escaner.camara.ControladorCamara"

/**
 * Crea el controlador del escáner: es el único lugar de la app que conoce la cámara real. Se busca por nombre para que la
 * app compile aunque `ControladorCamara` todavía no exista (proguard-rules.pro lo conserva en release); cuando ya esté,
 * esto se puede cambiar por `ControladorCamara(context.applicationContext, config)`.
 *
 * Si no está o no se puede crear, devuelve uno de muestra en ERROR con el motivo, para que se note en pantalla en vez de
 * quedarse callado.
 */
fun crearControlador(context: Context, config: ConfigEscaner): ControladorEscaner {
    val app = context.applicationContext
    return try {
        Class.forName(CONTROLADOR_CAMARA)
            .getConstructor(Context::class.java, ConfigEscaner::class.java)
            .newInstance(app, config) as ControladorEscaner
    } catch (e: ReflectiveOperationException) {
        val causa = (e.cause ?: e).let { it::class.java.simpleName + (it.message?.let { m -> ": $m" } ?: "") }
        Log.e("Fabrica", "No se pudo crear $CONTROLADOR_CAMARA", e)
        ControladorDeMuestra(config, error = "No se pudo abrir el escáner ($causa).")
    } catch (e: ClassCastException) {
        Log.e("Fabrica", "$CONTROLADOR_CAMARA no es un ControladorEscaner", e)
        ControladorDeMuestra(config, error = "No se pudo abrir el escáner.")
    }
}
