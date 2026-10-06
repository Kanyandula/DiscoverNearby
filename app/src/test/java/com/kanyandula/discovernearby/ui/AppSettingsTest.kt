package com.kanyandula.discovernearby.ui

import android.Manifest
import android.provider.Settings
import androidx.activity.ComponentActivity
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
    fun neverAsksAgainWhenNoRationaleIsOffered() {
        assertTrue(neverAsksAgain(activity))
        shadowOf(activity.packageManager)
            .setShouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION, true)
        assertFalse(neverAsksAgain(activity))
    }
}
