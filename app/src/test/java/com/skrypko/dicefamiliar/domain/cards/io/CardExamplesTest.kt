package com.skrypko.dicefamiliar.domain.cards.io

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The example decks in docs/card-examples (tools/gen_card_examples.py) must import cleanly. */
class CardExamplesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun example(name: String) =
        listOf(File("../docs/card-examples", name), File("docs/card-examples", name)).first { it.isFile }

    private fun zip(name: String) =
        example(name).inputStream().use { CardDeckArchiveReader().read(it, File(temp.root, name)) } as CardArchiveResult.Success

    @Test
    fun omensIs54CardsWithImagesAndBack() {
        val deck = zip("omens.zip")
        assertEquals(54, deck.cards.size)
        assertEquals(0, deck.missingImages)
        assertEquals(0, deck.skippedFiles)
        assertEquals("back.png", deck.backImage)
        assertEquals(setOf("Rams", "Crows", "Books", "Masks"), deck.cards.mapNotNull { it.suit }.toSet())
        assertEquals("1 of Rams", deck.cards.first().name)
        assertEquals(listOf("Red Joker", "Black Joker"), deck.cards.takeLast(2).map { it.name })
        deck.cards.forEach { card -> assertTrue(card.name, File(deck.rootDir, card.image!!).isFile) }
    }

    @Test
    fun runesIsImagesOnly() {
        val deck = zip("runes.zip")
        assertEquals(16, deck.cards.size)
        assertEquals("01 sun", deck.cards.first().name)
        assertEquals("16 crown", deck.cards.last().name)
    }

    @Test
    fun tarotIsAPlainCsv() {
        val result = CardCsvParser.parse(example("tarot-major.csv").readText()) as CardCsvResult.Success
        assertEquals(22, result.cards.size)
        assertEquals("The Fool", result.cards.first().name)
        assertEquals("XXI", result.cards.last().value)
    }
}
