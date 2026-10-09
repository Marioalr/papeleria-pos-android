package mx.papeleriapos.escaner.nucleo

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Los carteles para la cámara del emulador (`-camera-back imagefile:<png>`, docs/pruebas.md): cada uno es una escena
 * con un código para probar la lectura y la guía sin celular. Se generan con `./gradlew :nucleo:carteles` en
 * nucleo/build/carteles.
 */
object Carteles {
    val codigos = listOf(
        "ean13" to (Simbologia.EAN_13 to "7501234567893"),
        "interno" to (Simbologia.EAN_13 to Gtin.completar("200000000001")),
        "ean8" to (Simbologia.EAN_8 to "96385074"),
        "upca" to (Simbologia.UPC_A to "036000291452"),
        "itf14" to (Simbologia.ITF to "10012345000017"),
        "code128" to (Simbologia.CODE_128 to "PAP0042x"),
    )

    val escenas = mapOf(
        "centro" to Escena(anchoRelativo = 0.45f),
        "chico" to Escena(anchoRelativo = 0.14f),
        "grande" to Escena(anchoRelativo = 1.1f),
        "izquierda" to Escena(centroX = 0.22f, anchoRelativo = 0.3f),
        "abajo" to Escena(centroY = 0.85f, anchoRelativo = 0.35f),
        "girado" to Escena(anchoRelativo = 0.45f, giroGrados = 30f),
        "borroso" to Escena(anchoRelativo = 0.45f, desenfoque = 3),
        "oscuro" to Escena(anchoRelativo = 0.45f, fondo = 25, espacio = 45, barra = 10),
        "reflejo" to Escena(anchoRelativo = 0.45f, reflejo = 0.4f),
    )

    fun escribir(carpeta: File, ancho: Int = 1280, alto: Int = 960) {
        carpeta.mkdirs()
        for ((nombreCodigo, codigo) in codigos) {
            val modulos = Barras.para(codigo.first, codigo.second)
            for ((nombreEscena, escena) in escenas) {
                if (nombreCodigo != "ean13" && nombreEscena != "centro") continue
                val luma = Lienzo.dibujar(modulos, Barras.zonaEnBlanco(codigo.first), ancho, alto, escena)
                escribirPng(luma, File(carpeta, "$nombreCodigo-$nombreEscena.png"))
            }
        }
    }

    fun escribirPng(luma: Luma, archivo: File) {
        val img = BufferedImage(luma.ancho, luma.alto, BufferedImage.TYPE_BYTE_GRAY)
        img.raster.setDataElements(0, 0, luma.ancho, luma.alto, luma.pixeles)
        ImageIO.write(img, "png", archivo)
    }

    @JvmStatic
    fun main(args: Array<String>) = escribir(File(args.firstOrNull() ?: "build/carteles"))
}
