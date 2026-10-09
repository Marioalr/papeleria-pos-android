package mx.papeleriapos.escaner.camara

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build

/**
 * Lo que dice el enfoque automático en cada captura (`Camera2Interop`, aislado aquí porque está marcado como
 * experimental y se depreca en CameraX 1.7). Todo es opcional: `LENS_FOCUS_DISTANCE` solo está garantizada en equipos
 * FULL y en LIMITED o LEGACY puede no venir; entonces la guía se apoya en el módulo y el largo del código.
 *
 * Se escribe en el hilo de la cámara y se lee en el de análisis: campos volátiles, sin asignar.
 */
internal class SenalesCamara : CameraCaptureSession.CaptureCallback() {
    @Volatile var estadoAf = SIN_DATO
        private set

    /** `LENS_FOCUS_DISTANCE` en dioptrías (1/m); −1 = no se sabe. */
    @Volatile var distanciaEnfoque = -1f
        private set

    /** `LENS_INFO_MINIMUM_FOCUS_DISTANCE` (lo más cerca que enfoca) en dioptrías; −1 = no se sabe. */
    @Volatile var distanciaMinima = -1f

    /** La cámara física activa en un equipo con varias (API 29+), para saber si cambió al teleobjetivo. */
    @Volatile var camaraFisica = ""
        private set

    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
        try {
            estadoAf = result.get(CaptureResult.CONTROL_AF_STATE) ?: SIN_DATO
            distanciaEnfoque = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: -1f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fisica = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
                if (fisica != null && fisica != camaraFisica) camaraFisica = fisica
            }
        } catch (_: IllegalArgumentException) {
            // Algún equipo no conoce la llave: la señal queda como desconocida.
        }
    }

    val enfocando: Boolean
        get() = estadoAf == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN || estadoAf == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN

    val desenfocado: Boolean
        get() = estadoAf == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED ||
            estadoAf == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED

    /**
     * Qué tan cerca está enfocando respecto a lo más cerca que puede: ≈1 = en su límite (hay que alejarse). Ambas van en
     * dioptrías, así que en un equipo bien calibrado nunca pasa de 1; si pasa (el emulador da 10–20, y algunos equipos
     * LIMITED reportan valores sin calibrar), el dato no sirve y se toma como desconocido.
     */
    fun proporcionEnfoque(): Float {
        val d = distanciaEnfoque
        val m = distanciaMinima
        if (d <= 0f || m <= 0f) return -1f
        val p = d / m
        return if (p > PROPORCION_MAXIMA_CREIBLE) -1f else p
    }

    fun textoAf(): String = when (estadoAf) {
        CaptureResult.CONTROL_AF_STATE_INACTIVE -> "inactivo"
        CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> "buscando"
        CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> "enfocado"
        CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> "buscando (tocado)"
        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> "fijo, enfocado"
        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> "fijo, desenfocado"
        CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> "desenfocado"
        else -> "sin datos"
    }

    fun reiniciar() {
        estadoAf = SIN_DATO
        distanciaEnfoque = -1f
    }

    private companion object {
        const val SIN_DATO = -1
    }
}

private const val PROPORCION_MAXIMA_CREIBLE = 1.2f
