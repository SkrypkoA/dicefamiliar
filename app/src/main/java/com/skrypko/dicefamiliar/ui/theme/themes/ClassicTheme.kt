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

/** Material You: light and dark palettes, wallpaper colours on Android 12+, a plain tonal tray. */
val ClassicTheme = AppThemeSpec(
    id = "classic",
    nameRes = R.string.theme_classic,
    descriptionRes = R.string.theme_classic_description,
    light = lightColorScheme(primary = Color(0xFF6650A4), secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260)),
    dark = darkColorScheme(primary = Color(0xFFD0BCFF), secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8)),
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
