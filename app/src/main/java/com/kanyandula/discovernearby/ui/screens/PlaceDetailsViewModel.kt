package com.kanyandula.discovernearby.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.navigation.NavigationLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Place Details destination's states (docs/03 §15). ponytail: NavigationUnavailable arrives with the real
 * hand-off (DN-M3-001).
 */
sealed interface PlaceDetailsUiState {
    data class Loading(val summary: PlaceSummary) : PlaceDetailsUiState
    data class Content(val details: PlaceDetails) : PlaceDetailsUiState
    data class SummaryOnly(val summary: PlaceSummary) : PlaceDetailsUiState
}

class PlaceDetailsViewModel(
    private val place: PlaceSummary,
    discover: DiscoverUseCase,
    private val navigation: NavigationLauncher,
) : ViewModel() {

    private val state = MutableStateFlow<PlaceDetailsUiState>(PlaceDetailsUiState.Loading(place))
    val uiState: StateFlow<PlaceDetailsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = discover.details(place.id)?.let { PlaceDetailsUiState.Content(it) }
                ?: PlaceDetailsUiState.SummaryOnly(place)
        }
    }

    // Navigate never waits for details (docs/02 §7). ponytail: the M0 fake cannot fail; DN-M3-001 maps a failed
    // hand-off to NavigationUnavailable.
    fun navigate() {
        navigation.navigateTo(place.location)
    }
}
