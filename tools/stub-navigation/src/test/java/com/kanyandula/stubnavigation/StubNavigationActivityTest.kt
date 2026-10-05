package com.kanyandula.stubnavigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
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

    private fun intent(uri: String? = null) =
        Intent(RuntimeEnvironment.getApplication(), StubNavigationActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri?.let(Uri::parse)
        }

    private fun launch(uri: String?) =
        Robolectric.buildActivity(StubNavigationActivity::class.java, intent(uri)).setup()

    private fun logged() = ShadowLog.getLogsForTag("StubNav").map { it.msg }

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
