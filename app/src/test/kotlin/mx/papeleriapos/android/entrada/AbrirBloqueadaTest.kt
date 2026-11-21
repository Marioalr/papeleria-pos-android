package mx.papeleriapos.android.entrada

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.asCoroutineDispatcher
import mx.papeleriapos.android.EscanerViewModel
import mx.papeleriapos.android.MainActivity
import mx.papeleriapos.android.caja.AlmacenAlta
import mx.papeleriapos.android.caja.Cifrador
import mx.papeleriapos.android.caja.ClienteCaja
import mx.papeleriapos.android.caja.Conexion
import mx.papeleriapos.android.copia.AlmacenCopia
import mx.papeleriapos.android.lista.ListasCompartidas
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.ui.ControladorDeMuestra
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executor

/**
 * Al abrir en frío con el celular dado de alta. De fábrica (0.4.4, dueño 2026-11-21) **no se bloquea**: abre directo a
 * trabajar, también al actualizar desde la 0.4.3 con el alta puesta. Con «Pedir huella al abrir» encendido, sale
 * bloqueada desde el primer cuadro, sin esperar a que se lea la copia (docs/revision-entrega-2.md, hallazgo 3).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AbrirBloqueadaTest {
    private val app = ApplicationProvider.getApplicationContext<Application>().also {
        shadowOf(it).grantPermissions(Manifest.permission.CAMERA)
    }
    private var actividad: ActivityController<MainActivity>? = null

    @Before
    fun preparar() {
        Entrada.reiniciar()
        // Un proceso nuevo: la conexión todavía no ha leído el alta ni la copia.
        Conexion.reiniciar()
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
    }

    @After
    fun cerrar() {
        actividad?.let { c ->
            c.get().finish()
            c.stop()
            c.destroy()
        }
        shadowOf(Looper.getMainLooper()).idle()
        app.getSharedPreferences(AlmacenAlta.ARCHIVO, Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences(Entrada.ARCHIVO, Context.MODE_PRIVATE).edit().clear().commit()
        Entrada.reiniciar()
        Conexion.reiniciar()
    }

    private fun abrir(): ActivityController<MainActivity> {
        val c = Robolectric.buildActivity(MainActivity::class.java, Intent.makeMainActivity(ComponentName(app, MainActivity::class.java)))
        val fabrica = viewModelFactory {
            initializer { EscanerViewModel(app, { _, config -> ControladorDeMuestra(EstadoEscaner(fase = Fase.PAUSADO), config) }, ListasCompartidas()) }
        }
        ViewModelProvider(c.get().viewModelStore, fabrica)[EscanerViewModel::class.java]
        actividad = c
        // Solo crear y pasar al frente, sin dejar correr el hilo principal: la lectura del alta y la copia no ha vuelto.
        c.create().start()
        return c
    }

    /** Un alta guardada (lo que mira es si hay llave, sin descifrarla) y una conexión que todavía la lee junto con la copia. */
    private fun conAltaLeyendose() {
        val prefs = app.getSharedPreferences(AlmacenAlta.ARCHIVO, Context.MODE_PRIVATE)
        prefs.edit().putString("llave", "cifrada").commit()
        // Su hilo no avanza en esta prueba.
        val leyendo = Executor { }.asCoroutineDispatcher()
        Conexion.reiniciar(
            Conexion(AlmacenAlta(prefs, Cifrador.Transparente), AlmacenCopia(File(app.cacheDir, "copia-bloqueada")), ClienteCaja(), "Prueba", hilo = leyendo),
        )
    }

    @Test
    fun `con alta y el ajuste de fabrica abre directo sin pedir la huella`() {
        // Como al actualizar desde la 0.4.3: el alta está y el archivo del ajuste no existe.
        conAltaLeyendose()
        abrir()
        assertFalse("De fábrica no se pide la huella al abrir", Entrada.bloqueada.value)
        assertFalse(Entrada.pedirAlAbrir.value)
    }

    @Test
    fun `con pedir huella encendido sale bloqueada antes de leer la copia`() {
        app.getSharedPreferences(Entrada.ARCHIVO, Context.MODE_PRIVATE).edit().putBoolean(Entrada.PEDIR_AL_ABRIR, true).commit()
        conAltaLeyendose()
        abrir()
        assertTrue("Sin bloquear mientras se lee la copia: se ve el ticket y las lecturas cuentan", Entrada.bloqueada.value)
    }

    @Test
    fun `sin alta no se bloquea`() {
        abrir()
        assertFalse(Entrada.bloqueada.value)
    }
}
