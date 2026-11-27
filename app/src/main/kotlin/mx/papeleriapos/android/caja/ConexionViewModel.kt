package mx.papeleriapos.android.caja

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Los pasos del alta (docs/celular-como-caja.md, «Dar de alta el celular», como la caja de Windows): encontrar la caja
 * principal, el usuario y el PIN de quien da de alta, el nombre y la serie de esta caja, listo. Cada paso guarda lo que
 * se escribió y lo que contestó el servicio; [error] es lo último que salió mal, tal cual lo dijo el servicio.
 */
sealed interface PasoAlta {
    val trabajando: Boolean
    val error: String?

    /** 1. Buscar la caja principal en la red o escribir su IP. [encontradas]: las que contestaron por UDP. */
    data class Buscar(
        val encontradas: List<Direccion> = emptyList(),
        val busco: Boolean = false,
        val ipEscrita: String = "",
        /** Si no contestó nadie: por qué se ve que no ([Diagnostico.pistaAlBuscar]), abajo de «Ninguna caja principal contestó…». */
        val pista: String? = null,
        override val trabajando: Boolean = false,
        override val error: String? = null,
    ) : PasoAlta

    /** 2. El POS en [direccion] (versión [version]): el usuario y el PIN de quien da de alta. */
    data class Pin(
        val direccion: Direccion,
        val version: String,
        val usuario: String = "",
        val pin: String = "",
        override val trabajando: Boolean = false,
        override val error: String? = null,
    ) : PasoAlta

    /** 3. La sucursal (si hay más de una), el nombre y la serie de esta caja; las cajas que ya hay se ven para no repetir serie. */
    data class Datos(
        val direccion: Direccion,
        val usuario: String,
        val pin: String,
        val sucursales: List<SucursalAltaDto>,
        val sucursalId: String,
        val nombre: String,
        val serie: String,
        override val trabajando: Boolean = false,
        override val error: String? = null,
    ) : PasoAlta {
        val sucursal: SucursalAltaDto? get() = sucursales.firstOrNull { it.id == sucursalId }
    }

    /** 4. Dado de alta: la copia ya baja ([Conexion.estado] dice cuánto lleva). */
    data class Listo(val alta: Alta) : PasoAlta {
        override val trabajando get() = false
        override val error: String? get() = null
    }
}

/** El alta paso a paso, para la pantalla de la conexión; el estado de la conexión en sí está en [Conexion.estado]. */
class ConexionViewModel(app: Application, private val conexion: Conexion) : AndroidViewModel(app) {
    /** El que crea la pantalla (`viewModel()`), con la conexión del proceso; las pruebas pasan la suya. */
    @Suppress("unused")
    constructor(app: Application) : this(app, Conexion.del(app))

    private val _paso = MutableStateFlow<PasoAlta>(PasoAlta.Buscar())
    val paso: StateFlow<PasoAlta> = _paso.asStateFlow()
    val estado: StateFlow<EstadoConexion> = conexion.estado
    private var trabajo: Job? = null

    private val _enlazando = MutableStateFlow(false)
    /** Con un alta ya activa, el dueño tocó «Enlazar otra sucursal»: se enseñan los pasos del alta sin quitar nada. */
    val enlazando: StateFlow<Boolean> = _enlazando.asStateFlow()

    init {
        // Por si la pantalla se abre antes de que la Activity haya puesto la conexión al frente (lee el alta del disco).
        conexion.precargar()
    }

    /** La pantalla enseñó el aviso de que la caja le quitó el acceso ([Conexion.avisoVisto]). */
    fun avisoVisto() = conexion.avisoVisto()

    /** «Enlazar otra sucursal»: el alta de siempre; la que ya está se queda, y la nueva queda en uso al terminar. */
    fun enlazarOtra() {
        _enlazando.value = true
        empezar()
    }

    /** Se dejó de enlazar otra sucursal: de regreso a la que está en uso. */
    fun cancelarEnlace() {
        trabajo?.cancel()
        _enlazando.value = false
        _paso.value = PasoAlta.Buscar()
    }

    /** «Usar esta»: el dueño eligió otra sucursal enlazada; su caja principal prueba quién es en el siguiente latido. */
    fun usar(cajaId: String) {
        viewModelScope.launch {
            conexion.cambiarA(cajaId)
            _enlazando.value = false
            _paso.value = PasoAlta.Buscar()
        }
    }

    /** Empieza el alta desde el principio, buscando en la red. */
    fun empezar() {
        _paso.value = PasoAlta.Buscar()
        buscar()
    }

    /** Pregunta por UDP quién es la caja principal (1.5 s). */
    fun buscar() {
        val b = _paso.value as? PasoAlta.Buscar ?: return
        if (b.trabajando) return
        _paso.value = b.copy(trabajando = true, error = null, pista = null)
        trabajo = viewModelScope.launch {
            var falla: Exception? = null
            val encontradas = try {
                conexion.buscarCajas()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                falla = e
                emptyList()
            }
            val pista = if (encontradas.isEmpty()) Diagnostico.pistaAlBuscar(conexion.redAlFallar("Nadie contestó al buscar por UDP", null, falla)) else null
            _paso.update { p ->
                if (p is PasoAlta.Buscar) p.copy(encontradas = encontradas, busco = true, trabajando = false, pista = pista) else p
            }
        }
    }

    fun escribirIp(texto: String) = _paso.update { if (it is PasoAlta.Buscar) it.copy(ipEscrita = texto, error = null) else it }

    /** Se eligió una dirección (o se escribió): se revisa que ahí conteste el POS y que dé de alta. */
    fun elegir(direccion: Direccion?) {
        val b = _paso.value as? PasoAlta.Buscar ?: return
        val d = direccion ?: RedLocal.direccionDe(b.ipEscrita)
        if (d == null) {
            _paso.value = b.copy(error = "Escribe la IP de la caja principal, como 192.168.1.10. Solo direcciones de la red de la tienda.")
            return
        }
        _paso.value = b.copy(trabajando = true, error = null)
        trabajo = viewModelScope.launch {
            try {
                val e = conexion.revisar(d)
                _paso.value = when {
                    e.unaSolaCaja -> b.copy(trabajando = false, error = "Esa caja principal se instaló como «La única caja» y no da de alta otros equipos.")
                    e.tiendaNueva -> b.copy(trabajando = false, error = "En esa caja principal todavía no hay tienda: primero se crea ahí.")
                    else -> PasoAlta.Pin(d, e.version)
                }
            } catch (e: ErrorDelPos) {
                _paso.value = b.copy(trabajando = false, error = e.message)
            } catch (e: IOException) {
                _paso.value = b.copy(trabajando = false, error = Diagnostico.alUsarIp(d, conexion.redAlFallar("No contestó la IP de la caja principal", d, e)))
            }
        }
    }

    fun escribirUsuario(texto: String) = _paso.update { if (it is PasoAlta.Pin) it.copy(usuario = texto, error = null) else it }
    fun escribirPin(texto: String) = _paso.update { if (it is PasoAlta.Pin) it.copy(pin = texto.filter(Char::isDigit).take(6), error = null) else it }

    /** Con el usuario y el PIN, las sucursales y sus cajas; se propone el nombre y la serie. */
    fun identificarse() {
        val p = _paso.value as? PasoAlta.Pin ?: return
        if (p.usuario.isBlank() || p.pin.isBlank()) {
            _paso.value = p.copy(error = "Escribe tu usuario y tu PIN del POS.")
            return
        }
        _paso.value = p.copy(trabajando = true, error = null)
        trabajo = viewModelScope.launch {
            try {
                val sucursales = conexion.opcionesDeAlta(p.direccion, p.usuario, p.pin)
                if (sucursales.isEmpty()) {
                    _paso.value = p.copy(trabajando = false, error = "El POS no tiene sucursales todavía.")
                    return@launch
                }
                val primera = sucursales[0]
                _paso.value = PasoAlta.Datos(
                    p.direccion, p.usuario.trim(), p.pin, sucursales, primera.id,
                    nombre = Conexion.nombrePropuesto(p.usuario), serie = Conexion.seriePropuesta(primera.cajas),
                )
            } catch (e: ErrorDelPos) {
                _paso.value = p.copy(trabajando = false, error = e.message)
            } catch (e: IOException) {
                _paso.value = p.copy(trabajando = false, error = noContesto(p.direccion, "Las opciones del alta no contestaron", e))
            }
        }
    }

    fun elegirSucursal(id: String) = _paso.update { p ->
        if (p is PasoAlta.Datos && p.sucursalId != id) {
            val s = p.sucursales.firstOrNull { it.id == id } ?: return@update p
            p.copy(sucursalId = id, serie = Conexion.seriePropuesta(s.cajas), error = null)
        } else {
            p
        }
    }

    fun escribirNombre(texto: String) = _paso.update { if (it is PasoAlta.Datos) it.copy(nombre = texto, error = null) else it }
    fun escribirSerie(texto: String) = _paso.update { if (it is PasoAlta.Datos) it.copy(serie = texto.uppercase().filter { c -> c in 'A'..'Z' }.take(3), error = null) else it }

    /** `POST /api/alta`: la caja nueva y su llave; luego baja la copia. */
    fun darDeAlta() {
        val d = _paso.value as? PasoAlta.Datos ?: return
        if (d.nombre.isBlank() || d.serie.isBlank()) {
            _paso.value = d.copy(error = "Escribe el nombre de la caja y su serie: de 1 a 3 letras.")
            return
        }
        _paso.value = d.copy(trabajando = true, error = null)
        trabajo = viewModelScope.launch {
            try {
                val alta = conexion.darDeAlta(d.direccion, d.usuario, d.pin, d.sucursalId, d.nombre, d.serie)
                _enlazando.value = false
                _paso.value = PasoAlta.Listo(alta)
            } catch (e: ErrorDelPos) {
                _paso.value = d.copy(trabajando = false, error = e.message)
            } catch (e: IOException) {
                _paso.value = d.copy(trabajando = false, error = noContesto(d.direccion, "El alta no contestó", e))
            }
        }
    }

    /** «No contestó la caja principal. Inténtalo otra vez.», con la causa abajo si se ve una (y al registro). */
    private fun noContesto(direccion: Direccion, que: String, e: IOException): String {
        val red = conexion.redAlFallar(que, direccion, e)
        val causas = Diagnostico.causas(red, direccion.ip)
        return (listOf("No contestó la caja principal. Inténtalo otra vez.") + Diagnostico.textos(causas, Diagnostico.Donde.IP, direccion.ip, red)).joinToString("\n")
    }

    /** Regresa al paso de antes (sin lo que contestó el servicio para el que se deja). */
    fun atras() {
        trabajo?.cancel()
        _paso.update { p ->
            when (p) {
                is PasoAlta.Pin -> PasoAlta.Buscar(ipEscrita = p.direccion.toString(), encontradas = listOf(p.direccion), busco = true)
                is PasoAlta.Datos -> PasoAlta.Pin(p.direccion, "", usuario = p.usuario)
                else -> p
            }
        }
    }
}
