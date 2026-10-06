package com.kanyandula.discovernearby.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.kanyandula.discovernearby.ui.theme.Accent
import com.kanyandula.discovernearby.ui.theme.FocusRingWidth

/**
 * Rotary focus (docs/02 §16, V7): a [FocusRingWidth] outline while the element after it in the chain has focus.
 * Touch never focuses a clickable, so touch never shows it. Pick a [color] that stands out from the fill.
 */
fun Modifier.focusRing(shape: Shape, color: Color = Accent): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(FocusRingWidth, color, shape) else Modifier)
}
