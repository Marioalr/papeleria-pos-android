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
 * Las altas de este celular en las preferencias de la app (`conexion.xml`), cada una con su llave cifrada por [Cifrador]:
 * una por sucursal enlazada (cada sucursal tiene su caja principal, su base y su llave), y una sola activa, la que usan
 * el latido, Precio, Cobrar y Mandar. Cambiar de una a otra lo hace el dueño ([activar]); nunca se cambia solo.
 *
 * Cada alta se guarda con sus campos terminados en «.<id de su caja>» y [ENLACES] dice cuáles hay. Un alta de antes de la
 * 0.5.5 (los campos sin terminación) se vuelve la primera la primera vez que se lee, sin darse de alta otra vez.
 *
 * Junto, sin nada de la caja, el aviso de que una caja le quitó el acceso a este celular ([leerAviso]): se queda hasta que
 * el dueño lo ve en Ajustes › La conexión o vuelve a dar de alta, aunque la app se reinicie.
 */
class AlmacenAlta(private val prefs: SharedPreferences, private val cifrador: Cifrador) {
    /** El alta activa; null si no hay ninguna activa (o la suya ya no se puede descifrar: entonces se quita). */
    fun leer(): Alta? {
        migrar()
        val id = prefs.getString(ACTIVO, null) ?: return null
        return leerEnlace(id)
    }

    /** Todas las altas guardadas, en el orden en que se dieron de alta; las que ya no se pueden descifrar se quitan. */
    fun enlaces(): List<Alta> {
        migrar()
        return ids().mapNotNull(::leerEnlace)
    }

    private fun ids(): List<String> = prefs.getString(ENLACES, null).orEmpty().split(',').filter { it.isNotBlank() }

    private fun leerEnlace(id: String): Alta? {
        val cifrada = prefs.getString(LLAVE + "." + id, null) ?: return null
        val llave = cifrador.descifrar(cifrada)
        if (llave.isNullOrEmpty()) {
            quitar(id, aviso = leerAviso())
            return null
        }
        fun texto(campo: String) = prefs.getString("$campo.$id", null)
        val ip = texto(IP) ?: return null
        return Alta(
            direccion = Direccion(ip, prefs.getInt("$PUERTO.$id", RedLocal.PUERTO_HTTP)),
            caja = CajaDto(
                id = texto(CAJA_ID) ?: id,
                nombre = texto(CAJA_NOMBRE).orEmpty(),
                serie = texto(SERIE).orEmpty(),
                sucursalId = texto(SUCURSAL_ID).orEmpty(),
                sucursal = texto(SUCURSAL).orEmpty(),
                equipo = texto(EQUIPO),
            ),
            equipo = texto(EQUIPO).orEmpty(),
            usuario = texto(USUARIO).orEmpty(),
            llave = llave,
            llaveDelServicio = texto(LLAVE_SERVICIO),
            altaEn = prefs.getLong("$ALTA_EN.$id", 0),
        )
    }

    /** Guarda [alta] (otra sucursal, o la misma caja con otra llave) y la deja activa; las demás no se tocan. */
    fun guardar(alta: Alta) {
        val id = clave(alta.caja.id)
        prefs.edit(commit = true) {
            escribir(id, alta)
            putString(ENLACES, (ids().filter { it != id } + id).joinToString(","))
            putString(ACTIVO, id)
            remove(AVISO)
        }
    }

    private fun SharedPreferences.Editor.escribir(id: String, alta: Alta) {
        putString("$LLAVE.$id", cifrador.cifrar(alta.llave))
        putString("$IP.$id", alta.direccion.ip)
        putInt("$PUERTO.$id", alta.direccion.puerto)
        putString("$CAJA_ID.$id", alta.caja.id)
        putString("$CAJA_NOMBRE.$id", alta.caja.nombre)
        putString("$SERIE.$id", alta.caja.serie)
        putString("$SUCURSAL_ID.$id", alta.caja.sucursalId)
        putString("$SUCURSAL.$id", alta.caja.sucursal)
        putString("$EQUIPO.$id", alta.equipo)
        putString("$USUARIO.$id", alta.usuario)
        if (alta.llaveDelServicio != null) putString("$LLAVE_SERVICIO.$id", alta.llaveDelServicio) else remove("$LLAVE_SERVICIO.$id")
        putLong("$ALTA_EN.$id", alta.altaEn)
    }

    /** Deja activa el alta de esa caja (otra sucursal); false si no está guardada. */
    fun activar(cajaId: String): Boolean {
        val id = clave(cajaId)
        if (id !in ids()) return false
        prefs.edit(commit = true) { putString(ACTIVO, id) }
        return true
    }

    /** La caja principal de esa alta cambió de IP (ya probada con [Identidad]). */
    fun guardarDireccion(cajaId: String, direccion: Direccion) {
        val id = clave(cajaId)
        if (id !in ids()) return
        prefs.edit(commit = true) {
            putString("$IP.$id", direccion.ip)
            putInt("$PUERTO.$id", direccion.puerto)
        }
    }

    /**
     * Quita el alta de esa caja (su llave y sus datos); las demás se quedan. Si era la activa, no queda ninguna activa: el
     * dueño elige otra en Ajustes. Con [aviso], lo deja para enseñarlo después.
     */
    fun quitar(cajaId: String, aviso: String? = null) {
        val id = clave(cajaId)
        val quedan = ids().filter { it != id }
        prefs.edit(commit = true) {
            for (campo in CAMPOS) remove("$campo.$id")
            if (quedan.isEmpty()) remove(ENLACES) else putString(ENLACES, quedan.joinToString(","))
            if (prefs.getString(ACTIVO, null) == id) remove(ACTIVO)
            if (aviso != null) putString(AVISO, aviso)
        }
    }

    /** Borra todas las altas; con [aviso], deja solo el aviso para enseñarlo después. */
    fun borrar(aviso: String? = null) {
        prefs.edit(commit = true) {
            clear()
            if (aviso != null) putString(AVISO, aviso)
        }
    }

    /** El aviso que quedó al quitar un alta ([quitar]) y que el dueño todavía no ha visto; null si no hay. */
    fun leerAviso(): String? = prefs.getString(AVISO, null)

    /** El dueño ya vio el aviso. */
    fun borrarAviso() {
        prefs.edit(commit = true) { remove(AVISO) }
    }

    /** Un alta de antes de la 0.5.5 (los campos sin terminación) pasa a ser la primera y la activa. */
    private fun migrar() {
        if (!prefs.contains(LLAVE)) return
        val id = prefs.getString(CAJA_ID, null)?.let(::clave)
        prefs.edit(commit = true) {
            if (id != null && prefs.contains(IP)) {
                for (campo in CAMPOS) {
                    when (val valor = prefs.all[campo]) {
                        is String -> putString("$campo.$id", valor)
                        is Int -> putInt("$campo.$id", valor)
                        is Long -> putLong("$campo.$id", valor)
                    }
                }
                putString(ENLACES, (ids().filter { it != id } + id).joinToString(","))
                putString(ACTIVO, id)
            }
            for (campo in CAMPOS) remove(campo)
        }
    }

    companion object {
        const val ARCHIVO = "conexion"

        /**
         * Si hay alguna alta guardada, sin descifrar la llave ni leer la copia: lo único que hace falta para bloquear la
         * app al abrir en frío antes del primer cuadro ([mx.papeleriapos.android.entrada.Entrada.bloquearAlAbrir]).
         */
        fun hayAlta(contexto: android.content.Context): Boolean {
            val prefs = contexto.applicationContext.getSharedPreferences(ARCHIVO, android.content.Context.MODE_PRIVATE)
            return prefs.contains(LLAVE) || !prefs.getString(ENLACES, null).isNullOrBlank()
        }

        /** El id de la caja como se guarda: en minúsculas y solo letras, cifras y guiones (es un Guid). */
        private fun clave(cajaId: String) = cajaId.lowercase().filter { it.isLetterOrDigit() || it == '-' }

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
        private const val ENLACES = "enlaces"
        private const val ACTIVO = "activo"
        private val CAMPOS = listOf(LLAVE, IP, PUERTO, CAJA_ID, CAJA_NOMBRE, SERIE, SUCURSAL_ID, SUCURSAL, EQUIPO, USUARIO, LLAVE_SERVICIO, ALTA_EN)
    }
}
