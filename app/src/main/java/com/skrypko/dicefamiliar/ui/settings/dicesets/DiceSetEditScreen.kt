package com.skrypko.dicefamiliar.ui.settings.dicesets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.data.dice.DieImageResult
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetEditor
import com.skrypko.dicefamiliar.domain.dice.skin.DieImageSlot
import com.skrypko.dicefamiliar.ui.dice.DiceIcon
import com.skrypko.dicefamiliar.ui.dice.skin.DiceSkin
import com.skrypko.dicefamiliar.ui.dice.skin.LocalDiceSkin

/** Sides used to draw a die key: `d6` → 6; the custom `dN` uses 0 (a circle). */
private fun sidesFor(key: String): Int = if (key == DiceSetEditor.CUSTOM_KEY) 0 else key.drop(1).toInt()

@Composable
fun DiceSetEditScreen(onBack: () -> Unit, viewModel: DiceSetEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingSlot by rememberSaveable { mutableStateOf<DieImageSlot?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = selectedKey
        val slot = pickingSlot
        pickingSlot = null
        if (uri != null && key != null && slot != null) {
            val resolver = context.contentResolver
            viewModel.onImagePicked(key, slot) { resolver.openInputStream(uri) }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DiceSetEditEvent.ImageFailed -> snackbar.showSnackbar(
                    resources.getString(
                        if (event.result == DieImageResult.TOO_LARGE) {
                            R.string.dice_set_error_too_large
                        } else {
                            R.string.dice_set_error_unreadable
                        },
                    ),
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.dice_set_back))
                }
                if (!state.missing) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text(stringResource(R.string.dice_set_name)) },
                        isError = state.name.isBlank() && !state.loading,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                DiceSetHelpButton(onClick = { showHelp = true })
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.missing -> Text(
                    stringResource(R.string.dice_set_not_found),
                    modifier = Modifier.padding(24.dp),
                )
                else -> DiceGrid(state, onDieClick = { selectedKey = it })
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    val entry = state.dice.firstOrNull { it.key == selectedKey }
    if (entry != null && !state.missing) {
        DieImagesSheet(
            entry = entry,
            skin = state.skin,
            busySlot = state.busy?.takeIf { it.first == entry.key }?.second,
            onUpload = { slot ->
                pickingSlot = slot
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemove = { slot -> viewModel.onRemoveImage(entry.key, slot) },
            onDismiss = { selectedKey = null },
        )
    }
    if (showHelp) DiceSetHelpSheet(onDismiss = { showHelp = false })
}

@Composable
private fun DiceGrid(state: DiceSetEditUiState, onDieClick: (String) -> Unit) {
    Text(
        stringResource(R.string.dice_set_editor_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    CompositionLocalProvider(LocalDiceSkin provides state.skin) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(state.dice, key = { it.key }) { entry ->
                OutlinedCard(onClick = { onDieClick(entry.key) }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    ) {
                        DiceIcon(sides = sidesFor(entry.key), text = entry.key, size = 56.dp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 8.dp).height(20.dp),
                        ) {
                            if (entry.has2d) Badge(R.string.dice_set_badge_2d)
                            if (entry.has3d) Badge(R.string.dice_set_badge_3d)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(@StringRes text: Int) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DieImagesSheet(
    entry: DieEntry,
    skin: DiceSkin,
    busySlot: DieImageSlot?,
    onUpload: (DieImageSlot) -> Unit,
    onRemove: (DieImageSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    val dieSkin = skin.perDie[entry.key]
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(
                entry.key,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            SlotRow(
                title = R.string.dice_set_image_2d,
                summary = R.string.dice_set_image_2d_summary,
                isSet = entry.has2d,
                busy = busySlot == DieImageSlot.IMAGE_2D,
                preview = {
                    CompositionLocalProvider(LocalDiceSkin provides skin) {
                        DiceIcon(sides = sidesFor(entry.key), text = entry.key, size = 64.dp)
                    }
                },
                onUpload = { onUpload(DieImageSlot.IMAGE_2D) },
                onRemove = { onRemove(DieImageSlot.IMAGE_2D) },
            )
            if (entry.supports3d) {
                SlotRow(
                    title = R.string.dice_set_face_3d,
                    summary = R.string.dice_set_face_3d_summary,
                    isSet = entry.has3d,
                    busy = busySlot == DieImageSlot.FACE_3D,
                    preview = {
                        val texture = dieSkin?.faceTexture3d.takeIf { entry.has3d }
                        val shape = RoundedCornerShape(8.dp)
                        if (texture != null) {
                            Image(
                                texture,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(shape),
                            )
                        } else {
                            Box(
                                Modifier.size(64.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onUpload = { onUpload(DieImageSlot.FACE_3D) },
                    onRemove = { onRemove(DieImageSlot.FACE_3D) },
                )
            }
        }
    }
}

@Composable
private fun SlotRow(
    @StringRes title: Int,
    @StringRes summary: Int,
    isSet: Boolean,
    busy: Boolean,
    preview: @Composable () -> Unit,
    onUpload: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { preview() }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isSet) {
                Text(
                    stringResource(R.string.dice_set_not_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onUpload, enabled = !busy) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(if (isSet) R.string.dice_set_replace else R.string.dice_set_upload))
                    }
                }
                if (isSet) {
                    OutlinedButton(onClick = onRemove, enabled = !busy) {
                        Text(stringResource(R.string.dice_set_remove))
                    }
                }
            }
        }
    }
}
