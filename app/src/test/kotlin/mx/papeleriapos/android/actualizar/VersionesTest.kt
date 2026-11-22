package mx.papeleriapos.android.actualizar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que la app lee de GitHub: el JSON de la última versión, qué APK elige y cómo compara las versiones. */
class VersionesTest {
    @Test
    fun `la version se lee de la etiqueta y se compara por sus tres numeros`() {
        assertEquals(Version(0, 5, 1), Version.de("v0.5.1"))
        assertEquals(Version(0, 5, 1), Version.de(" 0.5.1 "))
        assertEquals(Version(1, 0, 0), Version.de("1.0.0"))
        for (mala in listOf(null, "", "v0.5", "0.5.1-rc1", "instantanea-ronda1", "v0.5.1.2", "0.x.1")) assertNull(mala, Version.de(mala))
        assertTrue(Version(0, 5, 1) > Version(0, 5, 0))
        assertTrue(Version(0, 10, 0) > Version(0, 9, 9))
        assertTrue(Version(1, 0, 0) > Version(0, 99, 99))
        assertEquals(0, Version(0, 4, 4).compareTo(Version.de("v0.4.4")!!))
        // El mismo cálculo que el build: MAYOR·10000 + MENOR·100 + PARCHE.
        assertEquals(501L, Version(0, 5, 1).codigo)
        assertEquals(10_203L, Version(1, 2, 3).codigo)
        assertEquals("0.5.1", Version(0, 5, 1).toString())
    }

    @Test
    fun `el JSON de la ultima version da el numero, las notas, la fecha y el APK de release`() {
        val v = Versiones.leer(Ejemplos.json())
        assertEquals(Version(0, 5, 1), v.version)
        assertEquals(Ejemplos.NOMBRE, v.apk.nombre)
        assertEquals(15_404_054L, v.apk.tamano)
        assertEquals("https://github.com/Marioalr/papeleria-pos-android/releases/download/v0.5.1/escaner-0.5.1-a1b2c3d.apk", v.apk.url)
        assertEquals("ab".repeat(32), v.apk.sha256)
        assertEquals(Ejemplos.PUBLICADA, v.publicada)
        assertEquals(
            listOf(
                "Dos detalles de la venta.",
                "• El teclado de la cantidad: acepta medias piezas, como la caja.",
                "– Solo en lo que se vende a granel.",
                "• Buscar recuerda lo último que se eligió (pruebas).",
            ),
            v.notas,
        )
    }

    @Test
    fun `nunca se elige el APK de depuracion ni un archivo con otro nombre`() {
        val v = Version(0, 5, 1)
        fun archivo(nombre: String, tamano: Long = 10, url: String = "https://github.com/x/$nombre") = ArchivoGithubDto(nombre, tamano, url)
        val elegido = Versiones.elegirApk(listOf(archivo("escaner-0.5.1-a1b2c3d-depuracion.apk"), archivo("escaner-0.5.1-a1b2c3d.apk")), v)
        assertEquals("escaner-0.5.1-a1b2c3d.apk", elegido?.nombre)
        assertNull(Versiones.elegirApk(listOf(archivo("escaner-0.5.1-a1b2c3d-depuracion.apk")), v))
        // De otra versión, con otro nombre, vacío o sin dirección: ninguno.
        assertNull(Versiones.elegirApk(listOf(archivo("escaner-0.5.0-a1b2c3d.apk"), archivo("app-release.apk"), archivo("escaner-0.5.1-a1b2c3d.apk.sha256")), v))
        assertNull(Versiones.elegirApk(listOf(archivo("escaner-0.5.1-a1b2c3d.apk", tamano = 0)), v))
        assertNull(Versiones.elegirApk(listOf(archivo("escaner-0.5.1-a1b2c3d.apk", url = " ")), v))
        // El punto de la versión no es un comodín.
        assertNull(Versiones.elegirApk(listOf(archivo("escaner-0x5y1-a1b2c3d.apk")), v))
    }

    @Test
    fun `sin APK, con una etiqueta que no es version o con otra cosa que JSON, lo dice`() {
        val sinApk = assertThrows(ErrorDeActualizacion::class.java) { Versiones.leer(Ejemplos.json(archivos = "")) }
        assertEquals("La versión 0.5.1 está publicada sin el APK para instalar. Vuelve a buscar más tarde.", sinApk.message)
        for (malo in listOf("<html>Sin servicio</html>", "[]", "", Ejemplos.json(etiqueta = "instantanea"), """{"tag_name": 5, "assets": "x"}""")) {
            assertEquals(malo, Versiones.NO_SE_ENTIENDE, assertThrows(ErrorDeActualizacion::class.java) { Versiones.leer(malo) }.message)
        }
    }

    @Test
    fun `la huella solo cuenta si es un sha256 completo`() {
        assertEquals("ab".repeat(32), Versiones.sha256De("sha256:" + "AB".repeat(32)))
        assertEquals("0f".repeat(32), Versiones.sha256De("0f".repeat(32)))
        for (mala in listOf(null, "", "sha256:abc", "sha1:" + "ab".repeat(20), "sha256:" + "zz".repeat(32))) assertNull(mala, Versiones.sha256De(mala))
        // Sin huella, la versión se lee igual: se comprueba por tamaño, paquete y firma.
        assertNull(Versiones.leer(Ejemplos.json(huella = null)).apk.sha256)
    }

    @Test
    fun `la fecha de GitHub se lee en UTC y una que no se entiende no estorba`() {
        assertEquals(0L, Versiones.fecha("1970-01-01T00:00:00Z"))
        assertEquals(Ejemplos.PUBLICADA, Versiones.fecha("2026-11-28T18:30:05.123Z"))
        for (mala in listOf(null, "", "ayer", "2026-11-28")) assertNull(mala, Versiones.fecha(mala))
        assertNull(Versiones.leer(Ejemplos.json(publicada = "pronto")).publicada)
    }

    @Test
    fun `las notas salen sin Markdown y con los renglones partidos vueltos a juntar`() {
        val notas = Versiones.notasLegibles(
            "Los 9 hallazgos de la [revisión de la entrega 2](docs/revision-entrega-2.md), arreglados; cada uno con\n" +
                "su prueba.\n\n" +
                "- **La llave solo sale hacia la caja principal que probó quién es:** al abrir la app, la dirección guardada tiene\n" +
                "  que probar quién es (`/api/identidad`).\n" +
                "- `ConexionTest` contesta como el servicio de verdad.\n",
        )
        assertEquals(
            listOf(
                "Los 9 hallazgos de la revisión de la entrega 2, arreglados; cada uno con su prueba.",
                "• La llave solo sale hacia la caja principal que probó quién es: al abrir la app, la dirección guardada tiene que probar quién es (/api/identidad).",
                "• ConexionTest contesta como el servicio de verdad.",
            ),
            notas,
        )
        assertEquals(emptyList<String>(), Versiones.notasLegibles(null))
        assertEquals(emptyList<String>(), Versiones.notasLegibles(" \n\n"))
    }

    @Test
    fun `el tamano se dice en megas con un decimal`() {
        assertEquals("15.4 MB", Versiones.megas(15_404_054))
        assertEquals("0.0 MB", Versiones.megas(0))
    }
}
