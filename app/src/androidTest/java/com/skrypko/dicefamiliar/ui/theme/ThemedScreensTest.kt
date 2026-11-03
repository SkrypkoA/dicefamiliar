package com.skrypko.dicefamiliar.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.domain.number.NumberResult
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.dice.DiceContent
import com.skrypko.dicefamiliar.ui.dice.DiceUiState
import com.skrypko.dicefamiliar.ui.number.NumberContent
import com.skrypko.dicefamiliar.ui.number.NumberUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every registered theme renders the feature screens, with readable tray content and the "?" help. */
@RunWith(AndroidJUnit4::class)
class ThemedScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val noAnimations = AppSettings(animationsEnabled = false)

    @Test
    fun diceScreenInEveryTheme() {
        var themeId by mutableStateOf(AppThemes.default.id)
        compose.setContent {
            DiceFamiliarTheme(themeId = themeId) {
                DiceContent(DiceUiState(settings = noAnimations), {}, {}, {}, {}, {}, {}, {})
            }
        }
        AppThemes.all.forEach { spec ->
            themeId = spec.id
            compose.waitForIdle()
            compose.onNodeWithText("Swipe here to roll").assertIsDisplayed()
            compose.onNodeWithContentDescription("How to use the dice roller").assertIsDisplayed()
        }
    }

    @Test
    fun numberScreenInEveryTheme() {
        var themeId by mutableStateOf(AppThemes.default.id)
        val result = NumberResult("1", listOf(42), 0, 1, 100, true, 0)
        compose.setContent {
            DiceFamiliarTheme(themeId = themeId) {
                NumberContent(
                    state = NumberUiState(latest = result, recent = listOf(result), settings = noAnimations),
                    onMinChange = {},
                    onMaxChange = {},
                    onCountChange = {},
                    onAllowDuplicatesChange = {},
                    onModeChange = {},
                    onDecimalsChange = {},
                    onGenerate = {},
                )
            }
        }
        AppThemes.all.forEach { spec ->
            themeId = spec.id
            compose.waitForIdle()
            compose.onNodeWithText("42").assertIsDisplayed()
            compose.onNodeWithText("Generate").assertIsDisplayed()
        }
    }

    @Test
    fun diceHelpExplainsRemovingDice() {
        compose.setContent {
            DiceFamiliarTheme {
                DiceContent(DiceUiState(settings = noAnimations), {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("How to use the dice roller").performClick()
        compose.onNodeWithText("How to roll dice").assertIsDisplayed()
        compose.onNodeWithText("Remove dice").assertIsDisplayed()
        compose.onNodeWithText("Long-press a die to remove one of it", substring = true).assertIsDisplayed()
    }

    @Test
    fun numberHelpOpens() {
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
                )
            }
        }
        compose.onNodeWithContentDescription("How to use the number generator").performClick()
        compose.onNodeWithText("How to generate numbers").assertIsDisplayed()
        compose.onNodeWithText("Duplicates").assertIsDisplayed()
    }
}
