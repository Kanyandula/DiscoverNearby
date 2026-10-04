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
