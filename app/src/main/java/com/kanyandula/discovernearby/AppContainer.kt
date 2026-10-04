package com.kanyandula.discovernearby

import android.content.Context
import android.content.pm.ApplicationInfo
import com.kanyandula.discovernearby.car.CarDrivingRestrictions
import com.kanyandula.discovernearby.car.DrivingRestrictions
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.navigation.NavigationLauncher
import com.kanyandula.discovernearby.navigation.fake.FakeNavigationLauncher
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import com.kanyandula.discovernearby.places.fake.FakeScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The single wiring point (docs/03 §3): every app-scoped dependency is constructed here by hand.
 */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val applicationInfo = context.applicationInfo

    // ponytail: fakes until the provider (M1, after ADR-001) and AndroidLocationProvider (DN-M0-006).
    private val fakePlaces = FakePlacesRepository()
    val placesRepository: PlacesRepository = fakePlaces
    val locationProvider: LocationProvider = FakeLocationProvider()
    val drivingRestrictions: DrivingRestrictions = CarDrivingRestrictions(context, appScope)

    // ponytail: DN-M3-001 swaps in IntentNavigationLauncher (geo: intent, application Context).
    val navigationLauncher: NavigationLauncher = FakeNavigationLauncher()
    val discoverUseCase = DiscoverUseCase(placesRepository, locationProvider, BasicRecommendationEngine())

    /** Debug builds only: serve the named [FakeScenario], for the docs/04 §7 emulator scenarios. */
    fun useFakeScenario(name: String?) {
        if (name != null && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            fakePlaces.scenario = FakeScenario.valueOf(name)
        }
    }
}
