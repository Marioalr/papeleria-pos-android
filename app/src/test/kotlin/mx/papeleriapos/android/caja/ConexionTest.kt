package mx.papeleriapos.android.caja

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mx.papeleriapos.android.copia.AlmacenCopia
import mx.papeleriapos.android.copia.CopiaTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

/**
 * La conexión con una caja principal de mentira ([MockWebServer]): el alta baja la copia, el latido pide los cambios
 * solo cuando hay, un 401 «caja-sin-alta» borra todo, y si la caja principal cambia de IP se vuelve a encontrar solo
 * cuando prueba quién es.
 */
@RunWith(AndroidJUnit4::class)
class ConexionTest {
    private val app: Context = ApplicationProvider.getApplicationContext()
    private val servicio = ServicioDePrueba()
    private val principal = MockWebServer()
    private val carpeta = File(app.cacheDir, "copia-prueba-${System.nanoTime()}")
    private val prefs = app.getSharedPreferences("conexion-prueba-${System.nanoTime()}", Context.MODE_PRIVATE)
    private val almacenAlta = AlmacenAlta(prefs, Cifrador.Transparente)
    private val almacenCopia = AlmacenCopia(carpeta)
    private val cajaId = "0190c5a2-0000-7000-8000-00000000c0a1"

    /** Lo que contesta la caja principal de mentira; las pruebas lo cambian. */
    private var versionDatos = 41L
    private var versionExistencias = 7L
    private var cambios = """{"paqueteCompleto":null,"version":42,"versionExistencias":7,"ultimoFolio":0,"productos":[],"quitados":[],"codigos":[],"existencias":[],"marcas":["SOLO-UNA"]}"""
    private var llaveValida = true
    private val pedidos = ArrayList<String>()
    private var encontradas: List<Direccion> = emptyList()

    /**
     * Como el servicio de verdad (`SeguridadCajas.cs`): `/api/estado`, el alta y la identidad contestan a cualquiera; lo
     * demás, 401 «caja-sin-alta» a una llave que no conoce. [llaves] anota las llaves que le llegan a ese servidor.
     */
    private fun despachador(firma: ServicioDePrueba, llaves: MutableList<String> = ArrayList(), direccionDelServidor: () -> Direccion) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val ruta = request.path.orEmpty()
            synchronized(pedidos) { pedidos.add(ruta) }
            val conLlave = request.getHeader(ClienteCaja.ENCABEZADO_LLAVE)
            conLlave?.let { synchronized(llaves) { llaves.add(it) } }
            val abierta = ruta.startsWith("/api/estado") || ruta.startsWith("/api/alta") || ruta.startsWith("/api/identidad")
            if (!abierta && (conLlave == null || !llaveValida || conLlave != firma.llaveDeLaCaja)) {
                return MockResponse().setResponseCode(401).setBody("""{"mensaje":"Esta PC no está dada de alta como caja.","codigo":"caja-sin-alta"}""")
            }
            return when {
                ruta.startsWith("/api/estado") -> MockResponse().setBody(
                    """{"servicio":"Papelería POS","version":"1.0","sucursales":["Matriz"],"versionDatos":$versionDatos,"versionExistencias":$versionExistencias,"base":"0190c5a2-0000-7000-8000-00000000ba5e","formatoCopia":5}""",
                )
                ruta == "/api/alta/opciones" -> MockResponse().setBody("""[{"id":"s1","nombre":"Matriz","cajas":[{"id":"c1","nombre":"Caja 1","serie":"A","equipo":"PC"}]}]""")
                ruta == "/api/alta" -> MockResponse().setBody(
                    """{"caja":{"id":"$cajaId","nombre":"Celular de Luis","serie":"CEL","sucursalId":"s1","sucursal":"Matriz","equipo":"Prueba"},"llave":"${servicio.llaveDeLaCaja}","llaveDelServicio":"${servicio.llavePublica}"}""",
                )
                ruta.endsWith("/paquete") -> MockResponse().setBody(CopiaTest.textoDelPaquete())
                ruta.contains("/cambios") -> MockResponse().setBody(cambios)
                ruta.startsWith("/api/identidad") -> {
                    val reto = ruta.substringAfter("reto=")
                    val r = firma.identidad(cajaId, reto, direccionDelServidor())
                    MockResponse().setBody(JsonDelPos.encodeToString(IdentidadServicioDto.serializer(), r))
                }
                else -> MockResponse().setResponseCode(404).setBody("""{"mensaje":"No está."}""")
            }
        }
    }

    private lateinit var direccion: Direccion
    private lateinit var conexion: Conexion

    @Before
    fun arrancar() {
        principal.dispatcher = despachador(servicio) { direccion }
        principal.start()
        direccion = Direccion("127.0.0.1", principal.port)
        conexion = Conexion(
            almacenAlta, almacenCopia, ClienteCaja(msRespuesta = 2_000), equipo = "Prueba",
            descubrir = { encontradas }, reloj = { 1_000L }, cronometro = { 0L }, hilo = Dispatchers.Unconfined,
        )
    }

    @After
    fun apagar() {
        principal.shutdown()
        carpeta.deleteRecursively()
    }

    private fun <T> espera(bloque: suspend () -> T): T = runBlocking { withTimeout(10_000) { bloque() } }

    private fun darDeAlta(): Alta = espera {
        val alta = conexion.darDeAlta(direccion, "luis", "1234", "s1", "Celular de Luis", "CEL")
        conexion.copia.filterNotNull().first()
        alta
    }

    @Test
    fun `sin alta no se llama a nadie y el estado lo dice`() = espera {
        conexion.precargar()
        val estado = conexion.estado.first { it.leida }
        assertNull(estado.alta)
        conexion.latir()
        assertTrue(pedidos.isEmpty())
    }

    @Test
    fun `el alta guarda la llave cifrada, baja la copia y la deja en el disco`() {
        val alta = darDeAlta()
        assertEquals("Celular de Luis", alta.caja.nombre)
        assertEquals(servicio.llaveDeLaCaja, alta.llave)
        assertEquals(servicio.llavePublica, alta.llaveDelServicio)
        assertEquals("luis", alta.usuario)
        assertEquals(alta, almacenAlta.leer())
        val estado = conexion.estado.value
        assertEquals(6, estado.copia!!.variantes)
        assertEquals(41L, estado.copia!!.version)
        assertFalse(estado.bajando)
        assertEquals("La copia quedó en el disco", 41L, almacenCopia.leer()!!.version)
        assertTrue(pedidos.any { it.endsWith("/paquete") })
        // El PIN no quedó en ningún lado.
        assertFalse(prefs.all.values.any { it.toString().contains("1234") })
    }

    @Test
    fun `justo despues del alta la red ya sale buena, sin esperar al primer latido`() {
        darDeAlta()
        // Regresión: salía «Sin probar todavía» hasta el primer latido, aunque el alta y la copia ya habían hablado con el servicio.
        assertEquals(Red.BUENA, conexion.estado.value.red)
        assertEquals(1_000L, conexion.estado.value.ultimaVez)
        assertTrue("Nada de latidos todavía", pedidos.none { it.startsWith("/api/estado?caja") })
    }

    @Test
    fun `el latido aplica los cambios solo cuando las versiones cambian`() {
        darDeAlta()
        pedidos.clear()
        val guardada = File(carpeta, "copia.json").takeIf { it.exists() }?.lastModified()
        espera { conexion.latir() }
        // Mismas versiones: el latido y los cambios solo para saber si la llave sirve (/api/estado contesta a cualquiera).
        assertEquals(pedidos.toString(), 2, pedidos.size)
        assertEquals("/api/estado?caja=$cajaId", pedidos[0])
        assertTrue(pedidos[1], pedidos[1].startsWith("/api/cajas/$cajaId/cambios?desde=41&existencias=7"))
        assertEquals("Y no se aplica nada", 41L, conexion.copia.value!!.version)
        assertEquals(guardada, File(carpeta, "copia.json").takeIf { it.exists() }?.lastModified())
        assertEquals(Red.BUENA, conexion.estado.value.red)
        assertEquals(1_000L, conexion.estado.value.ultimaVez)

        versionDatos = 42
        pedidos.clear()
        espera { conexion.latir() }
        assertTrue(pedidos.toString(), pedidos.any { it.contains("/cambios?desde=41&existencias=7") })
        assertEquals(42L, conexion.copia.value!!.version)
        assertEquals(listOf("SOLO-UNA"), conexion.copia.value!!.marcas)
        assertEquals(42L, almacenCopia.leer()!!.version)

        // Si los cambios dicen que se baje entera, se baja.
        versionDatos = 43
        cambios = """{"paqueteCompleto":"otra base","version":0,"versionExistencias":0,"ultimoFolio":0,"productos":[],"quitados":[],"codigos":[],"existencias":[]}"""
        pedidos.clear()
        espera { conexion.latir() }
        assertTrue(pedidos.any { it.endsWith("/paquete") })
        assertEquals(41L, conexion.copia.value!!.version)
    }

    @Test
    fun `un 401 sin alta borra la llave y la copia y lo dice`() {
        darDeAlta()
        llaveValida = false
        espera { conexion.latir() }
        val estado = conexion.estado.value
        assertNull(estado.alta)
        assertEquals(Conexion.SIN_ACCESO, estado.aviso)
        assertNull(conexion.copia.value)
        assertNull(almacenAlta.leer())
        assertNull(almacenCopia.leer())
        assertTrue(estado.sinAcceso)
    }

    /** Otra conexión sobre el mismo disco: como si la app se reiniciara. */
    private fun alReabrir(): EstadoConexion {
        val otra = Conexion(almacenAlta, almacenCopia, ClienteCaja(msRespuesta = 500), "Prueba", descubrir = { emptyList() }, hilo = Dispatchers.Unconfined)
        otra.precargar()
        return espera { otra.estado.first { it.leida } }.also { reabierta = otra }
    }

    private var reabierta: Conexion? = null

    @Test
    fun `el aviso de sin acceso se guarda hasta que se ve, sin nada de la caja`() {
        darDeAlta()
        llaveValida = false
        espera { conexion.latir() }
        // Regresión: con la huella tapando todo o al reiniciar la app, el aviso se perdía y La conexión solo ofrecía el alta.
        val alAbrir = alReabrir()
        assertNull(alAbrir.alta)
        assertEquals(Conexion.SIN_ACCESO, alAbrir.aviso)
        assertTrue(alAbrir.sinAcceso)
        // Solo el aviso: ni la llave ni nada de la caja.
        assertEquals(setOf("aviso"), prefs.all.keys)
        assertFalse(prefs.all.values.any { it.toString().contains(servicio.llaveDeLaCaja) || it.toString().contains(cajaId) })

        // El dueño lo ve en La conexión: en esa pantalla sigue, pero ya no se guarda ni sale en la principal.
        reabierta!!.avisoVisto()
        assertFalse(reabierta!!.estado.value.sinAcceso)
        assertEquals(Conexion.SIN_ACCESO, reabierta!!.estado.value.aviso)
        val otraVez = alReabrir()
        assertNull(otraVez.aviso)
        assertFalse(otraVez.sinAcceso)
    }

    @Test
    fun `el aviso de sin acceso se borra al volver a dar de alta`() {
        darDeAlta()
        llaveValida = false
        espera { conexion.latir() }
        llaveValida = true
        darDeAlta()
        assertFalse(conexion.estado.value.sinAcceso)
        assertNull(conexion.estado.value.aviso)
        val alAbrir = alReabrir()
        assertNotNull(alAbrir.alta)
        assertNull(alAbrir.aviso)
        assertFalse(alAbrir.sinAcceso)
    }

    @Test
    fun `si la caja principal cambia de ip se vuelve a encontrar solo cuando prueba quien es`() {
        darDeAlta()
        // La caja principal «se muda»: otro servidor con la misma base; el de antes se apaga.
        val mudada = MockWebServer()
        val otraPc = ServicioDePrueba(servicio.llaveDeLaCaja) // Una copia de la base en otra PC: sabe la huella, no firma igual.
        lateinit var direccionMudada: Direccion
        lateinit var direccionImpostora: Direccion
        val impostora = MockWebServer()
        val llavesDeLaImpostora = ArrayList<String>()
        mudada.dispatcher = despachador(servicio) { direccionMudada }
        impostora.dispatcher = despachador(otraPc, llavesDeLaImpostora) { direccionImpostora }
        mudada.start()
        impostora.start()
        direccionMudada = Direccion("127.0.0.1", mudada.port)
        direccionImpostora = Direccion("127.0.0.1", impostora.port)
        principal.shutdown()
        try {
            encontradas = listOf(direccionImpostora, direccionMudada)
            espera { conexion.latir() }
            assertEquals(Red.CAIDA, conexion.estado.value.red)
            assertEquals("Con un fallo todavía no se busca", direccion, conexion.estado.value.alta!!.direccion)
            espera { conexion.latir() }
            assertEquals(direccionMudada, conexion.estado.value.alta!!.direccion)
            assertEquals(direccionMudada, almacenAlta.leer()!!.direccion)
            // Y a la impostora nunca se le mandó la llave.
            assertTrue("$llavesDeLaImpostora", synchronized(llavesDeLaImpostora) { llavesDeLaImpostora.isEmpty() })
            pedidos.clear()
            espera { conexion.latir() }
            assertEquals(Red.BUENA, conexion.estado.value.red)
        } finally {
            mudada.shutdown()
            impostora.shutdown()
        }
    }

    @Test
    fun `al abrir con alta, la copia del disco se lee sin red`() {
        darDeAlta()
        principal.shutdown()
        val otra = Conexion(almacenAlta, almacenCopia, ClienteCaja(msRespuesta = 500), "Prueba", descubrir = { emptyList() }, hilo = Dispatchers.Unconfined)
        otra.precargar()
        val estado = espera { otra.estado.first { it.leida } }
        assertNotNull(estado.alta)
        assertEquals(6, estado.copia!!.variantes)
        assertEquals(41L, otra.copia.value!!.version)
        espera { otra.latir() }
        assertEquals(Red.CAIDA, otra.estado.value.red)
        assertEquals("Sin red se sigue con la copia", 41L, otra.copia.value!!.version)
    }
}
