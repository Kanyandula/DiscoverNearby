package com.kanyandula.discovernearby

import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository

/**
 * The single wiring point (docs/03 §3): every app-scoped dependency is constructed here by hand.
 */
class AppContainer {
    // ponytail: fakes until the provider (M1, after ADR-001) and AndroidLocationProvider (DN-M0-006).
    val placesRepository: PlacesRepository = FakePlacesRepository()
    val locationProvider: LocationProvider = FakeLocationProvider()
}
