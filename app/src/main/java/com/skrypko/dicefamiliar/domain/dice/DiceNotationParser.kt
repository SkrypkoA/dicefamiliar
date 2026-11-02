package com.skrypko.dicefamiliar.domain.dice

/**
 * Parses dice notation: `term (('+'|'-') term)*` where a term is `[N]dM[(kh|kl)[K]]` or `K`.
 * Case-insensitive, whitespace is ignored, an optional leading sign is allowed.
 */
object DiceNotationParser {

    fun parse(input: String): ParseResult {
        if (input.length > DiceLimits.MAX_INPUT_LENGTH) return ParseResult.Error(NotationError.TOO_LONG, 0)
        val text = input.filterNot { it.isWhitespace() }.lowercase()
        if (text.isEmpty()) return ParseResult.Error(NotationError.EMPTY, 0)
        return Parser(text).parse()
    }

    fun isValid(input: String): Boolean = parse(input) is ParseResult.Success

    private class ParseException(val error: NotationError, val position: Int) : Exception()

    private class Parser(private val text: String) {
        private var pos = 0

        fun parse(): ParseResult = try {
            val terms = mutableListOf<Term>()
            var sign = readSign() ?: Sign.PLUS
            while (true) {
                terms += readTerm(sign)
                if (terms.size > DiceLimits.MAX_TERMS) fail(NotationError.TOO_MANY_TERMS)
                if (pos == text.length) break
                sign = readSign() ?: fail(NotationError.UNEXPECTED_CHAR)
            }
            val expression = DiceExpression(terms)
            if (expression.diceCount > DiceLimits.MAX_TOTAL_DICE) fail(NotationError.TOO_MANY_DICE, 0)
            ParseResult.Success(expression)
        } catch (e: ParseException) {
            ParseResult.Error(e.error, e.position)
        }

        private fun readSign(): Sign? = when (peek()) {
            '+' -> Sign.PLUS.also { pos++ }
            '-' -> Sign.MINUS.also { pos++ }
            else -> null
        }

        private fun readTerm(sign: Sign): Term {
            if (pos == text.length) fail(NotationError.INCOMPLETE)
            val start = pos
            val number = readNumber()
            if (peek() != 'd') {
                if (number == null) fail(NotationError.UNEXPECTED_CHAR)
                if (number > DiceLimits.MAX_CONSTANT) fail(NotationError.CONSTANT_OUT_OF_RANGE, start)
                return ConstantTerm(number.toInt(), sign)
            }
            pos++ // 'd'
            val count = number ?: 1L
            if (count !in DiceLimits.MIN_COUNT..DiceLimits.MAX_COUNT) fail(NotationError.COUNT_OUT_OF_RANGE, start)
            val sidesStart = pos
            val sides = readNumber() ?: fail(NotationError.MISSING_SIDES)
            if (sides !in DiceLimits.MIN_SIDES..DiceLimits.MAX_SIDES) {
                fail(NotationError.SIDES_OUT_OF_RANGE, sidesStart)
            }
            val keep = readKeep(count.toInt())
            return DiceTerm(count.toInt(), sides.toInt(), keep, sign)
        }

        private fun readKeep(count: Int): Keep? {
            if (peek() != 'k') return null
            val start = pos
            pos++
            val type = when (peek()) {
                'h' -> KeepType.HIGHEST
                'l' -> KeepType.LOWEST
                null -> fail(NotationError.INCOMPLETE)
                else -> fail(NotationError.UNEXPECTED_CHAR)
            }
            pos++
            val n = readNumber() ?: 1L
            if (n !in 1..count) fail(NotationError.KEEP_OUT_OF_RANGE, start)
            return Keep(type, n.toInt())
        }

        /** Reads digits; values are capped so huge inputs cannot overflow. */
        private fun readNumber(): Long? {
            val start = pos
            var value = 0L
            while (pos < text.length && text[pos].isDigit()) {
                value = (value * 10 + (text[pos] - '0')).coerceAtMost(Int.MAX_VALUE.toLong())
                pos++
            }
            return if (pos == start) null else value
        }

        private fun peek(): Char? = text.getOrNull(pos)

        private fun fail(error: NotationError, position: Int = pos): Nothing = throw ParseException(error, position)
    }
}
