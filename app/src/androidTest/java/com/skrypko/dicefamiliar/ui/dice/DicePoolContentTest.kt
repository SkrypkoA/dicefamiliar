package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.domain.dice.pool.Comparison
import com.skrypko.dicefamiliar.domain.dice.pool.DicePool
import com.skrypko.dicefamiliar.domain.dice.pool.PoolGroups
import com.skrypko.dicefamiliar.domain.dice.pool.SuccessCriterion
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DicePoolContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val pool = DicePool.of(6, listOf(1, 1, 3, 4, 5, 5, 6, 6, 3, 4))
    private val groups = PoolGroups().add(SuccessCriterion(1, Comparison.EQUAL)).add(SuccessCriterion(5)).copy(selectedId = null)

    private fun show(state: DiceUiState) = compose.setContent {
        DiceFamiliarTheme {
            DiceContent(
                state, {}, {}, {}, {}, onClear = { calls += "clear" }, {}, {},
                pool = DicePoolActions(
                    onModeChange = { calls += "mode $it" },
                    onToggleGroup = { calls += "toggle $it" },
                    onReroll = { calls += "reroll" },
                    onRemoveGroup = { calls += "remove $it" },
                    onRollGroup = { calls += "roll group" },
                ),
            )
        }
    }

    private fun poolState(selected: Int? = null, quickRoll: Boolean = false) = DiceUiState(
        notation = "10d6",
        poolMode = true,
        poolCountText = "10",
        pool = pool,
        poolGroups = groups.copy(selectedId = selected),
        settings = AppSettings(animationsEnabled = false, quickRoll = quickRoll),
    )

    @Test
    fun poolModeHidesSumControls() {
        show(poolState())
        compose.onNodeWithText("d100").assertDoesNotExist()
        compose.onNodeWithText("d20").assertIsDisplayed()
        compose.onNodeWithText("+1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show formula").assertDoesNotExist()
        compose.onNodeWithTag(POOL_COUNT_FIELD_TAG).assertTextEquals("How many", "10")
        compose.onNodeWithText("Sum").performClick()
        assertEquals(listOf("mode false"), calls)
    }

    @Test
    fun chipsShowHowManyDiceTheyHold() {
        show(poolState())
        compose.onNodeWithContentDescription("Group =1, 2 dice").assertIsDisplayed()
        compose.onNodeWithContentDescription("Group 5+, 4 dice").performClick()
        assertEquals(listOf("toggle 2"), calls)
        compose.onNodeWithTag(POOL_BAR_TAG).assertTextEquals("10d6")
        compose.onNodeWithTag(POOL_REROLL_TAG).assertDoesNotExist()
    }

    @Test
    fun selectedGroupIsHighlightedCountedAndActionable() {
        show(poolState(selected = 2, quickRoll = true))
        compose.onNodeWithTag(POOL_GRID_TAG).assertIsDisplayed()
        compose.onAllNodesWithTag(POOL_GROUP_DIE_TAG, useUnmergedTree = true).assertCountEquals(4)
        compose.onNodeWithTag(POOL_BAR_TAG).assertTextEquals("10d6 · 5+ → 4 successes")
        compose.onNodeWithTag(POOL_REROLL_TAG).assertIsEnabled().performClick()
        compose.onNodeWithTag(POOL_ROLL_GROUP_TAG).assertIsEnabled().performClick()
        assertEquals(listOf("reroll", "roll group"), calls)
    }

    @Test
    fun addButtonOpensTheCriterionEditor() {
        show(poolState())
        compose.onNodeWithTag(POOL_GROUP_ADD_TAG).performClick()
        compose.onNodeWithText("≥ (+)").assertIsDisplayed()
        compose.onNodeWithText("≤ (−)").assertIsDisplayed()
        compose.onNodeWithText("Group: 4+").assertIsDisplayed()
    }

    @Test
    fun longPressDeletesAGroupAfterConfirmation() {
        show(poolState())
        compose.onNodeWithContentDescription("Group =1, 2 dice").performTouchInput { longClick() }
        compose.onNodeWithText("Delete group?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Group =1, 2 dice").performTouchInput { longClick() }
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("remove 1"), calls)
    }

    @Test
    fun clearButtonClearsInPoolMode() {
        show(poolState())
        compose.onNodeWithContentDescription("Clear").assertIsEnabled().performClick()
        assertEquals(listOf("clear"), calls)
    }

    @Test
    fun smallPoolsStay3D() {
        show(poolState(selected = 2))
        compose.onNodeWithTag(POOL_SOLID_TAG).assertIsDisplayed()
        compose.onNodeWithTag(POOL_GRID_TAG).assertDoesNotExist()
        compose.onNodeWithTag(POOL_BAR_TAG).assertTextEquals("10d6 · 5+ → 4 successes")
    }

    @Test
    fun bigPoolsUseTheFlatGridToFitTheTray() {
        show(poolState().copy(pool = DicePool.of(6, List(40) { it % 6 + 1 })))
        compose.onNodeWithTag(POOL_SOLID_TAG).assertDoesNotExist()
        compose.onNodeWithTag(POOL_GRID_TAG).assertIsDisplayed()
        compose.onNodeWithTag(POOL_BAR_TAG).assertTextEquals("40d6")
    }
}
