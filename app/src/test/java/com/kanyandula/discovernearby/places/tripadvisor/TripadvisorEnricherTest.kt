package com.kanyandula.discovernearby.places.tripadvisor

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlacePhoto
import com.kanyandula.discovernearby.model.ProviderRating
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

private const val KEY = "TEST-KEY-not-real"

// Made-up places and URLs (public repo); the JSON has only the fields the enricher reads, in Terra's shape.
private const val NEARBY = """{"data": [
  {"location": {"id": 11, "names": [{"value": "Harbour Lane Deli", "primary": true}]}},
  {"location": {"id": 22, "names": [{"value": "Harbour Coffee Seaview", "primary": true}],
    "traveler_ratings": {"overall": {"rating": 4.5, "count": 312, "icon_url": "https://example.test/bubbles-4.5.svg"}}}}
]}"""
private const val PHOTOS = """{"data": [{"photo": {"original_size_url": "https://example.test/photo-22.jpg"},
  "user": {"username": "traveller"}}]}"""

class TripadvisorEnricherTest {

    private val requests = mutableListOf<Request>()
    private var respond: (Request) -> Response = { request ->
        if (request.url.encodedPath.endsWith("/photos")) reply(request, 200, PHOTOS) else reply(request, 200, NEARBY)
    }
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain -> chain.request().also { requests += it }.let(respond) }
        .build()
    private val enricher = TripadvisorEnricher(KEY, client)
    private val cafe = testPlace("HARBOUR COFFEE", "cafe")

    private fun reply(request: Request, code: Int, body: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    @Test
    fun theMatchedLocationGivesItsRatingAndFirstPhoto() = runBlocking {
        val enrichment = enricher.enrich(cafe, COFFEE)
        assertEquals(
            PlaceEnrichment(
                source = "Tripadvisor",
                photo = PlacePhoto("https://example.test/photo-22.jpg"),
                rating = ProviderRating(4.5, 312, "https://example.test/bubbles-4.5.svg"),
            ),
            enrichment,
        )
        assertEquals("/api/locations/22/photos", requests[1].url.encodedPath) // the matched one, not the nearest
    }

    @Test
    fun theNearbySearchAsksAroundThePlaceWithTheKeyInAHeader() = runBlocking {
        enricher.enrich(cafe, COFFEE)
        val url = requests[0].url
        assertEquals("terra.tripadvisor.com", url.host)
        assertEquals("/api/locations/nearby", url.encodedPath)
        assertEquals("${cafe.location.lat}", url.queryParameter("lat"))
        assertEquals("${cafe.location.lng}", url.queryParameter("lon"))
        assertEquals("0.2", url.queryParameter("radius"))
        assertEquals("KM", url.queryParameter("unit"))
        assertEquals("RESTAURANT", url.queryParameter("category")) // cafés are restaurants on Tripadvisor
        assertEquals("1", url.queryParameter("version"))
        assertNull(url.queryParameter("key")) // never in the URL
        assertEquals(KEY, requests[0].header("X-API-Key"))
    }

    @Test
    fun placesOtherThanFoodAreAttractions() = runBlocking {
        enricher.enrich(testPlace("Seaview Head", "viewpoint"), SCENIC)
        assertEquals("ATTRACTION", requests[0].url.queryParameter("category"))
    }

    @Test
    fun noNameMatchMeansNothingAndNoPhotoCall() = runBlocking {
        assertNull(enricher.enrich(testPlace("Marlowe Bakehouse", "cafe"), COFFEE))
        assertEquals(1, requests.size)
    }

    @Test
    fun aRefusedSearchIsNothing() = runBlocking {
        respond = { reply(it, 429, """{"message": "Too Many Requests"}""") }
        assertNull(enricher.enrich(cafe, COFFEE))
        assertEquals(1, requests.size) // no retry
    }

    @Test
    fun aFailedPhotoCallKeepsTheRating() = runBlocking {
        respond = { request ->
            if (request.url.encodedPath.endsWith("/photos")) reply(request, 500, "{}") else reply(request, 200, NEARBY)
        }
        val enrichment = enricher.enrich(cafe, COFFEE)
        assertNull(enrichment?.photo)
        assertEquals(4.5, enrichment?.rating?.value)
    }

    @Test
    fun aNetworkFailureOrBadBodyIsNothing() = runBlocking {
        respond = { throw IOException("offline") }
        assertNull(enricher.enrich(cafe, COFFEE))
        respond = { reply(it, 200, "not json") }
        assertNull(enricher.enrich(cafe, COFFEE))
    }
}
