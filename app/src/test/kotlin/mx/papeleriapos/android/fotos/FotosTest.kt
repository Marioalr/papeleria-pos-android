package mx.papeleriapos.android.fotos

import kotlinx.coroutines.runBlocking
import mx.papeleriapos.android.caja.CambiosDto
import mx.papeleriapos.android.caja.CanalDeFotos
import mx.papeleriapos.android.caja.ClienteCaja
import mx.papeleriapos.android.caja.Direccion
import mx.papeleriapos.android.caja.ErrorDelPos
import mx.papeleriapos.android.caja.FotoDto
import mx.papeleriapos.android.caja.FotoGuardadaDto
import mx.papeleriapos.android.caja.JsonDelPos
import mx.papeleriapos.android.caja.PaqueteDto
import mx.papeleriapos.android.caja.SolicitudFoto
import mx.papeleriapos.android.copia.Copia
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

/** La foto del producto (docs/celular-como-caja.md del POS, paso 12): la copia, el cliente, lo guardado y la lista. */
class FotosTest {
    @get:Rule
    val carpeta = TemporaryFolder()

    private val cuaderno = "0190c5a2-0000-7000-8000-000000000001"
    private val lapiz = "0190c5a2-0000-7000-8000-000000000002"

    private fun paquete(): PaqueteDto = JsonDelPos.decodeFromString(
        PaqueteDto.serializer(),
        listOf("src/test/resources/paquete-prueba.json", "app/src/test/resources/paquete-prueba.json").map(::File).first { it.isFile }.readText(),
    )

    private fun copia(vararg fotos: Pair<String, String>) = Copia.dePaquete(paquete().copy(fotos = fotos.map { FotoDto(it.first, it.second) }), 1L)

    // ---- La copia ----

    @Test
    fun `la copia dice que productos tienen foto, sin importar las mayusculas de su id`() {
        val c = copia(cuaderno.uppercase() to "aaaa")
        assertEquals("aaaa", c.huellaDe(cuaderno))
        assertEquals("aaaa", c.huellaDe(cuaderno.uppercase()))
        assertNull(c.huellaDe(lapiz))
        assertEquals("bbbb", c.conFoto(lapiz, "bbbb").huellaDe(lapiz))
        assertNull(c.sinFoto(cuaderno).huellaDe(cuaderno))
        assertTrue("Quitar la que no tiene no cambia nada", c.sinFoto(lapiz) === c)
    }

    @Test
    fun `los cambios traen solo las fotos nuevas y las quitadas, encima de las de la copia`() {
        val c = copia(cuaderno to "aaaa", lapiz to "bbbb")
        val sinCampos = c.aplicar(CambiosDto(version = c.version + 1), 2L)
        assertEquals("Sin los campos, las fotos no cambiaron", c.fotos, sinCampos.fotos)
        val con = c.aplicar(CambiosDto(version = c.version + 1, fotos = listOf(FotoDto(cuaderno, "cccc")), fotosQuitadas = listOf(lapiz.uppercase())), 2L)
        assertEquals(mapOf(cuaderno to "cccc"), con.fotos)
        // Un producto que se quitó del catálogo se lleva su foto.
        assertNull(c.aplicar(CambiosDto(version = c.version + 1, quitados = listOf(cuaderno)), 2L).huellaDe(cuaderno))
    }

    @Test
    fun `la copia guardada por una version anterior, sin fotos, se sigue leyendo`() {
        val json = JsonDelPos.encodeToString(Copia.serializer(), copia()).replace(Regex(",?\"fotos\":\\{\\}"), "")
        assertFalse(json.contains("\"fotos\""))
        assertTrue(JsonDelPos.decodeFromString(Copia.serializer(), json).fotos.isEmpty())
    }

    // ---- El cliente ----

    @Test
    fun `poner, quitar y bajar la foto van como los espera el servicio`() = runBlocking {
        val servidor = MockWebServer().apply { start() }
        try {
            val cliente = ClienteCaja(msRespuesta = 2_000)
            val direccion = Direccion("127.0.0.1", servidor.port)
            servidor.enqueue(MockResponse().setBody("""{"varianteId": "$cuaderno", "huella": "f3386238ad683036", "ancho": 240, "alto": 180, "bytes": 2044, "jpegBase64": "/9j/4AAQ"}"""))
            val guardada = cliente.ponerFoto(direccion, "LLAVE", cuaderno, SolicitudFoto("QUJD"))
            assertEquals("f3386238ad683036", guardada.huella)
            assertEquals(240, guardada.ancho)
            val put = servidor.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("/api/fotos/$cuaderno", put.path)
            assertEquals("LLAVE", put.getHeader(ClienteCaja.ENCABEZADO_LLAVE))
            assertEquals("Sin usuario: basta la llave del celular", """{"imagenBase64":"QUJD"}""", put.body.readUtf8())

            servidor.enqueue(MockResponse().setResponseCode(204))
            cliente.quitarFoto(direccion, "LLAVE", cuaderno)
            val quitar = servidor.takeRequest()
            assertEquals("POST", quitar.method)
            assertEquals("/api/fotos/$cuaderno/quitar", quitar.path)
            assertEquals("{}", quitar.body.readUtf8())

            servidor.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(byteArrayOf(1, 2, 3))))
            assertArrayEquals(byteArrayOf(1, 2, 3), cliente.bajarFoto(direccion, "LLAVE", cuaderno))
            assertEquals("/api/fotos/$cuaderno", servidor.takeRequest().path)
            servidor.enqueue(MockResponse().setResponseCode(404).setBody("""{"mensaje": "Ese producto no tiene foto."}"""))
            assertNull("Sin foto no es un error", cliente.bajarFoto(direccion, "LLAVE", cuaderno))

            servidor.enqueue(MockResponse().setResponseCode(400).setBody("""{"mensaje":"La caja principal está preparando otras fotos: vuelve a intentarlo en un momento.","codigo":"ocupada"}"""))
            try {
                cliente.ponerFoto(direccion, "LLAVE", cuaderno, SolicitudFoto("QUJD"))
                fail("Debió decir que está ocupada")
            } catch (e: ErrorDelPos) {
                assertEquals("ocupada", e.codigo)
            }
        } finally {
            servidor.shutdown()
        }
    }

    // ---- Lo guardado en el celular ----

    private class Canal : CanalDeFotos {
        var bajadas = 0
        val puestas = ArrayList<Pair<String, Int>>()
        val quitadas = ArrayList<String>()
        var enLaCaja: ByteArray? = byteArrayOf(9, 9)

        override suspend fun ponerFoto(varianteId: String, jpeg: ByteArray): FotoGuardadaDto {
            puestas.add(varianteId to jpeg.size)
            return FotoGuardadaDto(varianteId, "nueva", 240, 180, 3, Base64.getEncoder().encodeToString(byteArrayOf(7, 7, 7)))
        }

        override suspend fun quitarFoto(varianteId: String) {
            quitadas.add(varianteId)
        }

        override suspend fun bajarFoto(varianteId: String): ByteArray? {
            bajadas++
            return enLaCaja
        }
    }

    @Test
    fun `la foto se baja una vez y despues sale del celular, y poner otra reemplaza la guardada`() = runBlocking {
        val canal = Canal()
        val almacen = AlmacenFotos(carpeta.newFolder("fotos"))
        val fotos = Fotos(canal, almacen)
        assertArrayEquals(byteArrayOf(9, 9), fotos.imagen(cuaderno, "vieja"))
        assertArrayEquals(byteArrayOf(9, 9), fotos.imagen(cuaderno, "vieja"))
        assertEquals("La segunda vez ya no pide a la caja principal", 1, canal.bajadas)
        // Poner otra: queda la que contestó la caja principal (la de 240 px), con su huella, y la de antes se borra.
        assertEquals("nueva", fotos.poner(cuaderno, ByteArray(500)).huella)
        assertEquals(listOf(cuaderno to 500), canal.puestas)
        assertArrayEquals(byteArrayOf(7, 7, 7), almacen.leer(cuaderno, "nueva"))
        assertNull(almacen.leer(cuaderno, "vieja"))
        fotos.quitar(cuaderno)
        assertEquals(listOf(cuaderno), canal.quitadas)
        assertNull(almacen.leer(cuaderno, "nueva"))
        // Sin red y sin tenerla guardada: no hay imagen, y no truena.
        canal.enLaCaja = null
        assertNull(fotos.imagen(lapiz, "x"))
    }

    @Test
    fun `lo que llega de la caja nunca arma una ruta fuera de la carpeta`() {
        val raiz = carpeta.newFolder("fotos2")
        val almacen = AlmacenFotos(raiz)
        almacen.guardar("../../fuera", "../x", byteArrayOf(1))
        assertTrue(raiz.listFiles()!!.all { it.parentFile == raiz && !it.name.contains("..") && !it.name.contains("/") })
        assertEquals(1, raiz.listFiles()!!.size)
    }

    // ---- La imagen y la lista ----

    @Test
    fun `la imagen se achica a 960 por su lado mas largo, sin deformarla ni agrandarla`() {
        assertEquals(960 to 720, PreparacionDeFoto.tamano(4000, 3000))
        assertEquals(720 to 960, PreparacionDeFoto.tamano(3000, 4000))
        assertEquals(960 to 960, PreparacionDeFoto.tamano(2000, 2000))
        assertEquals("Una más chica no se agranda", 640 to 480, PreparacionDeFoto.tamano(640, 480))
        assertEquals(960 to 1, PreparacionDeFoto.tamano(20_000, 10))
    }

    @Test
    fun `la lista ensena los que no tienen foto, busca por pedazos o por codigo y pone primero lo ultimo escaneado`() {
        val c = copia(cuaderno to "aaaa")
        val sinFoto = productosParaFotos(c, "", soloSinFoto = true, ultimo = null)
        assertTrue(sinFoto.none { it.varianteId.equals(cuaderno, ignoreCase = true) })
        assertEquals(c.productos.size - 1, sinFoto.size)
        assertEquals("En orden alfabético", sinFoto.map { sinAcentos(it.descripcion) }.sorted(), sinFoto.map { sinAcentos(it.descripcion) })
        assertEquals(c.productos.size, productosParaFotos(c, "", soloSinFoto = false, ultimo = null).size)
        // Por pedazos de palabra, sin acentos; y por su código.
        assertEquals(listOf("Lápiz HB No. 2"), productosParaFotos(c, "lapi hb", soloSinFoto = true, ultimo = null).map { it.descripcion })
        assertEquals(listOf("Lápiz HB No. 2"), productosParaFotos(c, "7509990100026", soloSinFoto = true, ultimo = null).map { it.descripcion })
        // Lo último escaneado va primero, aunque ya tenga foto.
        val conUltimo = productosParaFotos(c, "", soloSinFoto = true, ultimo = cuaderno.uppercase())
        assertEquals("Cuaderno profesional cuadro chico 100 hojas", conUltimo.first().descripcion)
        assertEquals(c.productos.size, conUltimo.size)
    }
}
