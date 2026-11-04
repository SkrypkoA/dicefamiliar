package com.skrypko.dicefamiliar.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.vector.ImageVector
import com.skrypko.dicefamiliar.domain.settings.DarkMode
import com.skrypko.dicefamiliar.ui.theme.themes.ClassicTheme
import com.skrypko.dicefamiliar.ui.theme.themes.CyberpunkTheme
import com.skrypko.dicefamiliar.ui.theme.themes.TavernTheme
import com.skrypko.dicefamiliar.ui.theme.tray.TrayPainter

/** Icons that change with the theme: bottom-bar tabs and the main action buttons. */
data class ThemeIcons(
    val number: ImageVector,
    val pool: ImageVector,
    val dice: ImageVector,
    val cards: ImageVector,
    val settings: ImageVector,
    /** The LAN play tab. */
    val lan: ImageVector,
    /** Dice: the Quick-roll Roll button. */
    val roll: ImageVector,
    /** Number: the Generate button. */
    val generate: ImageVector,
)

/** Default dice colours (used when the active dice set doesn't set its own). */
data class ThemeDiceColors(val fill: Color, val edge: Color, val text: Color)

/**
 * Everything that defines one visual theme. To add a theme: create a spec in `ui/theme/themes/`,
 * add it to [AppThemes.all], and add its name and description strings. Settings, previews and the
 * whole app pick it up from here; nothing else is hard-coded per theme.
 *
 * Give [light], [dark] or both: with both, the user's [DarkMode] chooses; with one, the theme is
 * always that brightness.
 */
data class AppThemeSpec(
    /** Stable id saved in settings; never rename it once released. */
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val light: ColorScheme?,
    val dark: ColorScheme?,
    /** Whether Android 12+ wallpaper colours may replace [light]/[dark]. */
    val supportsDynamicColor: Boolean = false,
    val typography: Typography,
    val shapes: Shapes = Shapes(),
    val icons: ThemeIcons,
    /** Paints the dice tray and other play areas. */
    val tray: TrayPainter,
    /** Default dice look; null means primaryContainer body, primary edges, onPrimaryContainer numbers. */
    val dice: ThemeDiceColors? = null,
) {
    init {
        require(light != null || dark != null) { "Theme $id needs a light or a dark colour scheme" }
    }

    /** Both palettes exist, so the dark-mode setting applies. */
    val followsDarkMode: Boolean get() = light != null && dark != null

    fun isDark(darkMode: DarkMode, systemDark: Boolean): Boolean = when {
        light == null -> true
        dark == null -> false
        else -> when (darkMode) {
            DarkMode.SYSTEM -> systemDark
            DarkMode.LIGHT -> false
            DarkMode.DARK -> true
        }
    }

    fun scheme(dark: Boolean): ColorScheme = if (dark) this.dark ?: light!! else light ?: this.dark!!

    /** The theme's default dice colours for [scheme]. */
    fun diceColors(scheme: ColorScheme): ThemeDiceColors =
        dice ?: ThemeDiceColors(scheme.primaryContainer, scheme.primary, scheme.onPrimaryContainer)
}

/** All selectable themes, in the order shown in Settings. The first one is the default. */
object AppThemes {
    val all: List<AppThemeSpec> = listOf(ClassicTheme, TavernTheme, CyberpunkTheme)

    val default: AppThemeSpec get() = all.first()

    /** The theme with [id], or [default] when it is unknown (e.g. removed in a later version). */
    fun byId(id: String): AppThemeSpec = all.firstOrNull { it.id == id } ?: default
}

/** The active theme and its resolved brightness. */
data class ThemeStyle(val spec: AppThemeSpec, val isDark: Boolean) {
    val icons: ThemeIcons get() = spec.icons
    val tray: TrayPainter get() = spec.tray
}

val LocalThemeStyle = staticCompositionLocalOf { ThemeStyle(ClassicTheme, isDark = false) }

/** Default dice colours of the active theme. */
@Composable
@ReadOnlyComposable
fun themeDiceColors(): ThemeDiceColors = LocalThemeStyle.current.spec.diceColors(MaterialTheme.colorScheme)
