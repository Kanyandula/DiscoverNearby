package com.kanyandula.discovernearby.ui

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kanyandula.discovernearby.DiscoverApplication
import com.kanyandula.discovernearby.places.fake.FakeScenario
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme

/** Debug builds only: the [FakeScenario] to serve, for the docs/04 §7 emulator scenarios. */
internal const val EXTRA_SCENARIO = "scenario"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DiscoverApplication).container
        // adb shell am start -S -n com.kanyandula.discovernearby/.ui.MainActivity --es scenario SLOW
        if (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            intent.getStringExtra(EXTRA_SCENARIO)?.let { container.fakePlaces.scenario = FakeScenario.valueOf(it) }
        }
        setContent {
            DiscoverNearbyTheme {
                DiscoverNearbyApp(container)
            }
        }
    }
}
