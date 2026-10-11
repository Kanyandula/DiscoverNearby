package com.kanyandula.discovernearby.places.tripadvisor

import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.SCENIC
import com.kanyandula.discovernearby.discovery.testPlace
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PriceLevel
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
    "price_level": "Mid Range",
    "descriptions": [{"language": "fr", "value": "Un café."}, {"language": "en", "value": "A café by the harbour."}],
    "traveler_ratings": {"overall": {"rating": 4.5, "count": 312, "icon_url": "https://example.test/bubbles-4.5.png"}}}}
]}"""
private const val PHOTOS = """{"data": [{"photo": {"original_size_url": "https://example.test/photo-22.jpg"},
  "user": {"username": "traveller"}}]}"""

class TripadvisorEnricherTest {

    private val requests = mutableListOf<Request>()
    private var respond: (Request) -> Response = answering(NEARBY)
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain -> chain.request().also { requests += it }.let(respond) }
        .build()
    private val enricher = TripadvisorEnricher(KEY, client)
    private val cafe = testPlace("HARBOUR COFFEE", "cafe")

    /** The photos call gets [photos], the nearby search [nearby]. */
    private fun answering(nearby: String, photos: String = PHOTOS): (Request) -> Response = { request ->
        reply(request, 200, if (request.url.encodedPath.endsWith("/photos")) photos else nearby)
    }

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
                photoUrl = "https://example.test/photo-22.jpg?w=500&h=-1&s=1", // resized by the image server
                rating = ProviderRating(4.5, 312, "https://example.test/bubbles-4.5.png"),
                priceLevel = PriceLevel.MID,
                description = "A café by the harbour.",
            ),
            enrichment,
        )
        assertEquals("/api/locations/22/photos", requests[1].url.encodedPath) // the matched one, not the nearest
        assertEquals(2, requests.size) // price and description (DN-UX-005) come in the nearby search: no extra call
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
        assertNull(enrichment?.photoUrl)
        assertEquals(4.5, enrichment?.rating?.value)
    }

    // Tripadvisor's rules require its rating graphic, so a rating without one isn't shown; the photo still is.
    @Test
    fun aRatingWithoutItsGraphicIsDropped() = runBlocking {
        respond = answering(NEARBY.replace(""", "icon_url": "https://example.test/bubbles-4.5.png"""", ""))
        val enrichment = enricher.enrich(cafe, COFFEE)
        assertNull(enrichment?.rating)
        assertEquals("https://example.test/photo-22.jpg?w=500&h=-1&s=1", enrichment?.photoUrl)
    }

    @Test
    fun aNetworkFailureOrBadBodyIsNothing() = runBlocking {
        respond = { throw IOException("offline") }
        assertNull(enricher.enrich(cafe, COFFEE))
        respond = { reply(it, 200, "not json") }
        assertNull(enricher.enrich(cafe, COFFEE))
    }

    @Test
    fun eachPriceLevelMaps() {
        assertEquals(PriceLevel.CHEAP, priceLevel("Cheap Eats"))
        assertEquals(PriceLevel.MID, priceLevel("Mid Range"))
        assertEquals(PriceLevel.FINE, priceLevel("Fine Dining"))
    }

    // Review Focus 5: unknown or foreign text is left out, not guessed.
    @Test
    fun anUnknownPriceOrAForeignDescriptionIsLeftOut() = runBlocking {
        assertNull(priceLevel("$$ - $$$"))
        assertNull(priceLevel(null))
        respond = answering(
            NEARBY.replace("\"Mid Range\"", "\"Pricey\"").replace(
                """{"language": "en", "value": "A café by the harbour."}""",
                """{"language": "de", "value": "Ein Café."}""",
            ),
        )
        val enrichment = enricher.enrich(cafe, COFFEE)
        assertNull(enrichment?.priceLevel)
        assertNull(enrichment?.description)
    }
}
