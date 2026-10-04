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
    // Container roles feed Material 3 components (Card, chips, nav bars). Unset, they fall back to the
    // baseline purple; the canvas has only neutrals, so map them to its page, panel and tile tones.
    surfaceDim = Background,
    surfaceBright = SurfaceVariant,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceVariant,
    surfaceContainerHighest = SurfaceVariant,
    primaryContainer = SurfaceVariant,
    onPrimaryContainer = OnSurface,
    secondaryContainer = SurfaceVariant,
    onSecondaryContainer = OnSurface,
    tertiary = Brand,
    onTertiary = Background,
    tertiaryContainer = SurfaceVariant,
    onTertiaryContainer = OnSurface,
    outline = OnSurfaceVariant,
    outlineVariant = SurfaceVariant,
)

// ponytail: one scheme for day and night until Design supplies a day palette (open decision 1).
@Composable
fun DiscoverNearbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CanvasColors, typography = CanvasTypography, content = content)
}
