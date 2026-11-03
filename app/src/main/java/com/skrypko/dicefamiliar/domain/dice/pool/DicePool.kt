package com.skrypko.dicefamiliar.domain.dice.pool

import com.skrypko.dicefamiliar.domain.dice.DiceLimits
import kotlinx.serialization.Serializable

/** How a die is compared with a [SuccessCriterion]'s target. */
@Serializable
enum class Comparison(val symbol: String) {
    /** `5+`: the value is at least the target. */
    AT_LEAST("+"),

    /** `2−`: the value is at most the target. */
    AT_MOST("−"),

    /** `=1`: the value equals the target. */
    EQUAL("="),
}

/** A dice-pool success test such as `5+`, `2−` or `=1`. */
@Serializable
data class SuccessCriterion(val target: Int, val comparison: Comparison = Comparison.AT_LEAST) {

    fun matches(value: Int): Boolean = when (comparison) {
        Comparison.AT_LEAST -> value >= target
        Comparison.AT_MOST -> value <= target
        Comparison.EQUAL -> value == target
    }

    /** `5+`, `2−` or `=1`. */
    val label: String
        get() = when (comparison) {
            Comparison.AT_LEAST, Comparison.AT_MOST -> "$target${comparison.symbol}"
            Comparison.EQUAL -> "=$target"
        }

    /** The same test with its target kept within the die's faces (1..[sides]). */
    fun clampTo(sides: Int): SuccessCriterion = copy(target = target.coerceIn(1, sides.coerceAtLeast(1)))
}

/** One die of a pool; [id] stays the same when the die is rerolled, so it keeps its identity on screen. */
@Serializable
data class PoolDie(val id: Int, val value: Int)

/** A rolled pool of one die type. */
@Serializable
data class DicePool(val sides: Int, val dice: List<PoolDie>) {

    val size: Int get() = dice.size

    /** The dice that pass [criterion], in pool order. */
    fun matching(criterion: SuccessCriterion): List<PoolDie> = dice.filter { criterion.matches(it.value) }

    fun successes(criterion: SuccessCriterion): Int = dice.count { criterion.matches(it.value) }

    /**
     * The dice sorted for a selected group: descending for `N+` and `=N`, ascending for `N−`. Either way the
     * passing dice come first, side by side, so they read as one block. Ties keep the pool order.
     */
    fun ordered(criterion: SuccessCriterion): List<PoolDie> {
        val byValue = if (criterion.comparison == Comparison.AT_MOST) compareBy<PoolDie> { it.value } else compareByDescending { it.value }
        val sorted = dice.sortedWith(byValue)
        // `=N` sorted descending puts higher values before the matches; move the matches to the front.
        return if (criterion.comparison == Comparison.EQUAL) sorted.partition { criterion.matches(it.value) }.let { it.first + it.second } else sorted
    }

    /** The pool with the dice in [values] (id → new value) rerolled in place. */
    fun withValues(values: Map<Int, Int>): DicePool = copy(dice = dice.map { die -> values[die.id]?.let { die.copy(value = it) } ?: die })

    companion object {
        /** A pool from freshly rolled [values]; ids are 0, 1, 2, … */
        fun of(sides: Int, values: List<Int>): DicePool = DicePool(sides, values.mapIndexed { index, value -> PoolDie(index, value) })
    }
}

/** A named test the user can select on a pool; the pool's dice that pass it form the group. */
@Serializable
data class PoolGroup(val id: Int, val criterion: SuccessCriterion)

/** The user's groups, at most one of them selected. Groups are live filters: they re-match after every roll. */
@Serializable
data class PoolGroups(val groups: List<PoolGroup> = emptyList(), val selectedId: Int? = null) {

    val selected: PoolGroup? get() = groups.firstOrNull { it.id == selectedId }

    /** Adds a group (an identical criterion is reused instead) and selects it. */
    fun add(criterion: SuccessCriterion): PoolGroups {
        groups.firstOrNull { it.criterion == criterion }?.let { return copy(selectedId = it.id) }
        if (groups.size >= MAX_GROUPS) return this
        val id = (groups.maxOfOrNull { it.id } ?: 0) + 1
        return PoolGroups(groups + PoolGroup(id, criterion), id)
    }

    fun update(id: Int, criterion: SuccessCriterion): PoolGroups =
        copy(groups = groups.map { if (it.id == id) it.copy(criterion = criterion) else it })

    fun remove(id: Int): PoolGroups = PoolGroups(groups.filterNot { it.id == id }, selectedId.takeIf { it != id })

    /** Selects [id], or unselects it when it is already selected; selecting one unselects the others. */
    fun toggle(id: Int): PoolGroups = copy(selectedId = if (selectedId == id || groups.none { it.id == id }) null else id)

    companion object {
        const val MAX_GROUPS = 12
    }
}

/** Pool formulas are always `NdS`: one die type, 1–100 dice. Pure helpers for the palette and the count field. */
object PoolNotation {

    /** Dice offered in pool mode (no d100; dN is the custom die). */
    val POOL_DICE = listOf(4, 6, 8, 10, 12, 20)

    /** `(count, sides)` of a pool formula, or `null` when [notation] isn't `NdS`. */
    fun parse(notation: String): Pair<Int, Int>? {
        val match = PATTERN.matchEntire(notation.trim().lowercase()) ?: return null
        val count = match.groupValues[1].ifEmpty { "1" }.toIntOrNull() ?: return null
        val sides = match.groupValues[2].toIntOrNull() ?: return null
        if (count !in DiceLimits.MIN_COUNT..DiceLimits.MAX_COUNT || sides !in DiceLimits.MIN_SIDES..DiceLimits.MAX_SIDES) return null
        return count to sides
    }

    fun format(count: Int, sides: Int): String = "${count}d$sides"

    /** Palette tap: one more die of the same type, or a single die of a new type. */
    fun add(notation: String, sides: Int): String {
        val (count, current) = parse(notation) ?: return format(1, sides)
        return if (current == sides) format((count + 1).coerceAtMost(DiceLimits.MAX_COUNT), sides) else format(1, sides)
    }

    /** Palette long-press: one die fewer (the last one empties the pool). */
    fun remove(notation: String, sides: Int): String {
        val (count, current) = parse(notation) ?: return notation
        if (current != sides) return notation
        return if (count <= 1) "" else format(count - 1, sides)
    }

    /** The count field: [count] dice of the current type (1–100); nothing changes without a die type. */
    fun withCount(notation: String, count: Int): String {
        val (_, sides) = parse(notation) ?: return notation
        return format(count.coerceIn(DiceLimits.MIN_COUNT, DiceLimits.MAX_COUNT), sides)
    }

    private val PATTERN = Regex("(\\d*)d(\\d+)")
}
