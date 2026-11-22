package mx.papeleriapos.android.actualizar

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Lo que se revisa del APK bajado antes de dárselo a Android: la misma app, la versión publicada, más nueva y la misma firma. */
@RunWith(AndroidJUnit4::class)
class ComprobacionTest {
    @get:Rule
    val disco = TemporaryFolder()

    private val paquete = "mx.papeleriapos.android"
    private val instalada = DatosDeApk(paquete, 500, setOf("aa11"))
    private val nueva = Version(0, 5, 1)

    @Test
    fun `la misma app, la version publicada y la misma firma - se instala`() {
        assertNull(Comprobacion.razonDeRechazo(instalada, DatosDeApk(paquete, 501, setOf("aa11")), nueva))
    }

    @Test
    fun `lo que no cuadra dice por que`() {
        fun razon(bajado: DatosDeApk?, esperada: Version = nueva) = Comprobacion.razonDeRechazo(instalada, bajado, esperada)
        assertEquals(Comprobacion.NO_ES_UN_APK, razon(null))
        assertEquals("El APK bajado es de otra app (mx.otra.app): se borró y no se instala.", razon(DatosDeApk("mx.otra.app", 501, setOf("aa11"))))
        // GitHub dijo 0.5.1 y el archivo trae otra: no es lo publicado.
        assertEquals("El APK bajado no es la versión 0.5.1 que GitHub publicó: se borró y no se instala.", razon(DatosDeApk(paquete, 502, setOf("aa11"))))
        // La misma o una más vieja que la instalada (Android tampoco la aceptaría).
        assertEquals("El APK bajado no es más nuevo que la app instalada: se borró y no se instala.", razon(DatosDeApk(paquete, 500, setOf("aa11")), Version(0, 5, 0)))
        assertEquals("El APK bajado no es más nuevo que la app instalada: se borró y no se instala.", razon(DatosDeApk(paquete, 404, setOf("aa11")), Version(0, 4, 4)))
        // Otra llave, una llave de más o ninguna que se pueda leer.
        assertEquals(Comprobacion.OTRA_FIRMA, razon(DatosDeApk(paquete, 501, setOf("bb22"))))
        assertEquals(Comprobacion.OTRA_FIRMA, razon(DatosDeApk(paquete, 501, setOf("aa11", "bb22"))))
        assertEquals(Comprobacion.SIN_FIRMA, razon(DatosDeApk(paquete, 501, emptySet())))
        assertEquals(Comprobacion.SIN_FIRMA, Comprobacion.razonDeRechazo(instalada.copy(firmas = emptySet()), DatosDeApk(paquete, 501, setOf("aa11")), nueva))
    }

    // ---- Con el PackageManager (el de Robolectric): la app instalada y el archivo bajado ----

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    private fun info(nombre: String, codigo: Int, firma: String?) = PackageInfo().apply {
        packageName = nombre
        versionCode = codigo
        signatures = firma?.let { arrayOf(Signature(it)) }
    }

    /** Deja instalada la 0.5.0 firmada con [firma] y el archivo bajado como [bajado]; devuelve lo que dice el comprobador. */
    private fun revisar(firma: String, bajado: PackageInfo?): String? {
        shadowOf(app.packageManager).installPackage(info(app.packageName, 500, firma))
        val archivo = disco.newFile("actualizacion.apk").apply { writeBytes(ByteArray(16)) }
        if (bajado != null) shadowOf(app.packageManager).setPackageArchiveInfo(archivo.absolutePath, bajado)
        return ComprobadorAndroid(app).revisar(archivo, nueva)
    }

    // Robolectric llena las firmas con la bandera de antes (GET_SIGNATURES): es el camino de respaldo del comprobador.
    @Test
    fun `el comprobador lee la app instalada y el archivo y los compara`() {
        assertNull(revisar("aa11", info(app.packageName, 501, "aa11")))
    }

    @Test
    fun `el comprobador rechaza otra firma`() {
        assertEquals(Comprobacion.OTRA_FIRMA, revisar("aa11", info(app.packageName, 501, "bb22")))
    }

    @Test
    fun `el comprobador rechaza otra app`() {
        assertTrue(revisar("aa11", info("mx.otra.app", 501, "aa11"))!!.startsWith("El APK bajado es de otra app (mx.otra.app)"))
    }

    @Test
    fun `el comprobador rechaza lo que Android no puede leer`() {
        assertEquals(Comprobacion.NO_ES_UN_APK, revisar("aa11", null))
    }
}
