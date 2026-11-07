package mx.papeleriapos.android.rapido

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La regla de los accesos dinámicos del ícono, sin Android (docs/propuesta-interfaz.md, «Del bolsillo al precio» y «En
 * el piloto»): «Armar ticket» solo en el celular que arma tickets (el de una persona; el de la tienda no) y «Contar» solo
 * si se puede contar (en el piloto, no). Lo que ya no va se apaga con su mensaje, lo anclado que vuelve se enciende, y
 * solo se publica si el menú cambió: el sistema renumera los rangos, así que se compara el orden.
 */
class PlanDeAccesosTest {
    private val firmas = mapOf(AccesoDinamico.TICKET to "f-ticket", AccesoDinamico.CONTAR to "f-contar")
    private val mensajes = mapOf(
        AccesoDinamico.TICKET to "En este celular no se arman tickets",
        AccesoDinamico.CONTAR to "En el piloto no se cuenta",
    )

    private fun plan(armaTickets: Boolean, sePuedeContar: Boolean, vararg hay: AccesoPublicado) =
        planDeAccesos(armaTickets, sePuedeContar, hay.toList(), firmas, mensajes)

    /** En el menú (dinámico), con el rango que guardó el sistema. */
    private fun enElMenu(id: String, orden: Int, firma: String = "f-$id") =
        AccesoPublicado(id, encendido = true, enElMenu = true, orden = orden, firma = firma)

    /** Anclado y apagado: ya no sale en el menú. */
    private fun ancladoApagado(id: String) = AccesoPublicado(id, encendido = false, enElMenu = false, orden = 0, firma = "f-$id")

    @Test
    fun `cuales van segun lo que hace el celular`() {
        assertEquals(listOf(AccesoDinamico.TICKET, AccesoDinamico.CONTAR), accesosQueVan(armaTickets = true, sePuedeContar = true))
        assertEquals("El de una persona en el piloto", listOf(AccesoDinamico.TICKET), accesosQueVan(true, sePuedeContar = false))
        assertEquals("El de la tienda, cuando ya se cuente", listOf(AccesoDinamico.CONTAR), accesosQueVan(false, sePuedeContar = true))
        assertEquals("El de la tienda en el piloto: solo «Revisar precio», que es fijo", emptyList<AccesoDinamico>(), accesosQueVan(false, false))
        assertEquals("Abren en su modo", listOf("ticket", "contar"), AccesoDinamico.entries.map { it.id })
    }

    @Test
    fun `hoy este celular arma tickets y se puede contar`() {
        // Sin perfiles (entrega 2) ni piloto todavía: salen los dos.
        assertTrue(EsteCelular.ARMA_TICKETS)
        assertTrue(EsteCelular.SE_PUEDE_CONTAR)
    }

    @Test
    fun `sin nada publicado y nada que vaya no hace nada`() {
        val p = plan(armaTickets = false, sePuedeContar = false)
        assertTrue(p.van.isEmpty())
        assertTrue(p.apagar.isEmpty())
        assertTrue(p.encender.isEmpty())
        assertFalse(p.publicar)
    }

    @Test
    fun `la primera vez publica los dos en orden`() {
        val p = plan(armaTickets = true, sePuedeContar = true)
        assertEquals(listOf(AccesoDinamico.TICKET, AccesoDinamico.CONTAR), p.van)
        assertTrue(p.publicar)
    }

    @Test
    fun `igual que lo publicado no vuelve a publicar`() {
        assertFalse(plan(true, true, enElMenu("contar", 1), enElMenu("ticket", 0)).publicar)
    }

    @Test
    fun `el rango que renumera el sistema no cuenta como cambio`() {
        // En el de la tienda solo va «Contar»: la app le pone el rango 1 y el sistema lo guarda con 0.
        assertFalse(plan(armaTickets = false, sePuedeContar = true, enElMenu("contar", orden = 0)).publicar)
    }

    @Test
    fun `otro orden u otra etiqueta publica`() {
        assertTrue("Otro orden", plan(true, true, enElMenu("contar", 0), enElMenu("ticket", 1)).publicar)
        assertTrue("Otra etiqueta, otro idioma u otra versión", plan(true, false, enElMenu("ticket", 0, firma = "vieja")).publicar)
        assertTrue("Falta uno", plan(true, true, enElMenu("ticket", 0)).publicar)
    }

    @Test
    fun `lo que ya no va se apaga con su mensaje y no hace falta publicar`() {
        // El piloto empieza: «Contar» sale del menú (y, si alguien lo ancló, se queda apagado con su mensaje).
        val p = plan(armaTickets = true, sePuedeContar = false, enElMenu("ticket", 0), enElMenu("contar", 1))
        assertEquals(mapOf("contar" to "En el piloto no se cuenta"), p.apagar)
        assertFalse("disableShortcuts ya lo saca del menú", p.publicar)
        // El celular pasa a ser el de la tienda: «Armar ticket» se apaga con el suyo.
        assertEquals(
            mapOf("ticket" to "En este celular no se arman tickets"),
            plan(armaTickets = false, sePuedeContar = false, enElMenu("ticket", 0)).apagar,
        )
    }

    @Test
    fun `un anclado apagado que vuelve a ir se enciende y se publica`() {
        val p = plan(true, true, enElMenu("ticket", 0), ancladoApagado("contar"))
        assertEquals(listOf("contar"), p.encender)
        assertTrue("Solo publicarlo lo regresa al menú", p.publicar)
        assertTrue(p.apagar.isEmpty())
    }

    @Test
    fun `uno ya apagado no se vuelve a apagar`() {
        val p = plan(armaTickets = true, sePuedeContar = false, enElMenu("ticket", 0), ancladoApagado("contar"))
        assertTrue(p.apagar.isEmpty())
        assertTrue(p.encender.isEmpty())
        assertFalse(p.publicar)
    }
}
