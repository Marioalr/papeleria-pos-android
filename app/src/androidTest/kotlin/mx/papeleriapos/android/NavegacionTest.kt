package mx.papeleriapos.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Humo de la app completa con UiAutomator (sin Espresso, que en API 37 no arranca con la versión que trae ui-test):
 * abre la app, pasa por cada pestaña y regresa, y revisa que siga viva y en primer plano. Al salir de Escanear la
 * cámara se apaga (la marca [ETIQUETA_ESCANEANDO] se va) y al volver vuelve a escanear: se espera la marca, no basta con
 * que la barra de abajo siga ahí. No corre las pruebas de estrés ni el banco (tardan minutos); solo que sus pantallas
 * abren.
 */
@RunWith(AndroidJUnit4::class)
class NavegacionTest {
    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val dispositivo = UiDevice.getInstance(instrumentacion)
    private val contexto = instrumentacion.targetContext
    private val paquete = contexto.packageName
    private val hayCamara = contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    @Before
    fun abrir() {
        // Con el permiso dado, la pestaña Escanear abre la cámara en lugar de quedarse en el diálogo.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            instrumentacion.uiAutomation.grantRuntimePermission(paquete, Manifest.permission.CAMERA)
        } else {
            dispositivo.executeShellCommand("pm grant $paquete ${Manifest.permission.CAMERA}")
        }
        dispositivo.wakeUp()
        dispositivo.pressHome()
        val intento = contexto.packageManager.getLaunchIntentForPackage(paquete)
        assertNotNull("Sin actividad de inicio", intento)
        contexto.startActivity(intento!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue("La app no abrió", dispositivo.wait(Until.hasObject(By.pkg(paquete).depth(0)), ESPERA_MS))
    }

    @Test
    fun pasaPorTodasLasPestanasSinCaerse() {
        if (hayCamara) {
            assertTrue("La app no empezó a escanear", dispositivo.wait(Until.hasObject(By.res(ETIQUETA_ESCANEANDO)), ESPERA_ESCANEO_MS))
        }
        // Pestaña (etiqueta de la barra de abajo) → algo que solo está en esa pantalla.
        val recorrido = listOf(
            "Sesión" to "Sesión de prueba",
            "Banco" to "Banco de pruebas",
            "Estrés" to "Pruebas de estrés",
            "Ajustes" to "Simbologías",
            "Escanear" to null,
            "Estrés" to "Abrir y cerrar la cámara",
            "Sesión" to "Sesión de prueba",
            "Escanear" to null,
        )
        for ((pestana, senal) in recorrido) {
            val boton = dispositivo.wait(Until.findObject(By.text(pestana)), ESPERA_MS)
            assertNotNull("No está la pestaña «$pestana»", boton)
            boton.click()
            if (senal != null) {
                assertTrue("No se vio «$senal» en «$pestana»", dispositivo.wait(Until.hasObject(By.textContains(senal)), ESPERA_MS))
                // Fuera del escáner la cámara se apaga (no le roba batería ni CPU a las otras pantallas).
                if (hayCamara) {
                    assertTrue("La cámara siguió escaneando en «$pestana»", dispositivo.wait(Until.gone(By.res(ETIQUETA_ESCANEANDO)), ESPERA_MS))
                }
            } else {
                assertTrue(dispositivo.wait(Until.hasObject(By.text("Ajustes")), ESPERA_MS))
                // De vuelta en el escáner: tiene que volver a escanear (llega un cuadro analizado), no solo verse.
                if (hayCamara) {
                    assertTrue("Al volver a «$pestana» no volvió a escanear", dispositivo.wait(Until.hasObject(By.res(ETIQUETA_ESCANEANDO)), ESPERA_ESCANEO_MS))
                } else {
                    dispositivo.waitForIdle(2_000)
                }
            }
            assertEquals("La app dejó el primer plano en «$pestana»", paquete, dispositivo.currentPackageName)
        }
        // Atrás desde otra pestaña vuelve al escáner (no sale de la app).
        dispositivo.wait(Until.findObject(By.text("Banco")), ESPERA_MS)?.click()
        assertTrue(dispositivo.wait(Until.hasObject(By.text("Banco de pruebas")), ESPERA_MS))
        dispositivo.pressBack()
        assertTrue(dispositivo.wait(Until.gone(By.text("Banco de pruebas")), ESPERA_MS))
        assertEquals(paquete, dispositivo.currentPackageName)
    }

    private companion object {
        const val ESPERA_MS = 10_000L
        /** Abrir la cámara del emulador y analizar el primer cuadro puede tardar más que dibujar una pantalla. */
        const val ESPERA_ESCANEO_MS = 20_000L
    }
}
