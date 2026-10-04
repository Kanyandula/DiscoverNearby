package com.kanyandula.discovernearby

import android.content.Context
import com.kanyandula.discovernearby.car.CarDrivingRestrictions
import com.kanyandula.discovernearby.car.DrivingRestrictions
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The single wiring point (docs/03 §3): every app-scoped dependency is constructed here by hand.
 */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ponytail: fakes until the provider (M1, after ADR-001) and AndroidLocationProvider (DN-M0-006).
    // Debug launches choose fakePlaces.scenario for the docs/04 §7 emulator scenarios (MainActivity).
    val fakePlaces = FakePlacesRepository()
    val placesRepository: PlacesRepository = fakePlaces
    val locationProvider: LocationProvider = FakeLocationProvider()
    val drivingRestrictions: DrivingRestrictions = CarDrivingRestrictions(context, appScope)
    val discoverUseCase = DiscoverUseCase(placesRepository, locationProvider, BasicRecommendationEngine())
}
