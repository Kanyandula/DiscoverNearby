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
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.places.PlaceEnricher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Recommendations destination's states (docs/03 §15). All draw on one screen; none is pushed. */
sealed interface RecommendationsUiState {
    data object Loading : RecommendationsUiState
    /** [enrichments]: a second provider's photo and rating by place ID, filled in as they arrive (DN-UX-004). */
    data class Content(
        val requestId: Long,
        val recommendations: List<Recommendation>,
        val enrichments: Map<String, PlaceEnrichment> = emptyMap(),
    ) : RecommendationsUiState
    data object Empty : RecommendationsUiState

    /** Places were found, but the driving list limit allows none to be shown (docs/02 §17). */
    data object ParkToSee : RecommendationsUiState

    data class PermissionRequired(val canRequest: Boolean, val denial: Denial = Denial.NONE) : RecommendationsUiState
    data class Error(val type: DiscoverError) : RecommendationsUiState
}

class RecommendationsViewModel(
    private val category: DiscoveryCategory,
    private val discover: DiscoverUseCase,
    drivingRestrictions: DrivingRestrictions,
    private val enricher: PlaceEnricher,
) : ViewModel() {

    private val result = MutableStateFlow<DiscoverResult?>(null) // null while a request runs
    private var requestId = 0L
    private var request: Job? = null
    private val denial = MutableStateFlow(Denial.NONE)
    private val enrichments = MutableStateFlow<Map<String, PlaceEnrichment>>(emptyMap())

    // The driving state is combined in, not read, so the restrictions connection is open only while the
    // screen collects, and a change re-trims the list without a new request (docs/03 §6). No stop timeout
    // here: the shared restrictions flow already keeps its connection through a quick restart.
    val uiState: StateFlow<RecommendationsUiState> =
        combine(result, drivingRestrictions.state, denial, enrichments, ::toUiState).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = RecommendationsUiState.Loading,
        )

    init {
        load()
    }

    fun retry() = load()

    /** The answer to the location permission request: a grant resumes discovery; [permanent] means no dialog again. */
    fun onPermissionResult(granted: Boolean, permanent: Boolean = false) {
        denial.value = when {
            granted -> Denial.NONE
            permanent -> Denial.PERMANENT
            else -> Denial.ONCE
        }
        if (granted) load()
    }

    // Cancelling the previous request is what drops its late response: a cancelled coroutine never resumes
    // to assign its result (docs/03 §15). Back clears this ViewModel, which cancels the request the same way.
    private fun load() {
        request?.cancel()
        result.value = null
        enrichments.value = emptyMap()
        val id = ++requestId
        request = viewModelScope.launch {
            val found = discover(id, category)
            result.value = found
            if (found is DiscoverResult.Success) enrich(found.recommendations.take(desiredCount))
        }
    }

    // After ranking, one row at a time and in order, so the top rows fill first and the provider's burst limit holds.
    // It runs in the request's coroutine, so a new request or Back cancels it with the request. The rows a driving
    // limit hides are enriched too: the limit can lift without a new request (docs/03 §6).
    private suspend fun enrich(recommendations: List<Recommendation>) {
        for (recommendation in recommendations) {
            val place = recommendation.place
            enricher.enrich(place, category)?.let { found -> enrichments.update { it + (place.id to found) } }
        }
    }

    private fun toUiState(
        result: DiscoverResult?,
        driving: DrivingState,
        denial: Denial,
        enrichments: Map<String, PlaceEnrichment>,
    ): RecommendationsUiState = when (result) {
        null -> RecommendationsUiState.Loading
        is DiscoverResult.Success -> {
            val shown = result.recommendations.take(visibleCount(driving))
            when {
                result.recommendations.isEmpty() -> RecommendationsUiState.Empty
                shown.isEmpty() -> RecommendationsUiState.ParkToSee
                else -> RecommendationsUiState.Content(result.context.requestId, shown, enrichments)
            }
        }
        DiscoverResult.PermissionRequired -> RecommendationsUiState.PermissionRequired(
            canRequest = !driving.distractionOptimizationRequired,
            denial = denial,
        )
        is DiscoverResult.Failure -> RecommendationsUiState.Error(result.error)
    }

    // docs/03 §6: the first min(desired, uxLimit); the engine never sees the driving state.
    private fun visibleCount(driving: DrivingState) = minOf(desiredCount, driving.listLimit ?: Int.MAX_VALUE)

    private val desiredCount get() = CategoryConfigs.getValue(category).desiredResults
}

/** How the location permission was last refused (docs/02 §10). After [PERMANENT], only Settings can allow it. */
enum class Denial { NONE, ONCE, PERMANENT }
