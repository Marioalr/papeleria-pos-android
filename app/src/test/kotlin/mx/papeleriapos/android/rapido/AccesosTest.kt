package mx.papeleriapos.android.rapido

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.papeleriapos.android.MainActivity
import mx.papeleriapos.android.principal.ModoApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Los accesos dinámicos que se publican ([Accesos.sincronizar]) con el ShadowShortcutManager de serie, que sirve para lo
 * publicado: los id, las etiquetas y el intent con sus extras y sus banderas. Lo anclado va en [AccesosAncladosTest].
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AccesosTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** El menú del ícono (lo dinámico), en su orden. */
    private fun menu(): List<ShortcutInfoCompat> = ShortcutManagerCompat.getDynamicShortcuts(ctx).sortedBy { it.rank }

    @Test
    fun `armar ticket y contar abren la principal como el icono, en su modo y con las banderas de los fijos`() {
        val plan = Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true)
        assertTrue(plan!!.publicar)
        val accesos = menu()
        assertEquals(listOf("ticket", "contar"), accesos.map { it.id })
        assertEquals(listOf("Ticket" to "Armar ticket", "Contar" to "Contar"), accesos.map { it.shortLabel.toString() to it.longLabel.toString() })
        val icono = Intent.makeMainActivity(ComponentName(ctx, MainActivity::class.java))
        val fijos = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
        for ((acceso, modo) in accesos.zip(listOf(ModoApp.TICKET, ModoApp.CONTEO))) {
            val intent = acceso.intent
            assertEquals(Intent.ACTION_MAIN, intent.action)
            assertEquals(setOf(Intent.CATEGORY_LAUNCHER), intent.categories)
            // El mismo filtro que el ícono (lo que compara el sistema, sin los extras): después, el ícono trae esta tarea.
            assertTrue("${acceso.id}: otro filtro que el ícono", intent.filterEquals(icono))
            assertEquals("MainActivity lo entiende", modo, ModoApp.deExtra(intent.getStringExtra(MainActivity.EXTRA_MODO)))
            // Sin ellas solo llevaría NEW_TASK: con la app abierta, el sistema traería la tarea sin cambiar el modo.
            assertEquals("${acceso.id}: banderas 0x${Integer.toHexString(intent.flags)}", fijos, intent.flags and fijos)
            assertEquals(Accesos.VERSION, acceso.extras!!.getInt("version"))
        }
    }

    @Test
    fun `el de la tienda en el piloto no publica ninguno`() {
        val plan = Accesos.sincronizar(ctx, armaTickets = false, sePuedeContar = false)
        assertFalse(plan!!.publicar)
        assertTrue(menu().isEmpty())
    }

    @Test
    fun `revisar otra vez lo mismo no vuelve a publicar`() {
        Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true)
        // Lo que devuelve el sistema (etiquetas, modo y versión) se lee igual que lo que se publicó.
        val otraVez = Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true)
        assertFalse("Cada publicación avisa a todos los lanzadores", otraVez!!.publicar)
        assertTrue(otraVez.apagar.isEmpty() && otraVez.encender.isEmpty())
    }

    @Test
    fun `en api 24 no hay accesos y no truena`() {
        val sdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.N)
        try {
            // ShortcutManagerCompat no haría nada y diría que sí: ni se le llama.
            assertNull(Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true))
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        }
        assertTrue(ctx.getSystemService(ShortcutManager::class.java).dynamicShortcuts.isEmpty())
        assertNotNull("En API 25 o más sí", Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true))
    }
}
