package com.kanyandula.discovernearby.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kanyandula.discovernearby.DiscoverApplication
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DiscoverApplication).container
        setContent {
            DiscoverNearbyTheme {
                DiscoverNearbyApp(container)
            }
        }
    }
}
