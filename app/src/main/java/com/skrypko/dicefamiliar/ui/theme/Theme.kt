package com.skrypko.dicefamiliar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.domain.settings.DarkMode

/** Whether the theme [themeId] renders dark with this [darkMode]. */
@Composable
fun isDarkTheme(themeId: String, darkMode: DarkMode): Boolean =
    AppThemes.byId(themeId).isDark(darkMode, isSystemInDarkTheme())

/** Applies the theme [themeId] from [AppThemes]; unknown ids fall back to the default theme. */
@Composable
fun DiceFamiliarTheme(
    themeId: String = AppSettings.DEFAULT_THEME_ID,
    darkMode: DarkMode = DarkMode.SYSTEM,
    // Dynamic color is available on Android 12+, for themes that support it.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val spec = AppThemes.byId(themeId)
    val dark = spec.isDark(darkMode, isSystemInDarkTheme())
    val colorScheme = if (spec.supportsDynamicColor && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        spec.scheme(dark)
    }
    CompositionLocalProvider(LocalThemeStyle provides ThemeStyle(spec, dark)) {
        MaterialTheme(colorScheme = colorScheme, typography = spec.typography, shapes = spec.shapes, content = content)
    }
}
