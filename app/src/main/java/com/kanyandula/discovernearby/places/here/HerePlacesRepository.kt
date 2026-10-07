package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.PlacesRepository
import com.kanyandula.discovernearby.places.ProviderFailure
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val SEARCH_LIMIT = 20
private val BROWSE_URL = "https://browse.search.hereapi.com/v1/browse".toHttpUrl()
private val LOOKUP_URL = "https://lookup.search.hereapi.com/v1/lookup".toHttpUrl()

/**
 * HERE Geocoding & Search v7 behind [PlacesRepository] (ADR-001: provisionally selected; Legal before production).
 * REST through OkHttp, no SDK. Nothing is cached or stored (ADR-001 V6b). The URL carries [apiKey] and the origin,
 * so it is never logged, and failures carry nothing from the call.
 */
class HerePlacesRepository(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : PlacesRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun searchNearby(
        origin: GeoPoint,
        category: DiscoveryCategory,
        radiusMeters: Int,
    ): List<PlaceSummary> {
        val url = BROWSE_URL.newBuilder()
            .addQueryParameter("at", "${origin.lat},${origin.lng}")
            .addQueryParameter("in", "circle:${origin.lat},${origin.lng};r=$radiusMeters")
            .addQueryParameter("categories", HERE_CATEGORIES.getValue(category))
            .addQueryParameter("limit", "$SEARCH_LIMIT")
            .build()
        return get<HereBrowseResponse>(url).items.mapNotNull { it.toSummary() }
    }

    override suspend fun getPlaceDetails(placeId: String): PlaceDetails =
        get<HereItem>(LOOKUP_URL.newBuilder().addQueryParameter("id", placeId).build()).toDetails()
            ?: throw ProviderFailure()

    /** The decoded body. No key, an HTTP error or an unreadable body is a [ProviderFailure]. */
    private suspend inline fun <reified T> get(url: HttpUrl): T {
        val body = if (apiKey.isBlank()) null else fetch(url.newBuilder().addQueryParameter("apiKey", apiKey).build())
        return body?.let { decode<T>(it) } ?: throw ProviderFailure()
    }

    private inline fun <reified T> decode(body: String): T? = try {
        json.decodeFromString<T>(body)
    } catch (ignored: SerializationException) {
        null
    }

    /** A successful response's body, or null for an HTTP error. A call that can't complete is [NetworkUnavailable]. */
    private suspend fun fetch(url: HttpUrl): String? = try {
        client.newCall(Request(url)).awaitBody()
    } catch (ignored: IOException) {
        throw NetworkUnavailable()
    }
}

/**
 * The body of a successful response, or null for an HTTP error. The body is read on OkHttp's thread, so the caller
 * (Main, in the app) never blocks on it, and cancelling the coroutine cancels the call, body read included (stale
 * requests, docs/03 §15). A failed read surfaces as an [IOException].
 */
private suspend fun Call.awaitBody(): String? = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            runCatching { response.use { if (it.isSuccessful) it.body.string() else null } }
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it as? IOException ?: IOException("Body read failed")) }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
}
