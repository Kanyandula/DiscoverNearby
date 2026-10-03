package com.kanyandula.discovernearby.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiscoverNearbyThemeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun scheme(): ColorScheme {
        lateinit var scheme: ColorScheme
        rule.setContent { DiscoverNearbyTheme { scheme = MaterialTheme.colorScheme } }
        return scheme
    }

    @Test
    fun appShowsItsTitle() {
        rule.setContent { DiscoverNearbyTheme { DiscoverNearbyApp() } }
        rule.onNodeWithText("Discover Nearby").assertIsDisplayed()
    }

    // Literal hex values from the canvas Discover artboard, so a changed constant fails here.
    @Test
    fun usesCanvasPalette() {
        val scheme = scheme()
        assertEquals(Color(0xFF0B0E11), scheme.background)
        assertEquals(Color(0xFF15191D), scheme.surface)
        assertEquals(Color(0xFFEEF1F3), scheme.onSurface)
        assertEquals(Color(0xFFAEB6BD), scheme.onSurfaceVariant)
        assertEquals(Color(0xFF4C8DF6), scheme.primary)
        assertEquals(Color(0xFF5BD68A), scheme.secondary)
    }

    // Material 3 components draw containers from these roles; unset, they fall back to the baseline purple.
    @Test
    fun containerRolesUseCanvasNeutrals() {
        val scheme = scheme()
        val tile = Color(0xFF1F252B)
        assertEquals(tile, scheme.surfaceContainerHighest) // default filled Card
        assertEquals(tile, scheme.surfaceContainerHigh)
        assertEquals(Color(0xFF15191D), scheme.surfaceContainer)
        assertEquals(tile, scheme.primaryContainer)
        assertEquals(tile, scheme.secondaryContainer)
        assertEquals(tile, scheme.tertiaryContainer)
        assertEquals(Color(0xFFAEB6BD), scheme.outline)
        assertEquals(tile, scheme.outlineVariant)
    }
}
