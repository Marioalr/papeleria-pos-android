package mx.papeleriapos.android.rapido

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * El mosaico «Precio» (docs/propuesta-interfaz.md, «Del bolsillo al precio»), con ShadowTileService y ShadowTile. La clase
 * conserva su nombre de cuando decía «Escanear»: SystemUI guarda el mosaico por el paquete y la clase. En Android 7 a 9 un
 * mosaico ya agregado no toma la etiqueta nueva del manifiesto, así que también la pone `onStartListening`. `onClick` no
 * se prueba: en API 34+ lo atiende SystemUI con `activityLaunchForClick`, y su `startActivityAndCollapse(PendingIntent)` no
 * tiene shadow.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class MosaicoTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `se llama precio en el manifiesto y lo dice cada vez que se abre el panel`() {
        val pm = ctx.packageManager
        val servicio = pm.getServiceInfo(ComponentName(ctx, MosaicoEscanerService::class.java), 0)
        assertEquals("Precio", servicio.loadLabel(pm).toString())
        assertEquals(R.drawable.ic_mosaico, servicio.icon)
        val mosaico = Robolectric.buildService(MosaicoEscanerService::class.java).create().get()
        val tile = mosaico.qsTile
        // El de la prueba nace sin etiqueta; en el celular, uno agregado con la 0.2 seguiría diciendo «Escanear».
        assertNull(tile.label)
        mosaico.onStartListening()
        assertEquals("Precio", tile.label)
        assertEquals(Tile.STATE_INACTIVE, tile.state)
        // El toque (API 34+) abre el escaneo rápido.
        val pendiente = shadowOf(tile.activityLaunchForClick)
        assertTrue(pendiente.isActivity)
        assertEquals(ComponentName(ctx, EscaneoRapidoActivity::class.java), pendiente.savedIntent.component)
    }

    @Test
    fun `su icono es la etiqueta de precio del modo precio`() {
        // El de antes era un código de barras en una retícula; ahora el mismo dibujo que el modo Precio de la app
        // (Icons.Filled.Sell), también en el diálogo para agregarlo.
        val p = ctx.resources.getXml(R.drawable.ic_mosaico)
        val trazos = mutableListOf<String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "path") trazos += p.getAttributeValue(ANDROID, "pathData")
        }
        val sell = Icons.Filled.Sell.root.map { (it as VectorPath).pathData }
        assertEquals(sell, trazos.map { PathParser().parsePathString(it).toNodes() })
    }

    @Test
    fun `lo que dice del mosaico ya dice precio`() {
        assertEquals("Precio", ctx.getString(R.string.mosaico_etiqueta))
        val textos = listOf(
            COMO_AGREGAR_MOSAICO_A_MANO,
            mensajeMosaico(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED),
            mensajeMosaico(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED),
        )
        for (t in textos) {
            assertTrue(t, t.contains("«Precio»"))
            assertFalse(t, t.contains("Escanear"))
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
