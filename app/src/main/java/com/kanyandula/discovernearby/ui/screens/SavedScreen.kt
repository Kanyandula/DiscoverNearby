package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.components.Message
import com.kanyandula.discovernearby.ui.components.MessageState
import com.kanyandula.discovernearby.ui.components.ProviderAttribution
import com.kanyandula.discovernearby.ui.components.RecommendationRow
import com.kanyandula.discovernearby.ui.components.ScreenHeader
import com.kanyandula.discovernearby.ui.rememberReturnFocus
import com.kanyandula.discovernearby.ui.theme.Highlight
import com.kanyandula.discovernearby.ui.theme.RowGap

private val SavedEmptyMessage =
    Message(R.drawable.ic_heart, Highlight, R.string.saved_empty_title, R.string.saved_empty_body)

/** Saved places as Recommendations rows (DN-UX-006); every state draws on this one destination. */
@Composable
fun SavedScreen(
    state: SavedUiState,
    onBack: () -> Unit,
    onPlaceSelected: (SavedRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val returnFocus = rememberReturnFocus()
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        ScreenHeader(title = stringResource(R.string.tab_saved), onBack = onBack)
        val body = Modifier.weight(1f)
        when (state) {
            SavedUiState.Loading -> LoadingState(body)
            SavedUiState.Empty ->
                MessageState(SavedEmptyMessage, backLabel = R.string.back_to_discover, onBack = onBack, modifier = body)
            // The driving limit allows none; the list comes back on its own when the limit lifts.
            SavedUiState.ParkToSee ->
                MessageState(ParkToSeeMessage, backLabel = R.string.back, onBack = onBack, modifier = body)
            is SavedUiState.Content -> Column(modifier = body, verticalArrangement = Arrangement.spacedBy(RowGap)) {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    items(state.rows, key = { it.place.summary.id }) { row ->
                        val id = row.place.summary.id
                        RecommendationRow(
                            place = row.place.summary,
                            distanceMeters = row.distanceMeters,
                            enrichment = row.place.enrichment,
                            category = row.place.category,
                            onClick = {
                                returnFocus.selected(id)
                                onPlaceSelected(row)
                            },
                            modifier = returnFocus.item(id),
                        )
                    }
                }
                // HERE's notice with HERE's data (ADR-001 V6a); the fakes carry none.
                ProviderAttribution(state.rows.firstNotNullOfOrNull { it.place.summary.attribution })
            }
        }
    }
}
