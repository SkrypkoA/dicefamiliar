package com.skrypko.dicefamiliar.ui.theme.themes

import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.ui.theme.AppThemeSpec
import com.skrypko.dicefamiliar.ui.theme.ThemeIcons
import com.skrypko.dicefamiliar.ui.theme.tray.PlainTray

/**
 * The app's own colours, taken from the logo (the fairy gecko): violet primary, gecko-green secondary, gold
 * tertiary on light lavender surfaces; a matching night palette. Wallpaper colours (Android 12+) are optional.
 */
val ClassicTheme = AppThemeSpec(
    id = "classic",
    nameRes = R.string.theme_classic,
    descriptionRes = R.string.theme_classic_description,
    light = lightColorScheme(
        primary = Color(0xFF5A4AA0),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE5DEFF),
        onPrimaryContainer = Color(0xFF1A1050),
        secondary = Color(0xFF2F7A52),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC6F1D3),
        onSecondaryContainer = Color(0xFF00210F),
        tertiary = Color(0xFF7C5800),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE08C),
        onTertiaryContainer = Color(0xFF271900),
        background = Color(0xFFFCF9FF),
        onBackground = Color(0xFF1C1A24),
        surface = Color(0xFFFCF9FF),
        onSurface = Color(0xFF1C1A24),
        surfaceVariant = Color(0xFFE6E0F0),
        onSurfaceVariant = Color(0xFF48454F),
        outline = Color(0xFF79757F),
        outlineVariant = Color(0xFFCAC4D4),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6F2FC),
        surfaceContainer = Color(0xFFF0ECF7),
        surfaceContainerHigh = Color(0xFFEAE6F2),
        surfaceContainerHighest = Color(0xFFE5E0EC),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFC8BFFF),
        onPrimary = Color(0xFF2B2350),
        primaryContainer = Color(0xFF41378A),
        onPrimaryContainer = Color(0xFFE5DEFF),
        secondary = Color(0xFF8FD8A8),
        onSecondary = Color(0xFF003920),
        secondaryContainer = Color(0xFF1E5A43),
        onSecondaryContainer = Color(0xFFC6F1D3),
        tertiary = Color(0xFFFFD970),
        onTertiary = Color(0xFF412D00),
        tertiaryContainer = Color(0xFF5D4200),
        onTertiaryContainer = Color(0xFFFFE08C),
        background = Color(0xFF15122A),
        onBackground = Color(0xFFE6E1F2),
        surface = Color(0xFF15122A),
        onSurface = Color(0xFFE6E1F2),
        surfaceVariant = Color(0xFF48454F),
        onSurfaceVariant = Color(0xFFCAC4D4),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF48454F),
        surfaceContainerLowest = Color(0xFF100D22),
        surfaceContainerLow = Color(0xFF1C1833),
        surfaceContainer = Color(0xFF211D3A),
        surfaceContainerHigh = Color(0xFF2B2650),
        surfaceContainerHighest = Color(0xFF353060),
    ),
    supportsDynamicColor = true,
    typography = Typography(
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
    ),
    icons = ThemeIcons(
        number = Icons.Filled.Numbers,
        pool = Icons.AutoMirrored.Filled.List,
        dice = Icons.Filled.Casino,
        cards = Icons.Filled.Style,
        settings = Icons.Filled.Settings,
        lan = Icons.Filled.Wifi,
        roll = Icons.Filled.Casino,
        generate = Icons.Filled.Casino,
    ),
    tray = PlainTray,
)
