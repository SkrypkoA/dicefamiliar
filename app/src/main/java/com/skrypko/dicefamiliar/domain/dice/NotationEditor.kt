package com.skrypko.dicefamiliar.domain.dice

/** Result of a palette edit: the new text, or the unchanged text plus the error that blocked it. */
data class EditResult(val text: String, val error: NotationError? = null)

/** Pure helpers that build a formula from dice-palette taps. */
object NotationEditor {

    fun addDie(current: String, sides: Int): EditResult {
        if (current.isBlank()) return EditResult("1d$sides")
        val expression = parseOrNull(current)
            ?: return EditResult(appendRaw(current, "1d$sides"))
        val terms = expression.terms.toMutableList()
        val index = terms.indexOfFirst { it.isPlainPositiveDie(sides) }
        if (index >= 0) {
            val term = terms[index] as DiceTerm
            terms[index] = term.copy(count = term.count + 1)
        } else {
            val insertAt = terms.indexOfLast { it is DiceTerm } + 1
            terms.add(insertAt, DiceTerm(1, sides))
        }
        return validated(current, DiceExpression(terms))
    }

    fun removeDie(current: String, sides: Int): EditResult = removeLast(current) { it.isPlainPositiveDie(sides) }

    /** Removes one custom die (any non-standard sides, e.g. d7), the most recently added group first. */
    fun removeCustomDie(current: String): EditResult = removeLast(current) { term ->
        term is DiceTerm && term.sides !in DiceLimits.STANDARD_DICE && term.isPlain && term.sign == Sign.PLUS
    }

    /**
     * Sides of the custom die currently in the formula (e.g. 7 for `2d7`), or null when there is none.
     * The dN button keeps adding this value until all custom dice are removed.
     */
    fun customSides(current: String): Int? =
        parseOrNull(current)?.terms?.filterIsInstance<DiceTerm>()
            ?.firstOrNull { it.sides !in DiceLimits.STANDARD_DICE }?.sides

    /** True when the formula contains at least one die (modifiers alone don't count). */
    fun hasDice(current: String): Boolean = (parseOrNull(current)?.diceCount ?: 0) > 0

    /** Number of custom (non-standard) dice in the formula, for the dN palette badge. */
    fun countCustom(current: String): Int =
        parseOrNull(current)?.terms?.filterIsInstance<DiceTerm>()
            ?.filter { it.sides !in DiceLimits.STANDARD_DICE }?.sumOf { it.count } ?: 0

    private fun removeLast(current: String, matches: (Term) -> Boolean): EditResult {
        val expression = parseOrNull(current) ?: return EditResult(current)
        val terms = expression.terms.toMutableList()
        val index = terms.indexOfLast(matches)
        if (index < 0) return EditResult(current)
        val term = terms[index] as DiceTerm
        if (term.count > 1) terms[index] = term.copy(count = term.count - 1) else terms.removeAt(index)
        return EditResult(normalizeLeadingSign(terms))
    }

    fun adjustModifier(current: String, delta: Int): EditResult {
        val expression = if (current.isBlank()) DiceExpression(emptyList()) else parseOrNull(current)
        if (expression == null) return EditResult(current)
        val constant = expression.terms.filterIsInstance<ConstantTerm>().sumOf { it.sign.factor * it.value } + delta
        if (kotlin.math.abs(constant) > DiceLimits.MAX_CONSTANT) {
            return EditResult(current, NotationError.CONSTANT_OUT_OF_RANGE)
        }
        val terms = expression.terms.filterNot { it is ConstantTerm }.toMutableList()
        if (constant != 0) {
            terms += ConstantTerm(kotlin.math.abs(constant), if (constant < 0) Sign.MINUS else Sign.PLUS)
        }
        return EditResult(normalizeLeadingSign(terms))
    }

    /** Number of dice with [sides] in the formula, for the palette badge. */
    fun countOf(current: String, sides: Int): Int =
        parseOrNull(current)?.terms?.filterIsInstance<DiceTerm>()?.filter { it.sides == sides }?.sumOf { it.count }
            ?: 0

    private fun Term.isPlainPositiveDie(sides: Int) =
        this is DiceTerm && this.sides == sides && isPlain && sign == Sign.PLUS

    private fun parseOrNull(text: String) = (DiceNotationParser.parse(text) as? ParseResult.Success)?.expression

    private fun validated(current: String, expression: DiceExpression): EditResult {
        val text = expression.toNotation()
        return when (val parsed = DiceNotationParser.parse(text)) {
            is ParseResult.Success -> EditResult(text)
            is ParseResult.Error -> EditResult(current, parsed.error)
        }
    }

    private fun normalizeLeadingSign(terms: List<Term>): String = DiceExpression(terms).toNotation()

    private fun appendRaw(current: String, addition: String): String {
        val trimmed = current.trimEnd()
        return if (trimmed.endsWith('+') || trimmed.endsWith('-')) trimmed + addition else "$trimmed+$addition"
    }
}
