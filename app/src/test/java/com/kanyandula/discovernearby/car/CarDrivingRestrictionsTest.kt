package com.kanyandula.discovernearby.car

import android.content.pm.PackageManager
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class CarDrivingRestrictionsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() = scope.cancel()

    /** Collect, let the main looper run the upstream, and return what the flow settled on. */
    private fun settledState(): DrivingState {
        val restrictions = CarDrivingRestrictions(app, scope)
        scope.launch { restrictions.state.collect {} }
        shadowOf(Looper.getMainLooper()).idle()
        return restrictions.state.value
    }

    private fun logged(text: String) = ShadowLog.getLogsForTag("DrivingRestrictions").any { text in it.msg }

    @Test
    fun withoutAutomotiveFeatureReportsTheFallback() {
        assertEquals(UNKNOWN_DRIVING_STATE, settledState())
        assertTrue("took the no-Car-service path", logged("unavailable"))
    }

    // An automotive device whose Car service does not answer: Car.createCar returns null (as it does here).
    @Test
    fun automotiveWithoutACarServiceReportsTheFallback() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true)
        assertEquals(UNKNOWN_DRIVING_STATE, settledState())
        assertTrue("took the no-Car-service path", logged("unavailable"))
    }
}
