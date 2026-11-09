package mx.papeleriapos.android.entrada

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mx.papeleriapos.android.ui.ALTO_TOCABLE

/**
 * Lo que tapa la app mientras no se ha entrado (docs/propuesta-interfaz.md, «Entrar»): opaco, con «Entrar» para volver a
 * pedir la huella si el diálogo del sistema se cerró. Debajo, la cámara ya se está enlazando: al entrar, ya escanea.
 */
@Composable
fun PantallaEntrada(alEntrar: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // Se queda con los toques: nada de abajo se puede tocar.
            .pointerInput(Unit) {}
            .semantics { }
            .padding(32.dp)
            .testTag("pantalla-entrada"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Entra con tu huella", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text(
            "O con el bloqueo del celular. La copia del catálogo y el ticket son tuyos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(onClick = alEntrar, modifier = Modifier.padding(top = 24.dp).heightIn(min = ALTO_TOCABLE).testTag("entrar")) { Text("Entrar") }
    }
}
