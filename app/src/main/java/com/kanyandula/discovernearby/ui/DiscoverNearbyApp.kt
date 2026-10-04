package com.kanyandula.discovernearby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.AppContainer
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.HeaderHeight
import com.kanyandula.discovernearby.ui.theme.HeaderIconSize
import com.kanyandula.discovernearby.ui.theme.PanelPadding
import com.kanyandula.discovernearby.ui.theme.PanelRadius

@Composable
fun DiscoverNearbyApp(container: AppContainer, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(start = PanelPadding, end = PanelPadding, bottom = PanelPadding),
        ) {
            AppHeader()
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(PanelRadius),
            ) {
                DiscoverNavHost(container = container, modifier = Modifier.padding(PanelPadding))
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.height(HeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_location),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(HeaderIconSize),
        )
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
    }
}
