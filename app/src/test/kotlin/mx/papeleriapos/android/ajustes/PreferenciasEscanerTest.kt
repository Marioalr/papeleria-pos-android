package mx.papeleriapos.android.ajustes

import android.content.SharedPreferences
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.Lente
import mx.papeleriapos.escaner.Motor
import mx.papeleriapos.escaner.ResolucionAnalisis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenciasEscanerTest {
    @Test
    fun `lo que se guarda se lee igual y sin nada guardado es lo de omision`() {
        val prefs = PrefsEnMemoria()
        val p = PreferenciasEscaner(prefs)
        assertEquals(ConfigEscaner(), p.leer())
        val c = ConfigEscaner(
            motor = Motor.ZXING_CPP, lente = Lente.GRAN_ANGULAR, fps60 = true, fpsFijo = false,
            resolucion = ResolucionAnalisis.FULL_HD, leerGirados = true, recortarFranja = false, altoFranja = 0.5f,
        )
        p.guardar(c)
        assertEquals(c, p.leer())
        p.borrar()
        assertEquals(ConfigEscaner(), p.leer())
    }

    @Test
    fun `un valor guardado que ya no existe cae en lo de omision sin tirar lo demas`() {
        val prefs = PrefsEnMemoria()
        val p = PreferenciasEscaner(prefs)
        p.guardar(ConfigEscaner(resolucion = ResolucionAnalisis.UHD, leerGirados = true))
        prefs.edit().putString("resolucion", "8K").putString("lente", "TELE").apply()
        val leida = p.leer()
        assertEquals(ResolucionAnalisis.HD, leida.resolucion)
        assertEquals(Lente.SISTEMA, leida.lente)
        assertTrue(leida.leerGirados)
    }
}

/** Un SharedPreferences en memoria: lo justo para [PreferenciasEscaner], sin Robolectric. */
private class PrefsEnMemoria : SharedPreferences {
    private val datos = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(datos)
    override fun getString(key: String, defValue: String?): String? = datos[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (datos[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int = datos[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = datos[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = datos[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = datos[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = key in datos
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        private val cambios = LinkedHashMap<String, Any?>()
        private var limpiar = false

        override fun putString(key: String, value: String?) = apply { cambios[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { cambios[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { cambios[key] = value }
        override fun putLong(key: String, value: Long) = apply { cambios[key] = value }
        override fun putFloat(key: String, value: Float) = apply { cambios[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { cambios[key] = value }
        override fun remove(key: String) = apply { cambios[key] = null }
        override fun clear() = apply { limpiar = true }
        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (limpiar) datos.clear()
            for ((k, v) in cambios) if (v == null) datos.remove(k) else datos[k] = v
        }
    }
}
