package mx.papeleriapos.escaner.camara

import mx.papeleriapos.escaner.Lente
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
        assertEquals(RangoEnCache.AutoPorError, decodificarRango(codificarRango(huella, null, porError = true), huella))
        assertEquals("$huella|auto-error", codificarRango(huella, null, porError = true))
        // Con rango, porError no cuenta.
        assertEquals(RangoEnCache.Fijo(30, 30), decodificarRango(codificarRango(huella, 30 to 30, porError = true), huella))
        // Otra versión del sistema: se vuelve a calcular (también lo que se rechazó).
        assertEquals(RangoEnCache.SinDato, decodificarRango(codificarRango(huella, 30 to 30), "$huella-2"))
        assertEquals(RangoEnCache.SinDato, decodificarRango(codificarRango(huella, null, porError = true), "$huella-2"))
        assertEquals(RangoEnCache.SinDato, decodificarRango(null, huella))
    }

    @Test
    fun unRangoGuardadoRaroNoSeUsa() {
        val h = "h"
        for (texto in listOf("h|", "h|30", "h|-30", "h|a-30", "h|30-b", "h|31-30", "h|0-30", "30-30", "", "h|auto-", "h|error")) {
            assertEquals(texto, RangoEnCache.SinDato, decodificarRango(texto, h))
        }
    }

    @Test
    fun elRangoQueSeGuardaEsElQueSeElige() {
        // Las mismas reglas que al calcularlo: con tope 30, 30 fijo si se puede; si no, 15–30.
        val rangos = listOf(15 to 15, 15 to 30, 24 to 30, 30 to 30, 7 to 60)
        assertEquals(30 to 30, elegirRangoFps(rangos, PreferenciaFps(fijo = true, tope = 30)))
        assertEquals(15 to 30, elegirRangoFps(rangos, PreferenciaFps(fijo = false, tope = 30)))
        assertNull(elegirRangoFps(emptyList(), PreferenciaFps(fijo = true, tope = 30)))
    }

    @Test
    fun conTope60UsaLos60QueDaLaCamaraOSeQuedaEnLoMasRapido() {
        // Los rangos del Pixel 8 Pro.
        val pixel = listOf(15 to 15, 15 to 24, 24 to 24, 15 to 30, 24 to 30, 30 to 30, 15 to 60, 60 to 60)
        assertEquals(60 to 60, elegirRangoFps(pixel, PreferenciaFps(fijo = true, tope = 60)))
        assertEquals(15 to 60, elegirRangoFps(pixel, PreferenciaFps(fijo = false, tope = 60)))
        // Una cámara que no llega a 60: lo más rápido que tenga.
        val sin60 = listOf(15 to 30, 30 to 30)
        assertEquals(30 to 30, elegirRangoFps(sin60, PreferenciaFps(fijo = true, tope = 60)))
        assertEquals(15 to 30, elegirRangoFps(sin60, PreferenciaFps(fijo = false, tope = 60)))
        // Con 60 solo variable, el fijo toma el mínimo más alto de los de 60.
        assertEquals(7 to 60, elegirRangoFps(listOf(7 to 60, 30 to 30), PreferenciaFps(fijo = true, tope = 60)))
    }

    @Test
    fun laLenteFisicaSeEligePorFocalYSensor() {
        // Las cámaras físicas del Pixel 8 Pro (dumpsys media.camera): la principal completa y su recorte 2x (6.9 mm), el
        // teleobjetivo y su recorte (18 mm) y la gran angular (2.23 mm).
        val pixel = listOf(
            LenteFisica("2", 6.9f, 9.792f * 7.3728f),
            LenteFisica("5", 6.9f, 4.896f * 3.6864f),
            LenteFisica("4", 18f, 5.6448f * 4.2336f),
            LenteFisica("6", 18f, 2.8224f * 2.1168f),
            LenteFisica("3", 2.23f, 6.4f * 4.8f),
        )
        assertEquals("2", elegirLenteFisica(pixel, 6.9f, Lente.PRINCIPAL))
        assertEquals("3", elegirLenteFisica(pixel, 6.9f, Lente.GRAN_ANGULAR))
        assertNull(elegirLenteFisica(pixel, 6.9f, Lente.SISTEMA))
        // Sin la focal de la lógica: la de sensor más grande.
        assertEquals("2", elegirLenteFisica(pixel, null, Lente.PRINCIPAL))
        // Una sola física (o ninguna): la lógica.
        assertNull(elegirLenteFisica(listOf(LenteFisica("0", 4f, 30f)), 4f, Lente.PRINCIPAL))
        assertNull(elegirLenteFisica(emptyList(), null, Lente.GRAN_ANGULAR))
    }

    @Test
    fun lasLlavesDe30SonLasDeAntes() {
        // Lo guardado antes de que hubiera 60 sigue sirviendo.
        assertEquals("fps_30", PreferenciaFps(fijo = true, tope = 30).llave)
        assertEquals("fps_15_30", PreferenciaFps(fijo = false, tope = 30).llave)
        assertEquals("fps_60", PreferenciaFps(fijo = true, tope = 60).llave)
        assertEquals("fps_15_60", PreferenciaFps(fijo = false, tope = 60).llave)
    }

    @Test
    fun elAutoPorErrorEsPegajosoYLaValidacionNoLoSobrescribe() {
        // El caso del hallazgo: la cámara anuncia 30-30 pero la sesión lo rechaza (STREAM_CONFIG). Se guarda
        // «auto-error» y en el proceso siguiente no se valida: si se validara, volvería a guardar 30-30 y una apertura
        // de cada dos repetiría el error y el reenlace.
        assertFalse(debeValidarRangoFps(RangoEnCache.AutoPorError, sinFpsFijo = false, yaValidado = false))
        // En el mismo proceso, tras el rechazo, tampoco.
        assertFalse(debeValidarRangoFps(RangoEnCache.Fijo(30, 30), sinFpsFijo = true, yaValidado = false))
        // Lo guardado de la manera normal sí, una vez por proceso.
        assertTrue(debeValidarRangoFps(RangoEnCache.Fijo(30, 30), sinFpsFijo = false, yaValidado = false))
        assertTrue(debeValidarRangoFps(RangoEnCache.Auto, sinFpsFijo = false, yaValidado = false))
        assertFalse(debeValidarRangoFps(RangoEnCache.Fijo(30, 30), sinFpsFijo = false, yaValidado = true))
        // Calculado en este mismo enlace: no hay nada que confirmar.
        assertFalse(debeValidarRangoFps(RangoEnCache.SinDato, sinFpsFijo = false, yaValidado = false))
    }

    // ---- Estado de la cámara ----

    @Test
    fun abiertaLimpiaElErrorSinImportarElCodigo() {
        assertEquals(AccionEstado.ABIERTA, reaccionEstado(OPEN, 0, 2, fpsFijo = true).accion)
        assertEquals(AccionEstado.ABIERTA, reaccionEstado(OPEN, CameraState.ERROR_CAMERA_IN_USE, 0, fpsFijo = true).accion)
        for (tipo in listOf(CameraState.Type.PENDING_OPEN, OPENING, CLOSING, CLOSED)) {
            assertEquals(AccionEstado.NINGUNA, reaccionEstado(tipo, 0, 0, fpsFijo = true).accion)
        }
    }

    @Test
    fun otraAppConLaCamaraAvisaYCameraXReintenta() {
        for (codigo in listOf(CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE)) {
            for (tipo in listOf(CameraState.Type.PENDING_OPEN, OPENING)) {
                val r = reaccionEstado(tipo, codigo, 0, fpsFijo = true)
                assertEquals(AccionEstado.AVISO_RECUPERABLE, r.accion)
                assertEquals("Otra app está usando la cámara", r.mensaje)
            }
        }
        // Un tropiezo recuperable no se avisa.
        assertEquals(AccionEstado.NINGUNA, reaccionEstado(OPENING, CameraState.ERROR_OTHER_RECOVERABLE_ERROR, 0, true).accion)
    }

    @Test
    fun desactivadaONoMolestarSoloAvisan() {
        val d = reaccionEstado(CLOSING, CameraState.ERROR_CAMERA_DISABLED, 0, fpsFijo = true)
        assertEquals(AccionEstado.AVISO, d.accion)
        assertTrue(d.mensaje!!.contains("desactivada"))
        val n = reaccionEstado(CLOSED, CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED, 0, fpsFijo = true)
        assertEquals(AccionEstado.AVISO, n.accion)
        assertTrue(n.mensaje!!.contains("No molestar"))
    }

    @Test
    fun configuracionNoAceptadaVuelveSinFpsFijoUnaSolaVez() {
        // Con FPS fijo se atiende ya, aunque CameraX lo esté reintentando (la misma configuración volvería a fallar).
        assertEquals(AccionEstado.REENLAZAR_SIN_FPS, reaccionEstado(CLOSING, CameraState.ERROR_STREAM_CONFIG, 0, fpsFijo = true).accion)
        assertEquals(AccionEstado.REENLAZAR_SIN_FPS, reaccionEstado(OPENING, CameraState.ERROR_STREAM_CONFIG, 0, fpsFijo = true).accion)
        // Ya sin FPS fijo: se trata como error crítico (reintentos y luego error).
        assertEquals(AccionEstado.REENLAZAR, reaccionEstado(CLOSED, CameraState.ERROR_STREAM_CONFIG, 0, fpsFijo = false).accion)
        assertEquals(AccionEstado.ERROR_FINAL, reaccionEstado(CLOSED, CameraState.ERROR_STREAM_CONFIG, 2, fpsFijo = false).accion)
    }

    @Test
    fun errorCriticoReintentaDosVecesConEsperaCrecienteYLuegoFalla() {
        for (codigo in listOf(CameraState.ERROR_CAMERA_FATAL_ERROR, CameraState.ERROR_CAMERA_REMOVED)) {
            val r0 = reaccionEstado(CLOSING, codigo, 0, fpsFijo = false)
            val r1 = reaccionEstado(CLOSED, codigo, 1, fpsFijo = false)
            val r2 = reaccionEstado(CLOSING, codigo, 2, fpsFijo = false)
            assertEquals(AccionEstado.REENLAZAR, r0.accion)
            assertEquals(500L, r0.esperaMs)
            assertEquals(AccionEstado.REENLAZAR, r1.accion)
            assertEquals(1_000L, r1.esperaMs)
            assertEquals(AccionEstado.ERROR_FINAL, r2.accion)
            assertEquals("La cámara falló. Si sigue, reinicia el celular.", r2.mensaje)
        }
    }

    @Test
    fun conOpeningOPendingOpenUnErrorCriticoNoSeReintentaPorqueCameraXYaLoHace() {
        for (tipo in listOf(OPENING, CameraState.Type.PENDING_OPEN)) {
            for (codigo in listOf(CameraState.ERROR_CAMERA_FATAL_ERROR, CameraState.ERROR_STREAM_CONFIG)) {
                assertEquals(AccionEstado.NINGUNA, reaccionEstado(tipo, codigo, 0, fpsFijo = false).accion)
                // Ni siquiera con los reintentos gastados: todavía no se rindió.
                assertEquals(AccionEstado.NINGUNA, reaccionEstado(tipo, codigo, 2, fpsFijo = false).accion)
            }
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
    fun elVigilanteReenlazaUnaVezSiConLaCamaraAbiertaYLaPantallaAlFrenteNoSaleNingunCuadro() {
        assertTrue(vigilanteDebeReenlazar(true, true, 0, 2_000, yaReenlazo = false, enPausa = false, errorRecuperable = false))
        // Salió algún cuadro, falta tiempo desde que abrió, ya lo hizo, en pausa, en segundo plano u otra app con la
        // cámara: no.
        assertFalse(vigilanteDebeReenlazar(true, true, 1, 2_000, false, false, false))
        assertFalse(vigilanteDebeReenlazar(true, true, 0, 1_999, false, false, false))
        assertFalse(vigilanteDebeReenlazar(true, true, 0, 5_000, yaReenlazo = true, enPausa = false, errorRecuperable = false))
        assertFalse(vigilanteDebeReenlazar(true, true, 0, 5_000, yaReenlazo = false, enPausa = true, errorRecuperable = false))
        assertFalse(vigilanteDebeReenlazar(false, true, 0, 5_000, false, false, false))
        assertFalse(vigilanteDebeReenlazar(true, true, 0, 5_000, yaReenlazo = false, enPausa = false, errorRecuperable = true))
        // La cámara todavía no abre (en frío, en gama baja, abrir puede tardar más que el plazo): no se reenlaza
        // aunque desde el enlace o desde ON_RESUME ya hayan pasado más de 2 s; el plazo cuenta desde OPEN.
        assertFalse(vigilanteDebeReenlazar(true, abierta = false, cuadrosNuevos = 0, msDesdeAbierta = 5_000, false, false, false))
    }

    private companion object {
        val OPEN = CameraState.Type.OPEN
        val OPENING = CameraState.Type.OPENING
        val CLOSING = CameraState.Type.CLOSING
        val CLOSED = CameraState.Type.CLOSED
    }
}
