package com.skrypko.dicefamiliar.domain.cards.io

import com.skrypko.dicefamiliar.domain.cards.CardFace
import org.junit.Assert.assertEquals
import org.junit.Test

class CardCsvParserTest {

    private fun ok(text: String) = CardCsvParser.parse(text) as CardCsvResult.Success
    private fun error(text: String, max: Int = 1000) = (CardCsvParser.parse(text, max) as CardCsvResult.Failure).error

    @Test
    fun headerInAnyOrder() {
        val result = ok("suit,name,value\n♥,Ace of Hearts,A\n")
        assertEquals(listOf(CardFace("Ace of Hearts", "A", "♥")), result.cards)
    }

    @Test
    fun noHeaderReadsNameValueSuitImage() {
        assertEquals(listOf(CardFace("Fool", "0", "Major", "img/fool.png")), ok("Fool,0,Major,img/fool.png").cards)
    }

    @Test
    fun quotingBomBlankLinesAndCrlf() {
        val result = ok("\uFEFFname,value\r\n\"Card, with comma\",\"1\"\"\"\r\n\r\n,,extra\r\nSecond,2\r\n")
        assertEquals(listOf("Card, with comma", "Second"), result.cards.map { it.name })
        assertEquals("1\"", result.cards[0].value)
        assertEquals(1, result.skippedRows)
    }

    @Test
    fun nameFallsBackToValueAndSuitOrImage() {
        val result = ok("name,value,suit,image\n,7,Rams,\n,,,art/the_tower.png\n")
        assertEquals(listOf("7 Rams", "the_tower"), result.cards.map { it.name })
    }

    @Test
    fun errors() {
        assertEquals(CardDeckImportError.NO_CARDS, error(""))
        assertEquals(CardDeckImportError.NO_CARDS, error("name,value\n"))
        assertEquals(CardDeckImportError.BAD_CSV, error("name\n\"open"))
        assertEquals(CardDeckImportError.TOO_MANY_CARDS, error("a\nb\nc", max = 2))
    }
}
