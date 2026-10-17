package com.kanyandula.discovernearby.ui

import android.app.Activity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs

/** car-ui's `RotaryConstants.ROTARY_VERTICALLY_SCROLLABLE`: the content description the rotary service looks for. */
internal const val ROTARY_VERTICALLY_SCROLLABLE = "com.android.car.ui.utils.VERTICALLY_SCROLLABLE"

/**
 * Rotary (DN-UX-009, DN-UX-010): marks a list for the rotary service, which moves only to rows on screen. Unmarked, a
 * turn at the list's visible edge left it for the screen's Back or the app rail, skipping the rows off screen. Marked,
 * while the list can still scroll that way, the service keeps focus and sends a rotary scroll instead (Android 13's
 * Navigator.findRotateTarget), which [moveFocusForRotaryScroll] turns into a move to the next or previous row.
 */
fun Modifier.rotaryScrollable(): Modifier = semantics { contentDescription = ROTARY_VERTICALLY_SCROLLABLE }

/**
 * The rotary service's scroll of a [rotaryScrollable] list, as a move to the next row (clockwise) or the previous one,
 * a row per detent. Compose can't act on that scroll itself: it reads a rotary event's AXIS_SCROLL, not the
 * AXIS_VSCROLL the service sends, and the service's own move after it uses a focus search Compose doesn't support.
 * Tab and Shift+Tab are Compose's next and previous, which compose a row off screen and bring it into view. Returns
 * false, leaving [event] alone, when it is no such scroll.
 */
fun Activity.moveFocusForRotaryScroll(event: MotionEvent): Boolean {
    val detents = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toInt()
    val isListScroll = event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) &&
        event.actionMasked == MotionEvent.ACTION_SCROLL && detents != 0
    if (isListScroll) {
        val meta = if (detents > 0) KeyEvent.META_SHIFT_ON else 0 // clockwise, down the list, is negative
        repeat(abs(detents)) {
            dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0, meta))
            dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0, meta))
        }
    }
    return isListScroll
}
