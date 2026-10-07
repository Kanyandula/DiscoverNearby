package com.kanyandula.discovernearby.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.R
import java.time.Year

/**
 * The data provider's copyright notice, "© {year} {holder}", in the muted style; nothing when the data needs none
 * (the fakes). HERE's brand guidance asks for "© 20XX HERE" (ADR-001 V6a); the year is the device's.
 */
@Composable
fun ProviderAttribution(holder: String?, modifier: Modifier = Modifier) {
    if (holder == null) return
    val year = Year.now().value
    Text(
        text = stringResource(R.string.provider_attribution, year, holder),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
