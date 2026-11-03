package com.skrypko.dicefamiliar.ui.number

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.number.NumberError
import com.skrypko.dicefamiliar.domain.number.NumberField
import com.skrypko.dicefamiliar.domain.number.NumberFormatter
import com.skrypko.dicefamiliar.domain.number.NumberLimits
import com.skrypko.dicefamiliar.domain.number.NumberMode
import com.skrypko.dicefamiliar.domain.number.NumberResult
import com.skrypko.dicefamiliar.ui.common.HelpButton
import com.skrypko.dicefamiliar.ui.common.HelpSheet
import com.skrypko.dicefamiliar.ui.common.HelpTopic
import com.skrypko.dicefamiliar.ui.common.StepperArrows
import com.skrypko.dicefamiliar.ui.common.StepperNumberField
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import com.skrypko.dicefamiliar.ui.theme.LocalThemeStyle
import com.skrypko.dicefamiliar.ui.theme.tray.themedTray
import com.skrypko.dicefamiliar.ui.theme.tray.trayColors
import kotlinx.coroutines.launch

@Composable
fun NumberScreen(viewModel: NumberViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                NumberEvent.Shown ->
                    if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                NumberEvent.Invalid -> {
                    if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    if (settings.animationsEnabled) {
                        scope.launch {
                            shake.snapTo(0f)
                            shake.animateTo(
                                0f,
                                keyframes {
                                    durationMillis = 300
                                    12f at 50
                                    -12f at 110
                                    8f at 170
                                    -4f at 230
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        NumberContent(
            state = uiState,
            fieldShake = shake.value,
            onMinChange = viewModel::onMinChange,
            onMaxChange = viewModel::onMaxChange,
            onCountChange = viewModel::onCountChange,
            onMinStep = viewModel::onMinStep,
            onMaxStep = viewModel::onMaxStep,
            onCountStep = viewModel::onCountStep,
            onAllowDuplicatesChange = viewModel::onAllowDuplicatesChange,
            onModeChange = viewModel::onModeChange,
            onDecimalsChange = viewModel::onDecimalsChange,
            onGenerate = viewModel::generate,
            onClearHistory = viewModel::clearHistory,
            onCopy = { result ->
                scope.launch {
                    val clip = ClipData.newPlainText(resources.getString(R.string.title_number), result.text)
                    clipboard.setClipEntry(clip.toClipEntry())
                    // Android 13+ confirms clipboard copies itself.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        snackbar.showSnackbar(resources.getString(R.string.number_copied))
                    }
                }
            },
            onShare = { result ->
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, result.text)
                context.startActivity(Intent.createChooser(send, resources.getString(R.string.number_share)))
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Form (range, count, mode, duplicates) on top, the result area in the middle, Generate at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberContent(
    state: NumberUiState,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    onCountChange: (String) -> Unit,
    onAllowDuplicatesChange: (Boolean) -> Unit,
    onModeChange: (NumberMode) -> Unit,
    onDecimalsChange: (Int) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
    onMinStep: (Int) -> Unit = {},
    onMaxStep: (Int) -> Unit = {},
    onCountStep: (Int) -> Unit = {},
    fieldShake: Float = 0f,
    onClearHistory: () -> Unit = {},
    onCopy: (NumberResult) -> Unit = {},
    onShare: (NumberResult) -> Unit = {},
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val older = state.recent.drop(1)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NumberForm(
            state = state,
            onMinChange = onMinChange,
            onMaxChange = onMaxChange,
            onCountChange = onCountChange,
            onMinStep = onMinStep,
            onMaxStep = onMaxStep,
            onCountStep = onCountStep,
            onAllowDuplicatesChange = onAllowDuplicatesChange,
            onModeChange = onModeChange,
            onDecimalsChange = onDecimalsChange,
            modifier = Modifier.offset { IntOffset(fieldShake.dp.roundToPx(), 0) },
        )
        ResultArea(
            state = state,
            onCopy = onCopy,
            onShare = onShare,
            onShowHistory = if (older.isNotEmpty()) ({ showHistory = true }) else null,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Button(
            onClick = onGenerate,
            enabled = !state.isGenerating,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(LocalThemeStyle.current.icons.generate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.number_generate), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (showHistory && older.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                RecentResults(older, onCopy = onCopy, onClear = {
                    onClearHistory()
                    showHistory = false
                })
            }
        }
    }
}

@Composable
private fun NumberForm(
    state: NumberUiState,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    onCountChange: (String) -> Unit,
    onMinStep: (Int) -> Unit,
    onMaxStep: (Int) -> Unit,
    onCountStep: (Int) -> Unit,
    onAllowDuplicatesChange: (Boolean) -> Unit,
    onModeChange: (NumberMode) -> Unit,
    onDecimalsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val decimal = state.mode == NumberMode.DECIMAL
    val boundKeyboard = if (decimal) KeyboardType.Decimal else KeyboardType.Number
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                value = state.min,
                onValueChange = onMinChange,
                onStep = onMinStep,
                label = R.string.number_min,
                error = state.errors[NumberField.MIN],
                state = state,
                keyboard = boundKeyboard,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = state.max,
                onValueChange = onMaxChange,
                onStep = onMaxStep,
                label = R.string.number_max,
                error = state.errors[NumberField.MAX],
                state = state,
                keyboard = boundKeyboard,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            NumberField(
                value = state.count,
                onValueChange = onCountChange,
                onStep = onCountStep,
                label = R.string.number_count,
                error = state.errors[NumberField.COUNT],
                state = state,
                keyboard = KeyboardType.NumberPassword,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .weight(1f)
                    .padding(top = 8.dp)
                    .toggleable(
                        value = state.allowDuplicates,
                        role = Role.Switch,
                        onValueChange = onAllowDuplicatesChange,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.number_allow_duplicates),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.allowDuplicates, onCheckedChange = null)
            }
        }
        val modes = NumberMode.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == state.mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    NumberMode.INTEGER -> R.string.number_mode_integer
                                    NumberMode.DECIMAL -> R.string.number_mode_decimal
                                },
                            ),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        if (decimal) DecimalsStepper(state.decimals, onDecimalsChange)
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    label: Int,
    error: NumberError?,
    state: NumberUiState,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(label)
    StepperNumberField(
        value = value,
        onValueChange = onValueChange,
        onStep = onStep,
        increaseDescription = stringResource(R.string.stepper_increase, name),
        decreaseDescription = stringResource(R.string.stepper_decrease, name),
        arrows = StepperArrows.AFTER,
        label = name,
        isError = error != null,
        supportingText = error?.let { { Text(errorMessage(it, state)) } },
        keyboardType = keyboard,
        textStyle = MaterialTheme.typography.titleLarge,
        modifier = modifier,
    )
}

@Composable
private fun errorMessage(error: NumberError, state: NumberUiState): String = when (error) {
    NumberError.MIN_INVALID, NumberError.MAX_INVALID -> stringResource(
        if (state.mode == NumberMode.INTEGER) R.string.number_error_whole else R.string.number_error_number,
    )
    NumberError.MIN_TOO_PRECISE, NumberError.MAX_TOO_PRECISE -> pluralStringResource(R.plurals.number_error_too_precise, state.decimals, state.decimals)
    NumberError.MIN_OUT_OF_LIMITS, NumberError.MAX_OUT_OF_LIMITS -> stringResource(R.string.number_error_limits)
    NumberError.MIN_ABOVE_MAX -> stringResource(R.string.number_error_min_above_max)
    NumberError.COUNT_INVALID -> stringResource(R.string.number_error_count)
    NumberError.COUNT_EXCEEDS_RANGE -> stringResource(R.string.number_error_count_range)
}

@Composable
private fun DecimalsStepper(decimals: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.number_decimals),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(decimals - 1) }, enabled = decimals > NumberLimits.MIN_DECIMALS) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.number_decimals_less))
        }
        Text(
            decimals.toString(),
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = { onChange(decimals + 1) }, enabled = decimals < NumberLimits.MAX_DECIMALS) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.number_decimals_more))
        }
    }
}

@Composable
private fun ResultArea(
    state: NumberUiState,
    onCopy: (NumberResult) -> Unit,
    onShare: (NumberResult) -> Unit,
    onShowHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val latest = state.latest
    var showHelp by rememberSaveable { mutableStateOf(false) }
    if (showHelp) HelpSheet(R.string.help_number_title, NUMBER_HELP, onDismiss = { showHelp = false })
    val shape = MaterialTheme.shapes.extraLarge
    val colors = trayColors()
    Surface(
        modifier.themedTray(shape),
        shape = shape,
        // The theme's tray painter draws the background (plain surface, felt, neon grid, ...).
        color = Color.Transparent,
        contentColor = colors.content,
    ) {
        Column(Modifier.padding(8.dp + LocalThemeStyle.current.tray.contentInset)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    latest?.let { rangeCaption(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.muted,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    maxLines = 2,
                )
                val actionsEnabled = latest != null && !state.isGenerating
                IconButton(onClick = { latest?.let(onCopy) }, enabled = actionsEnabled) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.number_copy))
                }
                IconButton(onClick = { latest?.let(onShare) }, enabled = actionsEnabled) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.number_share))
                }
                IconButton(onClick = { onShowHistory?.invoke() }, enabled = onShowHistory != null) {
                    Icon(Icons.Filled.History, contentDescription = stringResource(R.string.number_history))
                }
                HelpButton(contentDescription = stringResource(R.string.help_number_button), onClick = { showHelp = true })
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val animate = state.settings.animationsEnabled
                when {
                    state.isGenerating && animate -> GeneratingDots()
                    latest == null -> Text(
                        stringResource(R.string.number_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.muted,
                    )
                    animate -> AnimatedContent(
                        targetState = latest,
                        contentKey = { it.id },
                        transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.7f)) togetherWith fadeOut(tween(90)) },
                        label = "result",
                    ) { ResultValues(it) }
                    else -> ResultValues(latest)
                }
            }
        }
    }
}

@Composable
private fun GeneratingDots() {
    val pulse by rememberInfiniteTransition(label = "generating").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
        label = "pulse",
    )
    val description = stringResource(R.string.number_generating)
    Text(
        "• • •",
        style = MaterialTheme.typography.displayMedium,
        color = trayColors().accent,
        modifier = Modifier.alpha(pulse).semantics { contentDescription = description },
    )
}

@Composable
private fun ResultValues(result: NumberResult) {
    if (result.values.size == 1) {
        Text(
            NumberFormatter.format(result.values.single(), result.decimals),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
            color = trayColors().accent,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 112.sp),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            result.values.forEach { value ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        NumberFormatter.format(value, result.decimals),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.number_sum, NumberFormatter.format(result.sum, result.decimals)),
            style = MaterialTheme.typography.titleMedium,
            color = trayColors().muted,
        )
    }
}

@Composable
private fun rangeCaption(result: NumberResult): String {
    val min = NumberFormatter.format(result.min, result.decimals)
    val max = NumberFormatter.format(result.max, result.decimals)
    val base = if (result.values.size == 1) {
        stringResource(R.string.number_range, min, max)
    } else {
        stringResource(R.string.number_range_count, min, max, result.values.size)
    }
    return if (result.allowDuplicates || result.values.size == 1) base else "$base · ${stringResource(R.string.number_no_duplicates)}"
}

@Composable
private fun RecentResults(results: List<NumberResult>, onCopy: (NumberResult) -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.number_history),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.number_clear_history)) }
        }
        results.forEach { result ->
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        result.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 3,
                    )
                    Text(
                        rangeCaption(result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onCopy(result) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.number_copy))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NumberContentPreview() {
    DiceFamiliarTheme {
        NumberContent(
            state = NumberUiState(
                count = "5",
                latest = NumberResult("1", listOf(7, 42, 13, 99, 1), 0, 1, 100, false, 0),
            ),
            onMinChange = {},
            onMaxChange = {},
            onCountChange = {},
            onAllowDuplicatesChange = {},
            onModeChange = {},
            onDecimalsChange = {},
            onGenerate = {},
        )
    }
}

/** The number generator's "?" sheet. */
internal val NUMBER_HELP = listOf(
    HelpTopic(R.string.help_number_range_title, R.string.help_number_range_text),
    HelpTopic(R.string.help_number_count_title, R.string.help_number_count_text),
    HelpTopic(R.string.help_number_mode_title, R.string.help_number_mode_text),
    HelpTopic(R.string.help_number_duplicates_title, R.string.help_number_duplicates_text),
    HelpTopic(R.string.help_number_results_title, R.string.help_number_results_text),
    HelpTopic(R.string.help_number_memory_title, R.string.help_number_memory_text),
)
