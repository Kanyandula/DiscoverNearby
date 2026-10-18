package com.kanyandula.discovernearby.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** car-ui's `RotaryConstants.ROTARY_VERTICALLY_SCROLLABLE`: the content description the rotary service looks for. */
internal const val ROTARY_VERTICALLY_SCROLLABLE = "com.android.car.ui.utils.VERTICALLY_SCROLLABLE"

/**
 * Rotary (DN-UX-009, DN-UX-010): marks a list for the rotary service, which moves only to rows on screen. Unmarked, a
 * turn at the list's visible edge left it for the screen's Back or the app rail, skipping the rows off screen. Marked,
 * while the list can still scroll that way, the service keeps focus and sends a rotary scroll instead (Android 13's
 * Navigator.findRotateTarget). The focused row answers it by bringing the rows beside it into view (RecommendationRow),
 * and the service moves to one: on the next turn, or right after its scroll on a quick spin, when it takes the list's
 * first or last row on screen. So the reveal shows one neighbour, never more, or a quick spin would skip rows.
 */
fun Modifier.rotaryScrollable(): Modifier = semantics { contentDescription = ROTARY_VERTICALLY_SCROLLABLE }
