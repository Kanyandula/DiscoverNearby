package com.kanyandula.discovernearby.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.CategoryIconSize
import com.kanyandula.discovernearby.ui.theme.CategoryLabelSize
import com.kanyandula.discovernearby.ui.theme.CategorySubtitleSize
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.TileRadius
import com.kanyandula.discovernearby.ui.visual

@Composable
fun CategoryTile(category: DiscoveryCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val visual = category.visual
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget),
        shape = RoundedCornerShape(TileRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            Text(text = stringResource(visual.label), fontSize = CategoryLabelSize, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(visual.subtitle),
                fontSize = CategorySubtitleSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
