package com.kanyandula.discovernearby.location.fake

import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.places.fake.TestLocation

/** Always returns [result]; the default stands at test location A until DN-M0-006 reads the real one. */
class FakeLocationProvider(
    private val result: LocationResult = LocationResult.Available(TestLocation.GREYSTONES.point),
) : LocationProvider {
    override suspend fun currentLocation(): LocationResult = result
}
