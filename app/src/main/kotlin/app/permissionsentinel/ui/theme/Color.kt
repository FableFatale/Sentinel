package app.permissionsentinel.ui.theme

import androidx.compose.ui.graphics.Color

// Light — warm paper background, ink text, teal accent (EasyUI reference)
val LightBackground = Color(0xFFFAFAF7)
val LightOnBackground = Color(0xFF16181D)
val LightSurface = Color(0xFFF1F1EC)
val LightSurfaceHigh = Color(0xFFE7E7E1)
val LightPrimary = Color(0xFF2E6E62)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightOutline = Color(0xFFDDDDD6)
val LightMuted = Color(0xFF6B7076)

// Dark — deep slate, teal-tinted accent
val DarkBackground = Color(0xFF0E1116)
val DarkOnBackground = Color(0xFFE8EAE6)
val DarkSurface = Color(0xFF1A1F26)
val DarkSurfaceHigh = Color(0xFF242B33)
val DarkPrimary = Color(0xFF7FD1C8)
val DarkOnPrimary = Color(0xFF0E1116)
val DarkOutline = Color(0xFF313942)
val DarkMuted = Color(0xFF9AA3AB)

// Heatmap scale (empty -> busy), shared by both themes with theme-aware base
fun heatmapColor(base: Color, accent: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return lerp(base, accent, 0.25f + 0.75f * f)
}

private fun lerp(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha + (to.alpha - from.alpha) * fraction,
)
