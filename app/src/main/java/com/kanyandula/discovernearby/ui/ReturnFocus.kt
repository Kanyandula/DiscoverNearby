package com.kanyandula.discovernearby.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Rotary focus after Back (docs/02 §16, V7). Remembers the item last selected by rotary and gives it focus again
 * one frame after its screen returns.
 *
 * Why a frame late: on 2026-10-06 (ADR-002, E1), focus that Compose set in a returning screen's first frame was
 * never reported to the rotary service. The service kept the ComposeView host, and the driver's next turn was
 * spent finding focus. A change a frame later is reported, as it is when a screen opens.
 *
 * Only an item that had focus when selected is remembered: rotary selects the focused item, while a touch never
 * focuses one, so touch never leaves a ring behind.
 */
class ReturnFocus internal constructor(private val target: MutableState<String?>) {
    private val requester = FocusRequester()
    private var focused: String? = null

    /** The modifier for the item identified by [key]; put it before the item's clickable. */
    fun item(key: String): Modifier = Modifier
        .onFocusChanged { if (it.isFocused) focused = key else if (focused == key) focused = null }
        .then(if (key == target.value) Modifier.focusRequester(requester) else Modifier)

    /** Call from the item's onClick: remembers [key] if it has focus, otherwise forgets any earlier item. */
    fun selected(key: String) {
        target.value = key.takeIf { it == focused }
    }

    // ponytail: requestFocus returns false when the item is gone (e.g. trimmed by the driving limit); nothing moves.
    internal suspend fun restore() {
        if (target.value == null) return
        withFrameNanos { }
        requester.requestFocus()
    }
}

@Composable
fun rememberReturnFocus(): ReturnFocus {
    val target = rememberSaveable { mutableStateOf<String?>(null) }
    val returnFocus = remember { ReturnFocus(target) }
    LaunchedEffect(returnFocus) { returnFocus.restore() }
    return returnFocus
}
