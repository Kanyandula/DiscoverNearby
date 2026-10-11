package com.kanyandula.discovernearby.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.model.AttributeType
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.attributeTypes
import com.kanyandula.discovernearby.ui.label
import com.kanyandula.discovernearby.ui.theme.AmenityIconSize
import com.kanyandula.discovernearby.ui.theme.AmenityTileHeight
import com.kanyandula.discovernearby.ui.theme.AmenityTileRadius
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.Raised

private class AmenityTile(@param:DrawableRes val icon: Int?, @param:StringRes val label: Int)

private const val TILE_SLOTS = 4 // the concept's row

/** The place's own amenities, then a car park nearby; at most a row of four. */
private fun tilesFor(place: PlaceSummary, carParkNearby: Boolean?): List<AmenityTile> {
    val own = place.attributeTypes().map {
        AmenityTile(if (it == AttributeType.PARKING) R.drawable.ic_parking else null, it.label)
    }
    val carPark = AmenityTile(R.drawable.ic_parking, R.string.car_park_nearby).takeIf { carParkNearby == true }
    return (own + listOfNotNull(carPark)).take(TILE_SLOTS)
}

/**
 * Place Details' amenity tiles (DN-UX-005): [place]'s own amenities and a car park nearby, in four equal slots so a
 * lone tile keeps a quarter of the row as on the concept; nothing when there are none. Display only: not focusable.
 */
@Composable
fun AmenityTiles(place: PlaceSummary, carParkNearby: Boolean?, modifier: Modifier = Modifier) {
    val tiles = tilesFor(place, carParkNearby)
    if (tiles.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(ContentGap)) {
        tiles.forEach { AmenityTile(it, Modifier.weight(1f)) }
        repeat(TILE_SLOTS - tiles.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun AmenityTile(tile: AmenityTile, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(AmenityTileHeight)
            .background(Raised, RoundedCornerShape(AmenityTileRadius))
            .padding(horizontal = ContentGap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The slot stays when there is no icon, so every label sits on the same line.
        Box(Modifier.size(AmenityIconSize)) {
            // Decorative: the label says it. The badge carries its own colours.
            tile.icon?.let { Icon(painter = painterResource(it), contentDescription = null, tint = Color.Unspecified) }
        }
        Text(
            text = stringResource(tile.label),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
