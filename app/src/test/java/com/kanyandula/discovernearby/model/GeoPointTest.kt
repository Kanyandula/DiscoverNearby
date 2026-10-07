package com.kanyandula.discovernearby.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

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

    // Coordinates for URLs and URIs: six decimals and dots whatever the locale, never scientific notation.
    @Test
    fun latLngIsSixDecimalsWithDots() {
        val default = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals("53.144000,-6.063300", GeoPoint(53.144, -6.0633).latLng())
            assertEquals("0.000500,-0.000400", GeoPoint(0.0005, -0.0004).latLng())
        } finally {
            Locale.setDefault(default)
        }
    }
}
