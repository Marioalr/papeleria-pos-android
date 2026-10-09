package mx.papeleriapos.escaner.camara

import androidx.camera.core.CameraState
import androidx.camera.core.CameraState.Type.CLOSED
import androidx.camera.core.CameraState.Type.CLOSING
import androidx.camera.core.CameraState.Type.OPEN
import androidx.camera.core.CameraState.Type.OPENING
import androidx.camera.core.CameraState.Type.PENDING_OPEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La secuencia de estados como la ve el controlador: cada enlace con su número, lo viejo que sigue en la cola del hilo
 * principal, el ciclo detenido y los reintentos. Cada prueba reproduce uno de los casos del hallazgo.
 */
class MaquinaEstadoCamaraTest {
    private val m = MaquinaEstadoCamara()

    /** Lo que hace el controlador con cada acción que cambia algo: así se cuentan los enlaces de verdad. */
    private var enlaces = 0
    private val acciones = mutableListOf<AccionEstado>()

    private fun enlazar(fpsFijo: Boolean): Int {
        enlaces++
        return m.nuevoEnlace(fpsFijo)
    }

    private fun estado(enlace: Int, tipo: CameraState.Type, error: Int = 0, activo: Boolean = true): ReaccionEstado =
        m.alEstado(enlace, tipo, error, activo).also { if (it.accion != AccionEstado.IGNORAR) acciones += it.accion }

    @Test
    fun streamConfigReenlazaSinFpsUnaSolaVezYElErrorViejoNoProvocaOtroReenlace() {
        val e1 = enlazar(fpsFijo = true)
        estado(e1, OPENING)
        assertEquals(AccionEstado.REENLAZAR_SIN_FPS, estado(e1, CLOSING, CameraState.ERROR_STREAM_CONFIG).accion)
        // El controlador vuelve a enlazar sin FPS fijo.
        val e2 = enlazar(fpsFijo = false)
        // Lo del enlace anterior que seguía en la cola (antes se volvía a procesar el STREAM_CONFIG con fpsFijo = false
        // y se programaba otro reenlace a 500 ms que cerraba una cámara que ya había abierto bien).
        assertEquals(AccionEstado.IGNORAR, estado(e1, CLOSING, CameraState.ERROR_STREAM_CONFIG).accion)
        assertEquals(AccionEstado.IGNORAR, estado(e1, CLOSED).accion)
        // El enlace nuevo abre.
        estado(e2, CLOSED)
        estado(e2, OPENING)
        assertEquals(AccionEstado.ABIERTA, estado(e2, OPEN).accion)
        assertEquals(2, enlaces)
        assertEquals(1, acciones.count { it == AccionEstado.REENLAZAR_SIN_FPS })
        assertFalse(acciones.contains(AccionEstado.REENLAZAR))
        assertTrue(m.abierta)
    }

    @Test
    fun unErrorFatalSeReintentaDosVecesDeVerdadYSoloDespuesSeDaPorPerdido() {
        val e1 = enlazar(fpsFijo = false)
        estado(e1, OPENING)
        // CameraX todavía lo reintenta por su cuenta: no se hace nada.
        assertEquals(AccionEstado.NINGUNA, estado(e1, OPENING, CameraState.ERROR_CAMERA_FATAL_ERROR).accion)
        val r1 = estado(e1, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR)
        assertEquals(AccionEstado.REENLAZAR, r1.accion)
        assertEquals(500L, r1.esperaMs)
        // Mientras espera, otro error del mismo enlace no programa otro reenlace ni lo da por perdido.
        assertEquals(AccionEstado.NINGUNA, estado(e1, CLOSED, CameraState.ERROR_CAMERA_FATAL_ERROR).accion)
        assertTrue(m.reintentoPendiente)

        // Se cumple el plazo: segundo enlace. Lo viejo que llega después no gasta reintentos.
        m.sinReintentoPendiente()
        val e2 = enlazar(fpsFijo = false)
        assertEquals(AccionEstado.IGNORAR, estado(e1, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR).accion)
        estado(e2, OPENING)
        val r2 = estado(e2, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR)
        assertEquals(AccionEstado.REENLAZAR, r2.accion)
        assertEquals(1_000L, r2.esperaMs)

        // Tercer enlace: el ERROR_FINAL se decide con su propio resultado, no con el estado viejo.
        m.sinReintentoPendiente()
        val e3 = enlazar(fpsFijo = false)
        assertEquals(AccionEstado.IGNORAR, estado(e2, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR).accion)
        assertEquals(AccionEstado.NINGUNA, estado(e3, OPENING).accion)
        assertEquals(AccionEstado.ERROR_FINAL, estado(e3, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR).accion)

        assertEquals(3, enlaces)
        assertEquals(listOf(AccionEstado.REENLAZAR, AccionEstado.REENLAZAR), acciones.filter { it == AccionEstado.REENLAZAR })
        assertEquals(2, m.reintentos)
    }

    @Test
    fun siElTercerEnlaceAbreNoHayErrorFinal() {
        val e1 = enlazar(fpsFijo = false)
        estado(e1, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR)
        m.sinReintentoPendiente()
        val e2 = enlazar(fpsFijo = false)
        estado(e2, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR)
        m.sinReintentoPendiente()
        val e3 = enlazar(fpsFijo = false)
        assertEquals(AccionEstado.ABIERTA, estado(e3, OPEN).accion)
        assertFalse(acciones.contains(AccionEstado.ERROR_FINAL))
        assertEquals(0, m.reintentos)
    }

    @Test
    fun enUsoYLuegoReintentarNoVuelveAPonerElErrorViejo() {
        val e1 = enlazar(fpsFijo = true)
        val aviso = estado(e1, PENDING_OPEN, CameraState.ERROR_CAMERA_IN_USE)
        assertEquals(AccionEstado.AVISO_RECUPERABLE, aviso.accion)
        // «Reintentar»: los reintentos desde cero y otro enlace.
        m.reiniciarReintentos()
        val e2 = enlazar(fpsFijo = true)
        // El IN_USE viejo (antes ponía otra vez la fase ERROR y sonaba el tono aunque la cámara ya estuviera libre).
        assertEquals(AccionEstado.IGNORAR, estado(e1, PENDING_OPEN, CameraState.ERROR_CAMERA_IN_USE).accion)
        estado(e2, CLOSING)
        estado(e2, CLOSED)
        estado(e2, OPENING)
        assertEquals(AccionEstado.ABIERTA, estado(e2, OPEN).accion)
        assertEquals(1, acciones.count { it == AccionEstado.AVISO_RECUPERABLE })
    }

    @Test
    fun unOpenViejoNoCuentaComoCamaraAbierta() {
        val e1 = enlazar(fpsFijo = true)
        assertEquals(AccionEstado.ABIERTA, estado(e1, OPEN).accion)
        // Relanzamiento rápido (tibio): enlace nuevo; el OPEN del anterior llega tarde y no debe marcar CAMARA_ABIERTA.
        val e2 = enlazar(fpsFijo = true)
        assertFalse(m.abierta)
        assertEquals(AccionEstado.IGNORAR, estado(e1, OPEN).accion)
        assertFalse(m.abierta)
        estado(e2, OPENING)
        assertEquals(AccionEstado.ABIERTA, estado(e2, OPEN).accion)
        assertTrue(m.abierta)
    }

    @Test
    fun conElCicloDetenidoNoSeReaccionaYAlVolverSeProcesaAunqueRepita() {
        val e1 = enlazar(fpsFijo = false)
        assertEquals(AccionEstado.ABIERTA, estado(e1, OPEN).accion)
        // Segundo plano: el escaneo rápido usa la cámara; sus estados llegan a este oyente (es por cámara).
        m.alDetenerse()
        assertEquals(AccionEstado.IGNORAR, estado(e1, CLOSING, activo = false).accion)
        assertEquals(AccionEstado.IGNORAR, estado(e1, CLOSED, CameraState.ERROR_CAMERA_FATAL_ERROR, activo = false).accion)
        assertEquals(AccionEstado.IGNORAR, estado(e1, OPEN, activo = false).accion)
        assertFalse(m.abierta)
        assertEquals(0, m.reintentos)
        // De vuelta al frente: el mismo enlace reabre y el OPEN cuenta (es otra apertura).
        assertEquals(AccionEstado.ABIERTA, estado(e1, OPEN).accion)
    }

    @Test
    fun abrirCancelaElReintentoPendienteYReiniciaLosReintentos() {
        val e1 = enlazar(fpsFijo = false)
        estado(e1, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR)
        assertTrue(m.reintentoPendiente)
        assertEquals(1, m.reintentos)
        // CameraX la recuperó sola antes del plazo: el reenlace programado sobra.
        assertEquals(AccionEstado.ABIERTA, estado(e1, OPEN).accion)
        assertFalse(m.reintentoPendiente)
        assertEquals(0, m.reintentos)
    }

    @Test
    fun reintentarDespuesDelErrorFinalVuelveADarDosReintentos() {
        var e = enlazar(fpsFijo = false)
        repeat(2) {
            estado(e, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR)
            m.sinReintentoPendiente()
            e = enlazar(fpsFijo = false)
        }
        assertEquals(AccionEstado.ERROR_FINAL, estado(e, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR).accion)
        // «Reintentar» (abrir con forzar): antes el siguiente error iba directo a ERROR_FINAL.
        m.reiniciarReintentos()
        val otro = enlazar(fpsFijo = false)
        val r = estado(otro, CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR)
        assertEquals(AccionEstado.REENLAZAR, r.accion)
        assertEquals(500L, r.esperaMs)
    }

    @Test
    fun soltarLaCamaraDescartaLoQueSigaLlegando() {
        val e1 = enlazar(fpsFijo = false)
        estado(e1, OPEN)
        // Apagada por inactividad: un error que llegue después no debe volver a enlazar una cámara que se soltó a propósito.
        m.soltar()
        assertEquals(AccionEstado.IGNORAR, estado(e1, CLOSED, CameraState.ERROR_CAMERA_FATAL_ERROR).accion)
        assertFalse(m.reintentoPendiente)
        assertFalse(m.abierta)
    }

    @Test
    fun elMismoEstadoSeguidoSeProcesaUnaVez() {
        val e1 = enlazar(fpsFijo = false)
        assertEquals(AccionEstado.NINGUNA, estado(e1, OPENING).accion)
        assertEquals(AccionEstado.IGNORAR, estado(e1, OPENING).accion)
        assertEquals(AccionEstado.ABIERTA, estado(e1, OPEN).accion)
        assertEquals(AccionEstado.IGNORAR, estado(e1, OPEN).accion)
        // Se cerró y volvió a abrir sola (p. ej. tras un error recuperable): el OPEN vuelve a contar.
        estado(e1, OPENING, CameraState.ERROR_OTHER_RECOVERABLE_ERROR)
        assertFalse(m.abierta)
        assertEquals(AccionEstado.ABIERTA, estado(e1, OPEN).accion)
    }
}
