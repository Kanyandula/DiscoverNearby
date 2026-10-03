package com.kanyandula.discovernearby.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.R

// ponytail: placeholder; DN-M0-002 replaces the body with DiscoverNavHost.
@Composable
fun DiscoverNearbyApp() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        }
    }
}
