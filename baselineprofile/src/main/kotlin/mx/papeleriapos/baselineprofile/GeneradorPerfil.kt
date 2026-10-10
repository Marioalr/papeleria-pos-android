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
 * Los recorridos del perfil, con la cámara ya permitida:
 *
 * - [aperturaHastaEscanear]: abrir la app hasta que escanea (el testTag «escaneando» aparece con el primer cuadro
 *   analizado). Va también al Startup Profile, que ordena el DEX: lo del arranque queda junto en classes.dex.
 * - [escaneoYPestanas]: seguir analizando unos segundos e ir a otra pestaña y volver, para que entren CameraX, el
 *   localizador, el motor, la guía y la vuelta al escáner. Solo al Baseline Profile: con todo en el de arranque, el DEX
 *   se ordenaría como si el escaneo continuo fuera parte de abrir.
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
        abrirHastaEscanear()
    }

    @Test
    fun escaneoYPestanas() = regla.collect(
        packageName = PAQUETE,
        includeInStartupProfile = false,
    ) {
        abrirHastaEscanear()
        // Más cuadros analizados: el camino caliente del análisis también se compila.
        Thread.sleep(MS_ESCANEANDO)
        // A otra pestaña (la cámara se apaga) y de vuelta al escáner (vuelve a abrir).
        device.wait(Until.findObject(By.text("Sesión")), MS_ESPERA)?.click()
        device.wait(Until.hasObject(By.textContains("Sesión de prueba")), MS_ESPERA)
        device.wait(Until.findObject(By.text("Escanear")), MS_ESPERA)?.click()
        if (!esperarEscaneando()) Log.w(ETIQUETA, "Al volver no apareció «$ESCANEANDO»")
        Thread.sleep(MS_ESCANEANDO)
    }

    private fun MacrobenchmarkScope.abrirHastaEscanear() {
        // Sin el diálogo del permiso: el recorrido de todos los días.
        device.executeShellCommand("pm grant $PAQUETE android.permission.CAMERA")
        pressHome()
        startActivityAndWait()
        if (!esperarEscaneando()) Log.w(ETIQUETA, "No apareció «$ESCANEANDO»: el perfil cubre solo el arranque")
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
