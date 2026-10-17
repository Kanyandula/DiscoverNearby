package com.kanyandula.discovernearby.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.components.Message
import com.kanyandula.discovernearby.ui.components.MessageState
import com.kanyandula.discovernearby.ui.components.ScreenHeader
import com.kanyandula.discovernearby.ui.theme.Highlight
import com.kanyandula.discovernearby.ui.theme.RowGap

/** A rail tab whose feature isn't in this version, Map or Profile (DN-UX-011): its name, "Coming soon", and Back. */
@Composable
fun ComingSoonScreen(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    @StringRes body: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        ScreenHeader(title = stringResource(title), onBack = onBack)
        MessageState(
            Message(icon, Highlight, R.string.coming_soon_title, body),
            backLabel = R.string.back,
            onBack = onBack,
            modifier = Modifier.weight(1f),
        )
    }
}
