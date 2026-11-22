package mx.papeleriapos.android.actualizar

import android.content.pm.PackageInstaller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.UnknownHostException

/**
 * Los pasos de la actualización, uno por uno: GitHub, el comprobador y Android son de mentira y cada uno espera a que la
 * prueba lo deje seguir, así se ve el estado en cada paso. Todo corre en el hilo de la prueba.
 */
class ActualizadorTest {
    @get:Rule
    val disco = TemporaryFolder()

    /** GitHub de mentira: cada llamada espera su compuerta. */
    private class Fuente : FuenteDeVersiones {
        var ultima = CompletableDeferred<VersionPublicada>()
        var descarga = CompletableDeferred<Unit>()
        var busquedas = 0
        var descargas = 0
        var avanzar: ((Long) -> Unit)? = null
        var destino: File? = null

        override suspend fun ultima(): VersionPublicada {
            busquedas++
            return ultima.await()
        }

        override suspend fun bajar(apk: ApkPublicado, destino: File, alAvanzar: (Long) -> Unit) {
            descargas++
            this.destino = destino
            avanzar = alAvanzar
            destino.parentFile?.mkdirs()
            destino.writeBytes(ByteArray(apk.tamano.toInt()))
            try {
                descarga.await()
            } catch (e: Throwable) {
                destino.delete()
                throw e
            }
        }
    }

    private class Android(var permitido: Boolean = true, var falla: IOException? = null) : Instalador {
        val instalados = mutableListOf<ByteArray>()
        var estadoAlInstalar: EstadoActualizacion? = null
        lateinit var actualizador: Actualizador

        override fun puedeInstalar() = permitido

        override fun instalar(archivo: File) {
            estadoAlInstalar = actualizador.estado.value
            falla?.let { throw it }
            instalados += archivo.readBytes()
        }
    }

    private val fuente = Fuente()
    private val android = Android()
    private var rechazo: String? = null
    private var estadoAlComprobar: EstadoActualizacion? = null
    private var comprobados = 0
    private val carpeta by lazy { File(disco.root, "actualizar") }

    private fun actualizador(instalada: Version = Version(0, 5, 0)): Actualizador {
        lateinit var a: Actualizador
        a = Actualizador(
            instalada = instalada,
            fuente = fuente,
            carpeta = carpeta,
            comprobador = { archivo, esperada ->
                comprobados++
                estadoAlComprobar = a.estado.value
                assertTrue("el archivo sigue ahí al comprobar", archivo.exists())
                assertEquals(Version(0, 5, 1), esperada)
                rechazo
            },
            instalador = android,
            ambito = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )
        android.actualizador = a
        return a
    }

    private val nueva = Ejemplos.version(tamano = 12)

    // ---- Buscar ----

    @Test
    fun `no busca sola - sin tocar nada no se le pide nada a GitHub`() {
        val a = actualizador()
        assertEquals(EstadoActualizacion.SinBuscar, a.estado.value)
        assertEquals(Version(0, 5, 0), a.instalada)
        assertEquals(0, fuente.busquedas)
        // Descargar sin haber buscado no hace nada.
        a.descargarEInstalar()
        a.cancelar()
        a.alContestarAndroid(RespuestaDeAndroid.INSTALADA)
        assertEquals(EstadoActualizacion.SinBuscar, a.estado.value)
        assertEquals(0, fuente.descargas)
    }

    @Test
    fun `buscar - buscando y luego hay una version nueva`() {
        val a = actualizador()
        a.buscar()
        assertEquals(EstadoActualizacion.Buscando, a.estado.value)
        // Tocar otra vez mientras busca no pregunta dos veces.
        a.buscar()
        assertEquals(1, fuente.busquedas)
        fuente.ultima.complete(nueva)
        assertEquals(EstadoActualizacion.Hay(nueva), a.estado.value)
    }

    @Test
    fun `si lo publicado es lo instalado o mas viejo, la app esta al dia`() {
        val igual = actualizador(instalada = Version(0, 5, 1))
        igual.buscar()
        fuente.ultima.complete(nueva)
        assertEquals(EstadoActualizacion.AlDia(Version(0, 5, 1)), igual.estado.value)

        fuente.ultima = CompletableDeferred()
        val adelantada = actualizador(instalada = Version(0, 6, 0))
        adelantada.buscar()
        fuente.ultima.complete(nueva)
        assertEquals(EstadoActualizacion.AlDia(Version(0, 5, 1)), adelantada.estado.value)
        // Al día no hay qué descargar.
        adelantada.descargarEInstalar()
        assertEquals(0, fuente.descargas)
    }

    @Test
    fun `sin Internet o con un error de GitHub lo dice y se puede volver a buscar`() {
        val a = actualizador()
        a.buscar()
        fuente.ultima.completeExceptionally(UnknownHostException("api.github.com"))
        assertEquals(EstadoActualizacion.Error(Actualizador.SIN_GITHUB), a.estado.value)

        fuente.ultima = CompletableDeferred()
        a.buscar()
        assertEquals(EstadoActualizacion.Buscando, a.estado.value)
        fuente.ultima.completeExceptionally(ErrorDeActualizacion("Todavía no hay ninguna versión publicada en GitHub."))
        assertEquals(EstadoActualizacion.Error("Todavía no hay ninguna versión publicada en GitHub."), a.estado.value)
        // Sin versión encontrada no hay qué volver a intentar descargar.
        a.descargarEInstalar()
        assertEquals(0, fuente.descargas)

        fuente.ultima = CompletableDeferred()
        a.buscar()
        fuente.ultima.complete(nueva)
        assertEquals(EstadoActualizacion.Hay(nueva), a.estado.value)
    }

    // ---- Descargar e instalar ----

    private fun conVersion(): Actualizador = actualizador().also {
        it.buscar()
        fuente.ultima.complete(nueva)
    }

    @Test
    fun `los pasos - bajando con su avance, comprobando, esperando a Android`() {
        val a = conVersion()
        a.descargarEInstalar()
        assertEquals(EstadoActualizacion.Bajando(nueva, 0), a.estado.value)
        // A medio bajar, ni otra descarga ni otra búsqueda.
        a.descargarEInstalar()
        a.buscar()
        assertEquals(1, fuente.descargas)
        assertEquals(1, fuente.busquedas)

        fuente.avanzar!!(6)
        assertEquals(EstadoActualizacion.Bajando(nueva, 6), a.estado.value)
        assertEquals(0.5f, (a.estado.value as EstadoActualizacion.Bajando).avance, 0.001f)
        assertEquals(File(carpeta, "actualizacion.apk"), fuente.destino)

        fuente.descarga.complete(Unit)
        assertEquals(EstadoActualizacion.Comprobando(nueva), estadoAlComprobar)
        // Al entregárselo a Android ya se le espera: puede contestar antes de que la entrega regrese.
        assertEquals(EstadoActualizacion.EsperandoAAndroid(nueva), android.estadoAlInstalar)
        assertEquals(EstadoActualizacion.EsperandoAAndroid(nueva), a.estado.value)
        assertEquals(12, android.instalados.single().size)
        // Android guarda su copia: en la carpeta de la app no queda nada.
        assertEquals(emptyList<String>(), carpeta.list().orEmpty().toList())
        // Un aviso de avance que llega tarde no regresa a «bajando».
        fuente.avanzar!!(12)
        assertEquals(EstadoActualizacion.EsperandoAAndroid(nueva), a.estado.value)
    }

    @Test
    fun `lo que contesta Android - instalada, cancelada o con su razon`() {
        val a = conVersion()
        fuente.descarga.complete(Unit)
        a.descargarEInstalar()
        a.alContestarAndroid(RespuestaDeAndroid.CANCELADA)
        assertEquals(EstadoActualizacion.Hay(nueva, Actualizador.INSTALACION_CANCELADA), a.estado.value)

        a.descargarEInstalar()
        assertEquals(EstadoActualizacion.EsperandoAAndroid(nueva), a.estado.value)
        val razon = ReceptorDeInstalacion.razon(PackageInstaller.STATUS_FAILURE_STORAGE, "INSTALL_FAILED_INSUFFICIENT_STORAGE")
        a.alContestarAndroid(RespuestaDeAndroid.FALLO, razon)
        assertEquals(EstadoActualizacion.Error("Android no la instaló: no hay espacio en el celular. (INSTALL_FAILED_INSUFFICIENT_STORAGE)", nueva), a.estado.value)

        // Después de un error con la versión a la vista, «Volver a intentar» la baja otra vez.
        a.descargarEInstalar()
        assertEquals(3, fuente.descargas)
        a.alContestarAndroid(RespuestaDeAndroid.INSTALADA)
        assertEquals(EstadoActualizacion.AlDia(Version(0, 5, 1)), a.estado.value)
        // Una respuesta de Android que nadie espera no cambia nada.
        a.alContestarAndroid(RespuestaDeAndroid.FALLO, "tarde")
        assertEquals(EstadoActualizacion.AlDia(Version(0, 5, 1)), a.estado.value)
    }

    @Test
    fun `las razones de Android, en español`() {
        assertEquals("Android no la instaló.", ReceptorDeInstalacion.razon(PackageInstaller.STATUS_FAILURE, null))
        assertEquals(
            "Android no la instaló: no es compatible con la app instalada (otra firma, o una versión más vieja). (INSTALL_FAILED_UPDATE_INCOMPATIBLE)",
            ReceptorDeInstalacion.razon(PackageInstaller.STATUS_FAILURE_CONFLICT, "INSTALL_FAILED_UPDATE_INCOMPATIBLE"),
        )
        assertEquals("Android bloqueó la instalación en este celular.", ReceptorDeInstalacion.razon(PackageInstaller.STATUS_FAILURE_BLOCKED, " "))
        assertEquals("Android no la instaló: el APK no es válido.", ReceptorDeInstalacion.razon(PackageInstaller.STATUS_FAILURE_INVALID, null))
    }

    @Test
    fun `si la comprobacion no cuadra, se borra, no llega a Android y lo dice`() {
        val a = conVersion()
        rechazo = Comprobacion.OTRA_FIRMA
        fuente.descarga.complete(Unit)
        a.descargarEInstalar()
        assertEquals(EstadoActualizacion.Error(Comprobacion.OTRA_FIRMA, nueva), a.estado.value)
        assertEquals(1, comprobados)
        assertNull(android.estadoAlInstalar)
        assertTrue(android.instalados.isEmpty())
        assertFalse(File(carpeta, "actualizacion.apk").exists())
    }

    @Test
    fun `si la descarga falla o no cuadra, lo dice y deja volver a intentar`() {
        val a = conVersion()
        a.descargarEInstalar()
        fuente.descarga.completeExceptionally(ErrorDeActualizacion("Lo que se bajó no es el archivo que GitHub publicó (su huella sha256 no cuadra): se borró y no se instala."))
        assertEquals(
            EstadoActualizacion.Error("Lo que se bajó no es el archivo que GitHub publicó (su huella sha256 no cuadra): se borró y no se instala.", nueva),
            a.estado.value,
        )
        assertEquals(0, comprobados)
        assertFalse(File(carpeta, "actualizacion.apk").exists())

        fuente.descarga = CompletableDeferred()
        a.descargarEInstalar()
        assertEquals(EstadoActualizacion.Bajando(nueva, 0), a.estado.value)
        fuente.descarga.completeExceptionally(IOException("unexpected end of stream"))
        assertEquals(EstadoActualizacion.Error(Actualizador.SE_CORTO, nueva), a.estado.value)
        assertTrue(android.instalados.isEmpty())
    }

    @Test
    fun `si Android no acepta el APK, lo dice`() {
        val a = conVersion()
        android.falla = IOException("Android no aceptó el APK")
        fuente.descarga.complete(Unit)
        a.descargarEInstalar()
        assertEquals(EstadoActualizacion.Error(Actualizador.ANDROID_NO_LO_ACEPTO, nueva), a.estado.value)
        assertFalse(File(carpeta, "actualizacion.apk").exists())
    }

    @Test
    fun `cancelar a medio bajar borra lo bajado y regresa a la version encontrada`() {
        val a = conVersion()
        a.descargarEInstalar()
        fuente.avanzar!!(6)
        a.cancelar()
        assertEquals(EstadoActualizacion.Hay(nueva, Actualizador.DESCARGA_CANCELADA), a.estado.value)
        assertTrue(fuente.descarga.isActive)
        assertFalse(File(carpeta, "actualizacion.apk").exists())
        assertEquals(0, comprobados)
        // El avance que llega después de cancelar no regresa a «bajando».
        fuente.avanzar!!(9)
        assertEquals(EstadoActualizacion.Hay(nueva, Actualizador.DESCARGA_CANCELADA), a.estado.value)
        // Y se puede volver a empezar.
        fuente.descarga = CompletableDeferred<Unit>().apply { complete(Unit) }
        a.descargarEInstalar()
        assertEquals(EstadoActualizacion.EsperandoAAndroid(nueva), a.estado.value)
    }

    @Test
    fun `sin el permiso de Android no se baja nada y lo dice`() {
        val a = conVersion()
        android.permitido = false
        assertFalse(a.puedeInstalar())
        a.descargarEInstalar()
        assertEquals(EstadoActualizacion.Error(Actualizador.SIN_PERMISO, nueva), a.estado.value)
        assertEquals(0, fuente.descargas)
        android.permitido = true
        fuente.descarga.complete(Unit)
        a.descargarEInstalar()
        assertEquals(EstadoActualizacion.EsperandoAAndroid(nueva), a.estado.value)
    }

    @Test
    fun `esperando a Android se puede volver a buscar, por si su cuadro se perdio`() {
        val a = conVersion()
        fuente.descarga.complete(Unit)
        a.descargarEInstalar()
        fuente.ultima = CompletableDeferred()
        a.buscar()
        assertEquals(EstadoActualizacion.Buscando, a.estado.value)
        // La respuesta de aquella instalación ya no cuenta.
        a.alContestarAndroid(RespuestaDeAndroid.CANCELADA)
        assertEquals(EstadoActualizacion.Buscando, a.estado.value)
    }

    @Test
    fun `lo que quedo de una descarga anterior se borra al empezar`() {
        carpeta.mkdirs()
        File(carpeta, "actualizacion.apk").writeBytes(ByteArray(100))
        File(carpeta, "otro.tmp").writeBytes(ByteArray(3))
        actualizador()
        assertEquals(emptyList<String>(), carpeta.list().orEmpty().toList())
    }
}
