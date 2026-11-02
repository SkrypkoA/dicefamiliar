package com.skrypko.dicefamiliar.ui.dice.render3d

/** How a d100 result is shown: a tens d10 (00..90) plus a units d10 (0..9), like tabletop percentile dice. */
object Percentile {
    /** Tens and units digits; 100 is shown as 00 + 0. */
    fun split(value: Int): Pair<Int, Int> {
        val v = value % 100
        return (v / 10) to (v % 10)
    }

    fun tensLabel(digit: Int): String = if (digit == 0) "00" else "${digit}0"

    fun unitsLabel(digit: Int): String = digit.toString()

    fun labels(value: Int): Pair<String, String> = split(value).let { (t, u) -> tensLabel(t) to unitsLabel(u) }
}

/** Which faces a d10 mesh shows. */
enum class DieKind { STANDARD, PERCENTILE_TENS, PERCENTILE_UNITS }
