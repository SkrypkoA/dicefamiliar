package com.skrypko.dicefamiliar.domain.number

import java.math.BigDecimal
import java.math.RoundingMode

/** What one ±1 step does to the Min/Max/Count field text (chevrons and swipes). */
object NumberStepper {

    private val NUMBER = Regex("""^[-+]?(\d+\.?\d*|\.\d+)$""")
    private val LIMIT = BigDecimal.valueOf(NumberLimits.MAX_ABS_VALUE)

    /**
     * Steps a bound by [delta] whole units, clamped to ±[NumberLimits.MAX_ABS_VALUE].
     * Integer mode first rounds a fraction towards the step (1.5 → up 2, down 1); decimal mode keeps
     * the fraction (0.25 → 1.25). Text that isn't a number becomes `0`. A typed `,` separator is kept.
     */
    fun stepBound(text: String, delta: Int, mode: NumberMode): String {
        val comma = ',' in text
        val clean = text.trim().replace(',', '.')
        if (!NUMBER.matches(clean)) return "0"
        var value = BigDecimal(clean)
        if (mode == NumberMode.INTEGER) {
            val rounded = value.setScale(0, if (delta > 0) RoundingMode.FLOOR else RoundingMode.CEILING)
            value = if (rounded.compareTo(value) != 0 && delta != 0) {
                // A fraction steps to the neighbouring whole number first.
                value.setScale(0, if (delta > 0) RoundingMode.CEILING else RoundingMode.FLOOR)
                    .add(BigDecimal.valueOf((delta - delta.sign()).toLong()))
            } else {
                rounded.add(BigDecimal.valueOf(delta.toLong()))
            }
        } else {
            value = value.add(BigDecimal.valueOf(delta.toLong()))
        }
        value = value.max(LIMIT.negate()).min(LIMIT)
        val out = value.toPlainString()
        return if (comma) out.replace('.', ',') else out
    }

    /** Steps the count by [delta], clamped to 1–1000. Text that isn't a count becomes `1`. */
    fun stepCount(text: String, delta: Int): String {
        val current = text.trim().toIntOrNull() ?: return NumberLimits.MIN_COUNT.toString()
        return (current.toLong() + delta).coerceIn(NumberLimits.MIN_COUNT.toLong(), NumberLimits.MAX_COUNT.toLong()).toString()
    }

    private fun Int.sign(): Int = if (this > 0) 1 else if (this < 0) -1 else 0
}
