package mx.papeleriapos.android.catalogo

import mx.papeleriapos.android.ajustes.textoCatalogo
import mx.papeleriapos.escaner.nucleo.Catalogo
import mx.papeleriapos.escaner.nucleo.Coincidencia
import mx.papeleriapos.escaner.nucleo.Confirmador
import mx.papeleriapos.escaner.nucleo.Gtin
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.nucleo.Variante
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

class CatalogoPruebaTest {
    @get:Rule val temporal = TemporaryFolder()

    /** El de los assets (las pruebas corren desde la carpeta del módulo). */
    private val ejemplo = File("src/main/assets/${CatalogoPrueba.ARCHIVO_EJEMPLO}")

    private val carpeta get() = File(temporal.root, "catalogo")

    private fun almacen() = AlmacenCatalogo(carpeta) { ejemplo.inputStream() }

    private fun leido(texto: String, s: Simbologia) = Confirmador.normalizar(Lectura(texto, s, null))!!

    // ---- El catálogo de ejemplo ----

    @Test
    fun `el de ejemplo trae un caso por variante`() {
        val c = Catalogo.deTexto(ejemplo.readText())
        fun tal(clave: String) = Coincidencia(clave, Variante.TAL_CUAL)
        // Como los da la cámara (ya normalizados, igual con ML Kit y zxing-cpp).
        assertEquals(tal("7509993456786"), c.buscar(leido("7509993456786", Simbologia.EAN_13)))
        assertEquals(tal("099999291452"), c.buscar(leido("099999291452", Simbologia.UPC_A)))
        assertEquals(Coincidencia("99999400021", Variante.UPC_A_11), c.buscar(leido("099999400021", Simbologia.UPC_A)))
        assertEquals(Coincidencia("099994000059", Variante.UPC_E_A_UPC_A), c.buscar(leido("09999459", Simbologia.UPC_E)))
        assertEquals(tal("75099916"), c.buscar(leido("75099916", Simbologia.EAN_8)))
        assertEquals(tal("17509995000014"), c.buscar(leido("17509995000014", Simbologia.ITF)))
        assertEquals(tal("2999990000016"), c.buscar(leido("2999990000016", Simbologia.EAN_13)))
        assertEquals(tal("2999990000023"), c.buscar(leido("2999990000023", Simbologia.EAN_13)))
        for (clave in listOf("ZZ0001", "ZZ-PRUEBA-0001", "ZZP0042x")) {
            assertEquals(tal(clave), c.buscar(leido(clave, Simbologia.CODE_128)))
        }
        // Las variantes del EAN-13 que empieza con 0, con el texto de 13 como lo mandaría un lector configurado así.
        assertEquals(Coincidencia("099999291452", Variante.EAN13_A_UPC_A), c.buscar("0099999291452", Simbologia.EAN_13))
        assertEquals(Coincidencia("99999400038", Variante.EAN13_A_UPC_A_11), c.buscar("0099999400038", Simbologia.EAN_13))
        // La cámara lo da como UPC-A: llega a la misma clave por el paso 2.
        assertEquals(Coincidencia("99999400038", Variante.UPC_A_11), c.buscar(leido("0099999400038", Simbologia.EAN_13)))
        // El del verificador mal no coincide con la lectura buena.
        assertTrue("7509994000996" in c)
        assertNull(c.buscar(leido("7509994000995", Simbologia.EAN_13)))
    }

    @Test
    fun `el de ejemplo es ficticio y solo el que lo dice trae el verificador mal`() {
        val texto = ejemplo.readText()
        assertTrue(texto.startsWith("codigo,descripcion\n"))
        val filas = texto.lines().drop(1).filter { it.isNotBlank() }
        assertEquals(filas.size, Catalogo.deTexto(texto).tamano)
        for (fila in filas) {
            val (codigo, descripcion) = fila.split(',', limit = 2)
            when {
                codigo.startsWith("ZZ") -> Unit
                descripcion.startsWith("Verificador mal") -> assertFalse(codigo, Gtin.esValido(codigo))
                // Los de 11 dígitos son UPC-A sin su 0.
                codigo.length == 11 -> assertTrue(codigo, codigo.startsWith("9999") && Gtin.esValido("0$codigo"))
                else -> {
                    assertTrue(codigo, Gtin.esValido(codigo))
                    val prefijos = listOf("750999", "299999", "0999", "1750999")
                    assertTrue(codigo, prefijos.any { codigo.startsWith(it) })
                }
            }
        }
    }

    // ---- La copia en la memoria de la app ----

    @Test
    fun `sin archivo cargado usa el de ejemplo`() {
        val e = almacen().leer()
        assertEquals(OrigenCatalogo.EJEMPLO, e.origen)
        assertEquals(13, e.catalogo.tamano)
        assertNull(e.aviso)
        assertEquals("El de ejemplo · 13 códigos", textoCatalogo(e))
    }

    @Test
    fun `el archivo elegido se copia, sigue ahi al reabrir y se puede volver al de ejemplo`() {
        val a = almacen()
        val codigo = Gtin.completar("750999500001")
        val nuevo = a.copiar("productos.csv", { "Código;Descripción\n$codigo;Producto ficticio\n".byteInputStream() }, a.leer())
        assertEquals(OrigenCatalogo.ARCHIVO, nuevo.origen)
        assertEquals("productos.csv", nuevo.nombre)
        assertEquals(1, nuevo.catalogo.tamano)
        assertNull(nuevo.aviso)
        assertEquals("productos.csv · 1 código", textoCatalogo(nuevo))
        // Al reabrir la app (otro almacén, otro proceso) sigue el mismo.
        val reabierto = almacen().leer()
        assertEquals(OrigenCatalogo.ARCHIVO, reabierto.origen)
        assertEquals("productos.csv", reabierto.nombre)
        assertTrue(codigo in reabierto.catalogo)
        assertFalse("7509993456786" in reabierto.catalogo)
        // De vuelta al de ejemplo: la copia se borra.
        val deVuelta = almacen().olvidar()
        assertEquals(OrigenCatalogo.EJEMPLO, deVuelta.origen)
        assertTrue("7509993456786" in deVuelta.catalogo)
        assertEquals(emptyList<String>(), carpeta.list()?.toList().orEmpty())
        assertEquals(OrigenCatalogo.EJEMPLO, almacen().leer().origen)
    }

    @Test
    fun `un archivo sin codigos, que no se puede abrir o muy grande deja el anterior con un aviso`() {
        val a = almacen()
        val anterior = a.leer()
        val vacio = a.copiar("vacio.csv", { "codigo,descripcion\n\n".byteInputStream() }, anterior)
        assertEquals(anterior.copy(aviso = vacio.aviso), vacio)
        assertTrue(vacio.aviso!!.contains("no trae códigos"))
        val sinPermiso = a.copiar("x.csv", { throw SecurityException("sin permiso") }, anterior)
        assertEquals(anterior.catalogo, sinPermiso.catalogo)
        assertTrue(sinPermiso.aviso!!.contains("No se pudo abrir"))
        assertTrue(a.copiar("x.csv", { throw IOException() }, anterior).aviso!!.contains("No se pudo abrir"))
        val grande = ByteArray(AlmacenCatalogo.TOPE_BYTES + 1) { '7'.code.toByte() }
        val muyGrande = a.copiar("grande.csv", { ByteArrayInputStream(grande) }, anterior)
        assertEquals(anterior.catalogo, muyGrande.catalogo)
        assertTrue(muyGrande.aviso!!.contains("8 MB"))
        // Nada de eso se guardó.
        assertFalse(File(carpeta, "catalogo.txt").exists())
        assertEquals(OrigenCatalogo.EJEMPLO, almacen().leer().origen)
    }

    @Test
    fun `un CSV de Excel en Windows-1252 se lee con su encabezado`() {
        val codigo = Gtin.completar("750999500002")
        val bytes = "Código,Descripción\r\n$codigo,Lápiz ficticio\r\n".toByteArray(Charset.forName("windows-1252"))
        assertTrue(AlmacenCatalogo.decodificar(bytes).startsWith("Código"))
        val a = almacen()
        val e = a.copiar("excel.csv", { bytes.inputStream() }, a.leer())
        assertTrue(codigo in e.catalogo)
        assertEquals(1, e.catalogo.tamano)
        // En UTF-8 con BOM, igual.
        val utf8 = "﻿codigo\n$codigo\n".toByteArray(Charsets.UTF_8)
        assertEquals(1, Catalogo.deTexto(AlmacenCatalogo.decodificar(utf8)).tamano)
    }

    @Test
    fun `lo que dice Ajustes mientras carga`() {
        assertEquals("Cargando…", textoCatalogo(null))
        val e = EstadoCatalogo(OrigenCatalogo.ARCHIVO, "productos.csv", Catalogo.VACIO, cargando = true)
        assertEquals("Cargando el archivo…", textoCatalogo(e))
    }
}
