package mx.papeleriapos.android.caja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Por qué no contesta la caja principal, con redes de mentira (en la JVM, sin Android). El caso del 2026-11-21: el Pixel
 * con datos móviles, el Wi-Fi desconectado y Proton VPN encendida (0.0.0.0/0 por tun0).
 */
class DiagnosticoTest {
    private fun i(nombre: String, vararg ips: String, arriba: Boolean = true) =
        InterfazDeRed(nombre, arriba, ips.map { IpConPrefijo(it.substringBefore('/'), it.substringAfter('/').toInt()) })

    private val lo = i("lo", "127.0.0.1/8")
    private val wifi = i("wlan0", "192.168.1.34/24")
    private val datos = i("rmnet_data1", "10.150.20.7/30")
    private val clat = i("v4-rmnet_data1", "192.0.0.4/32")
    private val proton = i("tun0", "10.2.0.2/32")

    @Test
    fun `el caso del pixel sin wifi y con proton vpn dice las dos causas`() {
        val red = EstadoDeRed(listOf(lo, datos, clat, proton, i("wlan0", arriba = false)))
        assertEquals(listOf(CausaDeRed.SIN_WIFI, CausaDeRed.VPN), Diagnostico.causas(red))
        assertEquals(
            "El celular no está conectado al Wi-Fi de la tienda: conéctalo y vuelve a buscar.\n" +
                "Hay una VPN encendida en el celular (por ejemplo Proton VPN): apágala o permite las conexiones de la red local, y vuelve a buscar.",
            Diagnostico.pistaAlBuscar(red),
        )
        // Escribiendo la IP: lo mismo, con «vuelve a intentar»; la subred no se puede comparar sin Wi-Fi.
        assertEquals(
            "No contesta el POS en 192.168.1.67:5080.\n" +
                "El celular no está conectado al Wi-Fi de la tienda: conéctalo y vuelve a intentar.\n" +
                "Hay una VPN encendida en el celular (por ejemplo Proton VPN): apágala o permite las conexiones de la red local, y vuelve a intentar.",
            Diagnostico.alUsarIp(Direccion("192.168.1.67"), red),
        )
    }

    @Test
    fun `los datos moviles con ip privada no cuentan como el wifi`() {
        // Los operadores dan 10.x en rmnet: no es la red de la tienda.
        val red = EstadoDeRed(listOf(lo, datos, clat))
        assertTrue(red.local.isEmpty())
        assertFalse(red.vpn)
        assertEquals(listOf(CausaDeRed.SIN_WIFI), Diagnostico.causas(red))
        assertEquals(Diagnostico.sinWifi(), Diagnostico.pistaAlBuscar(red))
    }

    @Test
    fun `con wifi y vpn solo dice la vpn`() {
        val red = EstadoDeRed(listOf(lo, wifi, datos, proton))
        assertEquals(listOf(CausaDeRed.VPN), Diagnostico.causas(red, "192.168.1.67"))
        for (vpn in listOf("tun1", "ppp0", "ipsec0", "wg0")) {
            assertTrue(vpn, EstadoDeRed(listOf(wifi, i(vpn, "10.8.0.2/24"))).vpn)
        }
        assertFalse("Una VPN apagada no cuenta", EstadoDeRed(listOf(wifi, i("tun0", "10.2.0.2/32", arriba = false))).vpn)
    }

    @Test
    fun `con wifi y sin vpn se queda el mensaje de siempre con la pista`() {
        val red = EstadoDeRed(listOf(lo, wifi, datos))
        assertEquals(emptyList<CausaDeRed>(), Diagnostico.causas(red, "192.168.1.67"))
        assertEquals(
            "Revisa que el celular esté en el mismo Wi-Fi que la caja principal, y que esa PC esté encendida y con Papelería POS abierto.",
            Diagnostico.pistaAlBuscar(red),
        )
        assertEquals("Ninguna caja principal contestó en esta red.", Diagnostico.NADIE)
        assertEquals(
            "No contesta el POS en 192.168.1.67:5080. ¿Está encendida la caja principal, con Papelería POS abierto, y el celular en el mismo Wi-Fi?",
            Diagnostico.alUsarIp(Direccion("192.168.1.67"), red),
        )
        assertNull("Con alta y nada raro, basta «Sin conexión»", Diagnostico.alLatir(Direccion("192.168.1.67"), red))
    }

    @Test
    fun `una ip escrita de otra red lo dice`() {
        val red = EstadoDeRed(listOf(lo, wifi))
        assertEquals(listOf(CausaDeRed.OTRA_SUBRED), Diagnostico.causas(red, "192.168.0.67"))
        assertEquals(
            "No contesta el POS en 192.168.0.67:5080.\n" +
                "La IP 192.168.0.67 no es de la red del Wi-Fi del celular (el celular está en 192.168.1.34): revisa la IP de la caja principal o que los dos estén en el mismo Wi-Fi.",
            Diagnostico.alUsarIp(Direccion("192.168.0.67"), red),
        )
        // La misma red, con otro prefijo: 10.0.0.0/16 incluye 10.0.5.9.
        assertEquals(emptyList<CausaDeRed>(), Diagnostico.causas(EstadoDeRed(listOf(i("wlan0", "10.0.1.20/16"))), "10.0.5.9"))
        // El emulador: eth0 10.0.2.15/24 y su anfitrión 10.0.2.2.
        assertEquals(emptyList<CausaDeRed>(), Diagnostico.causas(EstadoDeRed(listOf(i("eth0", "10.0.2.15/24"))), "10.0.2.2"))
        // IPv6 o de enlace local: no se compara.
        assertEquals(emptyList<CausaDeRed>(), Diagnostico.causas(red, "fe80::1"))
        assertEquals(emptyList<CausaDeRed>(), Diagnostico.causas(red, "169.254.3.4"))
    }

    @Test
    fun `con alta el latido dice la causa sin pedir que se busque`() {
        val red = EstadoDeRed(listOf(lo, datos, proton))
        assertEquals(
            "El celular no está conectado al Wi-Fi de la tienda: conéctalo; la app vuelve a intentar sola.\n" +
                "Hay una VPN encendida en el celular (por ejemplo Proton VPN): apágala o permite las conexiones de la red local; la app vuelve a intentar sola.",
            Diagnostico.alLatir(Direccion("192.168.1.67"), red),
        )
    }

    @Test
    fun `sin poder leer las interfaces no se adivina`() {
        assertEquals(emptyList<CausaDeRed>(), Diagnostico.causas(null, "192.168.1.67"))
        assertEquals(Diagnostico.NADIE_PISTA, Diagnostico.pistaAlBuscar(null))
        assertTrue(Diagnostico.registro(null, emptyList()).contains("no se pudieron leer"))
    }

    @Test
    fun `el registro trae la red y la falla pero nada de llaves`() {
        val red = EstadoDeRed(listOf(lo, datos, proton))
        assertEquals(
            "sin-wifi,vpn; rmnet_data1 10.150.20.7/30, tun0 10.2.0.2/32",
            Diagnostico.registro(red, Diagnostico.causas(red)),
        )
        assertEquals("SocketTimeoutException: timeout", Diagnostico.deLaFalla(java.net.SocketTimeoutException("timeout")))
        assertEquals("IOException", Diagnostico.deLaFalla(java.io.IOException()))
        assertEquals(200, Diagnostico.deLaFalla(java.io.IOException("x".repeat(500))).length)
    }

    @Test
    fun `las cuentas de la subred`() {
        assertTrue(EstadoDeRed.mismaSubred("192.168.1.67", IpConPrefijo("192.168.1.34", 24)))
        assertFalse(EstadoDeRed.mismaSubred("192.168.0.67", IpConPrefijo("192.168.1.34", 24)))
        assertTrue(EstadoDeRed.mismaSubred("172.20.9.1", IpConPrefijo("172.16.0.5", 12)))
        assertTrue(EstadoDeRed.mismaSubred("8.8.8.8", IpConPrefijo("192.168.1.34", 0)))
        assertFalse(EstadoDeRed.mismaSubred("basura", IpConPrefijo("192.168.1.34", 24)))
        assertFalse("CGNAT no es red local", EstadoDeRed.esPrivadaDeRedLocal("100.64.1.1"))
        assertFalse(EstadoDeRed.esPrivadaDeRedLocal("172.32.0.1"))
    }
}
