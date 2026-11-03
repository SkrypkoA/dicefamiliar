package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.dice.NotationError
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.ui.dice.skin.LocalDiceSkin
import com.skrypko.dicefamiliar.ui.common.HelpButton
import com.skrypko.dicefamiliar.ui.common.HelpSheet
import com.skrypko.dicefamiliar.ui.common.HelpTopic
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import com.skrypko.dicefamiliar.ui.theme.LocalThemeStyle
import kotlinx.coroutines.launch

@Composable
fun DiceScreen(viewModel: DiceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val settings = viewModel.uiState.value.settings
            when (event) {
                DiceEvent.RollShown ->
                    if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                DiceEvent.DieRemoved ->
                    if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                DiceEvent.InvalidRoll -> {
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

    DiceContent(
        state = uiState,
        fieldShake = shake.value,
        onNotationChange = viewModel::onNotationChange,
        onDieTapped = viewModel::onDieTapped,
        onDieLongPressed = viewModel::onDieLongPressed,
        onModifier = viewModel::onModifier,
        onClear = viewModel::onClear,
        onModeChange = viewModel::onModeChange,
        onRoll = viewModel::roll,
        onToggleFormula = viewModel::toggleFormula,
        onTogglePanel = viewModel::togglePanel,
    )
}

/**
 * Dice screen layout: a collapsible panel (palette, formula, controls, modes) on top, then the
 * dice tray taking all remaining height, then the sum bar (and the Roll button in Quick roll).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiceContent(
    state: DiceUiState,
    onNotationChange: (String) -> Unit,
    onDieTapped: (Int) -> Unit,
    onDieLongPressed: (Int) -> Unit,
    onModifier: (Int) -> Unit,
    onClear: () -> Unit,
    onModeChange: (RollMode) -> Unit,
    onRoll: (Float) -> Unit,
    modifier: Modifier = Modifier,
    fieldShake: Float = 0f,
    onToggleFormula: () -> Unit = {},
    onTogglePanel: () -> Unit = {},
) {
    val quickRoll = state.settings.quickRoll
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    CompositionLocalProvider(LocalDiceSkin provides state.skin) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val panel: @Composable () -> Unit = {
                DicePanel(
                    state = state,
                    onNotationChange = onNotationChange,
                    onDieTapped = onDieTapped,
                    onDieLongPressed = onDieLongPressed,
                    onModifier = onModifier,
                    onClear = onClear,
                    onModeChange = onModeChange,
                    onRoll = onRoll,
                    onToggleFormula = onToggleFormula,
                    shake = fieldShake,
                    modifier = Modifier.heightIn(max = maxPanelHeight),
                )
            }
            if (state.settings.animationsEnabled) {
                AnimatedVisibility(
                    visible = state.panelVisible,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) { panel() }
            } else if (state.panelVisible) {
                panel()
            }
            PanelToggle(visible = state.panelVisible, onToggle = onTogglePanel)
            DiceTray(
                state = state,
                swipeToRoll = !quickRoll,
                onSwipeRoll = onRoll,
                modifier = Modifier.weight(1f),
            )
            RollSumBar(
                state = state,
                onShowHistory = if (state.recent.size > 1) ({ showHistory = true }) else null,
            )
            if (quickRoll) {
                Button(
                    onClick = { onRoll(0f) },
                    enabled = state.canRoll,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(LocalThemeStyle.current.icons.roll, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dice_roll), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (showHistory) {
            ModalBottomSheet(onDismissRequest = { showHistory = false }) {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    RecentRolls(state.recent.drop(1))
                }
            }
        }
    }
}

/** Chevron handle that collapses/expands the dice panel, with the screen's "?" help on the right. */
@Composable
private fun PanelToggle(visible: Boolean, onToggle: () -> Unit) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HelpButton(
            contentDescription = stringResource(R.string.help_dice_button),
            onClick = { showHelp = true },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        IconButton(onClick = onToggle, modifier = Modifier.size(width = 72.dp, height = 40.dp)) {
            Icon(
                if (visible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(if (visible) R.string.dice_panel_hide else R.string.dice_panel_show),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
    }
    if (showHelp) HelpSheet(R.string.help_dice_title, DICE_HELP, onDismiss = { showHelp = false })
}

@Composable
internal fun ModeSelector(mode: RollMode, onModeChange: (RollMode) -> Unit) {
    val modes = RollMode.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, item ->
            SegmentedButton(
                selected = item == mode,
                onClick = { onModeChange(item) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                label = {
                    Text(
                        stringResource(
                            when (item) {
                                RollMode.NORMAL -> R.string.dice_mode_normal
                                RollMode.ADVANTAGE -> R.string.dice_mode_advantage
                                RollMode.DISADVANTAGE -> R.string.dice_mode_disadvantage
                            },
                        ),
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
internal fun RecentRolls(records: List<RollRecord>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.dice_recent), style = MaterialTheme.typography.titleSmall)
        records.forEach { record ->
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(record.notation, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                    Text(
                        record.outcome.groups.joinToString("  ") { group ->
                            group.dice.joinToString(" ", "[", "]") { if (it.kept) "${it.value}" else "(${it.value})" }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Text(
                    record.outcome.total.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

internal fun NotationError.messageRes(): Int = when (this) {
    NotationError.EMPTY -> R.string.dice_error_empty
    NotationError.TOO_LONG -> R.string.dice_error_too_long
    NotationError.UNEXPECTED_CHAR -> R.string.dice_error_unexpected_char
    NotationError.INCOMPLETE -> R.string.dice_error_incomplete
    NotationError.MISSING_SIDES -> R.string.dice_error_missing_sides
    NotationError.COUNT_OUT_OF_RANGE -> R.string.dice_error_count
    NotationError.SIDES_OUT_OF_RANGE -> R.string.dice_error_sides
    NotationError.KEEP_OUT_OF_RANGE -> R.string.dice_error_keep
    NotationError.CONSTANT_OUT_OF_RANGE -> R.string.dice_error_constant
    NotationError.TOO_MANY_TERMS -> R.string.dice_error_terms
    NotationError.TOO_MANY_DICE -> R.string.dice_error_total_dice
}

@Preview(showBackground = true)
@Composable
private fun DiceContentPreview() {
    DiceFamiliarTheme {
        DiceContent(
            state = DiceUiState(notation = "2d6+1d4+1", paletteCounts = mapOf(6 to 2, 4 to 1)),
            onNotationChange = {},
            onDieTapped = {},
            onDieLongPressed = {},
            onModifier = {},
            onClear = {},
            onModeChange = {},
            onRoll = {},
        )
    }
}

/** The dice roller's "?" sheet, in the order a new user needs it. */
internal val DICE_HELP = listOf(
    HelpTopic(R.string.help_dice_add_title, R.string.help_dice_add_text),
    HelpTopic(R.string.help_dice_remove_title, R.string.help_dice_remove_text),
    HelpTopic(R.string.help_dice_custom_title, R.string.help_dice_custom_text),
    HelpTopic(R.string.help_dice_modifier_title, R.string.help_dice_modifier_text),
    HelpTopic(R.string.help_dice_formula_title, R.string.help_dice_formula_text),
    HelpTopic(R.string.help_dice_modes_title, R.string.help_dice_modes_text),
    HelpTopic(R.string.help_dice_roll_title, R.string.help_dice_roll_text),
    HelpTopic(R.string.help_dice_results_title, R.string.help_dice_results_text),
    HelpTopic(R.string.help_dice_panel_title, R.string.help_dice_panel_text),
)
