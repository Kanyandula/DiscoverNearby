package com.kanyandula.discovernearby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kanyandula.discovernearby.AppContainer
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.components.AppRail
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.HeaderHeight
import com.kanyandula.discovernearby.ui.theme.HeaderIconSize
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.PanelPadding
import com.kanyandula.discovernearby.ui.theme.PanelRadius
import com.kanyandula.discovernearby.ui.theme.RailGap

@Composable
fun DiscoverNearbyApp(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState() // read so the tab follows every move
    val tab = entry?.let { navController.selectedTab() } ?: RailTab.DISCOVER
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(start = PanelPadding, end = PanelPadding, bottom = PanelPadding),
        ) {
            AppHeader()
            // The panel is composed before the rail, so rotary reaches the rail after the screen's content and the
            // first turn still lands on the screen's first item (DN-UX-006); the rail is drawn on the left.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(start = MinTouchTarget + RailGap),
                    shape = RoundedCornerShape(PanelRadius),
                ) {
                    DiscoverNavHost(
                        container = container,
                        modifier = Modifier.padding(PanelPadding),
                        navController = navController,
                    )
                }
                AppRail(selected = tab, onSelect = navController::openTab)
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
