package com.kanyandula.discovernearby.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CanvasColors = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    secondary = Brand,
    onSecondary = Background,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
)

// ponytail: one scheme for day and night until Design supplies a day palette (open decision 1).
@Composable
fun DiscoverNearbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CanvasColors, content = content)
}
