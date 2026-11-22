package mx.papeleriapos.android.actualizar

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.papeleriapos.android.BuildConfig
import mx.papeleriapos.android.tema.TemaEscaner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** «Actualizar la app», dibujada: cada estado con lo suyo y los botones que llaman a lo que deben. */
@RunWith(AndroidJUnit4::class)
class PantallaActualizarTest {
    @get:Rule
    val compose = createComposeRule()

    private val nueva = Ejemplos.version(tamano = 15_404_054)
    private val tocado = mutableListOf<String>()
    private val acciones = AccionesActualizar(
        buscar = { tocado += "buscar" },
        descargarEInstalar = { tocado += "descargar" },
        cancelar = { tocado += "cancelar" },
        permitirInstalar = { tocado += "permitir" },
    )

    @After
    fun limpiar() = Actualizador.reiniciar()

    private fun dibujar(estado: EstadoActualizacion, puedeInstalar: Boolean = true) {
        compose.setContent { TemaEscaner { ContenidoActualizar(estado, "0.5.0 · a1b2c3d", puedeInstalar, acciones) } }
    }

    @Test
    fun `sin buscar - la version instalada, el boton y la nota de privacidad`() {
        dibujar(EstadoActualizacion.SinBuscar)
        compose.onNodeWithText("0.5.0 · a1b2c3d").assertIsDisplayed()
        compose.onNodeWithTag("nota-privacidad").assertIsDisplayed()
        compose.onNodeWithText(PRIVACIDAD).assertIsDisplayed()
        compose.onNodeWithTag("descargar-instalar").assertDoesNotExist()
        compose.onNodeWithTag("buscar-version").assertIsDisplayed().performClick()
        assertEquals(listOf("buscar"), tocado)
    }

    @Test
    fun `hay version - su numero, cuando se publico, cuanto pesa, que trae y el boton`() {
        dibujar(EstadoActualizacion.Hay(nueva))
        compose.onNodeWithText("Versión nueva: 0.5.1").assertIsDisplayed()
        compose.onNodeWithText("15.4 MB").assertIsDisplayed()
        compose.onNodeWithText("Publicada").assertIsDisplayed()
        compose.onNodeWithTag("notas-version").assertIsDisplayed()
        compose.onNodeWithText("• El teclado de la cantidad acepta medias piezas.").assertIsDisplayed()
        compose.onNodeWithTag("descargar-instalar").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("descargar"), tocado)
        compose.onNodeWithTag("permitir-instalar").assertDoesNotExist()
    }

    @Test
    fun `sin el permiso de Android lo explica y el boton abre su ajuste`() {
        dibujar(EstadoActualizacion.Hay(nueva), puedeInstalar = false)
        compose.onNodeWithTag("descargar-instalar").assertDoesNotExist()
        compose.onNodeWithTag("sin-permiso-instalar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("permitir-instalar").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("permitir"), tocado)
    }

    @Test
    fun `bajando, comprobando y esperando a Android dicen en que va`() {
        val estado = mutableStateOf<EstadoActualizacion>(EstadoActualizacion.Bajando(nueva, 7_702_027))
        compose.setContent { TemaEscaner { ContenidoActualizar(estado.value, "0.5.0 · a1b2c3d", true, acciones) } }
        compose.onNodeWithText("Bajando de GitHub: 7.7 MB de 15.4 MB").assertIsDisplayed()
        compose.onNodeWithTag("progreso-descarga").assertIsDisplayed()
        compose.onNodeWithTag("buscar-version").assertDoesNotExist()
        compose.onNodeWithTag("cancelar-descarga").assertIsDisplayed().performClick()
        assertEquals(listOf("cancelar"), tocado)

        estado.value = EstadoActualizacion.Comprobando(nueva)
        compose.onNodeWithTag("comprobando").assertIsDisplayed()
        compose.onNodeWithTag("cancelar-descarga").assertDoesNotExist()

        estado.value = EstadoActualizacion.EsperandoAAndroid(nueva)
        compose.onNodeWithTag("esperando-android").assertIsDisplayed()
        compose.onNodeWithTag("buscar-version").performScrollTo().assertIsDisplayed()

        estado.value = EstadoActualizacion.Buscando
        compose.onNodeWithTag("buscando-version").assertIsDisplayed()

        estado.value = EstadoActualizacion.AlDia(Version(0, 5, 0))
        compose.onNodeWithText("La app está al día: la última versión publicada es la 0.5.0.").assertIsDisplayed()
        compose.onNodeWithTag("buscar-version").assertIsDisplayed()
    }

    @Test
    fun `un error dice por que y deja volver a intentar o volver a buscar`() {
        val estado = mutableStateOf<EstadoActualizacion>(EstadoActualizacion.Error(Comprobacion.OTRA_FIRMA, nueva))
        compose.setContent { TemaEscaner { ContenidoActualizar(estado.value, "0.5.0 · a1b2c3d", true, acciones) } }
        compose.onNodeWithTag("actualizar-aviso").assertIsDisplayed()
        compose.onNodeWithText(Comprobacion.OTRA_FIRMA).assertIsDisplayed()
        compose.onNodeWithText("Volver a intentar").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("buscar-version").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("descargar", "buscar"), tocado)

        // Sin versión encontrada (no hubo Internet al buscar): solo queda volver a buscar.
        estado.value = EstadoActualizacion.Error(Actualizador.SIN_GITHUB)
        compose.onNodeWithText(Actualizador.SIN_GITHUB).assertIsDisplayed()
        compose.onNodeWithTag("descargar-instalar").assertDoesNotExist()
        compose.onNodeWithTag("buscar-version").assertIsDisplayed()
    }

    @Test
    fun `la pantalla de verdad se crea con el actualizador del proceso y no busca sola`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        // GitHub de mentira: si la pantalla buscara sola, se notaría aquí.
        var busquedas = 0
        val fuente = object : FuenteDeVersiones {
            override suspend fun ultima(): VersionPublicada {
                busquedas++
                return CompletableDeferred<VersionPublicada>().await()
            }

            override suspend fun bajar(apk: ApkPublicado, destino: File, alAvanzar: (Long) -> Unit) = error("No se baja nada")
        }
        val instalador = object : Instalador {
            override fun puedeInstalar() = true
            override fun instalar(archivo: File) = error("No se instala nada")
        }
        val propio = Actualizador(
            Version.de(BuildConfig.VERSION_NAME)!!, fuente, File(app.cacheDir, Actualizador.CARPETA), { _, _ -> null }, instalador,
            ambito = CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined,
        )
        Actualizador.reiniciar(propio)
        assertSame(propio, Actualizador.de(app))
        compose.setContent { TemaEscaner { PantallaActualizar() } }
        compose.onNodeWithTag("pantalla-actualizar").assertExists()
        compose.onNodeWithText("${BuildConfig.VERSION_NAME} · ${BuildConfig.COMMIT}").assertIsDisplayed()
        compose.waitForIdle()
        assertEquals(0, busquedas)
        assertEquals(EstadoActualizacion.SinBuscar, propio.estado.value)
        compose.onNodeWithTag("buscar-version").performClick()
        compose.onNodeWithTag("buscando-version").assertIsDisplayed()
        assertEquals(1, busquedas)
    }

    @Test
    fun `el actualizador del proceso se arma con la version instalada y sin tocar la red`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        Actualizador.reiniciar()
        val a = Actualizador.de(app)
        assertSame(a, Actualizador.de(app))
        assertEquals(Version.de(BuildConfig.VERSION_NAME), a.instalada)
        assertEquals(EstadoActualizacion.SinBuscar, a.estado.value)
    }
}
