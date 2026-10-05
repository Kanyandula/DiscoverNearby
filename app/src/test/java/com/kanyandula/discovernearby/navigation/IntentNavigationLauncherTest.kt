package com.kanyandula.discovernearby.navigation

import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class IntentNavigationLauncherTest {

    private val app = RuntimeEnvironment.getApplication()

    // The system picks the navigation app: no package or component is named (docs/03 §11).
    @Test
    fun startsAGeoViewIntentInANewTask() {
        assertTrue(IntentNavigationLauncher(app).navigateTo(HARBOUR_ROASTERS).isSuccess)
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("geo:53.148000,-6.060300", started.dataString)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(started.component)
        assertNull(started.`package`)
    }

    @Test
    fun noNavigationAppIsAFailure() {
        shadowOf(app).checkActivities(true) // startActivity now throws ActivityNotFoundException
        assertTrue(IntentNavigationLauncher(app).navigateTo(HARBOUR_ROASTERS).isFailure)
    }

    @Test
    fun anyOtherHandOffFailureIsAFailure() {
        listOf(SecurityException(), IllegalStateException()).forEach { failure ->
            val refusing = object : ContextWrapper(app) {
                override fun startActivity(intent: Intent) = throw failure
            }
            assertTrue(IntentNavigationLauncher(refusing).navigateTo(HARBOUR_ROASTERS).isFailure)
        }
    }
}
