package com.kanyandula.discovernearby.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanyandula.discovernearby.car.DrivingRestrictions
import com.kanyandula.discovernearby.car.DrivingState
import com.kanyandula.discovernearby.location.LocationProvider
import com.kanyandula.discovernearby.location.LocationResult
import com.kanyandula.discovernearby.saved.SavedPlace
import com.kanyandula.discovernearby.saved.SavedPlacesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Saved destination's states (DN-UX-006). All draw on one screen; none is pushed. */
sealed interface SavedUiState {
    data object Loading : SavedUiState
    data object Empty : SavedUiState

    /** Places are saved, but the driving list limit allows none to be shown (docs/02 §17). */
    data object ParkToSee : SavedUiState
    data class Content(val rows: List<SavedRow>) : SavedUiState
}

/** A saved place and how far it is from the car now; null without a location. */
data class SavedRow(val place: SavedPlace, val distanceMeters: Int?)

/**
 * Saved places nearest first (DN-UX-006). The location is read once, when the screen opens (docs/03 §13); without one
 * (permission refused, no fix) the newest come first, with no distances, and Saved never asks for permission. The
 * list follows the database, so a place removed on Place Details is gone on Back.
 */
class SavedViewModel(
    saved: SavedPlacesRepository,
    location: LocationProvider,
    drivingRestrictions: DrivingRestrictions,
) : ViewModel() {

    private val here = MutableStateFlow<LocationResult?>(null) // null while locating

    val uiState: StateFlow<SavedUiState> =
        combine(here, saved.savedPlaces(), drivingRestrictions.state, ::toUiState).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = SavedUiState.Loading,
        )

    init {
        viewModelScope.launch { here.value = location.currentLocation() }
    }
}

private fun toUiState(here: LocationResult?, places: List<SavedPlace>, driving: DrivingState): SavedUiState = when {
    places.isEmpty() -> SavedUiState.Empty
    here == null -> SavedUiState.Loading
    else -> {
        val origin = (here as? LocationResult.Available)?.point
        // A stable sort: without a location every distance is null, so the stored newest-first order stands.
        val rows = places.map { SavedRow(it, origin?.distanceMetersTo(it.summary.location)) }
            .sortedBy { it.distanceMeters }
        // docs/03 §6: the driving limit trims the list.
        val shown = rows.take(driving.listLimit ?: Int.MAX_VALUE)
        if (shown.isEmpty()) SavedUiState.ParkToSee else SavedUiState.Content(shown)
    }
}
