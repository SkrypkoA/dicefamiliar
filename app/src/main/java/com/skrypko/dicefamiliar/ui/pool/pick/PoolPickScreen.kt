package com.skrypko.dicefamiliar.ui.pool.pick

import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.pool.PickError
import com.skrypko.dicefamiliar.domain.pool.PoolDisplayMode
import com.skrypko.dicefamiliar.domain.pool.PoolLimits
import com.skrypko.dicefamiliar.ui.common.CaptureAsImage
import com.skrypko.dicefamiliar.ui.common.HelpButton
import com.skrypko.dicefamiliar.ui.common.HelpSheet
import com.skrypko.dicefamiliar.ui.common.HelpTopic
import com.skrypko.dicefamiliar.ui.common.ShareMenuButton
import com.skrypko.dicefamiliar.ui.common.StepperArrows
import com.skrypko.dicefamiliar.ui.common.StepperNumberField
import com.skrypko.dicefamiliar.ui.common.shareImage
import com.skrypko.dicefamiliar.ui.pool.common.PoolItemTile
import com.skrypko.dicefamiliar.ui.theme.LocalThemeStyle
import com.skrypko.dicefamiliar.ui.theme.tray.themedTray
import com.skrypko.dicefamiliar.ui.theme.tray.trayColors
import java.io.File
import kotlinx.coroutines.launch

const val PICK_BUTTON_TAG = "pool_pick_button"
const val PICK_RESULT_TAG = "pool_pick_result"
const val PICK_PUT_BACK_TAG = "pool_put_back"

@Composable
fun PoolPickScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: PoolPickViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val settings = viewModel.uiState.value.settings
            when (event) {
                PoolPickEvent.Shown -> if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                PoolPickEvent.Invalid -> {
                    if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    if (settings.animationsEnabled) {
                        scope.launch {
                            shake.snapTo(0f)
                            shake.animateTo(0f, keyframes { durationMillis = 300; 12f at 50; -12f at 110; 8f at 170; -4f at 230 })
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PoolPickContent(
            state = state,
            fieldShake = shake.value,
            actions = PoolPickActions(
                onBack = onBack,
                onEdit = { state.pool?.let { onEdit(it.id) } },
                onCountChange = viewModel::onCountChange,
                onCountStep = viewModel::onCountStep,
                onAllowRepeatsChange = viewModel::onAllowRepeatsChange,
                onRemovePickedChange = viewModel::onRemovePickedChange,
                onShowNamesChange = viewModel::onShowNamesChange,
                onPutBackAll = viewModel::putBackAll,
                onPick = viewModel::pick,
                onClearHistory = viewModel::clearHistory,
                onCopy = { record ->
                    scope.launch {
                        val clip = ClipData.newPlainText(state.pool?.name.orEmpty(), record.shareText())
                        clipboard.setClipEntry(clip.toClipEntry())
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbar.showSnackbar(resources.getString(R.string.pool_copied))
                    }
                },
                onShare = { record ->
                    val text = listOfNotNull(state.pool?.name, record.shareText()).joinToString(": ")
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, resources.getString(R.string.pool_share)))
                },
                onShareImage = { image ->
                    scope.launch { shareImage(context, image, "pick.png", resources.getString(R.string.pool_share)) }
                },
            ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** The picked names, comma-separated. */
fun PickRecord.shareText(): String = items.joinToString(", ") { it.name }

data class PoolPickActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onCountChange: (String) -> Unit = {},
    val onCountStep: (Int) -> Unit = {},
    val onAllowRepeatsChange: (Boolean) -> Unit = {},
    val onRemovePickedChange: (Boolean) -> Unit = {},
    val onShowNamesChange: (Boolean) -> Unit = {},
    val onPutBackAll: () -> Unit = {},
    val onPick: () -> Unit = {},
    val onClearHistory: () -> Unit = {},
    val onCopy: (PickRecord) -> Unit = {},
    val onShare: (PickRecord) -> Unit = {},
    val onShareImage: (ImageBitmap) -> Unit = {},
)

/** Options on top, the result tray in the middle (copy, share, history), Pick at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolPickContent(state: PoolPickUiState, actions: PoolPickActions, modifier: Modifier = Modifier, fieldShake: Float = 0f) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val pool = state.pool
    val mode = pool?.displayMode ?: PoolDisplayMode.NAMES_ONLY
    val latest = state.latest
    if (capturing && latest != null && pool != null) {
        CaptureAsImage(onCaptured = { image ->
            capturing = false
            actions.onShareImage(image)
        }) { PickSnapshot(pool.name, latest, mode, state.imageDir) }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.pool_pick_back))
            }
            Text(pool?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
            IconButton(onClick = actions.onEdit, enabled = pool != null) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.pool_edit, pool?.name.orEmpty()))
            }
            HelpButton(stringResource(R.string.pool_help), onClick = { showHelp = true })
        }
        Options(state, actions, Modifier.offset { IntOffset(fieldShake.dp.roundToPx(), 0) })
        ResultTray(
            state = state,
            mode = mode,
            onCopy = actions.onCopy,
            onShare = actions.onShare,
            onShareImage = { capturing = true },
            onShowHistory = if (state.recent.size > 1) ({ showHistory = true }) else null,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Button(
            onClick = actions.onPick,
            enabled = !state.isPicking && state.available.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag(PICK_BUTTON_TAG),
        ) {
            Icon(LocalThemeStyle.current.icons.generate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pool_pick), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (showHistory && state.recent.size > 1) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pool_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        actions.onClearHistory()
                        showHistory = false
                    }) { Text(stringResource(R.string.pool_history_clear)) }
                }
                state.recent.drop(1).forEach { record ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(record.shareText(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { actions.onCopy(record) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.pool_copy))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (showHelp) HelpSheet(R.string.pool_help_title, POOL_PICK_HELP, onDismiss = { showHelp = false })
}

@Composable
private fun Options(state: PoolPickUiState, actions: PoolPickActions, modifier: Modifier = Modifier) {
    val pool = state.pool
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            val name = stringResource(R.string.pool_pick_count)
            val count = state.countValue
            StepperNumberField(
                value = state.count,
                onValueChange = actions.onCountChange,
                onStep = actions.onCountStep,
                increaseDescription = stringResource(R.string.stepper_increase, name),
                decreaseDescription = stringResource(R.string.stepper_decrease, name),
                arrows = StepperArrows.AFTER,
                label = name,
                isError = state.countError != null,
                supportingText = state.countError?.let { error -> { Text(countErrorText(error, state)) } },
                keyboardType = KeyboardType.NumberPassword,
                textStyle = MaterialTheme.typography.titleLarge,
                canIncrease = count == null || count < state.maxCount,
                canDecrease = count == null || count > 1,
                modifier = Modifier.weight(1f),
            )
            SwitchRow(stringResource(R.string.pool_allow_repeats), state.allowRepeats, actions.onAllowRepeatsChange, Modifier.weight(1f).padding(top = 8.dp))
        }
        SwitchRow(stringResource(R.string.pool_remove_picked), state.removePicked, actions.onRemovePickedChange, Modifier.fillMaxWidth())
        if (state.removePicked && pool != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    pluralStringResource(R.plurals.pool_left, state.available.size, state.available.size, pool.items.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onPutBackAll, enabled = state.setAsideCount > 0, modifier = Modifier.testTag(PICK_PUT_BACK_TAG)) {
                    Icon(Icons.Outlined.Restore, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.pool_put_back))
                }
            }
        }
        if (pool != null && PoolDisplayMode.canHideNames(pool.items)) {
            SwitchRow(stringResource(R.string.pool_show_names), pool.showNames, actions.onShowNamesChange, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun countErrorText(error: PickError, state: PoolPickUiState): String = when (error) {
    PickError.COUNT_EXCEEDS_POOL -> pluralStringResource(R.plurals.pool_error_count_pool, state.available.size, state.available.size)
    PickError.COUNT_TOO_LARGE -> stringResource(R.string.pool_error_count_max, PoolLimits.MAX_PICKS)
    PickError.COUNT_TOO_SMALL, PickError.EMPTY_POOL -> stringResource(R.string.pool_error_count)
}

@Composable
private fun ResultTray(
    state: PoolPickUiState,
    mode: PoolDisplayMode,
    onCopy: (PickRecord) -> Unit,
    onShare: (PickRecord) -> Unit,
    onShareImage: () -> Unit,
    onShowHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val latest = state.latest
    val shape = MaterialTheme.shapes.extraLarge
    val colors = trayColors()
    Surface(modifier.themedTray(shape), shape = shape, color = Color.Transparent, contentColor = colors.content) {
        Column(Modifier.padding(8.dp + LocalThemeStyle.current.tray.contentInset)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                val enabled = latest != null && !state.isPicking
                IconButton(onClick = { latest?.let(onCopy) }, enabled = enabled) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.pool_copy))
                }
                ShareMenuButton(
                    contentDescription = stringResource(R.string.pool_share),
                    onShareText = { latest?.let(onShare) },
                    onShareImage = onShareImage,
                    enabled = enabled,
                )
                IconButton(onClick = { onShowHistory?.invoke() }, enabled = onShowHistory != null) {
                    Icon(Icons.Filled.History, contentDescription = stringResource(R.string.pool_history))
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val animate = state.settings.animationsEnabled
                when {
                    state.isPicking && animate -> PickingDots()
                    state.pool != null && state.available.isEmpty() -> Hint(
                        stringResource(if (state.pool.items.isEmpty()) R.string.pool_pick_empty else R.string.pool_pick_all_out),
                    )
                    latest == null -> Hint(stringResource(R.string.pool_pick_hint))
                    animate -> AnimatedContent(
                        targetState = latest,
                        contentKey = { it.id },
                        transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.7f)) togetherWith fadeOut(tween(90)) },
                        label = "pick",
                    ) { PickedItems(it, mode, state.imageDir) }
                    else -> PickedItems(latest, mode, state.imageDir)
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = trayColors().muted, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
}

@Composable
private fun PickingDots() {
    val pulse by rememberInfiniteTransition(label = "picking").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
        label = "pulse",
    )
    val description = stringResource(R.string.pool_picking)
    Text(
        "• • •",
        style = MaterialTheme.typography.displayMedium,
        color = trayColors().accent,
        modifier = Modifier.alpha(pulse).semantics { contentDescription = description },
    )
}

/** One result large, several as a wrapping grid of tiles. */
@Composable
private fun PickedItems(record: PickRecord, mode: PoolDisplayMode, imageDir: File, scrollable: Boolean = true) {
    val single = record.items.size == 1
    Column(
        Modifier.fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(8.dp).testTag(PICK_RESULT_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (single) {
            PoolItemTile(
                record.items.single(),
                mode,
                imageDir,
                imageSize = 180.dp,
                labelStyle = if (mode == PoolDisplayMode.NAMES_ONLY) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                record.items.forEach { PoolItemTile(it, mode, imageDir, imageSize = 80.dp) }
            }
        }
    }
}

/** The picture shared by "Share as image": the pool name, then the picked items on the theme's tray. */
@Composable
private fun PickSnapshot(poolName: String, record: PickRecord, mode: PoolDisplayMode, imageDir: File) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(poolName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Box(
            Modifier.fillMaxWidth().themedTray(MaterialTheme.shapes.extraLarge).padding(16.dp + LocalThemeStyle.current.tray.contentInset),
            contentAlignment = Alignment.Center,
        ) {
            PickedItems(record, mode, imageDir, scrollable = false)
        }
    }
}

val POOL_PICK_HELP = listOf(
    HelpTopic(R.string.help_pool_pick_title, R.string.help_pool_pick_text),
    HelpTopic(R.string.help_pool_repeats_title, R.string.help_pool_repeats_text),
    HelpTopic(R.string.help_pool_remove_title, R.string.help_pool_remove_text),
    HelpTopic(R.string.help_pool_names_title, R.string.help_pool_names_text),
    HelpTopic(R.string.help_pool_results_title, R.string.help_pool_results_text),
)
