package com.skrypko.dicefamiliar.ui.settings

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.ui.common.displayName
import com.skrypko.dicefamiliar.data.cards.CardDeckImportOutcome
import com.skrypko.dicefamiliar.data.cards.CardDeckRepository
import com.skrypko.dicefamiliar.data.cards.DeckInfo
import com.skrypko.dicefamiliar.data.dice.StreamSource
import com.skrypko.dicefamiliar.domain.cards.DeckKind
import com.skrypko.dicefamiliar.domain.cards.io.CardDeckImportError
import com.skrypko.dicefamiliar.ui.common.HelpButton
import com.skrypko.dicefamiliar.ui.settings.dicesets.HelpBody
import com.skrypko.dicefamiliar.ui.settings.dicesets.HelpCode
import com.skrypko.dicefamiliar.ui.settings.dicesets.HelpSection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardDecksUiState(val uploaded: List<DeckInfo> = emptyList(), val importing: Boolean = false)

@HiltViewModel
class CardDecksSettingsViewModel @Inject constructor(private val repository: CardDeckRepository) : ViewModel() {
    private val importing = MutableStateFlow(false)

    val uiState: StateFlow<CardDecksUiState> = combine(
        repository.decks.map { decks -> decks.filter { it.kind == DeckKind.UPLOADED } },
        importing,
    ) { uploaded, busy -> CardDecksUiState(uploaded, busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardDecksUiState())

    private val _results = Channel<CardDeckImportOutcome>(Channel.BUFFERED)
    val results: Flow<CardDeckImportOutcome> = _results.receiveAsFlow()

    fun import(source: StreamSource, fileName: String?) {
        if (importing.value) return
        importing.value = true
        viewModelScope.launch {
            try {
                _results.send(repository.importDeck(source, fileName))
            } finally {
                importing.value = false
            }
        }
    }

    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }
}

private val DECK_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/octet-stream",
)

/** Settings → Card decks: upload a deck (ZIP or CSV), the requirements sheet, and uploaded decks. */
@Composable
fun CardDecksSection(onMessage: (String) -> Unit, viewModel: CardDecksSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DeckInfo?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolver = context.contentResolver
            viewModel.import({ resolver.openInputStream(uri) }, displayName(resolver, uri))
        }
    }
    LaunchedEffect(viewModel) { viewModel.results.collect { onMessage(message(resources, it)) } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
    ) {
        Text(stringResource(R.string.cards_settings_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        HelpButton(stringResource(R.string.cards_upload_help), onClick = { showHelp = true })
    }
    if (state.uploaded.isEmpty()) {
        Text(
            stringResource(R.string.cards_settings_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    state.uploaded.forEach { deck ->
        ListItem(
            headlineContent = { Text(deck.name) },
            supportingContent = { Text(pluralStringResource(R.plurals.cards_count, deck.cardCount, deck.cardCount)) },
            trailingContent = {
                IconButton(onClick = { pendingDelete = deck }) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.cards_delete_deck, deck.name))
                }
            },
        )
    }
    OutlinedButton(
        onClick = { launcher.launch(DECK_MIME_TYPES) },
        enabled = !state.importing,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Outlined.FileUpload, contentDescription = null)
        Text(stringResource(R.string.cards_upload), modifier = Modifier.padding(start = 8.dp))
    }

    pendingDelete?.let { deck ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.cards_delete_title)) },
            text = { Text(stringResource(R.string.cards_delete_text, deck.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(deck.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.dice_sets_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showHelp) CardDeckHelpSheet(onDismiss = { showHelp = false })
}

/** Requirements for uploaded decks (short form of docs/card-decks.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDeckHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(stringResource(R.string.cards_upload_help), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            HelpBody(R.string.cards_help_intro)
            HelpSection(R.string.cards_help_zip_csv_title, R.string.cards_help_zip_csv_text)
            HelpCode(R.string.cards_help_zip_tree)
            HelpCode(R.string.cards_help_csv_example)
            HelpSection(R.string.cards_help_zip_images_title, R.string.cards_help_zip_images_text)
            HelpSection(R.string.cards_help_csv_title, R.string.cards_help_csv_text)
            HelpSection(R.string.cards_help_images_title, R.string.cards_help_images_text)
        }
    }
}

private fun message(res: Resources, outcome: CardDeckImportOutcome): String = when (outcome) {
    is CardDeckImportOutcome.Failed -> res.getString(outcome.error.messageRes())
    is CardDeckImportOutcome.Imported -> listOfNotNull(
        res.getString(R.string.cards_imported, outcome.name, outcome.cards),
        outcome.skippedFiles.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.cards_skipped, it, it) },
        outcome.missingImages.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.cards_missing, it, it) },
    ).joinToString(" · ")
}

fun CardDeckImportError.messageRes(): Int = when (this) {
    CardDeckImportError.NOT_A_ZIP -> R.string.cards_error_not_zip
    CardDeckImportError.UNSAFE_PATH -> R.string.cards_error_unsafe
    CardDeckImportError.TOO_MANY_CARDS -> R.string.cards_error_too_many
    CardDeckImportError.TOO_LARGE -> R.string.cards_error_too_large
    CardDeckImportError.NO_CARDS -> R.string.cards_error_no_cards
    CardDeckImportError.BAD_CSV -> R.string.cards_error_bad_csv
    CardDeckImportError.READ_FAILED -> R.string.cards_error_read
}

