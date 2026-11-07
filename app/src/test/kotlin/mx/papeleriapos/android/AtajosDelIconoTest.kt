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
import mx.papeleriapos.android.rapido.Accesos
import mx.papeleriapos.android.rapido.AccesoDinamico
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Los accesos fijos del ícono (res/xml/shortcuts.xml), leídos como los lee el sistema: ShortcutParser pasa cada `<intent>`
 * por `Intent.parseIntent` (ShadowShortcutManager no lee el archivo). «Revisar precio» es fijo y encendido, y abre
 * MainActivity con el mismo filtro que el ícono (acción MAIN y categoría LAUNCHER): el sistema compara el intent con el de
 * la raíz de la tarea sin los extras, y así, después de un acceso, el ícono trae la tarea en lugar de apilar otra
 * MainActivity. «Ticket» y «Conteo» de la 0.1 y la 0.2 quedan declarados apagados, con su mensaje: así se apaga lo que
 * alguien ancló (docs/propuesta-interfaz.md, «Del bolsillo al precio»). «Armar ticket» y «Contar» son dinámicos
 * ([mx.papeleriapos.android.rapido.AccesosTest]). Robolectric no tiene tareas ni lanzador: eso se revisa en el celular
 * (docs/pruebas.md, «Los accesos del ícono»).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AtajosDelIconoTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private data class Fijo(
        val id: String,
        val encendido: Boolean,
        val corto: Int,
        val largo: Int,
        val mensaje: Int,
        val icono: Int,
        var intent: Intent? = null,
    )

    private fun leer(): List<Fijo> {
        val p = ctx.resources.getXml(R.xml.shortcuts)
        val fijos = mutableListOf<Fijo>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "shortcut" -> fijos += Fijo(
                    id = p.getAttributeValue(ANDROID, "shortcutId"),
                    encendido = p.getAttributeBooleanValue(ANDROID, "enabled", true),
                    corto = p.getAttributeResourceValue(ANDROID, "shortcutShortLabel", 0),
                    largo = p.getAttributeResourceValue(ANDROID, "shortcutLongLabel", 0),
                    mensaje = p.getAttributeResourceValue(ANDROID, "shortcutDisabledMessage", 0),
                    icono = p.getAttributeResourceValue(ANDROID, "icon", 0),
                )
                // Como ShortcutParser: lee la acción, el destino, <categories> y <extra>. En un apagado lo ignoraría.
                "intent" -> fijos.last().intent = Intent.parseIntent(ctx.resources, p, Xml.asAttributeSet(p))
            }
        }
        return fijos
    }

    @Test
    fun `la principal declara los accesos fijos`() {
        val info = ctx.packageManager.getActivityInfo(ComponentName(ctx, MainActivity::class.java), PackageManager.GET_META_DATA)
        assertEquals(R.xml.shortcuts, info.metaData.getInt("android.app.shortcuts"))
    }

    @Test
    fun `revisar precio es fijo y abre la principal como el icono, en precio`() {
        val fijos = leer()
        // Los mismos id de la 0.1 y la 0.2: el «Precio» que alguien ancló sigue sirviendo, y lo de Ticket y Conteo se apaga.
        assertEquals(listOf(Accesos.PRECIO, "escanear_venta", "escanear_conteo"), fijos.map { it.id })
        val precio = fijos.first()
        assertTrue(precio.encendido)
        assertEquals("Precio" to "Revisar precio", ctx.getString(precio.corto) to ctx.getString(precio.largo))
        val intent = checkNotNull(precio.intent) { "«Revisar precio» sin intent: el sistema lo ignoraría" }
        val icono = Intent.makeMainActivity(ComponentName(ctx, MainActivity::class.java))
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals(setOf(Intent.CATEGORY_LAUNCHER), intent.categories)
        assertTrue("Otro filtro que el ícono ($intent contra $icono)", intent.filterEquals(icono))
        assertEquals(MainActivity.MODO_PRECIO, intent.getStringExtra(MainActivity.EXTRA_MODO))
        assertEquals("MainActivity lo entiende", ModoApp.PRECIO, ModoApp.deExtra(intent.getStringExtra(MainActivity.EXTRA_MODO)))
    }

    @Test
    fun `ticket y conteo de antes quedan declarados apagados con su mensaje y sin intent`() {
        val apagados = leer().drop(1)
        for (a in apagados) {
            assertFalse("${a.id}: encendido saldría en el menú de todos los celulares", a.encendido)
            assertNotEquals("${a.id}: sin etiqueta corta el sistema ignora la entrada (y lo anclado se queda como estaba)", 0, a.corto)
            assertNotEquals("${a.id}: sin mensaje", 0, a.mensaje)
            assertNotEquals("${a.id}: sin ícono", 0, a.icono)
            assertNull("${a.id}: el intent de un apagado se ignora", a.intent)
            assertEquals(
                "${a.id}: lo que dice quien lo toque, si su lanzador lo enseña",
                "Este acceso cambió. Mantén presionado el ícono para ver los de ahora.",
                ctx.getString(a.mensaje),
            )
        }
        // Con sus etiquetas de entonces: el anclado se ve igual, apagado.
        assertEquals(listOf("Ticket", "Conteo"), apagados.map { ctx.getString(it.corto) })
    }

    @Test
    fun `los dinamicos llevan id nuevos y todas las etiquetas caben`() {
        val fijos = leer()
        // Un id de shortcuts.xml es inmutable: cualquier llamada de ShortcutManager con él lanza IllegalArgumentException.
        assertTrue(AccesoDinamico.entries.none { d -> fijos.any { it.id == d.id } })
        // La corta va en el anclado (10 o menos) y la larga en el menú (25 o menos; guía «Best practices»).
        val dinamicos = AccesoDinamico.entries.map { Accesos.acceso(ctx, it) }
        val etiquetas = fijos.map { ctx.getString(it.corto) to ctx.getString(it.largo) } +
            dinamicos.map { it.shortLabel.toString() to it.longLabel.toString() }
        for ((corta, larga) in etiquetas) {
            assertTrue("«$corta» pasa de 10", corta.length <= 10)
            assertTrue("«$larga» pasa de 25", larga.length <= 25)
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
