package mx.papeleriapos.android.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Colores propios, sobrios (azul petróleo y grises) y sin los dinámicos del sistema: así la app se ve igual en todos los
 * celulares de la tienda y el verde de «leído» y el ámbar de «viendo» del escáner no compiten con el tema.
 */
private val Claro = lightColorScheme(
    primary = Color(0xFF1D5C7A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6F7),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = Color(0xFF4E616D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E5F3),
    onSecondaryContainer = Color(0xFF0A1E28),
    tertiary = Color(0xFF5F5A7D),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF181C1F),
    surface = Color(0xFFF7F9FB),
    onSurface = Color(0xFF181C1F),
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF40484D),
    surfaceContainer = Color(0xFFECEFF2),
    surfaceContainerHigh = Color(0xFFE6E9EC),
    outline = Color(0xFF70787E),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF8ECFF0),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004C68),
    onPrimaryContainer = Color(0xFFC8E6F7),
    secondary = Color(0xFFB5C9D7),
    onSecondary = Color(0xFF20333E),
    secondaryContainer = Color(0xFF364955),
    onSecondaryContainer = Color(0xFFD1E5F3),
    tertiary = Color(0xFFC9C1EA),
    onTertiary = Color(0xFF312C4C),
    background = Color(0xFF101417),
    onBackground = Color(0xFFDFE3E7),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFDFE3E7),
    surfaceVariant = Color(0xFF40484D),
    onSurfaceVariant = Color(0xFFBFC8CE),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262B2E),
    outline = Color(0xFF8A9298),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Colores de estado que Material no trae. */
@Immutable
data class ColoresEstado(val exito: Color, val enExito: Color, val aviso: Color, val enAviso: Color)

private val EstadoClaro = ColoresEstado(Color(0xFF2E7D32), Color.White, Color(0xFF8A5A00), Color.White)
private val EstadoOscuro = ColoresEstado(Color(0xFF81C784), Color(0xFF0B2E0D), Color(0xFFFFC107), Color(0xFF2B1F00))

val LocalColoresEstado = staticCompositionLocalOf { EstadoClaro }

@Composable
fun TemaEscaner(oscuro: Boolean = isSystemInDarkTheme(), contenido: @Composable () -> Unit) {
    CompositionLocalProvider(LocalColoresEstado provides if (oscuro) EstadoOscuro else EstadoClaro) {
        MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, content = contenido)
    }
}
