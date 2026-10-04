package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.Recommendation
import com.kanyandula.discovernearby.ui.METERS_PER_KM
import com.kanyandula.discovernearby.ui.SEPARATOR
import com.kanyandula.discovernearby.ui.components.ScreenHeader
import com.kanyandula.discovernearby.ui.label
import com.kanyandula.discovernearby.ui.theme.Action
import com.kanyandula.discovernearby.ui.theme.ActionColumnWidth
import com.kanyandula.discovernearby.ui.theme.DetailsColumnGap
import com.kanyandula.discovernearby.ui.theme.DetailsInset
import com.kanyandula.discovernearby.ui.theme.InfoIconGap
import com.kanyandula.discovernearby.ui.theme.InfoIconSize
import com.kanyandula.discovernearby.ui.theme.NavigateHeight
import com.kanyandula.discovernearby.ui.theme.NavigateIconGap
import com.kanyandula.discovernearby.ui.theme.NavigateIconSize
import com.kanyandula.discovernearby.ui.theme.NavigateRadius
import com.kanyandula.discovernearby.ui.theme.OpenNow
import com.kanyandula.discovernearby.ui.theme.Raised
import com.kanyandula.discovernearby.ui.theme.RowGap
import com.kanyandula.discovernearby.ui.theme.RowLineGap
import com.kanyandula.discovernearby.ui.theme.SectionPadding

/**
 * Place Details (canvas Place Details artboards): what is known about the place, with Navigate always there and
 * never waiting on the optional details call (docs/02 §7). One layout serves every state, so nothing moves when
 * details arrive. ponytail: no photo, attribution or place-kind label until ADR-001 (DN-M1-003, DN-M2-001).
 */
@Composable
fun PlaceDetailsScreen(
    recommendation: Recommendation,
    state: PlaceDetailsUiState,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = when (state) {
        is PlaceDetailsUiState.Loading -> state.summary
        is PlaceDetailsUiState.Content -> state.details.summary
        is PlaceDetailsUiState.SummaryOnly -> state.summary
    }
    val openingSummary = (state as? PlaceDetailsUiState.Content)?.details?.openingSummary
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        ScreenHeader(title = place.name, onBack = onBack)
        Row(
            modifier = Modifier.weight(1f).padding(start = DetailsInset),
            horizontalArrangement = Arrangement.spacedBy(DetailsColumnGap),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Facts(place, recommendation.distanceMeters, openingSummary)
                if (state is PlaceDetailsUiState.SummaryOnly) DetailsUnavailable()
            }
            NavigateButton(onClick = onNavigate, modifier = Modifier.width(ActionColumnWidth).align(Alignment.Bottom))
        }
    }
}

private class Fact(val primary: AnnotatedString?, val secondary: String?, val secondaryColor: Color)

/** Distance; rating and opening state; amenities. Each only when known, with dividers between. */
@Composable
private fun Facts(place: PlaceSummary, distanceMeters: Int, openingSummary: String?) {
    facts(place, distanceMeters, openingSummary).forEachIndexed { index, fact ->
        if (index > 0) HorizontalDivider(color = Raised)
        Column(
            modifier = Modifier.padding(vertical = SectionPadding),
            verticalArrangement = Arrangement.spacedBy(RowLineGap),
        ) {
            fact.primary?.let { Text(text = it, style = MaterialTheme.typography.headlineSmall) }
            fact.secondary?.let {
                Text(text = it, style = MaterialTheme.typography.titleMedium, color = fact.secondaryColor)
            }
        }
    }
}

@Composable
private fun facts(place: PlaceSummary, distanceMeters: Int, openingSummary: String?): List<Fact> {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val distance = AnnotatedString(stringResource(R.string.distance_away, distanceMeters / METERS_PER_KM))
    val opening = openingSummary ?: when (place.isOpenNow) {
        true -> stringResource(R.string.open_now)
        false -> stringResource(R.string.closed_now)
        null -> null
    }
    val rating = place.rating?.let { rating ->
        buildAnnotatedString {
            append(stringResource(R.string.rating, rating))
            place.ratingCount?.let { count ->
                withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) {
                    append(" ")
                    append(pluralStringResource(R.plurals.reviews, count, count))
                }
            }
        }
    }
    val amenities = place.attributes.map { it.type }.distinct().map { stringResource(it.label) }
    return listOfNotNull(
        Fact(distance, secondary = null, secondaryColor = muted),
        if (rating != null || opening != null) {
            Fact(rating, opening, if (place.isOpenNow == true) OpenNow else muted)
        } else {
            null
        },
        if (amenities.isNotEmpty()) {
            Fact(AnnotatedString(amenities.joinToString(SEPARATOR)), stringResource(R.string.amenities), muted)
        } else {
            null
        },
    )
}

@Composable
private fun DetailsUnavailable() {
    HorizontalDivider(color = Raised)
    Row(
        modifier = Modifier.padding(vertical = SectionPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InfoIconGap),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(InfoIconSize),
        )
        Text(
            text = stringResource(R.string.details_unavailable),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavigateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(NavigateHeight),
        shape = RoundedCornerShape(NavigateRadius),
        colors = ButtonDefaults.buttonColors(containerColor = Action, contentColor = Color.White),
    ) {
        // Decorative: the label says what it does.
        Icon(
            painter = painterResource(R.drawable.ic_navigate),
            contentDescription = null,
            modifier = Modifier.size(NavigateIconSize),
        )
        Spacer(modifier = Modifier.width(NavigateIconGap))
        Text(text = stringResource(R.string.navigate), style = MaterialTheme.typography.headlineSmall)
    }
}
