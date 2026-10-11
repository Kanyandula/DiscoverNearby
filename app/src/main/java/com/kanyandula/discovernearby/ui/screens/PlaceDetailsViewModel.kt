package com.kanyandula.discovernearby.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.model.Closing
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.closingAt
import com.kanyandula.discovernearby.navigation.NavigationLauncher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** The Place Details destination's states (docs/03 §15). */
sealed interface PlaceDetailsUiState {
    /** The place as currently known: the route's summary, or the fresher one that came with the details. */
    val summary: PlaceSummary

    data class Loading(override val summary: PlaceSummary) : PlaceDetailsUiState
    /** [closing]: when the place closes today, from the details' schedule at the time they arrived (DN-UX-005). */
    data class Content(val details: PlaceDetails, val closing: Closing? = null) : PlaceDetailsUiState {
        override val summary: PlaceSummary get() = details.summary
    }
    data class SummaryOnly(override val summary: PlaceSummary) : PlaceDetailsUiState

    /** The hand-off failed (docs/02 §14): the message, under this place's header. */
    data class NavigationUnavailable(override val summary: PlaceSummary) : PlaceDetailsUiState
}

class PlaceDetailsViewModel(
    private val place: PlaceSummary,
    discover: DiscoverUseCase,
    private val navigation: NavigationLauncher,
    now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    private val state = MutableStateFlow<PlaceDetailsUiState>(PlaceDetailsUiState.Loading(place))
    val uiState: StateFlow<PlaceDetailsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            // Its own call beside the details, so neither waits for the other (DN-UX-005).
            val carPark = async { discover.carParkNear(place.location) }
            val loaded = discover.details(place.id)
                ?.let { PlaceDetailsUiState.Content(it, it.openingPeriods.closingAt(now())) }
                ?: PlaceDetailsUiState.SummaryOnly(place)
            // A failed hand-off stays on screen: the driver asked to navigate, not to read the details.
            state.update { if (it is PlaceDetailsUiState.NavigationUnavailable) it else loaded }
            val nearby = carPark.await() ?: return@launch
            state.update { current ->
                if (current !is PlaceDetailsUiState.Content) return@update current
                current.copy(details = current.details.copy(carParkNearby = nearby))
            }
        }
    }

    // Navigate never waits for details (docs/02 §7). Any hand-off failure shows NavigationUnavailable (docs/03 §16).
    fun navigate() {
        navigation.navigateTo(place.location).onFailure {
            state.value = PlaceDetailsUiState.NavigationUnavailable(state.value.summary)
        }
    }
}
