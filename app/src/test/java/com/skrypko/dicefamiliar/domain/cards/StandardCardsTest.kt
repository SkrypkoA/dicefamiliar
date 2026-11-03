package com.skrypko.dicefamiliar.domain.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandardCardsTest {

    @Test
    fun presetSizes() {
        assertEquals(36, StandardCards.preset36.size)
        assertEquals(52, StandardCards.preset52.size)
        assertEquals(54, StandardCards.preset54.size)
        assertEquals(54, StandardCards.all54.toSet().size)
    }

    @Test
    fun jokersComeFirst() {
        assertEquals(listOf(StandardCards.RED_JOKER, StandardCards.BLACK_JOKER), StandardCards.all54.take(2))
    }

    @Test
    fun preset36StartsAtSix() {
        assertEquals(setOf("6", "7", "8", "9", "10", "J", "Q", "K", "A"), StandardCards.preset36.map { it.dropLast(1) }.toSet())
    }

    @Test
    fun facesCarryNameValueAndSuit() {
        assertEquals(CardFace("Ace of Spades", "A", "♠", standardId = "AS"), StandardCards.face("AS"))
        assertEquals(CardFace("Ten of Hearts", "10", "♥", standardId = "10H"), StandardCards.face("10H"))
        assertEquals("Red Joker", StandardCards.face("JR")?.name)
        assertNull(StandardCards.face("1X"))
    }

    @Test
    fun presetsResolve() {
        assertEquals(52, DeckPresets.get(DeckPresets.DEFAULT_ID)?.cards?.size)
        assertEquals(listOf("preset-36", "preset-52", "preset-54"), DeckPresets.ids)
        assertNull(DeckPresets.get("nope"))
    }
}
