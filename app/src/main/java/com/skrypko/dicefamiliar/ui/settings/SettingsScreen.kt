package com.skrypko.dicefamiliar.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        onSounds = viewModel::setSounds,
        onAnimations = viewModel::setAnimations,
        onHaptics = viewModel::setHaptics,
        onQuickRoll = viewModel::setQuickRoll,
    )
}

@Composable
fun SettingsContent(
    settings: AppSettings,
    onSounds: (Boolean) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onQuickRoll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(R.string.settings_section_general)
        SwitchRow(R.string.settings_sounds, R.string.settings_sounds_summary, settings.soundsEnabled, onSounds)
        SwitchRow(
            R.string.settings_animations,
            R.string.settings_animations_summary,
            settings.animationsEnabled,
            onAnimations,
        )
        SwitchRow(R.string.settings_haptics, R.string.settings_haptics_summary, settings.hapticsEnabled, onHaptics)
        HorizontalDivider()
        SectionHeader(R.string.settings_section_dice)
        SwitchRow(R.string.settings_quick_roll, R.string.settings_quick_roll_summary, settings.quickRoll, onQuickRoll)
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    DiceFamiliarTheme {
        SettingsContent(AppSettings(), {}, {}, {}, {})
    }
}
