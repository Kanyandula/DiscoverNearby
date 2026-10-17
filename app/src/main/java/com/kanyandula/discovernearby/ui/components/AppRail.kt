package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kanyandula.discovernearby.ui.RailTab
import com.kanyandula.discovernearby.ui.theme.Accent
import com.kanyandula.discovernearby.ui.theme.Action
import com.kanyandula.discovernearby.ui.theme.ContentGap
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.OnSurfaceVariant
import com.kanyandula.discovernearby.ui.theme.PanelRadius
import com.kanyandula.discovernearby.ui.theme.RailIconSize
import com.kanyandula.discovernearby.ui.theme.RailLabelGap
import com.kanyandula.discovernearby.ui.theme.RailPadding
import com.kanyandula.discovernearby.ui.theme.RailTabGap
import com.kanyandula.discovernearby.ui.theme.RailTabHeight
import com.kanyandula.discovernearby.ui.theme.RailTileHeight
import com.kanyandula.discovernearby.ui.theme.RailTileWidth
import com.kanyandula.discovernearby.ui.theme.Raised
import com.kanyandula.discovernearby.ui.theme.RowRadius

/**
 * The app rail as the Product Lead's artboards R1 and R2 (DN-UX-011): its own panel, labelled tabs in rotary order,
 * Profile at the bottom under a divider; the selected tab on an Action-blue tile with an Accent label.
 */
@Composable
fun AppRail(selected: RailTab, onSelect: (RailTab) -> Unit, modifier: Modifier = Modifier) {
    val (bottom, top) = RailTab.entries.partition { it.atBottom }
    Surface(modifier = modifier.width(MinTouchTarget).fillMaxHeight(), shape = RoundedCornerShape(PanelRadius)) {
        Column(
            modifier = Modifier.padding(vertical = RailPadding),
            verticalArrangement = Arrangement.spacedBy(RailTabGap),
        ) {
            top.forEach { tab -> RailItem(tab, selected = tab == selected, onClick = { onSelect(tab) }) }
            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(modifier = Modifier.padding(horizontal = ContentGap), color = Raised)
            bottom.forEach { tab -> RailItem(tab, selected = tab == selected, onClick = { onSelect(tab) }) }
        }
    }
}

@Composable
private fun RailItem(tab: RailTab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(RowRadius)
    val name = stringResource(tab.label)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.focusRing(shape).size(MinTouchTarget, RailTabHeight).semantics { role = Role.Tab },
        shape = shape,
        color = Color.Transparent,
        contentColor = if (selected) Color.White else OnSurfaceVariant,
    ) {
        // The label is the tab's name, read once: the visible label adds nothing to it.
        Column(
            modifier = Modifier.padding(top = RailLabelGap).clearAndSetSemantics { contentDescription = name },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RailLabelGap),
        ) {
            Box(
                modifier = Modifier
                    .size(RailTileWidth, RailTileHeight)
                    .background(if (selected) Action else Color.Transparent, shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(tab.icon),
                    contentDescription = null,
                    modifier = Modifier.size(RailIconSize),
                    tint = if (tab.tinted) LocalContentColor.current else Color.Unspecified,
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) Accent else OnSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
