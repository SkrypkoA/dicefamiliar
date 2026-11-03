package com.skrypko.dicefamiliar.domain.number

import java.math.BigDecimal

sealed interface NumberInputResult {
    data class Valid(val spec: NumberSpec) : NumberInputResult
    data class Invalid(val errors: List<NumberError>) : NumberInputResult
}

/** Turns the text fields into a [NumberSpec]; accepts `.` or `,` as the decimal separator. */
object NumberInputParser {

    private val NUMBER = Regex("""^[-+]?(\d+\.?\d*|\.\d+)$""")
    private val LIMIT = BigDecimal.valueOf(NumberLimits.MAX_ABS_VALUE)

    fun parse(
        minText: String,
        maxText: String,
        countText: String,
        mode: NumberMode,
        decimals: Int,
        allowDuplicates: Boolean,
    ): NumberInputResult {
        val places = when (mode) {
            NumberMode.INTEGER -> 0
            NumberMode.DECIMAL -> decimals.coerceIn(NumberLimits.MIN_DECIMALS, NumberLimits.MAX_DECIMALS)
        }
        val errors = mutableListOf<NumberError>()
        val min = bound(minText, places, NumberError.MIN_INVALID, NumberError.MIN_TOO_PRECISE, NumberError.MIN_OUT_OF_LIMITS)
            .onError(errors)
        val max = bound(maxText, places, NumberError.MAX_INVALID, NumberError.MAX_TOO_PRECISE, NumberError.MAX_OUT_OF_LIMITS)
            .onError(errors)
        if (min != null && max != null && min > max) errors += NumberError.MIN_ABOVE_MAX

        val count = countText.trim().toIntOrNull()?.takeIf { it in NumberLimits.MIN_COUNT..NumberLimits.MAX_COUNT }
        if (count == null) {
            errors += NumberError.COUNT_INVALID
        } else if (!allowDuplicates && min != null && max != null && min <= max && count > max - min + 1) {
            errors += NumberError.COUNT_EXCEEDS_RANGE
        }

        return if (errors.isEmpty()) {
            NumberInputResult.Valid(NumberSpec(min!!, max!!, count!!, allowDuplicates, places))
        } else {
            NumberInputResult.Invalid(errors)
        }
    }

    private sealed interface Bound
    private data class Ok(val scaled: Long) : Bound
    private data class Err(val error: NumberError) : Bound

    private fun Bound.onError(errors: MutableList<NumberError>): Long? = when (this) {
        is Ok -> scaled
        is Err -> {
            errors += error
            null
        }
    }

    private fun bound(text: String, places: Int, invalid: NumberError, tooPrecise: NumberError, outOfLimits: NumberError): Bound {
        val clean = text.trim().replace(',', '.')
        if (clean.isEmpty() || clean.length > NumberLimits.MAX_INPUT_LENGTH || !NUMBER.matches(clean)) return Err(invalid)
        val value = BigDecimal(clean)
        if (value.stripTrailingZeros().scale() > places) return Err(if (places == 0) invalid else tooPrecise)
        if (value.abs() > LIMIT) return Err(outOfLimits)
        return Ok(value.movePointRight(places).longValueExact())
    }
}
