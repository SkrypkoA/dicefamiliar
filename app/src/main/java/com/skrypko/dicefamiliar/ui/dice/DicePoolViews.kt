package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.dice.pool.Comparison
import com.skrypko.dicefamiliar.domain.dice.pool.DicePool
import com.skrypko.dicefamiliar.domain.dice.pool.PoolDie
import com.skrypko.dicefamiliar.domain.dice.pool.PoolGroup
import com.skrypko.dicefamiliar.domain.dice.pool.SuccessCriterion
import com.skrypko.dicefamiliar.ui.common.StepperArrows
import com.skrypko.dicefamiliar.ui.common.StepperNumberField
import com.skrypko.dicefamiliar.ui.theme.LocalThemeStyle
import com.skrypko.dicefamiliar.ui.theme.tray.themedTray
import kotlin.math.ceil
import kotlin.math.sqrt

const val POOL_GRID_TAG = "dice_pool_grid"
const val POOL_GROUP_CHIP_TAG = "dice_pool_group_"
const val POOL_GROUP_ADD_TAG = "dice_pool_group_add"
const val POOL_GROUP_DIE_TAG = "dice_pool_group_die"
const val POOL_REROLL_TAG = "dice_pool_reroll"
const val POOL_ROLL_GROUP_TAG = "dice_pool_roll_group"

/** Up to this many dice, pool dice are as large as the normal results; larger pools use smaller dice. */
internal const val POOL_LARGE_DICE = 12

/** Smallest pool die, for big pools (more than [POOL_LARGE_DICE]). */
internal val POOL_MIN_DIE = 28.dp

/** Die size for [count] dice in a [width] × [height] area: as large as a near-square grid allows. */
internal fun poolDieSize(count: Int, width: Dp, height: Dp): Pair<Int, Dp> {
    val n = count.coerceAtLeast(1)
    val cols = ceil(sqrt(n * (width / height))).toInt().coerceIn(1, n)
    val rows = ceil(n / cols.toFloat()).toInt().coerceAtLeast(1)
    val cell = minOf(width / cols, height / rows)
    val min = if (count > POOL_LARGE_DICE) POOL_MIN_DIE else 44.dp
    return cols to (cell * 0.78f).coerceIn(min, 112.dp)
}

/**
 * The rolled pool as a flat grid. With a group selected the dice are sorted for it (descending for `N+` and
 * `=N`, ascending for `N−`) and the group's dice sit on a shared tint, so they read as one group; the others
 * are dimmed. Dice slide to their new places when the order changes.
 */
@Composable
fun PoolGrid(pool: DicePool, criterion: SuccessCriterion?, animated: Boolean, key: Int, modifier: Modifier = Modifier) {
    val dice = criterion?.let { pool.ordered(it) } ?: pool.dice
    BoxWithConstraints(modifier.fillMaxSize().testTag(POOL_GRID_TAG), contentAlignment = Alignment.Center) {
        val (cols, die) = poolDieSize(dice.size, maxWidth, maxHeight)
        val cell = die / 0.78f
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.width(cell * cols),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center,
            userScrollEnabled = true,
        ) {
            items(dice, key = { it.id }) { poolDie ->
                val inGroup = criterion?.matches(poolDie.value) == true
                PoolDieCell(
                    pool.sides,
                    poolDie,
                    inGroup = inGroup,
                    dimmed = criterion != null && !inGroup,
                    size = die,
                    cell = cell,
                    animated = animated,
                    key = key,
                    modifier = if (animated) Modifier.animateItem() else Modifier,
                )
            }
        }
    }
}

@Composable
private fun PoolDieCell(
    sides: Int,
    die: PoolDie,
    inGroup: Boolean,
    dimmed: Boolean,
    size: Dp,
    cell: Dp,
    animated: Boolean,
    key: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(cell)
            .then(
                if (inGroup) {
                    Modifier.padding(cell * 0.03f).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(cell * 0.22f)).testTag(POOL_GROUP_DIE_TAG)
                } else {
                    Modifier
                },
            )
            .alpha(if (dimmed) 0.5f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        ResultDie(sides, die.value, kept = true, animated = animated, delayIndex = die.id, key = key, size = size)
    }
}

/**
 * Pool groups under the tray: one chip per group ("5+ · 5": the test and how many dice pass it; tap to select,
 * long-press to delete after a confirmation), the add button, and — with a group selected — Reroll and Roll group.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PoolGroupBar(
    state: DiceUiState,
    onToggle: (Int) -> Unit,
    onAdd: (SuccessCriterion) -> Unit,
    onRemove: (Int) -> Unit,
    onReroll: () -> Unit,
    onRollGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sides = state.pool?.sides ?: com.skrypko.dicefamiliar.domain.dice.pool.PoolNotation.parse(state.notation)?.second ?: 6
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PoolGroup?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.poolGroups.groups, key = { it.id }) { group ->
                    GroupChip(
                        group = group,
                        count = state.pool?.successes(group.criterion),
                        selected = group.id == state.poolGroups.selectedId,
                        onClick = { onToggle(group.id) },
                        onLongClick = { deleting = group },
                    )
                }
            }
            FilledTonalIconButton(onClick = { adding = true }, modifier = Modifier.testTag(POOL_GROUP_ADD_TAG)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.dice_pool_add_group))
            }
        }
        if (state.selectedCriterion != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onReroll,
                    enabled = state.canActOnGroup,
                    modifier = Modifier.weight(1f).testTag(POOL_REROLL_TAG),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dice_pool_reroll, state.selectedCount), maxLines = 1)
                }
                Button(
                    onClick = onRollGroup,
                    enabled = state.canActOnGroup,
                    modifier = Modifier.weight(1f).testTag(POOL_ROLL_GROUP_TAG),
                ) {
                    Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dice_pool_roll_group, state.selectedCount), maxLines = 1)
                }
            }
        }
    }
    if (adding) {
        CriterionDialog(
            initial = SuccessCriterion((sides + 1) / 2 + 1, Comparison.AT_LEAST).clampTo(sides),
            sides = sides,
            onSave = {
                adding = false
                onAdd(it)
            },
            onDismiss = { adding = false },
        )
    }
    deleting?.let { group ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.dice_pool_delete_title)) },
            text = { Text(stringResource(R.string.dice_pool_delete_text, group.criterion.label)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onRemove(group.id)
                }) { Text(stringResource(R.string.dice_pool_delete_group), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(group: PoolGroup, count: Int?, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val label = group.criterion.label
    val description = if (count == null) label else pluralStringResource(R.plurals.dice_pool_group_description, count, label, count)
    val deleteLabel = stringResource(R.string.dice_pool_delete_group_action, label)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onLongClick)
            .testTag(POOL_GROUP_CHIP_TAG + label)
            .semantics {
                this.selected = selected
                contentDescription = description
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { onLongClick(); true })
            },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            if (count != null) {
                Text(" · $count", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A new group's test: the target (1..[sides]) and ≥ / ≤ / =. */
@Composable
private fun CriterionDialog(
    initial: SuccessCriterion,
    sides: Int,
    onSave: (SuccessCriterion) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial.target.toString()) }
    var comparison by rememberSaveable { mutableStateOf(initial.comparison) }
    val target = text.toIntOrNull()
    val valid = target != null && target in 1..sides
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dice_pool_add_group)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val name = stringResource(R.string.dice_pool_target)
                StepperNumberField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(4) },
                    onStep = { delta -> text = ((target ?: 1) + delta).coerceIn(1, sides).toString() },
                    increaseDescription = stringResource(R.string.stepper_increase, name),
                    decreaseDescription = stringResource(R.string.stepper_decrease, name),
                    arrows = StepperArrows.AFTER,
                    label = name,
                    isError = !valid,
                    supportingText = if (!valid) ({ Text(stringResource(R.string.dice_pool_target_range, sides)) }) else null,
                    keyboardType = KeyboardType.NumberPassword,
                    textStyle = MaterialTheme.typography.titleLarge,
                    canIncrease = (target ?: 0) < sides,
                    canDecrease = (target ?: 2) > 1,
                )
                val options = listOf(
                    Comparison.AT_LEAST to R.string.dice_pool_at_least,
                    Comparison.AT_MOST to R.string.dice_pool_at_most,
                    Comparison.EQUAL to R.string.dice_pool_equal,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = comparison == value,
                            onClick = { comparison = value },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            label = { Text(stringResource(label), maxLines = 1) },
                        )
                    }
                }
                if (valid) {
                    Text(
                        stringResource(R.string.dice_pool_preview, SuccessCriterion(target!!, comparison).label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(SuccessCriterion(target!!, comparison)) }, enabled = valid) { Text(stringResource(R.string.dice_pool_save_group)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** " · 4+ → 3 successes" for the selected group, or "" without one. */
@Composable
private fun groupSuffix(pool: DicePool, criterion: SuccessCriterion?): String {
    criterion ?: return ""
    val successes = pool.successes(criterion)
    return " · ${criterion.label} → " + pluralStringResource(R.plurals.dice_pool_successes, successes, successes)
}

/** "10d6" or "10d6 · 4+ → 3 successes". */
@Composable
fun poolSummary(pool: DicePool, criterion: SuccessCriterion?): String = "${pool.size}d${pool.sides}" + groupSuffix(pool, criterion)

/** The pool as text: "10d6: 6 6 5 5 5 4 4 3 3 1 · 5+ → 5 successes" (sorted for the group, if any). */
@Composable
fun poolShareText(pool: DicePool, criterion: SuccessCriterion?): String {
    val values = (criterion?.let { pool.ordered(it) } ?: pool.dice).joinToString(" ") { it.value.toString() }
    return "${pool.size}d${pool.sides}: $values" + groupSuffix(pool, criterion)
}

/** "Share as image" in pool mode: the pool on the tray, the group highlighted, and the summary. */
@Composable
internal fun PoolSnapshot(pool: DicePool, criterion: SuccessCriterion?) {
    val inset = LocalThemeStyle.current.tray.contentInset
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(poolSummary(pool, criterion), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        FlowRow(
            Modifier.fillMaxWidth().themedTray(MaterialTheme.shapes.extraLarge).padding(20.dp + inset),
            horizontalArrangement = Arrangement.Center,
        ) {
            val die = if (pool.size > POOL_LARGE_DICE) 44.dp else 64.dp
            (criterion?.let { pool.ordered(it) } ?: pool.dice).forEach { poolDie ->
                val inGroup = criterion?.matches(poolDie.value) == true
                PoolDieCell(pool.sides, poolDie, inGroup, dimmed = criterion != null && !inGroup, size = die, cell = die / 0.78f, animated = false, key = 0)
            }
        }
    }
}
