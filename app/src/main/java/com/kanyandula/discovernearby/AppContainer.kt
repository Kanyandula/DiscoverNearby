package com.kanyandula.discovernearby

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.kanyandula.discovernearby.car.CarDrivingRestrictions
import com.kanyandula.discovernearby.car.DrivingRestrictions
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.location.AndroidLocationProvider
import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.navigation.IntentNavigationLauncher
import com.kanyandula.discovernearby.navigation.NavigationLauncher
import com.kanyandula.discovernearby.places.PlaceEnricher
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import com.kanyandula.discovernearby.places.fake.FakeScenario
import com.kanyandula.discovernearby.places.here.HerePlacesRepository
import com.kanyandula.discovernearby.places.tripadvisor.TripadvisorEnricher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The single wiring point (docs/03 §3): every app-scoped dependency is constructed here by hand.
 */
class AppContainer(context: Context, private val hereApiKey: String, private val tripadvisorApiKey: String = "") {
    init {
        // Without a key the app serves the fakes; say so once per process, so a keyless build isn't taken for live
        // data. A requested scenario (useFakeScenario) is deliberate, so it isn't logged.
        if (hereApiKey.isBlank()) Log.i(TAG, "No HERE key: serving the fake places")
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val applicationInfo = context.applicationInfo

    private val fakePlaces = FakePlacesRepository()
    private var fakesRequested = false
    private val livePlaces by lazy { HerePlacesRepository(hereApiKey) }

    /**
     * Live HERE data when a key is configured (DN-M1-002; ADR-001: provisionally selected). The fakes when there is
     * no key (CI, Robolectric) or once a debug launch names a scenario, warm relaunches included.
     */
    val placesRepository: PlacesRepository
        get() = if (servesFakes) fakePlaces else livePlaces

    private val servesFakes get() = hereApiKey.isBlank() || fakesRequested
    private val tripadvisor by lazy { TripadvisorEnricher(tripadvisorApiKey) }

    /**
     * Tripadvisor's photos and ratings for live HERE places when its key is set (DN-UX-004, ADR-003); nothing for the
     * fakes, so development, CI and Robolectric make no Tripadvisor call. Decided per call, like the places source.
     */
    val placeEnricher = PlaceEnricher { place, category ->
        if (servesFakes || tripadvisorApiKey.isBlank()) null else tripadvisor.enrich(place, category)
    }

    // The use case asks for the current source on every call, so a scenario named later still applies.
    private val selectedPlaces = object : PlacesRepository {
        override suspend fun searchNearby(origin: GeoPoint, category: DiscoveryCategory, radiusMeters: Int) =
            placesRepository.searchNearby(origin, category, radiusMeters)

        override suspend fun getPlaceDetails(placeId: String) = placesRepository.getPlaceDetails(placeId)
    }
    val locationProvider: LocationProvider = AndroidLocationProvider(context)
    val drivingRestrictions: DrivingRestrictions = CarDrivingRestrictions(context, appScope)

    val navigationLauncher: NavigationLauncher = IntentNavigationLauncher(context.applicationContext)
    val discoverUseCase = DiscoverUseCase(selectedPlaces, locationProvider, BasicRecommendationEngine())

    /** Debug builds only: serve the named [FakeScenario], for the docs/04 §7 emulator scenarios. */
    fun useFakeScenario(name: String?) {
        if (name != null && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            fakePlaces.scenario = FakeScenario.valueOf(name)
            fakesRequested = true
        }
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}
