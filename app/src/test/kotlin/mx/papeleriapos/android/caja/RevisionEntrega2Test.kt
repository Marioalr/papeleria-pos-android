package mx.papeleriapos.android.caja

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mx.papeleriapos.android.copia.AlmacenCopia
import mx.papeleriapos.android.copia.CopiaTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Las pruebas de docs/revision-entrega-2.md: cada una demuestra un hallazgo y falla con el código de la 0.4.0; desde la
 * 0.4.2, arreglados, pasan. (Los hallazgos 3 y 5 se prueban en `AbrirBloqueadaTest` y `CatalogoDelDiaTest`.)
 *
 * A diferencia del despachador de [ConexionTest], el de aquí contesta como el servicio de verdad (rama `celular/como-caja`
 * del POS): `GET /api/estado` está abierto con cualquier llave o sin ella (`SeguridadCajas.cs:53`, `ApiCajas.cs:24-27`), y
 * solo lo demás contesta 401 «caja-sin-alta» a una llave que no conoce (`SeguridadCajas.cs:59-60`).
 */
@RunWith(AndroidJUnit4::class)
class RevisionEntrega2Test {
    private val app: Context = ApplicationProvider.getApplicationContext()
    private val servicio = ServicioDePrueba()
    private val principal = MockWebServer()
    private val carpeta = File(app.cacheDir, "copia-revision-${System.nanoTime()}")
    private val prefs = app.getSharedPreferences("conexion-revision-${System.nanoTime()}", Context.MODE_PRIVATE)
    private val almacenAlta = AlmacenAlta(prefs, Cifrador.Transparente)
    private val almacenCopia = AlmacenCopia(carpeta)
    private val cajaId = "0190c5a2-0000-7000-8000-00000000c0a1"
    private val base = "0190c5a2-0000-7000-8000-00000000ba5e"

    private var llaveValida = true
    private var msPaquete = 0L
    private val pedidos: MutableList<String> = Collections.synchronizedList(ArrayList())
    private var encontradas: List<Direccion> = emptyList()
    private lateinit var direccion: Direccion

    /** Como el servicio de verdad: el estado abierto, lo demás con la llave. */
    private fun comoElPos(firma: ServicioDePrueba, direccionDelServidor: () -> Direccion) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val ruta = request.path.orEmpty()
            pedidos.add(ruta)
            val conLlave = request.getHeader(ClienteCaja.ENCABEZADO_LLAVE)
            val abierta = ruta.startsWith("/api/estado") || ruta.startsWith("/api/alta") || ruta.startsWith("/api/identidad")
            if (!abierta && (conLlave == null || !llaveValida || conLlave != firma.llaveDeLaCaja)) {
                return MockResponse().setResponseCode(401).setBody("""{"mensaje":"Esta PC no está dada de alta como caja.","codigo":"caja-sin-alta"}""")
            }
            return when {
                ruta.startsWith("/api/estado") -> MockResponse().setBody(estado(base, 41, 7))
                ruta == "/api/alta" -> MockResponse().setBody(
                    """{"caja":{"id":"$cajaId","nombre":"Celular de Luis","serie":"CEL","sucursalId":"s1","sucursal":"Matriz","equipo":"Prueba"},"llave":"${firma.llaveDeLaCaja}","llaveDelServicio":"${firma.llavePublica}"}""",
                )
                ruta.endsWith("/paquete") -> MockResponse().setBody(CopiaTest.textoDelPaquete()).setHeadersDelay(msPaquete, TimeUnit.MILLISECONDS)
                ruta.contains("/cambios") -> MockResponse().setBody("""{"paqueteCompleto":null,"version":41,"versionExistencias":7,"ultimoFolio":0,"productos":[],"quitados":[],"codigos":[],"existencias":[]}""")
                ruta.startsWith("/api/identidad") -> {
                    val reto = ruta.substringAfter("reto=")
                    MockResponse().setBody(JsonDelPos.encodeToString(IdentidadServicioDto.serializer(), firma.identidad(cajaId, reto, direccionDelServidor())))
                }
                else -> MockResponse().setResponseCode(404).setBody("""{"mensaje":"No está."}""")
            }
        }
    }

    private fun estado(base: String, versionDatos: Long, versionExistencias: Long) =
        """{"servicio":"Papelería POS","version":"1.0","sucursales":["Matriz"],"versionDatos":$versionDatos,"versionExistencias":$versionExistencias,"base":"$base","formatoCopia":5}"""

    private fun conexion(msEntreLatidos: Long = Conexion.MS_ENTRE_LATIDOS) = Conexion(
        almacenAlta, almacenCopia, ClienteCaja(msRespuesta = 2_000), equipo = "Prueba",
        descubrir = { encontradas }, reloj = { 1_000L }, cronometro = { 0L }, msEntreLatidos = msEntreLatidos,
    )

    @Before
    fun arrancar() {
        principal.dispatcher = comoElPos(servicio) { direccion }
        principal.start()
        direccion = Direccion("127.0.0.1", principal.port)
    }

    @After
    fun apagar() {
        principal.shutdown()
        carpeta.deleteRecursively()
    }

    private fun <T> espera(bloque: suspend () -> T): T = runBlocking { withTimeout(15_000) { bloque() } }

    private fun Conexion.altaConCopia(): Alta = espera {
        val alta = darDeAlta(direccion, "luis", "1234", "s1", "Celular de Luis", "CEL")
        while (copia.value == null) delay(20)
        alta
    }

    /** Lo que espera hasta que [condicion] o se acaba el tiempo (las pruebas con hilos de verdad). */
    private fun hasta(ms: Long = 5_000, condicion: () -> Boolean) {
        val fin = System.currentTimeMillis() + ms
        while (!condicion() && System.currentTimeMillis() < fin) Thread.sleep(20)
    }

    // ---- Hallazgo 1 ----

    @Test
    fun `despues de una caida la llave no sale hacia la ip guardada sin que pruebe quien es`() {
        val c = conexion()
        c.altaConCopia()
        // La caja principal se apaga (o cambia de IP): el latido no tiene respuesta.
        principal.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }
        espera { c.latir() }
        assertEquals(Red.CAIDA, c.estado.value.red)
        // El router le da esa IP a otro equipo de la red (otro celular, una laptop): contesta lo que sea y anota lo que recibe.
        val llavesRecibidas: MutableList<String?> = Collections.synchronizedList(ArrayList())
        principal.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                llavesRecibidas.add(request.getHeader(ClienteCaja.ENCABEZADO_LLAVE))
                return if (request.path.orEmpty().startsWith("/api/identidad")) {
                    MockResponse().setResponseCode(404).setBody("""{"mensaje":"No está."}""")
                } else {
                    MockResponse().setBody(estado(base, 41, 7))
                }
            }
        }
        espera { c.latir() }
        assertTrue(
            "La llave de la caja salió en claro hacia un equipo que no probó ser la caja principal: $llavesRecibidas",
            llavesRecibidas.none { it == servicio.llaveDeLaCaja },
        )
    }

    // ---- Hallazgo 2 ----

    @Test
    fun `otro pos en la ip guardada no borra el alta si la caja principal de verdad se encuentra en otra ip`() {
        val c = conexion()
        c.altaConCopia()
        // La PC de la caja principal tomó otra IP; la de antes ahora es de una PC con el POS de prueba (otra base, no conoce esta caja).
        val mudada = MockWebServer()
        lateinit var direccionMudada: Direccion
        mudada.dispatcher = comoElPos(servicio) { direccionMudada }
        mudada.start()
        direccionMudada = Direccion("127.0.0.1", mudada.port)
        encontradas = listOf(direccionMudada)
        val otraBase = "0190c5a2-0000-7000-8000-0000000000ff"
        principal.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val ruta = request.path.orEmpty()
                return when {
                    ruta.startsWith("/api/estado") -> MockResponse().setBody(estado(otraBase, 3, 1))
                    ruta.startsWith("/api/identidad") -> MockResponse().setResponseCode(404).setBody("""{"mensaje":"Esa caja no está dada de alta en este servicio.","codigo":"caja-sin-alta"}""")
                    else -> MockResponse().setResponseCode(401).setBody("""{"mensaje":"Esta PC no está dada de alta como caja.","codigo":"caja-sin-alta"}""")
                }
            }
        }
        try {
            espera { c.latir() }
            assertNotNull("Un servicio que no probó quién es borró la llave y la copia", c.estado.value.alta)
            assertNotNull(almacenCopia.leer())
            espera { c.latir() }
            assertEquals(direccionMudada, c.estado.value.alta?.direccion)
        } finally {
            mudada.shutdown()
        }
    }

    // ---- Hallazgo 4 ----

    @Test
    fun `al darse de alta con la app al frente la copia completa se baja una sola vez`() {
        msPaquete = 1_500
        // La app al frente con el latido corriendo (aquí cada 200 ms en lugar de 30 s), todavía sin alta.
        val c = conexion(msEntreLatidos = 200)
        c.precargar()
        c.alFrente()
        try {
            espera { c.darDeAlta(direccion, "luis", "1234", "s1", "Celular de Luis", "CEL") }
            hasta { c.copia.value != null }
            Thread.sleep(2_000)
            val paquetes = pedidos.count { it.endsWith("/paquete") }
            assertEquals("Varios MB bajando dos veces a la vez", 1, paquetes)
        } finally {
            c.alFondo()
        }
    }

    @Test
    fun `una copia que termina de bajar despues de olvidar el alta no se publica`() {
        msPaquete = 1_000
        val c = conexion()
        espera { c.darDeAlta(direccion, "luis", "1234", "s1", "Celular de Luis", "CEL") }
        // Mientras baja la copia, el alta se olvida (lo que hace un 401 «caja-sin-alta» en otra llamada).
        espera { c.olvidarAlta(Conexion.SIN_ACCESO) }
        hasta(3_000) { c.copia.value != null }
        assertNull(c.estado.value.alta)
        assertNull("Quedó la copia de un alta que ya no existe", c.copia.value)
        assertNull("Y se escribió en el disco", almacenCopia.leer())
    }

    // ---- Hallazgo 6 ----

    @Test
    fun `irse al fondo mientras baja la primera copia no la tira`() {
        // Un alta guardada sin copia todavía (se dio de alta y se cerró la app, o la copia no se pudo leer).
        almacenAlta.guardar(
            Alta(direccion, CajaDto(cajaId, "Celular de Luis", "CEL", "s1", "Matriz"), "Prueba", "luis", servicio.llaveDeLaCaja, servicio.llavePublica, 1L),
        )
        msPaquete = 1_000
        val c = conexion()
        c.alFrente()
        hasta { pedidos.any { it.endsWith("/paquete") } }
        // Contestan un WhatsApp: la app se va al fondo unos segundos mientras baja la copia.
        c.alFondo()
        Thread.sleep(2_500)
        assertFalse("«bajando» se queda encendido", c.estado.value.bajando)
        assertNotNull("La copia bajó completa y se tiró (Conexion.kt:120 dice que lo que esté bajando, termina)", c.copia.value)
    }

    // ---- Hallazgo 7 ----

    @Test
    fun `al quitarle la llave el latido lo nota aunque no cambie el catalogo`() {
        val c = conexion()
        c.altaConCopia()
        // Configuración › Cajas › «Quitar PC» en la caja principal.
        llaveValida = false
        espera { c.latir() }
        assertNull(
            "Con el servicio de verdad (/api/estado abierto) el latido sigue «Buena» con una llave que ya no sirve",
            c.estado.value.alta,
        )
    }

    // ---- Hallazgo 8 ----

    @Test
    fun `solo direcciones privadas de la red de la tienda`() {
        // 100.64/10 es la red compartida de los operadores (CGNAT, RFC 6598): con datos móviles, son otros clientes del operador.
        assertNull(RedLocal.direccionDe("100.64.12.7"))
        assertNull(RedLocal.direccionDe("100.127.255.1"))
    }
}
