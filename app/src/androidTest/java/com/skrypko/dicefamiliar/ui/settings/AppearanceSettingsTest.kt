package com.skrypko.dicefamiliar.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.domain.settings.DarkMode
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceSettingsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(settings: AppSettings, onTheme: (String) -> Unit = {}, onDarkMode: (DarkMode) -> Unit = {}) =
        compose.setContent {
            DiceFamiliarTheme(themeId = settings.themeId, darkMode = settings.darkMode) {
                SettingsContent(
                    settings = settings,
                    onSounds = {},
                    onAnimations = {},
                    onHaptics = {},
                    onQuickRoll = {},
                    onTheme = onTheme,
                    onDarkMode = onDarkMode,
                )
            }
        }

    @Test
    fun everyThemeIsOfferedAndSelectable() {
        val picked = mutableListOf<String>()
        show(AppSettings(), onTheme = { picked += it })
        compose.onNodeWithText("Classic").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Tavern").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cyberpunk").assertIsDisplayed().performClick()
        assertEquals(listOf("tavern", "cyberpunk"), picked)
    }

    @Test
    fun classicOffersDarkMode() {
        val modes = mutableListOf<DarkMode>()
        show(AppSettings(themeId = "classic"), onDarkMode = { modes += it })
        compose.onNodeWithText("Dark mode").assertIsDisplayed()
        compose.onNodeWithText("Dark").performClick()
        assertEquals(listOf(DarkMode.DARK), modes)
    }

    @Test
    fun fixedThemesHideDarkModeAndDescribeThemselves() {
        show(AppSettings(themeId = "tavern"))
        compose.onNodeWithText("Dark mode").assertDoesNotExist()
        compose.onNodeWithText("Warm parchment and leather, serif titles, and dice rolled on green felt.").assertIsDisplayed()
    }
}
