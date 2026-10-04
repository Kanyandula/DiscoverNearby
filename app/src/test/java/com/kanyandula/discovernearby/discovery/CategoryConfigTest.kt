package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryConfigTest {

    @Test
    fun everyCategoryIsConfigured() {
        assertEquals(DiscoveryCategory.entries.toSet(), CategoryConfigs.keys)
    }

    // docs/03 §9 illustrative radii.
    @Test
    fun radiiMatchTheDocs() {
        val km = mapOf(COFFEE to 5, FOOD to 5, OUTDOORS to 20, FAMILY to 15, SCENIC to 30, EXPLORE to 15)
        km.forEach { (category, value) ->
            assertEquals(category.name, value * 1_000, CategoryConfigs.getValue(category).radiusMeters)
        }
    }

    @Test
    fun everyCategoryWantsFiveResults() {
        assertEquals(5, DESIRED_RECOMMENDATIONS)
        CategoryConfigs.values.forEach { assertEquals(DESIRED_RECOMMENDATIONS, it.desiredResults) }
    }

    // docs/03 §9 target kinds; Outdoors (do) and Scenic (look) stay distinct.
    @Test
    fun targetKindsFollowTheDocs() {
        assertEquals(setOf("cafe", "coffee_shop"), CategoryConfigs.getValue(COFFEE).targetKinds)
        assertTrue("park" in CategoryConfigs.getValue(OUTDOORS).targetKinds)
        assertTrue("viewpoint" in CategoryConfigs.getValue(SCENIC).targetKinds)
        assertTrue("viewpoint" !in CategoryConfigs.getValue(OUTDOORS).targetKinds)
        assertTrue("playground" in CategoryConfigs.getValue(FAMILY).targetKinds)
        assertTrue("museum" in CategoryConfigs.getValue(EXPLORE).targetKinds)
        assertTrue(CategoryConfigs.values.all { it.targetKinds.isNotEmpty() })
    }
}
