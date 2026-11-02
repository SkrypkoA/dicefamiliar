package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import kotlin.math.sign
import kotlin.math.abs
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.material3.IconButton
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.dice.DiceLimits

/** Tap a die to add it to the formula, long-press to remove one. */
@Composable
fun DicePalette(
    counts: Map<Int, Int>,
    customSides: Int?,
    onAdd: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DiceLimits.STANDARD_DICE.forEach { sides ->
            PaletteDie(
                sides = sides,
                label = stringResource(R.string.dice_name, sides),
                count = counts[sides] ?: 0,
                onClick = { onAdd(sides) },
                onLongClick = { onRemove(sides) },
            )
        }
        // dN: the first tap asks for the sides; afterwards it keeps adding that same die
        // (shown as e.g. "d7") until all custom dice are removed or the formula is cleared.
        val customLabel = customSides?.let { stringResource(R.string.dice_name, it) }
        PaletteDie(
            sides = DiceViewModel.CUSTOM_DIE,
            label = customLabel ?: stringResource(R.string.dice_custom_label),
            count = counts[DiceViewModel.CUSTOM_DIE] ?: 0,
            onClick = { if (customSides != null) onAdd(customSides) else showCustom = true },
            onLongClick = { onRemove(DiceViewModel.CUSTOM_DIE) },
            description = customLabel?.let { stringResource(R.string.dice_add, it) }
                ?: stringResource(R.string.dice_custom_add),
        )
    }
    if (showCustom) {
        CustomDieDialog(
            onDismiss = { showCustom = false },
            onConfirm = { sides ->
                showCustom = false
                onAdd(sides)
            },
        )
    }
}

@Composable
private fun PaletteDie(
    sides: Int,
    label: String,
    count: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    description: String = stringResource(R.string.dice_add, label),
) {
    val countDescription = if (count > 0) {
        pluralStringResource(R.plurals.dice_in_formula, count, count, label)
    } else {
        null
    }
    BadgedBox(
        badge = { if (count > 0) Badge { Text(stringResource(R.string.dice_badge, count)) } },
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(description, countDescription).joinToString(", ")
        },
    ) {
        DiceIcon(
            sides = sides,
            text = label,
            modifier = Modifier.combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClick?.let { stringResource(R.string.dice_remove, label) },
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDieDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val sides = text.toIntOrNull()
    val valid = sides != null && sides in DiceLimits.MIN_SIDES..DiceLimits.MAX_SIDES
    val step: (Int) -> Unit = { delta -> text = DiceLimits.stepSides(text.toIntOrNull(), delta).toString() }
    val increase = stringResource(R.string.dice_custom_increase)
    val decrease = stringResource(R.string.dice_custom_decrease)
    val stepPx = with(LocalDensity.current) { SWIPE_STEP.toPx() }
    // A basic dialog (not AlertDialog) so the actions can be real buttons pushed to the corners.
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
            // Tap the field to type the value; swipe up / down anywhere on this area (field and
            // chevrons) to increase / decrease it. The chevrons can also be tapped.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalSteps(stepPx, step),
            ) {
                IconButton(onClick = { step(1) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = increase,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(4) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CUSTOM_SIDES_FIELD),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                    label = { Text(stringResource(R.string.dice_custom_sides)) },
                    isError = text.isNotEmpty() && !valid,
                    supportingText = {
                        Text(
                            stringResource(R.string.dice_custom_range, DiceLimits.MIN_SIDES, DiceLimits.MAX_SIDES),
                            color = if (text.isNotEmpty() && !valid) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                IconButton(onClick = { step(-1) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = decrease,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Secondary action: white, outlined, in a different colour from "Add".
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = DIALOG_BUTTON_SIZE,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = { sides?.let(onConfirm) }, enabled = valid, modifier = DIALOG_BUTTON_SIZE) {
                    Text(stringResource(R.string.dice_custom_confirm))
                }
            }
        }
        }
    }
}

/**
 * Vertical swipe stepping: every [stepPx] of finger travel up calls `onStep(+1)`, down `onStep(-1)`.
 * Events are read in the Initial pass so the gesture wins over the text field's own cursor/selection
 * drags (e.g. the cursor handle shown under a focused field). Until the finger moves past touch slop
 * nothing is consumed, so a tap still reaches the field and opens the keyboard.
 */
private fun Modifier.verticalSteps(stepPx: Float, onStep: (Int) -> Unit): Modifier = pointerInput(stepPx) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var lastY = down.position.y
        var travelled = 0f
        var dragging = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            travelled += change.position.y - lastY
            lastY = change.position.y
            if (!dragging && abs(travelled) > slop) {
                dragging = true
                travelled -= sign(travelled) * slop
            }
            if (dragging) {
                change.consume()
                while (travelled <= -stepPx) {
                    onStep(1)
                    travelled += stepPx
                }
                while (travelled >= stepPx) {
                    onStep(-1)
                    travelled -= stepPx
                }
            }
        }
    }
}

/** Shared size so Cancel and Add are identical buttons. */
private val DIALOG_BUTTON_SIZE = Modifier.height(48.dp).widthIn(min = 120.dp)

/** Finger travel per ±1 step when swiping the custom-sides field. */
private val SWIPE_STEP = 24.dp

/** Test tag of the custom die's number field. */
internal const val CUSTOM_SIDES_FIELD = "customSidesField"
