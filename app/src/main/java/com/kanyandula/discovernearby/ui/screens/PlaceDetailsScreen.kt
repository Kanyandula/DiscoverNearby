package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.ProviderRating
import com.kanyandula.discovernearby.model.attributeTypes
import com.kanyandula.discovernearby.ui.SEPARATOR
import com.kanyandula.discovernearby.ui.components.Message
import com.kanyandula.discovernearby.ui.components.MessageState
import com.kanyandula.discovernearby.ui.components.PlaceImage
import com.kanyandula.discovernearby.ui.components.ProviderAttribution
import com.kanyandula.discovernearby.ui.components.ScreenHeader
import com.kanyandula.discovernearby.ui.components.TripadvisorRating
import com.kanyandula.discovernearby.ui.components.focusRing
import com.kanyandula.discovernearby.ui.kilometres
import com.kanyandula.discovernearby.ui.kindLabel
import com.kanyandula.discovernearby.ui.label
import com.kanyandula.discovernearby.ui.theme.Action
import com.kanyandula.discovernearby.ui.theme.ActionColumnWidth
import com.kanyandula.discovernearby.ui.theme.DetailsColumnGap
import com.kanyandula.discovernearby.ui.theme.DetailsImageGap
import com.kanyandula.discovernearby.ui.theme.DetailsImageHeight
import com.kanyandula.discovernearby.ui.theme.DetailsImageIconSize
import com.kanyandula.discovernearby.ui.theme.DetailsImageRadius
import com.kanyandula.discovernearby.ui.theme.DetailsInset
import com.kanyandula.discovernearby.ui.theme.InfoIconGap
import com.kanyandula.discovernearby.ui.theme.NavigateHeight
import com.kanyandula.discovernearby.ui.theme.NavigateIconGap
import com.kanyandula.discovernearby.ui.theme.NavigateIconSize
import com.kanyandula.discovernearby.ui.theme.NavigateRadius
import com.kanyandula.discovernearby.ui.theme.OnSurface
import com.kanyandula.discovernearby.ui.theme.OnSurfaceVariant
import com.kanyandula.discovernearby.ui.theme.OpenNow
import com.kanyandula.discovernearby.ui.theme.PhotoCreditGap
import com.kanyandula.discovernearby.ui.theme.Raised
import com.kanyandula.discovernearby.ui.theme.RowGap
import com.kanyandula.discovernearby.ui.theme.RowLineGap
import com.kanyandula.discovernearby.ui.theme.SectionPadding

/**
 * Place Details (canvas Place Details artboards): what is known about the place, with Navigate always there and
 * never waiting on the optional details call (docs/02 §7). One layout serves every state but the hand-off failure
 * message, so nothing moves when details arrive. The right column is the place's image with Navigate under it
 * (03-place-details). [enrichment] is the row's Tripadvisor photo and rating (DN-UX-004), the photo credited.
 */
@Composable
fun PlaceDetailsScreen(
    distanceMeters: Int,
    category: DiscoveryCategory,
    state: PlaceDetailsUiState,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enrichment: PlaceEnrichment? = null,
) {
    val openingSummary = (state as? PlaceDetailsUiState.Content)?.details?.openingSummary
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        ScreenHeader(title = state.summary.name, onBack = onBack)
        if (state is PlaceDetailsUiState.NavigationUnavailable) {
            // docs/02 §14, design 11: Back is the only action, and it leaves the destination like the header's.
            MessageState(NavigationUnavailableMessage, R.string.back, onBack = onBack, modifier = Modifier.weight(1f))
        } else {
            Row(
                modifier = Modifier.weight(1f).padding(start = DetailsInset),
                horizontalArrangement = Arrangement.spacedBy(DetailsColumnGap),
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Facts(state.summary, distanceMeters, openingSummary, enrichment?.rating)
                    if (state is PlaceDetailsUiState.SummaryOnly) DetailsUnavailable()
                    Spacer(Modifier.weight(1f))
                    // Bottom left: HERE's notice with HERE's data (ADR-001 V6a).
                    ProviderAttribution(state.summary.attribution)
                }
                Column(
                    modifier = Modifier.width(ActionColumnWidth),
                    verticalArrangement = Arrangement.spacedBy(DetailsImageGap),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(PhotoCreditGap)) {
                        PlaceImage(
                            category = category,
                            photoUrl = enrichment?.photoUrl,
                            radius = DetailsImageRadius,
                            iconSize = DetailsImageIconSize,
                            modifier = Modifier.fillMaxWidth().height(DetailsImageHeight),
                        )
                        if (enrichment?.photoUrl != null) PhotoCredit()
                    }
                    NavigateButton(onClick = onNavigate, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private val NavigationUnavailableMessage = Message(
    R.drawable.ic_navigate_off,
    OnSurfaceVariant,
    R.string.navigation_unavailable_title,
    R.string.navigation_unavailable_body,
)

/**
 * Distance and the kind of place; then rating and opening state; then amenities. Each only when known, with dividers
 * between. The opening line is the status, as on the canvas ("Open now"); the provider's schedule (HERE sends the
 * whole week) shows only when the status is unknown.
 */
@Composable
private fun Facts(place: PlaceSummary, distanceMeters: Int, openingSummary: String?, providerRating: ProviderRating?) {
    Section(
        AnnotatedString(stringResource(R.string.distance_away, kilometres(distanceMeters))),
        kindLabel(place.primaryKind)?.let { stringResource(it) },
    )
    val rating = ratingLine(place)
    val opening = when (place.isOpenNow) {
        true -> stringResource(R.string.open_now)
        false -> stringResource(R.string.closed_now)
        null -> openingSummary
    }
    if (providerRating != null || rating != null || opening != null) {
        HorizontalDivider(color = Raised)
        Section(
            primary = rating,
            secondary = opening,
            secondaryColor = if (place.isOpenNow == true) OpenNow else Color.Unspecified,
            // Tripadvisor's rating as Tripadvisor draws it, in place of the text rating.
            primaryContent = providerRating?.let { tripadvisor ->
                {
                    TripadvisorRating(
                        rating = tripadvisor,
                        count = tripadvisor.count?.let { pluralStringResource(R.plurals.reviews, it, it) },
                    )
                }
            },
        )
    }
    val amenities = place.attributeTypes().map { stringResource(it.label) }
    if (amenities.isNotEmpty()) {
        HorizontalDivider(color = Raised)
        Section(AnnotatedString(amenities.joinToString(SEPARATOR)), stringResource(R.string.amenities))
    }
}

/** "4.6 ★" and, when the provider counts them, "(342 reviews)" in the muted style; null without a rating. */
@Composable
private fun ratingLine(place: PlaceSummary): AnnotatedString? {
    val rating = place.rating ?: return null
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return buildAnnotatedString {
        append(stringResource(R.string.rating, rating))
        place.ratingCount?.let { count ->
            withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) {
                append(" ")
                append(pluralStringResource(R.plurals.reviews, count, count))
            }
        }
    }
}

/**
 * A primary line and a muted secondary line, as on the canvas sections. One line each: the panel has no room to
 * scroll, and a wrapped amenity list would push the summary-only note off it.
 */
@Composable
private fun Section(
    primary: AnnotatedString?,
    secondary: String? = null,
    secondaryColor: Color = Color.Unspecified,
    primaryContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.padding(vertical = SectionPadding),
        verticalArrangement = Arrangement.spacedBy(RowLineGap),
    ) {
        if (primaryContent != null) {
            primaryContent()
        } else if (primary != null) {
            Text(
                text = primary,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        secondary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = secondaryColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Whose content the photo is; Tripadvisor masks who took it. */
@Composable
private fun PhotoCredit() {
    Text(
        text = stringResource(R.string.photo_tripadvisor),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
    val shape = RoundedCornerShape(NavigateRadius)
    Button(
        onClick = onClick,
        // Action blue fill: a light ring stands out where an Accent one would not.
        modifier = modifier.focusRing(shape, OnSurface).height(NavigateHeight),
        shape = shape,
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
