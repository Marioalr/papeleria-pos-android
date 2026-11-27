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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * El celular enlazado a dos sucursales, cada una con su caja principal, su base y su llave (dos servicios de mentira):
 * enlazar la segunda no borra la primera, cambiar es solo cuando el dueño lo pide, cada una tiene su copia, la llave de una
 * nunca sale hacia la otra, y un alta de antes de la 0.5.5 se vuelve la primera sin darse de alta otra vez.
 */
@RunWith(AndroidJUnit4::class)
class DosSucursalesTest {
    private val app: Context = ApplicationProvider.getApplicationContext()
    private val carpeta = File(app.cacheDir, "copias-dos-${System.nanoTime()}")
    private val prefs = app.getSharedPreferences("conexion-dos-${System.nanoTime()}", Context.MODE_PRIVATE)
    private val almacenAlta = AlmacenAlta(prefs, Cifrador.Transparente)
    private val almacenCopia = AlmacenCopia(carpeta)

    /** Una caja principal de mentira: su llave, la caja que da de alta, su sucursal y qué llaves le llegaron. */
    private inner class Sucursal(val cajaId: String, val nombre: String, val serie: String, val version: Long) {
        val firma = ServicioDePrueba("llave-de-$serie-" + "x".repeat(40))
        val servidor = MockWebServer()
        val llavesQueLlegaron = ArrayList<String>()
        lateinit var direccion: Direccion

        fun arrancar() {
            servidor.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val ruta = request.path.orEmpty()
                    val llave = request.getHeader(ClienteCaja.ENCABEZADO_LLAVE)
                    llave?.let { synchronized(llavesQueLlegaron) { llavesQueLlegaron.add(it) } }
                    val abierta = ruta.startsWith("/api/estado") || ruta.startsWith("/api/alta") || ruta.startsWith("/api/identidad")
                    if (!abierta && llave != firma.llaveDeLaCaja) {
                        return MockResponse().setResponseCode(401).setBody("""{"mensaje":"Esta PC no está dada de alta como caja.","codigo":"caja-sin-alta"}""")
                    }
                    return when {
                        ruta.startsWith("/api/estado") -> MockResponse().setBody(
                            """{"servicio":"Papelería POS","version":"1.0","sucursales":["$nombre"],"versionDatos":$version,"versionExistencias":7,"base":"0190c5a2-0000-7000-8000-00000000ba5e","formatoCopia":5}""",
                        )
                        ruta == "/api/alta" -> MockResponse().setBody(
                            """{"caja":{"id":"$cajaId","nombre":"Celular","serie":"$serie","sucursalId":"s-$serie","sucursal":"$nombre","equipo":"Prueba"},"llave":"${firma.llaveDeLaCaja}","llaveDelServicio":"${firma.llavePublica}"}""",
                        )
                        ruta.endsWith("/paquete") -> MockResponse().setBody(
                            CopiaTest.textoDelPaquete()
                                .replace(CAJA_DEL_PAQUETE, cajaId)
                                .replace("\"sucursal\": \"Matriz\"", "\"sucursal\": \"$nombre\"")
                                .replace("\"version\": 41", "\"version\": $version"),
                        )
                        ruta.contains("/cambios") -> MockResponse().setBody(
                            """{"paqueteCompleto":null,"version":$version,"versionExistencias":7,"ultimoFolio":0,"productos":[],"quitados":[],"codigos":[],"existencias":[]}""",
                        )
                        ruta.startsWith("/api/identidad") -> {
                            val reto = ruta.substringAfter("reto=")
                            MockResponse().setBody(JsonDelPos.encodeToString(IdentidadServicioDto.serializer(), firma.identidad(cajaId, reto, direccion)))
                        }
                        else -> MockResponse().setResponseCode(404).setBody("""{"mensaje":"No está."}""")
                    }
                }
            }
            servidor.start()
            direccion = Direccion("127.0.0.1", servidor.port)
        }
    }

    private val matriz = Sucursal(CAJA_DEL_PAQUETE, "Matriz", "CEL", 41)
    private val sucursal2 = Sucursal("0190c5a2-0000-7000-8000-0000000000b2", "Sucursal 2", "CEA", 90)

    /** Lo que contesta la búsqueda por UDP; las pruebas lo cambian. */
    private var encontradas: List<Direccion> = emptyList()

    private fun conexion() = Conexion(
        almacenAlta, almacenCopia, ClienteCaja(msRespuesta = 2_000), equipo = "Prueba",
        descubrir = { encontradas }, reloj = { 1_000L }, cronometro = { 0L }, hilo = Dispatchers.Unconfined, leerRed = { null },
    )

    private lateinit var c: Conexion

    @Before
    fun arrancar() {
        matriz.arrancar()
        sucursal2.arrancar()
        c = conexion()
    }

    @After
    fun apagar() {
        matriz.servidor.shutdown()
        sucursal2.servidor.shutdown()
        carpeta.deleteRecursively()
    }

    private fun <T> espera(bloque: suspend () -> T): T = runBlocking { withTimeout(10_000) { bloque() } }

    private fun enlazar(s: Sucursal): Alta = espera {
        val alta = c.darDeAlta(s.direccion, "dueno", "3333", "s-${s.serie}", "Celular", s.serie)
        c.copia.filterNotNull().first { it.caja.id == s.cajaId }
        alta
    }

    @Test
    fun `enlazar otra sucursal deja las dos, cada una con su copia, y la nueva en uso`() {
        enlazar(matriz)
        enlazar(sucursal2)
        val e = c.estado.value
        assertEquals(sucursal2.cajaId, e.alta!!.caja.id)
        assertEquals(listOf("Matriz", "Sucursal 2"), e.enlaces.map { it.sucursal })
        assertEquals(listOf("Matriz"), e.otras.map { it.sucursal })
        assertEquals(41L, almacenCopia.leer(matriz.cajaId)!!.version)
        assertEquals(90L, almacenCopia.leer(sucursal2.cajaId)!!.version)
        assertEquals(2, almacenAlta.enlaces().size)
    }

    @Test
    fun `usar esta cambia al instante con la copia del disco y la llave sale hasta que la otra pruebe quien es`() {
        enlazar(matriz)
        enlazar(sucursal2)
        // La caja principal de la matriz no está en esta red: cambiar no la llama.
        matriz.servidor.shutdown()
        assertTrue(espera { c.cambiarA(matriz.cajaId) })
        assertEquals(matriz.cajaId, c.estado.value.alta!!.caja.id)
        assertEquals("La copia de la matriz, sin red", 41L, c.copia.value!!.version)
        assertEquals("Matriz", c.estado.value.copia!!.sucursal)
        // Reabrir la app: sigue en la matriz.
        val otra = conexion()
        otra.precargar()
        assertEquals(matriz.cajaId, espera { otra.estado.first { it.leida } }.alta!!.caja.id)
    }

    @Test
    fun `la llave de una sucursal nunca sale hacia la otra`() {
        enlazar(matriz)
        enlazar(sucursal2)
        espera { c.cambiarA(matriz.cajaId) }
        // La matriz cambió de IP y en la de antes ahora contesta el POS de la sucursal 2 (otro servicio).
        val llavesAntes = sucursal2.llavesQueLlegaron.size
        val viejaDeMatriz = matriz.direccion
        almacenAlta.guardarDireccion(matriz.cajaId, sucursal2.direccion)
        val c2 = conexion()
        c2.precargar()
        espera { c2.estado.first { it.leida } }
        encontradas = listOf(viejaDeMatriz)
        espera { c2.latir() }
        assertFalse(
            "La llave de la matriz llegó a la sucursal 2",
            sucursal2.llavesQueLlegaron.drop(llavesAntes).any { it == matriz.firma.llaveDeLaCaja },
        )
        assertEquals("Se encontró la matriz donde sí prueba quién es", viejaDeMatriz, c2.estado.value.alta!!.direccion)
    }

    @Test
    fun `estas en sucursal 2, cambiar busca una vez su caja principal y solo cambia si prueba quien es`() {
        enlazar(matriz)
        val s2 = enlazar(sucursal2)
        espera { c.cambiarA(matriz.cajaId) }
        // La sucursal 2 cambió de IP: en la guardada no hay nadie, por UDP contesta en la nueva.
        almacenAlta.guardarDireccion(sucursal2.cajaId, Direccion("127.0.0.1", 9))
        encontradas = listOf(matriz.direccion, sucursal2.direccion)
        val nueva = espera { c.buscarYCambiarA(s2.caja.id) }
        assertEquals(sucursal2.direccion, nueva.direccion)
        assertEquals(sucursal2.cajaId, c.estado.value.alta!!.caja.id)
        assertEquals("Se guardó la dirección nueva", sucursal2.direccion, almacenAlta.leer()!!.direccion)
        assertEquals(90L, c.copia.value!!.version)
    }

    @Test
    fun `si la otra sucursal no contesta no se cambia y se dice tal cual`() {
        enlazar(matriz)
        enlazar(sucursal2)
        espera { c.cambiarA(matriz.cajaId) }
        sucursal2.servidor.shutdown()
        encontradas = listOf(matriz.direccion)
        try {
            espera { c.buscarYCambiarA(sucursal2.cajaId) }
            fail("Cambió sin encontrar la caja principal de la sucursal 2")
        } catch (e: NoSeMando) {
            assertTrue(e.message!!, e.message!!.contains("Sucursal 2"))
            assertTrue(e.sinRed)
        }
        assertEquals("Se queda en la matriz", matriz.cajaId, c.estado.value.alta!!.caja.id)
    }

    @Test
    fun `si una caja le quita el acceso solo se olvida esa sucursal y ninguna queda en uso`() {
        enlazar(matriz)
        enlazar(sucursal2)
        espera { c.olvidarAlta(Conexion.SIN_ACCESO) }
        val e = c.estado.value
        assertNull(e.alta)
        assertTrue(e.sinAcceso)
        assertEquals(listOf("Matriz"), e.enlaces.map { it.sucursal })
        assertNull(almacenCopia.leer(sucursal2.cajaId))
        assertNotNull(almacenCopia.leer(matriz.cajaId))
        assertTrue(AlmacenAlta.hayAlta(appConPrefs()))
        // El dueño elige la que queda.
        assertTrue(espera { c.cambiarA(matriz.cajaId) })
        assertEquals(41L, c.copia.value!!.version)
    }

    /** [AlmacenAlta.hayAlta] lee el archivo de la app: se copia ahí lo de esta prueba. */
    private fun appConPrefs(): Context {
        val real = app.getSharedPreferences(AlmacenAlta.ARCHIVO, Context.MODE_PRIVATE)
        real.edit().clear().apply {
            for ((k, v) in prefs.all) if (v is String) putString(k, v)
        }.commit()
        return app
    }

    @Test
    fun `un alta de antes de la 0_5_5 se vuelve la primera, con su copia, sin darse de alta otra vez`() {
        // Como la guardaba la 0.5.4: los campos sin terminación y la copia en files/copia/copia.json.
        prefs.edit()
            .putString("llave", matriz.firma.llaveDeLaCaja)
            .putString("ip", matriz.direccion.ip)
            .putInt("puerto", matriz.direccion.puerto)
            .putString("caja_id", matriz.cajaId)
            .putString("caja_nombre", "Celular de Luis")
            .putString("serie", "CEL")
            .putString("sucursal_id", "s1")
            .putString("sucursal", "Matriz")
            .putString("equipo", "Pixel 8 Pro")
            .putString("usuario", "luis")
            .putString("llave_servicio", matriz.firma.llavePublica)
            .putLong("alta_en", 5L)
            .commit()
        carpeta.mkdirs()
        File(carpeta, "copia.json").writeText(
            mx.papeleriapos.android.caja.JsonDelPos.encodeToString(
                mx.papeleriapos.android.copia.Copia.serializer(),
                mx.papeleriapos.android.copia.Copia.dePaquete(
                    JsonDelPos.decodeFromString(PaqueteDto.serializer(), CopiaTest.textoDelPaquete()), 3L,
                ),
            ),
        )
        c.precargar()
        val e = espera { c.estado.first { it.leida } }
        assertEquals(matriz.cajaId, e.alta!!.caja.id)
        assertEquals("Celular de Luis", e.alta!!.caja.nombre)
        assertEquals(5L, e.alta!!.altaEn)
        assertEquals("La copia de antes se lee", 41L, c.copia.value!!.version)
        assertFalse("Ya no quedan los campos de antes", prefs.contains("llave"))
        // Enlazar la sucursal 2 no toca la de antes.
        enlazar(sucursal2)
        assertEquals(listOf("Matriz", "Sucursal 2"), c.estado.value.enlaces.map { it.sucursal })
        assertEquals(41L, almacenCopia.leer(matriz.cajaId)!!.version)
    }

    companion object {
        /** La caja de `paquete-prueba.json`. */
        const val CAJA_DEL_PAQUETE = "0190c5a2-0000-7000-8000-00000000c0a1"
    }
}
