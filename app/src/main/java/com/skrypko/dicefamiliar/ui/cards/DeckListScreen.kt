package com.skrypko.dicefamiliar.ui.cards

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.data.cards.DeckInfo
import com.skrypko.dicefamiliar.domain.cards.DeckKind
import com.skrypko.dicefamiliar.ui.common.HelpButton
import com.skrypko.dicefamiliar.ui.common.HelpSheet
import com.skrypko.dicefamiliar.ui.common.HelpTopic

@Composable
fun DeckListScreen(
    onBack: () -> Unit,
    onEditCustom: (String?) -> Unit,
    viewModel: DeckListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DeckListContent(
        state = state,
        onBack = onBack,
        onSelect = { id ->
            viewModel.select(id)
            onBack()
        },
        onEditCustom = onEditCustom,
        onDelete = viewModel::delete,
    )
}

/** Presets, then custom decks (with "New custom deck"), then uploaded decks. Tapping one plays it. */
@Composable
fun DeckListContent(
    state: DeckListUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onEditCustom: (String?) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<DeckInfo?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cards_custom_back))
            }
            Text(stringResource(R.string.cards_decks_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            HelpButton(stringResource(R.string.help_decks_button), onClick = { showHelp = true })
        }
        Column(Modifier.selectableGroup()) {
            Section(R.string.cards_section_presets)
            state.decks.filter { it.kind == DeckKind.PRESET }.forEach { deck ->
                DeckRow(deck, deck.id == state.selectedId, onSelect)
            }
            Section(R.string.cards_section_custom)
            state.decks.filter { it.kind == DeckKind.CUSTOM }.forEach { deck ->
                DeckRow(deck, deck.id == state.selectedId, onSelect, onEdit = { onEditCustom(deck.id) }, onDelete = { pendingDelete = deck })
            }
            OutlinedButton(onClick = { onEditCustom(null) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.cards_new_custom), modifier = Modifier.padding(start = 8.dp))
            }
            val uploaded = state.decks.filter { it.kind == DeckKind.UPLOADED }
            Section(R.string.cards_section_uploaded)
            uploaded.forEach { deck ->
                DeckRow(deck, deck.id == state.selectedId, onSelect, onDelete = { pendingDelete = deck })
            }
            Text(
                stringResource(R.string.cards_upload_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
    pendingDelete?.let { deck ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.cards_delete_title)) },
            text = { Text(stringResource(R.string.cards_delete_text, deck.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deck.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.dice_sets_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showHelp) HelpSheet(R.string.help_decks_title, DECKS_HELP, onDismiss = { showHelp = false })
}

@Composable
private fun Section(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun DeckRow(
    deck: DeckInfo,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val name = deck.presetSize?.let { pluralStringResource(R.plurals.cards_preset_name, it, it) } ?: deck.name
    val summary = when (deck.presetSize) {
        36 -> stringResource(R.string.cards_preset_36_summary)
        52 -> stringResource(R.string.cards_preset_52_summary)
        54 -> stringResource(R.string.cards_preset_54_summary)
        else -> pluralStringResource(R.plurals.cards_count, deck.cardCount, deck.cardCount)
    }
    ListItem(
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(name) },
        supportingContent = { Text(summary) },
        trailingContent = if (onEdit == null && onDelete == null) {
            null
        } else {
            {
                Row {
                    onEdit?.let {
                        IconButton(onClick = it) { Icon(Icons.Outlined.Edit, stringResource(R.string.cards_edit_deck, name)) }
                    }
                    onDelete?.let {
                        IconButton(onClick = it) { Icon(Icons.Outlined.Delete, stringResource(R.string.cards_delete_deck, name)) }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(deck.id) }),
    )
}

internal val DECKS_HELP = listOf(
    HelpTopic(R.string.help_decks_standard_title, R.string.help_decks_standard_text),
    HelpTopic(R.string.help_decks_custom_title, R.string.help_decks_custom_text),
    HelpTopic(R.string.help_decks_upload_title, R.string.help_decks_upload_text),
)
