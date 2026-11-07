package mx.papeleriapos.android.ui

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.principal.CHICO
import mx.papeleriapos.android.principal.ConsultaPrecio
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.android.tema.TemaEscaner
import mx.papeleriapos.escaner.ui.ControladorDeMuestra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * «Abrir la app» del escaneo rápido con la app en Ajustes o en una herramienta de medición (docs/propuesta-interfaz.md,
 * «Del bolsillo al precio»): regresa a la principal, en Precio y con ese producto. Una vez por cada uno: después se puede
 * volver a Ajustes sin que lo de antes la regrese.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = CHICO)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppEscanerDeFueraTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var visto: Destino? = null

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun abrirEn(desde: Destino): EscanerViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val vm = EscanerViewModel(app, { _, c -> ControladorDeMuestra(config = c) }, ListasCompartidas())
        vm.cambiarModoApp(ModoApp.TICKET)
        compose.setContent { TemaEscaner { AppEscaner(inicial = desde, vm = vm, alCambiarDestino = { visto = it }) } }
        idle()
        assertEquals(desde, visto)
        return vm
    }

    private fun regresaConElProducto(vm: EscanerViewModel) {
        vm.ensenarDeFuera(CUADERNO)
        // Los productos los pide la principal al dibujarse.
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.piso.catalogo.value != null
        }
        idle()
        assertEquals(Destino.PRINCIPAL, visto)
        assertEquals(ModoApp.PRECIO, vm.piso.modo.value)
        assertTrue(vm.piso.precio.value is ConsultaPrecio.Encontrado)
        compose.onNode(hasText("Cuaderno profesional cuadro chico 100 hojas") and hasAnyAncestor(hasTestTag("tarjeta-precio"))).assertExists()
    }

    @Test
    fun `desde ajustes regresa a la principal con el producto, una vez`() {
        val vm = abrirEn(Destino.AJUSTES)
        regresaConElProducto(vm)
        // Otra vez a Ajustes: lo de antes ya se atendió y no la regresa.
        compose.onNodeWithTag("boton-ajustes").performClick()
        idle()
        assertEquals(Destino.AJUSTES, visto)
    }

    @Test
    fun `desde una herramienta de medicion tambien`() {
        regresaConElProducto(abrirEn(Destino.SESION))
    }

    private companion object {
        const val CUADERNO = "7509990100019"
    }
}
