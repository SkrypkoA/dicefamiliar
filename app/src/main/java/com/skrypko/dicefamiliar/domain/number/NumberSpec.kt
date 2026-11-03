package com.skrypko.dicefamiliar.domain.number

import kotlinx.serialization.Serializable

enum class NumberMode { INTEGER, DECIMAL }

object NumberLimits {
    /** Largest |min| or |max| accepted. */
    const val MAX_ABS_VALUE = 1_000_000_000L
    const val MIN_COUNT = 1
    const val MAX_COUNT = 1000
    const val MIN_DECIMALS = 1
    const val MAX_DECIMALS = 6
    const val DEFAULT_DECIMALS = 2
    const val MAX_INPUT_LENGTH = 20
}

enum class NumberField { MIN, MAX, COUNT }

/** Validation errors; each belongs to the field the UI marks. */
enum class NumberError(val field: NumberField) {
    /** Not a number, or a fraction in integer mode. */
    MIN_INVALID(NumberField.MIN),
    MAX_INVALID(NumberField.MAX),

    /** More decimal places than the chosen precision. */
    MIN_TOO_PRECISE(NumberField.MIN),
    MAX_TOO_PRECISE(NumberField.MAX),
    MIN_OUT_OF_LIMITS(NumberField.MIN),
    MAX_OUT_OF_LIMITS(NumberField.MAX),
    MIN_ABOVE_MAX(NumberField.MAX),
    COUNT_INVALID(NumberField.COUNT),

    /** Duplicates are off and the range holds fewer values than requested. */
    COUNT_EXCEEDS_RANGE(NumberField.COUNT),
}

/**
 * A validated request. [min] and [max] are *scaled* by 10^[decimals] (inclusive), so decimal mode
 * is integer generation on the scaled range: uniform, both ends included, exact duplicate rules.
 */
data class NumberSpec(
    val min: Long,
    val max: Long,
    val count: Int,
    val allowDuplicates: Boolean,
    val decimals: Int,
) {
    /** How many distinct values the range holds. */
    val rangeSize: Long get() = max - min + 1
}

/** One finished generation; values are scaled like [NumberSpec]. */
@Serializable
data class NumberResult(
    val id: String,
    val values: List<Long>,
    val decimals: Int,
    val min: Long,
    val max: Long,
    val allowDuplicates: Boolean,
    val generatedAt: Long,
) {
    /** The values as copied or shared: `"7, 42, 13"`. */
    val text: String get() = NumberFormatter.joined(values, decimals)
    val sum: Long get() = values.sum()
}
