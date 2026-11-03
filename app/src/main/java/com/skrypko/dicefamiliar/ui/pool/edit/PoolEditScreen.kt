package com.skrypko.dicefamiliar.ui.pool.edit

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.pool.ItemColors
import com.skrypko.dicefamiliar.domain.pool.PoolDisplayMode
import com.skrypko.dicefamiliar.domain.pool.PoolError
import com.skrypko.dicefamiliar.domain.pool.PoolItem
import com.skrypko.dicefamiliar.domain.pool.io.ImportFileTypes
import com.skrypko.dicefamiliar.domain.pool.io.PoolImportError
import com.skrypko.dicefamiliar.domain.pool.io.SafeFileNames
import com.skrypko.dicefamiliar.ui.common.HelpButton
import com.skrypko.dicefamiliar.ui.common.HelpSheet
import com.skrypko.dicefamiliar.ui.common.HelpTopic
import com.skrypko.dicefamiliar.ui.common.displayName
import com.skrypko.dicefamiliar.ui.common.shareFile
import com.skrypko.dicefamiliar.ui.common.sharedCacheFile
import com.skrypko.dicefamiliar.ui.pool.common.ItemNameLabel
import com.skrypko.dicefamiliar.ui.pool.common.ItemThumbnail
import com.skrypko.dicefamiliar.ui.pool.common.contentColorOn
import com.skrypko.dicefamiliar.ui.settings.dicesets.HelpBody
import com.skrypko.dicefamiliar.ui.settings.dicesets.HelpCode
import com.skrypko.dicefamiliar.ui.settings.dicesets.HelpSection
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

const val POOL_NAME_FIELD_TAG = "pool_name_field"
const val POOL_SHOW_NAMES_TAG = "pool_show_names"
const val POOL_ITEM_ROW_TAG = "pool_item_row"
const val ITEM_NAME_FIELD_TAG = "item_name_field"
const val ITEM_SAVE_TAG = "item_save"

private const val CSV_MIME = "text/csv"
private const val ZIP_MIME = "application/zip"

/** [onDone] gets the saved pool's id when it has items (so the caller can open Pick), otherwise `null`. */
@Composable
fun PoolEditScreen(onDone: (pickPoolId: Long?) -> Unit, viewModel: PoolEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resolver = context.contentResolver

    var pendingExport by remember { mutableStateOf(false) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) { uri ->
        if (uri != null) viewModel.export(withImages = false, open = { resolver.openOutputStream(uri) })
    }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_MIME)) { uri ->
        if (uri != null) viewModel.export(withImages = true, open = { resolver.openOutputStream(uri) })
    }
    val openImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import({ resolver.openInputStream(uri) }, displayName(resolver, uri), resolver.getType(uri))
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onEditorImagePicked { resolver.openInputStream(uri) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PoolEditEvent.Saved -> onDone(event.poolId.takeIf { event.hasItems })
                PoolEditEvent.Discarded -> onDone(null)
                else -> message(resources, event)?.let { scope.launch { snackbar.showSnackbar(it) } }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PoolEditContent(
            state = state,
            actions = PoolEditActions(
                onBack = viewModel::discard,
                onSave = viewModel::save,
                onNameChange = viewModel::onNameChange,
                onShowNamesChange = viewModel::onShowNamesChange,
                onAddItem = viewModel::addItem,
                onEditItem = viewModel::editItem,
                onMove = viewModel::move,
                onEditorNameChange = viewModel::onEditorNameChange,
                onEditorColorChange = viewModel::onEditorColorChange,
                onEditorPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onEditorRemoveImage = viewModel::onEditorRemoveImage,
                onEditorSave = viewModel::saveEditor,
                onEditorDelete = viewModel::deleteEditorItem,
                onEditorDismiss = viewModel::dismissEditor,
                onChooseImportFile = { openImport.launch(ImportFileTypes.MIME_TYPES) },
                onApplyImport = viewModel::applyImport,
                onCancelImport = viewModel::cancelImport,
                onSaveExport = { zip ->
                    val name = SafeFileNames.forPool(state.name.ifBlank { "pool" }, if (zip) "zip" else "csv")
                    if (zip) saveZip.launch(name) else saveCsv.launch(name)
                },
                onShareExport = { zip ->
                    if (!pendingExport) {
                        pendingExport = true
                        val file = sharedCacheFile(context, SafeFileNames.forPool(state.name.ifBlank { "pool" }, if (zip) "zip" else "csv"))
                        viewModel.export(zip, open = { FileOutputStream(file) }, announce = false) { ok ->
                            pendingExport = false
                            if (ok) shareFile(context, file, if (zip) ZIP_MIME else CSV_MIME, resources.getString(R.string.pool_export_share))
                        }
                    }
                },
            ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Callbacks of [PoolEditContent]; defaults do nothing (handy in tests and previews). */
data class PoolEditActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onShowNamesChange: (Boolean) -> Unit = {},
    val onAddItem: () -> Unit = {},
    val onEditItem: (Long) -> Unit = {},
    val onMove: (Long, Int) -> Unit = { _, _ -> },
    val onEditorNameChange: (String) -> Unit = {},
    val onEditorColorChange: (Int?) -> Unit = {},
    val onEditorPickImage: () -> Unit = {},
    val onEditorRemoveImage: () -> Unit = {},
    val onEditorSave: () -> Unit = {},
    val onEditorDelete: () -> Unit = {},
    val onEditorDismiss: () -> Unit = {},
    val onChooseImportFile: () -> Unit = {},
    val onApplyImport: (replace: Boolean) -> Unit = {},
    val onCancelImport: () -> Unit = {},
    val onSaveExport: (zip: Boolean) -> Unit = {},
    val onShareExport: (zip: Boolean) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolEditContent(state: PoolEditUiState, actions: PoolEditActions, modifier: Modifier = Modifier) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showImportHelp by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (state.dirty) confirmDiscard = true else actions.onBack() }
    BackHandler(enabled = state.editor == null && state.preview == null, onBack = leave)

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = leave) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.pool_edit_back))
            }
            Text(
                stringResource(if (state.isNew) R.string.pool_new_title else R.string.pool_edit_title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showImport = true }, enabled = !state.importing) {
                Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.pool_import))
            }
            IconButton(onClick = { showExport = true }, enabled = state.items.isNotEmpty()) {
                Icon(Icons.Outlined.FileUpload, contentDescription = stringResource(R.string.pool_export))
            }
            HelpButton(stringResource(R.string.pool_help), onClick = { showHelp = true })
            Button(onClick = actions.onSave, enabled = state.loaded, modifier = Modifier.padding(end = 8.dp)) {
                Text(stringResource(R.string.pool_save))
            }
        }
        if (state.importing) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pool_importing), style = MaterialTheme.typography.bodyMedium)
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = actions.onNameChange,
                    label = { Text(stringResource(R.string.pool_name)) },
                    isError = state.nameError,
                    supportingText = if (state.nameError) ({ Text(stringResource(R.string.pool_error_name_required)) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(POOL_NAME_FIELD_TAG),
                )
                ShowNamesRow(state, actions.onShowNamesChange)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        pluralStringResource(R.plurals.pool_items, state.items.size, state.items.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = actions.onAddItem) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.pool_add_item))
                    }
                }
                if (state.items.isEmpty()) {
                    Text(
                        stringResource(R.string.pool_items_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            itemsIndexed(state.items, key = { _, it -> it.key }) { index, draft ->
                ItemRow(
                    item = draft.item,
                    mode = state.displayMode,
                    imageDir = state.imageDir,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.items.lastIndex,
                    onClick = { actions.onEditItem(draft.key) },
                    onMove = { actions.onMove(draft.key, it) },
                )
            }
        }
    }

    state.editor?.let { ItemEditorSheet(it, state.imageDir, actions) }
    state.preview?.let { ImportPreviewDialog(it, state.items.size, actions.onApplyImport, actions.onCancelImport) }
    if (showImport) {
        ImportDialog(
            onChoose = {
                showImport = false
                actions.onChooseImportFile()
            },
            onHelp = { showImportHelp = true },
            onDismiss = { showImport = false },
        )
    }
    if (showImportHelp) PoolImportHelpSheet(onDismiss = { showImportHelp = false })
    if (showExport) {
        ExportDialog(
            hasImages = state.hasImages,
            onSave = { zip ->
                showExport = false
                actions.onSaveExport(zip)
            },
            onShare = { zip ->
                showExport = false
                actions.onShareExport(zip)
            },
            onDismiss = { showExport = false },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.pool_discard_title)) },
            text = { Text(stringResource(R.string.pool_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    actions.onBack()
                }) { Text(stringResource(R.string.pool_discard_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.pool_keep_editing)) } },
        )
    }
    if (showHelp) HelpSheet(R.string.pool_help_title, POOL_EDIT_HELP, onDismiss = { showHelp = false })
}

/** "Show names": only possible to turn off when every item has an image. */
@Composable
private fun ShowNamesRow(state: PoolEditUiState, onChange: (Boolean) -> Unit) {
    val enabled = state.canHideNames
    val checked = state.showNames || !enabled
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag(POOL_SHOW_NAMES_TAG)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pool_show_names), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (enabled) R.string.pool_show_names_hint else R.string.pool_show_names_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ItemRow(
    item: PoolItem,
    mode: PoolDisplayMode,
    imageDir: File,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(POOL_ITEM_ROW_TAG)
            .clickable(onClickLabel = stringResource(R.string.pool_edit_item), onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        if (mode != PoolDisplayMode.NAMES_ONLY) {
            ItemThumbnail(item, imageDir, if (mode == PoolDisplayMode.IMAGES_ONLY) 64.dp else 48.dp, describe = mode == PoolDisplayMode.IMAGES_ONLY)
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f)) {
            if (mode != PoolDisplayMode.IMAGES_ONLY) ItemNameLabel(item.name, item.colorArgb, maxLines = 1)
        }
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.pool_move_up, item.name))
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.pool_move_down, item.name))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemEditorSheet(editor: ItemEditorState, imageDir: File, actions: PoolEditActions) {
    ModalBottomSheet(onDismissRequest = actions.onEditorDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editor.key == null) R.string.pool_add_item else R.string.pool_edit_item),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = editor.name,
                onValueChange = actions.onEditorNameChange,
                label = { Text(stringResource(R.string.pool_item_name)) },
                isError = editor.error != null,
                supportingText = editor.error?.let { error -> { Text(itemErrorText(error, editor.name)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(ITEM_NAME_FIELD_TAG),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ItemThumbnail(PoolItem(name = editor.name, imagePath = editor.imagePath, colorArgb = editor.colorArgb), imageDir, 72.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = actions.onEditorPickImage, enabled = !editor.storingImage) {
                        if (editor.storingImage) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (editor.imagePath == null) R.string.pool_item_add_image else R.string.pool_item_replace_image))
                    }
                    if (editor.imagePath != null) {
                        TextButton(onClick = actions.onEditorRemoveImage) {
                            Icon(Icons.Outlined.HideImage, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.pool_item_remove_image))
                        }
                    }
                }
            }
            Text(stringResource(R.string.pool_item_color), style = MaterialTheme.typography.titleSmall)
            ColorChips(editor.colorArgb, actions.onEditorColorChange)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (editor.key != null) {
                    TextButton(onClick = actions.onEditorDelete) {
                        Text(stringResource(R.string.pool_item_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = actions.onEditorDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = actions.onEditorSave, enabled = editor.canSave, modifier = Modifier.testTag(ITEM_SAVE_TAG)) {
                    Text(stringResource(R.string.pool_item_done))
                }
            }
        }
    }
}

@Composable
private fun ColorChips(selected: Int?, onSelect: (Int?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val noneLabel = stringResource(R.string.pool_item_no_color)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(36.dp)
                .border(if (selected == null) 3.dp else 1.dp, if (selected == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                .selectable(selected = selected == null, role = Role.RadioButton, onClick = { onSelect(null) })
                .semantics { contentDescription = noneLabel },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        ItemColors.palette.forEach { argb ->
            val label = stringResource(R.string.pool_item_color_value, ItemColors.format(argb))
            Surface(
                shape = CircleShape,
                color = Color(argb),
                modifier = Modifier
                    .size(36.dp)
                    .then(if (selected == argb) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                    .selectable(selected = selected == argb, role = Role.RadioButton, onClick = { onSelect(argb) })
                    .semantics { contentDescription = label },
            ) {
                if (selected == argb) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = contentColorOn(argb), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun itemErrorText(error: PoolError, name: String): String = when (error) {
    PoolError.DUPLICATE_ITEM_NAME -> stringResource(R.string.pool_error_duplicate, name.trim())
    PoolError.ITEM_NAME_REQUIRED -> stringResource(R.string.pool_error_item_name_required)
    PoolError.NAME_REQUIRED -> stringResource(R.string.pool_error_name_required)
    PoolError.TOO_MANY_ITEMS -> stringResource(R.string.pool_error_too_many)
}

@Composable
private fun ImportDialog(onChoose: () -> Unit, onHelp: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pool_import_title), modifier = Modifier.weight(1f))
                HelpButton(stringResource(R.string.pool_import_help), onClick = onHelp)
            }
        },
        text = { Text(stringResource(R.string.pool_import_hint)) },
        confirmButton = { Button(onClick = onChoose) { Text(stringResource(R.string.pool_import_choose)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ImportPreviewDialog(preview: ImportPreview, current: Int, onApply: (Boolean) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.pool_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(
                        pluralStringResource(R.plurals.pool_items, preview.items.size, preview.items.size),
                        pluralStringResource(R.plurals.pool_images, preview.images, preview.images).takeIf { preview.images > 0 },
                        pluralStringResource(R.plurals.pool_images_skipped, preview.missingImages, preview.missingImages)
                            .takeIf { preview.missingImages > 0 },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (current > 0) Text(pluralStringResource(R.plurals.pool_import_question, current, current))
            }
        },
        confirmButton = {
            Row {
                if (current > 0) TextButton(onClick = { onApply(true) }) { Text(stringResource(R.string.pool_import_replace)) }
                Button(onClick = { onApply(false) }) {
                    Text(stringResource(if (current > 0) R.string.pool_import_extend else R.string.pool_import_add))
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ExportDialog(hasImages: Boolean, onSave: (Boolean) -> Unit, onShare: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var zip by rememberSaveable { mutableStateOf(hasImages) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pool_export_title)) },
        text = {
            if (hasImages) {
                Column {
                    listOf(true to R.string.pool_export_zip, false to R.string.pool_export_csv).forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().selectable(selected = zip == value, role = Role.RadioButton, onClick = { zip = value }),
                        ) {
                            RadioButton(selected = zip == value, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(label))
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.pool_export_csv_only))
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onShare(zip && hasImages) }) { Text(stringResource(R.string.pool_export_share_button)) }
                Button(onClick = { onSave(zip && hasImages) }) { Text(stringResource(R.string.pool_export_save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The import format sheet: both formats, the CSV example, the ZIP tree and the allowed file types. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolImportHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(stringResource(R.string.pool_import_help), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            HelpBody(R.string.pool_import_hint)
            HelpSection(R.string.pool_help_csv_title, R.string.pool_help_csv_text)
            HelpCode(R.string.pool_help_csv_example)
            HelpSection(R.string.pool_help_zip_title, R.string.pool_help_zip_text)
            HelpCode(R.string.pool_help_zip_tree)
            HelpSection(R.string.pool_help_rules_title, R.string.pool_help_rules_text)
        }
    }
}

val POOL_EDIT_HELP = listOf(
    HelpTopic(R.string.help_pool_items_title, R.string.help_pool_items_text),
    HelpTopic(R.string.help_pool_names_title, R.string.help_pool_names_text),
    HelpTopic(R.string.help_pool_display_title, R.string.help_pool_display_text),
    HelpTopic(R.string.help_pool_import_title, R.string.help_pool_import_text),
    HelpTopic(R.string.help_pool_export_title, R.string.help_pool_export_text),
)

private fun message(res: Resources, event: PoolEditEvent): String? = when (event) {
    is PoolEditEvent.Imported -> listOfNotNull(
        res.getQuantityString(R.plurals.pool_imported, event.items, event.items),
        event.renamed.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.pool_renamed, it, it) },
        event.imagesSkipped.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.pool_images_skipped, it, it) },
    ).joinToString(" · ")
    is PoolEditEvent.ImportFailed -> res.getString(event.error.messageRes())
    PoolEditEvent.ImageFailed -> res.getString(R.string.pool_error_image)
    PoolEditEvent.TooManyItems -> res.getString(R.string.pool_error_too_many)
    is PoolEditEvent.Exported -> res.getString(if (event.ok) R.string.pool_exported else R.string.pool_export_failed)
    is PoolEditEvent.Saved, PoolEditEvent.Discarded -> null
}

fun PoolImportError.messageRes(): Int = when (this) {
    PoolImportError.NOT_A_ZIP -> R.string.pool_error_not_zip
    PoolImportError.NO_ITEMS_CSV -> R.string.pool_error_no_items_csv
    PoolImportError.NO_ITEMS -> R.string.pool_error_no_items
    PoolImportError.BAD_CSV -> R.string.pool_error_bad_csv
    PoolImportError.UNSAFE_PATH -> R.string.pool_error_unsafe
    PoolImportError.TOO_LARGE -> R.string.pool_error_too_large
    PoolImportError.TOO_MANY_ITEMS -> R.string.pool_error_too_many
    PoolImportError.READ_FAILED -> R.string.pool_error_read
}
