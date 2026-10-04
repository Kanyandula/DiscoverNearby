package com.kanyandula.discovernearby.ui

import android.net.Uri
import android.os.Bundle
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.AttributeSource.PROVIDED
import com.kanyandula.discovernearby.model.AttributeType.PARKING
import com.kanyandula.discovernearby.model.PlaceAttribute
import com.kanyandula.discovernearby.model.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JsonNavTypeTest {

    private val type = JsonNavType(Recommendation.serializer())

    // Characters a route string must survive, in the place name and the JSON around it.
    private val recommendation = Recommendation(
        place = testPlace("brew-1", "cafe").copy(
            name = "Brew & Bloom, Greystones /?#% \"1\"",
            rating = 4.6,
            ratingCount = 128,
            attributes = setOf(PlaceAttribute(PARKING, PROVIDED)),
        ),
        score = 1.0,
        distanceMeters = 1_900,
        travelTimeMinutes = null,
        minutesAhead = null,
        detourMinutes = null,
    )

    // Navigation decodes the route value before parseValue.
    @Test
    fun survivesTheRouteString() {
        assertEquals(recommendation, type.parseValue(Uri.decode(type.serializeAsValue(recommendation))))
    }

    // The back stack's saved state: Details keeps its place after process death.
    @Test
    fun survivesSavedState() {
        val bundle = Bundle()
        type.put(bundle, "recommendation", recommendation)
        assertEquals(recommendation, type.get(bundle, "recommendation"))
    }
}
