package com.kanyandula.discovernearby.ui

import android.Manifest
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.kanyandula.discovernearby.location.LOCATION_PERMISSIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test
    fun opensThisAppsDetailsPage() {
        assertTrue(openAppSettings(activity))
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:${activity.packageName}", started.dataString)
    }

    // Review Focus 5: nothing on the system can show it, so return false and don't crash.
    @Test
    fun openingWithNothingToShowItReturnsFalse() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        assertFalse(openAppSettings(activity))
    }

    @Test
    fun aRefusalWithNoRationaleIsForGood() {
        assertTrue(refusedForGood(activity, refused))
        shadowOf(activity.packageManager)
            .setShouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION, true)
        assertFalse(refusedForGood(activity, refused))
    }

    @Test
    fun anApproximateGrantIsNotARefusal() {
        val result = mapOf(
            Manifest.permission.ACCESS_FINE_LOCATION to false,
            Manifest.permission.ACCESS_COARSE_LOCATION to true,
        )
        assertFalse(refusedForGood(activity, result))
    }

    // Final review: a cancelled or overlapping request (Grant pressed twice) comes back empty; it isn't a refusal.
    @Test
    fun anEmptyResultIsNotARefusal() {
        assertFalse(refusedForGood(activity, emptyMap()))
    }

    private val refused = LOCATION_PERMISSIONS.associateWith { false }
}
