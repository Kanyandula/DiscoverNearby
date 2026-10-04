package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.visual

// ponytail: placeholder header only; DN-M0-004 adds the list and its states.
@Composable
fun RecommendationsScreen(category: DiscoveryCategory, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ContentGap)) {
            // The AOSP car system bar has no Back button, so the screen provides one (docs/02 §3.6).
            IconButton(onClick = onBack, modifier = Modifier.size(MinTouchTarget)) {
                Icon(painter = painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
            Text(text = stringResource(category.visual.label), style = MaterialTheme.typography.headlineMedium)
        }
    }
}
