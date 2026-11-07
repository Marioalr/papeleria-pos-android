package mx.papeleriapos.android

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.android.principal.ConsultaPrecio
import mx.papeleriapos.android.principal.Hoja
import mx.papeleriapos.android.principal.ModoApp
import mx.papeleriapos.android.principal.PreferenciasPiso
import mx.papeleriapos.android.rapido.intentAbrirApp
import mx.papeleriapos.escaner.Apertura
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.ui.ControladorDeMuestra
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * La app abierta con un intent (docs/propuesta-interfaz.md, «Del bolsillo al precio»): «Abrir la app» del escaneo rápido
 * trae el modo y el producto, y MainActivity lo aplica en `onCreate` (sin estado guardado) y en `onNewIntent` (la app ya
 * abierta: NEW_TASK | CLEAR_TOP | SINGLE_TOP), salvo si Recientes relanzó una tarea vacía con el intent de entonces. Con
 * la MainActivity de verdad y el controlador de muestra en lugar de la cámara. Los accesos del ícono se revisan después
 * del primer cuadro analizado, para no estorbarle a la cámara.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AbrirConUnProductoTest {
    private val app = ApplicationProvider.getApplicationContext<Application>().also {
        shadowOf(it).grantPermissions(Manifest.permission.CAMERA)
    }
    private lateinit var vm: EscanerViewModel
    private lateinit var muestra: ControladorDeMuestra
    private val listas = ListasCompartidas()
    private var actividad: ActivityController<MainActivity>? = null

    private fun icono() = Intent.makeMainActivity(ComponentName(app, MainActivity::class.java))

    /** Como lo dejó la app la última vez: en [modo]. */
    private fun guardarModo(modo: ModoApp) {
        val prefs = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
        PreferenciasPiso(prefs).guardarModo(modo)
        val escaner = PreferenciasEscaner(prefs)
        escaner.guardar(escaner.leer().copy(modo = modo.delNucleo))
    }

    /**
     * La app abierta con [intent], con su ViewModel (puesto antes de `onCreate`, donde lo pide `by viewModels()`). La
     * muestra, en pausa: escaneando, la retícula pulsa sin fin y el hilo principal de Robolectric nunca queda libre.
     */
    private fun abrir(intent: Intent, guardado: Bundle? = null): ActivityController<MainActivity> {
        val c = Robolectric.buildActivity(MainActivity::class.java, intent)
        val fabrica = viewModelFactory {
            initializer {
                EscanerViewModel(app, { _, config -> ControladorDeMuestra(EstadoEscaner(fase = Fase.PAUSADO), config).also { muestra = it } }, listas)
            }
        }
        vm = ViewModelProvider(c.get().viewModelStore, fabrica)[EscanerViewModel::class.java]
        actividad = c
        if (guardado == null) c.setup() else c.setup(guardado)
        // El primer cuadro es solo el visor; lo demás (y los productos) llega a los 400 ms si el visor no avisa antes.
        idle()
        ShadowSystemClock.advanceBy(Duration.ofMillis(500))
        hasta { vm.piso.catalogo.value != null }
        return c
    }

    @After
    fun cerrar() {
        actividad?.let { c ->
            val estado = c.get().lifecycle.currentState
            c.get().finish()
            if (estado.isAtLeast(Lifecycle.State.RESUMED)) c.pause()
            if (estado.isAtLeast(Lifecycle.State.STARTED)) c.stop()
            c.destroy()
        }
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Espera a [condicion], dejando correr el hilo principal (lo de otros hilos vuelve a él). */
    private fun hasta(condicion: () -> Boolean) {
        val limite = System.currentTimeMillis() + 5_000
        idle()
        while (!condicion()) {
            check(System.currentTimeMillis() < limite) { "No pasó a tiempo" }
            Thread.sleep(5)
            idle()
        }
    }

    private fun producto(clave: String) = vm.piso.catalogo.value!!.porClave(clave)!!

    @Test
    fun `abre en precio con el producto del escaneo rapido, sin tocar el ticket`() {
        guardarModo(ModoApp.CONTEO)
        listas.venta.value = listas.venta.value.sumar(LAPIZ, Simbologia.EAN_13, 1, clave = LAPIZ)
        listas.conteo.value = listas.conteo.value.sumar(LAPIZ, Simbologia.EAN_13, 1, clave = LAPIZ)
        val ticket = listas.venta.value
        val conteo = listas.conteo.value
        abrir(intentAbrirApp(app, CUADERNO))
        assertEquals(ModoApp.PRECIO, vm.piso.modo.value)
        assertEquals("El escáner, en el modo del núcleo de Precio", Modo.VENTA, muestra.config.value.modo)
        assertEquals(ConsultaPrecio.Encontrado(producto(CUADERNO)), vm.piso.precio.value)
        assertEquals("TalkBack lo dice", "Cuaderno profesional cuadro chico 100 hojas, \$46.00", vm.piso.anuncio.value?.texto)
        assertSame("El ticket no cambia", ticket, listas.venta.value)
        assertSame("Ni el conteo", conteo, listas.conteo.value)
        // No es una lectura: el escáner no leyó nada, así que no vibra ni suena (lo hace con cada una de las suyas).
        assertEquals(0, muestra.estado.value.lecturas)
    }

    @Test
    fun `con la app abierta el producto llega por onNewIntent y no lo anota la sesion de prueba`() {
        val c = abrir(icono())
        vm.iniciarSesion("prueba")
        vm.piso.abrir(Hoja.Buscar)
        idle()
        val intent = intentAbrirApp(app, NO_ESTA)
        c.newIntent(intent)
        idle()
        assertSame("Se queda con el intent nuevo (setIntent)", intent, c.get().intent)
        assertNull("Las hojas se cierran", vm.piso.hoja.value)
        assertEquals(ModoApp.PRECIO, vm.piso.modo.value)
        assertEquals("Lo que no está, con su código", ConsultaPrecio.NoEsta(NO_ESTA), vm.piso.precio.value)
        assertEquals("No está en el catálogo", vm.piso.anuncio.value?.texto)
        assertTrue("No es una lectura: la sesión no la anota", vm.sesion.value!!.intentos.isEmpty())
        // Otro producto con la app abierta, ahora sí en el catálogo.
        c.newIntent(intentAbrirApp(app, CAJA_LAPICES))
        idle()
        assertEquals(ConsultaPrecio.Encontrado(producto(CAJA_LAPICES)), vm.piso.precio.value)
    }

    @Test
    fun `relanzada desde recientes no vuelve a ensenar el producto de entonces`() {
        guardarModo(ModoApp.TICKET)
        // Después de reiniciar, Recientes relanza la tarea vacía con su intent: el de «Abrir la app» de hace días.
        abrir(intentAbrirApp(app, CUADERNO).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        assertEquals("Ni el modo", ModoApp.TICKET, vm.piso.modo.value)
        assertNull(vm.piso.precio.value)
    }

    @Test
    fun `restaurada con su estado no vuelve a aplicar el intent`() {
        guardarModo(ModoApp.CONTEO)
        // Android mató el proceso en segundo plano: el intent es el de cuando se abrió, y ya se aplicó entonces.
        abrir(intentAbrirApp(app, CUADERNO), guardado = Bundle())
        assertEquals(ModoApp.CONTEO, vm.piso.modo.value)
        assertNull(vm.piso.precio.value)
    }

    @Test
    fun `los accesos del icono de los modos tambien abren en su modo`() {
        guardarModo(ModoApp.PRECIO)
        abrir(icono().putExtra(MainActivity.EXTRA_MODO, MainActivity.MODO_CONTEO))
        assertEquals(ModoApp.CONTEO, vm.piso.modo.value)
        assertNull("Sin producto, nada en la tarjeta", vm.piso.precio.value)
    }

    @Test
    fun `los accesos del icono se publican despues del primer cuadro analizado`() {
        abrir(icono())
        idle()
        assertTrue("Antes, nada: no le estorba a la cámara", ShortcutManagerCompat.getDynamicShortcuts(app).isEmpty())
        muestra.cambiar { it.copy(apertura = Apertura(marcas = mapOf(PasoApertura.PRIMER_ANALISIS to 1L))) }
        // Este celular arma tickets y se puede contar (todavía no hay perfiles ni piloto): los dos, en su orden.
        hasta { ShortcutManagerCompat.getDynamicShortcuts(app).size == 2 }
        assertEquals(listOf("ticket", "contar"), ShortcutManagerCompat.getDynamicShortcuts(app).sortedBy { it.rank }.map { it.id })
    }

    @Test
    fun `listo para los accesos con el primer cuadro analizado o si la camara no va a abrir`() {
        val sinAnalizar = ControladorDeMuestra().estado.value
        assertEquals(false, listoParaLosAccesos(sinAnalizar))
        assertTrue(listoParaLosAccesos(sinAnalizar.copy(apertura = Apertura(marcas = mapOf(PasoApertura.PRIMER_ANALISIS to 1L)))))
        assertTrue("Sin permiso", listoParaLosAccesos(sinAnalizar.copy(fase = Fase.SIN_PERMISO)))
        assertTrue("Con error", listoParaLosAccesos(sinAnalizar.copy(fase = Fase.ERROR)))
        assertEquals("Abriendo", false, listoParaLosAccesos(sinAnalizar.copy(fase = Fase.ABRIENDO)))
    }

    private companion object {
        const val CUADERNO = "7509990100019"
        const val LAPIZ = "7509990100026"
        const val CAJA_LAPICES = "7509990100200"
        const val NO_ESTA = "7509990100194"
    }
}
