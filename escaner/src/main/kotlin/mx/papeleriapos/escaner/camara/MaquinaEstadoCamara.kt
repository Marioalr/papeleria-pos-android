package mx.papeleriapos.escaner.camara

import androidx.camera.core.CameraState

/**
 * La secuencia de estados de la cámara de un controlador, sin Android (se prueba en la JVM): qué hacer con cada
 * `CameraState` que llega y cuántos reintentos van. El controlador solo aplica lo que devuelve [alEstado].
 *
 * - Cada enlace tiene su número ([nuevoEnlace]) y su propio oyente que lo lleva. Lo que llega con un número viejo (lo
 *   que se publicó antes de volver a enlazar y seguía en la cola del hilo principal) se descarta: así un STREAM_CONFIG
 *   o un error crítico del enlace anterior no se cuenta otra vez, un IN_USE viejo no vuelve a poner el error tras
 *   «Reintentar» y un OPEN viejo no marca la apertura. Por eso tampoco se procesa `cameraState.value` al enlazar: es
 *   el valor del enlace anterior.
 * - Con el ciclo de vida detenido (`activo = false`) no se reacciona: el oyente es por cámara, y otro controlador (el
 *   escaneo rápido sobre la app) puede estar usándola.
 * - Un error crítico se reintenta solo cuando CameraX ya se rindió (CLOSING o CLOSED): con OPENING o PENDING_OPEN lo
 *   está reintentando por su cuenta (CameraPipe, `willAttemptRetry`). Con un reintento ya programado no se programa
 *   otro ni se da por perdido: se espera a ver qué pasa con el siguiente enlace.
 *
 * Solo en el hilo principal.
 */
internal class MaquinaEstadoCamara {
    /** El número del enlace vigente; lo que llegue con otro se descarta. */
    var enlace = 0
        private set

    /** Reenlaces por error crítico desde la última vez que abrió (o desde «Reintentar» o la vuelta al frente). */
    var reintentos = 0
        private set

    /** Hay un reenlace por error crítico programado (esperando su plazo). */
    var reintentoPendiente = false
        private set

    /** La cámara de este enlace está abierta ahora (el último estado fue OPEN): el vigilante cuenta desde ahí. */
    var abierta = false
        private set

    /** El enlace vigente lleva un rango de FPS fijo (un STREAM_CONFIG se reintenta sin él). */
    private var fpsFijo = false

    /** El último estado procesado de este enlace (tipo y error), para no repetir el mismo. */
    private var ultimaClave = SIN_CLAVE

    /** Se acaba de enlazar: devuelve el número que debe llevar el oyente de este enlace. */
    fun nuevoEnlace(fpsFijo: Boolean): Int {
        enlace++
        this.fpsFijo = fpsFijo
        abierta = false
        ultimaClave = SIN_CLAVE
        // Un enlace nuevo deja sin sentido el reenlace que estuviera esperando (el controlador lo cancela).
        reintentoPendiente = false
        return enlace
    }

    /** Se soltó la cámara (inactividad, cerrar, o antes de volver a enlazar): lo que siga llegando ya no cuenta. */
    fun soltar() {
        enlace++
        abierta = false
        ultimaClave = SIN_CLAVE
        reintentoPendiente = false
    }

    /** «Reintentar»: los reintentos automáticos vuelven a empezar (hasta [MAX_REINTENTOS_CRITICOS] con espera creciente). */
    fun reiniciarReintentos() {
        reintentos = 0
    }

    /** El reenlace programado ya se hizo (o se canceló). */
    fun sinReintentoPendiente() {
        reintentoPendiente = false
    }

    /**
     * El ciclo se detuvo (segundo plano): la cámara se cierra sola. Al volver al frente es otra apertura: el primer
     * estado se procesa aunque repita el último y los reintentos empiezan de nuevo.
     */
    fun alDetenerse() {
        abierta = false
        ultimaClave = SIN_CLAVE
        reintentoPendiente = false
        reintentos = 0
    }

    /**
     * Un `CameraState` que llegó al oyente del enlace [enlace]. [activo]: el ciclo de vida del controlador está al
     * menos en STARTED. Devuelve [AccionEstado.IGNORAR] si no es de este enlace, si el ciclo está detenido o si repite
     * el anterior; si no, lo que hay que hacer.
     */
    fun alEstado(enlace: Int, tipo: CameraState.Type, codigoError: Int, activo: Boolean): ReaccionEstado {
        if (enlace != this.enlace) return IGNORADA
        if (!activo) {
            ultimaClave = SIN_CLAVE
            abierta = false
            return IGNORADA
        }
        val clave = tipo.ordinal * 100 + codigoError
        if (clave == ultimaClave) return IGNORADA
        ultimaClave = clave
        abierta = tipo == CameraState.Type.OPEN
        val r = reaccionEstado(tipo, codigoError, reintentos, fpsFijo)
        return when (r.accion) {
            AccionEstado.ABIERTA -> {
                // Abrió bien: el reenlace que estuviera esperando sobra (el controlador lo cancela).
                reintentos = 0
                reintentoPendiente = false
                r
            }
            AccionEstado.REENLAZAR -> if (reintentoPendiente) SIN_ACCION else {
                reintentos++
                reintentoPendiente = true
                r
            }
            // Con un reenlace en camino todavía no se sabe si falla: se espera a su resultado.
            AccionEstado.ERROR_FINAL -> if (reintentoPendiente) SIN_ACCION else r
            AccionEstado.REENLAZAR_SIN_FPS -> {
                fpsFijo = false
                r
            }
            else -> r
        }
    }

    private companion object {
        const val SIN_CLAVE = -1
        val IGNORADA = ReaccionEstado(AccionEstado.IGNORAR)
        val SIN_ACCION = ReaccionEstado(AccionEstado.NINGUNA)
    }
}
