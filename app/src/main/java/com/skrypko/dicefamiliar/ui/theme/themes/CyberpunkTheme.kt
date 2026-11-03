package com.skrypko.dicefamiliar.ui.theme.themes

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Bolt
import androidx.compose.material.icons.sharp.Casino
import androidx.compose.material.icons.sharp.Memory
import androidx.compose.material.icons.sharp.Storage
import androidx.compose.material.icons.sharp.Style
import androidx.compose.material.icons.sharp.Terminal
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.ui.theme.AppThemeSpec
import com.skrypko.dicefamiliar.ui.theme.ThemeIcons
import com.skrypko.dicefamiliar.ui.theme.tray.NeonGridTray
import com.skrypko.dicefamiliar.ui.theme.tray.TrayColors

private val Cyan = Color(0xFF00F0FF)
private val Magenta = Color(0xFFFF2BD6)
private val Yellow = Color(0xFFF5FF3D)
private val Night = Color(0xFF07070D)
private val Glow = Color(0xFFE6F7FF)

/** Bright neon on dark: cyan, magenta and yellow, monospace type, cut corners, a neon grid tray. */
val CyberpunkTheme = AppThemeSpec(
    id = "cyberpunk",
    nameRes = R.string.theme_cyberpunk,
    descriptionRes = R.string.theme_cyberpunk_description,
    light = null,
    dark = darkColorScheme(
        primary = Cyan,
        onPrimary = Color(0xFF00201F),
        primaryContainer = Color(0xFF003A40),
        onPrimaryContainer = Color(0xFF8AFBFF),
        inversePrimary = Color(0xFF006970),
        secondary = Magenta,
        onSecondary = Color(0xFF2E0026),
        secondaryContainer = Color(0xFF4A0040),
        onSecondaryContainer = Color(0xFFFFB0EE),
        tertiary = Yellow,
        onTertiary = Color(0xFF1F2200),
        tertiaryContainer = Color(0xFF3A3D00),
        onTertiaryContainer = Color(0xFFF5FF9E),
        background = Night,
        onBackground = Glow,
        surface = Color(0xFF0A0A12),
        onSurface = Glow,
        surfaceVariant = Color(0xFF1A1A2A),
        onSurfaceVariant = Color(0xFF9AB3C8),
        surfaceTint = Cyan,
        inverseSurface = Glow,
        inverseOnSurface = Color(0xFF12121E),
        error = Color(0xFFFF4D6D),
        onError = Color(0xFF3B0010),
        errorContainer = Color(0xFF5C0020),
        onErrorContainer = Color(0xFFFFB3C1),
        outline = Color(0xFF3D6A80),
        outlineVariant = Color(0xFF22303D),
        surfaceBright = Color(0xFF26263A),
        surfaceDim = Night,
        surfaceContainerLowest = Color(0xFF05050A),
        surfaceContainerLow = Color(0xFF0E0E18),
        surfaceContainer = Color(0xFF12121E),
        surfaceContainerHigh = Color(0xFF181826),
        surfaceContainerHighest = Color(0xFF1F1F30),
    ),
    typography = Typography().run {
        // Terminal monospace for numbers, titles and labels, slightly tracked out.
        fun TextStyle.mono(spacing: Float = 0.5f) = copy(fontFamily = FontFamily.Monospace, letterSpacing = spacing.sp)
        copy(
            displayLarge = displayLarge.mono(0f),
            displayMedium = displayMedium.mono(0f),
            displaySmall = displaySmall.mono(0f),
            headlineLarge = headlineLarge.mono(),
            headlineMedium = headlineMedium.mono(),
            headlineSmall = headlineSmall.mono(),
            titleLarge = titleLarge.mono(),
            titleMedium = titleMedium.mono(),
            titleSmall = titleSmall.mono(),
            labelLarge = labelLarge.mono(1f),
            labelMedium = labelMedium.mono(1f),
            labelSmall = labelSmall.mono(1f),
        )
    },
    // Corners are cut, not rounded.
    shapes = Shapes(
        extraSmall = CutCornerShape(2.dp),
        small = CutCornerShape(4.dp),
        medium = CutCornerShape(8.dp),
        large = CutCornerShape(12.dp),
        extraLarge = CutCornerShape(16.dp),
    ),
    // A chip for numbers, storage for pools, a terminal for settings, a bolt to act.
    icons = ThemeIcons(
        number = Icons.Sharp.Memory,
        pool = Icons.Sharp.Storage,
        dice = Icons.Sharp.Casino,
        cards = Icons.Sharp.Style,
        settings = Icons.Sharp.Terminal,
        roll = Icons.Sharp.Bolt,
        generate = Icons.Sharp.Bolt,
    ),
    tray = NeonGridTray(
        top = Color(0xFF0B0B18),
        bottom = Color(0xFF1A0A24),
        lineA = Cyan,
        lineB = Magenta,
        text = TrayColors(content = Glow, muted = Color(0xFF9AB3C8), accent = Cyan),
    ),
)
