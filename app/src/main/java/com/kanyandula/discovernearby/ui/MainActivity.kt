package com.kanyandula.discovernearby.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kanyandula.discovernearby.DiscoverApplication
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme

/** Debug builds only: the FakeScenario to serve, for the docs/04 §7 emulator scenarios. */
internal const val EXTRA_SCENARIO = "scenario"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DiscoverApplication).container
        // adb shell am start -S -n com.kanyandula.discovernearby/.ui.MainActivity --es scenario SLOW
        container.useFakeScenario(intent.getStringExtra(EXTRA_SCENARIO))
        setContent {
            DiscoverNearbyTheme {
                DiscoverNearbyApp(container)
            }
        }
    }
}
