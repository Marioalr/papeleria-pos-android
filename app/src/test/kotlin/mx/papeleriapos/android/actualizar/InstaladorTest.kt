package mx.papeleriapos.android.actualizar

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

/** Entregarle el APK a Android y oír lo que contesta, con el instalador de paquetes de Robolectric. */
@RunWith(AndroidJUnit4::class)
class InstaladorTest {
    @get:Rule
    val disco = TemporaryFolder()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val nueva = Ejemplos.version(tamano = 12)

    @After
    fun limpiar() = Actualizador.reiniciar()

    /** El actualizador del proceso, ya esperando a Android (GitHub y el comprobador, de mentira; [instalador], el que se pruebe). */
    private fun esperandoAAndroid(instalador: Instalador): Actualizador {
        val fuente = object : FuenteDeVersiones {
            override suspend fun ultima() = nueva
            override suspend fun bajar(apk: ApkPublicado, destino: File, alAvanzar: (Long) -> Unit) {
                destino.parentFile?.mkdirs()
                destino.writeBytes(ByteArray(apk.tamano.toInt()) { 7 })
            }
        }
        val a = Actualizador(
            Version(0, 5, 0), fuente, File(disco.root, "actualizar"), { _, _ -> null }, instalador,
            ambito = CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined,
        )
        Actualizador.reiniciar(a)
        a.buscar()
        a.descargarEInstalar()
        assertEquals(EstadoActualizacion.EsperandoAAndroid(nueva), a.estado.value)
        return a
    }

    private val deMentira = object : Instalador {
        override fun puedeInstalar() = true
        override fun instalar(archivo: File) = Unit
    }

    private fun respuesta(estado: Int, mensaje: String? = null, cuadro: Intent? = null) =
        Intent(app, ReceptorDeInstalacion::class.java).setAction(InstaladorAndroid.ACCION)
            .putExtra(PackageInstaller.EXTRA_STATUS, estado)
            .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, mensaje)
            .putExtra(Intent.EXTRA_INTENT, cuadro)

    @Test
    fun `le entrega el APK a Android en una sesion de esta app`() {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        val a = esperandoAAndroid(InstaladorAndroid(app))
        val sesion = app.packageManager.packageInstaller.allSessions.single()
        assertEquals(app.packageName, sesion.appPackageName)
        // Android dice que no: llega al receptor de la app y de ahí al actualizador.
        shadowOf(app.packageManager.packageInstaller).setSessionFails(sesion.sessionId)
        shadowOf(Looper.getMainLooper()).idle()
        val estado = a.estado.value
        assertTrue("$estado", estado is EstadoActualizacion.Error && estado.version == nueva && estado.mensaje.startsWith("Android no la instaló"))
    }

    @Test
    fun `si Android pide confirmar, se abre su cuadro y se le sigue esperando`() {
        val a = esperandoAAndroid(deMentira)
        val cuadro = Intent("android.content.pm.action.CONFIRM_INSTALL").setPackage("com.android.packageinstaller")
        ReceptorDeInstalacion().onReceive(app, respuesta(PackageInstaller.STATUS_PENDING_USER_ACTION, cuadro = cuadro))
        val abierto = shadowOf(app).nextStartedActivity
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", abierto.action)
        assertTrue(abierto.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(EstadoActualizacion.EsperandoAAndroid(nueva), a.estado.value)
    }

    @Test
    fun `si Android pide confirmar sin su cuadro, lo dice en lugar de quedarse esperando`() {
        val a = esperandoAAndroid(deMentira)
        ReceptorDeInstalacion().onReceive(app, respuesta(PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertNull(shadowOf(app).nextStartedActivity)
        assertTrue(a.estado.value is EstadoActualizacion.Error)
    }

    @Test
    fun `cancelar en el cuadro de Android regresa a la version encontrada`() {
        val a = esperandoAAndroid(deMentira)
        ReceptorDeInstalacion().onReceive(app, respuesta(PackageInstaller.STATUS_FAILURE_ABORTED, "INSTALL_FAILED_ABORTED: User rejected permissions"))
        assertEquals(EstadoActualizacion.Hay(nueva, Actualizador.INSTALACION_CANCELADA), a.estado.value)
    }

    @Test
    fun `una falla de Android dice por que, y lo instalado deja la app al dia`() {
        val a = esperandoAAndroid(deMentira)
        ReceptorDeInstalacion().onReceive(app, respuesta(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, "INSTALL_FAILED_UPDATE_INCOMPATIBLE"))
        assertEquals(
            EstadoActualizacion.Error("Android no la instaló: no es compatible con la app instalada (otra firma, o una versión más vieja). (INSTALL_FAILED_UPDATE_INCOMPATIBLE)", nueva),
            a.estado.value,
        )
        val otro = esperandoAAndroid(deMentira)
        ReceptorDeInstalacion().onReceive(app, respuesta(PackageInstaller.STATUS_SUCCESS))
        assertEquals(EstadoActualizacion.AlDia(Version(0, 5, 1)), otro.estado.value)
    }

    @Test
    fun `un aviso con otra accion no cuenta`() {
        val a = esperandoAAndroid(deMentira)
        ReceptorDeInstalacion().onReceive(app, respuesta(PackageInstaller.STATUS_SUCCESS).setAction("otra.cosa"))
        assertEquals(EstadoActualizacion.EsperandoAAndroid(nueva), a.estado.value)
    }

    @Test
    fun `el permiso de instalar se lee de Android y su ajuste es el de esta app`() {
        val instalador = InstaladorAndroid(app)
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        assertFalse(instalador.puedeInstalar())
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        assertTrue(instalador.puedeInstalar())
        val ajuste = InstaladorAndroid.ajusteParaPermitir(app)!!
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, ajuste.action)
        assertEquals(Uri.parse("package:${app.packageName}"), ajuste.data)
        assertTrue(ajuste.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
