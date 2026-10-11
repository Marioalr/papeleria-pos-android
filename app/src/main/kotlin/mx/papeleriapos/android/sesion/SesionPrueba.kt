package mx.papeleriapos.android.sesion

import mx.papeleriapos.android.csv.Csv
import mx.papeleriapos.escaner.Apertura
import mx.papeleriapos.escaner.ComparacionMotores
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.EventoLectura
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.nucleo.Catalogo
import mx.papeleriapos.escaner.nucleo.CodigoLeido
import mx.papeleriapos.escaner.nucleo.Gtin
import mx.papeleriapos.escaner.nucleo.Modo
import mx.papeleriapos.escaner.nucleo.Simbologia
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Cómo terminó un intento, según el operador (protocolo de la prueba en tienda). */
enum class ResultadoIntento(val etiqueta: String, val deLectura: Boolean) {
    OK("Bien", true),
    LECTURA_INCORRECTA("Lectura incorrecta", true),
    FALSO_POSITIVO("Falso positivo", true),
    DUPLICADO("Duplicado", true),
    /** No leyó a tiempo. */
    TIMEOUT("No leyó", false),
    CANCELADO("Cancelado", false),
}

/** En qué condición se escanea un bloque de intentos; el operador la elige antes de empezar el bloque. */
enum class Condicion(val etiqueta: String) {
    NORMAL("Normal"),
    POCA_LUZ("Poca luz"),
    REFLEJO("Reflejo"),
    CURVO("Curvo"),
    CHICO_LEJOS("Chico o lejos"),
    VARIOS_CODIGOS("Varios códigos"),
    MOVIDO("Movido"),
    OTRA("Otra"),
}

/**
 * La apertura del escáner con la que se hizo el intento (`EstadoEscaner.apertura`): cómo arrancó y los ms desde el
 * inicio de la apertura hasta cada paso; −1 = no ocurrió o no se marcó.
 */
data class MedidasApertura(
    val tipo: String = "",
    val msActividad: Long = -1,
    /** Hasta que terminó de enlazar la cámara (`ENLACE_FIN`). */
    val msEnlace: Long = -1,
    val msCamara: Long = -1,
    val msPrimerCuadro: Long = -1,
    val msPrimerAnalisis: Long = -1,
    val msPrimeraLectura: Long = -1,
) {
    companion object {
        fun de(a: Apertura): MedidasApertura = if (a.marcas.isEmpty()) MedidasApertura() else MedidasApertura(
            tipo = a.tipo.name,
            msActividad = a.ms(PasoApertura.ACTIVIDAD),
            msEnlace = a.ms(PasoApertura.ENLACE_FIN),
            msCamara = a.ms(PasoApertura.CAMARA_ABIERTA),
            msPrimerCuadro = a.ms(PasoApertura.PRIMER_CUADRO_SENSOR),
            msPrimerAnalisis = a.ms(PasoApertura.PRIMER_ANALISIS),
            msPrimeraLectura = a.ms(PasoApertura.PRIMERA_LECTURA),
        )
    }
}

/**
 * Lo que se mide del celular en cada intento; −1 = no se pudo medir. La CPU va en delta desde el intento anterior (no el
 * acumulado del proceso), con su ventana de reloj para sacar el % de un núcleo.
 */
data class Medicion(
    val bateriaPct: Int = -1,
    /** ms de CPU del proceso (todos sus hilos) desde la medición anterior. */
    val cpuMs: Long = -1,
    val estadoTermico: Int = -1,
    /** ms de reloj desde la medición anterior. */
    val paredMs: Long = -1,
    val apertura: MedidasApertura = MedidasApertura(),
) {
    /** % de un núcleo en la ventana; NaN si no se sabe. No incluye cameraserver ni el HAL (otros procesos). */
    val cpuPctNucleo: Double get() = if (cpuMs < 0 || paredMs <= 0) Double.NaN else 100.0 * cpuMs / paredMs
}

/** Lo que es igual en todos los renglones de un equipo. */
data class DatosEquipo(val dispositivo: String, val sdk: Int, val versionApp: String, val commit: String)

/** Lo que el evento no trae y se toma de la configuración y del celular al registrar. */
data class Contexto(
    val hora: Long,
    val modo: Modo,
    val motor: Motor,
    val rangoFps: String,
    val medicion: Medicion = Medicion(),
    /** Solo para los intentos fallidos (los leídos traen su propio zoom y linterna). */
    val zoom: Float = 0f,
    val linterna: Boolean = false,
    /** El lente elegido en Ajustes ([mx.papeleriapos.escaner.Lente]), la cámara física en uso y la resolución de análisis. */
    val lente: String = "",
    val camaraFisica: String = "",
    val resolucion: String = "",
    /** Los ms por cuadro del diagnóstico, solo para los fallidos (los leídos traen el suyo); 0 = no se sabe. */
    val msCuadro: Float = 0f,
    /** El catálogo de prueba en que se busca cada lectura; null = no se buscó. */
    val catalogo: Catalogo? = null,
)

/**
 * Un intento de la prueba, ya como va al CSV: en los códigos 2D (el QR del ticket) [valor], [normalizado], [esperado] y
 * [claveCatalogo] quedan ocultos desde aquí, para que no salgan ni en el archivo ni en la copia que se guarda.
 */
data class Intento(
    val n: Int,
    val hora: Long,
    val motor: String,
    /** Por dónde leyó: DIRECTA (zxing-cpp), UMBRAL (con el umbral del localizador) o RESPALDO (ML Kit); vacío si no leyó. */
    val via: String = "",
    val modo: String,
    val rangoFps: String,
    /** −1 = no se sabe. */
    val msDesdeCandidato: Long,
    val indicaciones: List<String>,
    /** Vacío en los intentos fallidos. */
    val simbologia: String,
    val valor: String,
    val normalizado: String,
    val esperado: String,
    /** null = no había código esperado o no se leyó nada. */
    val coincide: Boolean?,
    /** Si lo leído está en el catálogo de prueba (docs/escaner.md, «Búsqueda en el catálogo»); null = no se buscó. */
    val enCatalogo: Boolean? = null,
    /** Por qué variante coincidió ([mx.papeleriapos.escaner.nucleo.Variante]); vacío si no coincidió. */
    val variante: String = "",
    /** La clave del catálogo que coincidió; vacío si no coincidió. */
    val claveCatalogo: String = "",
    /** 0 = desconocido. */
    val moduloPx: Float,
    /** 0 = desconocido. */
    val zoom: Float,
    val linterna: Boolean,
    /** El lente de Ajustes (SISTEMA, PRINCIPAL o GRAN_ANGULAR); vacío en un CSV de antes. */
    val lente: String = "",
    /** La cámara física en uso (id de Camera2, API 29+); vacío si no se sabe. */
    val camaraFisica: String = "",
    /** La resolución del análisis («1280x720»); vacío si no se sabe. */
    val resolucion: String = "",
    /** ms de análisis por cuadro al momento del intento; 0 = desconocido. */
    val msCuadro: Float = 0f,
    /** A cuántos cm enfocaba al leer, si la cámara da el enfoque calibrado; −1 = desconocido (los fallidos no lo traen). */
    val distanciaCm: Float = -1f,
    /** Con el motor «Lado a lado»: qué hizo cada motor con los cuadros de esta lectura; null con los demás y en los fallidos. */
    val comparacion: ComparacionMotores? = null,
    val resultado: ResultadoIntento,
    val medicion: Medicion,
)

/**
 * Una sesión de la prueba en tienda (docs/pruebas-en-tienda.md): cada lectura aceptada es un intento; el operador
 * corrige el resultado del último y registra a mano los que no leyeron. Inmutable: cada cambio devuelve otra.
 */
data class SesionPrueba(
    /** El equipo y el operador, como lo escribió quien inicia (p. ej. «Moto G24 Lupita»). */
    val nombre: String,
    val inicio: Long,
    val intentos: List<Intento> = emptyList(),
    /** El código que se va a escanear, tecleado por el operador; vacío = no se compara. */
    val esperado: String = "",
) {
    val ultimo: Intento? get() = intentos.lastOrNull()

    /** Identifica la sesión en el CSV: nombre y hora de inicio. */
    fun clave(zona: TimeZone = TimeZone.getDefault()): String {
        val f = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).apply { timeZone = zona }
        return "${nombre.trim()}@${f.format(Date(inicio))}"
    }

    /** El esperado se guarda sin ningún espacio: tecleado con espacios en medio («7509 9934 56786») no coincidiría nunca. */
    fun conEsperado(codigo: String): SesionPrueba = copy(esperado = sinEspacios(codigo))

    fun registrarLectura(evento: EventoLectura, c: Contexto): SesionPrueba {
        val codigo = evento.aceptada.codigo
        val coincide = if (esperado.isEmpty()) null else coincide(esperado, codigo)
        val oculto = !codigo.simbologia.unaDimension
        // Un HashSet: microsegundos.
        val enElCatalogo = c.catalogo?.buscar(codigo)
        val intento = Intento(
            n = intentos.size + 1,
            hora = c.hora,
            motor = evento.motor.name,
            via = evento.via.name,
            modo = c.modo.name,
            rangoFps = c.rangoFps,
            msDesdeCandidato = evento.msDesdeCandidato,
            indicaciones = evento.indicaciones.map { it.name },
            simbologia = codigo.simbologia.name,
            valor = if (oculto) Csv.ocultar(codigo.original) else codigo.original,
            normalizado = if (oculto) Csv.ocultar(codigo.normalizado) else codigo.normalizado,
            esperado = if (oculto) Csv.ocultar(esperado) else esperado,
            coincide = coincide,
            enCatalogo = if (c.catalogo == null) null else enElCatalogo != null,
            variante = enElCatalogo?.variante?.name.orEmpty(),
            claveCatalogo = enElCatalogo?.clave?.let { if (oculto) Csv.ocultar(it) else it }.orEmpty(),
            moduloPx = evento.moduloPx,
            zoom = evento.zoom,
            linterna = evento.linterna,
            lente = c.lente,
            camaraFisica = c.camaraFisica,
            resolucion = c.resolucion,
            msCuadro = evento.msCuadro,
            distanciaCm = evento.distanciaCm,
            comparacion = evento.comparacion,
            // Si se tecleó el esperado y no coincide, de entrada es lectura incorrecta; el operador lo puede cambiar.
            resultado = if (coincide == false) ResultadoIntento.LECTURA_INCORRECTA else ResultadoIntento.OK,
            medicion = c.medicion,
        )
        return copy(intentos = intentos + intento)
    }

    /** Un intento que no leyó ([ResultadoIntento.TIMEOUT]) o que se abandonó ([ResultadoIntento.CANCELADO]). */
    fun registrarFallo(resultado: ResultadoIntento, c: Contexto): SesionPrueba {
        require(!resultado.deLectura) { "Un intento fallido es TIMEOUT o CANCELADO." }
        val intento = Intento(
            n = intentos.size + 1,
            hora = c.hora,
            motor = c.motor.name,
            modo = c.modo.name,
            rangoFps = c.rangoFps,
            msDesdeCandidato = -1,
            indicaciones = emptyList(),
            simbologia = "",
            valor = "",
            normalizado = "",
            esperado = esperado,
            coincide = null,
            moduloPx = 0f,
            zoom = c.zoom,
            linterna = c.linterna,
            lente = c.lente,
            camaraFisica = c.camaraFisica,
            resolucion = c.resolucion,
            msCuadro = c.msCuadro,
            resultado = resultado,
            medicion = c.medicion,
        )
        return copy(intentos = intentos + intento)
    }

    /**
     * Corrige el resultado del último intento. Un intento leído solo puede ser OK, lectura incorrecta, falso positivo o
     * duplicado, y uno fallido solo TIMEOUT o CANCELADO; lo demás se ignora.
     */
    fun marcarUltimo(resultado: ResultadoIntento): SesionPrueba {
        val u = ultimo ?: return this
        val fueLectura = u.simbologia.isNotEmpty()
        if (resultado.deLectura != fueLectura || u.resultado == resultado) return this
        return copy(intentos = intentos.dropLast(1) + u.copy(resultado = resultado))
    }

    /** El último intento se registró a mano (no leyó o se canceló): se puede deshacer. */
    val puedeDeshacerFallo: Boolean get() = ultimo?.let { !it.resultado.deLectura && it.simbologia.isEmpty() } == true

    /**
     * Quita el último intento si fue un fallo registrado a mano (un doble toque en «No leyó»). Las lecturas no se quitan:
     * quedaron registradas por el escáner y se corrigen con su resultado (falso positivo, duplicado…).
     */
    fun quitarUltimoFallo(): SesionPrueba = if (puedeDeshacerFallo) copy(intentos = intentos.dropLast(1)) else this

    fun contadores(): Map<ResultadoIntento, Int> {
        val cuenta = ResultadoIntento.entries.associateWithTo(LinkedHashMap()) { 0 }
        for (i in intentos) cuenta[i.resultado] = cuenta.getValue(i.resultado) + 1
        return cuenta
    }

    companion object {
        /** Lo tecleado sin espacios ni tabuladores (en medio también): un código no los lleva. */
        fun sinEspacios(texto: String): String = texto.filterNot { it.isWhitespace() }

        /**
         * Si lo leído es el código esperado. Los GTIN se comparan con ceros a la izquierda hasta 14 dígitos, así un UPC-A
         * de 12 dígitos coincide con su EAN-13 y un UPC-E con su expansión. Los espacios del esperado no cuentan (una
         * sesión recuperada de un CSV viejo puede traerlos).
         */
        fun coincide(esperado: String, codigo: CodigoLeido): Boolean {
            val e = sinEspacios(esperado)
            if (e.isEmpty()) return false
            val leidos = buildSet {
                add(codigo.original); add(codigo.normalizado); addAll(codigo.clavesDeBusqueda)
            }
            if (e in leidos) return true
            val gtinEsperado = comoGtin14(e, codigo.simbologia) ?: return false
            return leidos.any { comoGtin14(it, codigo.simbologia) == gtinEsperado }
        }

        private fun comoGtin14(texto: String, simbologia: Simbologia): String? {
            if (texto.isEmpty() || !texto.all { it in '0'..'9' }) return null
            val gtin = if (texto.length == 8 && simbologia == Simbologia.UPC_E) Gtin.upcEaUpcA(texto) ?: texto else texto
            return if (gtin.length in 8..14) gtin.padStart(14, '0') else null
        }
    }
}
