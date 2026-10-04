package com.kanyandula.discovernearby.location

import android.Manifest
import android.os.Looper
import com.kanyandula.discovernearby.places.fake.TestLocation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy, getCompleted
@RunWith(RobolectricTestRunner::class)
class AndroidLocationProviderTest {

    private val provider = AndroidLocationProvider(RuntimeEnvironment.getApplication())
    private val greystones = TestLocation.GREYSTONES.point

    private companion object {
        const val MINUTE = 60_000L
    }

    /** Starts a read and lets the location callback run; the result is there unless it is still waiting. */
    private fun TestScope.read(): Deferred<LocationResult> = async { provider.currentLocation() }.also {
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    @Test
    fun withoutPermissionItIsPermissionMissing() = runTest {
        deviceAt(greystones, permissions = emptyArray())
        assertEquals(LocationResult.PermissionMissing, read().getCompleted())
    }

    @Test
    fun readsTheCurrentFix() = runTest {
        deviceAt(greystones)
        assertEquals(LocationResult.Available(greystones), read().getCompleted())
    }

    // Android 12+ lets the user grant approximate location only; discovery must still work.
    @Test
    fun approximateOnlyStillReadsTheLocation() = runTest {
        deviceAt(greystones, permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(LocationResult.Available(greystones), read().getCompleted())
    }

    // Approximate only, with no recent fix to coarsen: Unavailable at once, not after the timeout.
    @Test
    fun approximateWithoutARecentFixIsUnavailableAtOnce() = runTest {
        deviceAt(point = null, permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(LocationResult.Unavailable, read().getCompleted())
    }

    // The platform refreshes an approximate-only app's coarse fix only every 10 min, so a 12-min-old one is current.
    @Test
    fun approximateAcceptsTheLatestCoarseFix() = runTest {
        val approximateOnly = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        deviceAt(greystones, permissions = approximateOnly, fixAgeMillis = 12 * MINUTE)
        assertEquals(LocationResult.Available(greystones), read().getCompleted())
    }

    // A precise read that gets no current fix may fall back only to a fix a couple of minutes old, not one from
    // several kilometres back.
    @Test
    fun preciseFallsBackOnlyToAFreshFix() = runTest {
        deviceAt(greystones, fixAgeMillis = 5 * MINUTE)
        val result = read()
        advanceTimeBy(LOCATION_TIMEOUT_MILLIS + 1)
        assertEquals(LocationResult.Unavailable, result.getCompleted())
    }

    @Test
    fun locationOffIsUnavailable() = runTest {
        deviceAt(greystones, locationOn = false)
        assertEquals(LocationResult.Unavailable, read().getCompleted())
    }

    // docs/02 §11: no fix ends in Location unavailable (Retry, Back), never an endless Loading.
    @Test
    fun noFixInTimeIsUnavailable() = runTest {
        deviceAt(point = null)
        val result = read()
        assertFalse(result.isCompleted)
        advanceTimeBy(LOCATION_TIMEOUT_MILLIS + 1)
        assertEquals(LocationResult.Unavailable, result.getCompleted())
    }
}
