package mx.papeleriapos.escaner.camara

import androidx.camera.core.CameraState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticasTest {

    // ---- Proporción del visor ----

    @Test
    fun laProporcionSeGuardaYSeLeeComoTexto() {
        val p = Proporcion(1080, 2208)
        assertEquals("1080x2208", p.texto())
        assertEquals(p, Proporcion.de(p.texto()))
        assertNull(Proporcion.de(null))
        assertNull(Proporcion.de(""))
        assertNull(Proporcion.de("1080"))
        assertNull(Proporcion.de("x2208"))
        assertNull(Proporcion.de("1080x"))
        assertNull(Proporcion.de("0x2208"))
        assertNull(Proporcion.de("abcx2208"))
    }

    @Test
    fun casiLaMismaProporcionNoVuelveAEnlazar() {
        val a = Proporcion(1080, 2208)
        // Un píxel de redondeo no cuenta.
        assertTrue(mismaProporcion(a, Proporcion(1080, 2209)))
        assertTrue(mismaProporcion(a, Proporcion(540, 1104)))
        // Las barras del sistema sí (≈6 %).
        assertFalse(mismaProporcion(a, Proporcion(1080, 2340)))
        assertFalse(mismaProporcion(a, Proporcion(0, 2208)))
    }

    @Test
    fun laProporcionInicialEsLaGuardadaOLaVentanaMenosLasBarras() {
        val guardada = Proporcion(1080, 2000)
        assertEquals(guardada, proporcionInicial(guardada, 1080, 2400, 80, 60, 240))
        // Primera vez: la ventana menos la barra de estado, la de navegación y las pestañas.
        assertEquals(Proporcion(1080, 2020), proporcionInicial(null, 1080, 2400, 80, 60, 240))
        // Pantalla completa (escaneo rápido): la ventana entera.
        assertEquals(Proporcion(1080, 2400), proporcionInicial(null, 1080, 2400, 0, 0))
        // Insets negativos (no debería pasar) no agrandan el visor.
        assertEquals(Proporcion(1080, 2400), proporcionInicial(null, 1080, 2400, -5, -5, -5))
        // Sin con qué estimar.
        assertNull(proporcionInicial(null, 0, 0, 0, 0))
        assertNull(proporcionInicial(null, 1080, 100, 80, 60, 0))
        // Una guardada inválida no se usa.
        assertEquals(Proporcion(1080, 2400), proporcionInicial(Proporcion(0, 0), 1080, 2400, 0, 0))
    }

    @Test
    fun abrirConLoMismoNoVuelveAEnlazarYConOtraProporcionEsperaAQueSeAsiente() {
        val enlazada = Proporcion(1080, 2020)
        // Nada enlazado: ya.
        assertEquals(DecisionEnlace.AHORA, decidirEnlace(false, null, -1, enlazada, 0, forzar = false))
        assertEquals(DecisionEnlace.AHORA, decidirEnlace(true, null, -1, enlazada, 0, forzar = false))
        // Lo mismo (el segundo «abrir» de PantallaEscaner, o el de la pantalla tras el enlace en onCreate): nada.
        assertEquals(DecisionEnlace.NADA, decidirEnlace(true, enlazada, 0, Proporcion(1080, 2021), 0, forzar = false))
        // Otra proporción: después (el tamaño puede seguir cambiando).
        assertEquals(DecisionEnlace.DESPUES, decidirEnlace(true, enlazada, 0, Proporcion(1080, 2208), 0, forzar = false))
        // Otra rotación o «Reintentar»: ya.
        assertEquals(DecisionEnlace.AHORA, decidirEnlace(true, enlazada, 0, enlazada, 1, forzar = false))
        assertEquals(DecisionEnlace.AHORA, decidirEnlace(true, enlazada, 0, enlazada, 0, forzar = true))
    }

    // ---- Rango de FPS guardado ----

    @Test
    fun elRangoGuardadoSoloValeConLaMismaHuella() {
        val huella = "google/husky/husky:17/CP41.260828.004.A7/123:user/release-keys"
        assertEquals(RangoEnCache.Fijo(30, 30), decodificarRango(codificarRango(huella, 30 to 30), huella))
        assertEquals(RangoEnCache.Fijo(15, 30), decodificarRango(codificarRango(huella, 15 to 30), huella))
        assertEquals(RangoEnCache.Auto, decodificarRango(codificarRango(huella, null), huella))
        // Otra versión del sistema: se vuelve a calcular.
        assertEquals(RangoEnCache.SinDato, decodificarRango(codificarRango(huella, 30 to 30), "$huella-2"))
        assertEquals(RangoEnCache.SinDato, decodificarRango(null, huella))
    }

    @Test
    fun unRangoGuardadoRaroNoSeUsa() {
        val h = "h"
        for (texto in listOf("h|", "h|30", "h|-30", "h|a-30", "h|30-b", "h|31-30", "h|0-30", "30-30", "")) {
            assertEquals(texto, RangoEnCache.SinDato, decodificarRango(texto, h))
        }
    }

    @Test
    fun elRangoQueSeGuardaEsElQueSeElige() {
        // Las mismas reglas que al calcularlo: 30 fijo si se puede; si no, 15–30.
        val rangos = listOf(15 to 15, 15 to 30, 24 to 30, 30 to 30, 7 to 60)
        assertEquals(30 to 30, elegirRangoFps(rangos, fijo30 = true))
        assertEquals(15 to 30, elegirRangoFps(rangos, fijo30 = false))
        assertNull(elegirRangoFps(emptyList(), fijo30 = true))
    }

    // ---- Estado de la cámara ----

    @Test
    fun abiertaLimpiaElErrorSinImportarElCodigo() {
        assertEquals(AccionEstado.ABIERTA, reaccionEstado(true, 0, 2, fpsFijo = true).accion)
        assertEquals(AccionEstado.ABIERTA, reaccionEstado(true, CameraState.ERROR_CAMERA_IN_USE, 0, fpsFijo = true).accion)
        assertEquals(AccionEstado.NINGUNA, reaccionEstado(false, 0, 0, fpsFijo = true).accion)
    }

    @Test
    fun otraAppConLaCamaraAvisaYCameraXReintenta() {
        for (codigo in listOf(CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE)) {
            val r = reaccionEstado(false, codigo, 0, fpsFijo = true)
            assertEquals(AccionEstado.AVISO_RECUPERABLE, r.accion)
            assertEquals("Otra app está usando la cámara", r.mensaje)
        }
        // Un tropiezo recuperable no se avisa.
        assertEquals(AccionEstado.NINGUNA, reaccionEstado(false, CameraState.ERROR_OTHER_RECOVERABLE_ERROR, 0, true).accion)
    }

    @Test
    fun desactivadaONoMolestarSoloAvisan() {
        val d = reaccionEstado(false, CameraState.ERROR_CAMERA_DISABLED, 0, fpsFijo = true)
        assertEquals(AccionEstado.AVISO, d.accion)
        assertTrue(d.mensaje!!.contains("desactivada"))
        val n = reaccionEstado(false, CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED, 0, fpsFijo = true)
        assertEquals(AccionEstado.AVISO, n.accion)
        assertTrue(n.mensaje!!.contains("No molestar"))
    }

    @Test
    fun configuracionNoAceptadaVuelveSinFpsFijoUnaSolaVez() {
        assertEquals(AccionEstado.REENLAZAR_SIN_FPS, reaccionEstado(false, CameraState.ERROR_STREAM_CONFIG, 0, fpsFijo = true).accion)
        // Ya sin FPS fijo: se trata como error crítico (reintentos y luego error).
        assertEquals(AccionEstado.REENLAZAR, reaccionEstado(false, CameraState.ERROR_STREAM_CONFIG, 0, fpsFijo = false).accion)
        assertEquals(AccionEstado.ERROR_FINAL, reaccionEstado(false, CameraState.ERROR_STREAM_CONFIG, 2, fpsFijo = false).accion)
    }

    @Test
    fun errorCriticoReintentaDosVecesConEsperaCrecienteYLuegoFalla() {
        for (codigo in listOf(CameraState.ERROR_CAMERA_FATAL_ERROR, CameraState.ERROR_CAMERA_REMOVED)) {
            val r0 = reaccionEstado(false, codigo, 0, fpsFijo = false)
            val r1 = reaccionEstado(false, codigo, 1, fpsFijo = false)
            val r2 = reaccionEstado(false, codigo, 2, fpsFijo = false)
            assertEquals(AccionEstado.REENLAZAR, r0.accion)
            assertEquals(500L, r0.esperaMs)
            assertEquals(AccionEstado.REENLAZAR, r1.accion)
            assertEquals(1_000L, r1.esperaMs)
            assertEquals(AccionEstado.ERROR_FINAL, r2.accion)
            assertEquals("La cámara falló. Si sigue, reinicia el celular.", r2.mensaje)
        }
    }

    @Test
    fun elTextoDelDiagnosticoDiceElTipoYElError() {
        assertEquals("OPEN", textoEstadoCamara("OPEN", 0))
        assertEquals("OPENING · error 2 CAMERA_IN_USE", textoEstadoCamara("OPENING", CameraState.ERROR_CAMERA_IN_USE))
        assertEquals("CLOSED · error 8 CAMERA_REMOVED", textoEstadoCamara("CLOSED", CameraState.ERROR_CAMERA_REMOVED))
        assertEquals("CLOSED · error 99 DESCONOCIDO", textoEstadoCamara("CLOSED", 99))
    }

    // ---- Vigilante ----

    @Test
    fun elVigilanteReenlazaUnaVezSiConLaPantallaAlFrenteNoSaleNingunCuadro() {
        assertTrue(vigilanteDebeReenlazar(true, true, 0, 2_000, yaReenlazo = false, enPausa = false, errorRecuperable = false))
        // Salió algún cuadro, falta tiempo, ya lo hizo, en pausa, en segundo plano, sin enlace u otra app con la cámara: no.
        assertFalse(vigilanteDebeReenlazar(true, true, 1, 2_000, false, false, false))
        assertFalse(vigilanteDebeReenlazar(true, true, 0, 1_999, false, false, false))
        assertFalse(vigilanteDebeReenlazar(true, true, 0, 5_000, yaReenlazo = true, enPausa = false, errorRecuperable = false))
        assertFalse(vigilanteDebeReenlazar(true, true, 0, 5_000, yaReenlazo = false, enPausa = true, errorRecuperable = false))
        assertFalse(vigilanteDebeReenlazar(false, true, 0, 5_000, false, false, false))
        assertFalse(vigilanteDebeReenlazar(true, false, 0, 5_000, false, false, false))
        assertFalse(vigilanteDebeReenlazar(true, true, 0, 5_000, yaReenlazo = false, enPausa = false, errorRecuperable = true))
    }
}
