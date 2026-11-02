package com.skrypko.dicefamiliar.domain.dice

import com.skrypko.dicefamiliar.domain.random.SeededRandomSource
import com.skrypko.dicefamiliar.testing.ScriptedRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceRollerTest {

    private fun expr(text: String) = (DiceNotationParser.parse(text) as ParseResult.Success).expression

    @Test
    fun sumsGroupsAndConstants() {
        val outcome = DiceRoller(ScriptedRandomSource(3, 5, 2)).roll(expr("2d6+1d4+3"))
        assertEquals(listOf(3, 5), outcome.groups[0].dice.map { it.value })
        assertEquals(8, outcome.groups[0].subtotal)
        assertEquals(2, outcome.groups[1].subtotal)
        assertEquals(3, outcome.constant)
        assertEquals(13, outcome.total)
    }

    @Test
    fun negativeGroupsAndConstantsSubtract() {
        val outcome = DiceRoller(ScriptedRandomSource(6, 4)).roll(expr("1d8-1d4-2"))
        assertEquals(-4, outcome.groups[1].subtotal)
        assertEquals(-2, outcome.constant)
        assertEquals(0, outcome.total)
    }

    @Test
    fun keepHighestDropsLowest() {
        val outcome = DiceRoller(ScriptedRandomSource(2, 6, 1, 5)).roll(expr("4d6kh3"))
        assertEquals(listOf(true, true, false, true), outcome.groups.single().dice.map { it.kept })
        assertEquals(13, outcome.total)
    }

    @Test
    fun keepLowest() {
        val outcome = DiceRoller(ScriptedRandomSource(17, 4)).roll(expr("2d20kl1"))
        assertEquals(listOf(false, true), outcome.groups.single().dice.map { it.kept })
        assertEquals(4, outcome.total)
    }

    @Test
    fun tiesKeepEarliestDie() {
        val outcome = DiceRoller(ScriptedRandomSource(5, 5, 5)).roll(expr("3d6kh2"))
        assertEquals(listOf(true, true, false), outcome.groups.single().dice.map { it.kept })
        assertEquals(10, outcome.total)
    }

    @Test
    fun valuesStayInRange() {
        val roller = DiceRoller(SeededRandomSource(3))
        val seen = HashSet<Int>()
        repeat(10_000) {
            val value = roller.roll(expr("1d6")).total
            assertTrue(value in 1..6)
            seen += value
        }
        assertEquals((1..6).toSet(), seen)
    }
}
