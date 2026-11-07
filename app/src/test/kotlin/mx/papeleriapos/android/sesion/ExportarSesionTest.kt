package mx.papeleriapos.android.sesion

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.botonrapido.Lugar
import mx.papeleriapos.android.principal.ContadoresRapido
import mx.papeleriapos.android.principal.PreferenciasPiso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Lo que sale con la medición de la sesión de prueba. Los contadores del botón rápido salen solo con ella
 * (docs/propuesta-interfaz.md, «Cómo se aprende» y «Ajustes y herramientas de medición»): la app no manda estadísticas.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ExportarSesionTest {
    @Test
    fun `la medicion de la sesion trae los contadores del boton rapido`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
        PreferenciasPiso(prefs).guardarContadores(ContadoresRapido().alAbrirConToque().alElegir(Lugar.LADO, conElGesto = true))
        val sesion = SesionPrueba("Moto G24 Lupita", 1_762_400_000_000L)
        val uris = ExportarSesion.medicion(app, sesion)
        val nombre = CsvSesion.nombreArchivo("contadores-rapido", sesion.clave())
        assertTrue("Salen: ${uris.map { it.lastPathSegment }}", uris.any { it.lastPathSegment == nombre })
        val texto = File(File(app.cacheDir, "exports"), nombre).readText()
        assertEquals(ContadoresRapido().alAbrirConToque().alElegir(Lugar.LADO, conElGesto = true).csv(), texto)
        assertTrue(texto.contains("Abierto con un toque,1"))
        assertTrue(texto.contains("←: con el gesto,1"))
    }
}
