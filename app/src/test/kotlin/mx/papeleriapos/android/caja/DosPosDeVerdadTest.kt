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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Dos sucursales contra dos servicios del POS de verdad, cada uno con su base (docs/pruebas.md, «La conexión con el POS»):
 * solo corre con `POS_URL` y `POS_URL_2`, contra bases ficticias, nunca contra la tienda. Da de alta una caja nueva en
 * cada una, cambia de una a otra y cotiza en la segunda con su copia.
 *
 * `POS_URL=http://192.168.1.10:5080 POS_URL_2=http://192.168.1.10:5090 ./gradlew :app:testDebugUnitTest --tests '*DosPosDeVerdad*'`
 */
@RunWith(AndroidJUnit4::class)
class DosPosDeVerdadTest {
    private val url1 = System.getenv("POS_URL")
    private val url2 = System.getenv("POS_URL_2")
    private val usuario = System.getenv("POS_USUARIO") ?: "dueno"
    private val pin = System.getenv("POS_PIN") ?: "3333"

    @Test
    fun `dos sucursales de verdad, cada una con su alta y su copia`() = runBlocking {
        assumeTrue("Sin POS_URL y POS_URL_2 no corre", url1 != null && url2 != null)
        val d1 = RedLocal.direccionDe(url1!!)!!
        val d2 = RedLocal.direccionDe(url2!!)!!
        val app: Context = ApplicationProvider.getApplicationContext()
        val carpeta = File(app.cacheDir, "copias-de-verdad-${System.nanoTime()}")
        val prefs = app.getSharedPreferences("conexion-de-verdad-${System.nanoTime()}", Context.MODE_PRIVATE)
        val almacenAlta = AlmacenAlta(prefs, Cifrador.Transparente)
        val cliente = ClienteCaja()
        var encontradas = listOf(d1, d2)
        val c = Conexion(almacenAlta, AlmacenCopia(carpeta), cliente, "Prueba JVM", descubrir = { encontradas }, hilo = Dispatchers.Unconfined)
        suspend fun enlazar(d: Direccion): Alta {
            val s = c.opcionesDeAlta(d, usuario, pin)[0]
            val a = c.darDeAlta(d, usuario, pin, s.id, "Celular de prueba", Conexion.seriePropuesta(s.cajas))
            withTimeout(60_000) { c.copia.filterNotNull().first { it.caja.id.equals(a.caja.id, ignoreCase = true) } }
            return a
        }
        val a1 = enlazar(d1)
        val copia1 = c.copia.value!!
        val a2 = enlazar(d2)
        val copia2 = c.copia.value!!
        println("Matriz: ${a1.caja.sucursal}, ${copia1.productos.size} productos; la otra: ${a2.caja.sucursal}, ${copia2.productos.size} productos")
        assertEquals(2, c.estado.value.enlaces.size)
        assertNotEquals("Cada base tiene sus ids", copia1.productos.map { it.varianteId }.toSet(), copia2.productos.map { it.varianteId }.toSet())

        // De regreso a la primera, sin red: su copia del disco.
        assertTrue(c.cambiarA(a1.caja.id))
        assertEquals(copia1.version, c.copia.value!!.version)
        c.latir()
        assertEquals("La primera prueba quién es y contesta", Red.BUENA, c.estado.value.red)

        // «¿Estás en la otra? Cambiar», encontrándola por UDP.
        encontradas = listOf(d1, d2)
        val nueva = c.buscarYCambiarA(a2.caja.id)
        assertEquals(d2, nueva.direccion)
        val codigo = c.copia.value!!.codigos.first { it.codigo.length == 13 }
        val cotizacion = c.cotizar(SolicitudCotizar(listOf(RenglonMandado(codigo.varianteId, codigo.presentacionId, codigo.codigo, 1000))))
        println("Cotizado en ${a2.caja.sucursal}: ${cotizacion.totalCentavos} centavos")
        assertTrue(cotizacion.totalCentavos > 0)
        carpeta.deleteRecursively()
        Unit
    }
}
