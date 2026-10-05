package com.kanyandula.discovernearby.ui

import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

// V7: Android 13's RotaryController (Navigator.findVirtualViewAncestor) moves focus through Compose only below a
// node with exactly this class name that is in the accessibility tree. Compose's root already qualifies; this pins
// the precondition rotary support rests on.
@RunWith(RobolectricTestRunner::class)
class RotaryContractTest {

    @Test
    fun composeRootIsVisibleToTheRotaryService() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, root.importantForAccessibility)
        assertEquals("androidx.compose.ui.platform.ComposeView", root.accessibilityClassName.toString())
    }
}
