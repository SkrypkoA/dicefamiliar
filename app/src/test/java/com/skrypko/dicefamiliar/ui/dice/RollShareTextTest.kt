package com.skrypko.dicefamiliar.ui.dice

import com.skrypko.dicefamiliar.domain.dice.DiceNotationParser
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.dice.ParseResult
import com.skrypko.dicefamiliar.domain.dice.RollMode
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.testing.ScriptedRandomSource
import org.junit.Assert.assertEquals
import org.junit.Test

class RollShareTextTest {

    private fun record(notation: String, vararg values: Int): RollRecord {
        val expression = (DiceNotationParser.parse(notation) as ParseResult.Success).expression
        val outcome = DiceRoller(ScriptedRandomSource(*values)).roll(expression)
        val me = Participant("me", "Me")
        return RollRecord("1", notation, RollMode.NORMAL, null, outcome, me, me, 0L)
    }

    @Test
    fun listsTheTotalAndEachDie() {
        assertEquals("2d6+1d4+3 = 13 · [3 5] [2]", record("2d6+1d4+3", 3, 5, 2).shareText())
    }

    @Test
    fun droppedDiceAreInParentheses() {
        assertEquals("4d6kh3 = 13 · [2 6 (1) 5]", record("4d6kh3", 2, 6, 1, 5).shareText())
    }
}
