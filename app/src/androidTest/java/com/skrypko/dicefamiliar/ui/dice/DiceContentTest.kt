package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.domain.dice.DiceNotationParser
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.dice.ParseResult
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.random.RandomSource
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiceContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun record(notation: String, vararg values: Int): RollRecord {
        val queue = ArrayDeque(values.toList())
        val random = object : RandomSource {
            override fun nextInt(from: Int, until: Int) = queue.removeFirst()
            override fun nextLong(from: Long, until: Long) = error("unused")
            override fun nextDouble(from: Double, until: Double) = error("unused")
        }
        val expression = (DiceNotationParser.parse(notation) as ParseResult.Success).expression
        val me = Participant("me", "Me")
        return RollRecord("1", notation, RollMode.NORMAL, null, DiceRoller(random).roll(expression), me, me, 0L)
    }

    private fun show(
        state: DiceUiState,
        onToggleFormula: () -> Unit = {},
        onTogglePanel: () -> Unit = {},
    ) = compose.setContent {
        DiceFamiliarTheme {
            DiceContent(
                state, {}, {}, {}, {}, {}, {}, {},
                onToggleFormula = onToggleFormula,
                onTogglePanel = onTogglePanel,
            )
        }
    }

    /** Swipe mode (Quick roll off): 3D dice in the tray, sum in the bar below. Also guards the nested-scroll crash. */
    @Test
    fun swipeModeShowsSolidDiceAndSumBar() {
        val latest = record("2d6+1d4+3", 3, 5, 2)
        show(DiceUiState(notation = "2d6+1d4+3", latest = latest, recent = listOf(latest, latest)))
        compose.onNodeWithText("2d6+1d4+3 = 13").assertIsDisplayed()
        compose.onNodeWithContentDescription("d6 rolled 3, d6 rolled 5, d4 rolled 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Recent rolls").assertIsDisplayed()
    }

    @Test
    fun quickRollShowsFlatDiceAndRollButton() {
        val latest = record("4d6kh3", 2, 6, 1, 5)
        show(
            DiceUiState(
                notation = "4d6kh3",
                latest = latest,
                settings = AppSettings(quickRoll = true, animationsEnabled = false),
            ),
        )
        compose.onNodeWithText("4d6kh3 = 13").assertIsDisplayed()
        compose.onNodeWithContentDescription("d6 rolled 1, dropped").assertExists()
        compose.onNodeWithText("Roll dice").assertIsDisplayed()
    }

    @Test
    fun d100IsShownAsPercentilePair() {
        val latest = record("1d100", 73)
        show(DiceUiState(notation = "1d100", latest = latest, settings = AppSettings(quickRoll = true)))
        compose.onNodeWithText("1d100 = 73").assertIsDisplayed()
        compose.onNodeWithText("70", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun formulaFieldHiddenByDefaultWithToggle() {
        var toggled = false
        show(DiceUiState(notation = "2d6+1"), onToggleFormula = { toggled = true })
        // Fully hidden: neither the field nor the formula text is shown.
        compose.onNodeWithText("Formula").assertDoesNotExist()
        compose.onNodeWithText("2d6+1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show formula").performClick()
        assert(toggled)
    }

    @Test
    fun formulaFieldShownWhenVisible() {
        show(DiceUiState(notation = "2d6+1", formulaVisible = true))
        compose.onNodeWithText("Formula").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide formula").assertIsDisplayed()
    }

    @Test
    fun panelCanBeHiddenToGiveTheTrayTheSpace() {
        var toggled = false
        show(DiceUiState(notation = "1d6", panelVisible = false), onTogglePanel = { toggled = true })
        compose.onNodeWithContentDescription("Add d6").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show dice").assertIsDisplayed().performClick()
        assert(toggled)
    }

    @Test
    fun panelShownByDefault() {
        show(DiceUiState(notation = "1d6"))
        compose.onNodeWithContentDescription("Add d6").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide dice").assertIsDisplayed()
        // The toggle is a chevron only, with no text label.
        compose.onNodeWithText("Hide dice").assertDoesNotExist()
    }

    @Test
    fun clearIsAnIconButtonAndModifiersAreButtons() {
        show(DiceUiState(notation = "2d6"))
        compose.onNodeWithContentDescription("Clear").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("+1").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("−1").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun customDieButtonShowsItsValueAndAddsItWithoutAsking() {
        var added: Int? = null
        compose.setContent {
            DiceFamiliarTheme {
                DiceContent(
                    DiceUiState(notation = "1d7", customSides = 7, paletteCounts = mapOf(DiceViewModel.CUSTOM_DIE to 1)),
                    {}, { added = it }, {}, {}, {}, {}, {},
                )
            }
        }
        compose.onNodeWithContentDescription("Add d7, 1 d7 in formula").assertIsDisplayed().performClick()
        assert(added == 7)
        compose.onNodeWithText("Custom die").assertDoesNotExist()
    }

    private fun sidesValue() = compose.onNodeWithTag(CUSTOM_SIDES_FIELD)
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text.toInt()

    @Test
    fun customDieFieldSwipesUpAndDownToStep() {
        var added: Int? = null
        compose.setContent {
            DiceFamiliarTheme { DiceContent(DiceUiState(), {}, { added = it }, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithContentDescription("Add a custom die").performClick()
        val field = compose.onNodeWithTag(CUSTOM_SIDES_FIELD)
        // Typing focuses the field (cursor + handle shown), like a real user.
        field.performClick()
        field.performTextInput("20")

        // Slow finger swipes starting at the field centre and leaving the field.
        field.performTouchInput { swipe(center, center.copy(y = center.y - 300f), durationMillis = 600) }
        val afterUp = sidesValue()
        assert(afterUp > 20) { "swipe up should increase, got $afterUp" }

        field.performTouchInput { swipe(center, center.copy(y = center.y + 300f), durationMillis = 600) }
        val afterDown = sidesValue()
        assert(afterDown < afterUp) { "swipe down should decrease, got $afterDown after $afterUp" }

        field.performTouchInput { swipe(center, center.copy(y = center.y + 600f), durationMillis = 600) }
        assert(sidesValue() < afterDown) { "a longer swipe down keeps decreasing" }

        compose.onNodeWithText("Add").performClick()
        assert(added != null && added!! < afterDown)
    }

    @Test
    fun customDieChevronsStepTheValue() {
        compose.setContent {
            DiceFamiliarTheme { DiceContent(DiceUiState(), {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithContentDescription("Add a custom die").performClick()
        compose.onNodeWithTag(CUSTOM_SIDES_FIELD).performTextInput("10")
        compose.onNodeWithContentDescription("Fewer sides").performClick()
        compose.onNodeWithContentDescription("Fewer sides").performClick()
        assert(sidesValue() == 8)
        compose.onNodeWithContentDescription("More sides").performClick()
        assert(sidesValue() == 9)
    }

    @Test
    fun modeSelectorOnlyForSingleDie() {
        show(DiceUiState(notation = "1d6", modeApplicable = true))
        compose.onNodeWithText("Advantage").assertIsDisplayed()
    }

    @Test
    fun modeSelectorHiddenForSeveralDice() {
        show(DiceUiState(notation = "2d6", modeApplicable = false))
        compose.onNodeWithText("Advantage").assertDoesNotExist()
    }

    @Test
    fun d10ShowsZeroForTen() {
        val latest = record("1d10", 10)
        show(DiceUiState(notation = "1d10", latest = latest, settings = AppSettings(quickRoll = true)))
        compose.onNodeWithText("1d10 = 10").assertIsDisplayed()
        compose.onNodeWithText("0", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("d10 rolled 10").assertExists()
    }

    @Test
    fun rollingShowsEllipsisUntilDiceSettle() {
        val pending = record("3d6", 1, 2, 3)
        show(DiceUiState(notation = "3d6", isRolling = true, pending = pending, tumbleMillis = 600))
        compose.onNodeWithText("3d6 = …").assertIsDisplayed()
        compose.onNodeWithText("Swipe here to roll").assertDoesNotExist()
    }
}
