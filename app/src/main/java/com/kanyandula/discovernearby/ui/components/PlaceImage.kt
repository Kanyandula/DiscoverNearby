package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.visual

private const val ARTWORK_TINT_ALPHA = 0.16f

/**
 * The place's image area on rows and Place Details (canvas "[Provider photo]"): the provider's [photoUrl] (Tripadvisor,
 * DN-UX-004), cropped to the area, over our own category artwork (the category's icon on its tint), which shows while
 * the photo loads, when it fails and when there is none. Decorative: the name next to it says what the place is.
 */
@Composable
fun PlaceImage(
    category: DiscoveryCategory,
    photoUrl: String?,
    radius: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val visual = category.visual
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier.background(visual.tint.copy(alpha = ARTWORK_TINT_ALPHA), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(visual.icon),
            contentDescription = null,
            tint = visual.tint,
            modifier = Modifier.size(iconSize),
        )
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(shape),
            )
        }
    }
}
