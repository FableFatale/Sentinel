package app.permissionsentinel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Light — "Paper": white ground, zinc cards, blue accent (Figma spec)
val LightBackground = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF18181B)
val LightSurface = Color(0xFFF4F4F5)
val LightSurfaceHigh = Color(0xFFE4E4E7)
val LightPrimary = Color(0xFF2563EB)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightOutline = Color(0xFFE4E4E7)
val LightMuted = Color(0xFF60606B)
val LightWarning = Color(0xFF946119)

// Dark — zinc 950 ground, zinc-800 cards, blue-400 accent
val DarkBackground = Color(0xFF0A0A0C)
val DarkOnBackground = Color(0xFFF4F4F5)
val DarkSurface = Color(0xFF1C1C1F)
val DarkSurfaceHigh = Color(0xFF2A2A2E)
val DarkPrimary = Color(0xFF60A5FA)
val DarkOnPrimary = Color(0xFF0B1220)
val DarkOutline = Color(0xFF3F3F46)
val DarkMuted = Color(0xFFA1A1AA)
val DarkWarning = Color(0xFFFBBF24)

/** Activity heatmap intensity scale, index 0 = empty cell … 4 = peak (also the selected fill). */
fun heatmapLevels(dark: Boolean): List<Color> = if (dark) {
    listOf(
        Color(0xFF242428),
        Color(0xFF1E3A8A),
        Color(0xFF1D4ED8),
        Color(0xFF3B82F6),
        Color(0xFF60A5FA),
    )
} else {
    listOf(
        Color(0xFFEEF0F3),
        Color(0xFFCFE3FF),
        Color(0xFF8DBCF8),
        Color(0xFF4F91EC),
        Color(0xFF2563EB),
    )
}

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Amber stroke for the system-access warning triangle (spec: #946119 light). */
@Composable
fun warningColor(): Color = if (isDarkTheme()) DarkWarning else LightWarning

/** Tinted info bar behind selected-interval details and the live camera banner. */
@Composable
fun detailBarColor(): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme()) 0.16f else 0.08f)
