package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.kanyandula.discovernearby.ui.RailTab
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.OnSurface
import com.kanyandula.discovernearby.ui.theme.OnSurfaceVariant
import com.kanyandula.discovernearby.ui.theme.RailGap
import com.kanyandula.discovernearby.ui.theme.RailIconSize
import com.kanyandula.discovernearby.ui.theme.Raised
import com.kanyandula.discovernearby.ui.theme.RowRadius

/** Icons only, as in the concept (03b-place-details-concept); the selected tab on Raised grey. */
@Composable
fun AppRail(selected: RailTab, onSelect: (RailTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RailGap)) {
        RailTab.entries.forEach { tab -> RailItem(tab, selected = tab == selected, onClick = { onSelect(tab) }) }
    }
}

@Composable
private fun RailItem(tab: RailTab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(RowRadius)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.focusRing(shape).size(MinTouchTarget).semantics { role = Role.Tab },
        shape = shape,
        color = if (selected) Raised else Color.Transparent,
        contentColor = if (selected) OnSurface else OnSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(tab.icon),
                contentDescription = stringResource(tab.label),
                modifier = Modifier.size(RailIconSize),
                tint = if (tab.tinted) LocalContentColor.current else Color.Unspecified,
            )
        }
    }
}
