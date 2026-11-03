package com.skrypko.dicefamiliar.domain.pool

/** Item colours: the preset palette, `#RRGGBB` / `#AARRGGBB` parsing and readable text on a colour. */
object ItemColors {

    /** The palette offered in the item editor (opaque ARGB). */
    val palette: List<Int> = listOf(
        0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
        0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1,
        0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33,
        0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFF6D4C41,
    ).map { it.toInt() }

    enum class TextTone { BLACK, WHITE }

    /** `#RRGGBB` (opaque) or `#AARRGGBB`; anything else is `null`. */
    fun parse(text: String?): Int? {
        val hex = text?.trim()?.removePrefix("#") ?: return null
        if (hex.length != 6 && hex.length != 8) return null
        val value = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 6) (0xFF000000 or value).toInt() else value.toInt()
    }

    /** `#RRGGBB` for opaque colours, otherwise `#AARRGGBB`; upper-case hex. */
    fun format(argb: Int): String {
        val alpha = argb ushr 24
        return if (alpha == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)
    }

    /** Black or white text, whichever contrasts more with [argb] (WCAG relative luminance). */
    fun contrastText(argb: Int): TextTone {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        val onBlack = (luminance + 0.05) / 0.05
        val onWhite = 1.05 / (luminance + 0.05)
        return if (onBlack >= onWhite) TextTone.BLACK else TextTone.WHITE
    }
}
