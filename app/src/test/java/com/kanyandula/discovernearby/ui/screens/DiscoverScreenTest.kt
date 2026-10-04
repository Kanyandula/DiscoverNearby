package com.kanyandula.discovernearby.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class DiscoverScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val selected = mutableListOf<DiscoveryCategory>()

    private fun show() = rule.setContent {
        DiscoverNearbyTheme { DiscoverScreen(onCategorySelected = { selected += it }) }
    }

    private fun tiles() = rule.onAllNodes(hasClickAction())

    @Test
    fun showsSixCategoriesInTwoRowsOfThree() {
        show()
        val nodes = tiles().fetchSemanticsNodes()
        val labels = nodes.map { it.config.getOrNull(SemanticsProperties.Text)?.first()?.text }
        assertEquals(listOf("Coffee", "Food", "Outdoors", "Family", "Scenic", "Explore"), labels)

        val tops = nodes.map { it.boundsInRoot.top }
        val lefts = nodes.map { it.boundsInRoot.left }
        assertEquals("row 1 shares a top", 1, tops.take(3).distinct().size)
        assertEquals("row 2 shares a top", 1, tops.drop(3).distinct().size)
        assertEquals("row 2 is below row 1", true, tops[3] > tops[0])
        assertEquals("columns line up", lefts.take(3), lefts.drop(3))
    }

    @Test
    fun showsEachSubtitle() {
        show()
        listOf(
            "Great coffee near you", "Places to eat", "Parks, trails and more",
            "Family-friendly places", "Beautiful views near you", "Hidden gems and local spots",
        ).forEach { rule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
    }

    @Test
    fun tilesMeetTheMinimumTouchTarget() {
        show()
        repeat(DiscoveryCategory.entries.size) { i ->
            tiles()[i].assertHeightIsAtLeast(MinTouchTarget).assertWidthIsAtLeast(MinTouchTarget)
        }
    }

    @Test
    fun eachTileSelectsItsCategory() {
        show()
        listOf("Coffee", "Food", "Outdoors", "Family", "Scenic", "Explore")
            .forEach { rule.onNodeWithText(it).performClick() }
        assertEquals(DiscoveryCategory.entries, selected)
    }
}
