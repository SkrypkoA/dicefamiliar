package com.skrypko.dicefamiliar.domain.dice

import kotlinx.serialization.Serializable

@Serializable
enum class RollMode {
    NORMAL,
    ADVANTAGE,
    DISADVANTAGE,
    ;

    /**
     * Advantage/disadvantage apply only when the formula has exactly one die in total and it is a
     * plain positive `1dN` (any N): it becomes `2dNkh1` / `2dNkl1`, modifiers are kept.
     * Anything else is returned unchanged.
     */
    fun applyTo(expression: DiceExpression): DiceExpression {
        val keepType = when (this) {
            NORMAL -> return expression
            ADVANTAGE -> KeepType.HIGHEST
            DISADVANTAGE -> KeepType.LOWEST
        }
        if (!isApplicable(expression)) return expression
        return DiceExpression(
            expression.terms.map { term ->
                if (term is DiceTerm) term.copy(count = 2, keep = Keep(keepType, 1)) else term
            },
        )
    }

    companion object {
        /** True when the formula is a single plain die (plus optional modifiers). */
        fun isApplicable(expression: DiceExpression): Boolean {
            val dice = expression.terms.filterIsInstance<DiceTerm>()
            val die = dice.singleOrNull() ?: return false
            return die.count == 1 && die.isPlain && die.sign == Sign.PLUS
        }
    }
}
