package com.skrypko.dicefamiliar.domain.cards

import com.skrypko.dicefamiliar.domain.random.RandomSource
import kotlinx.serialization.Serializable

/**
 * The state of a deck on the table: draw pile, hand and discard pile, as **indices** into the
 * deck's card list (so repeated names stay distinct and the state is tiny to save or send).
 * In every pile the **last element is the top**; the hand is in drawing order.
 *
 * Operations return a new table and are no-ops when they can't apply (empty pile, unknown card).
 * All randomness goes through [RandomSource.shuffle]. LAN-ready: a future `cards.draw` handler on
 * the host applies [draw] to the host's table and sends back the drawn card's face.
 */
@Serializable
data class CardTable(
    val drawPile: List<Int>,
    val hand: List<Int>,
    val discard: List<Int>,
) {
    val size: Int get() = drawPile.size + hand.size + discard.size

    /** Top of the draw pile into the hand. */
    fun draw(): CardTable = if (drawPile.isEmpty()) this else copy(drawPile = drawPile.dropLast(1), hand = hand + drawPile.last())

    /** Top of the discard pile back into the hand. */
    fun takeFromDiscard(): CardTable =
        if (discard.isEmpty()) this else copy(discard = discard.dropLast(1), hand = hand + discard.last())

    /** Moves [card] from the hand onto the discard pile. */
    fun discard(card: Int): CardTable = if (card !in hand) this else copy(hand = hand - card, discard = discard + card)

    /** The whole hand onto the discard pile (the last card drawn ends on top). */
    fun discardHand(): CardTable = if (hand.isEmpty()) this else copy(hand = emptyList(), discard = discard + hand)

    /** Puts [card] from the hand on top of the draw pile (it will be drawn next). */
    fun putOnDraw(card: Int): CardTable = if (card !in hand) this else copy(drawPile = drawPile + card, hand = hand - card)

    /** The whole hand on top of the draw pile (the last card drawn ends on top). */
    fun handToDraw(): CardTable = if (hand.isEmpty()) this else copy(drawPile = drawPile + hand, hand = emptyList())

    fun shuffleDraw(random: RandomSource): CardTable = copy(drawPile = random.shuffle(drawPile))

    fun shuffleDiscard(random: RandomSource): CardTable = copy(discard = random.shuffle(discard))

    /**
     * The discard pile is turned over onto the draw pile, as with real cards: no shuffle, so the first
     * card discarded ends on top. Long-press the deck afterwards to mix it.
     */
    fun discardOntoDraw(): CardTable =
        if (discard.isEmpty()) this else copy(drawPile = drawPile + discard.reversed(), discard = emptyList())

    /** Every card back into the draw pile, shuffled. */
    fun reset(random: RandomSource): CardTable = fresh(size, random)

    /** True when the table holds each of `0 until deckSize` exactly once. */
    fun isValidFor(deckSize: Int): Boolean {
        val all = drawPile + hand + discard
        return all.size == deckSize && all.toSet() == (0 until deckSize).toSet()
    }

    companion object {
        fun fresh(deckSize: Int, random: RandomSource): CardTable =
            CardTable(random.shuffle((0 until deckSize).toList()), emptyList(), emptyList())
    }
}
