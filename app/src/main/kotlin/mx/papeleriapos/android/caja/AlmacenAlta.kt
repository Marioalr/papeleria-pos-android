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

/** El alta en las preferencias de la app (`conexion.xml`), con la llave cifrada por [Cifrador]. */
class AlmacenAlta(private val prefs: SharedPreferences, private val cifrador: Cifrador) {
    /** El alta guardada; null si no hay o la llave ya no se puede descifrar (entonces se borra lo que quedaba). */
    fun leer(): Alta? {
        val cifrada = prefs.getString(LLAVE, null) ?: return null
        val llave = cifrador.descifrar(cifrada)
        if (llave.isNullOrEmpty()) {
            borrar()
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
        }
    }

    /** La caja principal cambió de IP (ya probada con [Identidad]). */
    fun guardarDireccion(direccion: Direccion) {
        prefs.edit(commit = true) {
            putString(IP, direccion.ip)
            putInt(PUERTO, direccion.puerto)
        }
    }

    fun borrar() {
        prefs.edit(commit = true) { clear() }
    }

    companion object {
        const val ARCHIVO = "conexion"
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
    }
}
