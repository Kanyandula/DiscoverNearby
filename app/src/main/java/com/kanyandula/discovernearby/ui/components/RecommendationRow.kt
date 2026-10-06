package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.SEPARATOR
import com.kanyandula.discovernearby.ui.attributeTypes
import com.kanyandula.discovernearby.ui.kilometres
import com.kanyandula.discovernearby.ui.label
import com.kanyandula.discovernearby.ui.theme.ChevronSize
import com.kanyandula.discovernearby.ui.theme.RowLineGap
import com.kanyandula.discovernearby.ui.theme.RowPadding
import com.kanyandula.discovernearby.ui.theme.RowRadius
import com.kanyandula.discovernearby.ui.theme.RowVerticalPadding

private const val MAX_ROW_ATTRIBUTES = 3 // docs/02 §6: 1–3 provided or derived attributes

/**
 * One recommendation (canvas Recommendations artboard): name; rating and attributes when known; distance.
 * Missing fields are left out, never shown blank. ponytail: no photo or attribution until ADR-001 says what
 * the provider allows.
 */
@Composable
fun RecommendationRow(recommendation: Recommendation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val place = recommendation.place
    val shape = RoundedCornerShape(RowRadius)
    Surface(
        onClick = onClick,
        modifier = modifier.focusRing(shape).fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = RowPadding, vertical = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RowPadding),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowLineGap)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                detailsLine(place)?.let { SupportingLine(it) }
                SupportingLine(stringResource(R.string.distance_km, kilometres(recommendation.distanceMeters)))
            }
            // Decorative, as on the canvas.
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                modifier = Modifier.size(ChevronSize),
            )
        }
    }
}

/** Rating, only when the provider gives one, and up to three attributes; null when there is neither. */
@Composable
private fun detailsLine(place: PlaceSummary): String? {
    val rating = place.rating?.let { rating ->
        place.ratingCount?.let { stringResource(R.string.rating_with_count, rating, it) }
            ?: stringResource(R.string.rating, rating)
    }
    val attributes = place.attributeTypes().take(MAX_ROW_ATTRIBUTES)
        .map { stringResource(it.label) }
    return (listOfNotNull(rating) + attributes).joinToString(SEPARATOR).ifEmpty { null }
}

@Composable
private fun SupportingLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
