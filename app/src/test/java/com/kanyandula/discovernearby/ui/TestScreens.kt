package com.kanyandula.discovernearby.ui

import android.view.InputDevice
import android.view.MotionEvent
import androidx.annotation.StringRes
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.kanyandula.discovernearby.DiscoverApplication
import com.kanyandula.discovernearby.ui.theme.MinTouchTarget
import com.kanyandula.discovernearby.ui.theme.RailGap
import org.robolectric.RuntimeEnvironment
import java.time.Year

/** Robolectric qualifiers for the reference AVD's automotive_1024p_landscape screen. */
const val AUTOMOTIVE_1024P = "w1024dp-h768dp-land-mdpi"

/**
 * The panel a destination gets on the reference AVD: 768 dp, less the system bars (76 + 96), the app header (56),
 * the outer bottom padding (24) and the panel's own padding (24 + 24).
 */
val ReferencePanelHeight = 468.dp

/** The panel's width on the reference AVD: 928 dp, less the app rail and its gap (DN-UX-006). */
val ReferencePanelWidth = 928.dp - MinTouchTarget - RailGap

/** The app's own container, as MainActivity passes it (Robolectric creates the keyless TestDiscoverApplication). */
fun appContainer() = (RuntimeEnvironment.getApplication() as DiscoverApplication).container

/** A string resource as the app shows it. */
fun text(@StringRes id: Int): String = RuntimeEnvironment.getApplication().getString(id)

/** A rail tab, found by its label: "Saved", "Map" and "Profile" are also screen titles, which aren't tabs. */
fun railTab(label: String) = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

/** Rotary puts Compose in keyboard mode, where a clickable takes focus; touch never focuses one. */
fun ComposeTestRule.useRotaryInput(inputModes: InputModeManager) {
    runOnIdle { inputModes.requestInputMode(InputMode.Keyboard) }
}

/** A rotary select as RotaryService delivers it: keyboard mode, focus on the node, then the centre key. */
fun ComposeTestRule.rotarySelect(node: SemanticsNodeInteraction, inputModes: InputModeManager) {
    useRotaryInput(inputModes)
    node.requestFocus()
    node.performKeyInput { pressKey(Key.DirectionCenter) }
}

/** HERE's copyright notice as the screens draw it this year (ADR-001 V6a). */
fun hereNotice() = "© ${Year.now().value} HERE"

/**
 * A rotary scroll as Android 13's rotary service sends one to a list it can't leave (RotaryService.injectScrollEvent):
 * ACTION_SCROLL from the rotary encoder, AXIS_VSCROLL −detents clockwise.
 */
fun rotaryScroll(
    detents: Float,
    source: Int = InputDevice.SOURCE_ROTARY_ENCODER,
    axis: Int = MotionEvent.AXIS_VSCROLL,
): MotionEvent {
    val properties = MotionEvent.PointerProperties().apply { id = 0 }
    val coords = MotionEvent.PointerCoords().apply { setAxisValue(axis, detents) }
    return MotionEvent.obtain(
        0L, 0L, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, source, 0,
    )
}
