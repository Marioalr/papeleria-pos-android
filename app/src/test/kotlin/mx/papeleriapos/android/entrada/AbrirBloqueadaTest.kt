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
 * docs/revision-entrega-2.md, hallazgo 3: al abrir en frío con el celular dado de alta, la app sale bloqueada desde el
 * primer cuadro, sin esperar a que se lea la copia (antes se veía el ticket y las lecturas contaban mientras tanto).
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

    @Test
    fun `con alta guardada sale bloqueada antes de leer la copia`() {
        // Un alta guardada (lo que mira es si hay llave, sin descifrarla) y una conexión que todavía la está leyendo junto con
        // la copia: su hilo no avanza en esta prueba.
        val prefs = app.getSharedPreferences(AlmacenAlta.ARCHIVO, Context.MODE_PRIVATE)
        prefs.edit().putString("llave", "cifrada").commit()
        val leyendo = Executor { }.asCoroutineDispatcher()
        Conexion.reiniciar(
            Conexion(AlmacenAlta(prefs, Cifrador.Transparente), AlmacenCopia(File(app.cacheDir, "copia-bloqueada")), ClienteCaja(), "Prueba", hilo = leyendo),
        )
        abrir()
        assertTrue("Sin bloquear mientras se lee la copia: se ve el ticket y las lecturas cuentan", Entrada.bloqueada.value)
    }

    @Test
    fun `sin alta no se bloquea`() {
        abrir()
        assertFalse(Entrada.bloqueada.value)
    }
}
