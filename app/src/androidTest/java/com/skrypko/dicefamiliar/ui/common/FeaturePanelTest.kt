package com.skrypko.dicefamiliar.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeaturePanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun chevronHidesThePanelWhileShareHistoryAndHelpStay() {
        val calls = mutableListOf<String>()
        compose.setContent {
            DiceFamiliarTheme {
                var visible by remember { mutableStateOf(true) }
                Column {
                    FeaturePanel(visible = visible, animate = false) { Text("Panel content") }
                    FeatureHandleBar(
                        panelVisible = visible,
                        onTogglePanel = { visible = !visible },
                        hideDescription = "Hide it",
                        showDescription = "Show it",
                        helpDescription = "Help me",
                        onHelp = { calls += "help" },
                        share = FeatureShare("Share it", { calls += "copy" }, { calls += "text" }, { calls += "image" }, enabled = true),
                        history = FeatureHistory("History", onClick = null),
                    )
                }
            }
        }
        compose.onNodeWithText("Panel content").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide it").performClick()
        compose.onNodeWithText("Panel content").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show it").assertIsDisplayed()
        compose.onNodeWithContentDescription("History").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Help me").performClick()
        listOf("Copy" to "copy", "Share as text" to "text", "Share as image" to "image").forEach { (item, _) ->
            compose.onNodeWithContentDescription("Share it").performClick()
            compose.onNodeWithText(item).performClick()
        }
        assertEquals(listOf("help", "copy", "text", "image"), calls)
    }
}
