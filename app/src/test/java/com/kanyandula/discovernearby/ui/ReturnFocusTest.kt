package com.kanyandula.discovernearby.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// V7 (ADR-002): Back gives rotary focus back to the item selected by rotary, a frame after the screen returns.
@RunWith(RobolectricTestRunner::class)
class ReturnFocusTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var inputModes: InputModeManager
    private var keys by mutableStateOf(listOf("A", "B", "C"))
    private var shown by mutableStateOf(true)

    @Composable
    private fun Items() {
        val returnFocus = rememberReturnFocus()
        Column {
            keys.forEach { key ->
                Button(onClick = { returnFocus.selected(key) }, modifier = returnFocus.item(key)) { Text(key) }
            }
        }
    }

    /** The screen leaves and comes back with its saved state, as a NavHost destination does. */
    private fun showItems() {
        rule.setContent {
            inputModes = LocalInputModeManager.current
            val holder = rememberSaveableStateHolder()
            if (shown) holder.SaveableStateProvider("screen") { Items() }
        }
    }

    private fun rotarySelect(text: String) = rule.rotarySelect(rule.onNodeWithText(text), inputModes)

    private fun leaveAndReturn(newKeys: List<String> = keys) {
        rule.runOnIdle { shown = false }
        rule.runOnIdle {
            keys = newKeys
            shown = true
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(SETTLE_MS) // past ReturnFocus's wait for Compose's semantics snapshot
        rule.waitForIdle()
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }

    @Test
    fun rotarySelectionGetsFocusBack() {
        showItems()
        rotarySelect("B")
        leaveAndReturn()
        rule.onNodeWithText("B").assertIsFocused()
    }

    @Test
    fun touchSelectionReplacesARotarySelection() {
        showItems()
        rotarySelect("B")
        rule.onNodeWithText("C").performClick() // touch: switches to touch mode, so nothing is remembered
        leaveAndReturn()
        rule.onNodeWithText("B").assertIsNotFocused()
        rule.onNodeWithText("C").assertIsNotFocused()
    }

    // Review Focus 1: the item is gone when the screen returns (e.g. the driving limit trimmed the list).
    @Test
    fun returnToAKeyNoLongerShownDoesNothing() {
        showItems()
        rotarySelect("C")
        leaveAndReturn(listOf("A", "B"))
        rule.onNodeWithText("A").assertIsNotFocused()
        rule.onNodeWithText("B").assertIsNotFocused()
    }
}
