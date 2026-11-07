package mx.papeleriapos.android.rapido

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

/**
 * Lo que alguien ancló, con [ShadowAtajosComoAosp] (las reglas de AOSP que el shadow de serie no tiene): «Contar» y
 * «Armar ticket» anclados se apagan con su mensaje cuando ya no van y vuelven a servir cuando sí; los fijos de
 * shortcuts.xml anclados con la 0.1 o la 0.2 son inmutables y el código nunca los toca (lanzarían
 * IllegalArgumentException).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, shadows = [ShadowAtajosComoAosp::class])
class AccesosAncladosTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sombra: ShadowAtajosComoAosp = Shadow.extract(ctx.getSystemService(ShortcutManager::class.java))

    private fun menu(): List<String> = ShortcutManagerCompat.getDynamicShortcuts(ctx).sortedBy { it.rank }.map { it.id }

    private fun anclado(id: String): ShortcutInfoCompat =
        ShortcutManagerCompat.getShortcuts(ctx, ShortcutManagerCompat.FLAG_MATCH_PINNED).single { it.id == id }

    private fun anclar(a: AccesoDinamico) = assertTrue(ShortcutManagerCompat.requestPinShortcut(ctx, Accesos.acceso(ctx, a), null))

    @Test
    fun `contar sin anclar sale del menu y regresa`() {
        Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true)
        Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = false)
        assertEquals(listOf("ticket"), menu())
        assertTrue("Sin anclar, apagarlo lo borra", ShortcutManagerCompat.getShortcuts(ctx, ShortcutManagerCompat.FLAG_MATCH_PINNED).isEmpty())
        Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true)
        assertEquals(listOf("ticket", "contar"), menu())
        assertEquals(2, sombra.publicaciones)
    }

    @Test
    fun `contar anclado se apaga con su mensaje al empezar el piloto y vuelve a servir`() {
        Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true)
        anclar(AccesoDinamico.CONTAR)
        // Empieza el piloto: no se cuenta.
        Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = false)
        val apagado = anclado("contar")
        assertFalse(apagado.isEnabled)
        assertEquals("En el piloto no se cuenta", apagado.disabledMessage)
        assertEquals(ShortcutInfo.DISABLED_REASON_BY_APP, apagado.disabledReason)
        assertEquals(listOf("ticket"), menu())
        assertEquals("Apagar no necesitó publicar", 1, sombra.publicaciones)
        // Se puede contar otra vez: el anclado se enciende y regresa al menú.
        Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true)
        assertTrue(anclado("contar").isEnabled)
        assertEquals(listOf("ticket", "contar"), menu())
    }

    @Test
    fun `armar ticket anclado se apaga si el celular pasa a ser el de la tienda`() {
        Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = false)
        anclar(AccesoDinamico.TICKET)
        Accesos.sincronizar(ctx, armaTickets = false, sePuedeContar = false)
        val ticket = anclado("ticket")
        assertFalse(ticket.isEnabled)
        assertEquals("En este celular no se arman tickets", ticket.disabledMessage)
        assertTrue(menu().isEmpty())
    }

    @Test
    fun `los fijos anclados con versiones anteriores no estorban y nunca se tocan`() {
        // Como quedan en el celular al instalar esta encima: «Revisar precio» encendido e inmutable; «Ticket» y «Conteo»
        // declarados apagados, inmutables y con su mensaje.
        val viejo = "Este acceso cambió. Mantén presionado el ícono para ver los de ahora."
        sombra.anclarFijo(fijo("escanear_precio", "Precio"), apagado = false)
        sombra.anclarFijo(fijo("escanear_venta", "Ticket"), apagado = true, mensaje = viejo)
        sombra.anclarFijo(fijo("escanear_conteo", "Conteo"), apagado = true, mensaje = viejo)
        // Con cualquiera de ellos, el shadow (como AOSP) lanzaría IllegalArgumentException.
        Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true)
        Accesos.sincronizar(ctx, armaTickets = false, sePuedeContar = false)
        Accesos.sincronizar(ctx, armaTickets = true, sePuedeContar = true)
        assertEquals(listOf("ticket", "contar"), menu())
        for (id in listOf("escanear_precio", "escanear_venta", "escanear_conteo")) assertTrue(id, anclado(id).isImmutable)
        assertTrue(anclado("escanear_precio").isEnabled)
        assertFalse(anclado("escanear_conteo").isEnabled)
        assertEquals(viejo, anclado("escanear_conteo").disabledMessage)
    }

    private fun fijo(id: String, corto: String): ShortcutInfo =
        ShortcutInfo.Builder(ctx, id).setShortLabel(corto).setIntent(Intent(Intent.ACTION_VIEW)).build()
}
