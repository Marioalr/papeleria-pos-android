package mx.papeleriapos.android.entrada

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Cuándo se pide la huella: al abrir y al volver después de un rato en el fondo. */
@RunWith(AndroidJUnit4::class)
class EntradaTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun limpiar() = Entrada.reiniciar()

    @After
    fun borrarAjuste() {
        app.getSharedPreferences(Entrada.ARCHIVO, Context.MODE_PRIVATE).edit().clear().commit()
        Entrada.reiniciar()
    }

    @Test
    fun `pedir huella al abrir viene apagado y se guarda al cambiarlo`() {
        Entrada.leerAjuste(app)
        assertFalse("De fábrica, apagado", Entrada.pedirAlAbrir.value)

        Entrada.cambiarPedirAlAbrir(app, true)
        assertTrue(Entrada.pedirAlAbrir.value)
        assertFalse("Quien lo enciende ya está adentro: no se bloquea en ese momento", Entrada.bloqueada.value)
        assertFalse("Ni al volver enseguida", Entrada.hayQuePedir(ahora = 1_000))
        // Un proceso nuevo lo lee del disco.
        Entrada.reiniciar()
        Entrada.leerAjuste(app)
        assertTrue(Entrada.pedirAlAbrir.value)

        Entrada.cambiarPedirAlAbrir(app, false)
        Entrada.reiniciar()
        Entrada.leerAjuste(app)
        assertFalse(Entrada.pedirAlAbrir.value)
    }

    @Test
    fun `apagarlo con la app bloqueada deja entrar`() {
        Entrada.cambiarPedirAlAbrir(app, true)
        Entrada.reiniciar()
        app.getSharedPreferences(Entrada.ARCHIVO, Context.MODE_PRIVATE).edit().putBoolean(Entrada.PEDIR_AL_ABRIR, true).commit()
        org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.KeyguardManager::class.java)).setIsDeviceSecure(true)
        Entrada.bloquearAlAbrir(app, conAlta = true, ahora = 5_000)
        assertTrue(Entrada.bloqueada.value)
        Entrada.cambiarPedirAlAbrir(app, false)
        assertFalse(Entrada.bloqueada.value)
    }

    @Test
    fun `se pide al abrir y despues de diez minutos en el fondo`() {
        assertTrue("Nunca se ha entrado en este proceso", Entrada.hayQuePedir(ahora = 5_000))
        Entrada.desbloquear()
        assertFalse(Entrada.hayQuePedir(ahora = 6_000))
        Entrada.alFondo(ahora = 10_000)
        assertFalse("Un rato corto en el fondo no pide", Entrada.hayQuePedir(ahora = 10_000 + Entrada.MS_SIN_PEDIR - 1))
        assertTrue(Entrada.hayQuePedir(ahora = 10_000 + Entrada.MS_SIN_PEDIR))
        assertFalse(Entrada.bloqueada.value)
    }
}
