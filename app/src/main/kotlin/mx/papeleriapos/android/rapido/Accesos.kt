package mx.papeleriapos.android.rapido

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.WorkerThread
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_DYNAMIC
import androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_PINNED
import androidx.core.graphics.drawable.IconCompat
import mx.papeleriapos.android.MainActivity
import mx.papeleriapos.android.R
import mx.papeleriapos.android.principal.ModoApp

/*
 * Los accesos del ícono (mantener presionado; docs/propuesta-interfaz.md, «Del bolsillo al precio» y «En el piloto»).
 * «Revisar precio» es fijo (res/xml/shortcuts.xml): sirve en los dos celulares, nunca se apaga y el que se ancló con la
 * 0.1 sigue sirviendo. «Armar ticket» y «Contar» son dinámicos (ShortcutManagerCompat), los únicos que se ponen y se
 * quitan mientras la app corre: «Armar ticket» solo en el celular que arma tickets (el de una persona, no el de la
 * tienda) y «Contar» solo si se puede contar (en el piloto, no). Llevan id nuevos: los de shortcuts.xml son inmutables y
 * cualquier llamada de ShortcutManager con ellos lanza IllegalArgumentException (ShortcutService.java 16:2047 y
 * siguientes), aunque estén declarados apagados.
 */

/** Un acceso dinámico: su id (nunca uno de shortcuts.xml) y el modo con que abre la app. */
enum class AccesoDinamico(val id: String, val modo: ModoApp) {
    TICKET("ticket", ModoApp.TICKET),
    CONTAR("contar", ModoApp.CONTEO),
}

/**
 * Los dinámicos que van, en el orden del menú (después de «Revisar precio», que es fijo y sale primero): «Armar ticket»
 * si este celular [armaTickets] y «Contar» si [sePuedeContar].
 */
fun accesosQueVan(armaTickets: Boolean, sePuedeContar: Boolean): List<AccesoDinamico> = buildList {
    if (armaTickets) add(AccesoDinamico.TICKET)
    if (sePuedeContar) add(AccesoDinamico.CONTAR)
}

/** Un acceso como lo devuelve el sistema, reducido a lo que se compara. */
data class AccesoPublicado(
    val id: String,
    val encendido: Boolean,
    /** Es dinámico: sale en el menú del ícono (un anclado apagado ya no). */
    val enElMenu: Boolean,
    /** El rango que guardó el sistema. Lo renumera 0, 1, 2… (ShortcutPackage.adjustRanks): solo cuenta el orden. */
    val orden: Int,
    /** Lo que se ve y lo que abre ([Accesos.firma]). */
    val firma: String,
)

/** Lo que hay que hacer con los dinámicos, en este orden: apagar, encender y, si el menú cambió, publicar. */
data class PlanDeAccesos(
    /** Los que van, en su orden. */
    val van: List<AccesoDinamico>,
    /** id → mensaje: sale del menú; si alguien lo ancló, se apaga con ese mensaje; si no, se borra. */
    val apagar: Map<String, String>,
    /** Anclados que estaban apagados y vuelven a ir. */
    val encender: List<String>,
    /** El menú cambió: hay que llamar a setDynamicShortcuts. */
    val publicar: Boolean,
)

/**
 * La regla de los accesos dinámicos, pura (sin Android). [armaTickets] y [sePuedeContar] deciden cuáles van; [hay] es lo
 * publicado, sin los fijos; [firmas], cómo se publicaría hoy cada uno; [mensajes], con qué se apaga cada uno cuando ya no
 * va. Lo que ya está apagado no se vuelve a apagar. Publica solo si el menú cambió (otro orden, otra etiqueta, otro modo u
 * otra versión), no por el rango que renumeró el sistema: en el celular de la tienda «Contar» va con rango 1 y queda
 * guardado con 0, y publicaría en cada arranque.
 */
fun planDeAccesos(
    armaTickets: Boolean,
    sePuedeContar: Boolean,
    hay: List<AccesoPublicado>,
    firmas: Map<AccesoDinamico, String>,
    mensajes: Map<AccesoDinamico, String>,
): PlanDeAccesos {
    val van = accesosQueVan(armaTickets, sePuedeContar)
    val ids = van.map { it.id }
    val apagar = AccesoDinamico.entries
        .filter { a -> a.id !in ids && hay.any { it.id == a.id && it.encendido } }
        .associate { it.id to mensajes.getValue(it) }
    val encender = ids.filter { id -> hay.any { it.id == id && !it.encendido } }
    val menu = hay.filter { it.enElMenu && it.id !in apagar }.sortedBy { it.orden }.map { it.id to it.firma }
    return PlanDeAccesos(van, apagar, encender, publicar = menu != van.map { it.id to firmas.getValue(it) })
}

/**
 * Lo que decide los accesos en este celular mientras no hay perfiles (entrega 2: el de una persona o el de la tienda) ni
 * piloto: arma tickets y se puede contar (el conteo de prueba), así que salen los dos. Sin interruptores en Ajustes:
 * cuando lleguen, los valores salen de ahí.
 */
internal object EsteCelular {
    const val ARMA_TICKETS = true
    const val SE_PUEDE_CONTAR = true
}

/** Los accesos dinámicos, con ShortcutManagerCompat. */
object Accesos {
    /** El fijo (shortcuts.xml): nunca se le pasa a ShortcutManager. */
    const val PRECIO = "escanear_precio"

    /** Subirlo cuando cambie algo que no se lee de vuelta del sistema (el ícono, las banderas del intent): así se publica. */
    const val VERSION = 1
    private const val LLAVE_VERSION = "version"

    /**
     * Las mismas banderas que el sistema les pone a los fijos (ShortcutParser.java 16:181-185), para que los tres abran
     * igual: recrean la tarea con este intent. Sin ellas un dinámico solo lleva NEW_TASK (LauncherAppsService.java
     * 16:1490): con la app abierta el sistema solo traería la tarea y el modo no cambiaría.
     */
    const val BANDERAS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME

    /** [a] como se publica, en el lugar [rango] del menú. */
    fun acceso(context: Context, a: AccesoDinamico, rango: Int = 0): ShortcutInfoCompat {
        val (corto, largo) = when (a) {
            AccesoDinamico.TICKET -> R.string.acceso_ticket_corto to R.string.acceso_ticket_largo
            AccesoDinamico.CONTAR -> R.string.acceso_contar_corto to R.string.acceso_contar_largo
        }
        return ShortcutInfoCompat.Builder(context, a.id)
            .setShortLabel(context.getString(corto))
            .setLongLabel(context.getString(largo))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_escaner))
            .setIntent(
                // MAIN + LAUNCHER, como el ícono y «Revisar precio»: después, el ícono trae esta tarea y no apila otra.
                Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
                    .putExtra(MainActivity.EXTRA_MODO, extraDe(a.modo))
                    .addFlags(BANDERAS),
            )
            .setRank(rango)
            .setExtras(PersistableBundle().apply { putInt(LLAVE_VERSION, VERSION) })
            .build()
    }

    /** Con qué se apaga cada uno cuando ya no va (lo ve quien lo ancló, si su lanzador enseña el mensaje). */
    fun mensajesAlApagar(context: Context): Map<AccesoDinamico, String> = mapOf(
        AccesoDinamico.TICKET to context.getString(R.string.acceso_ticket_apagado),
        AccesoDinamico.CONTAR to context.getString(R.string.acceso_contar_apagado),
    )

    /** Lo que se compara para saber si el menú cambió: las etiquetas, el modo y la versión (no el rango). */
    fun firma(a: ShortcutInfoCompat): String =
        "${a.shortLabel}|${a.longLabel}|${a.intents.lastOrNull()?.getStringExtra(MainActivity.EXTRA_MODO)}|" +
            "${a.extras?.getInt(LLAVE_VERSION)}"

    /**
     * Pone al día los dinámicos, en el orden que pide el sistema: primero apaga lo que ya no va (sale del menú; el anclado
     * se queda apagado con su mensaje), luego enciende lo anclado que vuelve a ir (por si lo siguiente se topa con el
     * límite) y al final, solo si el menú cambió, llama a setDynamicShortcuts. Revisar es una consulta; publicar avisa a
     * todos los lanzadores y en segundo plano cuenta para el límite de llamadas. Devuelve el plan, o null en API 24 (no hay
     * accesos: ShortcutManagerCompat no haría nada y diría que sí). Es una llamada al sistema: fuera del hilo principal.
     */
    @WorkerThread
    fun sincronizar(context: Context, armaTickets: Boolean, sePuedeContar: Boolean): PlanDeAccesos? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return null
        val hay = ShortcutManagerCompat.getShortcuts(context, FLAG_MATCH_DYNAMIC or FLAG_MATCH_PINNED)
            // Los fijos, también los de la 0.1 que alguien ancló: inmutables, no se tocan.
            .filterNot { it.isImmutable }
            // En API 25 a 29 ShortcutManagerCompat junta dinámicos y anclados: uno puede venir dos veces.
            .distinctBy { it.id }
            .map { AccesoPublicado(it.id, it.isEnabled, it.isDynamic, it.rank, firma(it)) }
        val firmas = AccesoDinamico.entries.associateWith { firma(acceso(context, it)) }
        val plan = planDeAccesos(armaTickets, sePuedeContar, hay, firmas, mensajesAlApagar(context))
        for ((id, mensaje) in plan.apagar) ShortcutManagerCompat.disableShortcuts(context, listOf(id), mensaje)
        val quiero = plan.van.mapIndexed { posicion, a -> acceso(context, a, rango = posicion) }
        if (plan.encender.isNotEmpty()) ShortcutManagerCompat.enableShortcuts(context, quiero.filter { it.id in plan.encender })
        // false: el sistema no lo aceptó (el límite); se vuelve a intentar en la próxima apertura.
        if (plan.publicar) ShortcutManagerCompat.setDynamicShortcuts(context, quiero)
        return plan
    }

    private fun extraDe(modo: ModoApp): String = when (modo) {
        ModoApp.PRECIO -> MainActivity.MODO_PRECIO
        ModoApp.TICKET -> MainActivity.MODO_TICKET
        ModoApp.CONTEO -> MainActivity.MODO_CONTEO
    }
}
