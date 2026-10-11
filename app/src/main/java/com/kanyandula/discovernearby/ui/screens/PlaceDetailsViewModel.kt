package com.kanyandula.discovernearby.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.Closing
import com.kanyandula.discovernearby.model.PlaceDetails
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.closingAt
import com.kanyandula.discovernearby.navigation.NavigationLauncher
import com.kanyandula.discovernearby.saved.SavedPlacesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

/** The Place Details destination's states (docs/03 §15). */
sealed interface PlaceDetailsUiState {
    /** The place as currently known: the route's summary, or the fresher one that came with the details. */
    val summary: PlaceSummary

    data class Loading(override val summary: PlaceSummary) : PlaceDetailsUiState
    /**
     * [closing]: when the place closes today, from the details' schedule at the time they arrived; [carParkNearby]: a
     * car park within CAR_PARK_RADIUS_METERS, from its own call, null until it answers (DN-UX-005).
     */
    data class Content(
        val details: PlaceDetails,
        val closing: Closing? = null,
        val carParkNearby: Boolean? = null,
    ) : PlaceDetailsUiState {
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
    private val saved: SavedPlacesRepository,
    private val category: DiscoveryCategory,
    private val enrichment: PlaceEnrichment?,
    now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    private val state = MutableStateFlow<PlaceDetailsUiState>(PlaceDetailsUiState.Loading(place))
    val uiState: StateFlow<PlaceDetailsUiState> = state.asStateFlow()

    private val toggling = Mutex()

    /** Whether the place is saved, live from the database; false until it answers (DN-UX-006). */
    val isSaved: StateFlow<Boolean> = saved.isSaved(place.id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialValue = false)

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
            // Shown with the details only: the summary-only fallback keeps what the row had.
            state.update { if (it is PlaceDetailsUiState.Content) it.copy(carParkNearby = nearby) else it }
        }
    }

    // Navigate never waits for details (docs/02 §7). Any hand-off failure shows NavigationUnavailable (docs/03 §16).
    fun navigate() {
        navigation.navigateTo(place.location).onFailure {
            state.value = PlaceDetailsUiState.NavigationUnavailable(state.value.summary)
        }
    }

    /**
     * Save ↔ Saved (DN-UX-006): keeps the place as Details knows it now, with the row's category and enrichment. Each
     * tap asks the database, one tap at a time: [isSaved] can lag behind it, and two quick taps must toggle twice.
     */
    fun toggleSave() {
        val summary = state.value.summary
        viewModelScope.launch {
            toggling.withLock {
                if (saved.isSaved(place.id).first()) {
                    saved.remove(place.id)
                } else {
                    saved.save(summary, category, enrichment)
                }
            }
        }
    }
}
