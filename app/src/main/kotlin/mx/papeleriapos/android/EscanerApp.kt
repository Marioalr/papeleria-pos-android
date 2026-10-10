package mx.papeleriapos.android

import android.app.Application
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.lifecycle.ProcessCameraProvider

/**
 * Configura CameraX para abrir solo la cámara trasera (enumerar todas tarda en algunos equipos) y empieza a
 * inicializarla desde que arranca el proceso, para que la pantalla del escáner la encuentre lista (docs/escaner.md,
 * «Arranque»).
 */
class EscanerApp : Application(), CameraXConfig.Provider {
    override fun onCreate() {
        super.onCreate()
        // Solo calienta: el futuro lo recoge el controlador cuando abre la cámara.
        ProcessCameraProvider.getInstance(this)
    }

    override fun getCameraXConfig(): CameraXConfig =
        CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
            .setMinimumLoggingLevel(Log.ERROR)
            .build()
}
