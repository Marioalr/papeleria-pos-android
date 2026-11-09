package mx.papeleriapos.android.caja

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * El alta de este celular como caja (docs/celular-como-caja.md, «Dar de alta el celular»): dónde está la caja
 * principal, qué caja es este celular y su llave. La llave solo vive aquí (cifrada en disco) y en el encabezado de cada
 * llamada; nunca en el registro ni en los CSV.
 *
 * @param usuario Con qué usuario se dio de alta (su nombre de usuario, nunca su PIN): para cuando algo pida quién.
 * @param llaveDelServicio La llave pública del servicio que dio de alta, para reconocerlo si cambia de IP ([Identidad]); null con un servicio de antes.
 */
data class Alta(
    val direccion: Direccion,
    val caja: CajaDto,
    val equipo: String,
    val usuario: String,
    val llave: String,
    val llaveDelServicio: String?,
    val altaEn: Long,
)

/**
 * El alta en las preferencias de la app (`conexion.xml`), con la llave cifrada por [Cifrador]. Junto, sin nada de la
 * caja, el aviso de que la caja le quitó el acceso a este celular ([leerAviso]): se queda hasta que el dueño lo ve en
 * Ajustes › La conexión o vuelve a dar de alta, aunque la app se reinicie.
 */
class AlmacenAlta(private val prefs: SharedPreferences, private val cifrador: Cifrador) {
    /** El alta guardada; null si no hay o la llave ya no se puede descifrar (entonces se borra lo que quedaba). */
    fun leer(): Alta? {
        val cifrada = prefs.getString(LLAVE, null) ?: return null
        val llave = cifrador.descifrar(cifrada)
        if (llave.isNullOrEmpty()) {
            borrar(aviso = leerAviso())
            return null
        }
        val ip = prefs.getString(IP, null) ?: return null
        val cajaId = prefs.getString(CAJA_ID, null) ?: return null
        return Alta(
            direccion = Direccion(ip, prefs.getInt(PUERTO, RedLocal.PUERTO_HTTP)),
            caja = CajaDto(
                id = cajaId,
                nombre = prefs.getString(CAJA_NOMBRE, "").orEmpty(),
                serie = prefs.getString(SERIE, "").orEmpty(),
                sucursalId = prefs.getString(SUCURSAL_ID, "").orEmpty(),
                sucursal = prefs.getString(SUCURSAL, "").orEmpty(),
                equipo = prefs.getString(EQUIPO, null),
            ),
            equipo = prefs.getString(EQUIPO, "").orEmpty(),
            usuario = prefs.getString(USUARIO, "").orEmpty(),
            llave = llave,
            llaveDelServicio = prefs.getString(LLAVE_SERVICIO, null),
            altaEn = prefs.getLong(ALTA_EN, 0),
        )
    }

    fun guardar(alta: Alta) {
        prefs.edit(commit = true) {
            putString(LLAVE, cifrador.cifrar(alta.llave))
            putString(IP, alta.direccion.ip)
            putInt(PUERTO, alta.direccion.puerto)
            putString(CAJA_ID, alta.caja.id)
            putString(CAJA_NOMBRE, alta.caja.nombre)
            putString(SERIE, alta.caja.serie)
            putString(SUCURSAL_ID, alta.caja.sucursalId)
            putString(SUCURSAL, alta.caja.sucursal)
            putString(EQUIPO, alta.equipo)
            putString(USUARIO, alta.usuario)
            if (alta.llaveDelServicio != null) putString(LLAVE_SERVICIO, alta.llaveDelServicio) else remove(LLAVE_SERVICIO)
            putLong(ALTA_EN, alta.altaEn)
            remove(AVISO)
        }
    }

    /** La caja principal cambió de IP (ya probada con [Identidad]). */
    fun guardarDireccion(direccion: Direccion) {
        prefs.edit(commit = true) {
            putString(IP, direccion.ip)
            putInt(PUERTO, direccion.puerto)
        }
    }

    /** Borra el alta (la llave y los datos de la caja); con [aviso], deja solo el aviso para enseñarlo después. */
    fun borrar(aviso: String? = null) {
        prefs.edit(commit = true) {
            clear()
            if (aviso != null) putString(AVISO, aviso)
        }
    }

    /** El aviso que quedó al borrar el alta ([borrar]) y que el dueño todavía no ha visto; null si no hay. */
    fun leerAviso(): String? = prefs.getString(AVISO, null)

    /** El dueño ya vio el aviso. */
    fun borrarAviso() {
        prefs.edit(commit = true) { remove(AVISO) }
    }

    companion object {
        const val ARCHIVO = "conexion"

        /**
         * Si hay un alta guardada, sin descifrar la llave ni leer la copia: lo único que hace falta para bloquear la app
         * al abrir en frío antes del primer cuadro ([mx.papeleriapos.android.entrada.Entrada.bloquearAlAbrir]).
         */
        fun hayAlta(contexto: android.content.Context): Boolean =
            contexto.applicationContext.getSharedPreferences(ARCHIVO, android.content.Context.MODE_PRIVATE).contains(LLAVE)

        private const val LLAVE = "llave"
        private const val IP = "ip"
        private const val PUERTO = "puerto"
        private const val CAJA_ID = "caja_id"
        private const val CAJA_NOMBRE = "caja_nombre"
        private const val SERIE = "serie"
        private const val SUCURSAL_ID = "sucursal_id"
        private const val SUCURSAL = "sucursal"
        private const val EQUIPO = "equipo"
        private const val USUARIO = "usuario"
        private const val LLAVE_SERVICIO = "llave_servicio"
        private const val ALTA_EN = "alta_en"
        private const val AVISO = "aviso"
    }
}
