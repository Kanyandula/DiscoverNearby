package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.CategoryIconSize
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.FocusRingWidth
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.TileRadius
import com.kanyandula.discovernearby.ui.visual

@Composable
fun CategoryTile(category: DiscoveryCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val visual = category.visual
    val interactions = remember { MutableInteractionSource() }
    // Rotary focus must be clearly visible (docs/02 §16); touch never focuses a tile, so touch never shows it.
    val focused by interactions.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget),
        shape = RoundedCornerShape(TileRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (focused) BorderStroke(FocusRingWidth, MaterialTheme.colorScheme.primary) else null,
        interactionSource = interactions,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ContentGap, Alignment.CenterVertically),
        ) {
            // Decorative: the label names the category.
            Icon(
                painter = painterResource(visual.icon),
                contentDescription = null,
                tint = visual.tint,
                modifier = Modifier.size(CategoryIconSize),
            )
            Text(text = stringResource(visual.label), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(visual.subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
