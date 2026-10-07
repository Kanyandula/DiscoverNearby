package com.kanyandula.discovernearby.ui

import android.Manifest
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.kanyandula.discovernearby.location.LOCATION_PERMISSIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val activity: ComponentActivity get() = rule.activity

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

    private var returns = 0
    private lateinit var openSettings: () -> Unit

    private fun showOpenSettings() = rule.setContent {
        openSettings = rememberOpenAppSettings(onReturn = { returns++ })
    }

    /** The app goes behind Settings and comes back to the front, as Back from App info does. */
    private fun comeBack() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
    }

    // Review Focus 3: the second resume is unrelated (e.g. back from the navigation app), so nothing runs again.
    @Test
    fun returnFromSettingsRunsOnReturnOnce() {
        showOpenSettings()
        rule.runOnIdle { openSettings() }
        comeBack()
        assertEquals(1, returns)
        comeBack()
        assertEquals(1, returns)
    }

    @Test
    fun aResumeWithoutSettingsRunsNothing() {
        showOpenSettings()
        comeBack()
        assertEquals(0, returns)
    }

    // Review Focus 2: Settings didn't open, so the next resume isn't a return from it.
    @Test
    fun settingsThatDidNotOpenRunNothingOnReturn() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        showOpenSettings()
        rule.runOnIdle { openSettings() }
        comeBack()
        assertEquals(0, returns)
    }

    // Review Focus 1: the UI is rebuilt from saved state while Settings is in front (e.g. process death).
    @Test
    fun recreatedWhileInSettingsStillRunsOnReturn() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { openSettings = rememberOpenAppSettings(onReturn = { returns++ }) }
        rule.runOnIdle { openSettings() }
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        restoration.emulateSavedInstanceStateRestore()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        assertEquals(1, returns)
    }
}
