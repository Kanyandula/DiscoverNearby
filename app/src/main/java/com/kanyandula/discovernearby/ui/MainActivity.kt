package com.kanyandula.discovernearby.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kanyandula.discovernearby.DiscoverApplication
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ponytail: keeps the restrictions connection open while visible (and logged) until
        // DN-M0-004's ViewModel collects DrivingRestrictions; remove this then.
        val restrictions = (application as DiscoverApplication).container.drivingRestrictions
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { restrictions.state.collect {} }
        }
        setContent {
            DiscoverNearbyTheme {
                DiscoverNearbyApp()
            }
        }
    }
}
