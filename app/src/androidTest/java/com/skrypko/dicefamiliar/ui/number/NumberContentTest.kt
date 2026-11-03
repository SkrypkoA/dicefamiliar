package com.skrypko.dicefamiliar.ui.number

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.domain.number.NumberError
import com.skrypko.dicefamiliar.domain.number.NumberField
import com.skrypko.dicefamiliar.domain.number.NumberMode
import com.skrypko.dicefamiliar.domain.number.NumberResult
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NumberContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val noAnimations = AppSettings(animationsEnabled = false)

    private fun show(
        state: NumberUiState,
        onGenerate: () -> Unit = {},
        onDecimalsChange: (Int) -> Unit = {},
        onClearHistory: () -> Unit = {},
        onCopy: (NumberResult) -> Unit = {},
        onShare: (NumberResult) -> Unit = {},
    ) = compose.setContent {
        DiceFamiliarTheme {
            NumberContent(
                state = state,
                onMinChange = {},
                onMaxChange = {},
                onCountChange = {},
                onAllowDuplicatesChange = {},
                onModeChange = {},
                onDecimalsChange = onDecimalsChange,
                onGenerate = onGenerate,
                onClearHistory = onClearHistory,
                onCopy = onCopy,
                onShare = onShare,
            )
        }
    }

    @Test
    fun defaultsShowFieldsHintAndGenerate() {
        var generated = 0
        show(NumberUiState(settings = noAnimations), onGenerate = { generated++ })
        compose.onNodeWithText("Min").assertIsDisplayed()
        compose.onNodeWithText("Max").assertIsDisplayed()
        compose.onNodeWithText("100").assertIsDisplayed()
        compose.onNodeWithText("Tap Generate").assertIsDisplayed()
        compose.onNodeWithContentDescription("Share result").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Fewer decimal places").assertDoesNotExist()
        compose.onNodeWithText("Generate").performClick()
        assertEquals(1, generated)
    }

    @Test
    fun fieldSteppersReportTheirField() {
        val steps = mutableListOf<String>()
        compose.setContent {
            DiceFamiliarTheme {
                NumberContent(
                    state = NumberUiState(settings = noAnimations),
                    onMinChange = {},
                    onMaxChange = {},
                    onCountChange = {},
                    onAllowDuplicatesChange = {},
                    onModeChange = {},
                    onDecimalsChange = {},
                    onGenerate = {},
                    onMinStep = { steps += "min$it" },
                    onMaxStep = { steps += "max$it" },
                    onCountStep = { steps += "count$it" },
                )
            }
        }
        compose.onNodeWithContentDescription("Increase Max").performClick()
        compose.onNodeWithContentDescription("Decrease Min").performClick()
        compose.onNodeWithContentDescription("Increase How many").performClick()
        assertEquals(listOf("max1", "min-1", "count1"), steps)
    }

    @Test
    fun errorsAreShownUnderTheirFields() {
        show(
            NumberUiState(
                count = "500",
                allowDuplicates = false,
                errors = mapOf(
                    NumberField.MAX to NumberError.MIN_ABOVE_MAX,
                    NumberField.COUNT to NumberError.COUNT_EXCEEDS_RANGE,
                ),
                settings = noAnimations,
            ),
        )
        compose.onNodeWithText("Must be at least Min").assertIsDisplayed()
        compose.onNodeWithText("More than the range holds; allow duplicates or widen it").assertIsDisplayed()
    }

    @Test
    fun decimalModeShowsStepper() {
        var decimals = -1
        show(NumberUiState(mode = NumberMode.DECIMAL, decimals = 2, settings = noAnimations), onDecimalsChange = { decimals = it })
        compose.onNodeWithText("Decimal places").assertIsDisplayed()
        compose.onNodeWithContentDescription("More decimal places").performClick()
        assertEquals(3, decimals)
    }

    @Test
    fun singleResultWithCopyAndShare() {
        val result = NumberResult("1", listOf(42), 0, 1, 100, true, 0)
        val copied = mutableListOf<NumberResult>()
        val shared = mutableListOf<NumberResult>()
        show(
            NumberUiState(latest = result, recent = listOf(result), settings = noAnimations),
            onCopy = { copied += it },
            onShare = { shared += it },
        )
        compose.onNodeWithText("42").assertIsDisplayed()
        compose.onNodeWithText("1 – 100").assertIsDisplayed()
        compose.onNodeWithContentDescription("Recent results").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Share result").assertIsEnabled().performClick()
        compose.onNodeWithText("Copy").performClick()
        compose.onNodeWithContentDescription("Share result").performClick()
        compose.onNodeWithText("Share as text").performClick()
        assertEquals(listOf(result), copied)
        assertEquals(listOf(result), shared)
    }

    @Test
    fun collapsedPanelHidesTheFormButKeepsShareAndGenerate() {
        show(NumberUiState(settings = noAnimations, panelVisible = false))
        compose.onNodeWithText("Min").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show options").assertIsDisplayed()
        compose.onNodeWithContentDescription("Share result").assertIsDisplayed()
        compose.onNodeWithText("Generate").assertIsDisplayed()
    }

    @Test
    fun severalDecimalResultsShowChipsAndSum() {
        val result = NumberResult("1", listOf(150, 25), 2, 0, 200, false, 0)
        show(NumberUiState(latest = result, recent = listOf(result), settings = noAnimations))
        compose.onNodeWithText("1.50").assertIsDisplayed()
        compose.onNodeWithText("0.25").assertIsDisplayed()
        compose.onNodeWithText("Sum: 1.75").assertIsDisplayed()
        compose.onNodeWithText("0.00 – 2.00 · ×2 · no duplicates").assertIsDisplayed()
    }

    @Test
    fun historySheetListsOlderResultsAndClears() {
        val newest = NumberResult("2", listOf(7), 0, 1, 10, true, 0)
        val older = NumberResult("1", listOf(3, 9), 0, 1, 10, true, 0)
        var cleared = false
        show(
            NumberUiState(latest = newest, recent = listOf(newest, older), settings = noAnimations),
            onClearHistory = { cleared = true },
        )
        compose.onNodeWithContentDescription("Recent results").performClick()
        compose.onNodeWithText("3, 9").assertIsDisplayed()
        compose.onNodeWithText("Clear history").performClick()
        assertTrue(cleared)
    }
}
