package com.skrypko.dicefamiliar.domain.cards

import kotlinx.serialization.Serializable

/**
 * What a card shows. Standard cards set [standardId] (see [StandardCards]) and are drawn with a
 * classic face; uploaded cards show [image] if present, otherwise [name], [value] and [suit] as text.
 */
@Serializable
data class CardFace(
    val name: String,
    val value: String? = null,
    val suit: String? = null,
    /** Image file: relative to the deck folder when stored, absolute when loaded for display. */
    val image: String? = null,
    val standardId: String? = null,
)

enum class DeckKind { PRESET, CUSTOM, UPLOADED }

/** A playable deck: the cards in their original order (the table refers to them by index). */
data class DeckDefinition(
    val id: String,
    val name: String,
    val kind: DeckKind,
    val cards: List<CardFace>,
    /** Card-back image (absolute path), or null for the theme's back. */
    val backImage: String? = null,
)

enum class Suit(val symbol: String, val code: Char, val title: String, val isRed: Boolean) {
    SPADES("♠", 'S', "Spades", false),
    HEARTS("♥", 'H', "Hearts", true),
    DIAMONDS("♦", 'D', "Diamonds", true),
    CLUBS("♣", 'C', "Clubs", false),
}

enum class Rank(val label: String, val title: String) {
    TWO("2", "Two"), THREE("3", "Three"), FOUR("4", "Four"), FIVE("5", "Five"), SIX("6", "Six"),
    SEVEN("7", "Seven"), EIGHT("8", "Eight"), NINE("9", "Nine"), TEN("10", "Ten"),
    JACK("J", "Jack"), QUEEN("Q", "Queen"), KING("K", "King"), ACE("A", "Ace"),
}

/** A parsed standard card id. */
sealed interface StandardCard {
    data class Regular(val rank: Rank, val suit: Suit) : StandardCard
    data class Joker(val red: Boolean) : StandardCard
}

/** The 54-card French deck: ids, faces and the 36 / 52 / 54 presets. */
object StandardCards {
    const val RED_JOKER = "JR"
    const val BLACK_JOKER = "JB"

    /** `"AS"`, `"10H"`, … */
    fun id(rank: Rank, suit: Suit): String = rank.label + suit.code

    fun parse(id: String): StandardCard? = when (id) {
        RED_JOKER -> StandardCard.Joker(red = true)
        BLACK_JOKER -> StandardCard.Joker(red = false)
        else -> {
            val suit = Suit.entries.firstOrNull { it.code == id.lastOrNull() }
            val rank = Rank.entries.firstOrNull { it.label == id.dropLast(1) }
            if (suit != null && rank != null) StandardCard.Regular(rank, suit) else null
        }
    }

    fun face(id: String): CardFace? = when (val card = parse(id)) {
        is StandardCard.Joker -> CardFace(if (card.red) "Red Joker" else "Black Joker", "JOKER", null, standardId = id)
        is StandardCard.Regular -> CardFace("${card.rank.title} of ${card.suit.title}", card.rank.label, card.suit.symbol, standardId = id)
        null -> null
    }

    /** All 54 ids with the **jokers first** (easy to exclude), then suit by suit, 2 → A. */
    val all54: List<String> = listOf(RED_JOKER, BLACK_JOKER) + suitsOf(Rank.entries)

    /** 6 → A in four suits. */
    val preset36: List<String> = suitsOf(Rank.entries.filter { it >= Rank.SIX })
    val preset52: List<String> = suitsOf(Rank.entries)
    val preset54: List<String> = all54

    private fun suitsOf(ranks: List<Rank>) = Suit.entries.flatMap { suit -> ranks.map { id(it, suit) } }
}

/** Built-in decks. The UI names them by size; [DEFAULT_ID] is used when nothing is selected. */
object DeckPresets {
    const val DEFAULT_ID = "preset-52"
    private val presets = listOf(
        "preset-36" to StandardCards.preset36,
        "preset-52" to StandardCards.preset52,
        "preset-54" to StandardCards.preset54,
    )

    val ids: List<String> = presets.map { it.first }

    fun isPreset(id: String): Boolean = presets.any { it.first == id }

    fun get(id: String): DeckDefinition? = presets.firstOrNull { it.first == id }?.let { (deckId, cards) ->
        standardDeck(deckId, "${cards.size} cards", DeckKind.PRESET, cards)
    }

    fun standardDeck(id: String, name: String, kind: DeckKind, cardIds: List<String>) =
        DeckDefinition(id, name, kind, cardIds.mapNotNull { StandardCards.face(it) })
}

object CardLimits {
    const val MAX_CARDS = 1000
    const val MAX_NAME_LENGTH = 40
}
