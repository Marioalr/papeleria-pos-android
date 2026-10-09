package mx.papeleriapos.android

import android.Manifest
import android.content.Intent
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
 * abre la app, pasa por cada pestaña y regresa, y revisa que siga viva y en primer plano. No corre las pruebas de
 * estrés ni el banco (tardan minutos); solo que sus pantallas abren.
 */
@RunWith(AndroidJUnit4::class)
class NavegacionTest {
    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val dispositivo = UiDevice.getInstance(instrumentacion)
    private val contexto = instrumentacion.targetContext
    private val paquete = contexto.packageName

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
            } else {
                // El escáner: la barra de abajo sigue ahí y la app no se cayó al abrir la cámara.
                dispositivo.waitForIdle(2_000)
                assertTrue(dispositivo.wait(Until.hasObject(By.text("Ajustes")), ESPERA_MS))
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
    }
}
