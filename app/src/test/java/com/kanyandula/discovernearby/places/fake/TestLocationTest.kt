package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestLocationTest {

    // docs/03 §7: stored once, never re-typed.
    @Test
    fun coordinatesMatchTheDocs() {
        assertEquals(GeoPoint(53.1440, -6.0633), TestLocation.GREYSTONES.point)
        assertEquals(GeoPoint(53.3498, -6.2603), TestLocation.DUBLIN.point)
        assertEquals(GeoPoint(53.2707, -9.0568), TestLocation.GALWAY.point)
    }

    @Test
    fun nearestToFindsTheTownAnOriginIsIn() {
        assertEquals(TestLocation.GREYSTONES, TestLocation.nearestTo(GeoPoint(53.15, -6.07)))
        assertEquals(TestLocation.GALWAY, TestLocation.nearestTo(GeoPoint(53.27, -9.05)))
    }

    @Test
    fun nearestToIsNullFarFromEveryTestLocation() {
        assertNull(TestLocation.nearestTo(GeoPoint(51.5074, -0.1278))) // London
    }
}
