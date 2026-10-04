package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.PlacesException
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.ProviderFailure
import kotlinx.coroutines.withTimeoutOrNull

/** Provider calls give up after this long (docs/03 §15); below the fake's SLOW_DELAY_MILLIS (docs/04 O). */
const val PROVIDER_TIMEOUT_MILLIS = 8_000L

/** Why a request produced no list (docs/03 §15). Network, provider and timeout stay distinct for logging. */
enum class DiscoverError { LocationUnavailable, NetworkUnavailable, ProviderFailure, Timeout }

/** The outcome of one discovery request; the ViewModel turns it into UI state. */
sealed interface DiscoverResult {
    data class Success(val context: DiscoveryContext, val recommendations: List<Recommendation>) : DiscoverResult
    data object PermissionRequired : DiscoverResult
    data class Failure(val error: DiscoverError) : DiscoverResult
}

/**
 * One discovery: read the location (only now, docs/03 §13), search the category's radius, rank. The
 * ranked list is complete; the ViewModel decides how much of it to show.
 */
class DiscoverUseCase(
    private val places: PlacesRepository,
    private val location: LocationProvider,
    private val engine: RecommendationEngine,
    private val timeoutMillis: Long = PROVIDER_TIMEOUT_MILLIS,
) {
    suspend operator fun invoke(requestId: Long, category: DiscoveryCategory): DiscoverResult =
        when (val fix = location.currentLocation()) {
            is LocationResult.Available ->
                search(DiscoveryContext(requestId, fix.point, category, System.currentTimeMillis()))
            LocationResult.PermissionMissing -> DiscoverResult.PermissionRequired
            LocationResult.Unavailable -> DiscoverResult.Failure(DiscoverError.LocationUnavailable)
        }

    private suspend fun search(context: DiscoveryContext): DiscoverResult = try {
        val radius = CategoryConfigs.getValue(context.category).radiusMeters
        val found = withTimeoutOrNull(timeoutMillis) {
            places.searchNearby(context.origin, context.category, radius)
        }
        if (found == null) {
            DiscoverResult.Failure(DiscoverError.Timeout)
        } else {
            DiscoverResult.Success(context, engine.rank(found, context))
        }
    } catch (e: PlacesException) {
        DiscoverResult.Failure(e.toDiscoverError())
    }
}

private fun PlacesException.toDiscoverError() = when (this) {
    is NetworkUnavailable -> DiscoverError.NetworkUnavailable
    is ProviderFailure -> DiscoverError.ProviderFailure
}
