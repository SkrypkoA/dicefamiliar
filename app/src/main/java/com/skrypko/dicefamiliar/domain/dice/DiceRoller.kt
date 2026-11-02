package com.skrypko.dicefamiliar.domain.dice

import com.skrypko.dicefamiliar.domain.random.RandomSource
import kotlinx.serialization.Serializable

@Serializable
data class DieResult(val value: Int, val kept: Boolean = true)

/** Result of one dice group; [subtotal] already includes the group's sign. */
@Serializable
data class GroupResult(val term: DiceTerm, val dice: List<DieResult>, val subtotal: Int)

@Serializable
data class RollOutcome(
    val expression: DiceExpression,
    val groups: List<GroupResult>,
    val constant: Int,
    val total: Int,
)

class DiceRoller(private val random: RandomSource) {

    fun roll(expression: DiceExpression): RollOutcome {
        val groups = mutableListOf<GroupResult>()
        var constant = 0
        for (term in expression.terms) {
            when (term) {
                is DiceTerm -> groups += rollGroup(term)
                is ConstantTerm -> constant += term.sign.factor * term.value
            }
        }
        return RollOutcome(expression, groups, constant, groups.sumOf { it.subtotal } + constant)
    }

    private fun rollGroup(term: DiceTerm): GroupResult {
        val values = List(term.count) { random.nextInt(1, term.sides + 1) }
        val keptIndices = keptIndices(values, term.keep)
        val dice = values.mapIndexed { index, value -> DieResult(value, index in keptIndices) }
        val subtotal = dice.filter { it.kept }.sumOf { it.value } * term.sign.factor
        return GroupResult(term, dice, subtotal)
    }

    private fun keptIndices(values: List<Int>, keep: Keep?): Set<Int> {
        if (keep == null) return values.indices.toSet()
        val byValue = when (keep.type) {
            KeepType.HIGHEST -> compareByDescending<Int> { values[it] }
            KeepType.LOWEST -> compareBy { values[it] }
        }.thenBy { it }
        return values.indices.sortedWith(byValue).take(keep.count).toSet()
    }
}
