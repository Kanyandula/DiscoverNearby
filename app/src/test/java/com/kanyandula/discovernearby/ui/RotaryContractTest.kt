package com.kanyandula.discovernearby.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RotaryContractTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    // V7: Android 13's RotaryController (Navigator.findVirtualViewAncestor) moves focus through Compose only below a
    // node with exactly this class name that is in the accessibility tree. Compose's root already qualifies; this
    // pins the precondition rotary support rests on.
    @Test
    fun composeRootIsVisibleToTheRotaryService() {
        val root = rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, root.importantForAccessibility)
        assertEquals("androidx.compose.ui.platform.ComposeView", root.accessibilityClassName.toString())
    }

    // DN-UX-009, DN-UX-010, found on the emulator: on a turn, only the rotary service moves focus. Compose doesn't tell
    // the service about a move to a row that was off screen, so after the app moved focus on the service's list
    // scroll, the service turned on from the row it knew: a dead turn, and on a fast spin focus ran out of the list
    // and round the rail.
    @Test
    fun theRotaryServicesScrollLeavesFocusWhereItIs() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false) // rotary input, as useRotaryInput
        rule.onNodeWithText("Coffee").requestFocus().assertIsFocused()
        repeat(2) {
            rule.runOnUiThread { rule.activity.dispatchGenericMotionEvent(rotaryScroll(-1f)) }
            rule.waitForIdle()
        }
        rule.onNodeWithText("Coffee").assertIsFocused()
    }
}
