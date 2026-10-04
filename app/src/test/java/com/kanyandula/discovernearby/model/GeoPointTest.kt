package com.kanyandula.discovernearby.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoPointTest {

    private val greystones = GeoPoint(53.1440, -6.0633)
    private val dublin = GeoPoint(53.3498, -6.2603)
    private val galway = GeoPoint(53.2707, -9.0568)

    @Test
    fun distanceIsGreatCircleMetres() {
        assertEquals(26_372.0, greystones.distanceMetersTo(dublin).toDouble(), 30.0)
        assertEquals(199_841.0, greystones.distanceMetersTo(galway).toDouble(), 200.0)
    }

    @Test
    fun distanceIsSymmetricAndZeroToItself() {
        assertEquals(greystones.distanceMetersTo(dublin), dublin.distanceMetersTo(greystones))
        assertEquals(0, greystones.distanceMetersTo(greystones))
    }
}
