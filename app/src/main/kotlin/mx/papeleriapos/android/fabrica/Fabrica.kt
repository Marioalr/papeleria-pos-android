package mx.papeleriapos.android.fabrica

import android.content.Context
import mx.papeleriapos.escaner.ConfigEscaner
import mx.papeleriapos.escaner.ControladorEscaner
import mx.papeleriapos.escaner.camara.ControladorCamara

/** Crea el controlador del escáner: el único lugar de la app que conoce la cámara real. */
fun crearControlador(context: Context, config: ConfigEscaner): ControladorEscaner =
    ControladorCamara(context.applicationContext, config)
