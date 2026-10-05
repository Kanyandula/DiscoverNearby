package com.kanyandula.stubnavigation

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
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

@RunWith(RobolectricTestRunner::class)
class StubNavigationActivityTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val app = RuntimeEnvironment.getApplication()

    private fun intent(uri: String?) = Intent(app, StubNavigationActivity::class.java).setData(uri?.let(Uri::parse))

    private fun launch(uri: String?) =
        Robolectric.buildActivity(StubNavigationActivity::class.java, intent(uri)).setup()

    private fun logged() = ShadowLog.getLogsForTag("StubNav").map { it.msg }

    // Another app's hand-off names no package (CLAUDE.md), so the implicit intent must resolve here (docs/03 §11).
    @Test
    fun anImplicitGeoViewIntentResolvesToTheStub() {
        val handoff = Intent(Intent.ACTION_VIEW, Uri.parse(GREYSTONES))
        val handlers = app.packageManager.queryIntentActivities(handoff, PackageManager.MATCH_DEFAULT_ONLY)
        assertEquals(listOf(StubNavigationActivity::class.java.name), handlers.map { it.activityInfo.name })
        assertTrue("other apps must be able to start it", handlers.single().activityInfo.exported)
    }

    // Shown while driving; one instance, so a later hand-off reaches the open stub instead of being dropped.
    @Test
    fun showsWhileDrivingAndKeepsOneInstance() {
        val stub = ComponentName(app, StubNavigationActivity::class.java)
        val info = app.packageManager.getActivityInfo(stub, PackageManager.GET_META_DATA)
        assertTrue(info.metaData.getBoolean("distractionOptimized"))
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
    }

    @Test
    fun showsAndLogsTheReceivedDestination() {
        launch(GREYSTONES)
        rule.onNodeWithText(GREYSTONES).assertIsDisplayed()
        assertEquals(listOf("received $GREYSTONES"), logged())
    }

    // A second hand-off while the stub is still open replaces the first, on screen and in the log.
    @Test
    fun aLaterHandOffReplacesTheFirst() {
        launch(GREYSTONES).newIntent(intent(WICKLOW))
        rule.onNodeWithText(WICKLOW).assertIsDisplayed()
        rule.onNodeWithText(GREYSTONES).assertDoesNotExist()
        assertEquals(listOf("received $GREYSTONES", "received $WICKLOW"), logged())
    }

    @Test
    fun openedWithoutADestinationSaysSo() {
        launch(uri = null)
        rule.onNodeWithText("No destination received").assertIsDisplayed()
        assertEquals(emptyList<String>(), logged())
    }
}
