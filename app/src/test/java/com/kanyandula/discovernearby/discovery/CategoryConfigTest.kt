package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.EXPLORE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FAMILY
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.FOOD
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.OUTDOORS
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC
import com.kanyandula.discovernearby.model.AttributeType.CAFE
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.AttributeType.TOILETS
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
        assertEquals(setOf("cafe", "coffee_shop"), CategoryConfigs.getValue(COFFEE).kindWeights.keys)
        assertTrue("park" in CategoryConfigs.getValue(OUTDOORS).kindWeights.keys)
        assertTrue("viewpoint" in CategoryConfigs.getValue(SCENIC).kindWeights.keys)
        assertTrue("viewpoint" !in CategoryConfigs.getValue(OUTDOORS).kindWeights.keys)
        assertTrue("playground" in CategoryConfigs.getValue(FAMILY).kindWeights.keys)
        assertTrue("museum" in CategoryConfigs.getValue(EXPLORE).kindWeights.keys)
        assertTrue(CategoryConfigs.values.all { it.kindWeights.keys.isNotEmpty() })
    }

    // Product Lead, 2026-10-07: these categories drop known-closed places; Outdoors and Scenic keep them.
    @Test
    fun timeSensitiveCategoriesDropClosedPlaces() {
        assertEquals(setOf(COFFEE, FOOD, FAMILY, EXPLORE), CategoryConfigs.filterValues { it.excludeClosed }.keys)
    }

    // Product Lead, 2026-10-07: three of a kind in Outdoors, two in Family and Explore; Coffee, Food and Scenic
    // uncapped.
    @Test
    fun diversityCapsFollowTheProductDecisions() {
        assertEquals(
            mapOf(COFFEE to null, FOOD to null, OUTDOORS to 3, FAMILY to 2, SCENIC to null, EXPLORE to 2),
            CategoryConfigs.mapValues { it.value.maxPerKind },
        )
    }

    // docs/03 §10 Family example: the strongest kinds outweigh a park; toilets, parking and café count.
    @Test
    fun familyWeightsFollowTheDocsExample() {
        val family = CategoryConfigs.getValue(FAMILY)
        assertTrue(family.kindWeights.getValue("playground") > family.kindWeights.getValue("park"))
        assertEquals(setOf(TOILETS, PARKING, CAFE), family.amenityWeights.keys)
    }

    // A target kind that could never pass the floor would be dead configuration.
    @Test
    fun everyTargetKindPassesTheFloorAsPrimary() {
        CategoryConfigs.forEach { (category, config) ->
            config.kindWeights.forEach { (kind, weight) -> assertTrue("$category $kind", weight >= SCORE_FLOOR) }
        }
    }
}
