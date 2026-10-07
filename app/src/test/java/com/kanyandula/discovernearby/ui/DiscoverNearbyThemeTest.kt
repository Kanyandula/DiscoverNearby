package com.kanyandula.discovernearby.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kanyandula.discovernearby.ui.theme.Action
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.Highlight
import com.kanyandula.discovernearby.ui.theme.Raised
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

    // Canvas text roles: message title 36, tile label and screen title 32, place name 28, header 20, buttons 24
    // (all semibold); row details and message body 22; tile subtitle 20.
    @Test
    fun usesCanvasTypeScale() {
        lateinit var type: Typography
        rule.setContent { DiscoverNearbyTheme { type = MaterialTheme.typography } }
        assertEquals(36.sp, type.headlineLarge.fontSize)
        assertEquals(FontWeight.SemiBold, type.headlineLarge.fontWeight)
        assertEquals(32.sp, type.headlineMedium.fontSize)
        assertEquals(FontWeight.SemiBold, type.headlineMedium.fontWeight)
        assertEquals(28.sp, type.headlineSmall.fontSize)
        assertEquals(FontWeight.SemiBold, type.headlineSmall.fontWeight)
        assertEquals(20.sp, type.titleLarge.fontSize)
        assertEquals(FontWeight.SemiBold, type.titleLarge.fontWeight)
        assertEquals(22.sp, type.titleMedium.fontSize)
        assertEquals(FontWeight.Normal, type.titleMedium.fontWeight)
        assertEquals(20.sp, type.bodyLarge.fontSize)
        assertEquals(24.sp, type.labelLarge.fontSize)
        assertEquals(FontWeight.SemiBold, type.labelLarge.fontWeight)
    }

    // Literal hex values from the canvas message and loading artboards.
    @Test
    fun messageTokensMatchTheCanvas() {
        assertEquals(Color(0xFF2A3138), Raised)
        assertEquals(Color(0xFF2563EB), Action)
        assertEquals(Color(0xFF6FA8F5), Highlight)
    }

    @Test
    fun appShowsItsTitle() {
        rule.setContent { DiscoverNearbyTheme { DiscoverNearbyApp(appContainer()) } }
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
