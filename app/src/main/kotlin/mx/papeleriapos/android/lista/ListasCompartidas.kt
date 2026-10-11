package mx.papeleriapos.android.lista

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.papeleriapos.android.archivo.escribirAtomico
import mx.papeleriapos.escaner.nucleo.Modo
import java.io.File
import java.io.IOException

/**
 * Las listas de venta y conteo. Las de la app ([delProceso]) se guardan en disco: lo leído se queda hasta que el empleado
 * toca «Nueva venta» (o «Nuevo conteo»), aunque cierre la app, la abra con un atajo (que recrea la tarea), use el escaneo
 * rápido o Android mate el proceso. Así el comportamiento es uno solo, no «a veces reaparece».
 *
 * Se lee en segundo plano al crearse (la apertura no espera al disco); lo que se escanee antes de que termine queda
 * arriba de lo guardado ([ListaLecturas.unir]). Cada cambio se escribe en un hilo propio, y si llegan varios seguidos
 * basta con el último.
 */
class ListasCompartidas internal constructor(private val almacen: AlmacenListas?) {
    /** Sin disco (las pruebas). */
    constructor() : this(null)

    val venta = MutableStateFlow(ListaLecturas())
    val conteo = MutableStateFlow(ListaLecturas())

    /** Ya se juntó lo guardado en disco con lo de este proceso (para las pruebas). */
    @Volatile var cargadas: Boolean = almacen == null
        private set

    fun de(modo: Modo): MutableStateFlow<ListaLecturas> = if (modo == Modo.VENTA) venta else conteo

    private fun empezar() {
        val a = almacen ?: return
        alcance.launch {
            for ((i, modo) in Modo.entries.withIndex()) {
                val flujo = de(modo)
                val guardada = a.leer(modo)
                flujo.update { actual -> ListaLecturas.unir(guardada, actual) }
                // Desde aquí cada cambio se guarda; lo que ya está en disco no se vuelve a escribir.
                var escrita: ListaLecturas? = guardada.takeIf { flujo.value === it }
                launch {
                    flujo.collect { l ->
                        if (l !== escrita) {
                            a.guardar(modo, l)
                            escrita = l
                        }
                    }
                }
                if (i == Modo.entries.lastIndex) cargadas = true
            }
        }
    }

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

        @Volatile private var proceso: ListasCompartidas? = null

        /** Las de la app: una por proceso, guardadas en `files/listas`. */
        fun delProceso(contexto: Context): ListasCompartidas = proceso ?: synchronized(this) {
            // La carpeta se pide ya en el hilo del disco (filesDir revisa que exista).
            val app = contexto.applicationContext
            proceso ?: ListasCompartidas(AlmacenListas { File(app.filesDir, "listas") })
                .also {
                    proceso = it
                    it.empezar()
                }
        }
    }
}

/** Una lista por modo en `venta.csv` y `conteo.csv`, escritas con renombre atómico. Fuera del hilo principal. */
internal class AlmacenListas(obtenerCarpeta: () -> File) {
    private val carpeta: File by lazy(obtenerCarpeta)

    fun leer(modo: Modo): ListaLecturas = try {
        val f = archivo(modo)
        if (f.exists()) ListaLecturas.deTexto(f.readText()) else ListaLecturas()
    } catch (e: IOException) {
        Log.w(ETIQUETA, "No se pudo leer la lista de ${modo.name}", e)
        ListaLecturas()
    }

    fun guardar(modo: Modo, lista: ListaLecturas) {
        try {
            carpeta.mkdirs()
            archivo(modo).escribirAtomico(lista.aTexto())
        } catch (e: IOException) {
            Log.w(ETIQUETA, "No se pudo guardar la lista de ${modo.name}", e)
        }
    }

    private fun archivo(modo: Modo) = File(carpeta, if (modo == Modo.VENTA) "venta.csv" else "conteo.csv")

    private companion object {
        const val ETIQUETA = "Listas"
    }
}
