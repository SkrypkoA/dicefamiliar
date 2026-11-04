package com.skrypko.dicefamiliar.ui.theme.themes

import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.ui.theme.AppThemeSpec
import com.skrypko.dicefamiliar.ui.theme.ThemeDiceColors
import com.skrypko.dicefamiliar.ui.theme.ThemeIcons
import com.skrypko.dicefamiliar.ui.theme.tray.FeltTray
import com.skrypko.dicefamiliar.ui.theme.tray.TrayColors

private val Leather = Color(0xFF7B4A26)
private val Parchment = Color(0xFFF5EBDD)
private val Ink = Color(0xFF2E2218)
private val OnFelt = Color(0xFFF1E6CC)

/** Warm fantasy: parchment and leather, gold trim, bone dice, serif headings, a green felt tray. */
val TavernTheme = AppThemeSpec(
    id = "tavern",
    nameRes = R.string.theme_tavern,
    descriptionRes = R.string.theme_tavern_description,
    light = lightColorScheme(
        primary = Leather,
        onPrimary = Color(0xFFFFF8EE),
        primaryContainer = Color(0xFFF3E3C6),
        onPrimaryContainer = Color(0xFF3B2410),
        inversePrimary = Color(0xFFF0BC8F),
        secondary = Color(0xFF2F6B40),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3E6C8),
        onSecondaryContainer = Color(0xFF0F3A1E),
        tertiary = Color(0xFF9C6B1E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF6DFA8),
        onTertiaryContainer = Color(0xFF3A2700),
        background = Parchment,
        onBackground = Ink,
        surface = Parchment,
        onSurface = Ink,
        surfaceVariant = Color(0xFFE8D8C3),
        onSurfaceVariant = Color(0xFF5C4A3A),
        surfaceTint = Leather,
        inverseSurface = Color(0xFF3B2F25),
        inverseOnSurface = Color(0xFFF8EEE1),
        outline = Color(0xFF8C7663),
        outlineVariant = Color(0xFFD3C1AA),
        surfaceBright = Color(0xFFFBF3E8),
        surfaceDim = Color(0xFFE2D3BF),
        surfaceContainerLowest = Color(0xFFFFF8EF),
        surfaceContainerLow = Color(0xFFF8EEE1),
        surfaceContainer = Color(0xFFF1E4D2),
        surfaceContainerHigh = Color(0xFFEBDCC8),
        surfaceContainerHighest = Color(0xFFE5D3BD),
    ),
    dark = null,
    typography = Typography().run {
        // Storybook serif for headings and titles; body text stays sans for readability.
        fun TextStyle.serif(weight: FontWeight = FontWeight.SemiBold) = copy(fontFamily = FontFamily.Serif, fontWeight = weight)
        copy(
            displayLarge = displayLarge.serif(FontWeight.Bold),
            displayMedium = displayMedium.serif(FontWeight.Bold),
            displaySmall = displaySmall.serif(FontWeight.Bold),
            headlineLarge = headlineLarge.serif(),
            headlineMedium = headlineMedium.serif(),
            headlineSmall = headlineSmall.serif(),
            titleLarge = titleLarge.serif(),
            titleMedium = titleMedium.serif(),
            titleSmall = titleSmall.serif(),
        )
    },
    // A magic wand for numbers, a treasure chest for pools, a spellbook for settings, a gathering for LAN.
    icons = ThemeIcons(
        number = Icons.Outlined.AutoFixHigh,
        pool = Icons.Outlined.Inventory2,
        dice = Icons.Rounded.Casino,
        cards = Icons.Outlined.Style,
        settings = Icons.Outlined.AutoStories,
        lan = Icons.Outlined.Groups,
        roll = Icons.Rounded.Casino,
        generate = Icons.Outlined.AutoFixHigh,
    ),
    // Bone dice: an ivory body, aged-bone edges, dark carved numbers.
    dice = ThemeDiceColors(fill = Color(0xFFEDE4D0), edge = Color(0xFF9C8566), text = Color(0xFF3E2C1C)),
    tray = FeltTray(
        feltLight = Color(0xFF3D8350),
        felt = Color(0xFF2F6B40),
        feltDark = Color(0xFF1C4429),
        woodLight = Color(0xFF8A5A33),
        woodDark = Color(0xFF4E3019),
        text = TrayColors(content = OnFelt, muted = OnFelt.copy(alpha = 0.78f), accent = Color(0xFFFFE3A3)),
    ),
)
