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
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.model.attributeTypes
import com.kanyandula.discovernearby.ui.SEPARATOR
import com.kanyandula.discovernearby.ui.kilometres
import com.kanyandula.discovernearby.ui.kindLabel
import com.kanyandula.discovernearby.ui.label
import com.kanyandula.discovernearby.ui.theme.ChevronSize
import com.kanyandula.discovernearby.ui.theme.RowImageHeight
import com.kanyandula.discovernearby.ui.theme.RowImageIconSize
import com.kanyandula.discovernearby.ui.theme.RowImageRadius
import com.kanyandula.discovernearby.ui.theme.RowImageWidth
import com.kanyandula.discovernearby.ui.theme.RowLineGap
import com.kanyandula.discovernearby.ui.theme.RowPadding
import com.kanyandula.discovernearby.ui.theme.RowRadius
import com.kanyandula.discovernearby.ui.theme.RowVerticalPadding

private const val MAX_ROW_ATTRIBUTES = 3 // docs/02 §6: 1–3 provided or derived attributes

/**
 * One recommendation (canvas Recommendations artboard): the place's image; name; rating and attributes when known;
 * distance. Missing fields are left out, never shown blank. The provider's notice sits under the list, not on the
 * row (ProviderAttribution).
 */
@Composable
fun RecommendationRow(
    recommendation: Recommendation,
    category: DiscoveryCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = recommendation.place
    val shape = RoundedCornerShape(RowRadius)
    Surface(
        onClick = onClick,
        modifier = modifier.focusRing(shape).fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            // The image sits as close to the edge as the row's top and bottom, as on the canvas.
            modifier = Modifier.padding(
                start = RowVerticalPadding,
                end = RowPadding,
                top = RowVerticalPadding,
                bottom = RowVerticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RowPadding),
        ) {
            PlaceImage(
                category = category,
                radius = RowImageRadius,
                iconSize = RowImageIconSize,
                modifier = Modifier.size(width = RowImageWidth, height = RowImageHeight),
            )
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

/**
 * As on the canvas ("4.6 ★ (342) · Café · Parking"): the rating, only when the provider gives one; the kind of place;
 * up to three attributes. Null when there is none of them.
 */
@Composable
private fun detailsLine(place: PlaceSummary): String? {
    val rating = place.rating?.let { rating ->
        place.ratingCount?.let { stringResource(R.string.rating_with_count, rating, it) }
            ?: stringResource(R.string.rating, rating)
    }
    val kind = kindLabel(place.primaryKind)?.let { stringResource(it) }
    val attributes = place.attributeTypes().take(MAX_ROW_ATTRIBUTES)
        .map { stringResource(it.label) }
    // distinct: a café's kind and a CAFE attribute would both say "Café".
    return (listOfNotNull(rating, kind) + attributes).distinct().joinToString(SEPARATOR).ifEmpty { null }
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
