package com.skrypko.dicefamiliar.domain.dice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceNotationParserTest {

    private fun parse(text: String) = (DiceNotationParser.parse(text) as ParseResult.Success).expression
    private fun error(text: String) = (DiceNotationParser.parse(text) as ParseResult.Error).error

    @Test
    fun parsesSingleDieWithImplicitCount() {
        assertEquals(DiceExpression(listOf(DiceTerm(1, 20))), parse("d20"))
    }

    @Test
    fun parsesDiceWithModifier() {
        assertEquals(DiceExpression(listOf(DiceTerm(3, 6), ConstantTerm(2))), parse("3d6+2"))
        assertEquals(DiceExpression(listOf(DiceTerm(1, 8), ConstantTerm(1, Sign.MINUS))), parse("1d8-1"))
    }

    @Test
    fun parsesMultipleGroups() {
        assertEquals(
            DiceExpression(listOf(DiceTerm(2, 6), DiceTerm(1, 4), ConstantTerm(3))),
            parse("2d6+1d4+3"),
        )
    }

    @Test
    fun parsesKeepHighestAndLowest() {
        assertEquals(DiceTerm(4, 6, Keep(KeepType.HIGHEST, 3)), parse("4d6kh3").terms.single())
        assertEquals(DiceTerm(2, 20, Keep(KeepType.LOWEST, 1)), parse("2d20kl").terms.single())
    }

    @Test
    fun acceptsLeadingSignWhitespaceAndUpperCase() {
        assertEquals(
            DiceExpression(listOf(ConstantTerm(1, Sign.MINUS), DiceTerm(1, 4))),
            parse("-1+d4"),
        )
        assertEquals(parse("2d6kh1+3"), parse(" 2 D6 KH1 + 3 "))
    }

    @Test
    fun reportsErrors() {
        assertEquals(NotationError.EMPTY, error("   "))
        assertEquals(NotationError.INCOMPLETE, error("3d6+"))
        assertEquals(NotationError.INCOMPLETE, error("-"))
        assertEquals(NotationError.MISSING_SIDES, error("3d"))
        assertEquals(NotationError.UNEXPECTED_CHAR, error("3x6"))
        assertEquals(NotationError.UNEXPECTED_CHAR, error("2d6k3"))
        assertEquals(NotationError.UNEXPECTED_CHAR, error("abc"))
        assertEquals(NotationError.TOO_LONG, error("1".repeat(DiceLimits.MAX_INPUT_LENGTH + 1)))
    }

    @Test
    fun enforcesLimitBoundaries() {
        assertEquals(NotationError.COUNT_OUT_OF_RANGE, error("0d6"))
        assertTrue(DiceNotationParser.isValid("100d6"))
        assertEquals(NotationError.COUNT_OUT_OF_RANGE, error("101d6"))
        assertEquals(NotationError.SIDES_OUT_OF_RANGE, error("1d1"))
        assertTrue(DiceNotationParser.isValid("1d1000"))
        assertEquals(NotationError.SIDES_OUT_OF_RANGE, error("1d1001"))
        assertEquals(NotationError.KEEP_OUT_OF_RANGE, error("2d6kh3"))
        assertEquals(NotationError.KEEP_OUT_OF_RANGE, error("2d6kh0"))
        assertTrue(DiceNotationParser.isValid("1d6+10000"))
        assertEquals(NotationError.CONSTANT_OUT_OF_RANGE, error("1d6+10001"))
        assertEquals(NotationError.CONSTANT_OUT_OF_RANGE, error("99999999999999999999"))
        assertTrue(DiceNotationParser.isValid(List(20) { "1" }.joinToString("+")))
        assertEquals(NotationError.TOO_MANY_TERMS, error(List(21) { "1" }.joinToString("+")))
        assertTrue(DiceNotationParser.isValid("100d6+100d8"))
        assertEquals(NotationError.TOO_MANY_DICE, error("100d6+100d8+1d4"))
    }

    @Test
    fun canonicalNotationRoundTrips() {
        listOf("d20", "3d6+2", "2d6+1d4+3", "4d6kh3", "2d20kl", "-1+d4", "1d8-1d4-2").forEach { text ->
            val expression = parse(text)
            assertEquals(text, expression, parse(expression.toNotation()))
        }
        assertEquals("1d20", parse("d20").toNotation())
        assertEquals("-1+1d4", parse("-1+d4").toNotation())
        assertEquals("2d20kl1", parse("2d20kl").toNotation())
    }
}
