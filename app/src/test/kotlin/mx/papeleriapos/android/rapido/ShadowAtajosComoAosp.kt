package mx.papeleriapos.android.rapido

import android.content.IntentSender
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * ShadowShortcutManager más las reglas de AOSP que le faltan para lo anclado (ShortcutPackage.java y ShortcutService.java
 * de android-15.0.0_r1; el memo de la entrega 3, «Robolectric 4.17: lo que puede y lo que no»). Es un modelo nuestro: si
 * AOSP cambia, no se entera. No modela los límites ni el freno de llamadas, ni la renumeración de los rangos.
 *
 * - `disableShortcuts`: el anclado queda apagado CON su mensaje (`FLAG_DISABLED`, `DISABLED_REASON_BY_APP`) y deja de ser
 *   dinámico; el que no está anclado se borra (`deleteOrDisableWithId`). El de serie tira el mensaje y no lo marca.
 * - `set`/`addDynamicShortcuts`: el anclado sigue anclado y, si estaba apagado, vuelve encendido
 *   (`addOrReplaceDynamicShortcut`). El de serie no lo enciende.
 * - `requestPinShortcut`: el anclado lleva `FLAG_PINNED` (el de serie guarda una copia sin ella).
 * - Los inmutables (los fijos de shortcuts.xml, también los que se anclaron con otra versión) lanzan
 *   IllegalArgumentException en set/add/update/disable/enable (`ensureNotImmutable`). El de serie no los conoce.
 *
 * Se activa con `@Config(shadows = [ShadowAtajosComoAosp::class])`.
 */
@Implements(value = ShortcutManager::class, minSdk = Build.VERSION_CODES.N_MR1)
class ShadowAtajosComoAosp : ShadowShortcutManager() {
    /** Cuántas veces se llamó a setDynamicShortcuts: publicar avisa a todos los lanzadores y en segundo plano cuenta. */
    var publicaciones = 0
        private set

    private fun anclado(id: String): ShortcutInfo? = pinnedShortcuts.firstOrNull { it.id == id }

    private fun revisarInmutables(ids: Collection<String>) {
        val todos = manifestShortcuts + super.getDynamicShortcuts() + pinnedShortcuts
        val inmutable = todos.firstOrNull { it.id in ids && it.isImmutable } ?: return
        throw IllegalArgumentException("Manifest shortcut ID=${inmutable.id} may not be manipulated via APIs")
    }

    @Implementation
    override fun setDynamicShortcuts(shortcutInfoList: List<ShortcutInfo>): Boolean {
        publicaciones++
        revisarInmutables(shortcutInfoList.map { it.id })
        pinnedShortcuts.forEach { banderas(it, quitar = FLAG_DYNAMIC) }
        // Limpia los dinámicos y llama a addDynamicShortcuts.
        return super.setDynamicShortcuts(shortcutInfoList)
    }

    @Implementation
    override fun addDynamicShortcuts(shortcutInfoList: List<ShortcutInfo>): Boolean {
        revisarInmutables(shortcutInfoList.map { it.id })
        val encender = mutableListOf<String>()
        for (nuevo in shortcutInfoList) {
            require(nuevo.isEnabled) { "add/setDynamicShortcuts() cannot publish disabled shortcuts" }
            val viejo = anclado(nuevo.id) ?: continue
            banderas(nuevo, poner = FLAG_PINNED)
            if (!viejo.isEnabled) encender += nuevo.id
        }
        val ok = super.addDynamicShortcuts(shortcutInfoList)
        // Del mapa de los apagados al de los anclados.
        if (encender.isNotEmpty()) super.enableShortcuts(encender)
        return ok
    }

    @Implementation
    override fun getDynamicShortcuts(): List<ShortcutInfo> =
        (super.getDynamicShortcuts() + pinnedShortcuts.filter { it.isDynamic }).distinctBy { it.id }

    @Implementation
    override fun updateShortcuts(shortcutInfoList: List<ShortcutInfo>): Boolean {
        revisarInmutables(shortcutInfoList.map { it.id })
        return super.updateShortcuts(shortcutInfoList)
    }

    @Implementation(minSdk = Build.VERSION_CODES.O)
    override fun requestPinShortcut(shortcut: ShortcutInfo, resultIntent: IntentSender?): Boolean {
        val ok = super.requestPinShortcut(shortcut, resultIntent)
        anclado(shortcut.id)?.let { banderas(it, poner = FLAG_PINNED) }
        return ok
    }

    @Implementation
    override fun disableShortcuts(shortcutIds: List<String>, unused: CharSequence?) {
        revisarInmutables(shortcutIds)
        super.removeDynamicShortcuts(shortcutIds.filter { anclado(it) == null })
        super.disableShortcuts(shortcutIds, unused)
        for (id in shortcutIds) {
            val si = anclado(id) ?: continue
            banderas(si, poner = FLAG_DISABLED, quitar = FLAG_DYNAMIC or FLAG_MANIFEST)
            mensaje(si, unused?.toString(), ShortcutInfo.DISABLED_REASON_BY_APP)
        }
    }

    @Implementation
    override fun enableShortcuts(shortcutIds: List<String>) {
        revisarInmutables(shortcutIds)
        super.enableShortcuts(shortcutIds)
        for (id in shortcutIds) {
            val si = anclado(id) ?: continue
            banderas(si, quitar = FLAG_DISABLED)
            mensaje(si, null, ShortcutInfo.DISABLED_REASON_NOT_DISABLED)
        }
    }

    /**
     * Para la prueba: un acceso fijo que alguien ancló con otra versión, como queda en el celular al instalar esta encima
     * (inmutable; con [apagado], como los declarados con `enabled="false"`: apagado con razón `BY_APP` y su mensaje).
     */
    fun anclarFijo(fijo: ShortcutInfo, apagado: Boolean, mensaje: String? = null) {
        banderas(fijo, poner = FLAG_IMMUTABLE or (if (apagado) 0 else FLAG_MANIFEST))
        super.requestPinShortcut(fijo, null)
        if (apagado) {
            super.disableShortcuts(listOf(fijo.id), null)
            banderas(fijo, poner = FLAG_DISABLED)
            mensaje(fijo, mensaje, ShortcutInfo.DISABLED_REASON_BY_APP)
        }
    }

    private companion object {
        // Banderas @hide de ShortcutInfo (ShortcutInfo.java:86-110 de AOSP 15).
        const val FLAG_DYNAMIC = 1 shl 0
        const val FLAG_PINNED = 1 shl 1
        const val FLAG_MANIFEST = 1 shl 5
        const val FLAG_DISABLED = 1 shl 6
        const val FLAG_IMMUTABLE = 1 shl 8

        fun banderas(si: ShortcutInfo, poner: Int = 0, quitar: Int = 0) {
            if (poner != 0) ReflectionHelpers.callInstanceMethod<Any?>(si, "addFlags", ClassParameter.from(Int::class.javaPrimitiveType, poner))
            if (quitar != 0) ReflectionHelpers.callInstanceMethod<Any?>(si, "clearFlags", ClassParameter.from(Int::class.javaPrimitiveType, quitar))
        }

        fun mensaje(si: ShortcutInfo, texto: String?, razon: Int) {
            ReflectionHelpers.callInstanceMethod<Any?>(si, "setDisabledMessage", ClassParameter.from(String::class.java, texto))
            ReflectionHelpers.callInstanceMethod<Any?>(si, "setDisabledReason", ClassParameter.from(Int::class.javaPrimitiveType, razon))
        }
    }
}
