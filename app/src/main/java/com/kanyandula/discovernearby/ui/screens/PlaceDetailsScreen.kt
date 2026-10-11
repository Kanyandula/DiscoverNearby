package com.kanyandula.discovernearby.ui.screens

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.kanyandula.discovernearby.model.AttributeType
import com.kanyandula.discovernearby.model.Closing
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.attributeTypes
import com.kanyandula.discovernearby.ui.SEPARATOR
import com.kanyandula.discovernearby.ui.clockTime
import com.kanyandula.discovernearby.ui.components.BackButton
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
import com.kanyandula.discovernearby.ui.theme.AmenityIconSize
import com.kanyandula.discovernearby.ui.theme.AmenityTileHeight
import com.kanyandula.discovernearby.ui.theme.AmenityTileRadius
import com.kanyandula.discovernearby.ui.theme.ButtonGap
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.DetailsGap
import com.kanyandula.discovernearby.ui.theme.DetailsImageHeight
import com.kanyandula.discovernearby.ui.theme.DetailsImageIconSize
import com.kanyandula.discovernearby.ui.theme.DetailsImageRadius
import com.kanyandula.discovernearby.ui.theme.DetailsImageWidth
import com.kanyandula.discovernearby.ui.theme.InfoIconGap
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
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

/**
 * Place Details as in the Product Lead's concept (03b-place-details-concept, DN-UX-005): Back, the photo and what is
 * known about the place across the top; the amenity tiles; Tripadvisor's description; Navigate bottom right, with
 * Save's slot beside it (DN-UX-006). Navigate never waits on the optional details call (docs/02 §7), and one layout
 * serves every state but the hand-off failure, so nothing moves when details arrive. [enrichment] is the row's
 * Tripadvisor data (DN-UX-004, DN-UX-005); the description is hidden while [drivingRestricted]: cars limit text then.
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
    drivingRestricted: Boolean = false,
) {
    if (state is PlaceDetailsUiState.NavigationUnavailable) {
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
            ScreenHeader(title = state.summary.name, onBack = onBack)
            // docs/02 §14, design 11: Back is the only action, and it leaves the destination like the header's.
            MessageState(NavigationUnavailableMessage, R.string.back, onBack = onBack, modifier = Modifier.weight(1f))
        }
    } else {
        val content = state as? PlaceDetailsUiState.Content
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DetailsGap)) {
            // Squeezed first on a short panel; the actions and the notice below always stay whole.
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DetailsGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ContentGap)) {
                    BackButton(onBack)
                    Photo(category, enrichment?.photoUrl)
                    Summary(
                        place = state.summary,
                        distanceMeters = distanceMeters,
                        opening = openingLine(state.summary, content?.closing, content?.details?.openingSummary),
                        enrichment = enrichment,
                        summaryOnly = state is PlaceDetailsUiState.SummaryOnly,
                        modifier = Modifier.weight(1f),
                    )
                }
                val tiles = amenityTiles(state.summary, content?.details?.carParkNearby)
                if (tiles.isNotEmpty()) AmenityTiles(tiles)
                enrichment?.description?.takeUnless { drivingRestricted }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(PhotoCreditGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
                    Spacer(Modifier.weight(1f)) // Save's slot (DN-UX-006)
                    NavigateButton(onClick = onNavigate, modifier = Modifier.weight(1f))
                }
                // Bottom left: HERE's notice with HERE's data (ADR-001 V6a).
                ProviderAttribution(state.summary.attribution)
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

@Composable
private fun Photo(category: DiscoveryCategory, photoUrl: String?) {
    Column(modifier = Modifier.width(DetailsImageWidth), verticalArrangement = Arrangement.spacedBy(PhotoCreditGap)) {
        PlaceImage(
            category = category,
            photoUrl = photoUrl,
            radius = DetailsImageRadius,
            iconSize = DetailsImageIconSize,
            modifier = Modifier.fillMaxWidth().height(DetailsImageHeight),
        )
        if (photoUrl != null) PhotoCredit()
    }
}

/**
 * The name; Tripadvisor's rating as Tripadvisor draws it, else the place's own; "Kind · price · distance"; the opening
 * line; and on the fallback the summary-only note. Each only when known.
 */
@Composable
private fun Summary(
    place: PlaceSummary,
    distanceMeters: Int,
    opening: String?,
    enrichment: PlaceEnrichment?,
    summaryOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RowLineGap)) {
        Text(
            text = place.name,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val tripadvisor = enrichment?.rating
        if (tripadvisor != null) {
            TripadvisorRating(
                rating = tripadvisor,
                count = tripadvisor.count?.let { pluralStringResource(R.plurals.reviews, it, it) },
            )
        } else {
            ratingLine(place)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val facts = listOfNotNull(
            kindLabel(place.primaryKind)?.let { stringResource(it) },
            enrichment?.priceLevel?.let { stringResource(it.label) },
            stringResource(R.string.distance_away, kilometres(distanceMeters)),
        )
        Text(
            text = facts.joinToString(SEPARATOR),
            style = MaterialTheme.typography.titleMedium,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        opening?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = if (place.isOpenNow == true) OpenNow else muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (summaryOnly) DetailsUnavailable()
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
 * "Open · Closes 18:00", "Open 24 hours" or "Open now" while open; "Closed now"; HERE's weekly text only when the
 * status is unknown. The time follows the car's 12/24-hour setting.
 */
@Composable
private fun openingLine(place: PlaceSummary, closing: Closing?, openingSummary: String?): String? =
    when (place.isOpenNow) {
        true -> when (closing) {
            Closing.AllDay -> stringResource(R.string.open_all_day)
            is Closing.At -> stringResource(
                R.string.open_closes,
                clockTime(closing.time, DateFormat.is24HourFormat(LocalContext.current)),
            )
            null -> stringResource(R.string.open_now)
        }
        false -> stringResource(R.string.closed_now)
        null -> openingSummary
    }

private class AmenityTile(@DrawableRes val icon: Int?, @StringRes val label: Int)

private const val TILE_SLOTS = 4 // the concept's row

/** The place's own amenities, then a car park nearby; at most a row of four. */
private fun amenityTiles(place: PlaceSummary, carParkNearby: Boolean?): List<AmenityTile> {
    val own = place.attributeTypes().map {
        AmenityTile(if (it == AttributeType.PARKING) R.drawable.ic_parking else null, it.label)
    }
    val carPark = AmenityTile(R.drawable.ic_parking, R.string.car_park_nearby).takeIf { carParkNearby == true }
    return (own + listOfNotNull(carPark)).take(TILE_SLOTS)
}

/** Four equal slots, so a lone tile keeps a quarter of the row as on the concept. Display only: not focusable. */
@Composable
private fun AmenityTiles(tiles: List<AmenityTile>) {
    Row(horizontalArrangement = Arrangement.spacedBy(ContentGap)) {
        repeat(TILE_SLOTS) { slot ->
            val tile = tiles.getOrNull(slot)
            if (tile == null) {
                Spacer(Modifier.weight(1f))
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(AmenityTileHeight)
                        .background(Raised, RoundedCornerShape(AmenityTileRadius))
                        .padding(horizontal = ContentGap),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // An empty icon slot keeps every label on the same line.
                    if (tile.icon == null) {
                        Spacer(Modifier.size(AmenityIconSize))
                    } else {
                        Icon(
                            painter = painterResource(tile.icon),
                            contentDescription = null, // the label says it
                            tint = Action,
                            modifier = Modifier.size(AmenityIconSize),
                        )
                    }
                    Text(
                        text = stringResource(tile.label),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InfoIconGap)) {
        Icon(painter = painterResource(R.drawable.ic_info), contentDescription = null, tint = muted)
        Text(
            text = stringResource(R.string.details_unavailable),
            style = MaterialTheme.typography.titleMedium,
            color = muted,
        )
    }
}

@Composable
private fun NavigateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(NavigateRadius)
    Button(
        onClick = onClick,
        // Action blue fill: a light ring stands out where an Accent one would not.
        modifier = modifier.focusRing(shape, OnSurface).height(MinTouchTarget),
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
