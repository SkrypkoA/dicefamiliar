package com.skrypko.dicefamiliar.domain.cards.io

import com.skrypko.dicefamiliar.domain.io.CsvReader
import com.skrypko.dicefamiliar.domain.cards.CardFace
import com.skrypko.dicefamiliar.domain.cards.CardLimits

enum class CardDeckImportError {
    NOT_A_ZIP,
    UNSAFE_PATH,
    TOO_MANY_CARDS,
    TOO_LARGE,
    NO_CARDS,
    BAD_CSV,
    READ_FAILED,
}

sealed interface CardCsvResult {
    /** [cards] keep the CSV's `image` path as written (resolved later against the archive). */
    data class Success(val cards: List<CardFace>, val skippedRows: Int) : CardCsvResult
    data class Failure(val error: CardDeckImportError) : CardCsvResult
}

/**
 * Reads a card list: `name,value,suit,image`. The header is optional (columns are then read in that
 * order) and may list the columns in any order. A row needs a name, a value or an image; without a
 * name, it is built from value and suit (or the image file name). Blank lines are skipped.
 */
object CardCsvParser {
    private val COLUMNS = listOf("name", "value", "suit", "image")

    fun parse(text: String, maxCards: Int = CardLimits.MAX_CARDS): CardCsvResult {
        val rows = try {
            CsvReader.read(text)
        } catch (_: IllegalArgumentException) {
            return CardCsvResult.Failure(CardDeckImportError.BAD_CSV)
        }
        if (rows.isEmpty()) return CardCsvResult.Failure(CardDeckImportError.NO_CARDS)
        val header = rows.first().map { it.trim().lowercase() }
        val hasHeader = "name" in header && header.all { it in COLUMNS || it.isEmpty() }
        val index = if (hasHeader) COLUMNS.associateWith { header.indexOf(it) } else COLUMNS.withIndex().associate { it.value to it.index }
        val data = if (hasHeader) rows.drop(1) else rows

        var skipped = 0
        val cards = mutableListOf<CardFace>()
        for (row in data) {
            fun cell(column: String) = index.getValue(column).takeIf { it >= 0 }?.let { row.getOrNull(it) }?.trim()?.ifEmpty { null }
            val value = cell("value")
            val suit = cell("suit")
            val image = cell("image")
            val name = cell("name")
                ?: listOfNotNull(value, suit).joinToString(" ").ifEmpty { null }
                ?: image?.substringAfterLast('/')?.substringBeforeLast('.')
            if (name == null) {
                skipped++
                continue
            }
            if (cards.size == maxCards) return CardCsvResult.Failure(CardDeckImportError.TOO_MANY_CARDS)
            cards += CardFace(name.take(MAX_TEXT), value?.take(MAX_TEXT), suit?.take(MAX_TEXT), image)
        }
        if (cards.isEmpty()) return CardCsvResult.Failure(CardDeckImportError.NO_CARDS)
        return CardCsvResult.Success(cards, skipped)
    }

    private const val MAX_TEXT = 80
}
