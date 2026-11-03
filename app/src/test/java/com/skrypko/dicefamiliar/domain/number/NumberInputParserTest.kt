package com.skrypko.dicefamiliar.domain.number

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberInputParserTest {

    private fun parse(
        min: String = "1",
        max: String = "100",
        count: String = "1",
        mode: NumberMode = NumberMode.INTEGER,
        decimals: Int = 2,
        duplicates: Boolean = true,
    ) = NumberInputParser.parse(min, max, count, mode, decimals, duplicates)

    private fun errors(result: NumberInputResult) = (result as NumberInputResult.Invalid).errors
    private fun spec(result: NumberInputResult) = (result as NumberInputResult.Valid).spec

    @Test
    fun defaults_areValid() {
        assertEquals(NumberSpec(1, 100, 1, true, 0), spec(parse()))
    }

    @Test
    fun negativeAndEqualBounds_areValid() {
        assertEquals(NumberSpec(-5, -5, 3, true, 0), spec(parse(min = " -5 ", max = "-5", count = "3")))
    }

    @Test
    fun decimalMode_scalesBounds_andAcceptsComma() {
        assertEquals(NumberSpec(0, 150, 2, true, 2), spec(parse(min = "0", max = "1,5", count = "2", mode = NumberMode.DECIMAL)))
        assertEquals(NumberSpec(-250, 50, 1, true, 3), spec(parse(min = "-.25", max = "0.050", mode = NumberMode.DECIMAL, decimals = 3)))
    }

    @Test
    fun integerMode_rejectsFractions_butAcceptsTrailingZeros() {
        assertEquals(listOf(NumberError.MIN_INVALID), errors(parse(min = "1.5")))
        assertEquals(NumberSpec(1, 100, 1, true, 0), spec(parse(min = "1.0")))
    }

    @Test
    fun decimalMode_rejectsTooManyPlaces() {
        assertEquals(listOf(NumberError.MAX_TOO_PRECISE), errors(parse(max = "1.234", mode = NumberMode.DECIMAL)))
    }

    @Test
    fun garbage_isInvalid() {
        listOf("", " ", "abc", "1e5", "--1", "1.2.3", ".", "+").forEach { text ->
            assertEquals(text, listOf(NumberError.MIN_INVALID), errors(parse(min = text)))
        }
        assertEquals(listOf(NumberError.MAX_INVALID), errors(parse(max = "1".repeat(21))))
    }

    @Test
    fun outOfLimits() {
        assertEquals(listOf(NumberError.MIN_OUT_OF_LIMITS), errors(parse(min = "-1000000001")))
        assertEquals(listOf(NumberError.MAX_OUT_OF_LIMITS), errors(parse(max = "1000000001")))
        assertEquals(NumberSpec(-1_000_000_000, 1_000_000_000, 1, true, 0), spec(parse(min = "-1000000000", max = "1000000000")))
    }

    @Test
    fun minAboveMax_marksMax() {
        val result = errors(parse(min = "10", max = "1"))
        assertEquals(listOf(NumberError.MIN_ABOVE_MAX), result)
        assertEquals(NumberField.MAX, result.single().field)
    }

    @Test
    fun countRange() {
        listOf("0", "1001", "", "x", "1.5").forEach { text ->
            assertEquals(text, listOf(NumberError.COUNT_INVALID), errors(parse(count = text)))
        }
        assertEquals(1000, spec(parse(count = "1000")).count)
    }

    @Test
    fun noDuplicates_countMustFitTheRange() {
        assertEquals(listOf(NumberError.COUNT_EXCEEDS_RANGE), errors(parse(count = "101", duplicates = false)))
        assertEquals(100, spec(parse(count = "100", duplicates = false)).count)
        // 0.0..1.0 with one place holds 11 values.
        assertEquals(
            listOf(NumberError.COUNT_EXCEEDS_RANGE),
            errors(parse(min = "0", max = "1", count = "12", mode = NumberMode.DECIMAL, decimals = 1, duplicates = false)),
        )
        assertEquals(11, spec(parse(min = "0", max = "1", count = "11", mode = NumberMode.DECIMAL, decimals = 1, duplicates = false)).count)
    }

    @Test
    fun severalErrors_areAllReported() {
        assertEquals(
            listOf(NumberError.MIN_INVALID, NumberError.MAX_OUT_OF_LIMITS, NumberError.COUNT_INVALID),
            errors(parse(min = "a", max = "2000000000", count = "0")),
        )
    }
}
