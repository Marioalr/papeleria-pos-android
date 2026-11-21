package mx.papeleriapos.android.caja

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Una interfaz de red del celular, como la ve [NetworkInterface]: su nombre («wlan0», «rmnet_data1», «tun0»), si está
 * arriba y sus direcciones IPv4 con su prefijo. Sin MAC ni nada que identifique al celular.
 */
data class InterfazDeRed(val nombre: String, val arriba: Boolean, val ipv4: List<IpConPrefijo> = emptyList())

/** «192.168.1.34/24». */
data class IpConPrefijo(val ip: String, val prefijo: Int) {
    override fun toString() = "$ip/$prefijo"
}

/**
 * Lo que se sabe de la red del celular, para decir por qué no contesta la caja principal (2026-11-21: el Pixel estaba
 * con datos móviles y Proton VPN encendida, y la app solo decía «Ninguna caja principal contestó en esta red»).
 *
 * Se lee con [NetworkInterface], sin permisos: el manifiesto quita a propósito ACCESS_NETWORK_STATE (ML Kit lo usaría
 * para mandar sus estadísticas), así que ConnectivityManager no sirve aquí.
 */
data class EstadoDeRed(val interfaces: List<InterfazDeRed>) {
    private val arriba get() = interfaces.filter { it.arriba }

    /** Las direcciones del celular en una red local (Wi-Fi o cable): privadas, en una interfaz que no es túnel ni de datos móviles. */
    val local: List<IpConPrefijo>
        get() = arriba.filter { esLocal(it.nombre) }.flatMap { i -> i.ipv4.filter { esPrivadaDeRedLocal(it.ip) } }

    /** Hay una VPN encendida: una interfaz de túnel arriba (tun0 de Proton VPN, WireGuard, OpenVPN; ppp, ipsec). */
    val vpn: Boolean get() = arriba.any { esTunel(it.nombre) }

    /** Las interfaces que están arriba, para el registro: «wlan0 192.168.1.34/24, tun0 10.2.0.2/32». */
    fun resumen(): String =
        arriba.filterNot { it.nombre.startsWith("lo") }
            .joinToString(", ") { i -> if (i.ipv4.isEmpty()) i.nombre else "${i.nombre} ${i.ipv4.joinToString(" ")}" }
            .ifEmpty { "sin interfaces arriba" }

    companion object {
        private val TUNELES = listOf("tun", "ppp", "ipsec", "wg", "tap", "utun", "l2tp", "pptp")

        /** Datos móviles (Qualcomm, MediaTek, Exynos/Unisoc, el 464XLAT) y otras que no son la red de la tienda. */
        private val NO_LOCALES = listOf("rmnet", "rev_rmnet", "ccmni", "seth", "pdp", "wwan", "v4-", "clat", "ims", "dummy", "p2p", "ifb", "sit", "ip6", "gre", "erspan", "lo")

        fun esTunel(nombre: String) = TUNELES.any { nombre.startsWith(it) }

        fun esLocal(nombre: String) = !esTunel(nombre) && NO_LOCALES.none { nombre.startsWith(it) }

        /** 10/8, 172.16/12 y 192.168/16 (las de un Wi-Fi). Ni 169.254 (sin DHCP) ni 100.64/10 (la de los operadores). */
        fun esPrivadaDeRedLocal(ip: String): Boolean {
            val b = octetos(ip) ?: return false
            return b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168)
        }

        /** Las de este celular ahora; vacía si Android no deja leerlas (entonces no se adivina la causa). */
        fun actual(): EstadoDeRed? = try {
            val lista = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { i ->
                InterfazDeRed(
                    i.name.orEmpty(),
                    i.isUp,
                    i.interfaceAddresses.mapNotNull { a ->
                        val ip = a.address as? Inet4Address ?: return@mapNotNull null
                        IpConPrefijo(ip.hostAddress ?: return@mapNotNull null, a.networkPrefixLength.toInt())
                    },
                )
            }
            EstadoDeRed(lista)
        } catch (e: Exception) {
            null
        }

        internal fun octetos(ip: String): IntArray? {
            val partes = ip.split('.')
            if (partes.size != 4) return null
            val b = IntArray(4)
            for (k in 0..3) b[k] = partes[k].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            return b
        }

        /** Si [ip] cae en la red de [red] (misma dirección de red con su prefijo). */
        fun mismaSubred(ip: String, red: IpConPrefijo): Boolean {
            val a = octetos(ip) ?: return false
            val b = octetos(red.ip) ?: return false
            val p = red.prefijo.coerceIn(0, 32)
            val mascara = if (p == 0) 0L else (0xFFFFFFFFL shl (32 - p)) and 0xFFFFFFFFL
            return (aNumero(a) and mascara) == (aNumero(b) and mascara)
        }

        private fun aNumero(b: IntArray): Long = (b[0].toLong() shl 24) or (b[1].toLong() shl 16) or (b[2].toLong() shl 8) or b[3].toLong()
    }
}

/** Por qué, probablemente, no contestó la caja principal. Puede haber más de una (sin Wi-Fi y con VPN a la vez). */
enum class CausaDeRed { SIN_WIFI, VPN, OTRA_SUBRED }

/**
 * Las causas probables y su mensaje, en español llano. Funciones puras: se prueban en la JVM con un [EstadoDeRed] de mentira.
 */
object Diagnostico {
    /**
     * Las causas, en el orden en que conviene arreglarlas. [ip] es la dirección que se escribió o la guardada (para ver si
     * es de otra red); null al buscar por UDP. Con [red] null (no se pudieron leer las interfaces) no se adivina nada.
     */
    fun causas(red: EstadoDeRed?, ip: String? = null): List<CausaDeRed> {
        if (red == null) return emptyList()
        val salida = ArrayList<CausaDeRed>()
        val local = red.local
        if (local.isEmpty()) salida.add(CausaDeRed.SIN_WIFI)
        if (red.vpn) salida.add(CausaDeRed.VPN)
        if (ip != null && local.isNotEmpty() && EstadoDeRed.esPrivadaDeRedLocal(ip) && local.none { EstadoDeRed.mismaSubred(ip, it) }) {
            salida.add(CausaDeRed.OTRA_SUBRED)
        }
        return salida
    }

    /** Dónde se dice: al buscar por UDP, al escribir la IP o, con alta, cuando el latido no contesta. */
    enum class Donde(val siguiente: String, val trasComa: String) {
        BUSCAR(" y vuelve a buscar.", ", y vuelve a buscar."),
        IP(" y vuelve a intentar.", ", y vuelve a intentar."),
        LATIDO("; la app vuelve a intentar sola.", "; la app vuelve a intentar sola."),
    }

    const val NADIE = "Ninguna caja principal contestó en esta red."
    const val NADIE_PISTA = "Revisa que el celular esté en el mismo Wi-Fi que la caja principal, y que esa PC esté encendida y con Papelería POS abierto."

    fun sinWifi(donde: Donde = Donde.BUSCAR) = "El celular no está conectado al Wi-Fi de la tienda: conéctalo" + donde.siguiente

    fun vpn(donde: Donde = Donde.BUSCAR) =
        "Hay una VPN encendida en el celular (por ejemplo Proton VPN): apágala o permite las conexiones de la red local" + donde.trasComa

    /** La IP es de otra red: «…(el celular está en 192.168.1.34)…». */
    fun otraSubred(ip: String, red: EstadoDeRed?): String {
        val del = red?.local?.firstOrNull()?.ip
        return "La IP $ip no es de la red del Wi-Fi del celular" + (if (del != null) " (el celular está en $del)" else "") +
            ": revisa la IP de la caja principal o que los dos estén en el mismo Wi-Fi."
    }

    /** Lo que se dice de cada causa, una por renglón. */
    fun textos(causas: List<CausaDeRed>, donde: Donde, ip: String? = null, red: EstadoDeRed? = null): List<String> = causas.map {
        when (it) {
            CausaDeRed.SIN_WIFI -> sinWifi(donde)
            CausaDeRed.VPN -> vpn(donde)
            CausaDeRed.OTRA_SUBRED -> otraSubred(ip.orEmpty(), red)
        }
    }

    /**
     * Al buscar por UDP y no contestar nadie, lo que va abajo de [NADIE]: la causa si se ve; si no, la pista de siempre
     * (el mismo Wi-Fi, la PC encendida y con el POS).
     */
    fun pistaAlBuscar(red: EstadoDeRed?): String {
        val causas = causas(red)
        return if (causas.isEmpty()) NADIE_PISTA else textos(causas, Donde.BUSCAR, red = red).joinToString("\n")
    }

    /** Al escribir la IP y no contestar ([direccion] es la que se escribió). */
    fun alUsarIp(direccion: Direccion, red: EstadoDeRed?): String {
        val causas = causas(red, direccion.ip)
        val base = "No contesta el POS en $direccion."
        return if (causas.isEmpty()) {
            "$base ¿Está encendida la caja principal, con Papelería POS abierto, y el celular en el mismo Wi-Fi?"
        } else {
            (listOf(base) + textos(causas, Donde.IP, direccion.ip, red)).joinToString("\n")
        }
    }

    /** Con alta, cuando el latido no contesta: la causa, o null si no se ve ninguna (basta «Sin conexión»). */
    fun alLatir(direccion: Direccion, red: EstadoDeRed?): String? =
        causas(red, direccion.ip).takeIf { it.isNotEmpty() }?.let { textos(it, Donde.LATIDO, direccion.ip, red).joinToString("\n") }

    /** Para el registro: «sin-wifi,vpn; wlan0 192.168.1.34/24, tun0 10.2.0.2/32». Sin llaves ni URL. */
    fun registro(red: EstadoDeRed?, causas: List<CausaDeRed>): String =
        (if (causas.isEmpty()) "sin causa a la vista" else causas.joinToString(",") { it.name.lowercase().replace('_', '-') }) +
            "; " + (red?.resumen() ?: "no se pudieron leer las interfaces")

    /** «SocketTimeoutException: timeout», sin la pila (y cortado, por si el mensaje trae de más). */
    fun deLaFalla(e: Throwable): String = (e::class.java.simpleName + (e.message?.let { ": $it" } ?: "")).take(200)
}
