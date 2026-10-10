package com.kanyandula.discovernearby.places.tripadvisor

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlacePhoto
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.ProviderRating
import com.kanyandula.discovernearby.places.PlaceEnricher
import com.kanyandula.discovernearby.places.awaitBody
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal const val TRIPADVISOR = "Tripadvisor"
private val API_URL = "https://terra.tripadvisor.com/api".toHttpUrl()
private const val SEARCH_RADIUS_KM = "0.2" // around HERE's position: the same place, not its neighbours
private const val NEARBY_RESULTS = 10
private const val ENRICH_TIMEOUT_MILLIS = 6_000L

/**
 * Tripadvisor (Terra) photos and ratings for a HERE place (DN-UX-004, ADR-003): a nearby search around the place,
 * a name match ([nameMatch]), then the location's first photo. Two calls a place. Nothing is kept: Tripadvisor's
 * caching policy allows only its location ID, and keeping that would save no call (the rating needs the search).
 * The key goes in the `X-API-Key` header and is never logged. Any failure, HTTP 429 included, is null: the row keeps
 * its artwork and nothing is retried (the nearby search allows one call a second, in bursts of five).
 */
class TripadvisorEnricher(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : PlaceEnricher {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun enrich(place: PlaceSummary, category: DiscoveryCategory): PlaceEnrichment? =
        withTimeoutOrNull(ENRICH_TIMEOUT_MILLIS) {
            try {
                val location = match(place, category) ?: return@withTimeoutOrNull null
                val photo = location.id?.let { firstPhoto(it) }
                val rating = location.travelerRatings?.overall?.let { overall ->
                    overall.rating?.let { ProviderRating(it, overall.count, overall.iconUrl) }
                }
                if (photo == null && rating == null) null else PlaceEnrichment(TRIPADVISOR, photo, rating)
            } catch (ignored: IOException) {
                null
            } catch (ignored: SerializationException) {
                null
            }
        }

    /** The location within the radius whose name best matches the place's (the nearer on a tie), or null. */
    private suspend fun match(place: PlaceSummary, category: DiscoveryCategory): TripadvisorLocation? {
        val url = API_URL.newBuilder().addPathSegments("locations/nearby")
            .addQueryParameter("lat", "${place.location.lat}")
            .addQueryParameter("lon", "${place.location.lng}")
            .addQueryParameter("radius", SEARCH_RADIUS_KM)
            .addQueryParameter("unit", "KM")
            .addQueryParameter("category", if (category.eatsOut) "RESTAURANT" else "ATTRACTION")
            .addQueryParameter("sort", "distance,asc")
            .addQueryParameter("size", "$NEARBY_RESULTS")
            .build()
        val nearby = get<NearbyResponse>(url) ?: return null
        // Results come nearest first, and maxByOrNull keeps the first of equal scores.
        return nearby.data.mapNotNull { it.location }
            .map { location -> location to score(place, location) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }?.first
    }

    private fun score(place: PlaceSummary, location: TripadvisorLocation) =
        location.names.maxOfOrNull { nameMatch(place.name, it.value.orEmpty()) } ?: 0

    private suspend fun firstPhoto(locationId: Long): PlacePhoto? {
        val url = API_URL.newBuilder().addPathSegments("locations/$locationId/photos")
            .addQueryParameter("size", "1")
            .build()
        return get<PhotosResponse>(url)?.data?.firstOrNull()?.photo?.url?.let { PlacePhoto(it) }
    }

    /** The decoded body, or null for an HTTP error (a 429 included). */
    private suspend inline fun <reified T> get(url: HttpUrl): T? {
        val request = Request.Builder()
            .url(url.newBuilder().addQueryParameter("version", "1").build())
            .header("X-API-Key", apiKey)
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).awaitBody()?.let { json.decodeFromString<T>(it) }
    }
}

// Tripadvisor files cafés under restaurants; everything else the app looks for is an attraction.
private val DiscoveryCategory.eatsOut get() = this == DiscoveryCategory.COFFEE || this == DiscoveryCategory.FOOD
