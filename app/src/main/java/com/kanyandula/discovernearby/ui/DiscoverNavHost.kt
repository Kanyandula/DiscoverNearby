package com.kanyandula.discovernearby.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.screens.DiscoverScreen
import com.kanyandula.discovernearby.ui.screens.RecommendationsScreen
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState
import kotlinx.serialization.Serializable

@Serializable
data object DiscoverRoute

@Serializable
data class RecommendationsRoute(val category: DiscoveryCategory)

/**
 * A destination acts only while it is the top of the back stack. navigate and popBackStack change the
 * top at once, so a second tap from the same screen does nothing, while a deliberate tap during the
 * 700 ms fade still works (a RESUMED check would drop it until the fade ends).
 */
private fun NavHostController.isTop(entry: NavBackStackEntry) = currentBackStackEntry?.id == entry.id

@Composable
fun DiscoverNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = DiscoverRoute, modifier = modifier) {
        composable<DiscoverRoute> { entry ->
            DiscoverScreen(
                onCategorySelected = { category ->
                    if (navController.isTop(entry)) navController.navigate(RecommendationsRoute(category))
                },
            )
        }
        composable<RecommendationsRoute> { entry ->
            RecommendationsScreen(
                category = entry.toRoute<RecommendationsRoute>().category,
                state = RecommendationsUiState.Loading, // Task 5 replaces this with the ViewModel's state
                onRetry = {},
                onBack = { if (navController.isTop(entry)) navController.popBackStack() },
            )
        }
    }
}
