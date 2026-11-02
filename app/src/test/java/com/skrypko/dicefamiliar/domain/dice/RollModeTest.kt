package com.skrypko.dicefamiliar.domain.dice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RollModeTest {

    private fun expr(text: String) = (DiceNotationParser.parse(text) as ParseResult.Success).expression

    private fun apply(mode: RollMode, text: String) = mode.applyTo(expr(text)).toNotation()

    @Test
    fun appliesToASingleDieOfAnyType() {
        assertEquals("2d6kh1", apply(RollMode.ADVANTAGE, "1d6"))
        assertEquals("2d20kh1+5", apply(RollMode.ADVANTAGE, "d20+5"))
        assertEquals("2d100kl1", apply(RollMode.DISADVANTAGE, "1d100"))
        assertEquals("2d8kh1+3", apply(RollMode.ADVANTAGE, "1d8+3"))
        assertEquals("2d7kl1", apply(RollMode.DISADVANTAGE, "1d7"))
    }

    @Test
    fun ignoredUnlessExactlyOnePlainPositiveDie() {
        listOf("1d20+1d4", "2d6", "3d20", "1d20kh1", "-1d6", "5").forEach { text ->
            assertEquals(text, expr(text).toNotation(), apply(RollMode.ADVANTAGE, text))
        }
        assertEquals("1d20", apply(RollMode.NORMAL, "1d20"))
    }

    @Test
    fun applicability() {
        assertTrue(RollMode.isApplicable(expr("1d12-2")))
        assertFalse(RollMode.isApplicable(expr("1d12+1d4")))
        assertFalse(RollMode.isApplicable(expr("2d12")))
        assertFalse(RollMode.isApplicable(expr("3")))
    }
}
