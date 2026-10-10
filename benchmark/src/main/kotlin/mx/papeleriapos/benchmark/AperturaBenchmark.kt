package mx.papeleriapos.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cuánto tarda la app en estar escaneando (docs/escaner.md, «Apertura»), en frío, tibio y caliente, sin y con el
 * Baseline Profile:
 * - `StartupTimingMetric`: TTID (primer cuadro de la interfaz) y TTFD, que la app reporta con el primer cuadro analizado
 *   (no hay TTFD en caliente: `reportFullyDrawn` vale una vez por Activity).
 * - `escaner.hastaPrimerCuadro`: la sección asíncrona de cada apertura, del inicio (proceso, Activity o vuelta al frente)
 *   al primer cuadro analizado; es la que sirve en caliente.
 * - `escaner.enlazar`: `bindToLifecycle`. `escaner.cuadro`, `escaner.localizar` y `escaner.leer`: el promedio por cuadro
 *   de lo que se analizó en la iteración.
 *
 * La cámara se permite antes (sin el diálogo, que pausa la Activity y anula el TTFD). Iteraciones: 15, o el argumento
 * `iteraciones`. En el emulador solo como prueba de humo, con `androidx.benchmark.suppressErrors=EMULATOR`.
 */
@OptIn(ExperimentalMetricApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class AperturaBenchmark {
    @get:Rule
    val regla = MacrobenchmarkRule()

    @Test fun frioSinPerfil() = medir(StartupMode.COLD, CompilationMode.None())

    @Test fun frioConPerfil() = medir(StartupMode.COLD, CompilationMode.Partial(BaselineProfileMode.Require))

    @Test fun tibioSinPerfil() = medir(StartupMode.WARM, CompilationMode.None())

    @Test fun tibioConPerfil() = medir(StartupMode.WARM, CompilationMode.Partial(BaselineProfileMode.Require))

    @Test fun calienteSinPerfil() = medir(StartupMode.HOT, CompilationMode.None())

    @Test fun calienteConPerfil() = medir(StartupMode.HOT, CompilationMode.Partial(BaselineProfileMode.Require))

    private fun medir(modo: StartupMode, compilacion: CompilationMode) = regla.measureRepeated(
        packageName = PAQUETE,
        metrics = listOf(
            StartupTimingMetric(),
            TraceSectionMetric("escaner.hastaPrimerCuadro", TraceSectionMetric.Mode.First),
            TraceSectionMetric("escaner.enlazar", TraceSectionMetric.Mode.First),
            // La espera de CameraX (en frío, su inicialización) y la elección del rango de FPS, antes de enlazar.
            TraceSectionMetric("escaner.esperaProveedor", TraceSectionMetric.Mode.First),
            TraceSectionMetric("escaner.rangosFps", TraceSectionMetric.Mode.First),
            TraceSectionMetric("escaner.cuadro", TraceSectionMetric.Mode.Average),
            TraceSectionMetric("escaner.localizar", TraceSectionMetric.Mode.Average),
            TraceSectionMetric("escaner.leer", TraceSectionMetric.Mode.Average),
        ),
        compilationMode = compilacion,
        startupMode = modo,
        iterations = iteraciones(),
        setupBlock = {
            device.executeShellCommand("pm grant $PAQUETE android.permission.CAMERA")
            pressHome()
        },
    ) {
        startActivityAndWait()
        esperarEscaneando()
        // Unos cuadros más para que el promedio por cuadro no sea solo el primero (el más caro).
        Thread.sleep(MS_ESCANEANDO)
    }

    private fun MacrobenchmarkScope.esperarEscaneando() {
        val listo = device.wait(Until.hasObject(By.res(ESCANEANDO)), MS_ESPERA) == true
        check(listo) { "La app no llegó a «$ESCANEANDO» en $MS_ESPERA ms: ¿hay cámara y permiso?" }
    }

    private fun iteraciones(): Int =
        InstrumentationRegistry.getArguments().getString("iteraciones")?.toIntOrNull()?.coerceAtLeast(1) ?: ITERACIONES

    private companion object {
        const val PAQUETE = "mx.papeleriapos.android"
        /** El testTag de MainActivity (ETIQUETA_ESCANEANDO), expuesto como id con testTagsAsResourceId. */
        const val ESCANEANDO = "escaneando"
        const val ITERACIONES = 15
        const val MS_ESPERA = 15_000L
        const val MS_ESCANEANDO = 1_000L
    }
}
