package com.kanyandula.discovernearby.ui.screens

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.kanyandula.discovernearby.model.Closing
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.ui.SEPARATOR
import com.kanyandula.discovernearby.ui.clockTime
import com.kanyandula.discovernearby.ui.components.AmenityTiles
import com.kanyandula.discovernearby.ui.components.Message
import com.kanyandula.discovernearby.ui.components.MessageState
import com.kanyandula.discovernearby.ui.components.PlaceImage
import com.kanyandula.discovernearby.ui.components.ProviderAttribution
import com.kanyandula.discovernearby.ui.components.ScreenHeader
import com.kanyandula.discovernearby.ui.components.SupportingLine
import com.kanyandula.discovernearby.ui.components.TripadvisorRating
import com.kanyandula.discovernearby.ui.components.focusRing
import com.kanyandula.discovernearby.ui.kilometres
import com.kanyandula.discovernearby.ui.kindLabel
import com.kanyandula.discovernearby.ui.label
import com.kanyandula.discovernearby.ui.theme.Accent
import com.kanyandula.discovernearby.ui.theme.Action
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
 * Place Details as the Product Lead's artboard 3c (DN-UX-007), from the concept (DN-UX-005): Back and the name as the
 * header; the photo under Back, with what is known about the place and Tripadvisor's description beside it; the
 * amenity tiles; Navigate bottom right, with Save ↔ Saved beside it (DN-UX-006). Navigate never waits on the optional
 * details call (docs/02 §7), and one layout serves every state but the hand-off failure, so the actions stay put when
 * details or the car park arrive (the tile row can appear late, under the photo). [enrichment] is the row's
 * Tripadvisor data (DN-UX-004, DN-UX-005); the description is hidden while [drivingRestricted]: cars limit text then.
 */
@Suppress("LongParameterList") // the state plus one lambda per user action
@Composable
fun PlaceDetailsScreen(
    distanceMeters: Int?,
    category: DiscoveryCategory,
    state: PlaceDetailsUiState,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    saved: Boolean,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
    enrichment: PlaceEnrichment? = null,
    drivingRestricted: Boolean = true, // unknown means restrictions apply (docs/03 §6)
) {
    if (state is PlaceDetailsUiState.NavigationUnavailable) {
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
            ScreenHeader(title = state.summary.name, onBack = onBack)
            // docs/02 §14, design 11: Back is the only action, and it leaves the destination like the header's.
            MessageState(NavigationUnavailableMessage, R.string.back, onBack = onBack, modifier = Modifier.weight(1f))
        }
    } else {
        val content = state as? PlaceDetailsUiState.Content
        val summaryOnly = state is PlaceDetailsUiState.SummaryOnly
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DetailsGap)) {
            // Back and the name head the page as on the other screens (DN-UX-007): the photo lines up with the tiles.
            ScreenHeader(title = state.summary.name, onBack = onBack)
            // Squeezed first on a short panel; the header, the actions and the notice always stay whole.
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DetailsGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ContentGap)) {
                    Photo(category, enrichment?.photoUrl)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RowLineGap)) {
                        Summary(
                            place = state.summary,
                            distanceMeters = distanceMeters,
                            opening = openingLine(state.summary, content?.closing, content?.details?.openingSummary),
                            enrichment = enrichment,
                            summaryOnly = summaryOnly,
                        )
                        // One line on the fallback, where the note takes the other (PlaceDetailsFitTest).
                        enrichment?.description?.takeUnless { drivingRestricted }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = if (summaryOnly) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                AmenityTiles(state.summary, content?.carParkNearby)
            }
            Column(verticalArrangement = Arrangement.spacedBy(PhotoCreditGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
                    // Save ↔ Saved (DN-UX-006) on Raised grey, so the Accent ring shows on it.
                    ActionButton(
                        icon = if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                        label = if (saved) R.string.saved else R.string.save,
                        container = Raised,
                        content = OnSurface,
                        onClick = onToggleSave,
                        modifier = Modifier.weight(1f),
                    )
                    // Action blue fill: a light ring stands out where an Accent one would not.
                    ActionButton(
                        icon = R.drawable.ic_navigate,
                        label = R.string.navigate,
                        container = Action,
                        content = Color.White,
                        onClick = onNavigate,
                        modifier = Modifier.weight(1f),
                        ring = OnSurface,
                    )
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
 * Tripadvisor's rating as Tripadvisor draws it, else the place's own; "Kind · price · distance"; the opening line; and
 * on the fallback the summary-only note. Each only when known, as lines of the caller's column beside the photo.
 */
@Composable
private fun ColumnScope.Summary(
    place: PlaceSummary,
    distanceMeters: Int?,
    opening: AnnotatedString?,
    enrichment: PlaceEnrichment?,
    summaryOnly: Boolean,
) {
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
        distanceMeters?.let { stringResource(R.string.distance_away, kilometres(it)) },
    )
    SupportingLine(facts.joinToString(SEPARATOR))
    opening?.let { SupportingLine(it) }
    if (summaryOnly) DetailsUnavailable()
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
 * "Open · Closes 18:00", "Open 24 hours" or "Open now" while open, the open part green as in the concept; "Closed
 * now"; HERE's weekly text only when the status is unknown. The time follows the car's 12/24-hour setting.
 */
@Composable
private fun openingLine(place: PlaceSummary, closing: Closing?, openingSummary: String?): AnnotatedString? {
    fun open(text: String) = AnnotatedString(text, SpanStyle(color = OpenNow))
    return when (place.isOpenNow) {
        true -> when (closing) {
            Closing.AllDay -> open(stringResource(R.string.open_all_day))
            is Closing.At -> open(stringResource(R.string.open)) + AnnotatedString(
                SEPARATOR + stringResource(
                    R.string.closes_at,
                    clockTime(closing.time, DateFormat.is24HourFormat(LocalContext.current)),
                ),
            )
            null -> open(stringResource(R.string.open_now))
        }
        false -> AnnotatedString(stringResource(R.string.closed_now))
        null -> openingSummary?.let(::AnnotatedString)
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

/** Save and Navigate: an icon and a label on a 76 dp button. The label says what it does, so the icon is decorative. */
@Composable
private fun ActionButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ring: Color = Accent,
) {
    val shape = RoundedCornerShape(NavigateRadius)
    Button(
        onClick = onClick,
        modifier = modifier.focusRing(shape, ring).height(MinTouchTarget),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(NavigateIconSize))
        Spacer(modifier = Modifier.width(NavigateIconGap))
        Text(text = stringResource(label), style = MaterialTheme.typography.headlineSmall)
    }
}
