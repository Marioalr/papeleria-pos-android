package mx.papeleriapos.android.caja

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import mx.papeleriapos.android.tema.TemaEscaner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ajustes › La conexión: la pantalla de verdad con su ViewModel (como la crea la app) y los pasos del alta dibujados. */
@RunWith(AndroidJUnit4::class)
class PantallaConexionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `la pantalla se crea con el viewmodel del proceso y sin alta ofrece el alta`() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { TemaEscaner { PantallaConexion() } }
        compose.onNodeWithTag("pantalla-conexion").assertExists()
        // La conexión del proceso lee el disco en su hilo: se espera a que lo haya leído.
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(5_000) { Conexion.del(app).estado.first { it.leida } } }
        compose.waitForIdle()
        // Robolectric no tiene bloqueo de pantalla: la pantalla lo dice en lugar de empezar el alta.
        compose.onNodeWithTag("sin-bloqueo").assertExists()
    }

    @Test
    fun `los pasos del alta se dibujan y una ip que no es privada se rechaza`() {
        val paso = androidx.compose.runtime.mutableStateOf<PasoAlta>(PasoAlta.Buscar())
        var ip = ""
        compose.setContent {
            TemaEscaner {
                ContenidoConexion(
                    estado = EstadoConexion(leida = true),
                    paso = paso.value,
                    hayBloqueo = true,
                    alAbrirSeguridad = {},
                    acciones = AccionesAlta(
                        escribirIp = { ip = it; paso.value = (paso.value as PasoAlta.Buscar).copy(ipEscrita = it) },
                        elegir = { d ->
                            val b = paso.value as PasoAlta.Buscar
                            paso.value = if (d == null && RedLocal.direccionDe(b.ipEscrita) == null) b.copy(error = "Escribe la IP de la caja principal") else PasoAlta.Pin(RedLocal.direccionDe(b.ipEscrita)!!, "1.0")
                        },
                    ),
                )
            }
        }
        compose.onNodeWithTag("buscar-caja").assertIsDisplayed()
        compose.onNodeWithTag("ip-caja").performTextInput("8.8.8.8")
        compose.onNodeWithTag("usar-ip").performClick()
        compose.onNodeWithTag("conexion-aviso").assertIsDisplayed()
        paso.value = PasoAlta.Buscar(ipEscrita = "192.168.1.10")
        compose.onNodeWithTag("usar-ip").performClick()
        compose.onNodeWithTag("alta-usuario").assertIsDisplayed()
        compose.onNodeWithTag("alta-pin").assertIsDisplayed()
        paso.value = PasoAlta.Datos(Direccion("192.168.1.10"), "luis", "1234", listOf(SucursalAltaDto("s1", "Matriz", listOf(CajaAltaDto("c1", "Caja 1", "A")))), "s1", "Celular de Luis", "CEL")
        compose.onNodeWithTag("alta-nombre").assertIsDisplayed()
        compose.onNodeWithTag("alta-serie").assertIsDisplayed()
        compose.onNodeWithTag("alta-dar").assertIsDisplayed()
        compose.onNodeWithText("Las cajas que ya hay: Caja 1 (serie A). El celular será una caja nueva; no toca a ninguna.").assertIsDisplayed()
    }

    @Test
    fun `con alta se lee la caja, la red y la copia`() {
        val alta = Alta(Direccion("192.168.1.10"), CajaDto("c9", "Celular de Luis", "CEL", "s1", "Matriz", "Pixel 8 Pro"), "Pixel 8 Pro", "luis", "LLAVE", null, 1_762_000_000_000L)
        compose.setContent {
            TemaEscaner {
                ContenidoConexion(
                    estado = EstadoConexion(alta = alta, red = Red.BUENA, ultimaVez = 1_762_000_000_000L, copia = ResumenCopia(1458, 1220, 1_762_000_000_000L, 6, "Papelería de prueba", "Matriz"), leida = true),
                    paso = PasoAlta.Buscar(),
                    hayBloqueo = true,
                    alAbrirSeguridad = {},
                    acciones = AccionesAlta(),
                )
            }
        }
        compose.onNodeWithText("192.168.1.10:5080").assertIsDisplayed()
        compose.onNodeWithText("Celular de Luis (serie CEL)").assertIsDisplayed()
        compose.onNodeWithText("Buena").assertIsDisplayed()
        // Más abajo de lo que cabe en la ventana de Robolectric: existe, y se llega deslizando.
        compose.onNodeWithText("1458").assertExists()
        compose.onNodeWithText("Papelería de prueba").assertExists()
        // Nada de la llave.
        compose.onAllNodesCount("LLAVE").let { check(it == 0) }
    }

    @Test
    fun `las fechas salen en espanol de mexico aunque el celular este en ingles`() {
        val idioma = java.util.Locale.getDefault()
        val zona = java.util.TimeZone.getDefault()
        java.util.Locale.setDefault(java.util.Locale.US)
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Mexico_City"))
        try {
            // 9 nov 2026, 8:47 y 20:05 en la Ciudad de México.
            val alta = Alta(Direccion("192.168.1.10"), CajaDto("c9", "Celular de Luis", "CEL", "s1", "Matriz", "Pixel 8 Pro"), "Pixel 8 Pro", "luis", "LLAVE", null, 1_794_235_620_000L)
            compose.setContent {
                TemaEscaner {
                    ContenidoConexion(
                        estado = EstadoConexion(alta = alta, red = Red.BUENA, ultimaVez = 1_794_276_300_000L, copia = ResumenCopia(1458, 1220, 1_794_235_620_000L, 6, null, "Matriz"), leida = true),
                        paso = PasoAlta.Buscar(),
                        hayBloqueo = true,
                        alAbrirSeguridad = {},
                        acciones = AccionesAlta(),
                    )
                }
            }
            // Regresión: salían «Nov 9, 2026 8:47 AM» y «8:05 PM», con el idioma del celular.
            check(compose.onAllNodesCount("9 nov 2026, 8:47") == 2) { "«Dado de alta» y «De cuándo» en español" }
            compose.onNodeWithText("20:05").assertExists()
            check(compose.onAllNodes(androidx.compose.ui.test.hasText("AM", substring = true).or(androidx.compose.ui.test.hasText("PM", substring = true).or(androidx.compose.ui.test.hasText("Nov", substring = true)))).fetchSemanticsNodes().isEmpty())
        } finally {
            java.util.Locale.setDefault(idioma)
            java.util.TimeZone.setDefault(zona)
        }
    }

    @Test
    fun `si nadie contesta al buscar dice la causa abajo`() {
        val sinWifiConVpn = EstadoDeRed(listOf(InterfazDeRed("rmnet_data1", true, listOf(IpConPrefijo("10.150.20.7", 30))), InterfazDeRed("tun0", true, listOf(IpConPrefijo("10.2.0.2", 32)))))
        val paso = androidx.compose.runtime.mutableStateOf<PasoAlta>(PasoAlta.Buscar(busco = true, pista = Diagnostico.pistaAlBuscar(sinWifiConVpn)))
        compose.setContent {
            TemaEscaner { ContenidoConexion(EstadoConexion(leida = true), paso.value, hayBloqueo = true, alAbrirSeguridad = {}, acciones = AccionesAlta()) }
        }
        compose.onNodeWithText(Diagnostico.NADIE).assertIsDisplayed()
        compose.onNodeWithTag("conexion-causa").assertIsDisplayed()
        compose.onNodeWithText(Diagnostico.sinWifi(), substring = true).assertIsDisplayed()
        compose.onNodeWithText("Proton VPN", substring = true).assertIsDisplayed()
        // En el Wi-Fi y sin VPN: el mensaje de siempre y la pista.
        paso.value = PasoAlta.Buscar(busco = true, pista = Diagnostico.pistaAlBuscar(EstadoDeRed(listOf(InterfazDeRed("wlan0", true, listOf(IpConPrefijo("192.168.1.34", 24)))))))
        compose.onNodeWithText(Diagnostico.NADIE_PISTA).assertIsDisplayed()
    }

    @Test
    fun `con alta y la red caida dice la causa`() {
        val alta = Alta(Direccion("192.168.1.10"), CajaDto("c9", "Celular de Luis", "CEL", "s1", "Matriz", "Pixel 8 Pro"), "Pixel 8 Pro", "luis", "LLAVE", null, 1_762_000_000_000L)
        val causa = Diagnostico.vpn(Diagnostico.Donde.LATIDO)
        compose.setContent {
            TemaEscaner {
                ContenidoConexion(EstadoConexion(alta = alta, red = Red.CAIDA, causa = causa, leida = true), PasoAlta.Buscar(), hayBloqueo = true, alAbrirSeguridad = {}, acciones = AccionesAlta())
            }
        }
        compose.onNodeWithText("Sin conexión").assertIsDisplayed()
        compose.onNodeWithTag("conexion-causa").assertIsDisplayed()
        compose.onNodeWithText(causa).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesCount(etiqueta: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(etiqueta).or(androidx.compose.ui.test.hasText(etiqueta))).fetchSemanticsNodes().size

    // ---- Dos sucursales (0.5.5) ----

    private val matriz = Enlace("c-m", "Matriz", "Celular", "CEL", Direccion("192.168.1.10", 5080))
    private val sucursal2 = Enlace("c-s2", "Sucursal 2", "Celular", "CEA", Direccion("192.168.0.20", 5080))
    private val altaMatriz = Alta(matriz.direccion, CajaDto("c-m", "Celular", "CEL", "s1", "Matriz"), "Prueba", "dueno", "llave", null, 1L)

    @Test
    fun `con alta se ven las sucursales enlazadas, la de ahora en uso y usar esta en la otra`() {
        var usada: String? = null
        var enlazar = 0
        compose.setContent {
            TemaEscaner {
                ContenidoConexion(
                    estado = EstadoConexion(alta = altaMatriz, leida = true, enlaces = listOf(matriz, sucursal2)),
                    paso = PasoAlta.Buscar(),
                    hayBloqueo = true,
                    alAbrirSeguridad = {},
                    acciones = AccionesAlta(usar = { usada = it }, enlazarOtra = { enlazar++ }),
                )
            }
        }
        compose.onNodeWithText("En uso").assertExists()
        compose.onNodeWithTag("enlace-usar-c-m").assertDoesNotExist()
        compose.onNodeWithTag("enlace-usar-c-s2").performScrollTo().performClick()
        org.junit.Assert.assertEquals("c-s2", usada)
        compose.onNodeWithTag("enlazar-otra").performScrollTo().performClick()
        org.junit.Assert.assertEquals(1, enlazar)
    }

    @Test
    fun `enlazando otra sucursal se ven los pasos del alta y se puede seguir con la de ahora`() {
        var cancelado = false
        compose.setContent {
            TemaEscaner {
                ContenidoConexion(
                    estado = EstadoConexion(alta = altaMatriz, leida = true, enlaces = listOf(matriz)),
                    paso = PasoAlta.Buscar(),
                    hayBloqueo = true,
                    alAbrirSeguridad = {},
                    acciones = AccionesAlta(cancelarEnlace = { cancelado = true }),
                    enlazando = true,
                )
            }
        }
        compose.onNodeWithText("Enlazar otra sucursal").assertExists()
        compose.onNodeWithTag("buscar-caja").assertExists()
        compose.onNodeWithTag("enlace-cancelar").performScrollTo().performClick()
        org.junit.Assert.assertTrue(cancelado)
    }

    @Test
    fun `sin ninguna en uso las que quedan se pueden usar sin dar de alta otra vez`() {
        compose.setContent {
            TemaEscaner {
                ContenidoConexion(
                    estado = EstadoConexion(leida = true, enlaces = listOf(matriz), aviso = Conexion.SIN_ACCESO, sinAcceso = true),
                    paso = PasoAlta.Buscar(),
                    hayBloqueo = true,
                    alAbrirSeguridad = {},
                    acciones = AccionesAlta(),
                )
            }
        }
        compose.onNodeWithTag("enlace-usar-c-m").assertExists()
        compose.onNodeWithTag("enlazar-otra").assertDoesNotExist()
        compose.onNodeWithTag("buscar-caja").assertExists()
    }
}
