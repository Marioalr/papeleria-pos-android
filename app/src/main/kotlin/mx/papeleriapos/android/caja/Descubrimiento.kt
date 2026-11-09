package mx.papeleriapos.android.caja

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Encontrar la caja principal en el Wi-Fi de la tienda sin saber su dirección (docs/celular-como-caja.md, «Encontrar la
 * caja principal»; `Descubrimiento.cs` del POS): un UDP «PAPELERIAPOS?» a la difusión de la red, al puerto 5081; el
 * servicio contesta «PAPELERIAPOS 5080» desde su IP. Lo que contesta no prueba nada (cualquiera en la red puede
 * contestar): al dar de alta, `GET /api/estado` dice si es el POS y el dueño escribe su PIN; después, antes de mandarle la
 * llave a una dirección nueva, [Identidad].
 */
object Descubrimiento {
    const val PUERTO = 5081
    const val PREGUNTA = "PAPELERIAPOS?"
    private const val RESPUESTA = "PAPELERIAPOS"

    /** Lo más que se recuerda de lo que contesta: una lluvia de respuestas no llena la memoria. */
    private const val TOPE = 64

    /** Las direcciones de los servicios que contestaron en [esperaMs], sin repetir y solo privadas; vacía si ninguno. */
    suspend fun buscar(esperaMs: Long = 1_500): List<Direccion> = withContext(Dispatchers.IO) {
        val encontradas = LinkedHashSet<Direccion>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = esperaMs.toInt().coerceAtLeast(100)
                val pregunta = PREGUNTA.toByteArray(Charsets.UTF_8)
                for (destino in RedLocal.difusiones()) {
                    try {
                        socket.send(DatagramPacket(pregunta, pregunta.size, destino, PUERTO))
                    } catch (e: Exception) {
                        // Una red que no deja difundir: las demás sí.
                    }
                }
                val fin = System.nanoTime() + esperaMs * 1_000_000
                val buffer = ByteArray(256)
                while (encontradas.size < TOPE) {
                    val queda = (fin - System.nanoTime()) / 1_000_000
                    if (queda <= 0) break
                    socket.soTimeout = queda.toInt().coerceAtLeast(1)
                    val paquete = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(paquete)
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                    leerRespuesta(String(paquete.data, paquete.offset, paquete.length, Charsets.UTF_8), paquete.address)?.let(encontradas::add)
                }
            }
        } catch (e: Exception) {
            // Sin red, o Android no deja abrir el socket: lo que haya contestado antes, sirve.
        }
        encontradas.toList()
    }

    /** La dirección del servicio que contestó (su IP y el puerto que dijo); null si no es una respuesta del POS o no es privada. */
    fun leerRespuesta(mensaje: String, quien: InetAddress): Direccion? {
        val partes = mensaje.trim().split(' ')
        if (partes.size != 2 || partes[0] != RESPUESTA) return null
        val puerto = partes[1].toIntOrNull() ?: return null
        if (puerto !in 1..65535 || !RedLocal.esPrivada(quien)) return null
        return Direccion(quien.hostAddress ?: return null, puerto)
    }
}
