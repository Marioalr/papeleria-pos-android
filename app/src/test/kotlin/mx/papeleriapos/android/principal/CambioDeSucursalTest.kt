package mx.papeleriapos.android.principal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.papeleriapos.android.caja.NoSeMando
import mx.papeleriapos.android.catalogo.CatalogoDeProductos
import mx.papeleriapos.android.catalogo.Escala
import mx.papeleriapos.android.catalogo.Producto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** «¿Estás en Sucursal 2? Cambiar»: espera a los productos de la otra copia antes de volver a cotizar, y si no la encuentra lo dice. */
@OptIn(ExperimentalCoroutinesApi::class)
class CambioDeSucursalTest {
    private val deLaMatriz = CatalogoDeProductos(listOf(Producto("7509990100019", "Cuaderno", "Escolar", listOf(Escala(1, 4600)), 8.0)))
    private val deLaSucursal2 = CatalogoDeProductos(listOf(Producto("7509990100019", "Cuaderno", "Escolar", listOf(Escala(1, 4900)), 3.0)))

    @Test
    fun `cambia y sigue hasta que los productos son los de la otra sucursal`() = runTest(StandardTestDispatcher()) {
        val productos = MutableStateFlow<CatalogoDeProductos?>(deLaMatriz)
        val buscadas = ArrayList<String>()
        val cambio = CambioDeSucursal({ buscadas.add(it) }, this)
        var conQue: CatalogoDeProductos? = null
        cambio.cambiar("c-s2", productos) { conQue = it }
        // Sin adelantar el reloj: con advanceUntilIdle vencería la espera de los productos.
        runCurrent()
        assertEquals(listOf("c-s2"), buscadas)
        assertEquals("c-s2", cambio.estado.value.buscando)
        assertNull("Con los productos de la matriz no se cotiza en la sucursal 2", conQue)
        productos.value = deLaSucursal2
        runCurrent()
        assertSame(deLaSucursal2, conQue)
        assertNull(cambio.estado.value.buscando)
    }

    @Test
    fun `si no encuentra la otra sucursal lo dice y no sigue`() = runTest(StandardTestDispatcher()) {
        val cambio = CambioDeSucursal({ throw NoSeMando("No se encontró la caja principal de Sucursal 2 en esta red.", sinRed = true) }, this)
        var siguio = false
        cambio.cambiar("c-s2", MutableStateFlow(deLaMatriz)) { siguio = true }
        advanceUntilIdle()
        assertEquals("No se encontró la caja principal de Sucursal 2 en esta red.", cambio.estado.value.error)
        assertEquals(false, siguio)
        cambio.olvidar()
        assertNull(cambio.estado.value.error)
    }
}
