package com.kanyandula.discovernearby.ui

import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
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
