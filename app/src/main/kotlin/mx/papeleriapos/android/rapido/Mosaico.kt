package mx.papeleriapos.android.rapido

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.window.SplashScreen
import androidx.core.content.ContextCompat
import mx.papeleriapos.android.Arranque
import mx.papeleriapos.android.R

/**
 * El mosaico «Escanear» de Ajustes rápidos: abre [EscaneoRapidoActivity], también con el celular bloqueado.
 *
 * Modo NO activo a propósito (sin `ACTIVE_TILE`): el sistema enlaza el servicio cada vez que se abre el panel, lo que
 * arranca el proceso antes del toque (precalienta CameraX). En API 34+ el toque lo atiende SystemUI directo con
 * `activityLaunchForClick` (sin llamar a [onClick]); antes, [onClick] lanza la Activity.
 */
class MosaicoEscanerService : TileService() {
    override fun onCreate() {
        super.onCreate()
        // Si el panel arrancó el proceso, la Activity que llegue con el toque es tibia, no fría.
        Arranque.procesoNoNacioParaActividad()
    }

    override fun onStartListening() {
        super.onStartListening()
        val mosaico = qsTile ?: return
        mosaico.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mosaico.activityLaunchForClick = pendienteEscaneoRapido(this)
        }
        mosaico.updateTile()
    }

    // Lint marca la sobrecarga con Intent aunque esté dentro del if de versión: solo corre en API 24–33.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pendienteEscaneoRapido(this))
        } else {
            // Con targetSdk 34+ esta sobrecarga lanza UnsupportedOperationException en API 34+; aquí es API 24–33.
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intentEscaneoRapido(this))
        }
    }
}

/** Intent explícito al escaneo rápido (NEW_TASK: lo exige el arranque desde un servicio). */
fun intentEscaneoRapido(context: Context): Intent =
    Intent(context, EscaneoRapidoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * PendingIntent inmutable al escaneo rápido; lo envía SystemUI con su permiso para arrancar Activities. En API 33+ pide
 * la pantalla de inicio de color sólido (negro, sin ícono) cuando el proceso o la Activity no existían.
 */
fun pendienteEscaneoRapido(context: Context): PendingIntent {
    val opciones: Bundle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ActivityOptions.makeBasic().setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR).toBundle()
    } else {
        null
    }
    return PendingIntent.getActivity(
        context, CODIGO_MOSAICO, intentEscaneoRapido(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, opciones,
    )
}

private const val CODIGO_MOSAICO = 1

/** Se puede pedir agregar el mosaico desde la app (API 33+); antes, el usuario lo agrega editando el panel. */
fun puedePedirMosaico(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Muestra el diálogo del sistema para agregar el mosaico «Escanear» a Ajustes rápidos (API 33+). La app tiene que estar
 * en primer plano: llamarlo desde un botón. [alTerminar] recibe `StatusBarManager.TILE_ADD_REQUEST_RESULT_*` o un
 * `TILE_ADD_REQUEST_ERROR_*`. Devuelve false si el celular no lo permite (API < 33): entonces hay que explicar cómo
 * agregarlo a mano (bajar el panel, editar y arrastrar «Escanear» a los primeros lugares).
 */
fun pedirMosaico(context: Context, alTerminar: (Int) -> Unit = {}): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val barra = context.getSystemService(StatusBarManager::class.java) ?: return false
    return try {
        barra.requestAddTileService(
            ComponentName(context, MosaicoEscanerService::class.java),
            context.getString(R.string.mosaico_etiqueta),
            Icon.createWithResource(context, R.drawable.ic_mosaico),
            ContextCompat.getMainExecutor(context),
        ) { resultado -> alTerminar(resultado) }
        true
    } catch (e: RuntimeException) {
        Log.w("Escaner", "No se pudo pedir el mosaico", e)
        false
    }
}

/** Cómo agregarlo a mano (Android 7 a 12, o si el sistema no dejó pedirlo). */
const val COMO_AGREGAR_MOSAICO_A_MANO =
    "Para agregarlo a mano: desliza dos veces desde arriba, toca el lápiz (editar) y arrastra «Escanear» a los primeros lugares."

/** Qué decirle al usuario según lo que contestó el sistema al pedir el mosaico (`TILE_ADD_REQUEST_*`). */
@SuppressLint("InlinedApi") // Constantes: se copian al compilar; solo se usan con lo que devolvió [pedirMosaico] (API 33+).
fun mensajeMosaico(resultado: Int): String = when (resultado) {
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Listo: «Escanear» ya está en Ajustes rápidos."
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "«Escanear» ya estaba en Ajustes rápidos."
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "No se agregó. Puedes volver a pedirlo. $COMO_AGREGAR_MOSAICO_A_MANO"
    else -> "El sistema no dejó agregarlo (código $resultado). $COMO_AGREGAR_MOSAICO_A_MANO"
}

/** El resultado de [pedirMosaico] dice que el mosaico ya está en el panel. */
fun mosaicoAgregado(resultado: Int): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (
        resultado == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
            resultado == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
        )
