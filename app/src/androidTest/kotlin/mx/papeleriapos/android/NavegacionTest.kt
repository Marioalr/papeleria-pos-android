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
 * abre la app, pasa por Ajustes y cada herramienta de medición y regresa, y revisa que siga viva y en primer plano. Al
 * salir de la principal la cámara se apaga (la marca [ETIQUETA_ESCANEANDO] se va) y al volver vuelve a escanear: se
 * espera la marca, no basta con que los modos sigan ahí. No corre las pruebas de estrés ni el banco (tardan minutos);
 * solo que sus pantallas abren.
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
    fun pasaPorAjustesYLasHerramientasSinCaerse() {
        if (hayCamara) {
            assertTrue("La app no empezó a escanear", dispositivo.wait(Until.hasObject(By.res(ETIQUETA_ESCANEANDO)), ESPERA_ESCANEO_MS))
        }
        // Los tres modos están abajo, con el escáner encendido.
        for (modo in listOf("Precio", "Ticket", "Conteo")) {
            assertNotNull("No está el modo «$modo»", dispositivo.wait(Until.findObject(By.text(modo)), ESPERA_MS))
        }
        abrirAjustes()
        // Desde Ajustes, cada herramienta y de regreso: atrás sube a Ajustes y luego a la principal.
        val recorrido = listOf(
            "El escáner" to "Simbologías",
            "Sesión de prueba" to "Iniciar sesión",
            "Banco de pruebas" to "Exportar CSV",
            "Pruebas de estrés" to "Abrir y cerrar la cámara",
        )
        for ((fila, senal) in recorrido) {
            val boton = dispositivo.wait(Until.findObject(By.text(fila)), ESPERA_MS)
            assertNotNull("No está «$fila» en Ajustes", boton)
            boton.click()
            assertTrue("No se vio «$senal» en «$fila»", dispositivo.wait(Until.hasObject(By.textContains(senal)), ESPERA_MS))
            dispositivo.pressBack()
            assertTrue("Atrás no regresó a Ajustes desde «$fila»", dispositivo.wait(Until.hasObject(By.text("Herramientas de medición")), ESPERA_MS))
            assertEquals("La app dejó el primer plano en «$fila»", paquete, dispositivo.currentPackageName)
        }
        // Atrás desde Ajustes vuelve al escáner (no sale de la app), y vuelve a escanear.
        dispositivo.pressBack()
        assertTrue(dispositivo.wait(Until.hasObject(By.text("Precio")), ESPERA_MS))
        if (hayCamara) {
            assertTrue("Al volver no volvió a escanear", dispositivo.wait(Until.hasObject(By.res(ETIQUETA_ESCANEANDO)), ESPERA_ESCANEO_MS))
        }
        assertEquals(paquete, dispositivo.currentPackageName)
    }

    /** El botón de arriba a la izquierda: fuera del escáner la cámara se apaga (no le roba batería ni CPU a lo demás). */
    private fun abrirAjustes() {
        val ajustes = dispositivo.wait(Until.findObject(By.desc("Ajustes")), ESPERA_MS)
        assertNotNull("No está el botón de Ajustes", ajustes)
        ajustes.click()
        assertTrue("No abrió Ajustes", dispositivo.wait(Until.hasObject(By.text("Al escanear")), ESPERA_MS))
        if (hayCamara) {
            assertTrue("La cámara siguió escaneando en Ajustes", dispositivo.wait(Until.gone(By.res(ETIQUETA_ESCANEANDO)), ESPERA_MS))
        }
    }

    private companion object {
        const val ESPERA_MS = 10_000L
        /** Abrir la cámara del emulador y analizar el primer cuadro puede tardar más que dibujar una pantalla. */
        const val ESPERA_ESCANEO_MS = 20_000L
    }
}
