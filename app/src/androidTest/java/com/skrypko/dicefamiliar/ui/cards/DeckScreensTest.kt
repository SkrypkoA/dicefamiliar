package com.skrypko.dicefamiliar.ui.cards

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.data.cards.DeckInfo
import com.skrypko.dicefamiliar.domain.cards.DeckKind
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun deckListShowsPresetsCustomAndUploaded() {
        val picked = mutableListOf<String>()
        val edits = mutableListOf<String?>()
        val decks = listOf(
            DeckInfo("preset-36", "", DeckKind.PRESET, 36, 36),
            DeckInfo("preset-52", "", DeckKind.PRESET, 52, 52),
            DeckInfo("preset-54", "", DeckKind.PRESET, 54, 54),
            DeckInfo("mine", "Mine", DeckKind.CUSTOM, 20),
            DeckInfo("omens", "omens", DeckKind.UPLOADED, 54),
        )
        compose.setContent {
            DiceFamiliarTheme {
                DeckListContent(DeckListUiState(decks, "preset-52"), onBack = {}, onSelect = { picked += it }, onEditCustom = { edits += it }, onDelete = {})
            }
        }
        compose.onNodeWithText("36 cards").assertIsDisplayed()
        compose.onAllNodesWithText("54 cards").onFirst().performClick()
        compose.onNodeWithText("Mine").assertIsDisplayed()
        compose.onNodeWithText("omens").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit Mine").performClick()
        compose.onNodeWithText("New custom deck").performClick()
        assertEquals(listOf("preset-54"), picked)
        assertEquals(listOf("mine", null), edits)
    }

    @Test
    fun customEditorListsJokersFirst() {
        compose.setContent {
            DiceFamiliarTheme {
                CustomDeckContent(CustomDeckUiState(loaded = true), onBack = {}, onNameChange = {}, onToggle = {}, onToggleGroup = {}, onSave = {})
            }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        val jokers = compose.onNodeWithText("Jokers").getUnclippedBoundsInRoot()
        val spades = compose.onNodeWithText("Spades").getUnclippedBoundsInRoot()
        assertTrue(jokers.top < spades.top)
        compose.onNodeWithContentDescription("Red Joker").assertIsDisplayed()
    }
}
