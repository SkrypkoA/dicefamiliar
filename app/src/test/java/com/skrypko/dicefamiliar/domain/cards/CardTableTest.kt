package com.skrypko.dicefamiliar.domain.cards

import com.skrypko.dicefamiliar.domain.random.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTableTest {

    private val random = SeededRandomSource()
    private val table = CardTable(drawPile = listOf(0, 1, 2), hand = emptyList(), discard = emptyList())

    @Test
    fun freshIsAShuffledFullDrawPile() {
        val fresh = CardTable.fresh(52, random)
        assertTrue(fresh.isValidFor(52))
        assertEquals(52, fresh.drawPile.size)
        assertTrue(fresh.drawPile != (0 until 52).toList())
    }

    @Test
    fun drawTakesTheTopCard() {
        val after = table.draw().draw()
        assertEquals(listOf(0), after.drawPile)
        assertEquals(listOf(2, 1), after.hand)
    }

    @Test
    fun emptyPilesAreNoOps() {
        val empty = CardTable(emptyList(), listOf(5), emptyList())
        assertSame(empty, empty.draw())
        assertSame(empty, empty.takeFromDiscard())
        assertSame(empty, empty.discard(9))
        assertSame(empty, empty.discardOntoDraw())
    }

    @Test
    fun discardAndTakeBack() {
        val inHand = table.draw().draw().draw() // hand 2,1,0
        val one = inHand.discard(1)
        assertEquals(listOf(2, 0), one.hand)
        assertEquals(listOf(1), one.discard)
        val back = one.takeFromDiscard()
        assertEquals(listOf(2, 0, 1), back.hand)
        assertTrue(back.discard.isEmpty())
    }

    @Test
    fun putOnDrawPlacesTheCardOnTop() {
        val inHand = table.draw().draw() // draw 0, hand 2,1
        val back = inHand.putOnDraw(2)
        assertEquals(listOf(1), back.hand)
        assertEquals(listOf(0, 2), back.drawPile)
        assertEquals(2, back.draw().hand.last())
        assertSame(back, back.putOnDraw(7))
    }

    @Test
    fun handToDrawPlacesTheHandOnTop() {
        val after = table.draw().draw().handToDraw() // hand was 2,1
        assertTrue(after.hand.isEmpty())
        assertEquals(listOf(0, 2, 1), after.drawPile)
        assertSame(after, after.handToDraw())
    }

    @Test
    fun discardHandPutsLastDrawnOnTop() {
        val after = table.draw().draw().discardHand()
        assertTrue(after.hand.isEmpty())
        assertEquals(listOf(2, 1), after.discard)
        assertEquals(1, after.discard.last())
    }

    @Test
    fun discardOntoDrawTurnsThePileOverWithoutShuffling() {
        // draw 2, 1 → discard [2, 1] (1 on top); turned over onto [0]: the first card discarded (2) ends on top.
        val after = table.draw().draw().discardHand().discardOntoDraw()
        assertTrue(after.discard.isEmpty())
        assertEquals(listOf(0, 1, 2), after.drawPile)
    }

    @Test
    fun shufflesArePermutations() {
        val big = CardTable.fresh(54, random).let { t -> (1..20).fold(t) { acc, _ -> acc.draw() } }.discardHand()
        assertEquals(big.drawPile.toSet(), big.shuffleDraw(random).drawPile.toSet())
        assertEquals(big.discard.toSet(), big.shuffleDiscard(random).discard.toSet())
        assertTrue(big.shuffleDiscard(random).isValidFor(54))
    }

    @Test
    fun resetReturnsEverything() {
        val messy = CardTable.fresh(36, random).draw().draw().draw().discard(0).discardHand()
        val reset = messy.reset(random)
        assertTrue(reset.isValidFor(36))
        assertEquals(36, reset.drawPile.size)
    }

    @Test
    fun noCardIsEverLostOrDuplicated() {
        var t = CardTable.fresh(40, random)
        repeat(500) { step ->
            t = when (step % 7) {
                0, 1, 2 -> t.draw()
                3 -> t.hand.firstOrNull()?.let { t.discard(it) } ?: t
                4 -> t.takeFromDiscard()
                5 -> if (step % 3 == 0) t.discardHand() else t.shuffleDiscard(random)
                else -> if (t.drawPile.isEmpty()) t.discardOntoDraw() else t.shuffleDraw(random)
            }
            assertTrue("step $step", t.isValidFor(40))
        }
    }

    @Test
    fun validityCheck() {
        assertFalse(CardTable(listOf(0, 0), emptyList(), emptyList()).isValidFor(2))
        assertFalse(CardTable(listOf(0), emptyList(), emptyList()).isValidFor(2))
    }
}
