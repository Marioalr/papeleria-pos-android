package mx.papeleriapos.android.caja

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

/** Solo la red de la tienda: qué direcciones acepta la app y cómo lee lo que contesta el descubrimiento. */
class RedLocalTest {
    @Test
    fun `acepta direcciones privadas, con o sin puerto, y nada mas`() {
        assertEquals(Direccion("192.168.1.10", 5080), RedLocal.direccionDe("192.168.1.10"))
        assertEquals(Direccion("192.168.1.10", 5180), RedLocal.direccionDe(" 192.168.1.10:5180 "))
        assertEquals(Direccion("10.0.2.2", 5080), RedLocal.direccionDe("http://10.0.2.2:5080/"))
        assertEquals(Direccion("172.16.4.1", 5080), RedLocal.direccionDe("172.16.4.1"))
        assertEquals(Direccion("100.64.0.9", 5080), RedLocal.direccionDe("100.64.0.9"))
        assertEquals(5080, RedLocal.direccionDe("[fe80::1]")!!.puerto)
        assertNull("Pública", RedLocal.direccionDe("8.8.8.8"))
        assertNull("Un nombre", RedLocal.direccionDe("caja.tienda.local"))
        assertNull("Un nombre público", RedLocal.direccionDe("example.com"))
        assertNull("Puerto fuera de rango", RedLocal.direccionDe("192.168.1.10:70000"))
        assertNull("Vacío", RedLocal.direccionDe("  "))
        assertNull("Basura", RedLocal.direccionDe("192.168.1"))
    }

    @Test
    fun `la direccion se escribe como la firma el servicio`() {
        assertEquals("192.168.0.10:5080", Direccion("192.168.0.10").firmable)
        assertEquals("http://192.168.0.10:5080/", Direccion("192.168.0.10").url)
        assertEquals("[fe80::1]:5080", Direccion("fe80::1%wlan0").firmable)
    }

    @Test
    fun `lo que contesta el descubrimiento`() {
        val privada = InetAddress.getByName("192.168.0.10")
        assertEquals(Direccion("192.168.0.10", 5080), Descubrimiento.leerRespuesta("PAPELERIAPOS 5080", privada))
        assertEquals(Direccion("192.168.0.10", 5180), Descubrimiento.leerRespuesta("PAPELERIAPOS 5180\n", privada))
        assertNull(Descubrimiento.leerRespuesta("HOLA 5080", privada))
        assertNull(Descubrimiento.leerRespuesta("PAPELERIAPOS", privada))
        assertNull(Descubrimiento.leerRespuesta("PAPELERIAPOS abc", privada))
        assertNull(Descubrimiento.leerRespuesta("PAPELERIAPOS 5080", InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun `la serie y el nombre que se proponen`() {
        assertEquals("CEL", Conexion.seriePropuesta(listOf(CajaAltaDto("1", "Caja 1", "A"), CajaAltaDto("2", "Caja 2", "B"))))
        assertEquals("CEA", Conexion.seriePropuesta(listOf(CajaAltaDto("1", "Caja 1", "A"), CajaAltaDto("3", "Celular viejo", "cel"))))
        assertEquals("CEB", Conexion.seriePropuesta(listOf(CajaAltaDto("3", "Celular viejo", "CEL"), CajaAltaDto("4", "Otro", "CEA"))))
        assertEquals("Celular de Luis", Conexion.nombrePropuesto(" luis "))
        assertEquals("Celular", Conexion.nombrePropuesto(""))
    }
}
