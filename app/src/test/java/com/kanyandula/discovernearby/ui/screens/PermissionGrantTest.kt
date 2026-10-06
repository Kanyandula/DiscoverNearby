package com.kanyandula.discovernearby.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanyandula.discovernearby.car.DrivingState
import com.kanyandula.discovernearby.car.FakeDrivingRestrictions
import com.kanyandula.discovernearby.discovery.BasicRecommendationEngine
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.DiscoveryCategory.COFFEE
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import com.kanyandula.discovernearby.ui.AUTOMOTIVE_1024P
import com.kanyandula.discovernearby.ui.theme.DiscoverNearbyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// docs/04 §11: Grant hidden while distraction optimization is required, shown when it is not (fake restrictions),
// through the real ViewModel and screen.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AUTOMOTIVE_1024P)
class PermissionGrantTest {

    @get:Rule
    val rule = createComposeRule()

    private val restrictions = FakeDrivingRestrictions(
        MutableStateFlow(DrivingState(distractionOptimizationRequired = true, listLimit = null)),
    )
    private var grants = 0

    @Before
    fun setUp() {
        val discover = DiscoverUseCase(
            FakePlacesRepository(),
            FakeLocationProvider(LocationResult.PermissionMissing),
            BasicRecommendationEngine(),
        )
        val viewModel = RecommendationsViewModel(COFFEE, discover, restrictions)
        rule.setContent {
            DiscoverNearbyTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                RecommendationsScreen(
                    category = COFFEE,
                    state = state,
                    onRetry = {},
                    onBack = {},
                    onPlaceSelected = {},
                    onGrant = { grants++ },
                    onOpenSettings = {},
                )
            }
        }
    }

    @Test
    fun grantAppearsOnlyWhileRestrictionsAllow() {
        rule.onNodeWithText("Park the vehicle to allow Discover Nearby to access your location.").assertIsDisplayed()
        rule.onNodeWithText("Grant Permission").assertDoesNotExist()

        restrictions.state.value = DrivingState(distractionOptimizationRequired = false, listLimit = null)
        rule.onNodeWithText("Grant Permission").performClick()
        assertEquals(1, grants)

        restrictions.state.value = DrivingState(distractionOptimizationRequired = true, listLimit = 21)
        rule.onNodeWithText("Grant Permission").assertDoesNotExist()
    }
}
