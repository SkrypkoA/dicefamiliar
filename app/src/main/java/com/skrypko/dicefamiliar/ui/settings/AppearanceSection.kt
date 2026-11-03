package com.skrypko.dicefamiliar.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.domain.settings.DarkMode
import com.skrypko.dicefamiliar.ui.theme.AppThemeSpec
import com.skrypko.dicefamiliar.ui.theme.AppThemes
import com.skrypko.dicefamiliar.ui.theme.LocalThemeStyle

/**
 * Settings → Appearance: one preview card per theme in [AppThemes] (nothing here is per-theme), then
 * the dark-mode and dynamic-colour options when the active theme supports them.
 */
@Composable
internal fun AppearanceSection(
    settings: AppSettings,
    onTheme: (String) -> Unit,
    onDarkMode: (DarkMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
    val active = AppThemes.byId(settings.themeId)
    val activeDark = LocalThemeStyle.current.isDark
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppThemes.all.forEach { spec ->
            ThemeCard(
                spec = spec,
                selected = spec.id == active.id,
                // Two-palette themes preview in the brightness the user currently sees.
                dark = if (spec.followsDarkMode) activeDark else spec.light == null,
                onClick = { onTheme(spec.id) },
            )
        }
    }
    Text(
        stringResource(active.descriptionRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (active.followsDarkMode) {
        Text(
            stringResource(R.string.settings_dark_mode),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        val modes = DarkMode.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == settings.darkMode,
                    onClick = { onDarkMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    DarkMode.SYSTEM -> R.string.dark_mode_system
                                    DarkMode.LIGHT -> R.string.dark_mode_light
                                    DarkMode.DARK -> R.string.dark_mode_dark
                                },
                            ),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
    if (active.supportsDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
            supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
            trailingContent = { Switch(checked = settings.dynamicColor, onCheckedChange = null) },
            modifier = Modifier.toggleable(value = settings.dynamicColor, role = Role.Switch, onValueChange = onDynamicColor),
        )
    }
}

/** A miniature of the theme: its background, tray painter, a die in its colours and its icons. */
@Composable
private fun ThemeCard(spec: AppThemeSpec, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val scheme = spec.scheme(dark)
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(CARD_WIDTH)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Box {
            Surface(
                color = scheme.background,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .size(CARD_WIDTH, 132.dp)
                    .border(if (selected) 3.dp else 1.dp, outline, RoundedCornerShape(16.dp)),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .then(with(spec.tray) { Modifier.paintTray(spec.shapes.medium, scheme) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        // A die in the theme's default dice colours.
                        val die = spec.diceColors(scheme)
                        Surface(
                            color = die.fill,
                            shape = spec.shapes.small,
                            border = BorderStroke(2.dp, die.edge),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "20",
                                    color = die.text,
                                    style = spec.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(spec.icons.number, spec.icons.dice, spec.icons.settings).forEach { icon ->
                            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Text(
            stringResource(spec.nameRes),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private val CARD_WIDTH = 104.dp
