package mx.papeleriapos.android

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraXConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.view.doOnAttach
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mx.papeleriapos.android.ajustes.PreferenciasEscaner
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.MarcasApertura
import mx.papeleriapos.escaner.OrigenProceso
import mx.papeleriapos.escaner.PasoApertura
import mx.papeleriapos.escaner.TipoArranque
import mx.papeleriapos.escaner.camara.configuracionCameraX
import mx.papeleriapos.escaner.camara.precargarMemoriaApertura
import mx.papeleriapos.escaner.origenDeRazon
import mx.papeleriapos.escaner.tipoDeArranque

/**
 * Configura CameraX para abrir solo la cámara trasera (enumerar todas tarda en algunos equipos) y empieza a
 * inicializarla desde que arranca el proceso, para que la pantalla del escáner la encuentre lista (docs/escaner.md,
 * «Arranque»). También abre la apertura en frío (las marcas del proceso y de la Application) y empieza a leer en otro
 * hilo las preferencias que el `onCreate` de la Activity va a necesitar.
 *
 * Nada pesado aquí: con el mosaico de Ajustes rápidos el sistema arranca el proceso cada vez que se abre el panel.
 */
class EscanerApp : Application(), CameraXConfig.Provider {
    override fun onCreate() {
        val ahora = SystemClock.elapsedRealtimeNanos()
        MarcasApertura.nueva(TipoArranque.FRIO)
        // Process.getStartElapsedRealtime: ms, al empezar handleBindApplication (no incluye del toque al fork).
        MarcasApertura.marcar(PasoApertura.PROCESO, Process.getStartElapsedRealtime() * NANOS_POR_MS)
        MarcasApertura.marcar(PasoApertura.APLICACION, ahora)
        super.onCreate()
        // getSharedPreferences empieza a leer el archivo en otro hilo; la Activity lo lee después sin esperar al disco.
        getSharedPreferences(PreferenciasEscaner.ARCHIVO, Context.MODE_PRIVATE)
        precargarMemoriaApertura(this)
        // Solo calienta: el futuro lo recoge el controlador cuando enlaza la cámara.
        ProcessCameraProvider.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) Arranque.consultarOrigenDelProceso(this)
    }

    override fun getCameraXConfig(): CameraXConfig = configuracionCameraX()
}

private const val NANOS_POR_MS = 1_000_000L

/** Los registros de arranque que se piden: el del nacimiento del proceso es de los más recientes. */
private const val REGISTROS_ARRANQUE = 4

/**
 * Las marcas de la apertura que pone la app (docs/escaner.md, «Apertura»): el tipo de arranque y la Activity, el primer
 * cuadro de la interfaz, y el TTFD (`reportFullyDrawn`) = el primer cuadro analizado, que es cuando el personal ya puede
 * escanear. Las de la cámara las pone el controlador.
 */
internal object Arranque {
    private var actividadesCreadas = 0

    /** Por qué nació el proceso según el sistema (API 35+); lo escribe [consultarOrigenDelProceso] en otro hilo. */
    @Volatile private var origen = OrigenProceso.NO_SE

    /**
     * API 35+: pregunta al registro de arranques del sistema (`ApplicationStartInfo`) por qué nació este proceso, en un
     * hilo aparte para no poner una llamada al sistema en el camino de la apertura. Se llama en `Application.onCreate`,
     * cuando el único registro con este pid es el del nacimiento del proceso (los de Activities en un proceso que ya
     * vivía llegan después, y su tipo solo está garantizado tras el primer cuadro). Si no responde antes de la Activity,
     * [alCrearActividad] decide por el tiempo.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun consultarOrigenDelProceso(contexto: Context) {
        val am = contexto.getSystemService(ActivityManager::class.java) ?: return
        val pid = Process.myPid()
        Thread({
            try {
                val registro = am.getHistoricalProcessStartReasons(REGISTROS_ARRANQUE).firstOrNull { it.pid == pid }
                if (registro != null) origen = origenDeRazon(registro.reason)
            } catch (e: RuntimeException) {
                Log.w("Escaner", "No se pudo leer el registro de arranques", e)
            }
        }, "escaner-arranque").start()
    }

    /**
     * Lo llama un punto de entrada que no es Activity (el mosaico de Ajustes rápidos, que el sistema enlaza al abrir el
     * panel): si todavía no se creó ninguna Activity, el proceso no nació para una, y la que llegue después es tibia. Sin
     * esto, en API < 35 un toque al mosaico 1–5 s después de abrir el panel contaría como frío y el tiempo incluiría lo
     * que tardó el dedo.
     */
    fun procesoNoNacioParaActividad() {
        if (actividadesCreadas == 0) origen = OrigenProceso.OTRO
    }

    /**
     * Al principio del `onCreate` de una Activity del escáner: frío si es la primera del proceso y el proceso nació para
     * ella (API 35+: lo dice el sistema; antes: menos de 5 s desde `Application.onCreate`), si no tibio. El mosaico de
     * Ajustes rápidos arranca el proceso al abrir el panel, antes del toque: eso es tibio.
     */
    fun alCrearActividad(): TipoArranque {
        val ahora = SystemClock.elapsedRealtimeNanos()
        val primera = actividadesCreadas++ == 0
        val aplicacion = MarcasApertura.apertura.marcas[PasoApertura.APLICACION] ?: 0L
        val tipo = tipoDeArranque(primera, origen, aplicacion, ahora)
        if (tipo == TipoArranque.FRIO) MarcasApertura.tipo(tipo) else MarcasApertura.nueva(tipo)
        MarcasApertura.marcar(PasoApertura.ACTIVIDAD, ahora)
        return tipo
    }

    /**
     * En `onStart` después de un `onStop` (la misma Activity vuelve a verse: el celular se encendió o volvió de otra app):
     * apertura en caliente. La marca de la Activity es el momento en que volvió a verse.
     */
    fun alVolver(vista: View) {
        MarcasApertura.nueva(TipoArranque.CALIENTE)
        MarcasApertura.marcar(PasoApertura.ACTIVIDAD)
        marcarPrimerCuadroUi(vista)
    }

    /** [PasoApertura.PRIMER_CUADRO_UI] con el primer dibujo de la ventana de [vista] (la `decorView`). */
    fun marcarPrimerCuadroUi(vista: View) {
        vista.doOnAttach { v ->
            v.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
                private var hecho = false
                override fun onDraw() {
                    if (hecho) return
                    hecho = true
                    MarcasApertura.marcar(PasoApertura.PRIMER_CUADRO_UI)
                    // No se puede quitar dentro de onDraw.
                    v.post { v.viewTreeObserver.removeOnDrawListener(this) }
                }
            })
        }
    }

    /**
     * TTFD = el primer cuadro analizado: `addReporter` ahora y `removeReporter` cuando el controlador marca
     * [PasoApertura.PRIMER_ANALISIS] (en el hilo principal). Antes, en API 35+, las marcas propias van al registro de
     * arranques del sistema (`ApplicationStartInfo`), que descarta las que llegan después de `reportFullyDrawn`.
     * `reportFullyDrawn` vale una vez por Activity y no reporta si la Activity se pausó (p. ej. el diálogo del permiso).
     * Solo cuenta el primer análisis de la apertura de esta Activity (la que empezó [alCrearActividad] justo antes): si
     * otra apertura la reemplazó (otra Activity, o volvió de segundo plano sin haber escaneado), no se reporta.
     */
    fun reportarAlPrimerAnalisis(actividad: ComponentActivity, controlador: ControladorEscaner) {
        val reportero = actividad.fullyDrawnReporter
        val propia = MarcasApertura.numero
        reportero.addReporter()
        actividad.lifecycleScope.launch {
            // El estado se publica justo al poner la marca: sirve de aviso.
            // Si otra apertura reemplaza a la propia antes del primer análisis, se deja de esperar (sin reportar).
            controlador.estado.first {
                MarcasApertura.tieneEn(propia, PasoApertura.PRIMER_ANALISIS) || !MarcasApertura.esActual(propia)
            }
            if (!MarcasApertura.tieneEn(propia, PasoApertura.PRIMER_ANALISIS)) return@launch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) marcasAlSistema(actividad)
            reportero.removeReporter()
        }
    }

    /** Las marcas de la cámara en el registro de arranques (claves 21–30, reloj monotónico). */
    private fun marcasAlSistema(actividad: ComponentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        try {
            val am = actividad.getSystemService(ActivityManager::class.java) ?: return
            // elapsedRealtime incluye el sueño profundo; el monotónico (System.nanoTime) no: se convierten con el desfase.
            val desfase = SystemClock.elapsedRealtimeNanos() - System.nanoTime()
            val marcas = MarcasApertura.apertura.marcas
            for ((paso, clave) in CLAVES_INICIO) {
                val t = marcas[paso] ?: continue
                am.addStartInfoTimestamp(clave, t - desfase)
            }
        } catch (e: RuntimeException) {
            Log.w("Escaner", "No se pudieron agregar las marcas al registro de arranques", e)
        }
    }

    /** Las claves de `ApplicationStartInfo.getStartupTimestamps()` para leerlas con `dumpsys` o desde la app. */
    val CLAVES_INICIO = listOf(
        PasoApertura.ENLACE_INICIO to 21,
        PasoApertura.ENLACE_FIN to 22,
        PasoApertura.CAMARA_ABIERTA to 23,
        PasoApertura.PRIMER_CUADRO_SENSOR to 24,
        PasoApertura.PRIMER_ANALISIS to 25,
    )
}
