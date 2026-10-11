package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.places.CAR_PARK_RADIUS_METERS
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.ProviderFailure
import com.kanyandula.discovernearby.places.ProviderJson
import com.kanyandula.discovernearby.places.awaitBody
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

// In a dense city centre HERE's nearest 20 sat within about 200 m, too few to survive the closed filter and the
// per-kind caps (DN-M2-003); 100 is its maximum.
private const val SEARCH_LIMIT = 50

// The app's only UI language (strings.xml). Without it HERE answers in the place's own language, such as Irish.
private const val RESPONSE_LANGUAGE = "en"
private val BROWSE_URL = "https://browse.search.hereapi.com/v1/browse".toHttpUrl()
private val LOOKUP_URL = "https://lookup.search.hereapi.com/v1/lookup".toHttpUrl()

// HERE's parking facilities: garages, lots, park-and-ride (Places category system, ADR-001 [H3]).
private const val CAR_PARK_CATEGORY = "800-8500"

/**
 * HERE Geocoding & Search v7 behind [PlacesRepository] (ADR-001: provisionally selected; Legal before production).
 * REST through OkHttp, no SDK. Search results are not stored (ADR-004). The URL carries [apiKey] and the origin,
 * so it is never logged, and failures carry nothing from the call.
 */
class HerePlacesRepository(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : PlacesRepository {

    override suspend fun searchNearby(
        origin: GeoPoint,
        category: DiscoveryCategory,
        radiusMeters: Int,
    ): List<PlaceSummary> =
        browse(origin, radiusMeters, HERE_CATEGORIES.getValue(category), SEARCH_LIMIT).mapNotNull { it.toSummary() }

    override suspend fun getPlaceDetails(placeId: String): PlaceDetails =
        get<HereItem>(LOOKUP_URL.newBuilder().addQueryParameter("id", placeId).build()).toDetails()
            ?: throw ProviderFailure()

    override suspend fun hasCarParkNear(location: GeoPoint): Boolean =
        browse(location, CAR_PARK_RADIUS_METERS, CAR_PARK_CATEGORY, limit = 1).isNotEmpty()

    /** HERE's places of [categories] within [radiusMeters] of [at], nearest first. */
    private suspend fun browse(at: GeoPoint, radiusMeters: Int, categories: String, limit: Int): List<HereItem> {
        val url = BROWSE_URL.newBuilder()
            .addQueryParameter("at", at.latLng())
            .addQueryParameter("in", "circle:${at.latLng()};r=$radiusMeters")
            .addQueryParameter("categories", categories)
            .addQueryParameter("limit", "$limit")
            .build()
        return get<HereBrowseResponse>(url).items
    }

    /** The decoded body. No key, an HTTP error or an unreadable body is a [ProviderFailure]. */
    private suspend inline fun <reified T> get(url: HttpUrl): T {
        val withKey = url.newBuilder().addQueryParameter("lang", RESPONSE_LANGUAGE).addQueryParameter("apiKey", apiKey)
        val body = if (apiKey.isBlank()) null else fetch(withKey.build())
        return try {
            ProviderJson.decodeFromString<T>(body ?: throw ProviderFailure())
        } catch (ignored: SerializationException) {
            throw ProviderFailure()
        }
    }

    /** A successful response's body, or null for an HTTP error. A call that can't complete is [NetworkUnavailable]. */
    private suspend fun fetch(url: HttpUrl): String? = try {
        client.newCall(Request(url)).awaitBody()
    } catch (ignored: IOException) {
        throw NetworkUnavailable()
    }
}
