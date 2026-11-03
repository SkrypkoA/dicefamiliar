package com.skrypko.dicefamiliar.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/** Where the increase / decrease chevrons sit relative to the text field. */
enum class StepperArrows {
    /** Up chevron above the field, down chevron below it. */
    ABOVE_BELOW,

    /** A compact up/down pair after (to the right of) the field. */
    AFTER,

    /** A compact up/down pair before (to the left of) the field. */
    BEFORE,
}

/**
 * A number text field with increase / decrease steppers, shared by every form.
 *
 * - Tap the field to type a value.
 * - Swipe up / down anywhere on the component (field and chevrons) to step: every [swipeStep] of
 *   finger travel calls `onStep(+1)` (up) or `onStep(-1)` (down).
 * - Tap a chevron to step once.
 *
 * The component is stateless: the caller owns the text and decides what one step means (clamping,
 * step size), so the same field serves integers, decimals, counts and dice sides.
 */
@Composable
fun StepperNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    increaseDescription: String,
    decreaseDescription: String,
    modifier: Modifier = Modifier,
    arrows: StepperArrows = StepperArrows.ABOVE_BELOW,
    label: String? = null,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
    textStyle: TextStyle = LocalTextStyle.current,
    canIncrease: Boolean = true,
    canDecrease: Boolean = true,
    swipeStep: Dp = DEFAULT_SWIPE_STEP,
    fieldModifier: Modifier = Modifier,
) {
    val stepPx = with(LocalDensity.current) { swipeStep.toPx() }
    val field: @Composable (Modifier) -> Unit = { fieldLayout ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldLayout.then(fieldModifier),
            textStyle = textStyle,
            label = label?.let { { Text(it) } },
            isError = isError,
            supportingText = supportingText,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
    val up: @Composable (Modifier) -> Unit = { buttonModifier ->
        StepButton(Icons.Filled.KeyboardArrowUp, increaseDescription, canIncrease, buttonModifier) { onStep(1) }
    }
    val down: @Composable (Modifier) -> Unit = { buttonModifier ->
        StepButton(Icons.Filled.KeyboardArrowDown, decreaseDescription, canDecrease, buttonModifier) { onStep(-1) }
    }
    val latestOnStep by rememberUpdatedState(onStep)
    val swipe = modifier.verticalSteps(stepPx) { latestOnStep(it) }
    when (arrows) {
        StepperArrows.ABOVE_BELOW -> Column(swipe, horizontalAlignment = Alignment.CenterHorizontally) {
            up(Modifier)
            field(Modifier.fillMaxWidth())
            down(Modifier)
        }
        StepperArrows.AFTER, StepperArrows.BEFORE -> Row(swipe, verticalAlignment = Alignment.Top) {
            val pair: @Composable () -> Unit = {
                // Same height as the outlined field's box (56 dp, below its 8 dp label inset).
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.Center) {
                    up(Modifier.size(width = SIDE_BUTTON_WIDTH, height = SIDE_BUTTON_HEIGHT))
                    down(Modifier.size(width = SIDE_BUTTON_WIDTH, height = SIDE_BUTTON_HEIGHT))
                }
            }
            if (arrows == StepperArrows.BEFORE) pair()
            field(Modifier.weight(1f))
            if (arrows == StepperArrows.AFTER) pair()
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Keep [onStep] stable (or read state through `rememberUpdatedState`): the gesture is not restarted when it changes.
 *
 * Vertical swipe stepping: every [stepPx] of finger travel up calls `onStep(+1)`, down `onStep(-1)`.
 * Events are read in the Initial pass so the gesture wins over the text field's own cursor/selection
 * drags (e.g. the cursor handle shown under a focused field). Until the finger moves past touch slop
 * nothing is consumed, so a tap still reaches the field and opens the keyboard.
 */
fun Modifier.verticalSteps(stepPx: Float, onStep: (Int) -> Unit): Modifier = pointerInput(stepPx) {
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

val DEFAULT_SWIPE_STEP = 24.dp
private val SIDE_BUTTON_WIDTH = 40.dp
private val SIDE_BUTTON_HEIGHT = 28.dp
