package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.discovery.CategoryConfigs
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HereCategoriesTest {

    // Review Focus 1: the engine keeps only places whose kinds the category targets. A requested code that maps to
    // nothing, or to another category's kind, would show Empty on live data.
    @Test
    fun everyRequestedCategoryMapsToAKindItsCategoryAccepts() {
        assertEquals(DiscoveryCategory.entries.toSet(), HERE_CATEGORIES.keys)
        HERE_CATEGORIES.forEach { (category, codes) ->
            codes.split(",").forEach { code ->
                val kind = kindFor(code)
                assertTrue("$category $code -> $kind", kind in CategoryConfigs.getValue(category).kindWeights.keys)
            }
        }
    }

    @Test
    fun theMostSpecificCategoryWins() {
        assertEquals("coffee_shop", kindFor("100-1100-0010"))
        assertEquals("cafe", kindFor("100-1100-0331"))
        assertEquals("family_attraction", kindFor("300-3100-0027"))
        assertEquals("museum", kindFor("300-3100-0000"))
    }

    @Test
    fun aCategoryWeDoNotSearchHasNoKind() {
        assertNull(kindFor("700-7300-0000"))
        assertNull(kindFor("100-11000"))
    }
}
