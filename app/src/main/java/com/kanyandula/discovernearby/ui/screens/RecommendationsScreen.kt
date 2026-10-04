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
import com.kanyandula.discovernearby.ui.components.Message
import com.kanyandula.discovernearby.ui.components.MessageState
import com.kanyandula.discovernearby.ui.components.RecommendationRow
import com.kanyandula.discovernearby.ui.components.ScreenHeader
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
private val NoLocationMessage =
    Message(R.drawable.ic_location_off, OnSurfaceVariant, R.string.no_location_title, R.string.no_location_body)
private val PermissionMessage =
    Message(R.drawable.ic_location_off, OnSurfaceVariant, R.string.permission_title, R.string.permission_body)
private val PermissionRestrictedMessage = Message(
    R.drawable.ic_location_off,
    OnSurfaceVariant,
    R.string.permission_title,
    R.string.permission_body_restricted,
)

/** Every state draws on this one destination; a state change never pushes a screen (docs/02 §6). */
@Composable
fun RecommendationsScreen(
    category: DiscoveryCategory,
    state: RecommendationsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        ScreenHeader(title = stringResource(category.visual.label), onBack = onBack)
        val body = Modifier.weight(1f)
        when (state) {
            RecommendationsUiState.Loading -> LoadingState(body)
            is RecommendationsUiState.Content ->
                LazyColumn(modifier = body, verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    items(state.recommendations, key = { it.place.id }) { RecommendationRow(it) }
                }
            RecommendationsUiState.Empty ->
                MessageState(EmptyMessage, backLabel = R.string.back_to_discover, onBack = onBack, modifier = body)
            is RecommendationsUiState.Error ->
                MessageState(state.type.message, R.string.back, onBack = onBack, modifier = body, onRetry = onRetry)
            // ponytail: Back only; DN-M0-006 adds Grant Permission and its launcher (docs/02 §10).
            is RecommendationsUiState.PermissionRequired -> MessageState(
                if (state.canRequest) PermissionMessage else PermissionRestrictedMessage,
                R.string.back,
                onBack = onBack,
                modifier = body,
            )
        }
    }
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
