package com.kanyandula.discovernearby.ui

import android.net.Uri
import android.os.Bundle
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import kotlinx.serialization.builtins.nullable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JsonNavTypeTest {

    private val type = JsonNavType(PlaceSummary.serializer())

    // Characters a route string must survive, in the place name and the JSON around it.
    private val place = testPlace("brew-1", "cafe").copy(
        name = "Brew & Bloom, Greystones /?#% \"1\"",
        rating = 4.6,
        ratingCount = 128,
        attributes = setOf(PlaceAttribute(PARKING, PROVIDED)),
        attribution = "HERE", // Details reads HERE's notice from the route (DN-M1-003)
    )

    // DN-UX-004: the row's Tripadvisor photo and rating ride to Details, and a row without them carries null.
    @Test
    fun aNullableEnrichmentSurvivesTheRouteAndSavedState() {
        val enrichmentType = JsonNavType(PlaceEnrichment.serializer().nullable)
        val enrichment = PlaceEnrichment(photoUrl = "https://example.test/p.jpg?w=800&h=-1&s=1")
        assertEquals(enrichment, enrichmentType.parseValue(Uri.decode(enrichmentType.serializeAsValue(enrichment))))
        val bundle = Bundle()
        enrichmentType.put(bundle, "enrichment", null)
        assertNull(enrichmentType.get(bundle, "enrichment"))
    }

    // Navigation decodes the route value before parseValue.
    @Test
    fun survivesTheRouteString() {
        assertEquals(place, type.parseValue(Uri.decode(type.serializeAsValue(place))))
    }

    // The back stack's saved state: Details keeps its place after process death.
    @Test
    fun survivesSavedState() {
        val bundle = Bundle()
        type.put(bundle, "place", place)
        assertEquals(place, type.get(bundle, "place"))
    }
}
