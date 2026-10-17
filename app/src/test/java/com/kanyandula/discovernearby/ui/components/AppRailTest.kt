package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.RailTab
import com.kanyandula.discovernearby.ui.ReferencePanelHeight
import com.kanyandula.discovernearby.ui.railTab
import com.kanyandula.discovernearby.ui.text
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.PanelPadding
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// DN-UX-011: the rail's labelled tabs on the reference AVD, where the rail is as tall as the panel.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class AppRailTest {

    @get:Rule
    val rule = createComposeRule()

    private val railHeight = ReferencePanelHeight + PanelPadding * 2

    @Before
    fun setUp() {
        rule.setContent {
            DiscoverNearbyTheme {
                Box(modifier = Modifier.height(railHeight)) { AppRail(selected = RailTab.DISCOVER, onSelect = {}) }
            }
        }
    }

    private fun tabNode(tab: RailTab) = rule.onNode(railTab(text(tab.label)))

    @Test
    fun everyTabIsATouchTargetAndProfileSitsAtTheBottom() {
        RailTab.entries.forEach {
            tabNode(it).assertWidthIsAtLeast(MinTouchTarget).assertHeightIsAtLeast(MinTouchTarget)
        }
        val map = tabNode(RailTab.MAP).getUnclippedBoundsInRoot()
        val profile = tabNode(RailTab.PROFILE).getUnclippedBoundsInRoot()
        assertTrue(profile.top > map.bottom)
        assertTrue(profile.bottom <= railHeight && railHeight - profile.bottom <= 16.dp)
    }

    // The label is the tab's name; the icon is decorative, so the name isn't read twice.
    @Test
    fun eachTabIsReadOnceByItsLabel() {
        RailTab.entries.forEach {
            tabNode(it).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        }
    }
}
