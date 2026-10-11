package mx.papeleriapos.escaner.ui

import android.Manifest
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Indicacion
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Capturas de la pantalla en cada estado, para revisarlas a ojo. Como no hay cámara, el visor es una escena de muestra
 * con un EAN-13 donde dice la caja. Quedan en `Android/data/<paquete de prueba>/files/capturas/`; se bajan con
 * `adb pull` (instalar a mano: `connectedAndroidTest` desinstala y las borra).
 */
@RunWith(AndroidJUnit4::class)
class CapturasTest {
    @get:Rule
    val regla = createComposeRule()

    private val callado = ConfigEscaner(sonido = false, vibracion = false)

    @Before
    fun concederPermiso() {
        val instrumento = InstrumentationRegistry.getInstrumentation()
        instrumento.uiAutomation.grantRuntimePermission(instrumento.targetContext.packageName, Manifest.permission.CAMERA)
    }

    private fun mostrar(
        estado: EstadoEscaner,
        escena: Caja?,
        config: ConfigEscaner = callado,
        luz: Float = 1f,
        escalaLetra: Float = 1f,
        antes: (ControladorDeMuestra) -> Unit = {},
    ): ControladorDeMuestra {
        val muestra = ControladorDeMuestra(estado, config).also(antes)
        regla.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, escalaLetra)) {
                PantallaEscanerInterna(muestra, visor = { EscenaDeMuestra(escena, luz = luz) })
            }
        }
        regla.waitForIdle()
        return muestra
    }

    private fun capturar(nombre: String) {
        regla.waitForIdle()
        val imagen = regla.onRoot().captureToImage().asAndroidBitmap()
        val carpeta = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "capturas")
        carpeta.mkdirs()
        val archivo = File(carpeta, "$nombre.png")
        archivo.outputStream().use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("CapturasTest", "Captura: ${archivo.absolutePath} (${imagen.width}x${imagen.height})")
        assertTrue(archivo.length() > 0)
    }

    @Test
    fun c01_escaneando() {
        mostrar(EstadoEscaner(fase = Fase.ESCANEANDO), escena = null)
        capturar("01_escaneando")
    }

    @Test
    fun c02_viendoAlaIzquierda() {
        val caja = Caja(0.0f, 0.46f, 0.36f, 0.54f)
        mostrar(EstadoEscaner(fase = Fase.VIENDO, indicacion = Indicacion.IZQUIERDA, cajaCodigo = caja), caja)
        capturar("02_viendo_izquierda")
    }

    @Test
    fun c03_acercate() {
        val caja = Caja(0.42f, 0.485f, 0.58f, 0.515f)
        mostrar(EstadoEscaner(fase = Fase.VIENDO, indicacion = Indicacion.ACERCATE, cajaCodigo = caja), caja)
        capturar("03_acercate")
    }

    @Test
    fun c04_leido() {
        val caja = Caja(0.2f, 0.44f, 0.8f, 0.56f)
        mostrar(EstadoEscaner(fase = Fase.VIENDO, indicacion = Indicacion.LEYENDO, cajaCodigo = caja), caja) {
            it.emitirLectura("7509993456786", caja = caja)
            it.cambiar { e -> e.copy(fase = Fase.LEIDO) }
        }
        capturar("04_leido")
    }

    @Test
    fun c05_pausado() {
        mostrar(EstadoEscaner(fase = Fase.PAUSADO), escena = Caja(0.3f, 0.6f, 0.7f, 0.66f))
        capturar("05_pausado")
    }

    @Test
    fun c06_masLuzConLinterna() {
        mostrar(
            EstadoEscaner(fase = Fase.ESCANEANDO, indicacion = Indicacion.MAS_LUZ, hayLinterna = true, sugerirLinterna = true),
            escena = Caja(0.3f, 0.47f, 0.7f, 0.53f),
            luz = 0.3f,
        )
        capturar("06_mas_luz_linterna")
    }

    @Test
    fun c07_letraAlDoble() {
        val caja = Caja(0.25f, 0.45f, 0.75f, 0.55f)
        mostrar(
            EstadoEscaner(
                fase = Fase.VIENDO, indicacion = Indicacion.REFLEJO, cajaCodigo = caja, hayLinterna = true, zoom = 1.5f,
            ),
            caja,
            escalaLetra = 2f,
        )
        capturar("07_letra_al_doble")
    }

    @Test
    fun c08_leidoLetraAlDoble() {
        val caja = Caja(0.2f, 0.44f, 0.8f, 0.56f)
        mostrar(EstadoEscaner(fase = Fase.LEIDO, cajaCodigo = caja), caja, escalaLetra = 2f) {
            it.emitirLectura("7509993456786", caja = caja)
        }
        capturar("08_leido_letra_al_doble")
    }

    @Test
    fun c09_error() {
        mostrar(EstadoEscaner(fase = Fase.ERROR, mensajeError = "La cámara está ocupada por otra app"), escena = null)
        capturar("09_error")
    }

    @Test
    fun c10_sinPermiso() {
        val muestra = mostrar(EstadoEscaner(fase = Fase.ESCANEANDO), escena = null)
        muestra.cambiar { it.copy(fase = Fase.SIN_PERMISO) }
        capturar("10_sin_permiso")
    }

    @Test
    fun c11_abriendoTarda() {
        mostrar(EstadoEscaner(fase = Fase.ABRIENDO), escena = null)
        regla.mainClock.advanceTimeBy(1_200)
        capturar("11_abriendo")
    }

    @Test
    fun c12_diagnosticoYZoom() {
        val caja = Caja(0.3f, 0.46f, 0.72f, 0.55f)
        mostrar(
            EstadoEscaner(
                fase = Fase.VIENDO, indicacion = Indicacion.QUIETO, cajaCodigo = caja, zoom = 1.5f, hayLinterna = true,
                linterna = true, diagnostico = ControladorDeMuestra.DIAGNOSTICO_DE_MUESTRA,
            ),
            caja,
            config = callado.copy(mostrarDiagnostico = true),
        )
        capturar("12_diagnostico_zoom_linterna")
    }
}
