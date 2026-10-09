package mx.papeleriapos.escaner.camara

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.CuadroY
import mx.papeleriapos.escaner.Diagnostico
import mx.papeleriapos.escaner.EstadoEscaner
import mx.papeleriapos.escaner.EventoLectura
import mx.papeleriapos.escaner.Fase
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.MotorDeLectura
import mx.papeleriapos.escaner.motores.crearMotor
import mx.papeleriapos.escaner.nucleo.Caja
import mx.papeleriapos.escaner.nucleo.Confirmador
import mx.papeleriapos.escaner.nucleo.Guia
import mx.papeleriapos.escaner.nucleo.Indicacion
import mx.papeleriapos.escaner.nucleo.Lectura
import mx.papeleriapos.escaner.nucleo.Localizacion
import mx.papeleriapos.escaner.nucleo.Localizador
import mx.papeleriapos.escaner.nucleo.Observacion
import mx.papeleriapos.escaner.nucleo.Rectangulo
import mx.papeleriapos.escaner.nucleo.ReglasConfirmacion
import mx.papeleriapos.escaner.nucleo.ResultadoMotor
import mx.papeleriapos.escaner.nucleo.Simbologia
import mx.papeleriapos.escaner.nucleo.Transformacion
import mx.papeleriapos.escaner.nucleo.Via
import java.nio.ByteBuffer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Lo que el analizador le pide a la cámara; el controlador lo pasa al hilo principal. */
internal interface AccionesCamara {
    fun aplicarZoom(zoom: Float)
    /** Enfoca y mide la luz en un punto del buffer de análisis, normalizado de 0 a 1 (sin rotar). */
    fun enfocar(xBuffer: Float, yBuffer: Float)
    fun encenderLinterna(encender: Boolean)
    /** Lleva [ConfigEscaner.segundosParaApagar] en pausa: soltar la cámara. */
    fun apagarPorInactividad()
}

/**
 * El análisis de cada cuadro (docs/escaner.md, «Cuadro por cuadro»): lee la franja con el motor, localiza el código en
 * el cuadro completo, junta todo en la región visible, confirma, guía y publica el estado.
 *
 * Todo corre en el hilo de análisis: los cuadros y las órdenes del controlador (que llegan por el mismo ejecutor), así
 * que el estado interno no necesita candados. Nada se asigna por cuadro salvo lo inevitable (lo que asignan CameraX y
 * los motores, el `CuadroY`, el resultado del motor) y lo que se publica cuando cambia (cajas, estado).
 */
internal class Analizador(
    private val estado: MutableStateFlow<EstadoEscaner>,
    private val eventos: MutableSharedFlow<EventoLectura>,
    private val config: StateFlow<ConfigEscaner>,
    private val senales: SenalesCamara,
    private val acciones: AccionesCamara,
) : ImageAnalysis.Analyzer {

    /** Para las pruebas: ve cada resultado del motor con su transformación, en el hilo de análisis. */
    @Volatile var observadorMotor: ((ResultadoMotor, Transformacion) -> Unit)? = null

    /** Para el diagnóstico: ve cada cuadro (antes del motor) con su transformación y la franja en el buffer. */
    @Volatile var observadorCuadro: ((ImageProxy, Transformacion, Rectangulo) -> Unit)? = null

    /** Lo escribe el observador de la linterna (hilo principal). */
    @Volatile var linternaEncendida = false

    /** La pantalla dejó de verse (hilo principal): los cuadros que sigan llegando se sueltan sin analizar. */
    @Volatile var detenido = false

    private var cerrado = false

    // Piezas.
    private var motor: MotorDeLectura? = null
    private var motorActual = Motor.AUTOMATICO
    private var simbologiasActuales: Set<Simbologia> = emptySet()
    private var localizador: Localizador? = null
    private var ladoLocalizador = 0
    private val guia = Guia()
    private var confirmador = Confirmador()
    private var msAusencia = -1L
    private val observacion = Observacion()

    // Geometría: se recalcula solo si cambia el recorte visible, la rotación, el tamaño o la franja.
    private var transformacion: Transformacion? = null
    private var cacheIzquierda = 0
    private var cacheArriba = 0
    private var cacheDerecha = 0
    private var cacheAbajo = 0
    private var cacheRotacion = -1
    private var cacheAncho = 0
    private var cacheAlto = 0
    private var cacheAltoFranja = -1f
    private var franja = franjaPara(ConfigEscaner().altoFranja)
    private var regionFranja = Rectangulo(0, 0, 0, 0)
    private var regionVisible = Rectangulo(0, 0, 0, 0)
    private var preferidoX = 0f
    private var preferidoY = 0f
    private var toleranciaBordeX = 0f
    private var toleranciaBordeY = 0f
    private var pasoLuz = 8
    private var textoResolucion = ""
    private var textoFranja = ""
    private val punto = FloatArray(2)
    private val esquinasLocalizador = FloatArray(8)

    // Tiempos y estado (nanos de elapsedRealtime).
    private var primerCuadro = false
    private var pausado = false
    private var inicioPausa = 0L
    private var apagadoPedido = false
    private var ultimaActividad = 0L
    private var inicioReanudar = 0L
    private var leidoHasta = 0L
    private var cajaLeida: Caja? = null
    private var hayCandidato = false
    private var inicioCandidato = 0L
    private var ultimoCandidato = 0L
    /** La última vez que hubo un código a la vista o una lectura. */
    private var ultimoIndicio = 0L
    private var verificadosSeguidos = 0
    private var indiciosSeguidos = 0
    private var previoX = Float.NaN
    private var previoY = Float.NaN
    private val indicaciones = ArrayList<Indicacion>(MAX_INDICACIONES)
    private var ultimaRegistrada: Indicacion? = null
    private var indicacionRegistrada: Indicacion? = null
    private var ultimoEnfoque = 0L
    private var lecturas = 0L
    private var ultimoEvento: EventoLectura? = null

    // Zoom propio.
    private var zoomInicial = 1f
    private var zoomTope = 1f
    private var zoomActual = 1f
    private var ultimoCambioZoom = 0L

    // Luz.
    private var brillo = -1f
    private var saturados = 0f
    private var oscuroDesde = 0L
    private var sugerirLinterna = false
    private var linternaAutomaticaPedida = false
    private var hayLinterna = false

    // Diagnóstico.
    private val promedioIntervalo = PromedioMovil()
    private val promedioMotor = PromedioMovil()
    private val promedioLocalizador = PromedioMovil()
    private val promedioCuadro = PromedioMovil()
    private var ultimoCuadro = 0L
    private var ultimoDiagnostico = 0L
    private var forzarDiagnostico = true
    private var rangoFps = ""
    private var errores = 0
    private var enError = false

    override fun analyze(imagen: ImageProxy) {
        try {
            if (!cerrado && !detenido) procesar(imagen)
        } catch (e: Throwable) {
            // Un error de análisis (o una biblioteca nativa que no cargó) no debe tumbar el POS: se avisa y se sigue.
            if (e is VirtualMachineError) throw e
            fallo(e)
        } finally {
            imagen.close()
        }
    }

    private fun procesar(imagen: ImageProxy) {
        val ahora = SystemClock.elapsedRealtimeNanos()
        if (!primerCuadro) {
            primerCuadro = true
            ultimaActividad = ahora
            if (inicioReanudar == 0L) inicioReanudar = ahora
            forzarDiagnostico = true
            // Al volver de segundo plano la cámara se abre de nuevo: el zoom propio se vuelve a pedir.
            if (zoomTope > 1f) acciones.aplicarZoom(zoomActual)
            if (!pausado) estado.update { it.copy(fase = Fase.ESCANEANDO, mensajeError = null) }
        }
        if (pausado) {
            // En pausa solo se sueltan los cuadros; pasado el plazo, se apaga la cámara.
            val apagar = config.value.segundosParaApagar
            if (!apagadoPedido && apagar > 0 && ahora - inicioPausa >= apagar * NANOS_POR_S) {
                apagadoPedido = true
                acciones.apagarPorInactividad()
            }
            return
        }
        Trace.beginSection("escaner.cuadro")
        try {
            analizar(imagen, ahora)
        } finally {
            Trace.endSection()
        }
    }

    private fun analizar(imagen: ImageProxy, ahora: Long) {
        val cfg = config.value
        val lector = motor ?: crear(cfg)
        val t = geometria(imagen, cfg)
        val plano = imagen.planes[0]
        val y = plano.buffer
        val pasoFila = plano.rowStride
        val ancho = imagen.width
        val alto = imagen.height

        // 1) El localizador mira el cuadro COMPLETO (~0.2 ms): tiene que poder guiar hacia la franja, y le dice al motor
        // automático si vale la pena su respaldo.
        Trace.beginSection("escaner.localizar")
        val inicioLocalizar = SystemClock.elapsedRealtimeNanos()
        val loc = try {
            localizador(ancho, alto).procesar(y, pasoFila, ancho, alto, preferidoX, preferidoY)
        } finally {
            Trace.endSection()
        }
        val nanosLocalizar = SystemClock.elapsedRealtimeNanos() - inicioLocalizar
        medirLuz(y, pasoFila)
        val pareceCodigo = pareceCodigo(loc)
        observadorCuadro?.invoke(imagen, t, regionFranja)

        // 2) El motor lee la franja (o lo visible, para comparar).
        Trace.beginSection("escaner.leer")
        val resultado = try {
            lector.leer(
                CuadroY(
                    y, pasoFila, ancho, alto, t.rotacion, if (cfg.recortarFranja) regionFranja else regionVisible, imagen,
                    regionVisible = regionVisible,
                    // Mientras haya un código cerca (también uno ya leído que sigue a la vista) el respaldo sigue
                    // corriendo: si dejara de verlo, el filtro de repetidos lo daría por retirado y contaría doble.
                    codigoProbable = pareceCodigo || hayCandidato || ahora - ultimoIndicio < NANOS_INDICIO,
                    umbralSugerido = if (pareceCodigo) loc.umbral else -1,
                ),
            )
        } finally {
            Trace.endSection()
        }
        observadorMotor?.invoke(resultado, t)

        // 3) Todo a la región visible.
        val leidas: List<Pair<Lectura, Caja?>> =
            if (resultado.lecturas.isEmpty()) emptyList()
            else resultado.lecturas.map { it to it.esquinasBuffer?.let { e -> cajaVisible(t, e, punto) } }
        val potenciales: List<Caja> =
            if (resultado.potencialesBuffer.isEmpty()) emptyList()
            else resultado.potencialesBuffer.map { cajaVisible(t, it, punto) }

        // 4) El candidato: lo leído, luego lo que el motor ve sin leer, luego el localizador si se sostiene 2 cuadros.
        val es1D = loc.encontrado && loc.tipo == 1
        verificadosSeguidos = if (es1D && loc.verificado) verificadosSeguidos + 1 else 0
        indiciosSeguidos = if (pareceCodigo) indiciosSeguidos + 1 else 0
        var caja: Caja? = masCercana(leidas)
        // ML Kit también ve «potenciales» en hojas rayadas y en texto: solo cuentan si el localizador ve barras ahí.
        if (caja == null && pareceCodigo) caja = masCercanaCaja(potenciales)?.takeIf { cercaDe(it, loc, t) }
        var delLocalizador = false
        if (caja == null && (verificadosSeguidos >= CUADROS_LOCALIZADOR || indiciosSeguidos >= CUADROS_INDICIO)) {
            caja = cajaLocalizador(loc, t)
            delLocalizador = true
        }
        val candidato = caja != null
        // Las medidas del localizador valen si habla del mismo código.
        val usarLocalizador = es1D && (delLocalizador || (caja != null && cercaDe(caja, loc, t)))

        val o = observacion
        o.reiniciar()
        o.nanos = ahora
        o.candidato = candidato
        if (caja != null) llenarPosicion(o, caja, loc, usarLocalizador, t) else {
            previoX = Float.NaN
            previoY = Float.NaN
        }
        if (usarLocalizador) {
            o.moduloPx = loc.moduloPx
            o.nitidez = loc.nitidez
            o.saturados = loc.saturados
        } else {
            o.saturados = saturados
        }
        o.brillo = brillo
        o.proporcionEnfoque = senales.proporcionEnfoque()
        o.enfocando = senales.enfocando
        o.desenfocado = senales.desenfocado
        o.zoomAlTope = zoomActual >= zoomTope - 0.01f

        // 5) Confirmar y guiar.
        val aceptada = confirmador.procesar(leidas, potenciales, franja, ahora)
        o.leido = aceptada != null
        o.yaLeido = candidato && confirmador.yaLeidoEnCuadro
        val indicacion = guia.actualizar(o)
        if (indicacion != indicacionRegistrada) {
            indicacionRegistrada = indicacion
            Log.i(
                ETIQUETA,
                "Indicación: $indicacion (dx %.2f dy %.2f largo %.2f módulo %.1f nitidez %.2f brillo %.0f saturados %.2f enfoque %.2f%s%s)"
                    .format(o.dx, o.dy, o.largoRelativo, o.moduloPx, o.nitidez, o.brillo, o.saturados, o.proporcionEnfoque,
                        if (o.enfocando) " enfocando" else "", if (o.cortado) " cortado" else ""),
            )
        }

        if (candidato || aceptada != null) ultimoIndicio = ahora
        if (candidato) {
            if (!hayCandidato) {
                hayCandidato = true
                inicioCandidato = ahora
                indicaciones.clear()
                ultimaRegistrada = null
            }
            ultimoCandidato = ahora
            ultimaActividad = ahora
        } else if (hayCandidato && ahora - ultimoCandidato > NANOS_GRACIA_CANDIDATO) {
            hayCandidato = false
        }
        if (hayCandidato && indicacion != ultimaRegistrada && esPista(indicacion) && indicaciones.size < MAX_INDICACIONES) {
            indicaciones.add(indicacion)
            ultimaRegistrada = indicacion
        }

        var evento: EventoLectura? = null
        if (aceptada != null) {
            lecturas++
            val ms = when {
                inicioCandidato > 0L -> (ahora - inicioCandidato) / NANOS_POR_MS
                inicioReanudar > 0L -> (ahora - inicioReanudar) / NANOS_POR_MS
                else -> -1L
            }
            evento = EventoLectura(
                aceptada, ms, ArrayList(indicaciones), motorActual, resultado.via, o.moduloPx, zoomActual, linternaEncendida,
            )
            ultimoEvento = evento
            eventos.tryEmit(evento)
            val c = aceptada.codigo
            // Del QR (tickets) no se registra el contenido.
            val valor = if (c.simbologia == Simbologia.QR) "(${c.normalizado.length} caracteres)" else c.normalizado
            Log.i(ETIQUETA, "Leído ${c.simbologia.nombre} $valor en $ms ms con ${motorActual.nombre}" +
                (if (resultado.via != Via.DIRECTA) " (${resultado.via})" else "") + (if (aceptada.repetida) ", repetido" else ""))
            leidoHasta = ahora + NANOS_LEIDO
            cajaLeida = aceptada.caja ?: caja
            // El siguiente código empieza su propio conteo de tiempo e indicaciones.
            hayCandidato = false
            inicioCandidato = 0L
            indicaciones.clear()
            ultimaActividad = ahora
        }

        ajustarZoom(o, candidato, aceptada != null, ahora)
        if (caja != null && aceptada == null) enfocarCandidato(caja, t, ahora)
        ajustarLinterna(cfg, ahora)

        val nanosCuadro = SystemClock.elapsedRealtimeNanos() - ahora
        if (ultimoCuadro > 0L) promedioIntervalo.agregar((ahora - ultimoCuadro).toFloat())
        ultimoCuadro = ahora
        promedioMotor.agregar(resultado.nanos / 1e6f)
        promedioLocalizador.agregar(nanosLocalizar / 1e6f)
        promedioCuadro.agregar(nanosCuadro / 1e6f)

        val leyendo = ahora < leidoHasta
        val fase = when {
            leyendo -> Fase.LEIDO
            candidato -> Fase.VIENDO
            else -> Fase.ESCANEANDO
        }
        publicar(
            fase = fase,
            indicacion = if (leyendo) Indicacion.LEIDO else indicacion,
            caja = caja ?: if (leyendo) cajaLeida else null,
            evento = evento,
            o = o,
            ahora = ahora,
        )

        if (cfg.segundosParaPausar > 0 && ahora - ultimaActividad >= cfg.segundosParaPausar * NANOS_POR_S) pausar(ahora)
    }

    /** Publica el estado solo si cambió algo que se ve (la caja del código sí cambia en cada cuadro). */
    private fun publicar(fase: Fase, indicacion: Indicacion, caja: Caja?, evento: EventoLectura?, o: Observacion, ahora: Long) {
        val diagnostico = if (forzarDiagnostico || ahora - ultimoDiagnostico >= NANOS_DIAGNOSTICO) {
            forzarDiagnostico = false
            ultimoDiagnostico = ahora
            diagnostico(o)
        } else {
            null
        }
        val salirDeError = enError
        enError = false
        errores = 0
        val e = estado.value
        if (e.fase == fase && e.indicacion == indicacion && e.cajaCodigo == caja && evento == null &&
            e.sugerirLinterna == sugerirLinterna && e.zoom == zoomActual && diagnostico == null && !salirDeError
        ) return
        estado.update {
            it.copy(
                fase = fase,
                indicacion = indicacion,
                cajaCodigo = caja,
                ultima = evento ?: it.ultima,
                lecturas = lecturas,
                sugerirLinterna = sugerirLinterna,
                zoom = zoomActual,
                mensajeError = if (salirDeError) null else it.mensajeError,
                diagnostico = diagnostico ?: it.diagnostico,
            )
        }
    }

    private fun diagnostico(o: Observacion): Diagnostico {
        val intervalo = promedioIntervalo.valor
        return Diagnostico(
            motor = motorActual,
            resolucion = textoResolucion,
            franjaPx = textoFranja,
            cuadrosPorSegundo = if (intervalo > 0f) 1e9f / intervalo else 0f,
            msMotor = promedioMotor.valor,
            msLocalizador = promedioLocalizador.valor,
            msCuadro = promedioCuadro.valor,
            moduloPx = o.moduloPx,
            nitidez = o.nitidez,
            brillo = brillo,
            zoom = zoomActual,
            rangoFps = rangoFps,
            camaraFisica = senales.camaraFisica,
            estadoEnfoque = senales.textoAf(),
        )
    }

    /** Posición, tamaño, bordes y movimiento del candidato respecto a la franja. */
    private fun llenarPosicion(o: Observacion, caja: Caja, loc: Localizacion, usarLocalizador: Boolean, t: Transformacion) {
        val f = franja
        o.dx = (caja.centroX - f.centroX) / f.ancho
        o.dy = (caja.centroY - f.centroY) / f.alto
        o.largoRelativo = if (usarLocalizador && loc.largo > 0f) loc.largo / t.anchoDerecho / f.ancho else caja.ancho / f.ancho
        val izquierda = caja.izquierda <= toleranciaBordeX
        val derecha = caja.derecha >= 1f - toleranciaBordeX
        val arriba = caja.arriba <= toleranciaBordeY
        val abajo = caja.abajo >= 1f - toleranciaBordeY
        // Por un solo lado: hay que moverse; por los dos: no cabe (cortado, hay que alejarse).
        o.bordeX = if (izquierda == derecha) 0 else if (izquierda) -1 else 1
        o.bordeY = if (arriba == abajo) 0 else if (arriba) -1 else 1
        o.cortado = (usarLocalizador && loc.cortado) || (izquierda && derecha) || (arriba && abajo)
        val cx = caja.centroX
        val cy = caja.centroY
        o.movimiento = if (previoX.isNaN()) 0f else {
            hypot((cx - previoX) * t.anchoDerecho, (cy - previoY) * t.altoDerecho) / (f.ancho * t.anchoDerecho)
        }
        previoX = cx
        previoY = cy
    }

    /** Zoom propio: sube si el módulo es muy chico; vuelve al inicial tras leer o sin candidatos un rato. */
    private fun ajustarZoom(o: Observacion, candidato: Boolean, leido: Boolean, ahora: Long) {
        if (zoomTope <= zoomInicial + 0.01f) return
        val arribaDelInicial = zoomActual > zoomInicial + 0.01f
        if (arribaDelInicial && (leido || ahora - ultimoCandidato > NANOS_ZOOM_REGRESA)) {
            cambiarZoom(zoomInicial, ahora)
        } else if (candidato && o.moduloPx > 0f && o.moduloPx < MODULO_MINIMO_PX && zoomActual < zoomTope - 0.01f &&
            ahora - ultimoCambioZoom >= NANOS_ENTRE_ZOOMS
        ) {
            cambiarZoom(min(zoomActual * FACTOR_ZOOM, zoomTope), ahora)
        }
    }

    private fun cambiarZoom(zoom: Float, ahora: Long) {
        zoomActual = zoom
        ultimoCambioZoom = ahora
        acciones.aplicarZoom(zoom)
    }

    /** Un código a la vista que no se lee en 700 ms: enfocar ahí (máximo uno cada 2 s). */
    private fun enfocarCandidato(caja: Caja, t: Transformacion, ahora: Long) {
        if (inicioCandidato == 0L || ahora - inicioCandidato < NANOS_ENFOQUE_CANDIDATO || ahora - ultimoEnfoque < NANOS_ENTRE_ENFOQUES) return
        ultimoEnfoque = ahora
        aBuffer(t, caja.centroX.coerceIn(0f, 1f), caja.centroY.coerceIn(0f, 1f), punto)
        acciones.enfocar(punto[0] / cacheAncho, punto[1] / cacheAlto)
    }

    /** Poca luz durante 1 s: ofrecer la linterna (o prenderla si así se configuró). No se apaga por luz: ella misma la sube. */
    private fun ajustarLinterna(cfg: ConfigEscaner, ahora: Long) {
        if (!hayLinterna || linternaEncendida) {
            oscuroDesde = 0L
            sugerirLinterna = false
            return
        }
        if (brillo >= 0f && brillo < BRILLO_OSCURO) {
            if (oscuroDesde == 0L) oscuroDesde = ahora
            if (ahora - oscuroDesde >= NANOS_OSCURO) sugerirLinterna = true
        } else {
            oscuroDesde = 0L
            if (brillo > BRILLO_CLARO) sugerirLinterna = false
        }
        if (sugerirLinterna && cfg.linternaAutomatica && !linternaAutomaticaPedida) {
            linternaAutomaticaPedida = true
            acciones.encenderLinterna(true)
        }
    }

    private fun pausar(ahora: Long) {
        pausado = true
        inicioPausa = ahora
        apagadoPedido = false
        if (linternaEncendida) acciones.encenderLinterna(false)
        if (zoomActual != zoomInicial) cambiarZoom(zoomInicial, ahora)
        reiniciarSeguimiento()
        estado.update { it.copy(fase = Fase.PAUSADO, indicacion = Indicacion.APUNTA, cajaCodigo = null, sugerirLinterna = false) }
    }

    private fun reiniciarSeguimiento() {
        hayCandidato = false
        ultimoIndicio = 0L
        inicioCandidato = 0L
        verificadosSeguidos = 0
        indiciosSeguidos = 0
        previoX = Float.NaN
        previoY = Float.NaN
        indicaciones.clear()
        ultimaRegistrada = null
        leidoHasta = 0L
        cajaLeida = null
        sugerirLinterna = false
        oscuroDesde = 0L
        guia.reiniciar()
        confirmador.reiniciar()
    }

    // ---- Órdenes del controlador (llegan por el ejecutor de análisis) ----

    /** Crea o cambia el motor y las reglas; el motor viejo se cierra aquí, entre cuadros. */
    fun configurar(cfg: ConfigEscaner) {
        if (cerrado) return
        if (motor == null || cfg.motor != motorActual || cfg.simbologias != simbologiasActuales) {
            motor?.close()
            motor = null
            crear(cfg).calentar()
            forzarDiagnostico = true
        }
        if (cfg.msAusenciaParaRepetir != msAusencia) {
            msAusencia = cfg.msAusenciaParaRepetir
            confirmador = Confirmador(ReglasConfirmacion(msAusenciaParaRepetir = cfg.msAusenciaParaRepetir))
        }
    }

    /** La cámara acaba de enlazarse: se espera el primer cuadro y se aplica el zoom inicial. */
    fun alEnlazar(zoomInicial: Float, zoomTope: Float, hayLinterna: Boolean, rangoFps: String) {
        this.hayLinterna = hayLinterna
        this.rangoFps = rangoFps
        olvidarError()
        primerCuadro = false
        pausado = false
        apagadoPedido = false
        linternaAutomaticaPedida = false
        ultimoCuadro = 0L
        promedioIntervalo.reiniciar()
        senales.reiniciar()
        ajustarZoomBase(zoomInicial, zoomTope)
        ultimoEnfoque = SystemClock.elapsedRealtimeNanos()
        forzarDiagnostico = true
    }

    /** Cambia el zoom inicial y el tope (p. ej. al cambiar [ConfigEscaner.zoomMaximo]). */
    fun ajustarZoomBase(zoomInicial: Float, zoomTope: Float) {
        this.zoomInicial = zoomInicial
        this.zoomTope = max(zoomTope, zoomInicial)
        cambiarZoom(zoomInicial, SystemClock.elapsedRealtimeNanos())
    }

    /** Sale de la pausa o de la retícula verde y vuelve a escanear. */
    fun reanudar() {
        if (cerrado) return
        val ahora = SystemClock.elapsedRealtimeNanos()
        val estabaPausado = pausado
        olvidarError()
        pausado = false
        apagadoPedido = false
        ultimaActividad = ahora
        inicioReanudar = ahora
        linternaAutomaticaPedida = false
        if (estabaPausado) reiniciarSeguimiento() else leidoHasta = 0L
        if (primerCuadro) estado.update { it.copy(fase = Fase.ESCANEANDO, indicacion = Indicacion.APUNTA, cajaCodigo = null) }
    }

    /** Enfoca donde tocaron (región visible normalizada); sin cuadros todavía, al centro. */
    fun enfocarVisible(x: Float, y: Float) {
        val t = transformacion
        ultimoEnfoque = SystemClock.elapsedRealtimeNanos()
        if (t == null || cacheAncho == 0) {
            acciones.enfocar(0.5f, 0.5f)
            return
        }
        aBuffer(t, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), punto)
        acciones.enfocar(punto[0] / cacheAncho, punto[1] / cacheAlto)
    }

    /** La pantalla dejó de verse (CameraX suelta la cámara): al volver se espera de nuevo el primer cuadro. */
    fun alDetenerse() {
        linternaEncendida = false
        olvidarError()
        if (!pausado) primerCuadro = false
        reiniciarSeguimiento()
        if (zoomActual != zoomInicial) zoomActual = zoomInicial
        // Corre después del último cuadro en camino: lo que ese cuadro haya publicado se corrige aquí.
        estado.update {
            it.copy(fase = if (pausado) Fase.PAUSADO else Fase.ABRIENDO, indicacion = Indicacion.APUNTA, cajaCodigo = null, linterna = false)
        }
    }

    fun liberar() {
        cerrado = true
        motor?.close()
        motor = null
    }

    // ---- Auxiliares ----

    private fun crear(cfg: ConfigEscaner): MotorDeLectura {
        val nuevo = try {
            crearMotor(cfg.motor, cfg.simbologias)
        } catch (e: Throwable) {
            if (e is VirtualMachineError || cfg.motor == Motor.ML_KIT) throw e
            // Si zxing-cpp no carga (biblioteca nativa), se sigue con ML Kit.
            Log.e(ETIQUETA, "No se pudo crear ${cfg.motor.nombre}; se usa ML Kit", e)
            crearMotor(Motor.ML_KIT, cfg.simbologias)
        }
        motor = nuevo
        motorActual = nuevo.motor
        simbologiasActuales = cfg.simbologias
        return nuevo
    }

    private fun localizador(ancho: Int, alto: Int): Localizador {
        val lado = max(ancho, alto)
        val actual = localizador
        if (actual != null && lado <= ladoLocalizador) return actual
        ladoLocalizador = max(lado, 1920)
        return Localizador(ladoLocalizador, ladoLocalizador).also { localizador = it }
    }

    private fun geometria(imagen: ImageProxy, cfg: ConfigEscaner): Transformacion {
        val recorte = imagen.cropRect
        val rotacion = imagen.imageInfo.rotationDegrees
        val actual = transformacion
        if (actual != null && recorte.left == cacheIzquierda && recorte.top == cacheArriba && recorte.right == cacheDerecha &&
            recorte.bottom == cacheAbajo && rotacion == cacheRotacion && imagen.width == cacheAncho &&
            imagen.height == cacheAlto && cfg.altoFranja == cacheAltoFranja
        ) return actual

        cacheIzquierda = recorte.left; cacheArriba = recorte.top; cacheDerecha = recorte.right; cacheAbajo = recorte.bottom
        cacheRotacion = rotacion; cacheAncho = imagen.width; cacheAlto = imagen.height; cacheAltoFranja = cfg.altoFranja
        // Con el ViewPort de la sesión, cropRect es lo que se ve en pantalla; si viniera vacío, todo el buffer.
        regionVisible = if (recorte.width() > 0 && recorte.height() > 0) {
            Rectangulo(recorte.left, recorte.top, recorte.right, recorte.bottom)
        } else {
            Rectangulo(0, 0, imagen.width, imagen.height)
        }
        val t = Transformacion(regionVisible, rotacion)
        franja = franjaPara(cfg.altoFranja)
        regionFranja = t.aBuffer(franja)
        aBuffer(t, franja.centroX, franja.centroY, punto)
        preferidoX = punto[0]
        preferidoY = punto[1]
        toleranciaBordeX = BORDE_PX / t.anchoDerecho
        toleranciaBordeY = BORDE_PX / t.altoDerecho
        pasoLuz = max(2, sqrt(regionFranja.ancho.toFloat() * regionFranja.alto / MUESTRAS_LUZ).toInt())
        textoResolucion = "${imagen.width}x${imagen.height}"
        textoFranja = "${regionFranja.ancho}x${regionFranja.alto}"
        Log.i(
            ETIQUETA,
            "Geometría: buffer $textoResolucion, visible $regionVisible, rotación $rotacion°, franja $regionFranja",
        )
        transformacion = t
        forzarDiagnostico = true
        return t
    }

    /** Brillo medio y fracción saturada (reflejo) en la franja, con ~2000 muestras. */
    private fun medirLuz(y: ByteBuffer, pasoFila: Int) {
        val r = regionFranja
        var suma = 0L
        var n = 0
        var altos = 0
        var fila = r.arriba + pasoLuz / 2
        while (fila < r.abajo) {
            val base = fila * pasoFila
            var x = r.izquierda + pasoLuz / 2
            while (x < r.derecha) {
                val v = y.get(base + x).toInt() and 0xFF
                suma += v
                if (v >= SATURADO) altos++
                n++
                x += pasoLuz
            }
            fila += pasoLuz
        }
        if (n == 0) {
            brillo = -1f
            saturados = 0f
        } else {
            brillo = suma.toFloat() / n
            saturados = altos.toFloat() / n
        }
    }

    private fun masCercana(leidas: List<Pair<Lectura, Caja?>>): Caja? {
        var mejor: Caja? = null
        var distancia = Float.MAX_VALUE
        for (i in leidas.indices) {
            val c = leidas[i].second ?: continue
            val d = distanciaAlCentro(c)
            if (d < distancia) {
                distancia = d
                mejor = c
            }
        }
        return mejor
    }

    private fun masCercanaCaja(cajas: List<Caja>): Caja? {
        var mejor: Caja? = null
        var distancia = Float.MAX_VALUE
        for (i in cajas.indices) {
            val d = distanciaAlCentro(cajas[i])
            if (d < distancia) {
                distancia = d
                mejor = cajas[i]
            }
        }
        return mejor
    }

    private fun distanciaAlCentro(c: Caja): Float {
        val dx = c.centroX - franja.centroX
        val dy = c.centroY - franja.centroY
        return dx * dx + dy * dy
    }

    private fun cajaLocalizador(loc: Localizacion, t: Transformacion): Caja {
        val e = esquinasLocalizador
        e[0] = loc.izquierda.toFloat(); e[1] = loc.arriba.toFloat()
        e[2] = loc.derecha.toFloat(); e[3] = loc.arriba.toFloat()
        e[4] = loc.derecha.toFloat(); e[5] = loc.abajo.toFloat()
        e[6] = loc.izquierda.toFloat(); e[7] = loc.abajo.toFloat()
        return cajaVisible(t, e, punto)
    }

    /** El centro del localizador cae en la caja (con holgura de un cuarto de su tamaño). */
    private fun cercaDe(caja: Caja, loc: Localizacion, t: Transformacion): Boolean {
        aVisible(t, loc.centroX, loc.centroY, punto)
        val hx = caja.ancho * 0.25f
        val hy = caja.alto * 0.25f
        return punto[0] >= caja.izquierda - hx && punto[0] <= caja.derecha + hx &&
            punto[1] >= caja.arriba - hy && punto[1] <= caja.abajo + hy
    }

    /**
     * El localizador ve un código aunque no lo pueda verificar: con desenfoque o reflejo se pierden transiciones y la
     * verificación completa falla, pero las barras siguen siendo coherentes, continuas de una línea a otra y con la
     * proporción de oscuro y claro de un código (o lavadas por un reflejo). Las hojas rayadas (proporción ~0.17) y el
     * texto (continuidad < 0.3) no lo cumplen (docs/escaner.md, «Localizador»).
     */
    private fun pareceCodigo(loc: Localizacion) = loc.encontrado && loc.tipo == 1 && (
        loc.verificado || (
            loc.coherencia >= COHERENCIA_INDICIO && loc.continuidad >= CONTINUIDAD_INDICIO &&
                (loc.oscuroClaro in OSCURO_CLARO_MIN..OSCURO_CLARO_MAX || loc.saturados >= SATURADOS_INDICIO)
            )
        )

    private fun esPista(i: Indicacion) =
        i != Indicacion.APUNTA && i != Indicacion.LEYENDO && i != Indicacion.LEIDO && i != Indicacion.YA_LEIDO

    /** Tras volver a abrir o reanudar, un error se vuelve a avisar. */
    private fun olvidarError() {
        enError = false
        errores = 0
    }

    private fun fallo(e: Throwable) {
        errores++
        if (errores == 1 || errores % 100 == 0) Log.e(ETIQUETA, "Falló el análisis de un cuadro ($errores seguidos)", e)
        if (!enError) {
            enError = true
            estado.update { it.copy(fase = Fase.ERROR, mensajeError = "No se pudo analizar la imagen de la cámara.") }
        }
    }

    private companion object {
        const val ETIQUETA = "Escaner"
        const val NANOS_POR_MS = 1_000_000L
        const val NANOS_POR_S = 1_000_000_000L
        /** La retícula verde tras leer. */
        const val NANOS_LEIDO = 500 * NANOS_POR_MS
        /** Un hueco corto (un cuadro sin ver el código) no cuenta como candidato nuevo. */
        const val NANOS_GRACIA_CANDIDATO = 300 * NANOS_POR_MS
        const val NANOS_INDICIO = 1_000 * NANOS_POR_MS
        const val NANOS_ENFOQUE_CANDIDATO = 700 * NANOS_POR_MS
        const val NANOS_ENTRE_ENFOQUES = 2_000 * NANOS_POR_MS
        const val NANOS_ZOOM_REGRESA = 2_000 * NANOS_POR_MS
        /** Deja que el nuevo zoom llegue a los cuadros antes de volver a medir el módulo. */
        const val NANOS_ENTRE_ZOOMS = 500 * NANOS_POR_MS
        const val NANOS_OSCURO = 1_000 * NANOS_POR_MS
        const val NANOS_DIAGNOSTICO = 500 * NANOS_POR_MS
        const val MODULO_MINIMO_PX = 2f
        const val FACTOR_ZOOM = 1.4f
        const val BRILLO_OSCURO = 45f
        const val BRILLO_CLARO = 60f
        const val SATURADO = 250
        const val MUESTRAS_LUZ = 2000f
        const val BORDE_PX = 4f
        const val CUADROS_LOCALIZADOR = 2
        /** Un indicio sin verificar tiene que sostenerse más cuadros que uno verificado. */
        const val CUADROS_INDICIO = 3
        const val COHERENCIA_INDICIO = 0.95f
        const val CONTINUIDAD_INDICIO = 0.9f
        const val SATURADOS_INDICIO = 0.06f
        const val OSCURO_CLARO_MIN = 0.35f
        const val OSCURO_CLARO_MAX = 2.8f
        const val MAX_INDICACIONES = 32
    }
}
