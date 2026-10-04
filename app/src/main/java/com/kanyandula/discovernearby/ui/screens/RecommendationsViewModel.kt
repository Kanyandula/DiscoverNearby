package com.kanyandula.discovernearby.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanyandula.discovernearby.car.DrivingRestrictions
import com.kanyandula.discovernearby.car.DrivingState
import com.kanyandula.discovernearby.discovery.CategoryConfigs
import com.kanyandula.discovernearby.discovery.DiscoverError
import com.kanyandula.discovernearby.discovery.DiscoverResult
import com.kanyandula.discovernearby.discovery.DiscoverUseCase
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.Recommendation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Recommendations destination's states (docs/03 §15). All draw on one screen; none is pushed. */
sealed interface RecommendationsUiState {
    data object Loading : RecommendationsUiState
    data class Content(val requestId: Long, val recommendations: List<Recommendation>) : RecommendationsUiState
    data object Empty : RecommendationsUiState
    data class PermissionRequired(val canRequest: Boolean) : RecommendationsUiState
    data class Error(val type: DiscoverError) : RecommendationsUiState
}

/** How long the UI state keeps its upstreams after the screen stops collecting. */
internal const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L

class RecommendationsViewModel(
    private val category: DiscoveryCategory,
    private val discover: DiscoverUseCase,
    drivingRestrictions: DrivingRestrictions,
) : ViewModel() {

    private val result = MutableStateFlow<DiscoverResult?>(null) // null while a request runs
    private var requestId = 0L
    private var request: Job? = null

    // The driving state is combined in, not read, so the restrictions connection is open only while the
    // screen collects, and a change re-trims the list without a new request (docs/03 §6).
    val uiState: StateFlow<RecommendationsUiState> = combine(result, drivingRestrictions.state, ::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
            initialValue = RecommendationsUiState.Loading,
        )

    init {
        load()
    }

    fun retry() = load()

    // Cancelling the previous request is what drops its late response: a cancelled coroutine never resumes
    // to assign its result (docs/03 §15). Back clears this ViewModel, which cancels the request the same way.
    private fun load() {
        request?.cancel()
        result.value = null
        val id = ++requestId
        request = viewModelScope.launch { result.value = discover(id, category) }
    }

    private fun toUiState(result: DiscoverResult?, driving: DrivingState): RecommendationsUiState = when (result) {
        null -> RecommendationsUiState.Loading
        is DiscoverResult.Success -> if (result.recommendations.isEmpty()) {
            RecommendationsUiState.Empty
        } else {
            RecommendationsUiState.Content(
                requestId = result.context.requestId,
                recommendations = result.recommendations.take(visibleCount(driving)),
            )
        }
        DiscoverResult.PermissionRequired ->
            RecommendationsUiState.PermissionRequired(canRequest = !driving.distractionOptimizationRequired)
        is DiscoverResult.Failure -> RecommendationsUiState.Error(result.error)
    }

    // docs/03 §6: the first min(desired, uxLimit); the engine never sees the driving state.
    private fun visibleCount(driving: DrivingState) =
        minOf(CategoryConfigs.getValue(category).desiredResults, driving.listLimit ?: Int.MAX_VALUE)
}
