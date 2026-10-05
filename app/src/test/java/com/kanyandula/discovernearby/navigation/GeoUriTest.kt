package com.kanyandula.discovernearby.navigation

import com.kanyandula.discovernearby.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

// Harbour Roasters, Greystones in the fake data: TestLocation.GREYSTONES + its (0.004, 0.003) offset (FakePlaces).
internal val HARBOUR_ROASTERS = GeoPoint(53.148, -6.0603)

class GeoUriTest {

    @Test
    fun roundsToSixDecimalPlaces() {
        assertEquals("geo:53.123457,-6.000001", geoUri(GeoPoint(53.1234567, -6.0000009)))
    }

    // A comma-decimal locale must not reach the URI (docs/03 §11).
    @Test
    fun usesDotsWhateverTheDefaultLocale() {
        val default = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals("geo:53.148000,-6.060300", geoUri(HARBOUR_ROASTERS))
        } finally {
            Locale.setDefault(default)
        }
    }
}
