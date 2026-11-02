package com.skrypko.dicefamiliar.domain.dice

/** Hard limits for dice notation; also protect against oversized requests from other devices. */
object DiceLimits {
    const val MAX_INPUT_LENGTH = 100
    const val MIN_COUNT = 1
    const val MAX_COUNT = 100
    const val MIN_SIDES = 2
    const val MAX_SIDES = 1000
    const val MAX_TERMS = 20
    const val MAX_CONSTANT = 10_000
    const val MAX_TOTAL_DICE = 200

    /**
     * Steps a custom die's side count by [delta] for the dialog's −/+ buttons, clamped to the
     * allowed range. An empty or unparsable value starts from [MIN_SIDES].
     */
    fun stepSides(current: Int?, delta: Int): Int =
        if (current == null) MIN_SIDES else (current + delta).coerceIn(MIN_SIDES, MAX_SIDES)

    /** Dice offered in the palette. */
    val STANDARD_DICE = listOf(4, 6, 8, 10, 12, 20, 100)
}
