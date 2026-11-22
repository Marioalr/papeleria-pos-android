package mx.papeleriapos.android.actualizar

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.papeleriapos.android.BuildConfig
import mx.papeleriapos.android.ui.ALTO_TOCABLE
import mx.papeleriapos.android.ui.Fechas
import mx.papeleriapos.android.ui.FilaDato
import mx.papeleriapos.android.ui.TituloSeccion

/**
 * «Actualizar la app» (la 0.5): la versión instalada, «Buscar versión nueva» y, si la hay, qué trae, «Descargar e
 * instalar» y en qué va. La app nunca busca sola: abrir esta pantalla no pide nada a nadie; solo los botones.
 */
@Composable
fun PantallaActualizar(modifier: Modifier = Modifier, actualizador: Actualizador = Actualizador.de(LocalContext.current)) {
    val estado by actualizador.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    // El permiso se da en los ajustes de Android: al volver de ahí se vuelve a leer.
    var puedeInstalar by remember(actualizador) { mutableStateOf(actualizador.puedeInstalar()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { puedeInstalar = actualizador.puedeInstalar() }
    val acciones = remember(actualizador, contexto) {
        AccionesActualizar(
            buscar = actualizador::buscar,
            descargarEInstalar = {
                puedeInstalar = actualizador.puedeInstalar()
                actualizador.descargarEInstalar()
            },
            cancelar = actualizador::cancelar,
            permitirInstalar = {
                try {
                    InstaladorAndroid.ajusteParaPermitir(contexto)?.let(contexto::startActivity)
                } catch (e: ActivityNotFoundException) {
                    // Un Android sin ese ajuste: su propio cuadro lo pedirá al instalar.
                }
            },
        )
    }
    ContenidoActualizar(estado, "${actualizador.instalada} · ${BuildConfig.COMMIT}", puedeInstalar, acciones, modifier)
}

class AccionesActualizar(
    val buscar: () -> Unit = {},
    val descargarEInstalar: () -> Unit = {},
    val cancelar: () -> Unit = {},
    val permitirInstalar: () -> Unit = {},
)

@Composable
fun ContenidoActualizar(
    estado: EstadoActualizacion,
    /** «0.5.0 · a1b2c3d»: la versión y el commit con que se armó. */
    instalada: String,
    /** Si Android ya deja que esta app instale («Instalar apps desconocidas»). */
    puedeInstalar: Boolean,
    acciones: AccionesActualizar,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("pantalla-actualizar"),
    ) {
        TituloSeccion("Esta app")
        FilaDato("Instalada", instalada, Modifier.testTag("version-instalada"))
        Spacer(Modifier.height(8.dp))
        when (estado) {
            EstadoActualizacion.SinBuscar -> BotonBuscar(acciones.buscar, principal = true)
            EstadoActualizacion.Buscando -> EnCurso("Preguntando a GitHub…", "buscando-version")
            is EstadoActualizacion.AlDia -> {
                Text(
                    "La app está al día: la última versión publicada es la ${estado.publicada}.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("al-dia"),
                )
                BotonBuscar(acciones.buscar, principal = false)
            }
            is EstadoActualizacion.Hay -> {
                // El botón, antes de las notas: son largas y lo dejaban hasta abajo (visto en el emulador).
                VersionNueva(estado.version, conNotas = false)
                estado.aviso?.let { Nota(it, "actualizar-nota") }
                Instalar("Descargar e instalar", puedeInstalar, acciones)
                Notas(estado.version)
            }
            is EstadoActualizacion.Bajando -> {
                VersionNueva(estado.version, conNotas = false)
                Text(
                    "Bajando de GitHub: ${Versiones.megas(estado.bajado)} de ${Versiones.megas(estado.version.apk.tamano)}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
                LinearProgressIndicator(
                    progress = { estado.avance },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("progreso-descarga"),
                )
                OutlinedButton(onClick = acciones.cancelar, modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("cancelar-descarga")) {
                    Text("Cancelar")
                }
            }
            is EstadoActualizacion.Comprobando -> {
                VersionNueva(estado.version, conNotas = false)
                EnCurso("Comprobando que es la misma app, más nueva y con la misma firma…", "comprobando")
            }
            is EstadoActualizacion.EsperandoAAndroid -> {
                VersionNueva(estado.version, conNotas = false)
                Text(
                    "Android pregunta si quieres actualizar la app: toca «Actualizar» en su cuadro. Al instalarla, Android " +
                        "cierra la app; vuelve a abrirla y ya es la ${estado.version.version}.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("esperando-android"),
                )
                Nota(
                    "Google Play Protect puede pedir antes que revise la app (no viene de su tienda): acepta la revisión y " +
                        "luego «Instalar».",
                )
                Nota("Si el cuadro de Android no salió o se cerró, vuelve a buscar la versión.")
                BotonBuscar(acciones.buscar, principal = false)
            }
            is EstadoActualizacion.Error -> {
                estado.version?.let { VersionNueva(it, conNotas = false) }
                Aviso(estado.mensaje)
                if (estado.version != null) Instalar("Volver a intentar", puedeInstalar, acciones)
                BotonBuscar(acciones.buscar, principal = estado.version == null)
            }
        }
        Spacer(Modifier.height(12.dp))
        Nota(PRIVACIDAD, "nota-privacidad")
    }
}

const val PRIVACIDAD = "La app nunca busca sola. Solo al tocar «Buscar versión nueva» sale de la red de la tienda: lee la lista de " +
    "versiones de github.com y, si lo pides, baja el APK. No manda nada del celular ni de la tienda."

/** La versión encontrada: su número, cuándo se publicó, cuánto pesa y, con [conNotas], qué trae. */
@Composable
private fun VersionNueva(v: VersionPublicada, conNotas: Boolean = true) {
    TituloSeccion("Versión nueva: ${v.version}", Modifier.testTag("version-nueva"))
    v.publicada?.let { FilaDato("Publicada", Fechas.fechaYHora(it)) }
    FilaDato("Tamaño", Versiones.megas(v.apk.tamano))
    if (conNotas) Notas(v)
}

/** Qué trae la versión: las notas con que se publicó (la sección de CAMBIOS.md). */
@Composable
private fun Notas(v: VersionPublicada) {
    if (v.notas.isEmpty()) return
    Text("Qué trae", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Column(Modifier.padding(top = 2.dp).testTag("notas-version")) {
        for (renglon in v.notas) {
            Text(renglon, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

/** «Descargar e instalar» o, si Android todavía no deja que esta app instale, por qué y el botón que abre su ajuste. */
@Composable
private fun Instalar(texto: String, puedeInstalar: Boolean, acciones: AccionesActualizar) {
    if (!puedeInstalar) {
        Text(
            "Para instalarla, Android pide que le permitas a esta app «Instalar apps desconocidas». Se da una sola vez: " +
                "toca el botón, enciende el permiso y regresa.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 6.dp).testTag("sin-permiso-instalar"),
        )
        Button(onClick = acciones.permitirInstalar, modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_TOCABLE).testTag("permitir-instalar")) {
            Text("Abrir el permiso de Android")
        }
        return
    }
    Button(
        onClick = acciones.descargarEInstalar,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = ALTO_TOCABLE).testTag("descargar-instalar"),
    ) { Text(texto) }
    Nota("El alta con la caja, la copia del catálogo y el ticket se conservan.")
}

@Composable
private fun BotonBuscar(alTocar: () -> Unit, principal: Boolean) {
    val modificador = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = ALTO_TOCABLE).testTag("buscar-version")
    if (principal) Button(onClick = alTocar, modifier = modificador) { Text("Buscar versión nueva") }
    else OutlinedButton(onClick = alTocar, modifier = modificador) { Text("Buscar versión nueva") }
}

@Composable
private fun EnCurso(texto: String, etiqueta: String) {
    Text(texto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp).testTag(etiqueta))
    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("actualizar-aviso"),
    )
}

@Composable
private fun Nota(texto: String, etiqueta: String? = null) {
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp).then(if (etiqueta != null) Modifier.testTag(etiqueta) else Modifier),
    )
}
