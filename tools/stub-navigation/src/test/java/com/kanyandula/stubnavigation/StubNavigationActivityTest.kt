package com.kanyandula.stubnavigation

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLog

// The app hands off geo:%.6f,%.6f (Locale.US); the stub must show and log exactly that (docs/04 E).
private const val GREYSTONES = "geo:53.144000,-6.063300"
private const val WICKLOW = "geo:52.980000,-6.044000"

// AAOS's navigation intent, which the app sends first (DN-M3-002); the stub also takes a plain geo: view.
private const val NAVIGATE = "androidx.car.app.action.NAVIGATE"

@RunWith(RobolectricTestRunner::class)
class StubNavigationActivityTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val app = RuntimeEnvironment.getApplication()

    private fun intent(uri: String?, action: String = Intent.ACTION_VIEW) =
        Intent(app, StubNavigationActivity::class.java).setAction(action).setData(uri?.let(Uri::parse))

    private fun launch(uri: String?, action: String = Intent.ACTION_VIEW) =
        Robolectric.buildActivity(StubNavigationActivity::class.java, intent(uri, action)).setup()

    private fun logged() = ShadowLog.getLogsForTag("StubNav").map { it.msg }

    // Another app's hand-off names no package (CLAUDE.md), so the implicit intent must resolve here (docs/03 §11).
    @Test
    fun anImplicitGeoViewIntentResolvesToTheStub() {
        val handoff = Intent(Intent.ACTION_VIEW, Uri.parse(GREYSTONES))
        val handlers = app.packageManager.queryIntentActivities(handoff, PackageManager.MATCH_DEFAULT_ONLY)
        assertEquals(listOf(StubNavigationActivity::class.java.name), handlers.map { it.activityInfo.name })
        assertTrue("other apps must be able to start it", handlers.single().activityInfo.exported)
    }

    // The NAVIGATE filter sits on an alias of the same activity, so `pm disable` on the alias exercises the app's
    // fallback to a geo: view (docs/04 E) while the stub keeps answering it.
    @Test
    fun anImplicitNavigateIntentResolvesToTheStubThroughItsAlias() {
        val handoff = Intent(NAVIGATE, Uri.parse(GREYSTONES))
        val handler = app.packageManager.queryIntentActivities(handoff, PackageManager.MATCH_DEFAULT_ONLY).single()
        assertEquals(StubNavigationActivity::class.java.name, handler.activityInfo.targetActivity)
        assertTrue("other apps must be able to start it", handler.activityInfo.exported)
        val alias = ComponentName(app.packageName, handler.activityInfo.name)
        assertTrue(app.packageManager.getActivityInfo(alias, PackageManager.GET_META_DATA).metaData
            .getBoolean("distractionOptimized"))
    }

    // Android 13 lets the shell disable a component (the alias, docs/04 E) only in a test-only app.
    @Test
    fun isTestOnlySoTheShellCanDisableTheAlias() {
        assertTrue(app.applicationInfo.flags and ApplicationInfo.FLAG_TEST_ONLY != 0)
    }

    // Shown while driving; one instance, so a later hand-off reaches the open stub instead of being dropped.
    @Test
    fun showsWhileDrivingAndKeepsOneInstance() {
        val stub = ComponentName(app, StubNavigationActivity::class.java)
        val info = app.packageManager.getActivityInfo(stub, PackageManager.GET_META_DATA)
        assertTrue(info.metaData.getBoolean("distractionOptimized"))
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
    }

    private fun assertShowsAndLogs(action: String) {
        launch(GREYSTONES, action)
        rule.onNodeWithText(GREYSTONES).assertIsDisplayed()
        rule.onNodeWithText("via $action").assertIsDisplayed()
        assertEquals(listOf("received $GREYSTONES via $action"), logged())
    }

    @Test
    fun showsAndLogsANavigateRequest() = assertShowsAndLogs(NAVIGATE)

    @Test
    fun showsAndLogsAGeoView() = assertShowsAndLogs(Intent.ACTION_VIEW)

    // A second hand-off while the stub is still open replaces the first, on screen and in the log.
    @Test
    fun aLaterHandOffReplacesTheFirst() {
        val stub = launch(GREYSTONES).newIntent(intent(WICKLOW))
        rule.onNodeWithText(WICKLOW).assertIsDisplayed()
        rule.onNodeWithText(GREYSTONES).assertDoesNotExist()
        val view = Intent.ACTION_VIEW
        assertEquals(listOf("received $GREYSTONES via $view", "received $WICKLOW via $view"), logged())
        // A day/night relaunch rebuilds from the activity's intent, so it must be the latest hand-off.
        assertEquals(WICKLOW, stub.get().intent.dataString)
    }

    @Test
    fun openedWithoutADestinationSaysSo() {
        launch(uri = null)
        rule.onNodeWithText("No destination received").assertIsDisplayed()
        assertEquals(emptyList<String>(), logged())
    }
}
