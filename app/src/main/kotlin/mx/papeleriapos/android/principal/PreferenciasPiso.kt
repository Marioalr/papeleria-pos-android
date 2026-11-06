package mx.papeleriapos.android.principal

import android.content.SharedPreferences
import androidx.core.content.edit
import mx.papeleriapos.android.botonrapido.Lugar

/**
 * [AlmacenPiso] en las preferencias «escaner» (las mismas de [mx.papeleriapos.android.ajustes.PreferenciasEscaner], que
 * la Application ya precarga al arrancar), con llaves propias.
 */
class PreferenciasPiso(private val prefs: SharedPreferences) : AlmacenPiso {
    override fun leerModo(): ModoApp? = ModoApp.deNombre(prefs.getString(MODO, null))
    override fun guardarModo(modo: ModoApp) = prefs.edit { putString(MODO, modo.name) }

    override fun leerZurdo(): Boolean = prefs.getBoolean(ZURDO, false)
    override fun guardarZurdo(zurdo: Boolean) = prefs.edit { putBoolean(ZURDO, zurdo) }

    override fun leerPistasVistas(): Set<ModoApp> =
        prefs.getStringSet(PISTAS_VISTAS, null).orEmpty().mapNotNull { ModoApp.deNombre(it) }.toSet()

    override fun guardarPistasVistas(modos: Set<ModoApp>) =
        prefs.edit { putStringSet(PISTAS_VISTAS, modos.map { it.name }.toSet()) }

    override fun leerElegidasDeslizando(): Int = prefs.getInt(ELEGIDAS_DESLIZANDO, 0)
    override fun guardarElegidasDeslizando(veces: Int) = prefs.edit { putInt(ELEGIDAS_DESLIZANDO, veces) }

    override fun leerContadores(): ContadoresRapido = ContadoresRapido(
        abiertoConToque = prefs.getInt(ABIERTO_CON_TOQUE, 0),
        conGesto = porLugar(CON_GESTO),
        conToque = porLugar(CON_TOQUE),
        cerradasPronto = porLugar(CERRADAS_PRONTO),
    )

    override fun guardarContadores(contadores: ContadoresRapido) {
        prefs.edit {
            putInt(ABIERTO_CON_TOQUE, contadores.abiertoConToque)
            for (l in Lugar.entries) {
                putInt(CON_GESTO + l.name, contadores.conGesto[l] ?: 0)
                putInt(CON_TOQUE + l.name, contadores.conToque[l] ?: 0)
                putInt(CERRADAS_PRONTO + l.name, contadores.cerradasPronto[l] ?: 0)
            }
        }
    }

    private fun porLugar(prefijo: String): Map<Lugar, Int> =
        Lugar.entries.associateWith { prefs.getInt(prefijo + it.name, 0) }.filterValues { it > 0 }

    private companion object {
        const val MODO = "modo_app"
        const val ZURDO = "boton_rapido_izquierda"
        const val PISTAS_VISTAS = "pistas_vistas"
        const val ELEGIDAS_DESLIZANDO = "elegidas_deslizando"
        const val ABIERTO_CON_TOQUE = "rapido_abierto_con_toque"
        const val CON_GESTO = "rapido_con_gesto_"
        const val CON_TOQUE = "rapido_con_toque_"
        const val CERRADAS_PRONTO = "rapido_cerradas_pronto_"
    }
}
