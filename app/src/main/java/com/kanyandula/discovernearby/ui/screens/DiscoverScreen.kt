package com.kanyandula.discovernearby.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.components.CategoryTile
import com.kanyandula.discovernearby.ui.theme.GridGap

private const val GRID_COLUMNS = 3
private val Rows = DiscoveryCategory.entries.chunked(GRID_COLUMNS)

@Composable
fun DiscoverScreen(onCategorySelected: (DiscoveryCategory) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(GridGap)) {
        Rows.forEach { row ->
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                row.forEach { category ->
                    CategoryTile(
                        category = category,
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}
