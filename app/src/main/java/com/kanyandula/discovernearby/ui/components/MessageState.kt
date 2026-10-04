package com.kanyandula.discovernearby.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.ui.theme.Action
import com.kanyandula.discovernearby.ui.theme.ButtonGap
import com.kanyandula.discovernearby.ui.theme.ButtonMinWidth
import com.kanyandula.discovernearby.ui.theme.ButtonRadius
import com.kanyandula.discovernearby.ui.theme.MessageGap
import com.kanyandula.discovernearby.ui.theme.MessageIconSize
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.Raised

/** A centred message with a Back action and, when [onPrimary] is given, a primary action first (docs/02 §9–§13). */
@Composable
internal fun MessageState(
    message: Message,
    @StringRes backLabel: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPrimary: (() -> Unit)? = null,
    @StringRes primaryLabel: Int = R.string.try_again,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MessageGap, Alignment.CenterVertically),
    ) {
        // Decorative: the title says what happened.
        Icon(
            painter = painterResource(message.icon),
            contentDescription = null,
            tint = message.tint,
            modifier = Modifier.size(MessageIconSize),
        )
        Text(
            text = stringResource(message.title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(message.body),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(modifier = Modifier.padding(top = ButtonGap), horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
            if (onPrimary != null) MessageButton(primaryLabel, onPrimary, container = Action, content = Color.White)
            MessageButton(backLabel, onBack, container = Raised, content = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MessageButton(@StringRes label: Int, onClick: () -> Unit, container: Color, content: Color) {
    Button(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = ButtonMinWidth, minHeight = MinTouchTarget),
        shape = RoundedCornerShape(ButtonRadius),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}

/** What a message state says (canvas message artboards). */
internal class Message(
    @param:DrawableRes val icon: Int,
    val tint: Color,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
)
