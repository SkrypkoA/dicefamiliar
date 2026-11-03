package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import kotlin.math.ceil
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.Color
import com.skrypko.dicefamiliar.ui.theme.themeDiceColors
import com.skrypko.dicefamiliar.ui.theme.tray.themedTray
import com.skrypko.dicefamiliar.ui.theme.tray.trayColors
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.dice.pool.DicePool
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import com.skrypko.dicefamiliar.ui.dice.render3d.Dice3DCanvas
import com.skrypko.dicefamiliar.ui.dice.render3d.Die3D
import com.skrypko.dicefamiliar.ui.dice.render3d.Percentile
import com.skrypko.dicefamiliar.ui.dice.skin.DieColors
import kotlin.math.hypot

private const val MAX_3D_DICE = 12

/** Pool mode tumbles up to this many dice in 3D (smaller); bigger pools spin in 2D. */
private const val MAX_3D_POOL_DICE = 30
private val TRAY_MIN_HEIGHT = 160.dp
private const val MIN_SWIPE_SPEED = 600f

/**
 * The roll area. With [swipeToRoll] the user swipes across it to roll; the tumble animation
 * and the result are shown inside it.
 */
@Composable
fun DiceTray(
    state: DiceUiState,
    swipeToRoll: Boolean,
    onSwipeRoll: (speed: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animations = state.settings.animationsEnabled
    var direction by remember { mutableStateOf(Offset(1f, 0f)) }
    val minDistance = with(LocalDensity.current) { 48.dp.toPx() }
    val rollLabel = stringResource(R.string.dice_roll)
    val gestureModifier = if (swipeToRoll) {
        Modifier
            .pointerInput(minDistance) {
                val tracker = VelocityTracker()
                var drag = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        drag = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount
                        tracker.addPosition(change.uptimeMillis, change.position)
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity()
                        val speed = hypot(velocity.x, velocity.y)
                        if (drag.getDistance() >= minDistance || speed >= MIN_SWIPE_SPEED) {
                            val length = drag.getDistance()
                            direction = if (length > 0f) drag / length else Offset(1f, 0f)
                            onSwipeRoll(speed)
                        }
                    },
                )
            }
            .semantics {
                onClick(label = rollLabel) {
                    onSwipeRoll(0f)
                    true
                }
                customActions = listOf(CustomAccessibilityAction(rollLabel) { onSwipeRoll(0f); true })
            }
    } else {
        Modifier
    }

    val trayShape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TRAY_MIN_HEIGHT)
            .themedTray(trayShape)
            .then(gestureModifier),
        shape = trayShape,
        // The theme's tray painter draws the background (plain surface, felt, neon grid, ...).
        color = Color.Transparent,
        contentColor = trayColors().content,
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            val shown = state.pending ?: state.latest
            val pool = state.pool
            when {
                // Up to 12 dice stay 3D after the roll (Quick roll off); bigger pools and Quick roll use the flat grid.
                state.poolMode && state.pending == null && pool != null && !state.settings.quickRoll && pool.size <= MAX_3D_DICE ->
                    PoolSolidDice(state, pool)
                state.poolMode && state.pending == null && pool != null ->
                    PoolGrid(pool, state.selectedCriterion, animations, state.rollCount)
                state.poolMode && state.pending == null && swipeToRoll -> SwipeHint(animations)
                state.poolMode && state.pending == null -> Text(
                    stringResource(R.string.dice_tray_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = trayColors().muted,
                )
                state.poolMode && shown != null && (state.settings.quickRoll || shown.outcome.expression.diceCount > MAX_3D_POOL_DICE) ->
                    FlatDice(shown, rolling = true, animations, state.rollCount, minChip = POOL_MIN_DIE)
                state.poolMode && shown != null -> SolidDice(state, shown, direction, maxDice = MAX_3D_POOL_DICE)
                shown == null && swipeToRoll -> SwipeHint(animations)
                shown == null -> Text(
                    stringResource(R.string.dice_tray_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = trayColors().muted,
                )
                state.settings.quickRoll -> FlatDice(shown, rolling = state.pending != null, animations, state.rollCount)
                else -> SolidDice(state, shown, direction)
            }
        }
    }
}

/** Swipe mode: shaded 3D dice that tumble in the swipe direction and land on the rolled faces. */
@Composable
private fun SolidDice(state: DiceUiState, record: RollRecord, direction: Offset, maxDice: Int = MAX_3D_DICE) {
    val rolling = state.pending != null
    val progress = remember(state.rollCount, rolling) { Animatable(if (rolling) 0f else 1f) }
    if (rolling) {
        LaunchedEffect(state.rollCount) {
            progress.animateTo(1f, tween(state.tumbleMillis, easing = LinearEasing))
        }
    }
    val slots = record.slots()
    val shown = slots.take(maxDice)
    val diceDefaults = themeDiceColors()
    val skin = state.skin
    val resources = LocalContext.current.resources
    val description = if (rolling) {
        stringResource(R.string.dice_rolling)
    } else {
        record.outcome.groups.flatMap { group -> group.dice.map { group.term.sides to it } }
            .joinToString(", ") { (sides, die) ->
                resources.getString(
                    if (die.kept) R.string.dice_result_kept else R.string.dice_result_dropped,
                    sides,
                    die.value,
                )
            }
    }
    Box(Modifier.fillMaxSize()) {
        Dice3DCanvas(
            slots = shown,
            progress = progress.value,
            directionX = direction.x,
            directionY = direction.y,
            durationMs = state.tumbleMillis,
            skin = skin,
            colorsFor = { sides ->
                val die = skin.forSides(sides)
                DieColors(
                    fill = die.fill ?: diceDefaults.fill,
                    edge = die.edge ?: diceDefaults.edge,
                    text = die.text ?: diceDefaults.text,
                )
            },
            description = description,
            modifier = Modifier.fillMaxSize(),
        )
        if (slots.size > shown.size) {
            Text(
                stringResource(R.string.dice_more, slots.size - shown.size),
                modifier = Modifier.align(Alignment.BottomEnd),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * A rolled pool of up to [MAX_3D_DICE] dice, at rest in 3D. With a group selected the dice are sorted for it,
 * the group's dice sit on a tint and the others are dimmed.
 */
@Composable
private fun PoolSolidDice(state: DiceUiState, pool: DicePool) {
    val criterion = state.selectedCriterion
    val dice = criterion?.let { pool.ordered(it) } ?: pool.dice
    val diceDefaults = themeDiceColors()
    val skin = state.skin
    val resources = LocalResources.current
    val description = dice.joinToString(", ") { resources.getString(R.string.dice_result_kept, pool.sides, it.value) }
    Dice3DCanvas(
        slots = dice.map { Die3D.of(pool.sides, it.value, kept = criterion == null || criterion.matches(it.value)) },
        progress = 1f,
        directionX = 1f,
        directionY = 0f,
        durationMs = 0,
        skin = skin,
        colorsFor = { sides ->
            val die = skin.forSides(sides)
            DieColors(fill = die.fill ?: diceDefaults.fill, edge = die.edge ?: diceDefaults.edge, text = die.text ?: diceDefaults.text)
        },
        description = description,
        modifier = Modifier.fillMaxSize().testTag(POOL_SOLID_TAG),
        highlighted = criterion?.let { c -> dice.indices.filter { c.matches(dice[it].value) }.toSet() }.orEmpty(),
        highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
    )
}

const val POOL_SOLID_TAG = "dice_pool_solid"

/** Quick-roll mode: flat 2D silhouettes ("?" while rolling, values once shown). */
@Composable
private fun FlatDice(record: RollRecord, rolling: Boolean, animated: Boolean, key: Int, minChip: Dp = 44.dp) {
    val dice = record.outcome.groups.flatMap { group ->
        group.dice.map { Die3D(group.term.sides, it.value, it.kept) }
    }
    // Chips grow with the tray: as large as fits a near-square grid, between 44 and 112 dp.
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cols = ceil(sqrt(dice.size * (maxWidth / maxHeight))).toInt().coerceIn(1, dice.size.coerceAtLeast(1))
        val rows = ceil(dice.size / cols.toFloat()).toInt().coerceAtLeast(1)
        val cell = minOf(maxWidth / cols, maxHeight / rows)
        val chip = (cell * 0.72f).coerceIn(minChip, 112.dp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(chip * 0.2f, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(chip * 0.2f, Alignment.CenterVertically),
            modifier = Modifier.fillMaxWidth(),
        ) {
            dice.forEachIndexed { index, die ->
                if (rolling) {
                    RollingFlatDie(die.sides, index, key, chip)
                } else {
                    ResultDie(die.sides, die.value, die.kept, animated, delayIndex = index, key = key, size = chip)
                }
            }
        }
    }
}

@Composable
private fun RollingFlatDie(sides: Int, index: Int, key: Int, size: Dp) {
    val spin = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        spin.animateTo(
            if (index % 2 == 0) 720f else -720f,
            infiniteRepeatable(tween(700, easing = LinearEasing)),
        )
    }
    DiceIcon(
        sides = sides,
        text = "?",
        size = size,
        pairLabels = if (sides == 100) "?" to "?" else null,
        modifier = Modifier.rotate(spin.value),
    )
}

/** One slot per rolled die; a d100 slot holds its tens and units d10s. */
private fun RollRecord.slots(): List<List<Die3D>> = outcome.groups.flatMap { group ->
    group.dice.map { Die3D.of(group.term.sides, it.value, it.kept) }
}

/** Compact "notation = total" line shown under the tray. */
@Composable
fun RollSumBar(state: DiceUiState, modifier: Modifier = Modifier) {
    if (state.poolMode) {
        PoolBar(state, modifier)
        return
    }
    val record = state.pending ?: state.latest ?: return
    val rolling = state.pending != null
    val total = record.outcome.total
    val animatedTotal by animateIntAsState(
        targetValue = total,
        animationSpec = if (state.settings.animationsEnabled) tween(400) else snap(),
        label = "sum",
    )
    val shownTotal = if (rolling) "…" else animatedTotal.toString()
    val text = listOfNotNull(record.label, "${record.notation} = $shownTotal").joinToString(" · ")
    val description = if (rolling) stringResource(R.string.dice_rolling) else "${record.notation} = $total"
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .semantics { contentDescription = description },
            )
        }
    }
}

/** Pool mode: "10d6", or "10d6 · 4+ → 3 successes" with a group selected. */
@Composable
private fun PoolBar(state: DiceUiState, modifier: Modifier) {
    val pool = state.pool ?: return
    val text = if (state.pending != null) stringResource(R.string.dice_rolling) else poolSummary(pool, state.selectedCriterion)
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag(POOL_BAR_TAG),
        )
    }
}

const val POOL_BAR_TAG = "dice_pool_bar"

@Composable
private fun SwipeHint(animated: Boolean) {
    val shift = if (animated) {
        val transition = rememberInfiniteTransition(label = "hint")
        transition.animateFloat(
            initialValue = -12f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "hintShift",
        ).value
    } else {
        0f
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = trayColors().accent,
            modifier = Modifier.offset { IntOffset(shift.dp.roundToPx(), 0) },
        )
        Text(
            stringResource(R.string.dice_swipe_hint),
            style = MaterialTheme.typography.titleMedium,
            color = trayColors().muted,
        )
    }
}

@Composable
internal fun ResultDie(sides: Int, value: Int, kept: Boolean, animated: Boolean, delayIndex: Int, key: Int, size: Dp) {
    val appear = remember(key, delayIndex) { Animatable(if (animated) 0f else 1f) }
    if (animated) {
        LaunchedEffect(key, delayIndex) {
            appear.animateTo(1f, tween(220, delayMillis = (delayIndex * 40).coerceAtMost(400)))
        }
    }
    val description = stringResource(
        if (kept) R.string.dice_result_kept else R.string.dice_result_dropped,
        sides,
        value,
    )
    DiceIcon(
        sides = sides,
        text = if (sides == 10) (value % 10).toString() else value.toString(),
        pairLabels = if (sides == 100) Percentile.labels(value) else null,
        size = size,
        strikeThrough = !kept,
        modifier = Modifier
            .scale(appear.value)
            .alpha(if (kept) appear.value else appear.value * 0.38f)
            .semantics { contentDescription = description },
    )
}

/** The roll as text, e.g. "2d6+1d4+3 = 13 · [3 5] [2]" (dropped dice in parentheses). */
fun RollRecord.shareText(): String {
    val dice = outcome.groups.joinToString(" ") { group ->
        group.dice.joinToString(" ", "[", "]") { if (it.kept) "${it.value}" else "(${it.value})" }
    }
    return listOfNotNull(label, "$notation = ${outcome.total}", dice).joinToString(" · ")
}

/** The picture shared by "Share as image": the formula, each die (in the active skin) and the total, on the tray. */
@Composable
internal fun RollSnapshot(record: RollRecord) {
    val shape = MaterialTheme.shapes.extraLarge
    val inset = com.skrypko.dicefamiliar.ui.theme.LocalThemeStyle.current.tray.contentInset
    val colors = trayColors()
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(record.notation, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Column(
            Modifier.fillMaxWidth().themedTray(shape).padding(20.dp + inset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                record.outcome.groups.forEach { group ->
                    group.dice.forEachIndexed { index, die ->
                        ResultDie(group.term.sides, die.value, die.kept, animated = false, delayIndex = index, key = 0, size = 72.dp)
                    }
                }
            }
            Text(
                record.outcome.total.toString(),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.accent,
            )
        }
    }
}
