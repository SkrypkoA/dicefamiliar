package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.animation.AnimatedVisibility
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.dice.RollMode

/** Collapsible panel: dice palette, optional formula field, modifier controls and roll modes. */
@Composable
fun DicePanel(
    state: DiceUiState,
    onNotationChange: (String) -> Unit,
    onDieTapped: (Int) -> Unit,
    onDieLongPressed: (Int) -> Unit,
    onModifier: (Int) -> Unit,
    onClear: () -> Unit,
    onModeChange: (RollMode) -> Unit,
    onRoll: (Float) -> Unit,
    onToggleFormula: () -> Unit,
    shake: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DicePalette(
                counts = state.paletteCounts,
                customSides = state.customSides,
                onAdd = onDieTapped,
                onRemove = onDieLongPressed,
            )
            // With the ƒx toggle off the formula is not composed at all (nothing faded or disabled).
            if (state.formulaVisible) {
                OutlinedTextField(
                    value = state.notation,
                    onValueChange = onNotationChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dice_formula_label)) },
                    placeholder = { Text(stringResource(R.string.dice_formula_placeholder)) },
                    isError = state.notationError != null,
                    supportingText = {
                        Text(
                            state.notationError?.let { stringResource(it.messageRes()) }
                                ?: stringResource(R.string.dice_formula_hint),
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (state.canRoll) onRoll(0f) }),
                )
            }
            // ƒx at the start, −1/+1 exactly centred in the panel, Clear at the end.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(shake.dp.roundToPx(), 0) },
            ) {
                IconToggleButton(
                    checked = state.formulaVisible,
                    onCheckedChange = { onToggleFormula() },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        FormulaIcon,
                        contentDescription = stringResource(
                            if (state.formulaVisible) R.string.dice_formula_hide else R.string.dice_formula_show,
                        ),
                        tint = if (state.formulaVisible) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModifierButton(R.string.dice_modifier_minus) { onModifier(-1) }
                    ModifierButton(R.string.dice_modifier_plus) { onModifier(1) }
                }
                FilledTonalButton(
                    onClick = onClear,
                    enabled = state.notation.isNotEmpty(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .height(CONTROL_HEIGHT)
                        .widthIn(min = CONTROL_MIN_WIDTH),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(EraserIcon, contentDescription = stringResource(R.string.dice_clear), modifier = Modifier.size(26.dp))
                }
            }
            if (state.settings.animationsEnabled) {
                AnimatedVisibility(visible = state.modeApplicable) { ModeSelector(state.mode, onModeChange) }
            } else if (state.modeApplicable) {
                ModeSelector(state.mode, onModeChange)
            }
        }
    }
}

/** Large "+1" / "−1" modifier button. */
@Composable
private fun ModifierButton(@StringRes label: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(CONTROL_HEIGHT).widthIn(min = CONTROL_MIN_WIDTH),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.titleLarge)
    }
}

private val CONTROL_HEIGHT = 52.dp
private val CONTROL_MIN_WIDTH = 76.dp
