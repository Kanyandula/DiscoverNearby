package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.discovery.DiscoverError
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.components.Message
import com.kanyandula.discovernearby.ui.components.MessageState
import com.kanyandula.discovernearby.ui.components.ProviderAttribution
import com.kanyandula.discovernearby.ui.components.RecommendationRow
import com.kanyandula.discovernearby.ui.components.ScreenHeader
import com.kanyandula.discovernearby.ui.rememberReturnFocus
import com.kanyandula.discovernearby.ui.theme.Highlight
import com.kanyandula.discovernearby.ui.theme.MessageGap
import com.kanyandula.discovernearby.ui.theme.MessageIconSize
import com.kanyandula.discovernearby.ui.theme.OnSurfaceVariant
import com.kanyandula.discovernearby.ui.theme.Raised
import com.kanyandula.discovernearby.ui.theme.RowGap
import com.kanyandula.discovernearby.ui.theme.SpinnerStroke
import com.kanyandula.discovernearby.ui.visual

private val EmptyMessage = Message(R.drawable.ic_empty, Highlight, R.string.empty_title, R.string.empty_body)
private val LoadFailedMessage =
    Message(R.drawable.ic_error_network, OnSurfaceVariant, R.string.load_failed_title, R.string.load_failed_body)
private val TimeoutMessage = Message(R.drawable.ic_timeout, Highlight, R.string.timeout_title, R.string.timeout_body)
private val ParkToSeeMessage =
    Message(R.drawable.ic_info, Highlight, R.string.park_to_see_title, R.string.park_to_see_body)
private val NoLocationMessage =
    Message(R.drawable.ic_location_off, OnSurfaceVariant, R.string.no_location_title, R.string.no_location_body)

/** Every state draws on this one destination; a state change never pushes a screen (docs/02 §6). */
@Suppress("LongParameterList") // the state plus one lambda per user action
@Composable
fun RecommendationsScreen(
    category: DiscoveryCategory,
    state: RecommendationsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPlaceSelected: (Recommendation) -> Unit,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val returnFocus = rememberReturnFocus()
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        ScreenHeader(title = stringResource(category.visual.label), onBack = onBack)
        val body = Modifier.weight(1f)
        when (state) {
            RecommendationsUiState.Loading -> LoadingState(body)
            is RecommendationsUiState.Content -> Column(
                modifier = body,
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    items(state.recommendations, key = { it.place.id }) { recommendation ->
                        RecommendationRow(
                            recommendation,
                            category = category,
                            onClick = {
                                returnFocus.selected(recommendation.place.id)
                                onPlaceSelected(recommendation)
                            },
                            modifier = returnFocus.item(recommendation.place.id),
                        )
                    }
                }
                // HERE's notice with HERE's data (ADR-001 V6a), bottom left as on the canvas; the fakes carry none.
                ProviderAttribution(state.recommendations.firstNotNullOfOrNull { it.place.attribution })
            }
            RecommendationsUiState.Empty ->
                MessageState(EmptyMessage, backLabel = R.string.back_to_discover, onBack = onBack, modifier = body)
            // docs/02 §17: places were found, but the driving list limit allows none; parking shows them.
            RecommendationsUiState.ParkToSee -> MessageState(
                message = ParkToSeeMessage,
                backLabel = R.string.back,
                onBack = onBack,
                modifier = body,
                onPrimary = onRetry,
                primaryLabel = R.string.try_again,
            )
            is RecommendationsUiState.Error ->
                MessageState(
                    message = state.type.message,
                    backLabel = R.string.back,
                    onBack = onBack,
                    modifier = body,
                    onPrimary = onRetry,
                    primaryLabel = R.string.try_again,
                )
            is RecommendationsUiState.PermissionRequired ->
                PermissionMessage(state, onBack = onBack, onGrant = onGrant, onOpenSettings = onOpenSettings, body)
        }
    }
}

// Grant only while restrictions allow a permission dialog; otherwise ask the user to park (docs/02 §10). After a
// permanent refusal, Android shows no dialog, so Settings takes Grant's place, under the same rule (DN-UX-001).
@Composable
private fun PermissionMessage(
    state: RecommendationsUiState.PermissionRequired,
    onBack: () -> Unit,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.denial == Denial.PERMANENT
    MessageState(
        message = Message(
            icon = R.drawable.ic_location,
            tint = Highlight,
            title = R.string.permission_title,
            body = when {
                !state.canRequest -> R.string.permission_body_restricted
                settings -> R.string.permission_body_settings
                state.denial == Denial.ONCE -> R.string.permission_body_denied
                else -> R.string.permission_body
            },
        ),
        backLabel = R.string.back,
        onBack = onBack,
        modifier = modifier,
        onPrimary = (if (settings) onOpenSettings else onGrant).takeIf { state.canRequest },
        primaryLabel = if (settings) R.string.open_settings else R.string.grant_permission,
    )
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MessageGap, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(MessageIconSize),
            color = Highlight,
            strokeWidth = SpinnerStroke,
            trackColor = Raised,
        )
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
    }
}

// docs/02 §12 lets one message cover all three; the timeout design (10-timeout.png) gives it its own.
private val DiscoverError.message: Message
    get() = when (this) {
        DiscoverError.NetworkUnavailable, DiscoverError.ProviderFailure -> LoadFailedMessage
        DiscoverError.Timeout -> TimeoutMessage
        DiscoverError.LocationUnavailable -> NoLocationMessage
    }
