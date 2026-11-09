package mx.papeleriapos.android.catalogo

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.android.caja.Conexion
import mx.papeleriapos.android.copia.Copia
import mx.papeleriapos.android.copia.ProductosDeLaCopia
import mx.papeleriapos.android.copia.hoy
import mx.papeleriapos.pos.precio.Fecha

/**
 * De dónde salen los productos que enseñan Precio, Buscar, el ticket y el escaneo rápido: de la copia del catálogo de la
 * caja ([Conexion.copia]) o, con la herramienta «Productos de prueba» encendida (Ajustes › Herramientas de medición › El
 * escáner), de los ficticios de assets/productos-prueba.csv. Sin alta y sin la herramienta, un catálogo vacío: la
 * pantalla dice que hay que conectar el celular con la caja principal.
 *
 * Los productos se arman una vez por copia, en su hilo y fuera de la apertura de la cámara; mientras, [catalogo] es
 * null y las pantallas dicen «Cargando…». Un proceso, una fuente.
 */
object FuenteDeProductos {
    private const val LLAVE_DE_PRUEBA = "productos_de_prueba"

    private val _catalogo = MutableStateFlow<CatalogoDeProductos?>(null)
    /** null mientras se arma (al abrir la app o al llegar otra copia). */
    val catalogo: StateFlow<CatalogoDeProductos?> = _catalogo.asStateFlow()

    private val _dePrueba = MutableStateFlow(false)
    /** Si Precio, Buscar y el ticket usan los productos de prueba en lugar de la copia. */
    val dePrueba: StateFlow<Boolean> = _dePrueba.asStateFlow()

    private val _dia = MutableStateFlow(hoy())

    /**
     * La app pasó al frente (o el escaneo rápido pidió los productos): si cambió el día, los productos de la copia se
     * vuelven a armar, porque las promociones que corren y su cobro dependen del día. Si no cambió, no hace nada.
     */
    fun revisarElDia(dia: Fecha = hoy()) {
        _dia.value = dia
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val hilo by lazy { Dispatchers.IO.limitedParallelism(1) }
    private val alcance by lazy { CoroutineScope(SupervisorJob() + hilo) }

    @Volatile private var iniciada = false

    /** Empieza a leer la copia y a armar los productos, sin esperar; llamarlo de más no hace nada. */
    fun iniciar(contexto: Context) {
        if (iniciada) return
        synchronized(this) {
            if (iniciada) return
            iniciada = true
        }
        val app = contexto.applicationContext
        val prefs = prefs(app)
        _dePrueba.value = prefs.getBoolean(LLAVE_DE_PRUEBA, false)
        val conexion = Conexion.del(app)
        conexion.precargar()
        alcance.launch {
            var deLaCopia: CatalogoDelDia? = null
            var dePruebaArmado: CatalogoDeProductos? = null
            combine(conexion.copia, conexion.estado.map { it.leida }, _dePrueba, _dia) { copia, leida, dePrueba, dia -> Armar(copia, leida, dePrueba, dia) }
                .collect { (copia, leida, dePrueba, dia) ->
                    _catalogo.value = when {
                        dePrueba -> dePruebaArmado ?: dePrueba(app).also { dePruebaArmado = it }
                        !leida -> null
                        copia == null -> CatalogoDeProductos.VACIO
                        else -> CatalogoDelDia.de(deLaCopia, copia, dia).also { deLaCopia = it }.catalogo
                    }
                }
        }
    }

    /** Los productos; si todavía no están, espera a que se armen (fuera del hilo principal). */
    suspend fun listo(contexto: Context): CatalogoDeProductos {
        iniciar(contexto)
        revisarElDia()
        return _catalogo.value ?: _catalogo.filterNotNull().first()
    }

    /** La herramienta: con [si], los ficticios; sin ella, la copia. Se guarda. */
    fun usarDePrueba(contexto: Context, si: Boolean) {
        iniciar(contexto)
        prefs(contexto.applicationContext).edit().putBoolean(LLAVE_DE_PRUEBA, si).apply()
        _dePrueba.value = si
    }

    /** Los productos de prueba (assets), los del prototipo. */
    fun dePrueba(contexto: Context): CatalogoDeProductos =
        CatalogoDeProductos.deCsv(contexto.applicationContext.assets.open(CatalogoDeProductos.ARCHIVO_DE_PRUEBA).bufferedReader().use { it.readText() })

    private data class Armar(val copia: Copia?, val leida: Boolean, val dePrueba: Boolean, val dia: Fecha)

    private fun prefs(app: Context): SharedPreferences = app.getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
}

/**
 * Los productos de una copia armados para un día: las promociones que corren y su cobro se deciden con el día, así que con
 * la misma copia y otro día se vuelven a armar (la app puede seguir viva de un día para otro sin que cambie la copia).
 */
internal class CatalogoDelDia private constructor(val copia: Copia, val dia: Fecha, val catalogo: CatalogoDeProductos) {
    companion object {
        fun de(previo: CatalogoDelDia?, copia: Copia, dia: Fecha, armar: (Copia, Fecha) -> CatalogoDeProductos = ProductosDeLaCopia::armar): CatalogoDelDia =
            if (previo != null && previo.copia === copia && previo.dia == dia) previo else CatalogoDelDia(copia, dia, armar(copia, dia))
    }
}
