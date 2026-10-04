package com.kanyandula.discovernearby.car

import android.content.pm.PackageManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
@RunWith(RobolectricTestRunner::class)
class CarDrivingRestrictionsTest {

    private val app = RuntimeEnvironment.getApplication()

    /**
     * Collect on the test scheduler (connect included) and return what the flow settled on.
     * runCurrent, not advanceUntilIdle: the latter does not drive backgroundScope work here.
     */
    private fun TestScope.settledState(): DrivingState {
        val restrictions = CarDrivingRestrictions(app, backgroundScope, StandardTestDispatcher(testScheduler))
        backgroundScope.launch { restrictions.state.collect {} }
        runCurrent()
        return restrictions.state.value
    }

    private fun logged(text: String) = ShadowLog.getLogsForTag("DrivingRestrictions").any { text in it.msg }

    @Test
    fun withoutAutomotiveFeatureReportsTheFallback() = runTest {
        assertEquals(UNKNOWN_DRIVING_STATE, settledState())
        assertTrue("took the no-Car-service path", logged("unavailable"))
    }

    // An automotive device whose Car service does not answer: Car.createCar returns null (as it does here).
    @Test
    fun automotiveWithoutACarServiceReportsTheFallback() = runTest {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true)
        assertEquals(UNKNOWN_DRIVING_STATE, settledState())
        assertTrue("took the no-Car-service path", logged("unavailable"))
    }
}
