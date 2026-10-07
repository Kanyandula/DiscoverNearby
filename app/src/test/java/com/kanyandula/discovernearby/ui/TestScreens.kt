package com.kanyandula.discovernearby.ui

import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.kanyandula.discovernearby.DiscoverApplication
import org.robolectric.RuntimeEnvironment

/** Robolectric qualifiers for the reference AVD's automotive_1024p_landscape screen. */
const val AUTOMOTIVE_1024P = "w1024dp-h768dp-land-mdpi"

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
