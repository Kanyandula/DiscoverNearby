package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.model.ProviderRating
import com.kanyandula.discovernearby.ui.theme.RatingChipGap
import com.kanyandula.discovernearby.ui.theme.RatingChipPaddingHorizontal
import com.kanyandula.discovernearby.ui.theme.RatingChipPaddingVertical
import com.kanyandula.discovernearby.ui.theme.RatingMarkHeight

/**
 * Tripadvisor's rating as its display rules require (DN-UX-004, ADR-003): the rating graphic the API sends (its owl
 * mark and bubbles), never our own icons, on a white chip (the bubbles must sit on white), then [count], the review
 * count in the caller's words.
 */
@Composable
fun TripadvisorRating(rating: ProviderRating, count: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RatingChipGap),
    ) {
        AsyncImage(
            model = rating.iconUrl,
            contentDescription = stringResource(R.string.tripadvisor_rating, rating.value),
            contentScale = ContentScale.FillHeight,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(percent = 50))
                .padding(horizontal = RatingChipPaddingHorizontal, vertical = RatingChipPaddingVertical)
                .height(RatingMarkHeight),
        )
        count?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
