package com.kanyandula.discovernearby.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kanyandula.discovernearby.AppContainer
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.location.LOCATION_PERMISSIONS
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.ui.screens.DiscoverScreen
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsScreen
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsViewModel
import com.kanyandula.discovernearby.ui.screens.RecommendationsScreen
import com.kanyandula.discovernearby.ui.screens.RecommendationsViewModel
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf

@Serializable
data object DiscoverRoute

@Serializable
data class RecommendationsRoute(val category: DiscoveryCategory)

// The route carries the place itself, so Details shows the summary at once and keeps it after a failed details
// call or process death (docs/02 §7).
@Serializable
data class PlaceDetailsRoute(val place: PlaceSummary, val distanceMeters: Int)

private val PlaceDetailsTypes = mapOf(typeOf<PlaceSummary>() to JsonNavType(PlaceSummary.serializer()))

/**
 * A destination acts only while it is the top of the back stack. navigate and popBackStack change the
 * top at once, so a second tap from the same screen does nothing, while a deliberate tap on the new screen
 * during the 700 ms forward fade still works (a RESUMED check would drop it until the fade ends).
 */
private fun NavHostController.isTop(entry: NavBackStackEntry) = currentBackStackEntry?.id == entry.id

@Composable
fun DiscoverNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = DiscoverRoute,
        modifier = modifier,
        // Back switches at once: on a pop NavHost draws the outgoing screen on top, so during a fade its rows
        // would swallow a tap meant for the screen being returned to.
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<DiscoverRoute> { entry ->
            DiscoverScreen(
                onCategorySelected = { category ->
                    if (navController.isTop(entry)) navController.navigate(RecommendationsRoute(category))
                },
            )
        }
        composable<RecommendationsRoute> { entry ->
            val category = entry.toRoute<RecommendationsRoute>().category
            // Scoped to this back-stack entry: Back clears it, which cancels its request (docs/04 Scenario P).
            val viewModel = viewModel {
                RecommendationsViewModel(category, container.discoverUseCase, container.drivingRestrictions)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            // Fine and coarse in one dialog; either answer counts, so an approximate-only grant works too.
            val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                viewModel.onPermissionResult(granted = it.values.any { granted -> granted })
            }
            RecommendationsScreen(
                category = category,
                state = state,
                onRetry = viewModel::retry,
                onBack = { if (navController.isTop(entry)) navController.popBackStack() },
                onPlaceSelected = { picked ->
                    if (navController.isTop(entry)) {
                        navController.navigate(PlaceDetailsRoute(picked.place, picked.distanceMeters))
                    }
                },
                onGrant = { permissions.launch(LOCATION_PERMISSIONS) },
            )
        }
        composable<PlaceDetailsRoute>(typeMap = PlaceDetailsTypes) { entry ->
            val route = remember(entry) { entry.toRoute<PlaceDetailsRoute>() }
            val viewModel = viewModel {
                PlaceDetailsViewModel(route.place, container.discoverUseCase, container.navigationLauncher)
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PlaceDetailsScreen(
                distanceMeters = route.distanceMeters,
                state = state,
                onNavigate = viewModel::navigate,
                onBack = { if (navController.isTop(entry)) navController.popBackStack() },
            )
        }
    }
}
