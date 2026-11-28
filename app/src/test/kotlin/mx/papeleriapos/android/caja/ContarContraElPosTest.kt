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
import mx.papeleriapos.android.lista.ListaLecturas
import mx.papeleriapos.android.copia.ProductosDeLaCopia
import mx.papeleriapos.android.principal.IdDeConteo
import mx.papeleriapos.android.principal.renglonesDelConteo
import mx.papeleriapos.android.principal.utc
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.pos.precio.Fecha
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * El conteo contra un servicio del POS de verdad con la base ficticia, nunca contra la tienda. Solo corre con `POS_URL` y,
 * para aceptarlo del lado de la caja, `POS_CAJA` y `POS_LLAVE_CAJA` (una caja con turno abierto de alguien con «Conteo
 * completo de un departamento») y `POS_ACEPTA` (el id de ese usuario):
 *
 * `POS_URL=http://127.0.0.1:5080 POS_CAJA=… POS_LLAVE_CAJA=… POS_ACEPTA=… ./gradlew :app:testDebugUnitTest --tests '*ContarContraElPos*'`
 */
@RunWith(AndroidJUnit4::class)
class ContarContraElPosTest {
    private val url = System.getenv("POS_URL")
    private val cajaQueAcepta = System.getenv("POS_CAJA")
    private val llaveDeLaCaja = System.getenv("POS_LLAVE_CAJA")
    private val acepta = System.getenv("POS_ACEPTA")

    @Test
    fun `se manda, no se duplica, la caja lo acepta y el celular ve como quedo`() = runBlocking {
        assumeTrue("Sin POS_URL, POS_CAJA, POS_LLAVE_CAJA y POS_ACEPTA no corre", url != null && cajaQueAcepta != null && llaveDeLaCaja != null && acepta != null)
        val d = RedLocal.direccionDe(url!!)!!
        val app: Context = ApplicationProvider.getApplicationContext()
        val carpeta = File(app.cacheDir, "copia-conteo-${System.nanoTime()}")
        val prefs = app.getSharedPreferences("conexion-conteo-${System.nanoTime()}", Context.MODE_PRIVATE)
        val c = Conexion(AlmacenAlta(prefs, Cifrador.Transparente), AlmacenCopia(carpeta), ClienteCaja(), "Prueba JVM", hilo = Dispatchers.Unconfined)
        val s = c.opcionesDeAlta(d, "dueno", "3333")[0]
        val alta = c.darDeAlta(d, "dueno", "3333", s.id, "Celular que cuenta", Conexion.seriePropuesta(s.cajas))
        val copia = withTimeout(60_000) { c.copia.filterNotNull().first() }
        val catalogo = ProductosDeLaCopia.armar(copia, Fecha.de(2026, 11, 27))

        val cajas = c.cajasParaContar()
        println("Cajas: " + cajas.joinToString { "${it.nombre} recibe=${it.recibe} ${it.codigo ?: ""} ${it.porQue ?: ""}" })
        val destino = cajas.single { it.id.equals(cajaQueAcepta, ignoreCase = true) }
        assertTrue("La Caja 1 recibe: tiene turno de quien puede aceptar", destino.recibe)

        // Dos productos que llevan inventario, por su código de barras, como al escanearlos.
        val codigos = copia.codigos.filter { cod -> copia.productos.any { it.varianteId == cod.varianteId && it.llevaInventario } && cod.codigo.length == 13 }
            .distinctBy { it.varianteId }.take(2)
        var lista = ListaLecturas()
        for ((i, cod) in codigos.withIndex()) {
            val p = catalogo.porLectura(cod.codigo, Simbologia.EAN_13)!!
            lista = lista.sumar(cod.codigo, Simbologia.EAN_13, System.currentTimeMillis() - 60_000, cantidad = 7 + i, clave = p.clave)
        }
        val renglones = renglonesDelConteo(lista, catalogo)
        val ids = IdDeConteo.enMemoria()
        val solicitud = SolicitudMandarConteo(ids.de(renglones), destino.id, utc(System.currentTimeMillis() - 60_000), enviadoUtc = utc(System.currentTimeMillis()), renglones = renglones)
        val recibida = c.mandarConteo(solicitud)
        println("Mandado: ${recibida.estado} a ${recibida.caja}, ${recibida.productos} productos")
        assertEquals("recibida", recibida.estado)
        assertEquals(2, recibida.productos)

        // La respuesta se perdió: otra vez con el mismo id no se duplica.
        val otraVez = c.mandarConteo(solicitud.copy(id = ids.de(renglones)))
        assertEquals(recibida.id, otraVez.id)
        assertEquals(1, c.conteosMandados().count { it.id == recibida.id })

        // En la caja, quien puede la acepta con un cambio: el primero queda en 5.
        val primero = renglones[0].varianteId!!
        val cuerpo = """{"usuarioId":"$acepta","renglones":[{"varianteId":"$primero","cantidadMilesimas":5000},{"varianteId":"${renglones[1].varianteId}","cantidadMilesimas":${renglones[1].cantidadMilesimas}}]}"""
        val r = OkHttpClient().newCall(
            Request.Builder().url("${d.url}api/cajas/$cajaQueAcepta/conteos-recibidos/${recibida.id}/aceptar")
                .header(ClienteCaja.ENCABEZADO_LLAVE, llaveDeLaCaja!!)
                .post(cuerpo.toRequestBody("application/json".toMediaType())).build(),
        ).execute()
        println("Aceptar en la caja: ${r.code} ${r.body?.string()?.take(300)}")
        assertTrue(r.isSuccessful)

        val como = c.conteosMandados().first { it.id == recibida.id }
        println("Cómo quedó: ${como.estado}, resolvió ${como.resolvio}; " + como.renglones.joinToString { "${it.descripcion}: ${it.contado} → ${it.aceptado} (${it.nota})" })
        assertEquals("aceptada-con-cambios", como.estado)
        assertEquals(5.0, como.renglones.first { it.varianteId.equals(primero, ignoreCase = true) }.aceptado!!, 0.0)
        assertTrue(alta.caja.id.isNotBlank())
        carpeta.deleteRecursively()
        Unit
    }
}
