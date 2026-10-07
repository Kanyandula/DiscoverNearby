package com.kanyandula.discovernearby

import android.content.Context
import android.content.pm.ApplicationInfo
import com.kanyandula.discovernearby.car.CarDrivingRestrictions
import com.kanyandula.discovernearby.car.DrivingRestrictions
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.location.AndroidLocationProvider
import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.navigation.IntentNavigationLauncher
import com.kanyandula.discovernearby.navigation.NavigationLauncher
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import com.kanyandula.discovernearby.places.fake.FakeScenario
import com.kanyandula.discovernearby.places.here.HerePlacesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The single wiring point (docs/03 §3): every app-scoped dependency is constructed here by hand.
 */
class AppContainer(context: Context, private val hereApiKey: String) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val applicationInfo = context.applicationInfo

    private val fakePlaces = FakePlacesRepository()
    private var fakesRequested = false

    /**
     * Live HERE data when a key is configured (DN-M1-002; ADR-001: provisionally selected). The fakes when there is
     * no key (CI, Robolectric) or when a debug launch names a scenario. Chosen on first use, after MainActivity has
     * applied any scenario: `am start -S` starts a fresh process for each scenario run.
     */
    val placesRepository: PlacesRepository by lazy {
        if (hereApiKey.isBlank() || fakesRequested) fakePlaces else HerePlacesRepository(hereApiKey)
    }
    val locationProvider: LocationProvider = AndroidLocationProvider(context)
    val drivingRestrictions: DrivingRestrictions = CarDrivingRestrictions(context, appScope)

    val navigationLauncher: NavigationLauncher = IntentNavigationLauncher(context.applicationContext)
    val discoverUseCase by lazy { DiscoverUseCase(placesRepository, locationProvider, BasicRecommendationEngine()) }

    /** Debug builds only: serve the named [FakeScenario], for the docs/04 §7 emulator scenarios. */
    fun useFakeScenario(name: String?) {
        if (name != null && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            fakePlaces.scenario = FakeScenario.valueOf(name)
            fakesRequested = true
        }
    }
}
