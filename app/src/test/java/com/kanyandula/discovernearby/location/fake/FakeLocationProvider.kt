package com.kanyandula.discovernearby.location.fake

import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.places.fake.TestLocation

/** Returns [result], which a test may change; the app reads AndroidLocationProvider. */
class FakeLocationProvider(
    var result: LocationResult = LocationResult.Available(TestLocation.GREYSTONES.point),
) : LocationProvider {
    override suspend fun currentLocation(): LocationResult = result
}
