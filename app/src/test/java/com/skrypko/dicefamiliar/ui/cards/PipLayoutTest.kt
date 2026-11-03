package com.skrypko.dicefamiliar.ui.cards

import com.skrypko.dicefamiliar.domain.cards.Rank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipLayoutTest {

    @Test
    fun numberCardsHaveAsManyPipsAsTheirValue() {
        val values = mapOf(
            Rank.ACE to 1, Rank.TWO to 2, Rank.THREE to 3, Rank.FOUR to 4, Rank.FIVE to 5, Rank.SIX to 6,
            Rank.SEVEN to 7, Rank.EIGHT to 8, Rank.NINE to 9, Rank.TEN to 10,
        )
        values.forEach { (rank, count) -> assertEquals(rank.name, count, PipLayout.positions(rank).size) }
        listOf(Rank.JACK, Rank.QUEEN, Rank.KING).forEach { assertTrue(PipLayout.positions(it).isEmpty()) }
    }

    @Test
    fun pipsStayInsideTheAreaAndNeverOverlap() {
        Rank.entries.forEach { rank ->
            val pips = PipLayout.positions(rank)
            pips.forEach { assertTrue(it.x in 0f..1f && it.y in 0f..1f) }
            assertEquals(rank.name, pips.size, pips.toSet().size)
        }
    }

    @Test
    fun layoutsAreSymmetricTopToBottom() {
        // Every pip has a partner mirrored across the middle (the 7 is the classic exception).
        Rank.entries.filter { it != Rank.SEVEN }.forEach { rank ->
            val pips = PipLayout.positions(rank).map { it.x to Math.round(it.y * 600) }.toSet()
            pips.forEach { (x, y) -> assertTrue("$rank", (x to 600 - y) in pips) }
        }
    }
}
