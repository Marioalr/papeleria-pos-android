package mx.papeleriapos.android.caja

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Solo la red de la tienda (docs/plan.md, «Red y privacidad»): la app habla con la caja principal únicamente en una
 * dirección privada (las de un Wi-Fi o un cable: 10/8, 172.16/12, 192.168/16, la de enlace local 169.254/16 y, para el
 * emulador, su anfitrión 10.0.2.2, que ya es privada). Nada de nombres de dominio ni de direcciones públicas.
 */
object RedLocal {
    const val PUERTO_HTTP = 5080

    /** La dirección del servicio a partir de lo que escribe el dueño o contesta la red: «192.168.1.10», «192.168.1.10:5080». */
    fun direccionDe(texto: String, puertoPorOmision: Int = PUERTO_HTTP): Direccion? {
        val t = texto.trim().removePrefix("http://").removeSuffix("/")
        if (t.isEmpty()) return null
        val ip: String
        val puerto: Int
        if (t.startsWith("[")) {
            // IPv6 entre corchetes: [fe80::1]:5080.
            val fin = t.indexOf(']')
            if (fin < 0) return null
            ip = t.substring(1, fin)
            puerto = t.substring(fin + 1).removePrefix(":").ifEmpty { puertoPorOmision.toString() }.toIntOrNull() ?: return null
        } else {
            val partes = t.split(':')
            if (partes.size > 2) return null
            ip = partes[0]
            puerto = partes.getOrNull(1)?.toIntOrNull() ?: puertoPorOmision
        }
        if (puerto !in 1..65535) return null
        val direccion = ipDe(ip) ?: return null
        if (!esPrivada(direccion)) return null
        return Direccion(direccion.hostAddress ?: return null, puerto)
    }

    /** Solo una IP escrita con números (sin resolver nombres): null si no lo es. */
    private fun ipDe(texto: String): InetAddress? {
        val esIpv4 = texto.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
        val esIpv6 = ':' in texto && texto.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '%' }
        if (!esIpv4 && !esIpv6) return null
        return try {
            InetAddress.getByName(texto)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Una dirección de la red local: privada (RFC 1918), de enlace local o del propio equipo. Nunca 100.64/10 (RFC 6598):
     * es la red compartida de los operadores (CGNAT); con datos móviles, son otros clientes del operador.
     */
    fun esPrivada(ip: InetAddress): Boolean = ip.isSiteLocalAddress || ip.isLinkLocalAddress || ip.isLoopbackAddress

    /**
     * Las direcciones de difusión de las redes IPv4 de este celular (su Wi-Fi), más la general 255.255.255.255: a ellas va
     * la pregunta del descubrimiento. Vacía (solo la general) si no se pueden leer las interfaces.
     */
    fun difusiones(): List<InetAddress> {
        val salida = LinkedHashSet<InetAddress>()
        salida.add(InetAddress.getByName("255.255.255.255"))
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return salida.toList()
            for (i in interfaces) {
                if (!i.isUp || i.isLoopback) continue
                for (a in i.interfaceAddresses) {
                    val difusion = a.broadcast ?: continue
                    if (a.address is Inet4Address && esPrivada(a.address)) salida.add(difusion)
                }
            }
        } catch (e: Exception) {
            // Sin permiso para listar las interfaces o sin red: la general basta.
        }
        return salida.toList()
    }
}

/** La IP y el puerto del servicio de la caja principal. */
data class Direccion(val ip: String, val puerto: Int = RedLocal.PUERTO_HTTP) {
    /** «http://192.168.0.10:5080/», la base de cada llamada. */
    val url: String get() = "http://${anfitrion()}:$puerto/"

    /** «192.168.0.10:5080» (IPv6 entre corchetes): lo que firma el servicio en la prueba de identidad. */
    val firmable: String get() = "${anfitrion()}:$puerto"

    private fun anfitrion(): String = if (':' in ip) "[${ip.substringBefore('%')}]" else ip

    override fun toString(): String = firmable
}
