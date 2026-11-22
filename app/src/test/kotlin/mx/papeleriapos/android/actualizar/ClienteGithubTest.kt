package mx.papeleriapos.android.actualizar

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * La única salida de la app fuera de la red de la tienda: a dónde puede pedir, que no manda nada del celular, y que lo que
 * baja es lo que GitHub publicó. El servidor de mentira hace de GitHub; la regla de a dónde se puede pedir se prueba aparte
 * ([ClienteGithub.esDeGithub]) y aquí se cambia por «solo este servidor».
 */
class ClienteGithubTest {
    @get:Rule
    val carpeta = TemporaryFolder()

    private val github = MockWebServer()
    private val otro = MockWebServer()

    @Before
    fun arrancar() {
        github.start()
        otro.start()
    }

    @After
    fun apagar() {
        github.shutdown()
        otro.shutdown()
    }

    private fun soloGithubDeMentira(url: HttpUrl) = url.host == github.hostName && url.port == github.port

    private fun cliente() = ClienteGithub(urlUltima = github.url("/repos/x/y/releases/latest").toString(), permitida = ::soloGithubDeMentira)

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun apk(contenido: ByteArray, sha: String? = sha256(contenido), tamano: Long = contenido.size.toLong(), ruta: String = "/descarga/${Ejemplos.NOMBRE}") =
        ApkPublicado(Ejemplos.NOMBRE, tamano, github.url(ruta).toString(), sha)

    private fun destino() = File(carpeta.root, "actualizar/actualizacion.apk")

    // ---- A dónde puede salir ----

    @Test
    fun `solo HTTPS y solo a los hosts de GitHub`() {
        for (buena in listOf(
            ClienteGithub.URL_ULTIMA,
            "https://github.com/Marioalr/papeleria-pos-android/releases/download/v0.5.1/escaner-0.5.1-a1b2c3d.apk",
            "https://objects.githubusercontent.com/github-production-release-asset/1?X-Amz-Signature=abc",
            "https://release-assets.githubusercontent.com/github-production-release-asset/1?sig=abc",
        )) assertTrue(buena, ClienteGithub.esDeGithub(buena.toHttpUrl()))
        for (mala in listOf(
            "http://api.github.com/repos/Marioalr/papeleria-pos-android/releases/latest",
            "http://github.com/x.apk",
            "https://github.com:8443/x.apk",
            "https://github.com.ejemplo.mx/x.apk",
            "https://ejemplo.mx/github.com/x.apk",
            "https://raw.githubusercontent.com/x/y/main/app.apk",
            "https://gist.github.com/x",
            "https://githubusercontent.com/x",
            "https://api.github.com.evil.example/x",
            "https://192.168.1.10/x.apk",
            "http://192.168.1.10:5080/api/estado",
        )) assertFalse(mala, ClienteGithub.esDeGithub(mala.toHttpUrl()))
    }

    @Test
    fun `con la regla de verdad, a un servidor que no es GitHub no se le pide nada`() {
        val deVerdad = ClienteGithub(urlUltima = github.url("/repos/x/y/releases/latest").toString())
        val e = assertThrows(ErrorDeActualizacion::class.java) { runBlocking { deVerdad.ultima() } }
        assertEquals(ClienteGithub.FUERA_DE_GITHUB, e.message)
        val bajar = assertThrows(ErrorDeActualizacion::class.java) { runBlocking { deVerdad.bajar(apk(ByteArray(8)), destino()) } }
        assertEquals(ClienteGithub.FUERA_DE_GITHUB, bajar.message)
        assertEquals(0, github.requestCount)
        assertFalse(destino().exists())
    }

    @Test
    fun `una redireccion hacia otro host se rechaza sin pedirle nada`() {
        github.enqueue(MockResponse().setResponseCode(302).setHeader("Location", otro.url("/robado.apk")))
        val contenido = "no es el apk".toByteArray()
        otro.enqueue(MockResponse().setBody(Buffer().write(contenido)))
        val e = assertThrows(ErrorDeActualizacion::class.java) { runBlocking { cliente().bajar(apk(contenido), destino()) } }
        assertEquals(ClienteGithub.FUERA_DE_GITHUB, e.message)
        assertEquals(1, github.requestCount)
        assertEquals(0, otro.requestCount)
        assertFalse(destino().exists())
    }

    @Test
    fun `tampoco al preguntar por la ultima version`() {
        github.enqueue(MockResponse().setResponseCode(301).setHeader("Location", otro.url("/latest")))
        otro.enqueue(MockResponse().setBody(Ejemplos.json()))
        val e = assertThrows(ErrorDeActualizacion::class.java) { runBlocking { cliente().ultima() } }
        assertEquals(ClienteGithub.FUERA_DE_GITHUB, e.message)
        assertEquals(0, otro.requestCount)
    }

    @Test
    fun `las redirecciones dentro de lo permitido se siguen, pero no sin fin`() {
        val contenido = ByteArray(1_000) { (it % 7).toByte() }
        github.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/entrega/1?firma=abc"))
        github.enqueue(MockResponse().setBody(Buffer().write(contenido)))
        runBlocking { cliente().bajar(apk(contenido), destino()) }
        assertArrayEquals(contenido, destino().readBytes())
        assertEquals("/descarga/${Ejemplos.NOMBRE}", github.takeRequest().path)
        assertEquals("/entrega/1?firma=abc", github.takeRequest().path)

        repeat(10) { github.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/vuelta/$it")) }
        val e = assertThrows(ErrorDeActualizacion::class.java) { runBlocking { cliente().bajar(apk(contenido), destino()) } }
        assertTrue(e.message, e.message!!.contains("demasiadas veces"))
        assertFalse(destino().exists())
    }

    // ---- Lo que manda ----

    @Test
    fun `no manda cookies, llaves ni nada del celular`() {
        github.enqueue(MockResponse().setBody(Ejemplos.json()).setHeader("Set-Cookie", "sesion=abc; Path=/"))
        github.enqueue(MockResponse().setBody(Ejemplos.json()))
        val c = cliente()
        runBlocking {
            c.ultima()
            c.ultima()
        }
        repeat(2) {
            val pedido = github.takeRequest()
            assertEquals("GET", pedido.method)
            assertEquals(0L, pedido.bodySize)
            // Solo lo que pone OkHttp para hablar HTTP y el Accept de la API: ni cookies, ni llave de la caja, ni autorización.
            assertEquals(setOf("accept", "accept-encoding", "connection", "host", "user-agent"), pedido.headers.names().map { it.lowercase() }.toSet())
            assertEquals("application/vnd.github+json", pedido.getHeader("Accept"))
            assertTrue(pedido.getHeader("User-Agent")!!.startsWith("okhttp/"))
            assertNull(pedido.requestUrl!!.query)
        }
    }

    // ---- La última versión ----

    @Test
    fun `la ultima version se lee de lo que contesta GitHub`() {
        github.enqueue(MockResponse().setBody(Ejemplos.json()))
        val v = runBlocking { cliente().ultima() }
        assertEquals(Version(0, 5, 1), v.version)
        assertEquals(Ejemplos.NOMBRE, v.apk.nombre)
        assertEquals("/repos/x/y/releases/latest", github.takeRequest().path)
    }

    @Test
    fun `lo que GitHub contesta que no, se dice en español`() {
        fun mensajeCon(codigo: Int, cuerpo: String = """{"message": "Not Found"}"""): String {
            github.enqueue(MockResponse().setResponseCode(codigo).setBody(cuerpo))
            return assertThrows(ErrorDeActualizacion::class.java) { runBlocking { cliente().ultima() } }.message!!
        }
        assertEquals("Todavía no hay ninguna versión publicada en GitHub.", mensajeCon(404))
        assertTrue(mensajeCon(403).contains("Espera unos minutos"))
        assertTrue(mensajeCon(429).contains("Espera unos minutos"))
        assertEquals("GitHub contestó con un error (HTTP 500). Vuelve a buscar más tarde.", mensajeCon(500))
        assertEquals(Versiones.NO_SE_ENTIENDE, mensajeCon(200, "<html>mantenimiento</html>"))
        // Algo enorme no es la lista de una versión: no se lee entero.
        assertEquals(Versiones.NO_SE_ENTIENDE, mensajeCon(200, "{\"body\": \"" + "x".repeat(3 * 1024 * 1024) + "\"}"))
    }

    // ---- La descarga ----

    @Test
    fun `baja el APK, avisa cuanto va y comprueba el tamano y la huella`() {
        val contenido = ByteArray(700_000) { (it * 31 % 251).toByte() }
        github.enqueue(MockResponse().setBody(Buffer().write(contenido)))
        val avances = mutableListOf<Long>()
        runBlocking { cliente().bajar(apk(contenido), destino()) { avances += it } }
        assertArrayEquals(contenido, destino().readBytes())
        assertEquals(700_000L, avances.last())
        assertEquals(avances.sorted(), avances)
        assertTrue("avisa a medio camino: $avances", avances.size >= 2)
        assertEquals("application/octet-stream", github.takeRequest().getHeader("Accept"))
    }

    @Test
    fun `si la huella no cuadra, se borra y lo dice`() {
        val contenido = ByteArray(5_000) { 1 }
        github.enqueue(MockResponse().setBody(Buffer().write(contenido)))
        val e = assertThrows(ErrorDeActualizacion::class.java) { runBlocking { cliente().bajar(apk(contenido, sha = "00".repeat(32)), destino()) } }
        assertTrue(e.message, e.message!!.contains("su huella sha256 no cuadra"))
        assertFalse(destino().exists())
    }

    @Test
    fun `sin huella publicada, basta el tamano`() {
        val contenido = ByteArray(5_000) { 2 }
        github.enqueue(MockResponse().setBody(Buffer().write(contenido)))
        runBlocking { cliente().bajar(apk(contenido, sha = null), destino()) }
        assertEquals(5_000L, destino().length())
    }

    @Test
    fun `mas grande, mas chico o cortado de lo publicado, se borra y lo dice`() {
        val contenido = ByteArray(5_000) { 3 }
        github.enqueue(MockResponse().setBody(Buffer().write(contenido)))
        val grande = assertThrows(ErrorDeActualizacion::class.java) { runBlocking { cliente().bajar(apk(contenido, tamano = 4_000), destino()) } }
        assertTrue(grande.message, grande.message!!.contains("más de lo que GitHub publicó"))
        assertFalse(destino().exists())

        github.enqueue(MockResponse().setBody(Buffer().write(contenido)))
        val chico = assertThrows(ErrorDeActualizacion::class.java) { runBlocking { cliente().bajar(apk(contenido, tamano = 6_000), destino()) } }
        assertTrue(chico.message, chico.message!!.contains("incompleta"))
        assertFalse(destino().exists())

        github.enqueue(MockResponse().setResponseCode(404))
        val noEsta = assertThrows(ErrorDeActualizacion::class.java) { runBlocking { cliente().bajar(apk(contenido), destino()) } }
        assertEquals("GitHub no entregó el APK (HTTP 404). Vuelve a intentar más tarde.", noEsta.message)
        assertFalse(destino().exists())

        // Un tamaño publicado que no puede ser el de la app: ni se pide.
        val antes = github.requestCount
        assertThrows(ErrorDeActualizacion::class.java) { runBlocking { cliente().bajar(apk(contenido, tamano = ClienteGithub.MAXIMO_APK + 1), destino()) } }
        assertThrows(ErrorDeActualizacion::class.java) { runBlocking { cliente().bajar(apk(contenido, tamano = 0), destino()) } }
        assertEquals(antes, github.requestCount)
    }
}
