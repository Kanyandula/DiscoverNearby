package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.PriceLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FakeEnricherTest {

    // Made-up data for the scenario fakes, by the template name before the town: no photo, no rating, no call.
    @Test
    fun aKnownFakePlaceGetsAPriceAndDescription() = runTest {
        val enrichment = FakeEnricher.enrich(testPlace("The Daily Grind, Greystones", "coffee_shop"), COFFEE)
        assertEquals(PriceLevel.CHEAP, enrichment?.priceLevel)
        assertNotNull(enrichment?.description)
        assertNull(enrichment?.photoUrl)
        assertNull(enrichment?.rating)
    }

    @Test
    fun otherPlacesGetNothing() = runTest {
        assertNull(FakeEnricher.enrich(testPlace("Brew & Bloom, Greystones", "cafe"), COFFEE))
    }
}
