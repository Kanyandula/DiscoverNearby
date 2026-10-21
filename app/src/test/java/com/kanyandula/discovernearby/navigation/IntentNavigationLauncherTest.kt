package com.kanyandula.discovernearby.navigation

import android.content.ActivityNotFoundException
import android.content.Context
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

    /** Records each intent it starts; [refuse] throws for the actions it names. */
    private class Recording(base: Context, val refuse: (String?) -> Throwable?) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            refuse(intent.action)?.let { throw it }
            started += intent
        }
    }

    private fun assertHandOff(intent: Intent, action: String) {
        assertEquals(action, intent.action)
        assertEquals(geoUri(HARBOUR_ROASTERS), intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(intent.component)
        assertNull(intent.`package`)
    }

    // AAOS's navigation intent first (DN-SP-006); the system picks the app: no package or component (docs/03 §11).
    @Test
    fun startsTheNavigateIntentInANewTask() {
        assertTrue(IntentNavigationLauncher(app).navigateTo(HARBOUR_ROASTERS).isSuccess)
        assertHandOff(shadowOf(app).nextStartedActivity, ACTION_NAVIGATE)
        assertNull("nothing else is started", shadowOf(app).nextStartedActivity)
    }

    // An app that only views geo: links (the stub, a non-car app) still gets the place.
    @Test
    fun anyNavigateFailureFallsBackToAGeoView() {
        listOf(ActivityNotFoundException(), SecurityException(), IllegalStateException()).forEach { failure ->
            val context = Recording(app) { if (it == ACTION_NAVIGATE) failure else null }
            assertTrue(IntentNavigationLauncher(context).navigateTo(HARBOUR_ROASTERS).isSuccess)
            assertHandOff(context.started.single(), Intent.ACTION_VIEW)
        }
    }

    @Test
    fun noNavigationAppIsAFailure() {
        shadowOf(app).checkActivities(true) // startActivity now throws ActivityNotFoundException
        assertTrue(IntentNavigationLauncher(app).navigateTo(HARBOUR_ROASTERS).isFailure)
    }

    @Test
    fun aFailedFallbackIsAFailure() {
        listOf(SecurityException(), IllegalStateException()).forEach { failure ->
            val context = Recording(app) { failure }
            assertTrue(IntentNavigationLauncher(context).navigateTo(HARBOUR_ROASTERS).isFailure)
            assertTrue(context.started.isEmpty())
        }
    }
}
