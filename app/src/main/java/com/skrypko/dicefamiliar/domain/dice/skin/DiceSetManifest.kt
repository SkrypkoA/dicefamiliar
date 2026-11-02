package com.skrypko.dicefamiliar.domain.dice.skin

import kotlinx.serialization.Serializable

/**
 * `manifest.json` of a dice set (`assets/dice_sets/<id>/`). See docs/dice-assets.md.
 * Colours are `#RRGGBB`, `#AARRGGBB` or `theme` (use the app's Material colours).
 */
@Serializable
data class DiceSetManifest(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val style: DieStyleSpec = DieStyleSpec(),
    /** Per-die overrides keyed `d4`, `d6`, `d8`, `d10`, `d12`, `d20`, `d100`, `dN`. */
    val dice: Map<String, DieStyleSpec> = emptyMap(),
)

@Serializable
data class DieStyleSpec(
    val fill: String? = null,
    val edge: String? = null,
    val text: String? = null,
    /** Outline width in dp, 0..8. */
    val edgeWidth: Float? = null,
    /** Font for numbers/labels: a `.ttf`/`.otf` file, e.g. `fonts/numbers.ttf`. */
    val font: String? = null,
    /** 2D silhouette replacing the drawn shape: a `.png`/`.webp` file, e.g. `2d/d6.png`, 512×512. */
    val image2d: String? = null,
    /** Face texture for the 3D die, laid out on the face template: a `.png`/`.webp` file, e.g. `3d/d6_face.png`. */
    val faceTexture3d: String? = null,
    /** d6 only: draw pips instead of numbers on 3D faces. */
    val pips: Boolean? = null,
)

object DiceSetFormat {
    const val VERSION = 1
    const val THEME = "theme"
    const val MANIFEST = "manifest.json"
    val DIE_KEYS = listOf("d4", "d6", "d8", "d10", "d12", "d20", "d100", "dN")
    val IMAGE_EXTENSIONS = setOf("png", "webp")
    val FONT_EXTENSIONS = setOf("ttf", "otf")
    const val MAX_EDGE_WIDTH = 8f

    /** Manifest key for a die with [sides]. */
    fun keyFor(sides: Int): String = "d$sides".takeIf { it in DIE_KEYS } ?: "dN"
}
