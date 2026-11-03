package com.skrypko.dicefamiliar.ui.settings

import android.content.ContentResolver
import android.content.res.Resources
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.data.dice.DiceSetImportOutcome
import com.skrypko.dicefamiliar.data.dice.DiceSetInfo
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetImportError
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.settings.dicesets.DiceSetHelpButton
import com.skrypko.dicefamiliar.ui.settings.dicesets.DiceSetHelpSheet
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme

private val ZIP_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

@Composable
fun SettingsScreen(
    onEditDiceSet: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val diceSets by viewModel.diceSetsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolver = context.contentResolver
            viewModel.importDiceSet({ resolver.openInputStream(uri) }, displayName(resolver, uri))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.OpenDiceSet -> onEditDiceSet(event.id)
                is SettingsEvent.Imported -> snackbar.showSnackbar(importedMessage(resources, event.outcome))
                is SettingsEvent.ImportFailed -> snackbar.showSnackbar(resources.getString(event.error.messageRes()))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        SettingsContent(
            settings = settings,
            diceSets = diceSets,
            onSounds = viewModel::setSounds,
            onAnimations = viewModel::setAnimations,
            onHaptics = viewModel::setHaptics,
            onQuickRoll = viewModel::setQuickRoll,
            onSelectDiceSet = viewModel::selectDiceSet,
            onEditDiceSet = onEditDiceSet,
            onDeleteDiceSet = viewModel::deleteDiceSet,
            onCreateDiceSet = viewModel::createDiceSet,
            onImportDiceSet = { importLauncher.launch(ZIP_MIME_TYPES) },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun SettingsContent(
    settings: AppSettings,
    onSounds: (Boolean) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onQuickRoll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    diceSets: DiceSetsUiState = DiceSetsUiState(),
    onSelectDiceSet: (String) -> Unit = {},
    onEditDiceSet: (String) -> Unit = {},
    onDeleteDiceSet: (String) -> Unit = {},
    onCreateDiceSet: (String) -> Unit = {},
    onImportDiceSet: () -> Unit = {},
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
        DiceSetsSection(
            state = diceSets,
            onSelect = onSelectDiceSet,
            onEdit = onEditDiceSet,
            onDelete = onDeleteDiceSet,
            onCreate = onCreateDiceSet,
            onImport = onImportDiceSet,
        )
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun DiceSetsSection(
    state: DiceSetsUiState,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: (String) -> Unit,
    onImport: () -> Unit,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DiceSetInfo?>(null) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
    ) {
        Text(
            stringResource(R.string.dice_sets_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        DiceSetHelpButton(onClick = { showHelp = true })
    }
    state.sets.forEach { set ->
        val selected = set.id == state.activeId
        ListItem(
            leadingContent = { RadioButton(selected = selected, onClick = null) },
            headlineContent = { Text(set.name) },
            supportingContent = {
                Text(stringResource(if (set.builtIn) R.string.dice_sets_builtin else R.string.dice_sets_custom))
            },
            trailingContent = if (set.builtIn) {
                null
            } else {
                {
                    Row {
                        IconButton(onClick = { onEdit(set.id) }) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.dice_sets_edit, set.name))
                        }
                        IconButton(onClick = { pendingDelete = set }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.dice_sets_delete, set.name))
                        }
                    }
                }
            },
            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(set.id) }),
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedButton(onClick = onImport, enabled = !state.importing, modifier = Modifier.weight(1f)) {
            if (state.importing) {
                CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            }
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(if (state.importing) R.string.dice_sets_importing else R.string.dice_sets_import))
        }
        OutlinedButton(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.dice_sets_new))
        }
    }

    if (showHelp) DiceSetHelpSheet(onDismiss = { showHelp = false })
    if (showCreate) {
        CreateDiceSetDialog(
            onDismiss = { showCreate = false },
            onCreate = {
                showCreate = false
                onCreate(it)
            },
        )
    }
    pendingDelete?.let { set ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.dice_sets_delete_title, set.name)) },
            text = { Text(stringResource(R.string.dice_sets_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(set.id)
                }) { Text(stringResource(R.string.dice_sets_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CreateDiceSetDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val defaultName = stringResource(R.string.dice_sets_default_name)
    var name by rememberSaveable { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dice_sets_new_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.dice_sets_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.dice_sets_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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

private const val MAX_NAME_LENGTH = 40

@StringRes
fun DiceSetImportError.messageRes(): Int = when (this) {
    DiceSetImportError.NOT_A_ZIP -> R.string.dice_sets_error_not_zip
    DiceSetImportError.UNSAFE_PATH -> R.string.dice_sets_error_unsafe
    DiceSetImportError.TOO_MANY_ENTRIES -> R.string.dice_sets_error_too_many
    DiceSetImportError.TOO_LARGE -> R.string.dice_sets_error_too_large
    DiceSetImportError.NOTHING_TO_IMPORT -> R.string.dice_sets_error_nothing
    DiceSetImportError.INVALID_MANIFEST -> R.string.dice_sets_error_manifest
    DiceSetImportError.READ_FAILED -> R.string.dice_sets_error_read
}

private fun importedMessage(res: Resources, outcome: DiceSetImportOutcome.Imported): String {
    return listOfNotNull(
        res.getString(
            if (outcome.replaced) R.string.dice_sets_updated else R.string.dice_sets_imported,
            outcome.name,
        ),
        outcome.skippedFiles.takeIf { it > 0 }?.let {
            res.getQuantityString(R.plurals.dice_sets_skipped, it, it)
        },
        outcome.missingFiles.takeIf { it > 0 }?.let {
            res.getQuantityString(R.plurals.dice_sets_missing, it, it)
        },
    ).joinToString(" · ")
}

private fun displayName(resolver: ContentResolver, uri: Uri): String? = try {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (e: SecurityException) {
    null
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    DiceFamiliarTheme {
        SettingsContent(
            AppSettings(),
            {},
            {},
            {},
            {},
            diceSets = DiceSetsUiState(
                sets = listOf(DiceSetInfo("default", "Default", true), DiceSetInfo("wood", "Wood", false)),
            ),
        )
    }
}
