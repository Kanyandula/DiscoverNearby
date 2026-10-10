package com.kanyandula.discovernearby.places.tripadvisor

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.ProviderRating
import com.kanyandula.discovernearby.places.PlaceEnricher
import com.kanyandula.discovernearby.places.ProviderJson
import com.kanyandula.discovernearby.places.awaitBody
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private val API_URL = "https://terra.tripadvisor.com/api".toHttpUrl()
private const val SEARCH_RADIUS_KM = "0.2" // around HERE's position: the same place, not its neighbours
private const val NEARBY_RESULTS = 10
private const val CALL_TIMEOUT_MILLIS = 4_000L

// The image server's resizing (Terra docs): the Details panel's width, which the row's smaller slot reuses. The
// original can be 2,000 px wide and ten times the bytes.
private const val PHOTO_SIZE = "?w=800&h=-1&s=1"

/**
 * Tripadvisor (Terra) photo and rating for a HERE place (DN-UX-004, ADR-003): a nearby search around the place, a
 * strict name match ([nameMatch]), then the location's first photo. The key goes in the `X-API-Key` header and is
 * never logged. Any failure is no answer for that part, and nothing is retried or stored (ADR-003).
 */
class TripadvisorEnricher(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : PlaceEnricher {

    override suspend fun enrich(place: PlaceSummary, category: DiscoveryCategory): PlaceEnrichment? {
        val location = call { match(place, category) } ?: return null
        // Tripadvisor's display rules require its rating graphic: no graphic, no rating.
        val rating = location.travelerRatings?.overall?.let { overall ->
            if (overall.rating != null && overall.iconUrl != null) {
                ProviderRating(overall.rating, overall.count, overall.iconUrl)
            } else {
                null
            }
        }
        // Its own call and timeout, so a slow photo keeps the rating.
        val photoUrl = location.id?.let { id -> call { firstPhotoUrl(id) } }
        return if (photoUrl == null && rating == null) null else PlaceEnrichment(photoUrl, rating)
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
            .map { location -> location to (location.names.maxOfOrNull { nameMatch(place.name, it.value) } ?: 0) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }?.first
    }

    private suspend fun firstPhotoUrl(locationId: Long): String? {
        val url = API_URL.newBuilder().addPathSegments("locations/$locationId/photos")
            .addQueryParameter("size", "1")
            .build()
        val original = get<PhotosResponse>(url)?.data?.firstOrNull()?.photo?.url ?: return null
        return if ('?' in original) original else original + PHOTO_SIZE
    }

    /** [block]'s answer, or null on a timeout, a network failure, an HTTP error (a 429 included) or a bad body. */
    private suspend fun <T> call(block: suspend () -> T?): T? = withTimeoutOrNull(CALL_TIMEOUT_MILLIS) {
        try {
            block()
        } catch (ignored: IOException) {
            null
        } catch (ignored: SerializationException) {
            null
        }
    }

    /** The decoded body, or null for an HTTP error. */
    private suspend inline fun <reified T> get(url: HttpUrl): T? {
        val request = Request.Builder()
            .url(url.newBuilder().addQueryParameter("version", "1").build())
            .header("X-API-Key", apiKey)
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).awaitBody()?.let { ProviderJson.decodeFromString<T>(it) }
    }
}

// Tripadvisor files cafés under restaurants; everything else the app looks for is an attraction.
private val DiscoveryCategory.eatsOut get() = this == DiscoveryCategory.COFFEE || this == DiscoveryCategory.FOOD
