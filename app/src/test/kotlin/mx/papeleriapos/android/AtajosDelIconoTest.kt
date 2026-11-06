package mx.papeleriapos.android

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.principal.ModoApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Los atajos del ícono (res/xml/shortcuts.xml), leídos como los lee el sistema: ShortcutParser pasa cada `<intent>` por
 * `Intent.parseIntent` (ShadowShortcutManager no lee el archivo). Abren MainActivity con el mismo filtro que el ícono
 * (acción MAIN y categoría LAUNCHER): el sistema compara el intent con el de la raíz de la tarea sin los extras, y así,
 * después de un atajo, el ícono trae la tarea en lugar de apilar otra MainActivity. Robolectric no tiene tareas: eso se
 * revisa en el celular (docs/pruebas.md, «Los atajos del ícono»).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AtajosDelIconoTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private data class Atajo(val id: String, val encendido: Boolean, val corto: String, val largo: String, var intent: Intent? = null)

    private fun leer(): List<Atajo> {
        val p = ctx.resources.getXml(R.xml.shortcuts)
        val atajos = mutableListOf<Atajo>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "shortcut" -> atajos += Atajo(
                    id = p.getAttributeValue(ANDROID, "shortcutId"),
                    encendido = p.getAttributeBooleanValue(ANDROID, "enabled", true),
                    corto = ctx.getString(p.getAttributeResourceValue(ANDROID, "shortcutShortLabel", 0)),
                    largo = ctx.getString(p.getAttributeResourceValue(ANDROID, "shortcutLongLabel", 0)),
                )
                // Como ShortcutParser: lee la acción, el destino, <categories> y <extra>.
                "intent" -> atajos.last().intent = Intent.parseIntent(ctx.resources, p, Xml.asAttributeSet(p))
            }
        }
        return atajos
    }

    @Test
    fun `la principal declara los atajos`() {
        val info = ctx.packageManager.getActivityInfo(ComponentName(ctx, MainActivity::class.java), PackageManager.GET_META_DATA)
        assertEquals(R.xml.shortcuts, info.metaData.getInt("android.app.shortcuts"))
    }

    @Test
    fun `cada atajo abre la principal como el icono, con su modo`() {
        val atajos = leer()
        // Los mismos de la 0.1 y la 0.2, encendidos y con sus etiquetas: el id del ticket sigue siendo el de «venta».
        assertEquals(listOf("escanear_precio", "escanear_venta", "escanear_conteo"), atajos.map { it.id })
        assertTrue(atajos.all { it.encendido })
        assertEquals(
            listOf("Precio" to "Ver un precio", "Ticket" to "Armar un ticket", "Conteo" to "Contar"),
            atajos.map { it.corto to it.largo },
        )
        val icono = Intent.makeMainActivity(ComponentName(ctx, MainActivity::class.java))
        for ((atajo, modo) in atajos.zip(listOf(ModoApp.PRECIO, ModoApp.TICKET, ModoApp.CONTEO))) {
            val intent = checkNotNull(atajo.intent) { "${atajo.id} sin intent" }
            assertEquals(Intent.ACTION_MAIN, intent.action)
            assertEquals(setOf(Intent.CATEGORY_LAUNCHER), intent.categories)
            assertTrue("${atajo.id}: otro filtro que el ícono ($intent contra $icono)", intent.filterEquals(icono))
            assertEquals(modo.name.lowercase(), intent.getStringExtra(MainActivity.EXTRA_MODO))
            assertEquals("MainActivity lo entiende", modo, ModoApp.deExtra(intent.getStringExtra(MainActivity.EXTRA_MODO)))
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
