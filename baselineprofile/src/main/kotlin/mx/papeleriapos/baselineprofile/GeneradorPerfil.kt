package mx.papeleriapos.baselineprofile

import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El recorrido del Baseline Profile: abrir la app con la cámara ya permitida, esperar a que escanee (el testTag
 * «escaneando» aparece con el primer cuadro analizado) y seguir analizando unos segundos, para que entren al perfil el
 * arranque, CameraX, el localizador, el motor y la guía. `includeInStartupProfile`: también ordena el DEX (Startup
 * Profile), así lo del arranque queda junto en classes.dex.
 *
 * Con la cámara del emulador apuntando a un cartel (`-camera-back imagefile:…`, docs/pruebas.md) entran también la
 * lectura y la confirmación.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class GeneradorPerfil {
    @get:Rule
    val regla = BaselineProfileRule()

    @Test
    fun aperturaHastaEscanear() = regla.collect(
        packageName = PAQUETE,
        includeInStartupProfile = true,
    ) {
        // Sin el diálogo del permiso: el recorrido de todos los días.
        device.executeShellCommand("pm grant $PAQUETE android.permission.CAMERA")
        pressHome()
        startActivityAndWait()
        if (!esperarEscaneando()) Log.w(ETIQUETA, "No apareció «$ESCANEANDO»: el perfil cubre solo el arranque")
        // Más cuadros analizados: el camino caliente del análisis también se compila.
        Thread.sleep(MS_ESCANEANDO)
    }

    private fun MacrobenchmarkScope.esperarEscaneando(): Boolean =
        device.wait(Until.hasObject(By.res(ESCANEANDO)), MS_ESPERA) == true

    private companion object {
        const val PAQUETE = "mx.papeleriapos.android"
        /** El testTag de MainActivity (ETIQUETA_ESCANEANDO), expuesto como id con testTagsAsResourceId. */
        const val ESCANEANDO = "escaneando"
        const val MS_ESPERA = 15_000L
        const val MS_ESCANEANDO = 3_000L
        const val ETIQUETA = "GeneradorPerfil"
    }
}
