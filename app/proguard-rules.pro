# La fábrica crea el controlador de la cámara por nombre para que la app compile sin él (fabrica/Fabrica.kt).
-keep class mx.papeleriapos.escaner.camara.ControladorCamara {
    public <init>(android.content.Context, mx.papeleriapos.escaner.ConfigEscaner);
}
