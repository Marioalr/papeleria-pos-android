package mx.papeleriapos.escaner.nucleo

/** La indicación que ve el empleado: una sola a la vez (docs/escaner.md, «Guía»). */
enum class Indicacion {
    /** No hay ningún código a la vista. */
    APUNTA,
    /** Hay un código, bien colocado: se está leyendo. */
    LEYENDO,
    /** El código a la vista ya se leyó: para que cuente otra vez hay que retirarlo (docs/escaner.md, «Repetidos»). */
    YA_LEIDO,
    ACERCATE,
    ALEJATE,
    /** Mover el celular hacia donde está el código (si el código se ve a la izquierda, mover a la izquierda). */
    IZQUIERDA,
    DERECHA,
    ARRIBA,
    ABAJO,
    QUIETO,
    ENFOCANDO,
    MAS_LUZ,
    REFLEJO,
    LEIDO,
}

/**
 * Lo observado en un cuadro, ya en la región visible normalizada (como se ve en pantalla) y respecto a la franja de
 * lectura. Objeto reutilizable: el analizador lo llena en cada cuadro.
 */
class Observacion {
    var nanos = 0L
    /** Hubo una lectura aceptada en este cuadro. */
    var leido = false
    /** El código a la vista es uno ya aceptado que no se ha retirado (no vuelve a contar). */
    var yaLeido = false
    /** Hay un código a la vista: potencial del motor o localizador verificado en varios cuadros. */
    var candidato = false
    /** (centro del código − centro de la franja) / ancho de la franja; positivo = el código está a la derecha. */
    var dx = 0f
    /** (centro del código − centro de la franja) / alto de la franja; positivo = el código está abajo. */
    var dy = 0f
    /** Largo del código / ancho de la franja. */
    var largoRelativo = 0f
    /**
     * Las barras llegan al borde de la región visible. Si toca un solo lado ([bordeX]/[bordeY]) se pide moverse hacia
     * ese lado; si no (toca ambos), alejarse.
     */
    var cortado = false
    /** Toca el borde de la región visible por un solo lado (−1 izquierda/arriba, +1 derecha/abajo, 0 ninguno). */
    var bordeX = 0
    var bordeY = 0
    /** Píxeles del buffer por módulo; 0 = desconocido. */
    var moduloPx = 0f
    /** 0 a 1; −1 = desconocido. */
    var nitidez = -1f
    /** Luminancia media del centro de la imagen (0 a 255); −1 = desconocida. Cuenta aunque no haya candidato. */
    var brillo = -1f
    var saturados = 0f
    /** `LENS_FOCUS_DISTANCE / LENS_INFO_MINIMUM_FOCUS_DISTANCE`; −1 = desconocido. */
    var proporcionEnfoque = -1f
    var enfocando = false
    var desenfocado = false
    /** Cuánto se movió el código entre cuadros, en fracciones de la franja. */
    var movimiento = 0f
    /** El zoom propio ya llegó a su tope (solo entonces se pide acercarse). */
    var zoomAlTope = true

    fun reiniciar() {
        nanos = 0L; leido = false; yaLeido = false; candidato = false; dx = 0f; dy = 0f; largoRelativo = 0f; cortado = false
        bordeX = 0; bordeY = 0; moduloPx = 0f; nitidez = -1f; brillo = -1f; saturados = 0f; proporcionEnfoque = -1f
        enfocando = false; desenfocado = false; movimiento = 0f; zoomAlTope = true
    }
}

/**
 * Decide la indicación con histéresis, suavizado y tiempo mínimo en pantalla para que no parpadee
 * (docs/escaner.md, «Umbrales»).
 *
 * Prioridad (gana la primera que aplica): YA_LEIDO, REFLEJO, MAS_LUZ, ALEJATE, dirección (IZQUIERDA/DERECHA/ARRIBA/ABAJO),
 * ACERCATE, ENFOCANDO/QUIETO, LEYENDO; sin candidato, APUNTA. LEIDO entra de inmediato.
 *
 * - Una condición nueva se muestra cuando dura [UmbralesGuia.cuadrosParaMostrar] cuadros seguidos Y
 *   [UmbralesGuia.msParaMostrar]: lo primero filtra el ruido a pocos cuadros por segundo y lo segundo a muchos.
 * - Lo que se muestra se queda [UmbralesGuia.msEnPantalla], salvo que entre una de mayor prioridad.
 * - Sin pistas de dirección en los primeros [UmbralesGuia.msGraciaCandidatoNuevo] de un candidato: el enfoque y el
 *   zoom todavía se acomodan. La condición sí se va confirmando, así aparece justo al terminar la gracia.
 * - MAS_LUZ no depende del candidato (a oscuras el localizador no ve nada) y su confirmación es la propia espera de
 *   [UmbralesGuia.msOscuroParaPedirLuz].
 * - Las condiciones que salen de un solo cuadro sin suavizar (reflejo, movimiento, tocar un borde, cortado) cuentan
 *   solo si se sostienen [UmbralesGuia.cuadrosParaMostrar] cuadros seguidos: si una va y viene, no debe impedir que se
 *   confirme lo demás.
 *
 * Sin asignaciones por cuadro.
 */
class Guia(val umbrales: UmbralesGuia = UmbralesGuia()) {
    /** La indicación que se muestra ahora. */
    var actual: Indicacion = Indicacion.APUNTA
        private set

    // Suavizado exponencial.
    private var iniciado = false
    private var eLargo = 0f
    private var eDx = 0f
    private var eDy = 0f
    private var eNitidez = -1f
    private var eModulo = 0f

    // Banderas con histéresis.
    private var chico = false
    private var grande = false
    private var fueraX = 0
    private var fueraY = 0
    private var borroso = false
    private var reflejo = false
    private var oscuro = false
    private var moviendo = false
    private var oscuroDesde = 0L
    // Cuadros seguidos de cada condición cruda.
    private var cuadrosReflejo = 0
    private var cuadrosMoviendo = 0
    private var cuadrosCortado = 0
    private var cuadrosBordeX = 0
    private var cuadrosBordeY = 0
    private var bordeXPrevio = 0
    private var bordeYPrevio = 0

    private var hayCandidato = false
    private var candidatoDesde = 0L
    private var perdidos = 0

    private var mostradaDesde = 0L
    private var pendiente = Indicacion.APUNTA
    private var cuadrosPendiente = 0
    private var pendienteDesde = 0L

    fun actualizar(o: Observacion): Indicacion {
        val u = umbrales
        val t = o.nanos
        if (o.leido) {
            perdidos = 0
            return mostrar(Indicacion.LEIDO, t)
        }

        val estabaOscuro = oscuro
        oscuro = o.brillo >= 0f && bajo(oscuro, o.brillo, u.oscuroEntra, u.oscuroSale)
        if (oscuro && !estabaOscuro) oscuroDesde = t
        val pideLuz = oscuro && t - oscuroDesde >= u.msOscuroParaPedirLuz * MS

        if (!o.candidato) {
            perdidos++
            // Tolera pérdidas breves: el motor o el localizador fallan un cuadro aunque el código siga ahí.
            if (hayCandidato && perdidos < u.cuadrosPerdidosParaReiniciar) return actual
            olvidarCandidato()
            return if (pideLuz) mostrarSiPuede(Indicacion.MAS_LUZ, t) else proponer(Indicacion.APUNTA, t, false)
        }
        perdidos = 0
        if (!hayCandidato) {
            hayCandidato = true; candidatoDesde = t
        }
        suavizar(o)

        val n = u.cuadrosParaMostrar
        reflejo = alto(reflejo, o.saturados, u.reflejoEntra, u.reflejoSale)
        cuadrosReflejo = if (reflejo) cuadrosReflejo + 1 else 0
        grande = alto(grande, eLargo, u.grandeEntra, u.grandeSale)
        cuadrosCortado = if (o.cortado && o.bordeX == 0 && o.bordeY == 0) cuadrosCortado + 1 else 0
        val cortadoAmbos = cuadrosCortado >= n
        chico = when {
            // Con buena nitidez el módulo manda; borroso, las corridas no son confiables y se usa el largo.
            eNitidez >= u.nitidezConfiable && eModulo > 0f -> bajo(chico, eModulo, u.moduloEntra, u.moduloSale)
            eLargo > 0f -> bajo(chico, eLargo, u.chicoEntra, u.chicoSale)
            else -> false
        }
        fueraX = tres(fueraX, eDx, u.descentradoEntra, u.descentradoSale)
        fueraY = tres(fueraY, eDy, u.descentradoEntra, u.descentradoSale)
        // Tocar un solo borde ya dice hacia dónde: el código sigue del otro lado.
        cuadrosBordeX = if (o.bordeX != 0 && o.bordeX == bordeXPrevio) cuadrosBordeX + 1 else if (o.bordeX != 0) 1 else 0
        cuadrosBordeY = if (o.bordeY != 0 && o.bordeY == bordeYPrevio) cuadrosBordeY + 1 else if (o.bordeY != 0) 1 else 0
        bordeXPrevio = o.bordeX
        bordeYPrevio = o.bordeY
        if (cuadrosBordeX >= n) fueraX = o.bordeX
        if (cuadrosBordeY >= n) fueraY = o.bordeY
        borroso = eNitidez >= 0f && bajo(borroso, eNitidez, u.borrosoEntra, u.borrosoSale)
        moviendo = alto(moviendo, o.movimiento, u.movimientoEntra, u.movimientoSale)
        cuadrosMoviendo = if (moviendo) cuadrosMoviendo + 1 else 0
        // Borroso y demasiado cerca: la lente ya no alcanza a enfocar; acercarse más no sirve.
        val muyCerca = borroso && (
            (o.proporcionEnfoque >= 0f && o.proporcionEnfoque >= u.enfoqueCercano) ||
                (o.desenfocado && eLargo > LARGO_DESENFOCADO_CERCA) || eLargo > LARGO_BORROSO_CERCA
            )
        // El ancho del módulo no entra aquí: borroso se infla (mide 9 px donde hay 3.5) y pediría alejarse de más.

        val h = when {
            // Ya contado: moverse, acercarse o la luz ya no importan; lo único que sirve es retirarlo.
            o.yaLeido -> Indicacion.YA_LEIDO
            cuadrosReflejo >= n -> Indicacion.REFLEJO
            pideLuz -> Indicacion.MAS_LUZ
            grande || cortadoAmbos || muyCerca -> Indicacion.ALEJATE
            fueraX != 0 || fueraY != 0 ->
                if (fueraX != 0 && (fueraY == 0 || abs(eDx) >= abs(eDy))) {
                    if (fueraX > 0) Indicacion.DERECHA else Indicacion.IZQUIERDA
                } else {
                    if (fueraY > 0) Indicacion.ABAJO else Indicacion.ARRIBA
                }
            chico && o.zoomAlTope -> Indicacion.ACERCATE
            borroso && o.enfocando -> Indicacion.ENFOCANDO
            borroso || cuadrosMoviendo >= n -> Indicacion.QUIETO
            else -> Indicacion.LEYENDO
        }
        if (h == Indicacion.MAS_LUZ) return mostrarSiPuede(h, t)
        val enGracia = t - candidatoDesde < u.msGraciaCandidatoNuevo * MS
        return proponer(h, t, enGracia && esDireccion(h))
    }

    fun reiniciar() {
        olvidarCandidato()
        oscuro = false; oscuroDesde = 0L; perdidos = 0
        actual = Indicacion.APUNTA; mostradaDesde = 0L
        pendiente = Indicacion.APUNTA; cuadrosPendiente = 0; pendienteDesde = 0L
    }

    /** Lo que depende del código a la vista; la luz y lo que se muestra se conservan. */
    private fun olvidarCandidato() {
        hayCandidato = false; iniciado = false
        eLargo = 0f; eDx = 0f; eDy = 0f; eNitidez = -1f; eModulo = 0f
        chico = false; grande = false; fueraX = 0; fueraY = 0; borroso = false; reflejo = false; moviendo = false
        cuadrosReflejo = 0; cuadrosMoviendo = 0; cuadrosCortado = 0; cuadrosBordeX = 0; cuadrosBordeY = 0
        bordeXPrevio = 0; bordeYPrevio = 0
    }

    private fun suavizar(o: Observacion) {
        if (!iniciado) {
            eLargo = o.largoRelativo; eDx = o.dx; eDy = o.dy; eNitidez = o.nitidez; eModulo = o.moduloPx
            iniciado = true
            return
        }
        val a = umbrales.alfa
        eLargo += a * (o.largoRelativo - eLargo)
        eDx += a * (o.dx - eDx)
        eDy += a * (o.dy - eDy)
        // −1 y 0 son «desconocido»: no deben arrastrar el promedio.
        if (o.nitidez >= 0f) eNitidez = if (eNitidez < 0f) o.nitidez else eNitidez + a * (o.nitidez - eNitidez)
        if (o.moduloPx > 0f) eModulo = if (eModulo <= 0f) o.moduloPx else eModulo + a * (o.moduloPx - eModulo)
    }

    /** Confirma [h] por cuadros y por tiempo; [bloqueada] la sigue confirmando pero no la deja salir todavía. */
    private fun proponer(h: Indicacion, t: Long, bloqueada: Boolean): Indicacion {
        if (h == actual) {
            cuadrosPendiente = 0
            return actual
        }
        if (h == pendiente && cuadrosPendiente > 0) {
            cuadrosPendiente++
        } else {
            pendiente = h; cuadrosPendiente = 1; pendienteDesde = t
        }
        val u = umbrales
        val confirmada = cuadrosPendiente >= u.cuadrosParaMostrar && t - pendienteDesde >= u.msParaMostrar * MS
        if (!confirmada || bloqueada) return actual
        return mostrarSiPuede(h, t)
    }

    private fun mostrarSiPuede(h: Indicacion, t: Long): Indicacion {
        if (h == actual) return actual
        if (prioridadDe(h) > prioridadDe(actual) || t - mostradaDesde >= umbrales.msEnPantalla * MS) return mostrar(h, t)
        return actual
    }

    private fun mostrar(h: Indicacion, t: Long): Indicacion {
        // LEIDO reinicia su tiempo en pantalla con cada lectura.
        if (h != actual || h == Indicacion.LEIDO) {
            actual = h; mostradaDesde = t
        }
        cuadrosPendiente = 0
        return actual
    }

    private companion object {
        const val MS = 1_000_000L
        // Señales de «demasiado cerca para enfocar» cuando no hay distancia de enfoque (equipos LIMITED/LEGACY).
        const val LARGO_DESENFOCADO_CERCA = 0.6f
        const val LARGO_BORROSO_CERCA = 0.75f

        fun esDireccion(i: Indicacion) = i == Indicacion.IZQUIERDA || i == Indicacion.DERECHA ||
            i == Indicacion.ARRIBA || i == Indicacion.ABAJO

        fun abs(v: Float) = if (v < 0f) -v else v

        /** Se enciende cuando [v] pasa de [entra]; se apaga solo cuando baja de [sale] (entra > sale). */
        fun alto(activo: Boolean, v: Float, entra: Float, sale: Float) = if (activo) v > sale else v > entra

        /** Se enciende cuando [v] baja de [entra]; se apaga solo cuando sube de [sale] (entra < sale). */
        fun bajo(activo: Boolean, v: Float, entra: Float, sale: Float) = if (activo) v < sale else v < entra

        /** −1/0/+1 con banda muerta: entra con |v| > [entra], sale con |v| < [sale]. */
        fun tres(estado: Int, v: Float, entra: Float, sale: Float): Int = when {
            estado > 0 -> if (v < sale) (if (v < -entra) -1 else 0) else 1
            estado < 0 -> if (v > -sale) (if (v > entra) 1 else 0) else -1
            else -> if (v > entra) 1 else if (v < -entra) -1 else 0
        }
    }
}

/** Umbrales de la guía: puntos de partida que se calibran con los celulares de la tienda. */
data class UmbralesGuia(
    val chicoEntra: Float = 0.30f,
    val chicoSale: Float = 0.36f,
    val grandeEntra: Float = 0.90f,
    val grandeSale: Float = 0.80f,
    val moduloEntra: Float = 2.0f,
    val moduloSale: Float = 2.5f,
    val descentradoEntra: Float = 0.20f,
    val descentradoSale: Float = 0.12f,
    val borrosoEntra: Float = 0.15f,
    val borrosoSale: Float = 0.22f,
    val nitidezConfiable: Float = 0.25f,
    val reflejoEntra: Float = 0.06f,
    val reflejoSale: Float = 0.03f,
    val oscuroEntra: Float = 45f,
    val oscuroSale: Float = 60f,
    val movimientoEntra: Float = 0.05f,
    val movimientoSale: Float = 0.025f,
    val enfoqueCercano: Float = 0.95f,
    /** Cuadros seguidos que debe durar una condición antes de mostrarse. */
    val cuadrosParaMostrar: Int = 3,
    /** Tiempo mínimo que debe durar una condición antes de mostrarse. */
    val msParaMostrar: Long = 300,
    /** Tiempo mínimo en pantalla de una indicación (salvo que la reemplace otra de mayor prioridad). */
    val msEnPantalla: Long = 700,
    /** Sin pistas de dirección en los primeros ms de un candidato nuevo (deja trabajar al enfoque). */
    val msGraciaCandidatoNuevo: Long = 500,
    /** La oscuridad debe durar esto antes de pedir luz. */
    val msOscuroParaPedirLuz: Long = 500,
    val cuadrosPerdidosParaReiniciar: Int = 6,
    val alfa: Float = 0.4f,
)

/** Qué indicación gana cuando hay varias (docs/escaner.md, «Guía»): una de mayor prioridad reemplaza a la que está. */
internal fun prioridadDe(i: Indicacion): Int = when (i) {
    Indicacion.LEIDO -> 10
    Indicacion.YA_LEIDO -> 9
    Indicacion.REFLEJO -> 8
    Indicacion.MAS_LUZ -> 7
    Indicacion.ALEJATE -> 6
    Indicacion.IZQUIERDA, Indicacion.DERECHA, Indicacion.ARRIBA, Indicacion.ABAJO -> 5
    Indicacion.ACERCATE -> 4
    Indicacion.ENFOCANDO, Indicacion.QUIETO -> 3
    Indicacion.LEYENDO -> 2
    Indicacion.APUNTA -> 1
}
