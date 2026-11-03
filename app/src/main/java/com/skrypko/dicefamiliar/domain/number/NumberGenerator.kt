package com.skrypko.dicefamiliar.domain.number

import com.skrypko.dicefamiliar.domain.random.RandomSource
import java.math.BigDecimal

/** Generates numbers for a [NumberSpec]; all randomness comes from [random]. */
class NumberGenerator(private val random: RandomSource) {

    /** Scaled values (see [NumberSpec]), in random order. */
    fun generate(spec: NumberSpec): List<Long> {
        require(spec.count in NumberLimits.MIN_COUNT..NumberLimits.MAX_COUNT) { "count out of range" }
        require(spec.min <= spec.max) { "min > max" }
        if (spec.allowDuplicates) return List(spec.count) { random.nextLong(spec.min, spec.max + 1) }

        require(spec.count <= spec.rangeSize) { "count exceeds range" }
        // Floyd's sampling: exactly `count` draws, every subset equally likely.
        val n = spec.rangeSize
        val chosen = LinkedHashSet<Long>(spec.count * 2)
        for (j in n - spec.count until n) {
            val t = random.nextLong(0, j + 1)
            chosen += if (t in chosen) j else t
        }
        // Floyd fixes the subset, not the order: shuffle so the order is random too.
        return random.shuffle(chosen.map { spec.min + it })
    }
}

/** Exact formatting of scaled values (no floating point): `50, 2` → `"0.50"`. */
object NumberFormatter {
    fun format(scaled: Long, decimals: Int): String =
        if (decimals == 0) scaled.toString() else BigDecimal.valueOf(scaled, decimals).toPlainString()

    fun joined(values: List<Long>, decimals: Int): String = values.joinToString(", ") { format(it, decimals) }
}
