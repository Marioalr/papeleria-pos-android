package mx.papeleriapos.android.caja

import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.papeleriapos.android.copia.AlmacenCopia
import mx.papeleriapos.android.copia.Copia
import java.io.File
import java.io.IOException

/** Cómo está la red con la caja principal, por lo que tarda el latido: como la franja de la caja de Windows. */
enum class Red { SIN_PROBAR, BUENA, LENTA, CAIDA }

/** Lo que Ajustes › La conexión dice de la copia. */
data class ResumenCopia(val variantes: Int, val codigos: Int, val guardadaEn: Long, val version: Long, val nombreTienda: String?, val sucursal: String)

/**
 * El estado de la conexión, para la pantalla. [alta] null: este celular no está dado de alta. [aviso]: lo último que
 * salió mal, para enseñarlo tal cual (el servicio escribe sus mensajes para eso); null si nada.
 */
data class EstadoConexion(
    val alta: Alta? = null,
    val red: Red = Red.SIN_PROBAR,
    /** La última vez que el servicio contestó (ms del reloj de pared); null si nunca en este proceso. */
    val ultimaVez: Long? = null,
    val copia: ResumenCopia? = null,
    /** Bajando la copia completa (varios MB): mientras, se sigue con la de antes si la hay. */
    val bajando: Boolean = false,
    val aviso: String? = null,
    /**
     * La caja le quitó el acceso a este celular y el dueño todavía no lo ha visto en Ajustes › La conexión: se guarda en
     * el disco ([AlmacenAlta.leerAviso]) y la pantalla principal también lo dice, discreto.
     */
    val sinAcceso: Boolean = false,
    /** Ya se leyó el disco: antes de esto no se sabe si hay alta. */
    val leida: Boolean = false,
)

/**
 * La conexión del celular con la caja principal (docs/celular-como-caja.md, «La secuencia de la app»): el alta, el
 * latido mientras la app está al frente, la copia del catálogo y sus cambios, y volver a encontrar la caja principal si
 * cambia de IP. Todo lo que cambia el alta o la copia corre en un solo hilo, en orden. Un objeto por proceso ([del]); las
 * pruebas lo arman con sus piezas.
 *
 * Lo que nunca hace: abrir turno, vender, mandar a la bandeja ni pedir la sesión de nadie. Y nunca late en segundo plano:
 * solo entre [alFrente] y [alFondo].
 */
class Conexion(
    private val almacenAlta: AlmacenAlta,
    private val almacenCopia: AlmacenCopia,
    private val cliente: ClienteCaja,
    /** El modelo de este celular, como se verá en Configuración › Cajas. */
    val equipo: String,
    private val descubrir: suspend () -> List<Direccion> = { Descubrimiento.buscar() },
    private val reloj: () -> Long = System::currentTimeMillis,
    /** Un reloj que no retrocede, para medir el latido. */
    private val cronometro: () -> Long = SystemClock::elapsedRealtime,
    hilo: CoroutineDispatcher? = null,
    private val msEntreLatidos: Long = MS_ENTRE_LATIDOS,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val hilo: CoroutineDispatcher = hilo ?: Dispatchers.IO.limitedParallelism(1)
    private val alcance = CoroutineScope(SupervisorJob() + this.hilo)

    private val _estado = MutableStateFlow(EstadoConexion())
    val estado: StateFlow<EstadoConexion> = _estado.asStateFlow()

    private val _copia = MutableStateFlow<Copia?>(null)
    /** La copia del catálogo; null sin alta, mientras se lee el disco o mientras baja la primera. */
    val copia: StateFlow<Copia?> = _copia.asStateFlow()

    private var leida = false
    private var alta: Alta? = null
    private var latido: Job? = null
    private var alFrente = false

    /** Latidos seguidos sin respuesta: con [FALLOS_PARA_BUSCAR] se busca la caja principal por UDP por si cambió de IP. */
    private var fallos = 0

    /**
     * La dirección guardada todavía no prueba quién es ([Identidad]): al leer el alta del disco y después de cualquier
     * caída. Mientras, la llave no sale hacia ella (como la caja de Windows): en esa IP puede haber ya otro equipo.
     */
    private var sinComprobar = false

    /**
     * Cambia con cada alta y al olvidarla: lo que termina después (una copia que bajaba, una búsqueda por UDP) ve que ya
     * no es la suya y no publica nada.
     */
    private var generacion = 0L

    /** La copia completa que está bajando (una sola a la vez); no la cancela irse al fondo. */
    private var descarga: Job? = null

    // ---- Lo que se lee del disco ----

    /** Empieza a leer el alta y la copia del disco, sin esperar (al abrir la app, fuera de la apertura de la cámara). */
    fun precargar() {
        alcance.launch { leerSiFalta() }
    }

    private fun leerSiFalta() {
        if (leida) return
        leida = true
        alta = almacenAlta.leer()
        sinComprobar = alta != null
        val c = if (alta != null) almacenCopia.leer()?.takeIf { it.caja.id.equals(alta!!.caja.id, ignoreCase = true) } else null
        _copia.value = c
        val pendiente = if (alta == null) almacenAlta.leerAviso() else null
        _estado.update {
            if (pendiente != null) it.copy(alta = null, copia = null, aviso = pendiente, sinAcceso = true, leida = true)
            else it.copy(alta = alta, copia = c?.resumen(), leida = true)
        }
    }

    // ---- El latido ----

    /** La app pasó al frente: late ahora y cada [msEntreLatidos] hasta [alFondo]. */
    fun alFrente() {
        alFrente = true
        if (latido?.isActive == true) return
        latido = alcance.launch {
            leerSiFalta()
            while (isActive && alFrente) {
                latir()
                delay(msEntreLatidos)
            }
        }
    }

    /** La app pasó a segundo plano: ni latidos ni cambios hasta que vuelva (la copia que esté bajando, termina). */
    fun alFondo() {
        alFrente = false
        latido?.cancel()
        latido = null
    }

    /**
     * Un latido: `GET /api/estado` con la llave (antes, si hace falta, que la dirección pruebe quién es); si las versiones
     * cambiaron, los cambios (o la copia completa). Con las mismas versiones también pide los cambios, que salen casi
     * vacíos, solo para saber si la llave sigue sirviendo: `/api/estado` contesta a cualquiera, con llave o sin ella.
     */
    suspend fun latir() = withContext(hilo) { latirEnElHilo() }

    private suspend fun latirEnElHilo() {
        var a = alta ?: return
        val gen = generacion
        if (sinComprobar) {
            a = when (comprobar(a)) {
                Comprobacion.PROBADA -> a
                Comprobacion.NO_CONTESTA -> {
                    sinContestar(a, gen)
                    return
                }
                Comprobacion.OTRO -> {
                    _estado.update { it.copy(red = Red.CAIDA) }
                    reencontrar(a, gen) ?: return
                }
            }
            if (gen != generacion) return
            sinComprobar = false
        }
        val desde = cronometro()
        val estadoServicio = try {
            cliente.estado(a.direccion, a.llave, a.caja.id)
        } catch (e: ErrorDelPos) {
            if (gen != generacion) return
            if (e.sinAlta) sinLlave(a, gen) else _estado.update { it.copy(aviso = e.message) }
            return
        } catch (e: IOException) {
            if (gen == generacion) sinContestar(a, gen)
            return
        }
        if (gen != generacion) return
        fallos = 0
        val tardo = cronometro() - desde
        _estado.update { it.copy(red = if (tardo >= MS_LENTA) Red.LENTA else Red.BUENA, ultimaVez = reloj(), aviso = null) }
        val c = _copia.value
        when {
            c == null -> bajarCompleta(a)
            c.desactualizada(estadoServicio.versionDatos, estadoServicio.versionExistencias, estadoServicio.base, estadoServicio.formatoCopia) -> pedirCambios(a, c, gen, aplicar = true)
            else -> pedirCambios(a, c, gen, aplicar = false)
        }
    }

    /** La caja principal no contestó: la dirección queda sin comprobar y, a los [FALLOS_PARA_BUSCAR], se busca por UDP. */
    private suspend fun sinContestar(a: Alta, gen: Long) {
        sinComprobar = true
        fallos++
        _estado.update { it.copy(red = Red.CAIDA) }
        if (fallos >= FALLOS_PARA_BUSCAR) {
            fallos = 0
            reencontrar(a, gen)
        }
    }

    /** Con [aplicar] falso, los cambios solo se piden para saber si la llave sigue sirviendo: no se aplica ni se guarda nada. */
    private suspend fun pedirCambios(a: Alta, c: Copia, gen: Long, aplicar: Boolean) {
        val cambios = try {
            cliente.cambios(a.direccion, a.llave, c)
        } catch (e: ErrorDelPos) {
            if (gen != generacion) return
            if (e.sinAlta) sinLlave(a, gen) else if (aplicar) _estado.update { it.copy(aviso = e.message) }
            return
        } catch (e: IOException) {
            if (gen == generacion) {
                sinComprobar = true
                _estado.update { it.copy(red = Red.CAIDA) }
            }
            return
        }
        if (!aplicar || gen != generacion) return
        if (cambios.paqueteCompleto != null) {
            bajarCompleta(a)
            return
        }
        publicar(c.aplicar(cambios, reloj()))
    }

    /**
     * La copia completa: la primera vez, cuando los cambios dicen que se baje entera o cuando la de aquí no se pudo leer.
     * Una sola a la vez: si ya está bajando, espera a esa. Corre aparte del latido: irse al fondo no la tira.
     */
    private suspend fun bajarCompleta(a: Alta) {
        val activa = descarga?.takeIf { it.isActive } ?: iniciarDescarga(a)
        activa.join()
    }

    private fun iniciarDescarga(a: Alta): Job {
        val gen = generacion
        return alcance.launch { descargar(a, gen) }.also { descarga = it }
    }

    private suspend fun descargar(a: Alta, gen: Long) {
        _estado.update { it.copy(bajando = true) }
        try {
            val paquete = cliente.paquete(a.direccion, a.llave, a.caja.id)
            val nueva = Copia.dePaquete(paquete, reloj())
            // Mientras bajaba se olvidó el alta (o se dio otra): no es de nadie.
            if (gen != generacion) return
            publicar(nueva)
            // La copia bajó: el servicio contestó (lo que tardó no dice nada de la red, son varios MB).
            _estado.update { it.copy(aviso = null, red = if (it.red == Red.LENTA) Red.LENTA else Red.BUENA, ultimaVez = reloj()) }
        } catch (e: ErrorDelPos) {
            if (gen != generacion) return
            if (e.sinAlta) sinLlave(a, gen) else _estado.update { it.copy(aviso = e.message) }
        } catch (e: IOException) {
            if (gen != generacion) return
            sinComprobar = true
            _estado.update { it.copy(red = Red.CAIDA, aviso = "No se pudo bajar la copia del catálogo: se vuelve a intentar.") }
        } finally {
            if (gen == generacion) _estado.update { it.copy(bajando = false) }
        }
    }

    private fun publicar(c: Copia) {
        almacenCopia.guardar(c)
        _copia.value = c
        _estado.update { it.copy(copia = c.resumen()) }
    }

    private enum class Comprobacion { PROBADA, NO_CONTESTA, OTRO }

    /** Si en la dirección guardada sigue la caja principal: `GET /api/identidad` sin llave, con un reto ([Identidad]). */
    private suspend fun comprobar(a: Alta): Comprobacion {
        val reto = Identidad.nuevoReto()
        val respuesta = try {
            cliente.identidad(a.direccion, a.caja.id, reto)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return Comprobacion.NO_CONTESTA
        } catch (e: Exception) {
            return Comprobacion.OTRO
        }
        return if (Identidad.esLaCajaPrincipal(respuesta, a.llave, a.llaveDelServicio, a.caja.id, reto, a.direccion)) Comprobacion.PROBADA else Comprobacion.OTRO
    }

    /**
     * Un 401 «caja-sin-alta»: solo se olvida la llave si quien lo dice es la caja principal. Si la dirección guardada
     * prueba quién es, le quitaron el acceso. Si no lo prueba (en esa IP ya hay otro POS, o la caja ya no conoce a este
     * celular y no puede firmar), se busca por UDP: si la caja principal está en otra IP, se muda; si nadie lo prueba, se
     * olvida. Si no contesta nadie, se revisa en el siguiente latido.
     */
    private suspend fun sinLlave(a: Alta, gen: Long) {
        when (comprobar(a)) {
            Comprobacion.PROBADA -> Unit
            Comprobacion.NO_CONTESTA -> {
                sinComprobar = true
                return
            }
            Comprobacion.OTRO -> {
                sinComprobar = true
                if (reencontrar(a, gen) != null) return
            }
        }
        if (gen == generacion) olvidarAlta(SIN_ACCESO)
    }

    /**
     * La caja principal cambió de IP: se busca otra vez por UDP y, antes de mandarle la llave, se le pide que pruebe quién
     * es ([Identidad]). Se acepta la primera que lo prueba; null si ninguna (o si mientras se olvidó el alta).
     */
    private suspend fun reencontrar(a: Alta, gen: Long): Alta? {
        val candidatas = try {
            descubrir()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        for (d in candidatas) {
            if (d == a.direccion) continue
            val reto = Identidad.nuevoReto()
            val respuesta = try {
                cliente.identidad(d, a.caja.id, reto)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            if (!Identidad.esLaCajaPrincipal(respuesta, a.llave, a.llaveDelServicio, a.caja.id, reto, d)) continue
            if (gen != generacion) return null
            val nueva = a.copy(direccion = d)
            almacenAlta.guardarDireccion(d)
            alta = nueva
            sinComprobar = false
            fallos = 0
            _estado.update { it.copy(alta = nueva) }
            return nueva
        }
        return null
    }

    // ---- El alta ----

    /** Las cajas principales que contestan en la red (puede haber una de prueba): el dueño elige o escribe la IP. */
    suspend fun buscarCajas(): List<Direccion> = descubrir()

    /** `GET /api/estado` sin llave: si en esa dirección hay un POS y si da de alta (no es «La única caja» ni una tienda nueva). */
    suspend fun revisar(direccion: Direccion): EstadoServicioDto {
        val e = cliente.estado(direccion)
        if (e.servicio != SERVICIO) throw IOException("Ahí no contesta el POS.")
        return e
    }

    /** Las sucursales con sus cajas, con el usuario y el PIN de quien da de alta (el servicio dice si no puede). */
    suspend fun opcionesDeAlta(direccion: Direccion, usuario: String, pin: String): List<SucursalAltaDto> =
        cliente.opcionesDeAlta(direccion, usuario.trim(), pin.trim())

    /**
     * Da de alta este celular como una caja nueva de [sucursalId] y guarda su llave; después baja la copia. [nombre] y
     * [serie] como los pide la caja de Windows (la serie, de 1 a 3 letras; nunca se usa: el celular no vende).
     */
    suspend fun darDeAlta(direccion: Direccion, usuario: String, pin: String, sucursalId: String, nombre: String, serie: String): Alta {
        val desde = cronometro()
        val respuesta = cliente.darDeAlta(
            direccion,
            SolicitudAltaCaja(usuario.trim(), pin.trim(), sucursalId, cajaId = null, nombre = nombre.trim(), serie = serie.trim().uppercase(), equipo = equipo),
        )
        // El alta ya habló con el servicio: la red se sabe desde ahora, sin esperar al primer latido (30 s).
        val red = if (cronometro() - desde >= MS_LENTA) Red.LENTA else Red.BUENA
        val nueva = Alta(direccion, respuesta.caja, equipo, usuario.trim(), respuesta.llave, respuesta.llaveDelServicio, reloj())
        withContext(hilo) {
            leerSiFalta()
            almacenCopia.borrar()
            almacenAlta.guardar(nueva)
            generacion++
            alta = nueva
            fallos = 0
            sinComprobar = false
            _copia.value = null
            _estado.update { EstadoConexion(alta = nueva, red = red, ultimaVez = nueva.altaEn, leida = true) }
            iniciarDescarga(nueva)
        }
        return nueva
    }

    /**
     * Borra la llave y la copia: la caja le quitó el acceso a este celular (401 «caja-sin-alta»), o se quiere empezar de
     * nuevo. [aviso] se guarda (solo el aviso, nada de la caja) y se enseña en Ajustes hasta que el dueño lo ve
     * ([avisoVisto]) o vuelve a dar de alta: puede llegar con la entrada con la huella tapando todo, o reiniciarse la app.
     */
    suspend fun olvidarAlta(aviso: String? = null) = withContext(hilo) {
        generacion++
        almacenAlta.borrar(aviso)
        almacenCopia.borrar()
        alta = null
        sinComprobar = false
        _copia.value = null
        _estado.value = EstadoConexion(aviso = aviso, sinAcceso = aviso != null, leida = true)
    }

    /**
     * Ajustes › La conexión enseñó el aviso de sin acceso: ya no se guarda ni sale en la pantalla principal. En La conexión
     * se sigue leyendo mientras la app siga abierta.
     */
    fun avisoVisto() {
        alcance.launch {
            if (!_estado.value.sinAcceso) return@launch
            almacenAlta.borrarAviso()
            _estado.update { it.copy(sinAcceso = false) }
        }
    }

    private fun Copia.resumen() = ResumenCopia(productos.size, codigos.size, guardadaEn, version, nombreTienda, caja.sucursal)

    companion object {
        const val SERVICIO = "Papelería POS"

        /** Cada 30 s al frente (la caja de Windows late cada 5; el celular no lo necesita tan seguido). */
        const val MS_ENTRE_LATIDOS = 30_000L

        /** Un latido que tarda más que esto es red lenta; más que lo que espera el cliente, caída. */
        const val MS_LENTA = 1_500L

        /** Latidos seguidos sin respuesta antes de buscar la caja principal por UDP por si cambió de IP. */
        const val FALLOS_PARA_BUSCAR = 2

        const val SIN_ACCESO = "Este celular ya no tiene acceso: la caja le quitó la llave. Para volver a usarlo, se da de alta otra vez."

        @Volatile private var proceso: Conexion? = null

        /** La del proceso: la usan la app, el escaneo rápido y Ajustes. */
        fun del(contexto: Context): Conexion = proceso ?: synchronized(this) {
            proceso ?: crear(contexto.applicationContext).also { proceso = it }
        }

        /** Para las pruebas: como un proceso nuevo (la siguiente [del] crea otra y vuelve a leer el disco), o con [otra]. */
        internal fun reiniciar(otra: Conexion? = null) {
            proceso = otra
        }

        private fun crear(app: Context) = Conexion(
            almacenAlta = AlmacenAlta(app.getSharedPreferences(AlmacenAlta.ARCHIVO, Context.MODE_PRIVATE), CifradorKeystore()),
            almacenCopia = AlmacenCopia(File(app.filesDir, AlmacenCopia.CARPETA)),
            cliente = ClienteCaja(),
            equipo = equipoDeEsteCelular(),
        )

        /** «Pixel 8 Pro» (el fabricante solo si el modelo no lo trae). */
        fun equipoDeEsteCelular(): String {
            val modelo = Build.MODEL.orEmpty().trim()
            val marca = Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.uppercase() }
            return if (modelo.startsWith(marca, ignoreCase = true) || marca.isEmpty()) modelo.ifEmpty { "Celular" } else "$marca $modelo"
        }

        /** La serie que se propone: CEL si está libre; si no, CEA, CEB… (el servicio pide de 1 a 3 letras, solo letras). */
        fun seriePropuesta(cajas: List<CajaAltaDto>): String {
            val usadas = cajas.map { it.serie.uppercase() }.toSet()
            if ("CEL" !in usadas) return "CEL"
            for (c in 'A'..'Z') if ("CE$c" !in usadas) return "CE$c"
            for (c in 'A'..'Z') for (d in 'A'..'Z') if ("C$c$d" !in usadas) return "C$c$d"
            return "CEL"
        }

        /** «Celular de Luis», con el usuario que da de alta. */
        fun nombrePropuesto(usuario: String): String {
            val u = usuario.trim()
            return if (u.isEmpty()) "Celular" else "Celular de ${u.replaceFirstChar { it.uppercase() }}"
        }
    }
}
