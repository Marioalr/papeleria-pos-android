package mx.papeleriapos.escaner.ui

import android.Manifest
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Indicacion
import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** La interfaz del escáner contra el controlador de muestra: cada fase e indicación, la confirmación, la pausa y más. */
@RunWith(AndroidJUnit4::class)
class PantallaEscanerTest {
    @get:Rule
    val regla = createComposeRule()

    private lateinit var muestra: ControladorDeMuestra

    /** Sin bip ni vibración: las pruebas corren en fila y no tienen por qué sonar. */
    private val callado = ConfigEscaner(sonido = false, vibracion = false)

    @Before
    fun concederPermiso() {
        val instrumento = InstrumentationRegistry.getInstrumentation()
        instrumento.uiAutomation.grantRuntimePermission(instrumento.targetContext.packageName, Manifest.permission.CAMERA)
    }

    private fun mostrar(estado: EstadoEscaner, config: ConfigEscaner = callado) {
        muestra = ControladorDeMuestra(estado, config)
        regla.setContent { PantallaEscaner(muestra) }
        regla.waitForIdle()
    }

    private fun cambiar(transformar: (EstadoEscaner) -> EstadoEscaner) {
        muestra.cambiar(transformar)
        regla.waitForIdle()
    }

    private fun fase(descripcion: String) =
        regla.onNodeWithTag(EtiquetasEscaner.FASE).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, descripcion))

    // Los textos de la especificación, escritos aquí a mano para que un cambio en strings.xml no pase sin verse.
    private val textos = mapOf(
        Indicacion.APUNTA to "Apunta al código de barras",
        Indicacion.LEYENDO to "Leyendo…",
        Indicacion.YA_LEIDO to "Ya leído: retíralo para leerlo otra vez",
        Indicacion.ACERCATE to "Acércate",
        Indicacion.ALEJATE to "Aléjate un poco",
        Indicacion.IZQUIERDA to "Mueve el celular a la izquierda",
        Indicacion.DERECHA to "Mueve el celular a la derecha",
        Indicacion.ARRIBA to "Mueve el celular hacia arriba",
        Indicacion.ABAJO to "Mueve el celular hacia abajo",
        Indicacion.QUIETO to "Mantén firme",
        Indicacion.ENFOCANDO to "Enfocando…",
        Indicacion.MAS_LUZ to "Poca luz: enciende la linterna",
        Indicacion.REFLEJO to "Hay reflejo: inclina un poco el celular",
        Indicacion.LEIDO to "Leído",
    )

    @Test
    fun abreLaCamaraConElTamanoDelVisorYAvisaDelPermiso() {
        mostrar(EstadoEscaner(fase = Fase.ABRIENDO))
        regla.waitUntil(2_000) { muestra.aperturas >= 1 }
        val apertura = muestra.ultimaApertura
        assertNotNull(apertura)
        apertura!!
        assertTrue("ancho ${apertura.ancho}", apertura.ancho > 0)
        assertTrue("alto ${apertura.alto}", apertura.alto > apertura.ancho) // El emulador está en vertical.
        assertTrue("rotación ${apertura.rotacionPantalla}", apertura.rotacionPantalla in 0..3)
        assertEquals(true, muestra.permiso)
    }

    @Test
    fun abriendoMuestraElTextoYElSpinnerSoloSiTardaMasDeUnSegundo() {
        regla.mainClock.autoAdvance = false
        muestra = ControladorDeMuestra(EstadoEscaner(fase = Fase.ABRIENDO), callado)
        regla.setContent { PantallaEscaner(muestra) }
        regla.mainClock.advanceTimeByFrame()
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertTextEquals("Abriendo cámara…")
        fase("Abriendo cámara")
        regla.onNodeWithTag(EtiquetasEscaner.CARGANDO, useUnmergedTree = true).assertDoesNotExist()
        regla.mainClock.advanceTimeBy(1_100)
        regla.onNodeWithTag(EtiquetasEscaner.CARGANDO, useUnmergedTree = true).assertExists()
    }

    @Test
    fun escaneandoDiceQueApunteDesdeElPrimerCuadro() {
        mostrar(EstadoEscaner(fase = Fase.ABRIENDO))
        regla.mainClock.autoAdvance = false
        muestra.cambiar { it.copy(fase = Fase.ESCANEANDO, indicacion = Indicacion.APUNTA) }
        regla.mainClock.advanceTimeByFrame()
        regla.mainClock.advanceTimeByFrame()
        fase("Escaneando")
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertTextEquals("Apunta al código de barras")
        regla.onNodeWithTag(EtiquetasEscaner.LEIDO).assertDoesNotExist()
        regla.onNodeWithTag(EtiquetasEscaner.PAUSA).assertDoesNotExist()
    }

    @Test
    fun cadaIndicacionMuestraSuTexto() {
        mostrar(EstadoEscaner(fase = Fase.VIENDO, cajaCodigo = Caja(0.3f, 0.45f, 0.7f, 0.55f)))
        fase("Código a la vista")
        for (indicacion in Indicacion.entries) {
            cambiar { it.copy(indicacion = indicacion) }
            regla.onNodeWithTag(EtiquetasEscaner.INDICACION)
                .assertIsDisplayed()
                .assertTextEquals(textos.getValue(indicacion))
        }
    }

    @Test
    fun cadaFaseMuestraLoSuyo() {
        mostrar(EstadoEscaner(fase = Fase.ESCANEANDO))

        cambiar { it.copy(fase = Fase.VIENDO, indicacion = Indicacion.LEYENDO) }
        fase("Código a la vista")
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertTextEquals("Leyendo…")

        cambiar { it.copy(fase = Fase.LEIDO) }
        fase("Código leído")
        regla.onNodeWithTag(EtiquetasEscaner.LEIDO).assertIsDisplayed()
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertDoesNotExist()

        cambiar { it.copy(fase = Fase.PAUSADO) }
        fase("En pausa")
        regla.onNodeWithTag(EtiquetasEscaner.PAUSA).assertIsDisplayed().assertHasClickAction()
        regla.onNodeWithText("Toca para seguir escaneando").assertIsDisplayed()
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertDoesNotExist()

        cambiar { it.copy(fase = Fase.ERROR, mensajeError = "La cámara está ocupada") }
        fase("Error de la cámara")
        regla.onNodeWithTag(EtiquetasEscaner.ERROR).assertIsDisplayed()
        regla.onNodeWithText("La cámara está ocupada").assertIsDisplayed()
        regla.onNodeWithText("Reintentar").assertIsDisplayed().assertHasClickAction()
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertDoesNotExist()

        cambiar { it.copy(fase = Fase.ERROR, mensajeError = null) }
        regla.onNodeWithText("No se pudo usar la cámara").assertIsDisplayed()

        cambiar { it.copy(fase = Fase.SIN_PERMISO) }
        regla.onNodeWithTag(EtiquetasEscaner.SIN_PERMISO).assertIsDisplayed()
        regla.onNodeWithText("Falta el permiso de la cámara").assertIsDisplayed()
        regla.onNodeWithText("Abrir ajustes").assertHasClickAction()
        regla.onNodeWithTag(EtiquetasEscaner.FASE).assertDoesNotExist()
    }

    @Test
    fun sinPermisoDarPermisoVuelveAPedirloYAbre() {
        mostrar(EstadoEscaner(fase = Fase.ESCANEANDO))
        cambiar { it.copy(fase = Fase.SIN_PERMISO) }
        // El permiso ya está concedido: el sistema contesta que sí sin mostrar ventana.
        regla.onNodeWithText("Dar permiso").performClick()
        regla.waitUntil(3_000) { muestra.estado.value.fase == Fase.ABRIENDO }
        regla.waitForIdle()
        assertEquals(true, muestra.permiso)
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertTextEquals("Abriendo cámara…")
    }

    @Test
    fun alSubirLecturasSeConfirmaEnElMismoCuadroYSeQuitaTrasUnos600ms() {
        mostrar(EstadoEscaner(fase = Fase.VIENDO, indicacion = Indicacion.LEYENDO))
        regla.mainClock.autoAdvance = false
        muestra.emitirLectura("036000291452", Simbologia.UPC_A, normalizado = "0036000291452")
        regla.mainClock.advanceTimeByFrame()
        regla.mainClock.advanceTimeByFrame()
        regla.onNodeWithTag(EtiquetasEscaner.LEIDO).assertIsDisplayed()
            .assertTextContains("0036000291452")
            .assertTextContains("UPC-A · Leído")
        // Leído borra cualquier pista.
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertDoesNotExist()
        regla.mainClock.advanceTimeBy(300)
        regla.onNodeWithTag(EtiquetasEscaner.LEIDO).assertExists()
        regla.mainClock.advanceTimeBy(400)
        regla.onNodeWithTag(EtiquetasEscaner.LEIDO).assertDoesNotExist()
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertTextEquals("Leyendo…")
    }

    @Test
    fun otraLecturaDuranteLaConfirmacionLaAlarga() {
        mostrar(EstadoEscaner(fase = Fase.ESCANEANDO))
        regla.mainClock.autoAdvance = false
        muestra.emitirLectura("7501234567893")
        regla.mainClock.advanceTimeBy(400)
        muestra.emitirLectura("96385074", Simbologia.EAN_8)
        regla.mainClock.advanceTimeByFrame()
        regla.mainClock.advanceTimeByFrame()
        regla.onNodeWithTag(EtiquetasEscaner.LEIDO).assertTextContains("96385074")
        regla.mainClock.advanceTimeBy(350)
        regla.onNodeWithTag(EtiquetasEscaner.LEIDO).assertExists()
        regla.mainClock.advanceTimeBy(400)
        regla.onNodeWithTag(EtiquetasEscaner.LEIDO).assertDoesNotExist()
    }

    @Test
    fun lasLecturasDeAntesDeEntrarNoSeConfirman() {
        mostrar(EstadoEscaner(fase = Fase.ESCANEANDO, lecturas = 7))
        regla.onNodeWithTag(EtiquetasEscaner.LEIDO).assertDoesNotExist()
    }

    @Test
    fun enPausaTocarReanuda() {
        mostrar(EstadoEscaner(fase = Fase.PAUSADO))
        regla.onNodeWithTag(EtiquetasEscaner.PAUSA).performClick()
        regla.waitForIdle()
        assertEquals(1, muestra.reanudaciones)
        regla.onNodeWithTag(EtiquetasEscaner.PAUSA).assertDoesNotExist()
        fase("Escaneando")
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertTextEquals("Apunta al código de barras")
    }

    @Test
    fun tocarElVisorEnfocaEnCoordenadasNormalizadas() {
        mostrar(EstadoEscaner(fase = Fase.ESCANEANDO))
        regla.onNodeWithTag(EtiquetasEscaner.FASE).performTouchInput { click(Offset(width * 0.25f, height * 0.75f)) }
        regla.waitForIdle()
        val enfoque = muestra.ultimoEnfoque
        assertNotNull(enfoque)
        val (x, y) = enfoque!!
        assertEquals(0.25f, x, 0.01f)
        assertEquals(0.75f, y, 0.01f)
        assertEquals(0, muestra.reanudaciones)
    }

    @Test
    fun errorReintentarVuelveAAbrirLaCamara() {
        mostrar(EstadoEscaner(fase = Fase.ESCANEANDO))
        regla.waitUntil(2_000) { muestra.aperturas >= 1 }
        val antes = muestra.aperturas
        cambiar { it.copy(fase = Fase.ERROR, mensajeError = "No se encontró la cámara trasera") }
        regla.onNodeWithTag(EtiquetasEscaner.LINTERNA).assertDoesNotExist()
        regla.onNodeWithText("Reintentar").performClick()
        regla.waitForIdle()
        assertEquals(antes + 1, muestra.aperturas)
        fase("Abriendo cámara")
    }

    @Test
    fun linternaSoloSiHayYCambiaAlTocar() {
        mostrar(EstadoEscaner(fase = Fase.ESCANEANDO, hayLinterna = false))
        regla.onNodeWithTag(EtiquetasEscaner.LINTERNA).assertDoesNotExist()

        cambiar { it.copy(hayLinterna = true) }
        regla.onNodeWithTag(EtiquetasEscaner.LINTERNA).assertIsDisplayed().assertIsOff()
        regla.onNodeWithTag(EtiquetasEscaner.LINTERNA).performClick()
        regla.waitForIdle()
        assertEquals(1, muestra.cambiosDeLinterna)
        regla.onNodeWithTag(EtiquetasEscaner.LINTERNA).assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Encendida"))
    }

    @Test
    fun conPocaLuzSeOfreceLaLinterna() {
        mostrar(
            EstadoEscaner(fase = Fase.ESCANEANDO, indicacion = Indicacion.MAS_LUZ, hayLinterna = true, sugerirLinterna = true),
        )
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertTextEquals("Poca luz: enciende la linterna")
        regla.onNodeWithTag(EtiquetasEscaner.LINTERNA).assertIsDisplayed().assertIsOff()
    }

    @Test
    fun etiquetaDeZoomSoloSiPasaDeUno() {
        mostrar(EstadoEscaner(fase = Fase.ESCANEANDO, zoom = 1f))
        regla.onNodeWithTag(EtiquetasEscaner.ZOOM).assertDoesNotExist()
        cambiar { it.copy(zoom = 1.5f) }
        regla.onNodeWithTag(EtiquetasEscaner.ZOOM).assertIsDisplayed().assertTextEquals("1.5×")
        cambiar { it.copy(zoom = 2f) }
        regla.onNodeWithTag(EtiquetasEscaner.ZOOM).assertTextEquals("2×")
    }

    @Test
    fun diagnosticoSoloSiSePide() {
        mostrar(EstadoEscaner(fase = Fase.ESCANEANDO, diagnostico = ControladorDeMuestra.DIAGNOSTICO_DE_MUESTRA))
        regla.onNodeWithTag(EtiquetasEscaner.DIAGNOSTICO).assertDoesNotExist()
        muestra.configurar(callado.copy(mostrarDiagnostico = true))
        regla.waitForIdle()
        regla.onNodeWithTag(EtiquetasEscaner.DIAGNOSTICO).assertIsDisplayed()
        regla.onNodeWithText("ML Kit · 30 fps").assertIsDisplayed()
    }

    @Test
    fun mantieneLaPantallaEncendidaSoloEscaneandoYNoCierraAlSalir() {
        muestra = ControladorDeMuestra(EstadoEscaner(fase = Fase.ESCANEANDO), callado)
        var vista: View? = null
        var visible by mutableStateOf(true)
        regla.setContent {
            vista = LocalView.current
            if (visible) PantallaEscaner(muestra)
        }
        regla.waitForIdle()
        assertTrue(vista!!.keepScreenOn)
        // En pausa (o en error) la pantalla se puede apagar: no gasta batería ni se queda prendida sobre el bloqueo.
        muestra.cambiar { it.copy(fase = Fase.PAUSADO) }
        regla.waitForIdle()
        assertFalse(vista!!.keepScreenOn)
        muestra.cambiar { it.copy(fase = Fase.ESCANEANDO) }
        regla.waitForIdle()
        assertTrue(vista!!.keepScreenOn)
        // Al dejar de mostrarse no cierra el controlador: lo cierra quien lo creó (el ViewModel), así vuelve a abrir.
        visible = false
        regla.waitForIdle()
        assertFalse(vista!!.keepScreenOn)
        assertEquals(0, muestra.cierres)
    }

    @Test
    fun conLetraAlDobleTodoSigueVisible() {
        muestra = ControladorDeMuestra(
            EstadoEscaner(fase = Fase.VIENDO, indicacion = Indicacion.REFLEJO, hayLinterna = true, zoom = 1.5f),
            callado,
        )
        regla.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) { PantallaEscaner(muestra) }
        }
        regla.waitForIdle()
        regla.onNodeWithTag(EtiquetasEscaner.INDICACION).assertIsDisplayed()
            .assertTextEquals("Hay reflejo: inclina un poco el celular")
        regla.onNodeWithTag(EtiquetasEscaner.LINTERNA).assertIsDisplayed()
        regla.onNodeWithTag(EtiquetasEscaner.ZOOM).assertIsDisplayed()
        muestra.emitirLectura("7501234567893")
        muestra.cambiar { it.copy(fase = Fase.LEIDO) }
        regla.waitForIdle()
        regla.onNodeWithTag(EtiquetasEscaner.LEIDO).assertIsDisplayed().assertTextContains("7501234567893")
    }
}
