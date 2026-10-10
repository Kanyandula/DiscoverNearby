package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.visual

private const val ARTWORK_TINT_ALPHA = 0.16f

/**
 * The place's image area on rows and Place Details (canvas "[Provider photo]"). Decorative: the name next to it says
 * what the place is. ponytail: our own category artwork only (the category's icon on its tint); a provider photo
 * replaces it once DN-SP-004 picks a source: the layout stays, and this gains the photo as a parameter.
 */
@Composable
fun PlaceImage(category: DiscoveryCategory, radius: Dp, iconSize: Dp, modifier: Modifier = Modifier) {
    val visual = category.visual
    Box(
        modifier = modifier.background(visual.tint.copy(alpha = ARTWORK_TINT_ALPHA), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(visual.icon),
            contentDescription = null,
            tint = visual.tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
