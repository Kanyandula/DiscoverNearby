package com.kanyandula.discovernearby.ui

import androidx.activity.compose.LocalActivity
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
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.ui.screens.DiscoverScreen
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsScreen
import com.kanyandula.discovernearby.ui.screens.PlaceDetailsViewModel
import com.kanyandula.discovernearby.ui.screens.RecommendationsScreen
import com.kanyandula.discovernearby.ui.screens.RecommendationsUiState
import com.kanyandula.discovernearby.ui.screens.RecommendationsViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlin.reflect.typeOf

@Serializable
data object DiscoverRoute

@Serializable
data class RecommendationsRoute(val category: DiscoveryCategory)

// The route carries the place itself, so Details shows the summary at once and keeps it after a failed details
// call or process death (docs/02 §7); the category the user picked, for the place's artwork; and the row's
// Tripadvisor photo and rating, so Details shows them without a second lookup (DN-UX-004).
@Serializable
data class PlaceDetailsRoute(
    val place: PlaceSummary,
    val distanceMeters: Int,
    val category: DiscoveryCategory,
    val enrichment: PlaceEnrichment? = null,
)

private val PlaceDetailsTypes = mapOf(
    typeOf<PlaceSummary>() to JsonNavType(PlaceSummary.serializer()),
    typeOf<PlaceEnrichment?>() to JsonNavType(PlaceEnrichment.serializer().nullable, isNullableAllowed = true),
)

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
            val viewModel = viewModel { container.recommendationsViewModel(category) }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val location = rememberLocationActions(viewModel)
            RecommendationsScreen(
                category = category,
                state = state,
                onRetry = viewModel::retry,
                onBack = { if (navController.isTop(entry)) navController.popBackStack() },
                onPlaceSelected = { picked ->
                    if (navController.isTop(entry)) {
                        val enrichment = (state as? RecommendationsUiState.Content)?.enrichments?.get(picked.place.id)
                        navController.navigate(
                            PlaceDetailsRoute(picked.place, picked.distanceMeters, category, enrichment),
                        )
                    }
                },
                onGrant = location.grant,
                onOpenSettings = location.openSettings,
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
                category = route.category,
                enrichment = route.enrichment,
                state = state,
                onNavigate = viewModel::navigate,
                onBack = { if (navController.isTop(entry)) navController.popBackStack() },
            )
        }
    }
}

private fun AppContainer.recommendationsViewModel(category: DiscoveryCategory) =
    RecommendationsViewModel(category, discoverUseCase, drivingRestrictions, placeEnricher)

/** What the Recommendations destination can do about location access (docs/02 §10). */
private class LocationActions(val grant: () -> Unit, val openSettings: () -> Unit)

/**
 * Grant asks for location; after a permanent refusal, Open Settings shows the app's settings page, and the search
 * runs again on return.
 */
@Composable
private fun rememberLocationActions(viewModel: RecommendationsViewModel): LocationActions {
    val activity = LocalActivity.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val permanent = activity != null && refusedForGood(activity, it)
        viewModel.onPermissionResult(granted = true in it.values, permanent = permanent)
    }
    return LocationActions(
        grant = { permissions.launch(LOCATION_PERMISSIONS) },
        openSettings = rememberOpenAppSettings(onReturn = viewModel::retry),
    )
}
