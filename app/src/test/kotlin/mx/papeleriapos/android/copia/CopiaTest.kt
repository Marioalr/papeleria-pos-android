package mx.papeleriapos.android.copia

import mx.papeleriapos.android.caja.CambiosDto
import mx.papeleriapos.android.caja.CodigoLocalDto
import mx.papeleriapos.android.caja.ExistenciaLocalDto
import mx.papeleriapos.android.caja.JsonDelPos
import mx.papeleriapos.android.caja.PaqueteDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** La copia del catálogo: lo que se lee del paquete, lo que nunca se guarda y cómo se le aplican los cambios. */
class CopiaTest {
    private val paquete: PaqueteDto = JsonDelPos.decodeFromString(PaqueteDto.serializer(), textoDelPaquete())
    private val copia = Copia.dePaquete(paquete, ahora = 1_000L)

    @Test
    fun `el paquete se lee con sus versiones y sin lo que el celular no debe cargar`() {
        assertEquals(41L, copia.version)
        assertEquals(7L, copia.versionExistencias)
        assertEquals("0190c5a2-0000-7000-8000-00000000ba5e", copia.base)
        assertEquals(5, copia.formato)
        assertEquals(3L, copia.versionVendidos)
        assertEquals(2L, copia.versionElegidos)
        assertEquals("ABCDEF0123456789", copia.huellaPromociones)
        assertEquals("Celular de Luis", copia.caja.nombre)
        assertEquals("Papelería de prueba", copia.nombreTienda)
        assertEquals(6, copia.productos.size)
        assertEquals(8, copia.codigos.size)
        assertEquals(listOf("BIC", "SCRIBE"), copia.marcas)
        assertEquals(1, copia.sinonimos.size)
        assertEquals(2, copia.promociones.size)
        // Los usuarios con el hash de su PIN, sus permisos y reglas, y los clientes: ni en memoria ni en el disco.
        val guardado = JsonDelPos.encodeToString(Copia.serializer(), copia)
        for (prohibido in listOf("pinHash", "usuarios", "AQIDBA", "Cliente Ficticio", "clientes", "registrados", "permisos", "reglas", "saldo", "ultimoFolio", "encabezado")) {
            assertFalse("No debe ir: $prohibido", prohibido in guardado)
        }
        // Y se vuelve a leer igual.
        assertEquals(copia, JsonDelPos.decodeFromString(Copia.serializer(), guardado))
    }

    @Test
    fun `sabe cuando el latido trae otras versiones`() {
        assertFalse(copia.desactualizada(41, 7, "0190C5A2-0000-7000-8000-00000000BA5E", 5))
        assertFalse("Un servicio de antes no dice existencias ni base", copia.desactualizada(41, null, null, null))
        assertTrue(copia.desactualizada(42, 7, copia.base, 5))
        assertTrue(copia.desactualizada(41, 8, copia.base, 5))
        assertTrue(copia.desactualizada(41, 7, "otra-base", 5))
        assertTrue(copia.desactualizada(41, 7, copia.base, 6))
    }

    @Test
    fun `los cambios reemplazan, agregan, quitan y mueven codigos como la caja de windows`() {
        val cuaderno = copia.productos[0]
        val cambios = CambiosDto(
            version = 42,
            versionExistencias = 9,
            // El cuaderno cambia de nombre y llega uno nuevo.
            productos = listOf(
                cuaderno.copy(descripcion = "Cuaderno profesional cuadro chico 100 h", existencia = 5.0),
                cuaderno.copy(varianteId = "0190c5a2-0000-7000-8000-000000000099", descripcion = "Nuevo", codigo = "7509990100293", existencia = 1.0),
            ),
            // El listón se da de baja.
            quitados = listOf("0190C5A2-0000-7000-8000-000000000004"),
            // Los códigos del cuaderno (ahora con uno más) y el del nuevo, que antes era del plumón 5.
            codigos = listOf(
                CodigoLocalDto("7509990100019", cuaderno.varianteId, cuaderno.presentacion.id),
                CodigoLocalDto("7509990100057", cuaderno.varianteId, cuaderno.presentacion.id),
                CodigoLocalDto("7509990100293", "0190c5a2-0000-7000-8000-000000000099", cuaderno.presentacion.id),
                CodigoLocalDto("099999291452", "0190c5a2-0000-7000-8000-000000000099", cuaderno.presentacion.id),
            ),
            existencias = listOf(ExistenciaLocalDto("0190c5a2-0000-7000-8000-000000000002", 100.0), ExistenciaLocalDto("0190c5a2-0000-7000-8000-000000000003", 7.0)),
            marcas = listOf("BIC"),
            versionVendidos = 4,
            huellaPromociones = "NUEVA",
            promociones = emptyList(),
        )
        val nueva = copia.aplicar(cambios, ahora = 2_000L)
        assertEquals(42L, nueva.version)
        assertEquals(9L, nueva.versionExistencias)
        assertEquals(4L, nueva.versionVendidos)
        assertEquals(2L, nueva.versionElegidos)
        assertEquals("NUEVA", nueva.huellaPromociones)
        assertEquals(2_000L, nueva.guardadaEn)
        val nombres = nueva.productos.map { it.descripcion }
        assertEquals(6, nueva.productos.size)
        assertTrue("Cuaderno profesional cuadro chico 100 h" in nombres)
        assertTrue("Nuevo" in nombres)
        assertFalse("Listón satín 1 cm rojo" in nombres)
        assertEquals("El que llegó trae su existencia", 5.0, nueva.productos[0].existencia!!, 0.0)
        assertEquals("La existencia que cambió", 100.0, nueva.productos.first { it.descripcion == "Lápiz HB No. 2" }.existencia!!, 0.0)
        assertNull("Lo que no lleva inventario sigue sin existencia", nueva.productos.first { it.descripcion == "Copia B/N" }.existencia)
        val codigos = nueva.codigos.groupBy({ it.codigo }, { it.varianteId.lowercase() })
        assertEquals(listOf("0190c5a2-0000-7000-8000-000000000099"), codigos["099999291452"])
        assertEquals(2, codigos.getValue("7509990100040").size)
        assertNull("Los códigos del quitado se van", codigos["7509990100217"])
        assertEquals(listOf(cuaderno.varianteId.lowercase()), codigos["7509990100057"])
        assertEquals(listOf("BIC"), nueva.marcas)
        assertEquals("Lo que no vino se queda", copia.sinonimos, nueva.sinonimos)
        assertEquals(emptyList<Any>(), nueva.promociones)
    }

    @Test
    fun `los elegidos nuevos pisan los de antes con el mismo texto`() {
        val cambios = CambiosDto(version = 41, versionExistencias = 7, elegidos = listOf(mx.papeleriapos.android.caja.ElegidoDto("plumon", "x"), mx.papeleriapos.android.caja.ElegidoDto("cuad", "y")), versionElegidos = 5)
        val nueva = copia.aplicar(cambios, 3_000L)
        assertEquals(mapOf("plumon" to "x", "cuad" to "y"), nueva.elegidos.associate { it.texto to it.varianteId })
        assertEquals(5L, nueva.versionElegidos)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `con paqueteCompleto no se aplica nada`() {
        copia.aplicar(CambiosDto(paqueteCompleto = "otra base"), 0L)
    }

    companion object {
        /** El paquete de prueba, con usuarios y clientes ficticios que el celular tiene que descartar. */
        fun textoDelPaquete(): String =
            listOf("src/test/resources/paquete-prueba.json", "app/src/test/resources/paquete-prueba.json").map(::File).first { it.isFile }.readText()
    }
}
