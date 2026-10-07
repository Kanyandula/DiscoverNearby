package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.places.NetworkUnavailable
import com.kanyandula.discovernearby.places.PlacesException
import com.kanyandula.discovernearby.places.ProviderFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val KEY = "TEST-KEY-not-real"
private val GREYSTONES = GeoPoint(53.144, -6.0633)
private const val ITEM = """{"id": "here:pds:place:test-1", "title": "Test Coffee",
    "position": {"lat": 53.1445, "lng": -6.0631}, "categories": [{"id": "100-1100-0010", "primary": true}],
    "openingHours": [{"text": ["Mon-Sun: 08:00 - 18:00"], "isOpen": true}]}"""

@RunWith(RobolectricTestRunner::class)
class HerePlacesRepositoryTest {

    private val requests = mutableListOf<Request>()
    private var respond: (Request) -> Response = { reply(it, 200, """{"items": [$ITEM]}""") }
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain -> chain.request().also { requests += it }.let(respond) }
        .build()

    private fun reply(request: Request, code: Int, body: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private fun failure(block: suspend () -> Unit): PlacesException? = runBlocking {
        try {
            block()
            null
        } catch (e: PlacesException) {
            e
        }
    }

    private fun searchFailure(key: String = KEY) = failure {
        HerePlacesRepository(key, client).searchNearby(GREYSTONES, DiscoveryCategory.COFFEE, 1)
    }

    @Test
    fun browseAsksForTheCategoryAroundTheOrigin() = runBlocking {
        HerePlacesRepository(KEY, client).searchNearby(GREYSTONES, DiscoveryCategory.FAMILY, 15_000)
        val url = requests.single().url
        assertEquals("browse.search.hereapi.com", url.host)
        assertEquals("53.144,-6.0633", url.queryParameter("at"))
        assertEquals("circle:53.144,-6.0633;r=15000", url.queryParameter("in"))
        assertEquals("550-5520-0208,550-5520-0207,550-5520-0357,300-3100-0027", url.queryParameter("categories"))
        assertEquals("20", url.queryParameter("limit"))
        assertEquals(KEY, url.queryParameter("apiKey"))
    }

    // A comma-decimal locale must not reach the coordinates.
    @Test
    fun coordinatesUseDotsWhateverTheDefaultLocale() = runBlocking {
        val default = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            HerePlacesRepository(KEY, client).searchNearby(GREYSTONES, DiscoveryCategory.COFFEE, 5_000)
        } finally {
            Locale.setDefault(default)
        }
        assertEquals("53.144,-6.0633", requests.single().url.queryParameter("at"))
    }

    @Test
    fun browseItemsBecomePlaces() = runBlocking {
        val places = HerePlacesRepository(KEY, client).searchNearby(GREYSTONES, DiscoveryCategory.COFFEE, 5_000)
        assertEquals(listOf("Test Coffee"), places.map { it.name })
    }

    @Test
    fun detailsLookUpThePlaceById() = runBlocking {
        respond = { reply(it, 200, ITEM) }
        val details = HerePlacesRepository(KEY, client).getPlaceDetails("here:pds:place:test-1")
        val url = requests.single().url
        assertEquals("lookup.search.hereapi.com", url.host)
        assertEquals("here:pds:place:test-1", url.queryParameter("id"))
        assertEquals("Mon-Sun: 08:00 - 18:00", details.openingSummary)
    }

    @Test
    fun detailsWithoutAUsablePlaceAreAProviderFailure() {
        respond = { reply(it, 200, """{"id": "x"}""") }
        assertTrue(failure { HerePlacesRepository(KEY, client).getPlaceDetails("x") } is ProviderFailure)
    }

    // Review Focus 4
    @Test
    fun aMissingKeyFailsWithoutARequest() {
        assertTrue(searchFailure(key = "") is ProviderFailure)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun httpErrorsAreProviderFailures() {
        listOf(400, 401, 403, 429, 500, 503).forEach { code ->
            respond = { reply(it, code, """{"error": "x"}""") }
            val error = searchFailure()
            assertTrue("HTTP $code -> $error", error is ProviderFailure)
        }
    }

    @Test
    fun anUnreadableBodyIsAProviderFailure() {
        respond = { reply(it, 200, "<html>not json</html>") }
        assertTrue(searchFailure() is ProviderFailure)
    }

    @Test
    fun aCallThatCannotCompleteIsNetworkUnavailable() {
        respond = { throw IOException("timeout") }
        assertTrue(searchFailure() is NetworkUnavailable)
    }

    // Review Focus 5: the URL holds the key and the origin; an error that echoes it must not carry them on.
    @Test
    fun failuresCarryNoKeyOrOrigin() {
        respond = { reply(it, 401, """{"error": "Unauthorized", "request": "${it.url}"}""") }
        val error = checkNotNull(searchFailure())
        assertFalse(error.toString().contains(KEY))
        assertFalse(error.toString().contains("53.144"))
        assertNull(error.cause)
    }

    // Review Focus 3: Back or a category switch cancels the use case's coroutine; the HTTP call must stop too.
    @Test
    fun cancellingTheSearchCancelsTheCall() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val call = AtomicReference<Call>()
        val blocking = OkHttpClient.Builder().addInterceptor { chain ->
            call.set(chain.call())
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            reply(chain.request(), 200, """{"items": []}""")
        }.build()
        val search = launch(Dispatchers.IO) {
            HerePlacesRepository(KEY, blocking).searchNearby(GREYSTONES, DiscoveryCategory.COFFEE, 5_000)
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        search.cancelAndJoin()
        assertTrue(call.get().isCanceled())
        release.countDown()
    }

    // Final review: the body is read on OkHttp's thread, never the caller's (Main, in the app), so a read can't block
    // or crash it, and cancelling the call stops the read too.
    @Test
    fun theBodyIsReadOffTheCallersThread() {
        val readOn = AtomicReference<Thread>()
        respond = { request ->
            val body = object : ResponseBody() {
                override fun contentType() = "application/json".toMediaType()

                override fun contentLength() = -1L

                override fun source(): BufferedSource {
                    readOn.set(Thread.currentThread())
                    return Buffer().writeUtf8("""{"items": []}""")
                }
            }
            reply(request, 200, "").newBuilder().body(body).build()
        }
        val caller = Executors.newSingleThreadExecutor()
        try {
            val callerThread = caller.submit<Thread> { Thread.currentThread() }.get()
            runBlocking(caller.asCoroutineDispatcher()) {
                HerePlacesRepository(KEY, client).searchNearby(GREYSTONES, DiscoveryCategory.COFFEE, 1)
            }
            assertNotEquals(callerThread, readOn.get())
        } finally {
            caller.shutdown()
        }
    }
}
