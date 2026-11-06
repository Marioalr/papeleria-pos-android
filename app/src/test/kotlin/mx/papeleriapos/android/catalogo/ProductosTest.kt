package mx.papeleriapos.android.catalogo

import mx.papeleriapos.escaner.nucleo.Simbologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Los productos de prueba (assets/productos-prueba.csv), sus precios con mayoreo y la búsqueda por nombre. */
class ProductosTest {

    /** El archivo de la app: las pruebas de Gradle corren desde app/. */
    private val archivo = listOf("src/main/assets/productos-prueba.csv", "app/src/main/assets/productos-prueba.csv")
        .map(::File).first { it.isFile }

    private val catalogo by lazy { CatalogoDeProductos.deCsv(archivo.readText()) }

    private fun p(clave: String) = catalogo.porClave(clave)!!

    // ---- El archivo ----

    @Test
    fun `el archivo trae los productos del prototipo y los dos rapidos`() {
        assertEquals(31, catalogo.tamano)
        assertEquals(catalogo.productos.size, catalogo.productos.map { it.clave }.toSet().size)
        val rapidos = catalogo.productos.filter { it.rapido }.map { it.clave }
        assertEquals(listOf("RAPIDO-COPIA", "RAPIDO-CURP"), rapidos)
        assertNull("Los rápidos no llevan inventario", p("RAPIDO-COPIA").existencia)
    }

    @Test
    fun `los codigos son ficticios y con su digito verificador`() {
        for (prod in catalogo.productos.filter { it.clave.all(Char::isDigit) }) {
            assertTrue(prod.clave, prod.clave.startsWith("750999") || prod.clave.startsWith("299999"))
            assertTrue(prod.clave, mx.papeleriapos.escaner.nucleo.Gtin.esValido(prod.clave))
        }
    }

    @Test
    fun `el cuaderno trae su mayoreo y la caja de lapices sus piezas`() {
        val cuaderno = p("7509990100019")
        assertEquals("Cuaderno profesional cuadro chico 100 hojas", cuaderno.nombre)
        assertEquals("Escolar", cuaderno.departamento)
        assertEquals(4600L, cuaderno.precio)
        assertEquals(Escala(12, 4100), cuaderno.mayoreo)
        assertEquals(8.0, cuaderno.existencia!!, 0.0)
        val caja = p("7509990100200")
        assertEquals(12, caja.piezas)
        assertEquals("7509990100026", caja.base)
        assertEquals(5400L, caja.precio)
        val liston = p("7509990100217")
        assertEquals(Unidad.METRO, liston.unidad)
        assertEquals(85.5, liston.existencia!!, 0.0)
        assertEquals("Regreso a clases: lleva 3 y paga 2", p("7509990100033").promocion)
    }

    @Test
    fun `un archivo ajeno o con renglones rotos no truena`() {
        assertEquals(0, CatalogoDeProductos.deCsv("hola,mundo\n1,2").tamano)
        val roto = archivo.readText() + "x\n,Sin clave,Escolar,1.00,,,,pieza,,,,,\n750,Sin precio,Escolar,,,,,pieza,,,,,\n"
        assertEquals(31, CatalogoDeProductos.deCsv(roto).tamano)
    }

    // ---- El precio ----

    @Test
    fun `el mayoreo entra al llegar a la escala, como en la caja`() {
        val lapiz = p("7509990100026")
        assertEquals(550L, lapiz.precioUnitario(11))
        assertFalse(lapiz.esMayoreo(11))
        assertEquals(450L, lapiz.precioUnitario(12))
        assertTrue(lapiz.esMayoreo(12))
        assertEquals(5400L, lapiz.importe(12))
        assertEquals(6050L, lapiz.importe(11))
    }

    @Test
    fun `nunca mas caro que el menudeo aunque la escala este mal capturada`() {
        val raro = Producto("1", "Raro", "Escolar", listOf(Escala(1, 500), Escala(10, 600)), 3.0)
        assertEquals(500L, raro.precioUnitario(20))
        assertFalse(raro.esMayoreo(20))
    }

    @Test
    fun `los importes se escriben como en la caja`() {
        assertEquals("$0.00", pesos(0))
        assertEquals("$46.00", pesos(4600))
        assertEquals("$5.50", pesos(550))
        assertEquals("$1,234.50", pesos(123_450))
        assertEquals("$1,000,000.00", pesos(100_000_000))
        assertEquals("−$3.05", pesos(-305))
        assertEquals("85.5", cantidadCorta(85.5))
        assertEquals("140", cantidadCorta(140.0))
        assertEquals("1 pieza", Unidad.PIEZA.de(1))
        assertEquals("12 piezas", Unidad.PIEZA.de(12))
        assertEquals("10 metros", Unidad.METRO.de(10))
    }

    @Test
    fun `la caja se cobra con su precio propio pero nunca mas cara que sus piezas, como en la caja`() {
        val caja = p("7509990100200")
        assertEquals(5400L, caja.importe(1))
        assertEquals(5400L, caja.precioUnitario(1))
        assertEquals(10800L, caja.importe(2))
        // Una caja capturada más cara que sus 12 piezas a menudeo ($66.00) se cobra por sus piezas (CalculoPrecio del POS).
        val cara = CatalogoDeProductos(
            listOf(
                Producto("7509990100026", "Lápiz", "Escolar", listOf(Escala(1, 550)), 140.0),
                Producto("7509990100200", "Lápiz · caja con 12", "Escolar", listOf(Escala(1, 7000)), 140.0, piezas = 12, base = "7509990100026"),
            ),
        )
        assertEquals(6600L, cara.porClave("7509990100200")!!.importe(1))
    }

    // ---- Lo escaneado ----

    @Test
    fun `lo escaneado se encuentra con las variantes de upc y ean`() {
        val c = CatalogoDeProductos(
            listOf(
                Producto("099999291452", "Plumón ficticio", "Oficina", listOf(Escala(1, 1900)), 4.0),
                Producto("7509990100019", "Cuaderno", "Escolar", listOf(Escala(1, 4600)), 8.0),
            ),
        )
        // El UPC-A guardado de 12 dígitos llega de la cámara como el EAN-13 con un 0 adelante.
        assertEquals("099999291452", c.porLectura("0099999291452", Simbologia.EAN_13)!!.clave)
        assertEquals("7509990100019", c.porLectura("7509990100019", Simbologia.EAN_13)!!.clave)
        assertNull(c.porLectura("7509990100194", Simbologia.EAN_13))
        // Un código interno (Code 128) que está tal cual.
        val conInterno = CatalogoDeProductos(listOf(Producto("ZZP0042x", "Interno", "Escolar", listOf(Escala(1, 100)), 1.0)))
        assertEquals("ZZP0042x", conInterno.porLectura("ZZP0042x", Simbologia.CODE_128)!!.clave)
    }

    // ---- Buscar ----

    @Test
    fun `pedazos de palabra en cualquier orden y sin acentos`() {
        assertEquals("Papel lustre azul", catalogo.buscar("lus az").first().nombre)
        assertEquals("Papel lustre azul", catalogo.buscar("AZ lus").first().nombre)
        assertTrue(catalogo.buscar("lapiz").all { it.nombre.startsWith("Lápiz") })
        assertTrue(catalogo.buscar("xyzw").isEmpty())
    }

    @Test
    fun `un error de dedo tambien encuentra, como en la caja`() {
        assertTrue(catalogo.buscar("lapis").any { it.nombre == "Lápiz HB No. 2" })
        assertTrue(catalogo.buscar("cuadreno").any { it.nombre.startsWith("Cuaderno") })
    }

    @Test
    fun `lo elegido antes con ese texto sale primero`() {
        val caja = "7509990100200"
        assertTrue(catalogo.buscar("lapiz").first().clave != caja)
        assertEquals(caja, catalogo.buscar("lapiz", elegido = caja).first().clave)
    }

    @Test
    fun `sin escribir nada salen los rapidos y luego lo mas vendido`() {
        val r = catalogo.buscar("")
        assertEquals(listOf("RAPIDO-COPIA", "RAPIDO-CURP"), r.take(2).map { it.clave })
        assertEquals("Lápiz HB No. 2", r[2].nombre)
        assertEquals(30, r.size)
        assertEquals(5, catalogo.buscar("", maximo = 5).size)
    }

    @Test
    fun `al escribir lo mas vendido va primero`() {
        val r = catalogo.buscar("papel")
        assertEquals(listOf("Papel lustre azul", "Papel lustre rojo", "Papel china verde", "Papel para regalo, pliego").sorted(), r.map { it.nombre }.sorted())
        assertEquals(r.sortedByDescending { it.vendidos }, r)
    }
}
