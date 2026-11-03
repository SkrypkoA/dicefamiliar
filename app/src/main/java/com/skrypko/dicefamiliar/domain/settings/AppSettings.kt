package com.skrypko.dicefamiliar.domain.settings

/** Light/dark choice, used by themes that have both a light and a dark palette. */
enum class DarkMode { SYSTEM, LIGHT, DARK }

/** App-wide user preferences, shared by all features. */
data class AppSettings(
    val soundsEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    /** Dice: show a Roll button instead of swipe-to-roll. */
    val quickRoll: Boolean = false,
    /**
     * Id of the visual theme. Themes are defined in the UI layer (`ui/theme/AppThemes`); an unknown id
     * (e.g. a theme removed in a later version) falls back to the default theme.
     */
    val themeId: String = DEFAULT_THEME_ID,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    /** Wallpaper-based colours on Android 12+, for themes that support them. */
    val dynamicColor: Boolean = true,
) {
    companion object {
        const val DEFAULT_THEME_ID = "classic"
    }
}
