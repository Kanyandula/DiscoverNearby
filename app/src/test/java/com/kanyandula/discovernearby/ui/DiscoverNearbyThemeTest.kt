package com.kanyandula.discovernearby.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kanyandula.discovernearby.ui.theme.Accent
import com.kanyandula.discovernearby.ui.theme.Background
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.OnSurface
import com.kanyandula.discovernearby.ui.theme.Surface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiscoverNearbyThemeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun appShowsItsTitle() {
        rule.setContent { DiscoverNearbyTheme { DiscoverNearbyApp() } }
        rule.onNodeWithText("Discover Nearby").assertIsDisplayed()
    }

    @Test
    fun usesCanvasPalette() {
        lateinit var scheme: ColorScheme
        rule.setContent { DiscoverNearbyTheme { scheme = MaterialTheme.colorScheme } }
        assertEquals(Background, scheme.background)
        assertEquals(Surface, scheme.surface)
        assertEquals(OnSurface, scheme.onSurface)
        assertEquals(Accent, scheme.primary)
    }
}
